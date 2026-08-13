package gnu.client.module.modules.movement;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SpiderModuleTest {

    @Test
    public void defaultSettings() {
        SpiderModule m = new SpiderModule();
        assertEquals("Spider", m.getName());
        assertEquals(gnu.client.module.Category.PLAYER, m.getCategory());

        SliderSetting offset = null;
        BoolSetting jump = null;
        for (Setting<?> s : m.getSettings()) {
            switch (s.getName()) {
                case "Wall offset": offset = (SliderSetting) s; break;
                case "Require jump": jump = (BoolSetting) s; break;
                default: break;
            }
        }
        assertNotNull(offset);
        assertNotNull(jump);

        assertEquals(0.08f, offset.getValue(), 0.001f);
        assertTrue(jump.getValue());
    }

    @Test
    public void pushDirectionForwardAtYawZero() {
        double[] dir = SpiderModule.pushDirection(0.0f, 1.0f, 0.0f);
        assertEquals(0.0, dir[0], 0.001);
        assertEquals(1.0, dir[1], 0.001);
    }

    @Test
    public void pushDirectionForwardAtYawNinety() {
        double[] dir = SpiderModule.pushDirection(90.0f, 1.0f, 0.0f);
        assertEquals(-1.0, dir[0], 0.001);
        assertEquals(0.0, dir[1], 0.001);
    }

    @Test
    public void pushDirectionStrafeRightAtYawZero() {
        double[] dir = SpiderModule.pushDirection(0.0f, 0.0f, 1.0f);
        assertEquals(1.0, dir[0], 0.001);
        assertEquals(0.0, dir[1], 0.001);
    }

    @Test
    public void pushDirectionDiagonalNormalized() {
        double[] dir = SpiderModule.pushDirection(0.0f, 1.0f, 1.0f);
        double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
        assertEquals(1.0, len, 0.001);
        assertEquals(0.707, dir[0], 0.001);
        assertEquals(0.707, dir[1], 0.001);
    }

    @Test
    public void pushDirectionBackwardsAtYawOneEighty() {
        double[] dir = SpiderModule.pushDirection(180.0f, 1.0f, 0.0f);
        assertEquals(0.0, dir[0], 0.001);
        assertEquals(-1.0, dir[1], 0.001);
    }

    @Test
    public void pushDirectionNullWithoutInput() {
        assertNull(SpiderModule.pushDirection(0.0f, 0.0f, 0.0f));
    }
}
