package gnu.client.module.modules.combat.velocity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HypixelVelocityTest {

    @Test
    public void delaysOnlyInCombatAndNotInSetbackGrace() {
        assertTrue(HypixelVelocity.shouldDelaySelfVelocity(false, true));
        assertFalse(HypixelVelocity.shouldDelaySelfVelocity(true, true));
        assertFalse(HypixelVelocity.shouldDelaySelfVelocity(false, false));
        assertFalse(HypixelVelocity.shouldDelaySelfVelocity(true, false));
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
    public void wallAbsorbOnlyWhileHurtNearSolid() {
        assertTrue(HypixelVelocity.shouldWallAbsorb(5, 8, true));
        assertFalse(HypixelVelocity.shouldWallAbsorb(5, 8, false));
        assertFalse(HypixelVelocity.shouldWallAbsorb(5, 0, true));
        assertFalse(HypixelVelocity.shouldWallAbsorb(0, 8, true));
    }

    @Test
    public void constants() {
        assertEquals(5, HypixelVelocity.DELAY_TICKS);
        assertEquals(3, HypixelVelocity.SETBACK_GRACE_TICKS);
        assertEquals(0.35D, HypixelVelocity.WALL_ABSORB, 0.0);
        assertEquals(6.0D, HypixelVelocity.COMBAT_RANGE, 0.0);
    }
}
