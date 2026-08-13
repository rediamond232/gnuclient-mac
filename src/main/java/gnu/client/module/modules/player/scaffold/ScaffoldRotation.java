package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.PlayerUpdateHook;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Silent rotation for Scaffold — mirrors KillAura's rotation contract exactly:
 * the look is armed through {@link PlayerUpdateHook#requestRotation} (C03 override)
 * and {@link RotationState#applyState} with the Scaffold MoveFix priority, so
 * {@code MoveFixHook} remaps WASD relative to the sent look while the camera stays
 * wherever the user points it.
 */
final class ScaffoldRotation {

    private ScaffoldRotation() {}

    /**
     * @param moveFixOn whether MoveFix is enabled — when on, the move yaw tracks the
     *                  sent yaw (priority SCAFFOLD_MOVE_FIX_PRIORITY); when off, the
     *                  move yaw stays at camera yaw (render-only priority -1)
     */
    static void arm(EntityPlayerSP player, float yaw, float pitch, boolean moveFixOn) {
        if (player == null)
            return;
        // Grim AimModulo360: the C03 yaw must be in [0, 360) — negative yaws flag.
        yaw = ((yaw % 360.0f) + 360.0f) % 360.0f;
        PlayerUpdateHook.requestRotation(yaw, pitch);
        float pervYaw = moveFixOn ? yaw : Mc.getYaw();
        int priority = moveFixOn ? MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY : -1;
        RotationState.applyState(true, yaw, pitch, pervYaw, priority);
    }

    /** Releases the shared rotation state only if Scaffold owns it (never another module's). */
    static void disarmIfOwned() {
        int p = (int) RotationState.getPriority();
        if (p == MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY || p == -1)
            RotationState.reset();
    }
}
