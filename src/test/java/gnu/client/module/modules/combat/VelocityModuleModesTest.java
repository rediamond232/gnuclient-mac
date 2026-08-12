package gnu.client.module.modules.combat;

import gnu.client.module.modules.combat.velocity.VelocityMode;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VelocityModuleModesTest {

    @Test
    public void registersTwentySevenModesAndDefaultsToStandard() {
        VelocityModule v = new VelocityModule();
        ModeSetting mode = null;
        for (Setting<?> s : v.getSettings()) {
            if ("Mode".equals(s.getName()))
                mode = (ModeSetting) s;
        }
        assertNotNull(mode);
        assertEquals(27, mode.getModes().size());
        assertEquals("Standard", mode.getCurrentMode());
        assertEquals(7, mode.getIndex());

        VelocityMode active = v.getActiveMode();
        assertNotNull(active);
        assertEquals("Standard", active.getName());
    }

    @Test
    public void standardSettingsVisibleOnlyOnStandardFamily() {
        VelocityModule v = new VelocityModule();
        ModeSetting mode = null;
        Setting<?> horizontal = null;
        for (Setting<?> s : v.getSettings()) {
            if ("Mode".equals(s.getName()))
                mode = (ModeSetting) s;
            else if ("Horizontal".equals(s.getName()) || "horizontal".equals(s.getName()))
                horizontal = s;
        }
        assertNotNull(mode);
        assertNotNull(horizontal);

        mode.setValue(7); // Standard
        assertTrue(horizontal.isVisible());

        mode.setValue(6); // JumpReset
        assertTrue(!horizontal.isVisible());
    }
}
