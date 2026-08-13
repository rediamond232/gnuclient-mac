package gnu.client.module.modules.movement;

import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FlyModuleTest {

    @Test
    public void defaultSettings() {
        FlyModule m = new FlyModule();
        assertEquals("Fly", m.getName());
        assertEquals(gnu.client.module.Category.PLAYER, m.getCategory());

        ModeSetting mode = null;
        SliderSetting speed = null;
        SliderSetting cooldown = null;
        SliderSetting active = null;
        SliderSetting rest = null;
        for (Setting<?> s : m.getSettings()) {
            switch (s.getName()) {
                case "Mode": mode = (ModeSetting) s; break;
                case "Speed": speed = (SliderSetting) s; break;
                case "SetbackCooldown": cooldown = (SliderSetting) s; break;
                case "ActiveTicks": active = (SliderSetting) s; break;
                case "RestTicks": rest = (SliderSetting) s; break;
                default: break;
            }
        }
        assertNotNull(mode);
        assertNotNull(speed);
        assertNotNull(cooldown);
        assertNotNull(active);
        assertNotNull(rest);

        // Glide default — softer than full hover against Simulation immediate-setback.
        assertEquals(FlySetbackState.MODE_GLIDE, mode.getIndex());
        assertEquals("Glide", mode.getCurrentMode());
        assertEquals(0.05f, speed.getValue(), 0.001f);
        assertEquals(10.0f, cooldown.getValue(), 0.001f);
        assertEquals(2.0f, active.getValue(), 0.001f);
        assertEquals(3.0f, rest.getValue(), 0.001f);
    }

    @Test
    public void setbackStateStartsUnlocked() {
        FlyModule m = new FlyModule();
        assertFalse(m.setbackState().isLocked());
        assertTrue(m.setbackState().canApplyAirControl());
    }

    @Test
    public void suffixShowsMode() {
        FlyModule m = new FlyModule();
        assertEquals("Glide", m.getSuffix()[0]);
    }
}
