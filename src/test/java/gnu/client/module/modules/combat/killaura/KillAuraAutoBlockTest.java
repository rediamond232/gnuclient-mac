package gnu.client.module.modules.combat.killaura;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillAuraAutoBlockTest {

    @Test
    public void fakeModeIndexIsEight() {
        assertEquals(8, KillAuraAutoBlock.FAKE);
    }

    @Test
    public void watchdog2AndHypixel3ModeIndicesMatchReference() {
        assertEquals(10, KillAuraAutoBlock.WATCHDOG2);
        assertEquals(11, KillAuraAutoBlock.HYPIXEL3);
    }

    @Test
    public void attackWhileBlockingAllowedOnlyForVanillaGrimWatchdog2Hypixel3() {
        assertTrue(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.VANILLA));
        assertTrue(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.GRIM));
        assertTrue(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.WATCHDOG2));
        assertTrue(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.HYPIXEL3));

        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.NONE));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.SPOOF));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.HYPIXEL));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.BLINK));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.INTERACT));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.SWAP));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.LEGIT));
        assertFalse(KillAuraAutoBlock.isAttackAllowedWhileBlocking(KillAuraAutoBlock.FAKE));
    }

    @Test
    public void shouldAutoBlockModesMatchReference() {
        assertFalse(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.NONE));
        assertFalse(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.VANILLA));
        assertFalse(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.SPOOF));
        assertFalse(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.FAKE));

        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.HYPIXEL));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.BLINK));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.INTERACT));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.SWAP));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.LEGIT));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.GRIM));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.WATCHDOG2));
        assertTrue(KillAuraAutoBlock.isShouldAutoBlockMode(KillAuraAutoBlock.HYPIXEL3));
    }

    @Test
    public void watchdog2HoldDelayUsesAutoBlockCps() {
        assertEquals(125L, KillAuraAutoBlock.watchdog2HoldDelayMs(8.0f));
        assertEquals(166L, KillAuraAutoBlock.watchdog2HoldDelayMs(0.0f));
    }
}
