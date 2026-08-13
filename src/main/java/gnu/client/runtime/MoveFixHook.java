package gnu.client.runtime;

/**
 * OpenMyau movefix physics gate for {@code moveFlying} / jump.
 *
 * <p>OpenMyau swaps yaw whenever {@link RotationState#isActived()}, but their
 * {@code getSmoothedYaw()} stays at camera yaw unless {@code setPervRotation}
 * ran. Our {@link RotationState#applyState} writes the <em>move</em> yaw
 * ({@code pervYaw}) into smoothYaw when MoveFix is armed, so we require KillAura
 * (1), Displace (2), or Scaffold (3) priority — otherwise Simulation fires from
 * silent yaw + camera WASD.
 */
public final class MoveFixHook {

    private MoveFixHook() {}

    public static boolean shouldUseServerMoveYaw() {
        if (!RotationState.isActived())
            return false;
        int priority = (int) RotationState.getPriority();
        return priority == MoveFixUtil.KILLAURA_MOVE_FIX_PRIORITY
            || priority == MoveFixUtil.DISPLACE_MOVE_FIX_PRIORITY
            || priority == MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY;
    }
}
