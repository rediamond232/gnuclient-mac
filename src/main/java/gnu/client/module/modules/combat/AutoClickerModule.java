package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.modules.combat.HitSelectModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import gnu.client.mixin.impl.accessors.IAccessorGuiContainer;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.util.MovingObjectPosition;

import java.util.Random;

/**
 * Auto-clicks left mouse at a randomized CPS.
 *
 * <p>Worker thread schedules clicks; {@link ClientBootstrap#queueAttackClick(int)} queues them
 * and {@link ClientBootstrap#drainPendingAttackClicks()} fires
 * {@link Mc#pressAttackKeyOnce()} on the client tick.
 *
 * Also supports clicking in inventory/container GUIs (merged from the removed
 * InvClick module) via {@code PlayerControllerMP.windowClick} reflection when
 * the "Inventory" setting is enabled.
 */
public final class AutoClickerModule extends Module {

    private static final int CLICK_HISTORY = 32;

    private final BoolSetting randomize = addSetting(new BoolSetting("Randomize", true));
    private final BoolSetting breakBlocks = addSetting(new BoolSetting("Break Blocks", true));
    private final BoolSetting inventoryClick = addSetting(new BoolSetting("Inventory", false));
    private final SliderSetting minCps = addSetting(new SliderSetting("MinCPS", 8.0f, 1.0f, 20.0f));
    private final SliderSetting maxCps = addSetting(new SliderSetting("MaxCPS", 14.0f, 1.0f, 20.0f));

    private final Random random = new Random();

    private volatile boolean running;
    private Thread worker;

    // Worker-thread-only humanization state.
    private int clickCount = 0;
    private int fatigueThreshold = 60;
    private boolean fatigueActive = false;
    private int fatigueTicksLeft = 0;
    private double cpsDrift = 1.0;
    private long nextDriftAtMs = 0L;

    // Inventory-specific state (worker thread only).
    private long inventoryNextClickMs = 0L;

    private final long[] clickTimes = new long[CLICK_HISTORY];
    private int clickTimeIdx = 0;

    public AutoClickerModule() {
        super("AutoClicker", "Automatically clicks at a set CPS", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        running = true;
        clickCount = 0;
        fatigueActive = false;
        cpsDrift = 1.0;
        nextDriftAtMs = 0L;
        inventoryNextClickMs = 0L;
        worker = new Thread(this::loop, "GnuAutoClicker");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void onDisable() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private void loop() {
        while (running) {
            try {
                // --- Inventory click path ---
                if (inventoryClick.getValue() && tryInventoryClick()) {
                    // inventoryClick handled the frame; sleep and continue
                    long interval = rollInterval();
                    if (!sleepMs(interval))
                        break;
                    continue;
                }

                // --- Normal world click path ---
                long interval = rollInterval();
                EntityPlayerSP player = Mc.player();
                boolean lmbDown = readLeftMouseDown();

                if (gatesPass(player, lmbDown)) {
                    if (randomize.getValue() && random.nextFloat() < rollSkipChance()) {
                        if (!sleepMs(interval + 30L + random.nextInt(120)))
                            break;
                        continue;
                    }

                    if (HitSelectModule.shouldBlockClick()) {
                        if (!sleepMs(interval))
                            break;
                        continue;
                    }
                    long hold = rollHoldMs(interval);
                    ClientBootstrap.queueAttackClick((int) hold);
                    recordClick(System.currentTimeMillis());
                    fatigueAccount();
                    AimAssistModule.lastClickMs = System.currentTimeMillis();

                    long elapsed = hold;
                    long sleep = Math.max(8L, interval - elapsed);
                    if (!sleepMs(sleep))
                        break;
                } else if (!sleepMs(interval)) {
                    break;
                }
            } catch (Throwable t) {
                break;
            }
        }
    }

    /**
     * Inventory click loop iteration. Checks for a container GUI and performs
     * {@code windowClick} on the hovered slot at the configured CPS rate.
     *
     * @return true if an inventory was handled (caller should skip normal click path)
     */
    private boolean tryInventoryClick() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;

        GuiScreen screen = Mc.currentScreen();
        if (screen == null)
            return false;

        if (!readLeftMouseDown())
            return false;

        long now = System.currentTimeMillis();
        if (inventoryNextClickMs == 0L) {
            inventoryNextClickMs = now + 50L;
        }
        if (now < inventoryNextClickMs)
            return true;

        if (!(screen instanceof GuiContainer))
            return false;
        GuiContainer containerGui = (GuiContainer) screen;
        Slot slot = ((IAccessorGuiContainer) containerGui).getTheSlot();
        if (slot == null)
            return false;

        int slotNumber = slot.slotNumber;
        if (slotNumber < 0)
            return false;

        Container container = player.openContainer;
        if (container == null)
            return false;
        int windowId = container.windowId;

        PlayerControllerMP playerController = Mc.controller();
        if (playerController == null)
            return false;

        // Perform one windowClick at current CPS timing
        int clicksThisTick = 0;
        while (inventoryNextClickMs <= now) {
            boolean shiftHeld = ClientBootstrap.isShiftDown();
            int clickMode = shiftHeld ? 1 : 0;
            windowClick(playerController, windowId, slotNumber, 0, clickMode, player);
            clicksThisTick++;
            inventoryNextClickMs += rollInventoryInterval();
        }

        if (clicksThisTick > 0) {
            fatigueAccount();
            recordClick(now);
            AimAssistModule.lastClickMs = now;
        }

        return true; // handled inventory frame
    }

    /**
     * Roll a slightly gentler interval for inventory clicks to avoid
     * desync — same CPS range but with less jitter.
     */
    private long rollInventoryInterval() {
        float lo = minCps.getValue();
        float hi = maxCps.getValue();
        if (hi < lo)
            hi = lo;
        double cps = lo + random.nextDouble() * (hi - lo);
        double interval = 1000.0 / Math.max(1.0, cps);
        if (randomize.getValue()) {
            interval += random.nextDouble() * 20.0 - 10.0;
        }
        return Math.max(50L, (long) interval);
    }

    private boolean gatesPass(EntityPlayerSP player, boolean lmbDown) {
        if (player == null)
            return false;
        if (Mc.currentScreen() != null)
            return false;

        if (!lmbDown)
            return false;

        if (breakBlocks.getValue()) {
            MovingObjectPosition mop = Mc.objectMouseOver();
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
                return false;
        }
        return true;
    }

    private static boolean readLeftMouseDown() {
        return ClientBootstrap.isLeftMouseDown();
    }

    private float rollSkipChance() {
        return 0.02f + random.nextFloat() * 0.04f;
    }

    private long rollInterval() {
        float lo = minCps.getValue();
        float hi = maxCps.getValue();
        if (hi < lo)
            hi = lo;

        refreshCpsDrift();

        double cps = lo + random.nextDouble() * (hi - lo);
        cps *= cpsDrift;
        double interval = 1000.0 / Math.max(1.0, cps);

        if (randomize.getValue()) {
            double jitter = 1.0 + (random.nextGaussian() * 0.18);
            interval *= Math.max(0.65, Math.min(1.45, jitter));
            jitter = 1.0 + (random.nextGaussian() * 0.11);
            interval *= Math.max(0.78, Math.min(1.28, jitter));
            interval += random.nextDouble() * 24.0 - 12.0;
        }

        if (fatigueActive)
            interval *= 1.22 + random.nextDouble() * 0.62;

        if (clickCount > 0 && clickCount % (14 + random.nextInt(19)) == 0)
            interval += 35.0 + random.nextDouble() * 110.0;

        double minMs = 1000.0 / Math.max(hi, lo);
        double maxMs = 1000.0 / Math.min(lo, hi);
        interval = Math.max(minMs * 0.88, Math.min(maxMs * 1.22, interval));
        return Math.max(42L, (long) interval);
    }

    private void refreshCpsDrift() {
        long now = System.currentTimeMillis();
        if (now < nextDriftAtMs)
            return;
        cpsDrift = 0.88 + random.nextDouble() * 0.24;
        nextDriftAtMs = now + 900L + random.nextInt(1400);
    }

    private long rollHoldMs(long interval) {
        double frac = 0.06 + random.nextDouble() * 0.22;
        return Math.max(4L, (long) (interval * frac));
    }

    private void fatigueAccount() {
        clickCount++;
        if (fatigueActive) {
            if (--fatigueTicksLeft <= 0)
                fatigueActive = false;
        } else if (clickCount >= fatigueThreshold) {
            fatigueActive = true;
            fatigueTicksLeft = 18 + random.nextInt(26);
            fatigueThreshold = 38 + random.nextInt(44);
            clickCount = 0;
        }
    }

    private void recordClick(long now) {
        clickTimes[clickTimeIdx % CLICK_HISTORY] = now;
        clickTimeIdx++;
    }

    private boolean sleepMs(long ms) {
        if (ms <= 0)
            return running;
        try {
            Thread.sleep(ms);
            return running;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private static void windowClick(PlayerControllerMP playerController, int windowId, int slotId,
                                    int clickedButton, int mode, EntityPlayer player) {
        playerController.windowClick(windowId, slotId, clickedButton, mode, player);
    }

    @Override
    public String[] getSuffix() {
        float lo = Math.min(minCps.getValue(), maxCps.getValue());
        float hi = Math.max(minCps.getValue(), maxCps.getValue());
        return new String[] { String.format("%.0f-%.0f", lo, hi) };
    }
}
