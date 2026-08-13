package gnu.client.module.modules.settings;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.render.graphics.GraphicsReload;
import gnu.client.render.terrain.GnuTerrainRenderer;

/**
 * In-client OptiFine quality toggles. Always on; each setting independently enables
 * a graphics feature. Resource-pack formats follow public OptiFineDoc.
 */
public final class GraphicsModule extends Module {

    public static final String NAME = "Graphics";

    private static GraphicsModule instance;

    private final BoolSetting customSky = addSetting(new BoolSetting("Custom Sky", true));
    private final BoolSetting customFog = addSetting(new BoolSetting("Custom Fog", true));
    private final ModeSetting fogMode = addSetting(new ModeSetting("Fog", "Fancy", "Fancy", "Fast", "Off"));
    private final SliderSetting fogStart = addSetting(
            new SliderSetting("Fog Start", 0.75f, 0.2f, 0.8f, 0.05f));
    private final BoolSetting clearWater = addSetting(new BoolSetting("Clear Water", false));
    private final BoolSetting voidFog = addSetting(new BoolSetting("Void Fog", false));
    private final BoolSetting sky = addSetting(new BoolSetting("Sky", true));
    private final BoolSetting sunMoon = addSetting(new BoolSetting("Sun & Moon", true));
    private final BoolSetting stars = addSetting(new BoolSetting("Stars", true));
    private final BoolSetting customColors = addSetting(new BoolSetting("Custom Colors", true));
    private final BoolSetting connectedTextures = addSetting(new BoolSetting("Connected Textures", true));
    private final BoolSetting betterGrass = addSetting(new BoolSetting("Better Grass", false));
    private final BoolSetting betterSnow = addSetting(new BoolSetting("Better Snow", false));
    private final BoolSetting dynamicLights = addSetting(new BoolSetting("Dynamic Lights", false));
    private final BoolSetting customItems = addSetting(new BoolSetting("Custom Items", true));
    private final BoolSetting randomEntities = addSetting(new BoolSetting("Random Entities", true));
    private final BoolSetting emissive = addSetting(new BoolSetting("Emissive Textures", true));
    private final BoolSetting naturalTextures = addSetting(new BoolSetting("Natural Textures", false));
    private final BoolSetting hdFonts = addSetting(new BoolSetting("HD Fonts", true));
    private final BoolSetting customAnimations = addSetting(new BoolSetting("Custom Animations", true));
    private final BoolSetting smoothFps = addSetting(new BoolSetting("Smooth FPS", false));
    private final BoolSetting fastMath = addSetting(new BoolSetting("Fast Math", false));
    private final SliderSetting anisotropic = addSetting(
            new SliderSetting("Anisotropic Filter", 1f, 1f, 16f, 1f));
    private final SliderSetting antialiasing = addSetting(
            new SliderSetting("Antialiasing", 0f, 0f, 16f, 2f));

    public GraphicsModule() {
        super(NAME, "OptiFine-style skies, fog, textures, and quality toggles", Category.SETTINGS);
        instance = this;
        fogStart.visibleWhen(() -> !"Off".equals(fogMode.getCurrentMode()));
        customSky.onChanged(GraphicsReload::reload);
        customColors.onChanged(GraphicsReload::reload);
        connectedTextures.onChanged(() -> {
            GraphicsReload.reload();
            GnuTerrainRenderer.INSTANCE.markAllDirty();
        });
        betterGrass.onChanged(() -> GnuTerrainRenderer.INSTANCE.markAllDirty());
        naturalTextures.onChanged(() -> GnuTerrainRenderer.INSTANCE.markAllDirty());
        setEnabled(true);
    }

    public static GraphicsModule instance() {
        return instance;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(true);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    public static boolean customSky() {
        return instance != null && instance.customSky.isToggled();
    }

    public static boolean customFog() {
        return instance != null && instance.customFog.isToggled();
    }

    public static String fogMode() {
        return instance == null ? "Fancy" : instance.fogMode.getCurrentMode();
    }

    public static boolean fogOff() {
        return "Off".equals(fogMode());
    }

    public static boolean fogFast() {
        return "Fast".equals(fogMode());
    }

    public static float fogStart() {
        return instance == null ? 0.75f : instance.fogStart.getValue();
    }

    public static boolean clearWater() {
        return instance != null && instance.clearWater.isToggled();
    }

    public static boolean voidFog() {
        return instance != null && instance.voidFog.isToggled();
    }

    public static boolean sky() {
        return instance == null || instance.sky.isToggled();
    }

    public static boolean sunMoon() {
        return instance == null || instance.sunMoon.isToggled();
    }

    public static boolean stars() {
        return instance == null || instance.stars.isToggled();
    }

    public static boolean customColors() {
        return instance != null && instance.customColors.isToggled();
    }

    public static boolean connectedTextures() {
        return instance != null && instance.connectedTextures.isToggled();
    }

    public static boolean betterGrass() {
        return instance != null && instance.betterGrass.isToggled();
    }

    public static boolean betterSnow() {
        return instance != null && instance.betterSnow.isToggled();
    }

    public static boolean dynamicLights() {
        return instance != null && instance.dynamicLights.isToggled();
    }

    public static boolean customItems() {
        return instance != null && instance.customItems.isToggled();
    }

    public static boolean randomEntities() {
        return instance != null && instance.randomEntities.isToggled();
    }

    public static boolean emissive() {
        return instance != null && instance.emissive.isToggled();
    }

    public static boolean naturalTextures() {
        return instance != null && instance.naturalTextures.isToggled();
    }

    public static boolean hdFonts() {
        return instance != null && instance.hdFonts.isToggled();
    }

    public static boolean customAnimations() {
        return instance != null && instance.customAnimations.isToggled();
    }

    public static boolean smoothFps() {
        return instance != null && instance.smoothFps.isToggled();
    }

    public static boolean fastMath() {
        return instance != null && instance.fastMath.isToggled();
    }

    public static int anisotropic() {
        return instance == null ? 1 : Math.round(instance.anisotropic.getValue());
    }

    public static int antialiasing() {
        return instance == null ? 0 : Math.round(instance.antialiasing.getValue());
    }
}
