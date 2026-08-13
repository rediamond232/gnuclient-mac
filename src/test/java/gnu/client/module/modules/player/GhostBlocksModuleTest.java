package gnu.client.module.modules.player;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GhostBlocksModuleTest {

    @Test
    public void defaultSettings() {
        GhostBlocksModule m = new GhostBlocksModule();
        assertEquals("Ghost Blocks", m.getName());

        SliderSetting red = null, green = null, blue = null, alpha = null, max = null;
        BoolSetting remove = null, require = null;
        for (Setting<?> s : m.getSettings()) {
            switch (s.getName()) {
                case "Red": red = (SliderSetting) s; break;
                case "Green": green = (SliderSetting) s; break;
                case "Blue": blue = (SliderSetting) s; break;
                case "Opacity": alpha = (SliderSetting) s; break;
                case "Max blocks": max = (SliderSetting) s; break;
                case "Right click remove": remove = (BoolSetting) s; break;
                case "Require block": require = (BoolSetting) s; break;
                default: break;
            }
        }
        assertNotNull(red);
        assertNotNull(green);
        assertNotNull(blue);
        assertNotNull(alpha);
        assertNotNull(max);
        assertNotNull(remove);
        assertNotNull(require);

        assertEquals(90.0f, red.getValue(), 0.001f);
        assertEquals(220.0f, green.getValue(), 0.001f);
        assertEquals(255.0f, blue.getValue(), 0.001f);
        assertEquals(64.0f, max.getValue(), 0.001f);
        assertTrue(remove.getValue());
        assertTrue(require.getValue());
    }

    @Test
    public void inactiveByDefaultAndNoCollision() {
        GhostBlocksModule m = new GhostBlocksModule();
        assertFalse(GhostBlocksModule.isActive());
        List<AxisAlignedBB> out = new ArrayList<>();
        GhostBlocksModule.addCollisionBoxes(out, new AxisAlignedBB(0, 0, 0, 1, 1, 1));
        assertTrue(out.isEmpty());
    }
}
