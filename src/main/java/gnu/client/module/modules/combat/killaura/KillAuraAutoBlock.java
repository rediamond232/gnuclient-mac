package gnu.client.module.modules.combat.killaura;

import gnu.client.mixin.impl.accessors.IAccessorPlayerControllerMP;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.network.LagrangeModule;
import gnu.client.module.modules.player.NoSlowModule;
import gnu.client.runtime.BlinkManager;
import gnu.client.runtime.BlinkModules;
import gnu.client.runtime.PlayerStateManager;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketHelper;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Random;

/**
 * OpenMyau KillAura auto-block mode switch (cases 0–9).
 * KillAuraModule owns settings and combat gates; this helper owns block/blink state.
 */
public final class KillAuraAutoBlock {

    public static final int NONE = 0;
    public static final int VANILLA = 1;
    public static final int SPOOF = 2;
    public static final int HYPIXEL = 3;
    public static final int BLINK = 4;
    public static final int INTERACT = 5;
    public static final int SWAP = 6;
    public static final int LEGIT = 7;
    public static final int FAKE = 8;
    public static final int GRIM = 9;
    public static final int WATCHDOG2 = 10;
    public static final int HYPIXEL3 = 11;

    /** Reference performAttack: modes that may attack while sword-blocking. */
    public static boolean isAttackAllowedWhileBlocking(int mode) {
        return mode == VANILLA || mode == GRIM || mode == WATCHDOG2 || mode == HYPIXEL3;
    }

    /** Reference shouldAutoBlock mode membership (water/lava checked at call site). */
    public static boolean isShouldAutoBlockMode(int mode) {
        return mode == HYPIXEL
            || mode == BLINK
            || mode == INTERACT
            || mode == SWAP
            || mode == LEGIT
            || mode == GRIM
            || mode == WATCHDOG2
            || mode == HYPIXEL3;
    }

    /** Grim phase captured at {@link #tick} entry before the switch mutates {@code grimState}. */
    public int getPreTickGrimPhase() {
        return preGrimPhase;
    }

    /** {@code ctx.attackEligible} at {@link #tick} entry before attack is mutated. */
    public boolean getPreTickGrimAttackAllowed() {
        return preMutationAttackAllowed;
    }

    private final Random random = new Random();

    private boolean isBlocking;
    private boolean fakeBlockState;
    private boolean blockingState;
    private int blockTick;
    private boolean blinkReset;
    private int grimState;
    private int preGrimPhase = -1;
    private boolean preMutationAttackAllowed;
    private int grimReleaseTick;
    private int hypixel3Asw;
    private long watchdog2BlockDelayMs = 166L;
    private long watchdog2BlockStartMs;
    private int lastMode = NONE;

    /** Inputs for one KillAura preUpdate combat tick. */
    public static final class Context {
        public int mode;
        /** Any living candidate within AutoBlockRange (KA computes). */
        public boolean hasValidTarget;
        /** {@code target != null && canAttack()} from KA (before autoblock mutates). */
        public boolean attackEligible;
        /** Sword held and optional use-key press. */
        public boolean canAutoBlock;
        public int grimReleaseDelay;
        public long attackDelayMs;
        /** Single AutoBlockCPS from KillAura settings. */
        public float autoBlockCps;
        public float yaw;
        public float pitch;
        /** Current KA target for interactAttack; may be null. */
        public EntityLivingBase target;
    }

    /** Outputs for Task 8 wiring. */
    public static final class TickResult {
        public boolean attackAllowed;
        /** After attack: interactAttack if attacked, else startBlock. */
        public boolean swap;
        /** After attack: pulse AUTO_BLOCK blink false→true. */
        public boolean blockedBlinkPulse;
        /** OpenMyau {@code isBlocking} — use AutoBlockCPS when true. */
        public boolean blockingSession;
    }

    public void reset() {
        setAutoBlockBlink(false);
        blinkReset = false;
        blockTick = 0;
        hypixel3Asw = 0;
        grimState = 0;
        grimReleaseTick = 0;
        preGrimPhase = -1;
        preMutationAttackAllowed = false;
        watchdog2BlockDelayMs = 166L;
        watchdog2BlockStartMs = 0L;
        isBlocking = false;
        fakeBlockState = false;
        if (blockingState || isPlayerBlocking())
            stopBlock();
        blockingState = false;
        lastMode = NONE;
    }

    /** OpenMyau POST: release then reacquire AUTO_BLOCK when blinkReset was set. */
    public void onPostUpdate() {
        if (!blinkReset)
            return;
        blinkReset = false;
        setAutoBlockBlink(false);
        setAutoBlockBlink(true);
    }

    /**
     * Call each KA preUpdate when KA has combat context (enabled / target path).
     * Mirrors OpenMyau PRE auto-block switch; does not perform the attack itself.
     */
    public TickResult tick(Context ctx) {
        TickResult result = new TickResult();
        if (ctx == null) {
            result.attackAllowed = false;
            return result;
        }
        preGrimPhase = (ctx.mode == GRIM) ? grimState : -1;
        preMutationAttackAllowed = ctx.attackEligible;
        lastMode = ctx.mode;
        boolean attack = ctx.attackEligible;
        boolean block = attack && ctx.canAutoBlock;
        // FAKE is visual-only: keep pose whenever sword + AB-range target exist,
        // even if attackEligible is false (delay / gates). Other modes still
        // clear fakeBlockState when the real block session is inactive.
        if (ctx.mode == FAKE) {
            setAutoBlockBlink(false);
            isBlocking = false;
            fakeBlockState = ctx.canAutoBlock && ctx.hasValidTarget;
            blockTick = 0;
        } else if (!block) {
            setAutoBlockBlink(false);
            isBlocking = false;
            fakeBlockState = false;
            blockTick = 0;
        }
        result.attackAllowed = attack;
        result.swap = false;
        result.blockedBlinkPulse = false;
        if (!attack) {
            result.blockingSession = isBlocking;
            return result;
        }

        boolean swap = false;
        boolean blocked = false;
        if (block) {
            boolean digging = isDigging();
            boolean placing = isPlacing();
            switch (ctx.mode) {
                case NONE:
                    if (Mc.isUsingItem()) {
                        isBlocking = true;
                        if (!isPlayerBlocking() && !digging && !placing)
                            swap = true;
                    } else {
                        isBlocking = false;
                        if (isPlayerBlocking() && !digging && !placing)
                            stopBlock();
                    }
                    setAutoBlockBlink(false);
                    fakeBlockState = false;
                    break;
                case VANILLA:
                    if (ctx.hasValidTarget) {
                        if (!isPlayerBlocking() && !digging && !placing)
                            swap = true;
                        setAutoBlockBlink(false);
                        isBlocking = true;
                        fakeBlockState = false;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case SPOOF:
                    if (ctx.hasValidTarget) {
                        int item = currentPlayerItem();
                        EntityPlayerSP player = Mc.player();
                        if (digging || placing
                                || player == null
                                || player.inventory.currentItem != item
                                || isPlayerBlocking() && blockTick != 0
                                || ctx.attackDelayMs > 0L && ctx.attackDelayMs <= 50L) {
                            blockTick = 0;
                        } else {
                            int slot = findEmptySlot(item);
                            Mc.sendHeldItemChange(slot);
                            Mc.sendHeldItemChange(item);
                            clearBlockAfterSlotChange(player);
                            swap = true;
                            blockTick = 1;
                        }
                        setAutoBlockBlink(false);
                        isBlocking = true;
                        fakeBlockState = false;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case HYPIXEL:
                    if (ctx.hasValidTarget) {
                        if (!digging && !placing) {
                            switch (blockTick) {
                                case 0:
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    blocked = true;
                                    blockTick = 1;
                                    break;
                                case 1:
                                    if (isPlayerBlocking()) {
                                        NoSlowModule noSlow = NoSlowModule.instance();
                                        if (noSlow != null && noSlow.isEnabled()) {
                                            EntityPlayerSP p = Mc.player();
                                            if (p != null) {
                                                int randomSlot = random.nextInt(9);
                                                while (randomSlot == p.inventory.currentItem)
                                                    randomSlot = random.nextInt(9);
                                                Mc.sendHeldItemChange(randomSlot);
                                                Mc.sendHeldItemChange(p.inventory.currentItem);
                                                clearBlockAfterSlotChange(p);
                                            }
                                        }
                                        stopBlock();
                                        attack = false;
                                    }
                                    if (ctx.attackDelayMs <= 50L)
                                        blockTick = 0;
                                    break;
                                default:
                                    blockTick = 0;
                            }
                        }
                        isBlocking = true;
                        fakeBlockState = true;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case BLINK:
                    if (ctx.hasValidTarget) {
                        if (!digging && !placing) {
                            switch (blockTick) {
                                case 0:
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    blinkReset = true;
                                    blockTick = 1;
                                    break;
                                case 1:
                                    if (isPlayerBlocking()) {
                                        stopBlock();
                                        attack = false;
                                    }
                                    if (ctx.attackDelayMs <= 50L)
                                        blockTick = 0;
                                    break;
                                default:
                                    blockTick = 0;
                            }
                        }
                        isBlocking = true;
                        fakeBlockState = true;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case INTERACT:
                    if (ctx.hasValidTarget) {
                        int item = currentPlayerItem();
                        EntityPlayerSP player = Mc.player();
                        if (player != null
                                && player.inventory.currentItem == item
                                && !digging
                                && !placing) {
                            switch (blockTick) {
                                case 0:
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    blinkReset = true;
                                    blockTick = 1;
                                    break;
                                case 1:
                                    if (isPlayerBlocking()) {
                                        int slot = findEmptySlot(item);
                                        Mc.sendHeldItemChange(slot);
                                        setCurrentPlayerItem(slot);
                                        attack = false;
                                    }
                                    if (ctx.attackDelayMs <= 50L)
                                        blockTick = 0;
                                    break;
                                default:
                                    blockTick = 0;
                            }
                        }
                        isBlocking = true;
                        fakeBlockState = true;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case SWAP:
                    if (ctx.hasValidTarget) {
                        int item = currentPlayerItem();
                        EntityPlayerSP player = Mc.player();
                        if (player != null
                                && player.inventory.currentItem == item
                                && !digging
                                && !placing) {
                            switch (blockTick) {
                                case 0: {
                                    int slot = findSwordSlot(item);
                                    if (slot != -1) {
                                        if (!isPlayerBlocking())
                                            swap = true;
                                        blockTick = 1;
                                    }
                                    break;
                                }
                                case 1: {
                                    int swordsSlot = findSwordSlot(item);
                                    if (swordsSlot == -1) {
                                        blockTick = 0;
                                    } else if (!isPlayerBlocking()) {
                                        swap = true;
                                    } else if (ctx.attackDelayMs <= 50L) {
                                        Mc.sendHeldItemChange(swordsSlot);
                                        setCurrentPlayerItem(swordsSlot);
                                        clearBlockAfterSlotChange(player);
                                        startBlock(player.inventory.getStackInSlot(swordsSlot));
                                        attack = false;
                                        blockTick = 0;
                                    }
                                    break;
                                }
                                default:
                                    blockTick = 0;
                            }
                            setAutoBlockBlink(false);
                            isBlocking = true;
                            fakeBlockState = true;
                            break;
                        }
                    }
                    setAutoBlockBlink(false);
                    isBlocking = false;
                    fakeBlockState = false;
                    break;
                case LEGIT:
                    if (ctx.hasValidTarget) {
                        if (!digging && !placing) {
                            switch (blockTick) {
                                case 0:
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    blockTick = 1;
                                    break;
                                case 1:
                                    if (isPlayerBlocking()) {
                                        stopBlock();
                                        attack = false;
                                    }
                                    if (ctx.attackDelayMs <= 50L)
                                        blockTick = 0;
                                    break;
                                default:
                                    blockTick = 0;
                            }
                        }
                        setAutoBlockBlink(false);
                        isBlocking = true;
                        fakeBlockState = false;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                    }
                    break;
                case FAKE:
                    // State already applied above (visual-only; independent of attackEligible).
                    if (Mc.isUsingItem() && !isPlayerBlocking() && !digging && !placing)
                        swap = true;
                    break;
                case GRIM:
                    if (ctx.hasValidTarget) {
                        switch (grimState) {
                            case 0:
                                attack = true;
                                isBlocking = false;
                                fakeBlockState = false;
                                grimState = 1;
                                break;
                            case 1:
                                if (!digging && !placing && !isPlayerBlocking()) {
                                    NoSlowModule noSlow = NoSlowModule.instance();
                                    if (noSlow == null || !noSlow.isEnabled() || !noSlow.isSwordGrimActive()) {
                                        EntityPlayerSP player = Mc.player();
                                        if (player != null)
                                            Mc.sendHeldItemChange(grimSwapSlot(player.inventory.currentItem));
                                    }
                                    swap = true;
                                }
                                attack = false;
                                isBlocking = true;
                                fakeBlockState = false;
                                grimState = 2;
                                break;
                            case 2:
                                if (isPlayerBlocking())
                                    stopBlock();
                                attack = false;
                                isBlocking = false;
                                fakeBlockState = false;
                                grimReleaseTick = 0;
                                grimState = 3;
                                break;
                            case 3:
                                grimReleaseTick++;
                                if (grimReleaseTick >= ctx.grimReleaseDelay)
                                    grimState = 4;
                                break;
                            case 4:
                                if (ctx.attackDelayMs <= 0L)
                                    grimState = 0;
                                break;
                            default:
                                grimState = 0;
                                break;
                        }
                        setAutoBlockBlink(false);
                    } else {
                        if (isPlayerBlocking())
                            stopBlock();
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                        grimState = 0;
                        grimReleaseTick = 0;
                    }
                    break;
                case WATCHDOG2:
                    if (ctx.hasValidTarget) {
                        if (!digging && !placing) {
                            switch (blockTick) {
                                case 0:
                                    attack = false;
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    watchdog2BlockDelayMs = watchdog2HoldDelayMs(ctx.autoBlockCps);
                                    watchdog2BlockStartMs = System.currentTimeMillis();
                                    blockTick = 1;
                                    break;
                                case 1:
                                    attack = false;
                                    if (isPlayerBlocking()
                                            && System.currentTimeMillis() - watchdog2BlockStartMs
                                            >= watchdog2BlockDelayMs) {
                                        stopBlock();
                                        blockTick = 2;
                                    }
                                    break;
                                case 2:
                                    attack = false;
                                    if (ctx.attackDelayMs <= 0L)
                                        blockTick = 3;
                                    break;
                                case 3:
                                    attack = true;
                                    isBlocking = false;
                                    fakeBlockState = false;
                                    break;
                                default:
                                    blockTick = 0;
                            }
                        }
                        setAutoBlockBlink(false);
                        if (blockTick != 3) {
                            isBlocking = true;
                            fakeBlockState = false;
                        }
                    } else {
                        if (isPlayerBlocking())
                            stopBlock();
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                        blockTick = 0;
                    }
                    break;
                case HYPIXEL3:
                    if (ctx.hasValidTarget) {
                        setAutoBlockBlink(true);
                        if (!digging && !placing) {
                            switch (hypixel3Asw) {
                                case 0:
                                    if (isPlayerBlocking())
                                        stopBlock();
                                    attack = false;
                                    hypixel3Asw = 1;
                                    break;
                                case 1:
                                    if (isPlayerBlocking())
                                        stopBlock();
                                    attack = false;
                                    hypixel3Asw = 2;
                                    break;
                                case 2:
                                    if (!isPlayerBlocking())
                                        swap = true;
                                    blocked = true;
                                    hypixel3Asw = 0;
                                    break;
                                default:
                                    hypixel3Asw = 0;
                            }
                        } else {
                            attack = false;
                        }
                        isBlocking = true;
                        fakeBlockState = true;
                    } else {
                        setAutoBlockBlink(false);
                        isBlocking = false;
                        fakeBlockState = false;
                        hypixel3Asw = 0;
                    }
                    break;
                default:
                    setAutoBlockBlink(false);
                    isBlocking = false;
                    fakeBlockState = false;
                    break;
            }
        }

        result.attackAllowed = attack;
        result.swap = swap;
        result.blockedBlinkPulse = blocked;
        result.blockingSession = isBlocking;
        return result;
    }

    /**
     * OpenMyau after-attack path: swap → interactAttack or startBlock; blocked → blink pulse.
     */
    public void applyAfterAttack(TickResult result, boolean attacked, float yaw, float pitch, EntityLivingBase target) {
        if (result == null)
            return;
        if (result.swap) {
            if (attacked)
                interactAttack(target, yaw, pitch);
            else
                sendUseItem();
        }
        if (result.blockedBlinkPulse) {
            setAutoBlockBlink(false);
            setAutoBlockBlink(true);
        }
    }

    /** wsamiaw onPacket: C07 release / C09 slot change clear blockingState. */
    public void onOutboundPacket(Object packet) {
        if (PacketHelper.isReleaseUseItem(packet)) {
            blockingState = false;
            return;
        }
        if (PacketHelper.isHeldItemChange(packet) && lastMode != GRIM) {
            blockingState = false;
            if (isBlocking) {
                EntityPlayerSP player = Mc.player();
                if (player != null)
                    player.stopUsingItem();
            }
        }
    }

    public void notifyAttackSucceeded() {
        if (lastMode == WATCHDOG2)
            blockTick = 0;
    }

    public boolean shouldAutoBlock() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (player.isInWater() || player.isInLava())
            return false;
        return isPlayerBlocking() && isBlocking && isShouldAutoBlockMode(lastMode);
    }

    public boolean isPlayerBlocking() {
        EntityPlayerSP player = Mc.player();
        return player != null
                && (player.isUsingItem() || blockingState)
                && Mc.isHoldingSword();
    }

    public boolean isBlockingSession() {
        return isBlocking;
    }

    public boolean isFakeBlocking() {
        return fakeBlockState && Mc.isHoldingSword();
    }

    /** Reference performAttack: skip when blocking unless mode allows it. */
    public boolean shouldDeferAttack() {
        return isPlayerBlocking() && !isAttackAllowedWhileBlocking(lastMode);
    }

    public long attackDelayMsWhenBlocking(float autoBlockCps) {
        if (autoBlockCps <= 0.0f)
            return 1000L;
        return (long) (1000.0f / autoBlockCps);
    }

    static long watchdog2HoldDelayMs(float autoBlockCps) {
        if (autoBlockCps <= 0.0f)
            return 166L;
        return (long) (1000.0 / autoBlockCps);
    }

    private void startBlock(ItemStack stack) {
        EntityPlayerSP player = Mc.player();
        if (player == null || stack == null)
            return;
        Mc.startSwordBlock(player, stack);
        blockingState = true;
    }

    private void stopBlock() {
        EntityPlayerSP player = Mc.player();
        if (player == null) {
            blockingState = false;
            return;
        }
        Mc.stopSwordBlock(player);
        blockingState = false;
    }

    /** OpenMyau onPacket C09: clear blockingState and stop client use. */
    private void clearBlockAfterSlotChange(EntityPlayerSP player) {
        blockingState = false;
        if (player != null)
            player.stopUsingItem();
    }

    private void sendUseItem() {
        if (lastMode != GRIM) {
            PlayerControllerMP controller = Mc.controller();
            if (controller instanceof IAccessorPlayerControllerMP)
                ((IAccessorPlayerControllerMP) controller).invokeSyncCurrentPlayItem();
        }
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        startBlock(player.getHeldItem());
    }

    private void interactAttack(EntityLivingBase target, float yaw, float pitch) {
        if (target == null)
            return;
        MovingObjectPosition mop = rayTraceBox(target, yaw, pitch, 8.0);
        if (mop == null)
            return;
        PlayerControllerMP controller = Mc.controller();
        if (controller instanceof IAccessorPlayerControllerMP)
            ((IAccessorPlayerControllerMP) controller).invokeSyncCurrentPlayItem();
        Vec3 hit = mop.hitVec;
        Mc.addToSendQueue(new C02PacketUseEntity(
                target,
                new Vec3(hit.xCoord - target.posX, hit.yCoord - target.posY, hit.zCoord - target.posZ)));
        Mc.addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        ItemStack held = player.getHeldItem();
        if (held == null)
            return;
        Mc.startSwordBlock(player, held);
        blockingState = true;
    }

    private static MovingObjectPosition rayTraceBox(EntityLivingBase entity, float yaw, float pitch, double distance) {
        EntityPlayerSP player = Mc.player();
        if (player == null || entity == null || distance <= 0.0)
            return null;
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
        Vec3 eye = player.getPositionEyes(1.0f);
        float f = (float) Math.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = (float) Math.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = (float) -Math.cos(-pitch * 0.017453292F);
        float f3 = (float) Math.sin(-pitch * 0.017453292F);
        Vec3 look = new Vec3(f1 * f2, f3, f * f2);
        Vec3 end = eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return box.calculateIntercept(eye, end);
    }

    static int findEmptySlot(int currentSlot) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return Math.floorMod(currentSlot - 1, 9);
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot && player.inventory.getStackInSlot(i) == null)
                return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack != null && !stack.hasDisplayName())
                    return i;
            }
        }
        return Math.floorMod(currentSlot - 1, 9);
    }

    private int grimSwapSlot(int currentSlot) {
        return currentSlot == 0 ? 1 : 0;
    }

    static int findSwordSlot(int currentSlot) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return -1;
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                ItemStack item = player.inventory.getStackInSlot(i);
                if (item != null && item.getItem() instanceof ItemSword)
                    return i;
            }
        }
        return -1;
    }

    private void setAutoBlockBlink(boolean state) {
        if (state) {
            Module lag = ModuleManager.INSTANCE.getModule("Lagrange");
            if (lag instanceof LagrangeModule && lag.isEnabled())
                ((LagrangeModule) lag).pauseForBlink();
        }
        BlinkManager.INSTANCE.setBlinkState(state, BlinkModules.AUTO_BLOCK);
    }

    private static boolean isDigging() {
        return PlayerStateManager.INSTANCE.digging;
    }

    private static boolean isPlacing() {
        return PlayerStateManager.INSTANCE.placing;
    }

    private static int currentPlayerItem() {
        PlayerControllerMP controller = Mc.controller();
        if (controller instanceof IAccessorPlayerControllerMP)
            return ((IAccessorPlayerControllerMP) controller).getCurrentPlayerItem();
        EntityPlayerSP player = Mc.player();
        return player != null ? player.inventory.currentItem : 0;
    }

    private static void setCurrentPlayerItem(int slot) {
        PlayerControllerMP controller = Mc.controller();
        if (controller instanceof IAccessorPlayerControllerMP)
            ((IAccessorPlayerControllerMP) controller).setCurrentPlayerItem(slot);
    }
}
