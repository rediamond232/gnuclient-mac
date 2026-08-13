package gnu.client.module.modules.settings;

import gnu.client.render.terrain.GnuTerrainRenderer;
import gnu.client.common.OptiFineCompat;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;

/**
 * General client performance settings — optimizations applied to vanilla Minecraft
 * via mixins (not just this mod's overlays). Modeled after the perf toggles in
 * non-cheat clients: particle/weather reduction, entity render distance,
 * smooth-lighting off, and skipping heavy world passes while a GUI covers the screen.
 *
 * <p>These are feature toggles, not an enable-gated module: the module stays enabled
 * and each setting independently turns its mixin behavior on/off.
 *
 * <p>OptiFine is unsupported; custom terrain owns the world mesh/draw path when enabled.
 */
public final class PerformanceModule extends Module {

    public static final String NAME = "Performance";

    private static PerformanceModule instance;

    // Custom terrain (gnuclient-owned world renderer)
    private final BoolSetting customTerrain = addSetting(new BoolSetting("Custom Terrain", true));

    // Particles / weather
    private final BoolSetting reducedParticles = addSetting(new BoolSetting("Reduced Particles", false));
    private final SliderSetting particleLimit = addSetting(
            new SliderSetting("Particle Limit", 800f, 0f, 4000f, 50f));
    private final BoolSetting minimalParticles = addSetting(new BoolSetting("Minimal Particles", false));
    private final BoolSetting clearWeather = addSetting(new BoolSetting("Clear Weather", false));

    // Entities
    private final BoolSetting entityCulling = addSetting(new BoolSetting("Entity Culling", true));
    private final BoolSetting reducedEntityDistance = addSetting(new BoolSetting("Reduced Entity Distance", false));
    private final SliderSetting entityDistance = addSetting(
            new SliderSetting("Entity Distance", 0.75f, 0.1f, 1.0f, 0.05f));

    // World / chunk rendering
    private final BoolSetting reducedRenderDistance = addSetting(new BoolSetting("Reduced Render Distance", false));
    private final SliderSetting renderDistance = addSetting(
            new SliderSetting("Render Distance", 8f, 2f, 16f, 1f));
    private final BoolSetting cloudsOff = addSetting(new BoolSetting("Clouds Off", false));
    private final BoolSetting fastGraphics = addSetting(new BoolSetting("Fast Graphics", false));
    private final BoolSetting forceVbo = addSetting(new BoolSetting("Force VBO", false));
    private final BoolSetting noEntityNames = addSetting(new BoolSetting("No Entity Names", false));
    private final SliderSetting nameTagDistance = addSetting(
            new SliderSetting("Name Tag Distance", 64f, 16f, 128f, 4f));
    private final BoolSetting dynamicRenderDistance = addSetting(new BoolSetting("Dynamic Render Distance", false));
    private final SliderSetting dynamicRenderDistanceMin = addSetting(
            new SliderSetting("Dynamic RD Min", 4f, 2f, 16f, 1f));
    private final BoolSetting entityShadowsOff = addSetting(new BoolSetting("Entity Shadows Off", false));
    private final BoolSetting fboOff = addSetting(new BoolSetting("Disable FBO", false));
    private final BoolSetting viewBobbingOff = addSetting(new BoolSetting("No View Bobbing", false));
    /** When off, FOV stays at the setting value (no sprint/fly/bow zoom) — OptiFine Dynamic FOV replacement. */
    private final BoolSetting dynamicFov = addSetting(new BoolSetting("Dynamic FOV", false));
    private final BoolSetting mipmapsOff = addSetting(new BoolSetting("Disable Mipmaps", false));
    private final BoolSetting limitFps = addSetting(new BoolSetting("Limit FPS", false));
    private final SliderSetting fpsCap = addSetting(
            new SliderSetting("FPS Cap", 120f, 10f, 260f, 5f));
    private final BoolSetting fastChunkLoading = addSetting(new BoolSetting("Fast Chunk Loading", false));
    private final SliderSetting chunkBuildBudget = addSetting(
            new SliderSetting("Chunk Build Budget", 50f, 25f, 100f, 5f));

    // Rendering quality
    private final BoolSetting disableSmoothLighting = addSetting(new BoolSetting("Disable Smooth Lighting", true));
    private final BoolSetting skipWorldWhenGuiOpen = addSetting(new BoolSetting("Skip World When GUI Open", false));
    private final BoolSetting skipCloudsWhenGuiOpen = addSetting(new BoolSetting("Skip Clouds When GUI Open", false));
    private final BoolSetting noHurtCam = addSetting(new BoolSetting("No Hurt Cam", false));
    private final BoolSetting boxDisplayLists = addSetting(new BoolSetting("Box Display Lists", true));

    public PerformanceModule() {
        super(NAME, "General client performance (vanilla MC optimizations)", Category.SETTINGS);
        instance = this;
        entityDistance.visibleWhen(() -> reducedEntityDistance.getValue());
        chunkBuildBudget.visibleWhen(() -> fastChunkLoading.getValue());
        customTerrain.onChanged(() ->
                GnuTerrainRenderer.INSTANCE.onCustomTerrainSettingChanged(customTerrain.isToggled()));
        setEnabled(true);
        OptiFineCompat.warnUnsupportedIfPresent();
    }

    public static PerformanceModule instance() {
        return instance;
    }

    // ---- accessors read by the performance mixins ----

    /**
     * Whether to enlarge vanilla {@code ChunkRenderDispatcher} workers. False when the
     * Performance module is not ready yet (Minecraft may construct the dispatcher before
     * modules register) or when custom terrain owns meshing — avoids a second builder pool.
     */
    public static boolean scaleVanillaChunkWorkers() {
        return instance != null && !instance.customTerrain.isToggled();
    }

    /** When true, gnuclient owns terrain mesh/upload/draw (vanilla chunk draw cancelled). */
    public static boolean customTerrain() {
        return instance != null && instance.customTerrain.isToggled();
    }

    public static boolean reducedParticles() {
        return instance != null && instance.reducedParticles.isToggled();
    }

    public static int particleLimit() {
        return instance == null ? 800 : Math.round(instance.particleLimit.getValue());
    }

    public static boolean clearWeather() {
        return instance != null && instance.clearWeather.isToggled();
    }

    /**
     * Forces {@code GameSettings.particleSetting = 2} (Minimal) so only a small subset of
     * particles render.
     */
    public static boolean minimalParticles() {
        return instance != null && instance.minimalParticles.isToggled();
    }

    public static boolean reducedEntityDistance() {
        return instance != null && instance.reducedEntityDistance.isToggled();
    }

    /**
     * Fraction of vanilla entity render distance ({@code 64} blocks in
     * {@code Entity.isInRangeToRenderDist}). 0.75 → 48 blocks.
     */
    public static float entityDistanceFraction() {
        return instance == null ? 0.75f : instance.entityDistance.getValue();
    }

    /**
     * Strong render-only entity culling (type distance + behind-camera). Does not affect
     * entity ticks, collision, or combat raytraces.
     */
    public static boolean entityCulling() {
        return instance != null && instance.entityCulling.isToggled();
    }

    /**
     * When false, sprint/fly/bow/speed no longer change FOV (OptiFine Dynamic FOV off).
     * Defaults off — vanilla-without-OptiFine still applied the modifier every frame.
     */
    public static boolean dynamicFov() {
        return instance != null && instance.dynamicFov.isToggled();
    }

    public static boolean reducedRenderDistance() {
        return instance != null && instance.reducedRenderDistance.isToggled();
    }

    /** Absolute chunk render distance (in chunks) to clamp the game to. */
    public static int renderDistanceChunks() {
        return instance == null ? 8 : Math.round(instance.renderDistance.getValue());
    }

    public static boolean cloudsOff() {
        return instance != null && instance.cloudsOff.isToggled();
    }

    public static boolean fastGraphics() {
        return instance != null && instance.fastGraphics.isToggled();
    }

    /**
     * Forces {@code GameSettings.useVbo = true}. Always on when custom terrain is enabled.
     */
    public static boolean forceVbo() {
        if (customTerrain()) {
            return true;
        }
        return instance != null && instance.forceVbo.isToggled();
    }

    public static boolean noEntityNames() {
        return instance != null && instance.noEntityNames.isToggled();
    }

    /** Max distance (blocks) at which entity name tags render; beyond it they're skipped. */
    public static float nameTagDistance() {
        return instance == null ? 64f : instance.nameTagDistance.getValue();
    }

    public static boolean dynamicRenderDistance() {
        return instance != null && instance.dynamicRenderDistance.isToggled();
    }

    /** Floor (in chunks) that dynamic render distance will not drop below. */
    public static int dynamicRenderDistanceMin() {
        return instance == null ? 4 : Math.round(instance.dynamicRenderDistanceMin.getValue());
    }

    public static boolean entityShadowsOff() {
        return instance != null && instance.entityShadowsOff.isToggled();
    }

    public static boolean fboOff() {
        return instance != null && instance.fboOff.isToggled();
    }

    public static boolean viewBobbingOff() {
        return instance != null && instance.viewBobbingOff.isToggled();
    }

    public static boolean mipmapsOff() {
        return instance != null && instance.mipmapsOff.isToggled();
    }

    public static boolean limitFps() {
        return instance != null && instance.limitFps.isToggled();
    }

    public static int fpsCap() {
        return instance == null ? 120 : Math.round(instance.fpsCap.getValue());
    }

    /**
     * Divisor applied to the frame time to get the chunk-upload budget in
     * {@code EntityRenderer.updateCameraAndRender}. Vanilla is a hardcoded 4 (a quarter of a
     * frame); the slider expresses the budget as a percentage of the frame, so 50% -> 2 and
     * 100% -> 1. Returning 4 leaves vanilla behavior exactly as-is.
     */
    public static int chunkBuildBudgetDivisor() {
        // Custom terrain uses its own upload pump; still honor Fast Chunk Loading when on.
        if (instance == null || !instance.fastChunkLoading.isToggled()) {
            return customTerrain() ? 2 : 4;
        }
        int percent = Math.round(instance.chunkBuildBudget.getValue());
        if (percent < 1) {
            percent = 1;
        }
        return Math.max(1, 100 / percent);
    }

    public static boolean disableSmoothLighting() {
        // Combat-first custom terrain assumes flat lighting; force AO off when custom terrain is on.
        if (customTerrain()) {
            return true;
        }
        return instance != null && instance.disableSmoothLighting.isToggled();
    }

    public static boolean skipWorldWhenGuiOpen() {
        return instance != null && instance.skipWorldWhenGuiOpen.isToggled();
    }

    public static boolean skipCloudsWhenGuiOpen() {
        return instance != null && instance.skipCloudsWhenGuiOpen.isToggled();
    }

    public static boolean noHurtCam() {
        return instance != null && instance.noHurtCam.isToggled();
    }

    public static boolean boxDisplayLists() {
        return instance == null || instance.boxDisplayLists.isToggled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(true);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}
}
