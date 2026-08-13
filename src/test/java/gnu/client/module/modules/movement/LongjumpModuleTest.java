package gnu.client.module.modules.movement;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LongjumpModuleTest {

    @Test
    public void defaultSettings() {
        LongjumpModule m = new LongjumpModule();
        assertEquals("Longjump", m.getName());
        assertEquals(gnu.client.module.Category.PLAYER, m.getCategory());

        SliderSetting timerSpeed = null;
        SliderSetting timerTicks = null;
        SliderSetting multiply = null;
        SliderSetting cooldown = null;
        BoolSetting requireBounce = null;
        for (Setting<?> s : m.getSettings()) {
            switch (s.getName()) {
                case "TimerSpeed": timerSpeed = (SliderSetting) s; break;
                case "TimerTicks": timerTicks = (SliderSetting) s; break;
                case "VelocityMultiply": multiply = (SliderSetting) s; break;
                case "RequireBounce": requireBounce = (BoolSetting) s; break;
                case "Cooldown": cooldown = (SliderSetting) s; break;
                default: break;
            }
        }
        assertNotNull(timerSpeed);
        assertNotNull(timerTicks);
        assertNotNull(multiply);
        assertNotNull(requireBounce);
        assertNotNull(cooldown);
        assertEquals(1.8f, timerSpeed.getValue(), 0.001f);
        assertEquals(5.0f, timerTicks.getValue(), 0.001f);
        assertEquals(2.0f, multiply.getValue(), 0.001f);
        assertTrue(requireBounce.getValue());
        assertEquals(30.0f, cooldown.getValue(), 0.001f);
    }
}
