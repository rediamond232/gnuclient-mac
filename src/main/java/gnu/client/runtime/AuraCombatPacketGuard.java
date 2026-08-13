package gnu.client.runtime;

import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;

/**
 * Grim BadPacketsX parity: at most one sprint and one sneak C0B between C03 packets.
 * Also blocks START_SPRINTING on the aura attack tick (vanilla attack slow window).
 *
 * <p>Grim PacketOrderI: after {@code C07 RELEASE_USE_ITEM} in a tick, an attack
 * ({@code C02}) before the tick packet flags {@code type=attack, releasing=true}.
 * Manual unblock (no AutoBlock) sends C07 during key processing; KillAura must not
 * attack later in the same tick.
 *
 * <p>Grim RotationPlace: sword RMB while the crosshair is on a block also sends a
 * real face {@code C08} (interact/place) before the use-item {@code C08} (dir=255).
 * Silent KillAura C03 look aims at the target, not that block → pre/post-flying.
 * Cancel non-use-item C08 while KA is on and holding a sword.
 *
 * <p>Registered while KillAura is enabled. {@link gnu.client.runtime.mc.Mc#sendSprintActionPacket}
 * peeks via {@link #shouldCancelEntityAction} (read-only); only {@link #onSend} commits the
 * one-per-move slot so a pre-check cannot mark the slot and then cancel the real send.
 */
public final class AuraCombatPacketGuard implements PacketListener {

    private static final AuraCombatPacketGuard INSTANCE = new AuraCombatPacketGuard();

    private boolean sprintActionSinceMove;
    private boolean sneakActionSinceMove;
    /** Set when a RELEASE_USE_ITEM is sent; cleared at client tick start. */
    private boolean releaseUseItemThisTick;

    private AuraCombatPacketGuard() {}

    public static void register() {
        gnu.client.runtime.packet.PacketEvents.register(INSTANCE);
    }

    public static void unregister() {
        gnu.client.runtime.packet.PacketEvents.unregister(INSTANCE);
        INSTANCE.sprintActionSinceMove = false;
        INSTANCE.sneakActionSinceMove = false;
        INSTANCE.releaseUseItemThisTick = false;
    }

    /** Call from KillAura {@code onTickStart} so the flag is per client tick. */
    public static void onClientTickStart() {
        INSTANCE.releaseUseItemThisTick = false;
    }

    /**
     * True when an attack this tick would hit Grim PacketOrderI after a prior
     * {@code C07 RELEASE_USE_ITEM} in the same tick.
     */
    public static boolean shouldSkipAttackForReleaseOrder() {
        return isGuardActive() && INSTANCE.releaseUseItemThisTick;
    }

    /**
     * Read-only peek for {@code Mc.send*ActionPacket} — must not mutate slot flags.
     * Commit happens only in {@link #onSend}.
     */
    public static boolean shouldCancelEntityAction(Object packet) {
        if (!isGuardActive())
            return false;
        return INSTANCE.wouldCancel(packet);
    }

    @Override
    public int sendPriority() {
        return 900;
    }

    @Override
    public boolean onSend(Object packet) {
        if (!isGuardActive())
            return false;
        // Observe only — never cancel RELEASE_USE_ITEM.
        if (PacketHelper.isReleaseUseItem(packet)) {
            releaseUseItemThisTick = true;
            return false;
        }
        // Sword RMB against a block: drop interact/place C08; keep use-item (255).
        if (shouldCancelSwordBlockInteract(packet))
            return true;
        if (PacketHelper.isPlayerMovement(packet)) {
            sprintActionSinceMove = false;
            sneakActionSinceMove = false;
            return false;
        }
        return checkEntityAction(packet);
    }

    private static boolean shouldCancelSwordBlockInteract(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    private boolean wouldCancel(Object packet) {
        if (PacketHelper.isSprintEntityAction(packet))
            return wouldCancelSprint(packet);
        if (PacketHelper.isSneakEntityAction(packet))
            return sneakActionSinceMove;
        return false;
    }

    private boolean wouldCancelSprint(Object packet) {
        // One sprint C0B per move (BadPacketsX). No post-hit START suppress —
        // OpenMyau lets living re-sprint after attack slow.
        return sprintActionSinceMove;
    }

    /**
     * Mark the sprint C0B slot without sending — for KeepSprint STOP sent via
     * {@code sendPacketNoEvent} so BadPacketsX stays in sync with Grim.
     */
    public static void markSprintActionSent() {
        if (isGuardActive())
            INSTANCE.sprintActionSinceMove = true;
    }

    public static boolean isSprintSlotFree() {
        return !isGuardActive() || !INSTANCE.sprintActionSinceMove;
    }

    private boolean checkEntityAction(Object packet) {
        if (PacketHelper.isSprintEntityAction(packet))
            return checkSprintAction(packet);
        if (PacketHelper.isSneakEntityAction(packet))
            return checkSneakAction();
        return false;
    }

    private boolean checkSprintAction(Object packet) {
        if (wouldCancelSprint(packet))
            return true;
        sprintActionSinceMove = true;
        return false;
    }

    private boolean checkSneakAction() {
        if (sneakActionSinceMove)
            return true;
        sneakActionSinceMove = true;
        return false;
    }

    private static boolean isGuardActive() {
        Module module = ModuleManager.instance().getModule("KillAura");
        return module instanceof KillAuraModule && module.isEnabled();
    }
}
