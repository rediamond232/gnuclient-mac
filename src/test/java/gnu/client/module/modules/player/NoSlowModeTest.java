package gnu.client.module.modules.player;

import net.minecraft.item.EnumAction;
import org.junit.Test;
import static org.junit.Assert.*;

public class NoSlowModeTest {
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