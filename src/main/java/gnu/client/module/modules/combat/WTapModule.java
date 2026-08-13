package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.FoodStats;
import net.minecraft.block.material.Material;
import net.minecraft.util.MovementInput;

import java.util.Arrays;
import java.util.List;

/**
 * WTap / SuperKnockback — three modes ported from LiquidBounce + Raven.
 *
 * <p><b>Packet</b> (SuperKnockback): On attack, sends C0B EntityAction sprint
 * stop/start burst directly to the server via {@link Mc#sendSprintActionPacket}.
 * Each {stop,start} pair forces the server to recalculate knockback, producing
 * maximum KB regardless of client-side sprint state. Default 3 bursts =
 * stop→start→stop→start→stop→start (6 C0B packets).</p>
 *
 * <p><b>SprintTap</b> (Raven-style): Sends one C0B STOP_SPRINTING immediately
 * on attack, then blocks client sprint key for controlled ticks via
 * {@link #shouldSuppressSprintKey()}. Vanilla sprint-stop logic sends the
 * C0B naturally on the movement tick.</p>
 *
 * <p><b>Legit</b> (OpenMyau / old WTap): Temporarily zeros {@code moveForward}
 * on the MovementInput so vanilla stops sprinting without any packet injection.
 * Most AC-friendly but least reliable KB increase.</p>
 */
public final class WTapModule extends Module implements PacketListener {

    private static final float FORWARD_STOP_THRESHOLD = 0.8f;
    private static final long MS_PER_TICK = 50L;

    private static final List<String> MODE_NAMES = Arrays.asList("Packet", "SprintTap", "Legit");

    // ── Settings ──────────────────────────────────────────────────────────

    private final ModeSetting mode = addSetting(
            new ModeSetting("Mode", 0, MODE_NAMES));
    private final SliderSetting chance = addSetting(
            new SliderSetting("Chance", 100.0f, 0.0f, 100.0f));
    private final SliderSetting hurtTime = addSetting(
            new SliderSetting("HurtTime", 10.0f, 0.0f, 10.0f));
    private final SliderSetting attackWindow = addSetting(
            new SliderSetting("AttackWindow", 500.0f, 50.0f, 2000.0f));
    private final BoolSetting playersOnly = addSetting(
            new BoolSetting("Players only", true));
    private final BoolSetting condMousePressed = addSetting(
            new BoolSetting("CondMousePressed", false));
    private final BoolSetting condDamage = addSetting(
            new BoolSetting("CondDamage", false));
    private final BoolSetting condFalling = addSetting(
            new BoolSetting("CondFalling", false));
    private final BoolSetting disableInWater = addSetting(
            new BoolSetting("Disable in water", true));
    private final BoolSetting backwardOnly = addSetting(
            new BoolSetting("Backward only", false));

    // Packet-mode burst count (default 3 = stop→start→stop→start→stop→start)
    private final SliderSetting packetBursts = addSetting(
            new SliderSetting("Packet bursts", 3.0f, 1.0f, 6.0f));
    private final BoolSetting packetRestoreSprint = addSetting(
            new BoolSetting("Restore sprint", true));

    // SprintTap delay/wait
    private final SliderSetting sprintTapDelay = addSetting(
            new SliderSetting("SprintTap delay", 1.0f, 0.0f, 6.0f));
    private final SliderSetting sprintTapWait = addSetting(
            new SliderSetting("SprintTap wait", 2.0f, 0.0f, 10.0f));

    // Legit mode settings (existing OpenMyau approach)
    private final SliderSetting legitTickDelay = addSetting(
            new SliderSetting("Legit tick delay", 5.5f, 0.0f, 10.0f));
    private final SliderSetting legitDuration = addSetting(
            new SliderSetting("Legit duration", 1.5f, 0.5f, 5.0f));
    private final SliderSetting legitStartDelay = addSetting(
            new SliderSetting("Legit start delay", 0.0f, 0.0f, 10.0f));
    private final BoolSetting legitSprintingOnly = addSetting(
            new BoolSetting("Legit sprinting only", true));
    private final BoolSetting legitDisableOnHit = addSetting(
            new BoolSetting("Legit disable on hit", false));

    // ── State ─────────────────────────────────────────────────────────────

    private boolean active;
    private boolean stopForward;
    private long delayTicksRemaining;
    private long durationTicksRemaining;
    private long lastWTapMs;
    private int lastAttackTick = -1;

    // SprintTap blocking state
    private boolean sprintTapBlocking;
    private int sprintTapBlockTicks;

    // World/player change guard
    private Entity lastPlayer;
    private WorldClient lastWorld;

    public WTapModule() {
        super("W Tap", "Resets sprint to increase knockback (SuperKnockback)", Category.COMBAT);
        packetBursts.visibleWhen(() -> mode.getValue() == 0);
        packetRestoreSprint.visibleWhen(() -> mode.getValue() == 0);
        sprintTapDelay.visibleWhen(() -> mode.getValue() == 1);
        sprintTapWait.visibleWhen(() -> mode.getValue() == 1);
        legitTickDelay.visibleWhen(() -> mode.getValue() == 2);
        legitDuration.visibleWhen(() -> mode.getValue() == 2);
        legitStartDelay.visibleWhen(() -> mode.getValue() == 2);
        legitSprintingOnly.visibleWhen(() -> mode.getValue() == 2);
        legitDisableOnHit.visibleWhen(() -> mode.getValue() == 2);
    }

    // ── Static hooks for external modules ─────────────────────────────────

    /**
     * SprintTap mode: SprintModule checks this to release the sprint keybind
     * while the post-attack block window is active.
     */
    public static boolean shouldSuppressSprintKey() {
        Module mod = ModuleManager.INSTANCE.getModule("W Tap");
        if (!(mod instanceof WTapModule) || !mod.isEnabled())
            return false;
        WTapModule wtap = (WTapModule) mod;
        return "SprintTap".equals(wtap.mode.getCurrentMode()) && wtap.sprintTapBlocking;
    }

    /**
     * Legit mode: called from {@code MovementInputHook.afterUpdatePlayerMoveState}
     * (Forge mixin at {@code MovementInputFromOptions.updatePlayerMoveState} RETURN)
     * after vanilla key read. Brief {@code moveForward = 0} lets vanilla
     * sprint-stop logic run.
     */
    public static void patchMovementInput(Object movInput) {
        Module mod = ModuleManager.INSTANCE.getModule("W Tap");
        if (!(mod instanceof WTapModule))
            return;
        WTapModule wtap = (WTapModule) mod;
        String m = wtap.mode.getCurrentMode();
        if (!"Legit".equals(m))
            return;
        if (!wtap.isEnabled() || !wtap.stopForward || movInput == null)
            return;
        MovementInput input = (MovementInput) movInput;
        input.moveForward = 0.0f;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        clearState();
        PacketEvents.register(this);

        Module moreKb = ModuleManager.INSTANCE.getModule("MoreKB");
        if (moreKb != null && moreKb.isEnabled())
            moreKb.setEnabled(false);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        clearState();
    }

    private void clearState() {
        active = false;
        stopForward = false;
        delayTicksRemaining = 0L;
        durationTicksRemaining = 0L;
        lastWTapMs = 0L;
        lastAttackTick = -1;
        sprintTapBlocking = false;
        sprintTapBlockTicks = 0;
        lastPlayer = null;
        lastWorld = null;
    }

    // ── Tick handlers ─────────────────────────────────────────────────────

    @Override
    public void onTickStart() {
        if (!isEnabled())
            return;
        checkWorldOrPlayerChange();

        // SprintTap mode: decrement block ticks every tick
        if (sprintTapBlocking) {
            if (sprintTapBlockTicks <= 0) {
                sprintTapBlocking = false;
            } else {
                sprintTapBlockTicks--;
            }
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled())
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null) {
            clearState();
            return;
        }

        checkWorldOrPlayerChange();

        String m = mode.getCurrentMode();
        if ("Legit".equals(m)) {
            tickLegit(player);
        }
    }

    private void tickLegit(EntityPlayerSP player) {
        if (!active)
            return;

        if (!stopForward && !canTrigger(player)) {
            clearState();
            return;
        }

        if (delayTicksRemaining > 0L) {
            delayTicksRemaining -= MS_PER_TICK;
            return;
        }

        stopForward = true;
        durationTicksRemaining -= MS_PER_TICK;
        if (durationTicksRemaining <= 0L)
            clearState();
    }

    private void checkWorldOrPlayerChange() {
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null) {
            clearState();
            return;
        }
        if (lastPlayer != null && lastPlayer != player)
            clearState();
        else if (lastWorld != null && lastWorld != world)
            clearState();
        lastPlayer = player;
        lastWorld = world;
    }

    // ── Packet events ─────────────────────────────────────────────────────

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        String m = mode.getCurrentMode();
        if ("Legit".equals(m) && legitDisableOnHit.getValue()) {
            if (PacketHelper.isSelfEntityVelocity(packet)) {
                clearState();
            }
        }
        return false;
    }

    // ── Attack entry point ────────────────────────────────────────────────

    /**
     * Called from CombatAttackNotify and ClientEventListener on Forge
     * AttackEntityEvent.
     */
    public void noteForgeAttack(Entity target) {
        tryBeginSequence(target);
    }

    private void tryBeginSequence(Entity target) {
        if (!isEnabled() || target == null || active)
            return;
        if (!(target instanceof EntityLivingBase))
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null || target == player)
            return;

        if (playersOnly.getValue() && !(target instanceof EntityPlayer))
            return;

        if (condMousePressed.getValue() && !ClientBootstrap.isLeftMouseDown())
            return;

        if (condDamage.getValue() && player.hurtTime <= 0)
            return;

        if (condFalling.getValue() && player.fallDistance <= 0.0f)
            return;

        if (disableInWater.getValue() && player.isInWater())
            return;

        int targetHurtTime = ((EntityLivingBase) target).deathTime;
        if (targetHurtTime > hurtTime.getValue().intValue())
            return;

        // Chance gate (LiquidBounce: random % roll)
        if (chance.getValue() < 100.0f && Math.random() * 100.0 >= chance.getValue())
            return;

        // Attack window cooldown
        long now = System.currentTimeMillis();
        if (lastWTapMs > 0L && now - lastWTapMs < attackWindow.getValue().longValue())
            return;

        // Backward-only gate
        if (backwardOnly.getValue()) {
            MovementInput movInput = player.movementInput;
            if (movInput != null && movInput.moveForward >= 0f)
                return;
        }

        int tick = player.ticksExisted;
        if (tick == lastAttackTick)
            return;
        lastAttackTick = tick;

        lastWTapMs = now;

        String m = mode.getCurrentMode();

        switch (m) {
            case "Packet":
                doPacketBurst(player);
                break;
            case "SprintTap":
                doSprintTap(player);
                break;
            case "Legit":
                doLegitSequence();
                break;
        }
    }

    // ── Mode implementations ──────────────────────────────────────────────

    /**
     * Packet mode (SuperKnockback — LiquidBounce).
     *
     * Sends {stop→start}×bursts C0B EntityAction packets directly to the
     * server via {@link Mc#sendSprintActionPacket}. Each pair forces
     * the server to recalculate knockback for the hit.
     *
     * LiquidBounce uses 3 bursts: stop→start→stop→start→stop→start.
     * After the burst, restores client sprint flags so the local player
     * visually stays sprinting.
     */
    private void doPacketBurst(EntityPlayerSP player) {
        if (player == null)
            return;

        boolean wasClientSprinting = Mc.isClientSprinting(player);
        boolean wasServerSprinting = Mc.getServerSprintState(player);

        int bursts = packetBursts.getValue().intValue();
        if (bursts < 1)
            bursts = 1;
        if (bursts > 6)
            bursts = 6;

        for (int i = 0; i < bursts; i++) {
            Mc.sendSprintActionPacket(player, false);
            Mc.sendSprintActionPacket(player, true);
        }

        if (packetRestoreSprint.getValue()) {
            Mc.setClientSprinting(player, wasClientSprinting);
            Mc.setServerSprintState(player, wasServerSprinting);
        }

        active = true;
    }

    private void doSprintTap(EntityPlayerSP player) {
        if (player == null)
            return;

        Mc.sendSprintActionPacket(player, false);

        // Block sprint for the combined delay+wait duration
        active = true;
        sprintTapBlocking = true;
        sprintTapBlockTicks = sprintTapDelay.getValue().intValue() + sprintTapWait.getValue().intValue();
    }

    /**
     * Legit mode (OpenMyau / existing behaviour).
     *
     * Schedule delayed {@code moveForward = 0} on the client's MovementInput,
     * letting vanilla sprint-stop logic send the C0B naturally on the next
     * movement tick. No direct packet injection.
     */
    private void doLegitSequence() {
        active = true;
        stopForward = false;
        delayTicksRemaining = (long) ((legitTickDelay.getValue() + legitStartDelay.getValue()) * MS_PER_TICK);
        durationTicksRemaining = (long) (legitDuration.getValue() * MS_PER_TICK);
    }

    // ── Legit-mode helpers ────────────────────────────────────────────────

    private boolean canTrigger(EntityPlayerSP player) {
        MovementInput movInput = player.movementInput;
        if (movInput != null && movInput.moveForward < FORWARD_STOP_THRESHOLD)
            return false;

        if (player.isInsideOfMaterial(Material.web))
            return false;

        if (!hasSprintFood(player))
            return false;

        if (legitSprintingOnly.getValue()) {
            if (!Mc.isClientSprinting(player))
                return false;
        }

        return Mc.isClientSprinting(player)
                || Mc.settings().keyBindSprint.isKeyDown();
    }

    private static boolean hasSprintFood(EntityPlayer player) {
        FoodStats food = player.getFoodStats();
        if (food == null)
            return true;
        if (player.capabilities.allowFlying)
            return true;
        return food.getFoodLevel() > 6;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }
}
