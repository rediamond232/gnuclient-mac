package gnu.client.module.modules.movement;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SpeedModuleTest {

    @Test
    public void defaultSettings() {
        SpeedModule m = new SpeedModule();
        assertEquals("Speed", m.getName());
        assertEquals(gnu.client.module.Category.PLAYER, m.getCategory());

        SliderSetting boost = null;
        SliderSetting shrink = null;
        BoolSetting sprint = null;
        BoolSetting jump = null;
        for (Setting<?> s : m.getSettings()) {
            switch (s.getName()) {
                case "Boost": boost = (SliderSetting) s; break;
                case "Shrink box": shrink = (SliderSetting) s; break;
                case "Auto sprint": sprint = (BoolSetting) s; break;
                case "Auto jump": jump = (BoolSetting) s; break;
                default: break;
            }
        }
        assertNotNull(boost);
        assertNotNull(shrink);
        assertNotNull(sprint);
        assertNotNull(jump);

        assertEquals(0.08f, boost.getValue(), 0.001f);
        assertEquals(0.25f, shrink.getValue(), 0.001f);
        assertTrue(sprint.getValue());
        assertTrue(jump.getValue());
    }

    @Test
    public void pushDirForwardAtYawZero() {
        double[] dir = SpeedModule.pushDir(0.0f, 0.08);
        assertEquals(0.0, dir[0], 0.001);
        assertEquals(0.08, dir[1], 0.001);
    }

    @Test
    public void pushDirLeftAtYawNinety() {
        double[] dir = SpeedModule.pushDir(90.0f, 0.08);
        assertEquals(-0.08, dir[0], 0.001);
        assertEquals(0.0, dir[1], 0.001);
    }

    @Test
    public void pushDirScalesWithEntities() {
        double[] one = SpeedModule.pushDir(0.0f, 0.08);
        double[] three = SpeedModule.pushDir(0.0f, 0.24);
        assertEquals(3.0, three[1] / one[1], 0.001);
    }

    @Test
    public void pushDirZeroBoost() {
        double[] dir = SpeedModule.pushDir(45.0f, 0.0);
        assertEquals(0.0, dir[0], 0.001);
        assertEquals(0.0, dir[1], 0.001);
    }
}
