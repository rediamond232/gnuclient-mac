package gnu.client.module.modules.player;

import net.minecraft.item.EnumAction;
import org.junit.Test;
import static org.junit.Assert.*;

public class NoSlowModeTest {
    @Test
    public void grimSlotSpoofAlternatesCurrentAndSwapSlot() {
        assertEquals(1, NoSlowModule.nextGrimSlot(0, 1, true, -1));
        assertEquals(0, NoSlowModule.nextGrimSlot(0, 1, false, 1));
        assertEquals(2, NoSlowModule.nextGrimSlot(1, 1, false, 1));
    }

    @Test
    public void modeIndicesMatchOpenMyau() {
        assertEquals(0, NoSlowModule.MODE_NONE);
        assertEquals(1, NoSlowModule.MODE_VANILLA);
        assertEquals(2, NoSlowModule.MODE_GRIM);
    }

    @Test
    public void swordGrimActiveExcludesFoodOnly() {
        assertTrue(NoSlowModule.isSwordGrimActive(NoSlowModule.MODE_GRIM, true));
        assertFalse(NoSlowModule.isSwordGrimActive(NoSlowModule.MODE_GRIM, false));
        assertFalse(NoSlowModule.isSwordGrimActive(NoSlowModule.MODE_VANILLA, true));
        assertTrue(NoSlowModule.isFoodGrimSelected(NoSlowModule.MODE_GRIM, true));
        assertFalse(NoSlowModule.isFoodGrimSelected(NoSlowModule.MODE_NONE, true));
    }

    @Test
    public void eatingMatchesOpenMyauEnumAction() {
        assertTrue(NoSlowModule.matchesEatingUseAction(EnumAction.EAT, false));
        assertTrue(NoSlowModule.matchesEatingUseAction(EnumAction.DRINK, false));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.EAT, true));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.DRINK, true));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.NONE, false));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.BLOCK, false));
        assertFalse(NoSlowModule.matchesEatingUseAction(EnumAction.BOW, false));
        assertFalse(NoSlowModule.isEatingStack(null));
    }
}
