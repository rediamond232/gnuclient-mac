package gnu.client.module.modules.settings;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.ColorSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.ui.ClientTheme;

/**
 * Dual client accent colors & customizable fade engine.
 */
public final class ThemeModule extends Module {

    private static ThemeModule instance;

    private final ModeSetting preset = addSetting(new ModeSetting("Preset", "Custom",
            "Custom", "Classic Opal", "Ocean", "Sunset", "Cyberpunk", "Emerald", "Monochrome"));
    private final ModeSetting fadeMode = addSetting(new ModeSetting("Fade Mode", "Wave",
            "Wave", "Gradient", "Static Blend", "Color 1", "Color 2"));
    private final SliderSetting fadeSpeed = addSetting(new SliderSetting("Fade Speed", 1.0f, 0.2f, 3.0f));

    private final ColorSetting color1 = addSetting(new ColorSetting("Client Color 1", ClientTheme.DEFAULT_COLOR1));
    private final ColorSetting color2 = addSetting(new ColorSetting("Client Color 2", ClientTheme.DEFAULT_COLOR2));

    public ThemeModule() {
        super("Theme", "Client Color 1 / Color 2 accents for HUD and GUI", Category.SETTINGS);
        instance = this;
        setHidden(true);
        setEnabled(true);
        color1.visibleWhen(() -> "Custom".equalsIgnoreCase(preset.getCurrentMode()));
        color2.visibleWhen(() -> "Custom".equalsIgnoreCase(preset.getCurrentMode()));
    }

    public static ThemeModule instance() {
        return instance;
    }

    public ColorSetting getColor1() {
        return color1;
    }

    public ColorSetting getColor2() {
        return color2;
    }

    public ModeSetting getPreset() {
        return preset;
    }

    public String getFadeMode() {
        return fadeMode.getCurrentMode();
    }

    public float getFadeSpeed() {
        return fadeSpeed.getValue();
    }

    public int getResolvedColor1() {
        String p = preset.getCurrentMode();
        if ("Classic Opal".equalsIgnoreCase(p)) return 0xFF3A86FF;
        if ("Ocean".equalsIgnoreCase(p)) return 0xFF1E3A8A;
        if ("Sunset".equalsIgnoreCase(p)) return 0xFFF43F5E;
        if ("Cyberpunk".equalsIgnoreCase(p)) return 0xFFA855F7;
        if ("Emerald".equalsIgnoreCase(p)) return 0xFF059669;
        if ("Monochrome".equalsIgnoreCase(p)) return 0xFFE2E8F0;
        return color1.getRgb();
    }

    public int getResolvedColor2() {
        String p = preset.getCurrentMode();
        if ("Classic Opal".equalsIgnoreCase(p)) return 0xFF00F5D4;
        if ("Ocean".equalsIgnoreCase(p)) return 0xFF06B6D4;
        if ("Sunset".equalsIgnoreCase(p)) return 0xFFF59E0B;
        if ("Cyberpunk".equalsIgnoreCase(p)) return 0xFFEC4899;
        if ("Emerald".equalsIgnoreCase(p)) return 0xFF10B981;
        if ("Monochrome".equalsIgnoreCase(p)) return 0xFF64748B;
        return color2.getRgb();
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {
        // Theme stays available via defaults; re-enable so accents keep resolving.
        setEnabled(true);
    }
}
