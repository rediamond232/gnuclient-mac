package gnu.client.module.modules.settings;

import gnu.client.module.Category;
import gnu.client.module.KeybindAction;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.ui.UiFont;

import org.lwjgl.input.Keyboard;

import java.util.Arrays;

/**
 * Settings-tab entry for the in-game ClickGUI. The bound key opens
 * {@link gnu.client.ui.clickgui.ClickGuiScreen}; this module is never enabled as a feature.
 */
public final class ClickGuiModule extends Module {

    public static final String NAME = "ClickGUI";

    private static ClickGuiModule instance;

    private final ModeSetting font = addSetting(new ModeSetting("Font", 0,
            Arrays.asList("Modern", "Minecraft")));
    private final BoolSetting blur = addSetting(new BoolSetting("Blur", true));
    private final SliderSetting animationSpeed = addSetting(
            new SliderSetting("Animation speed", 1.0f, 0.5f, 2.0f, 0.05f));
    private final SliderSetting panelOpacity = addSetting(
            new SliderSetting("Panel opacity", 0.84f, 0.4f, 1.0f, 0.01f));
    private final SliderSetting scale = addSetting(
            new SliderSetting("Scale", 1.0f, 0.75f, 1.50f, 0.05f));

    public ClickGuiModule() {
        super(NAME, "Open the in-game ClickGUI menu", Category.SETTINGS);
        setKeyCode(Keyboard.KEY_INSERT);
        instance = this;
    }

    public static ClickGuiModule instance() {
        return instance;
    }

    public ModeSetting getFontSetting() {
        return font;
    }

    public BoolSetting getBlurSetting() {
        return blur;
    }

    public SliderSetting getAnimationSpeedSetting() {
        return animationSpeed;
    }

    public SliderSetting getPanelOpacitySetting() {
        return panelOpacity;
    }

    public SliderSetting getScaleSetting() {
        return scale;
    }

    public UiFont.Mode resolveFontMode() {
        return "Minecraft".equalsIgnoreCase(font.getCurrentMode())
                ? UiFont.Mode.MINECRAFT
                : UiFont.Mode.MODERN;
    }

    public boolean isBlurEnabled() {
        return blur.isToggled();
    }

    public float getAnimationSpeed() {
        return animationSpeed.getValue();
    }

    public float getPanelOpacity() {
        return panelOpacity.getValue();
    }

    public float getScale() {
        return scale.getValue();
    }

    public static float resolveScale() {
        ClickGuiModule gui = instance();
        return gui != null ? gui.getScale() : 1.0f;
    }

    @Override
    public KeybindAction getKeybindAction() {
        return KeybindAction.MENU;
    }

    @Override
    public void toggle() {
        ClientBootstrap.toggleMenu();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled)
            return;
        super.setEnabled(false);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}
}
