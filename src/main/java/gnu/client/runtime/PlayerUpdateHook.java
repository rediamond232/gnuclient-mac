package gnu.client.runtime;

import gnu.client.mixin.impl.accessors.IAccessorEntityPlayerSP;
import gnu.client.module.modules.combat.DisplaceModule;
import gnu.client.module.modules.combat.KeepSprintModule;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.movement.StasisModule;
import gnu.client.module.modules.player.NoSlowModule;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Forge mixin hooks around {@code EntityPlayerSP.onUpdate} (see
 * {@code MixinEntityPlayerSP}).
 *
 * <p>OpenMyau-style silent rotations are applied by temporarily writing the
 * requested yaw/pitch immediately before vanilla sends its walking packet, then
 * restoring the local camera rotation at {@code onUpdate} return.
 */
public final class PlayerUpdateHook {

    private static boolean overrideActive;
    private static float overrideYaw;
    private static float overridePitch;
    private static boolean pendingRestore;
    private static float pendingYaw;
    private static float pendingPitch;

    private PlayerUpdateHook() {}

    /**
     * @return true to skip the rest of {@code onUpdate} (raven {@code CallbackInfo.cancel}).
     */
    public static boolean onUpdateHead(Object player) {
        if (!(player instanceof EntityPlayerSP))
            return false;
        EntityPlayerSP sp = (EntityPlayerSP) player;
        EntityPlayerSP local = Mc.player();
        if (local == null || sp != local)
            return false;

        clearRotationOverride();
        StasisModule.onPreUpdate(player);
        DisplaceModule.onPreUpdate(player);
        KeepSprintModule.maintainWalkState();
        KillAuraModule.onPreUpdate(player);
        ScaffoldModule.onPreUpdate(player);
        return false;
    }

    public static void requestRotation(float yaw, float pitch) {
        overrideYaw = yaw;
        overridePitch = clampPitch(pitch);
        overrideActive = true;
    }

    /**
     * Yaw for movefix {@code moveFlying}/jump — OpenMyau uses
     * {@link RotationState#getSmoothedYaw()} (move/perv yaw), not C03 packet yaw.
     */
    public static float silentYawForMoveFix() {
        if (RotationState.isActived())
            return RotationState.getSmoothedYaw();
        if (overrideActive)
            return overrideYaw;
        return Mc.getYaw();
    }

    public static float lastReportedYaw(Object player) {
        if (!(player instanceof IAccessorEntityPlayerSP))
            return 0.0f;
        return ((IAccessorEntityPlayerSP) player).getLastReportedYaw();
    }

    public static float lastReportedPitch(Object player) {
        if (!(player instanceof IAccessorEntityPlayerSP))
            return 0.0f;
        return ((IAccessorEntityPlayerSP) player).getLastReportedPitch();
    }

    public static void beforeWalkingPlayer(Object player) {
        if (!isLocal(player))
            return;
        NoSlowModule.onGrimPreMovement();
        // Do NOT KeepSprint.applyStop here — living already ran. Clearing sprint after
        // sprint motion produces ground Simulation ~0.030 (walk packet vs sprint move).
        KillAuraModule.onBeforeWalkingPrepare(player);
        KillAuraModule.onBeforeWalkingAttack(player);
        if (overrideActive)
            beginRotationSwap(player);
        ScaffoldModule.onBeforeWalkingPlayer(player);
    }

    /** True between {@link #beginRotationSwap} and {@code onUpdate} return — rotation sent/will send this tick. */
    public static boolean isRotationTick() {
        return pendingRestore;
    }

    public static boolean hasRotationOverride() {
        return overrideActive;
    }

    /** After {@code onUpdateWalkingPlayer} — post-attack hooks only. */
    public static void onAfterWalkingPlayer(Object player) {
        if (!isLocal(player))
            return;
        KeepSprintModule.onAfterWalking();
        KillAuraModule.onAfterWalking(player);
    }

    /** Apply requested silent rotation to the local player before block placement. */
    public static void ensureRotationApplied(Object player) {
        beginRotationSwap(player);
    }

    private static void beginRotationSwap(Object player) {
        if (!(player instanceof EntityPlayerSP))
            return;
        EntityPlayerSP sp = (EntityPlayerSP) player;
        if (!isLocal(sp) || !overrideActive)
            return;
        if (!pendingRestore) {
            pendingYaw = sp.rotationYaw;
            pendingPitch = sp.rotationPitch;
            pendingRestore = true;
        }
        sp.rotationYaw = overrideYaw;
        sp.rotationPitch = overridePitch;
    }

    public static void onUpdateReturn(Object player) {
        if (!(player instanceof EntityPlayerSP)) {
            clearRotationOverride();
            return;
        }
        EntityPlayerSP sp = (EntityPlayerSP) player;
        if (!isLocal(sp)) {
            clearRotationOverride();
            return;
        }
        if (pendingRestore) {
            float sentYaw = sp.rotationYaw;
            float sentPitch = sp.rotationPitch;
            IAccessorEntityPlayerSP accessor = (IAccessorEntityPlayerSP) sp;
            accessor.setLastReportedYaw(sentYaw);
            accessor.setLastReportedPitch(sentPitch);

            float restoredYaw = sentYaw + wrapAngle(pendingYaw - sentYaw);
            sp.rotationYaw = restoredYaw;
            sp.rotationPitch = pendingPitch;
            sp.prevRotationYaw = restoredYaw;
            sp.prevRotationPitch = pendingPitch;
        }
        // Do NOT reset RotationState here — OpenMyau keeps state through render
        // (isRotated(1) for F5/FreeLook). Modules clear when they lose the target.
        clearRotationOverride();
        KillAuraModule.onPostUpdate();
    }

    private static boolean isLocal(Object player) {
        if (player == null)
            return false;
        EntityPlayerSP local = Mc.player();
        return local != null && player == local;
    }

    private static void clearRotationOverride() {
        overrideActive = false;
        pendingRestore = false;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0f, Math.min(90.0f, pitch));
    }

    private static float wrapAngle(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f)
            angle -= 360.0f;
        if (angle < -180.0f)
            angle += 360.0f;
        return angle;
    }

}
