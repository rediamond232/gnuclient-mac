package gnu.client.module.modules.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeepSprintStopStateTest {

    @Test
    public void stopThenOneWalkC03ThenReady() {
        KeepSprintStopState s = new KeepSprintStopState();
        s.setWalkC03s(1);
        s.beginStop();
        assertEquals(KeepSprintStopState.Phase.STOPPING, s.getPhase());
        assertTrue(s.shouldDeferAttack());
        s.onWalkC03(false);
        assertEquals(KeepSprintStopState.Phase.STOPPING, s.getPhase());
        s.onWalkC03(true);
        assertEquals(KeepSprintStopState.Phase.READY, s.getPhase());
        assertTrue(s.canOwnAttack());
        assertFalse(s.shouldDeferAttack());
    }

    @Test
    public void hitReturnsToIdle() {
        KeepSprintStopState s = new KeepSprintStopState();
        s.setWalkC03s(1);
        s.beginStop();
        s.onWalkC03(true);
        s.onOwnedHitFinished();
        assertEquals(KeepSprintStopState.Phase.IDLE, s.getPhase());
        assertFalse(s.shouldSuppressSprintKey());
    }

    @Test
    public void beginStopWhileReadyIsNoOp() {
        KeepSprintStopState s = new KeepSprintStopState();
        s.setWalkC03s(1);
        s.beginStop();
        s.onWalkC03(true);
        s.beginStop();
        assertEquals(KeepSprintStopState.Phase.READY, s.getPhase());
    }

    @Test
    public void invalidateReadyReturnsToStopping() {
        KeepSprintStopState s = new KeepSprintStopState();
        s.setWalkC03s(2);
        s.beginStop();
        s.onWalkC03(true);
        s.onWalkC03(true);
        assertEquals(KeepSprintStopState.Phase.READY, s.getPhase());
        s.invalidateReady();
        assertEquals(KeepSprintStopState.Phase.STOPPING, s.getPhase());
        assertTrue(s.shouldDeferAttack());
        assertFalse(s.canOwnAttack());
    }
}
