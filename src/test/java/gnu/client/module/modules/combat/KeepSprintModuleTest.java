package gnu.client.module.modules.combat;

import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KeepSprintModuleTest {

    @Test
    public void defaultSettings() {
        KeepSprintModule m = new KeepSprintModule();
        assertEquals("KeepSprint", m.getName());
        SliderSetting walk = null;
        BoolSetting debug = null;
        for (Setting<?> s : m.getSettings()) {
            if ("WalkC03s".equals(s.getName()))
                walk = (SliderSetting) s;
            if ("Debug".equals(s.getName()))
                debug = (BoolSetting) s;
            if ("SprintGap".equals(s.getName()) || "HitsPerWindow".equals(s.getName()))
                throw new AssertionError("duty-cycle setting should be removed: " + s.getName());
        }
        assertNotNull(walk);
        assertEquals(2f, walk.getValue(), 0.01f);
        assertFalse(walk.isVisible());
        assertNotNull(debug);
        assertFalse(debug.getValue());
    }

    @Test
    public void defaultModeIsPrediction() {
        KeepSprintModule m = new KeepSprintModule();
        ModeSetting mode = m.mode;
        assertNotNull(mode);
        assertEquals("Prediction", mode.getCurrentMode());
    }

    @Test
    public void walkC03sAlwaysHidden() {
        KeepSprintModule m = new KeepSprintModule();
        SliderSetting walk = null;
        for (Setting<?> s : m.getSettings()) {
            if ("WalkC03s".equals(s.getName()))
                walk = (SliderSetting) s;
        }
        assertNotNull(walk);
        assertFalse(walk.isVisible());
        m.mode.setValue(1);
        assertEquals("Grim", m.mode.getCurrentMode());
        assertFalse(walk.isVisible());
        m.mode.setValue(0);
        assertEquals("Prediction", m.mode.getCurrentMode());
        assertFalse(walk.isVisible());
    }

    @Test
    public void modeSettingOffersPredictionAndGrim() {
        KeepSprintModule m = new KeepSprintModule();
        assertEquals(Arrays.asList("Prediction", "Grim"), m.mode.getModes());
    }

    @Test
    public void moduleDescriptionDocumentsBothModes() {
        KeepSprintModule m = new KeepSprintModule();
        String desc = m.getDescription();
        assertTrue(desc.contains("Prediction"));
        assertTrue(desc.contains("Grim"));
        assertTrue(desc.contains("×0.6") || desc.toLowerCase().contains("0.6"));
        assertTrue(desc.contains("AttackSlow") || desc.contains("Simulation"));
    }
}
