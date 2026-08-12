package gnu.client.module.modules.combat.velocity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HypixelVelocityTest {

    @Test
    public void delaysUnlessSetbackGrace() {
        assertTrue(HypixelVelocity.shouldDelaySelfVelocity(false));
        assertFalse(HypixelVelocity.shouldDelaySelfVelocity(true));
    }

    @Test
    public void queueRules() {
        assertFalse(HypixelVelocity.shouldQueue(false, false, false));
        assertTrue(HypixelVelocity.shouldQueue(false, true, false));
        assertFalse(HypixelVelocity.shouldQueue(false, false, true));
        assertTrue(HypixelVelocity.shouldQueue(true, false, true));
        assertFalse(HypixelVelocity.shouldQueue(true, false, false));
    }

    @Test
    public void restoresSprintOnlyWhileHurt() {
        assertTrue(HypixelVelocity.shouldRestoreSprintAfterAttack(10));
        assertFalse(HypixelVelocity.shouldRestoreSprintAfterAttack(0));
    }

    @Test
    public void constants() {
        assertEquals(5, HypixelVelocity.DELAY_TICKS);
        assertEquals(3, HypixelVelocity.SETBACK_GRACE_TICKS);
    }
}
