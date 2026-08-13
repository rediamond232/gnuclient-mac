package gnu.client.module.modules.movement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LongjumpStateTest {

    @Test
    public void startsIdle() {
        LongjumpState s = new LongjumpState();
        assertEquals(LongjumpState.Phase.IDLE, s.getPhase());
        assertTrue(s.canArm());
        assertFalse(s.hasCaptured());
    }

    @Test
    public void armingStartsTimerPulse() {
        LongjumpState s = new LongjumpState();
        assertTrue(s.tryStartArming(3));
        assertEquals(LongjumpState.Phase.ARMING, s.getPhase());
        assertTrue(s.isTimerPulseActive());
        assertFalse(s.canArm());
    }

    @Test
    public void captureOnceOnly() {
        LongjumpState s = new LongjumpState();
        s.tryStartArming(5);
        assertTrue(s.tryCapture());
        assertTrue(s.hasCaptured());
        assertFalse(s.tryCapture());
    }

    @Test
    public void captureRejectedWhenIdle() {
        LongjumpState s = new LongjumpState();
        assertFalse(s.tryCapture());
    }

    @Test
    public void armingExpiresToCooldownWithoutCapture() {
        LongjumpState s = new LongjumpState();
        s.tryStartArming(2);
        s.setCooldownLength(4);
        s.onClientTick();
        assertEquals(LongjumpState.Phase.ARMING, s.getPhase());
        s.onClientTick();
        assertEquals(LongjumpState.Phase.COOLDOWN, s.getPhase());
        assertFalse(s.isTimerPulseActive());
        assertFalse(s.hasCaptured());
    }

    @Test
    public void captureThenReleaseEntersCooldown() {
        LongjumpState s = new LongjumpState();
        s.setCooldownLength(3);
        s.tryStartArming(5);
        assertTrue(s.tryCapture());
        s.beginCooldownAfterCapture();
        assertEquals(LongjumpState.Phase.COOLDOWN, s.getPhase());
        assertFalse(s.isTimerPulseActive());
        s.onClientTick();
        s.onClientTick();
        s.onClientTick();
        assertEquals(LongjumpState.Phase.IDLE, s.getPhase());
        assertTrue(s.canArm());
        assertFalse(s.hasCaptured());
    }

    @Test
    public void resetClearsAll() {
        LongjumpState s = new LongjumpState();
        s.setCooldownLength(10);
        s.tryStartArming(5);
        s.tryCapture();
        s.beginCooldownAfterCapture();
        s.reset();
        assertEquals(LongjumpState.Phase.IDLE, s.getPhase());
        assertFalse(s.hasCaptured());
        assertTrue(s.canArm());
    }
}
