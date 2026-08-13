package gnu.client.module.modules.settings;

import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GraphicsModuleTest {

    @Test
    public void fogStartDefaultsToVanilla() {
        GraphicsModule m = new GraphicsModule();
        SliderSetting fogStart = null;
        for (Setting<?> s : m.getSettings()) {
            if ("Fog Start".equals(s.getName())) {
                fogStart = (SliderSetting) s;
            }
        }
        assertNotNull(fogStart);
        assertEquals(0.75f, fogStart.getValue(), 0.001f);
        assertEquals(0.2f, fogStart.getMin(), 0.001f);
        assertEquals(0.8f, fogStart.getMax(), 0.001f);
        assertTrue(GraphicsModule.sky());
        assertTrue(GraphicsModule.sunMoon());
        assertTrue(GraphicsModule.stars());
    }
}
