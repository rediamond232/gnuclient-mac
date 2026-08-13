package gnu.client.module.modules.movement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Soft-budget fly: stay under Grim Simulation immediate-setback (~0.1) and
 * pulse illegal ticks with vanilla rest so advantage can decay.
 */
public class FlySetbackStateTest {

    @Test
    public void startsUnlocked() {
        FlySetbackState state = new FlySetbackState();
        assertFalse(state.isLocked());
        assertTrue(state.canApplyAirControl());
    }

    @Test
    public void setbackLocksAirControl() {
        FlySetbackState state = new FlySetbackState();
        state.onSetbackReceived(2);
        assertTrue(state.isLocked());
        assertFalse(state.canApplyAirControl());
    }

    @Test
    public void cooldownTicksBeforeResume() {
        FlySetbackState state = new FlySetbackState();
        state.onSetbackReceived(2);

        state.onClientTick();
        assertTrue(state.isLocked());
        assertFalse(state.canApplyAirControl());

        state.onClientTick();
        assertFalse(state.isLocked());
        assertTrue(state.canApplyAirControl());
    }

    @Test
    public void zeroCooldownResumesNextTick() {
        FlySetbackState state = new FlySetbackState();
        state.onSetbackReceived(0);
        assertTrue(state.isLocked());
        state.onClientTick();
        assertFalse(state.isLocked());
        assertTrue(state.canApplyAirControl());
    }

    @Test
    public void resetClearsLock() {
        FlySetbackState state = new FlySetbackState();
        state.onSetbackReceived(5);
        state.reset();
        assertFalse(state.isLocked());
        assertTrue(state.canApplyAirControl());
    }

    @Test
    public void softHoverLeavesSmallFall() {
        assertEquals(FlySetbackState.HOVER_MOTION_Y,
                FlySetbackState.verticalMotion(FlySetbackState.MODE_HOVER, -0.5), 0.0001);
    }

    @Test
    public void glideCapsDownwardSpeed() {
        assertEquals(-FlySetbackState.GLIDE_MAX_FALL,
                FlySetbackState.verticalMotion(FlySetbackState.MODE_GLIDE, -0.5), 0.0001);
        assertEquals(-0.02, FlySetbackState.verticalMotion(FlySetbackState.MODE_GLIDE, -0.02), 0.0001);
        assertEquals(0.1, FlySetbackState.verticalMotion(FlySetbackState.MODE_GLIDE, 0.1), 0.0001);
    }

    @Test
    public void horizontalFromInputMatchesYaw() {
        double[] dir = FlySetbackState.horizontalMotion(0.0f, 1.0f, 0.0f, 0.05);
        assertEquals(0.0, dir[0], 0.001);
        assertEquals(0.05, dir[1], 0.001);
    }

    @Test
    public void horizontalNullWithoutInput() {
        assertNull(FlySetbackState.horizontalMotion(0.0f, 0.0f, 0.0f, 0.05));
    }

    @Test
    public void budgetClampKeepsOffsetUnderMax() {
        // Full hover (dy=0.08) + fast horiz would exceed immediate setback; clamp softens it.
        double[] out = FlySetbackState.clampToBudget(0.2, 0.0, 0.0, -0.08, 0.085);
        double dx = out[0];
        double dy = out[1] - (-0.08);
        double dz = out[2];
        double offset = Math.sqrt(dx * dx + dy * dy + dz * dz);
        assertTrue(offset <= 0.085 + 1e-9);
    }

    @Test
    public void budgetClampLeavesSoftMotionUntouched() {
        double[] out = FlySetbackState.clampToBudget(0.03, -0.04, 0.0, -0.08, 0.085);
        assertEquals(0.03, out[0], 0.0001);
        assertEquals(-0.04, out[1], 0.0001);
        assertEquals(0.0, out[2], 0.0001);
    }

    @Test
    public void pulseRestsBetweenActiveTicks() {
        FlySetbackState state = new FlySetbackState();
        state.setPulse(2, 2); // 2 active, 2 rest

        assertTrue(state.canApplyAirControl()); // tick phase 0
        state.onClientTick();
        assertTrue(state.canApplyAirControl()); // phase 1
        state.onClientTick();
        assertFalse(state.canApplyAirControl()); // phase 2 rest
        state.onClientTick();
        assertFalse(state.canApplyAirControl()); // phase 3 rest
        state.onClientTick();
        assertTrue(state.canApplyAirControl()); // phase 0 again
    }
}
