package gnu.client.render.graphics.sky;

import gnu.client.common.GnuLog;
import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;
import gnu.client.render.shaders.ShaderEngine;
import gnu.client.render.terrain.GnuTerrainDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * OptiFine / MCPatcher custom sky layers.
 *
 * @see <a href="https://optifine.readthedocs.io/custom_sky.html">Custom sky</a>
 */
public final class CustomSky {

    private static final List<SkyLayer> OVERWORLD = new ArrayList<SkyLayer>();
    private static final List<SkyLayer> END = new ArrayList<SkyLayer>();
    private static SkyLayer overworldSun;
    private static SkyLayer overworldMoon;
    private static SkyLayer endSun;
    private static SkyLayer endMoon;
    private static boolean loaded;

    private CustomSky() {}

    public static void reload() {
        loaded = true;
        OVERWORLD.clear();
        END.clear();
        overworldSun = null;
        overworldMoon = null;
        endSun = null;
        endMoon = null;
        loadWorld(0, OVERWORLD);
        loadWorld(1, END);
        overworldSun = loadSpecial(0, "sun");
        overworldMoon = loadSpecial(0, "moon_phases");
        endSun = loadSpecial(1, "sun");
        endMoon = loadSpecial(1, "moon_phases");
        GnuLog.log("CustomSky loaded overworld=" + OVERWORLD.size() + " end=" + END.size()
                + " sun=" + (overworldSun != null) + " moon=" + (overworldMoon != null));
    }

    private static void loadWorld(int world, List<SkyLayer> dest) {
        String folder = "sky/world" + world;
        LinkedHashSet<ResourceLocation> files = new LinkedHashSet<ResourceLocation>();
        files.addAll(GraphicsPackRoots.listNumbered(folder, "sky", ".properties"));
        for (ResourceLocation loc : GraphicsPackRoots.listProperties(folder)) {
            String name = loc.getResourcePath();
            int slash = name.lastIndexOf('/');
            String file = slash >= 0 ? name.substring(slash + 1) : name;
            if (file.startsWith("sky") && file.endsWith(".properties")) {
                files.add(loc);
            }
        }
        for (ResourceLocation loc : files) {
            SkyLayer layer = SkyLayer.parse(loc);
            if (layer != null) {
                dest.add(layer);
            }
        }
    }

    private static SkyLayer loadSpecial(int world, String name) {
        ResourceLocation loc = GraphicsPackRoots.find("sky/world" + world + "/" + name + ".properties");
        if (loc == null) {
            return null;
        }
        return SkyLayer.parse(loc);
    }

    public static boolean hasLayers(int dimension) {
        return !layersFor(dimension).isEmpty();
    }

    public static void render(float partialTicks) {
        if (!GraphicsModule.customSky()) {
            return;
        }
        if (!loaded) {
            reload();
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return;
        }
        int dim = mc.theWorld.provider.getDimensionId();
        List<SkyLayer> layers = layersFor(dim);
        if (layers.isEmpty()) {
            return;
        }
        WorldClient world = mc.theWorld;
        long time = world.getWorldTime();
        float celestial = world.getCelestialAngle(partialTicks);
        BlockPos pos = new BlockPos(mc.getRenderViewEntity());
        BiomeGenBase biome = world.getBiomeGenForCoords(pos);
        String biomeName = biome == null ? "" : biome.biomeName;
        int y = pos.getY();
        boolean rain = world.isRaining();
        boolean thunder = world.isThundering();
        String weather = thunder ? "thunder" : (rain ? "rain" : "clear");
        int day = (int) (time / 24000L);

        OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
        GnuTerrainDraw.disableClientStates();
        ShaderEngine.INSTANCE.bindSkyTextured();
        GlStateManager.enableTexture2D();
        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();

        for (SkyLayer layer : layers) {
            float brightness = layer.brightness(time, day, y, biomeName, weather);
            if (brightness <= 0.001f) {
                continue;
            }
            layer.bind(mc);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            applyBlend(layer.blend);
            GlStateManager.color(brightness, brightness, brightness, brightness);
            GlStateManager.pushMatrix();
            if (layer.rotate) {
                rotateLayer(layer, celestial, time);
            }
            drawSkybox();
            GlStateManager.popMatrix();
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.enableFog();
        GlStateManager.popMatrix();
    }

    public static ResourceLocation sunOverride(int dimension) {
        SkyLayer s = dimension == 1 ? endSun : overworldSun;
        return s == null ? null : s.source;
    }

    public static ResourceLocation moonOverride(int dimension) {
        SkyLayer s = dimension == 1 ? endMoon : overworldMoon;
        return s == null ? null : s.source;
    }

    private static List<SkyLayer> layersFor(int dimension) {
        if (dimension == 1) {
            return END;
        }
        if (dimension == -1) {
            return java.util.Collections.emptyList();
        }
        return OVERWORLD;
    }

    private static void applyBlend(String blend) {
        String b = blend == null ? "add" : blend.toLowerCase(Locale.ROOT);
        if ("alpha".equals(b)) {
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        } else if ("multiply".equals(b)) {
            GlStateManager.tryBlendFuncSeparate(774, 771, 1, 0);
        } else if ("subtract".equals(b)) {
            GlStateManager.tryBlendFuncSeparate(0, 769, 1, 0);
        } else if ("replace".equals(b)) {
            GlStateManager.disableBlend();
        } else if ("screen".equals(b) || "dodge".equals(b)) {
            GlStateManager.tryBlendFuncSeparate(1, 1, 1, 0);
        } else if ("burn".equals(b) || "overlay".equals(b)) {
            GlStateManager.tryBlendFuncSeparate(774, 768, 1, 0);
        } else {
            // add (default)
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0);
        }
    }

    /**
     * OptiFine remaps properties {@code axis=x y z} to {@code (z, y, -x)} before
     * {@code glRotatef}. Default {@code 0 0 1} therefore rotates around +X.
     */
    static float[] remapAxis(float ax, float ay, float az) {
        return new float[] { az, ay, -ax };
    }

    private static void rotateLayer(SkyLayer layer, float celestial, long worldTime) {
        float angleDayStart = 0f;
        if (layer.speed != Math.round(layer.speed)) {
            long worldDay = (worldTime + 18000L) / 24000L;
            double anglePerDay = layer.speed % 1.0f;
            angleDayStart = (float) ((worldDay * anglePerDay) % 1.0);
        }
        float[] axis = remapAxis(layer.axisX, layer.axisY, layer.axisZ);
        GlStateManager.rotate(360f * (angleDayStart + celestial * layer.speed),
                axis[0], axis[1], axis[2]);
    }

    /**
     * OptiFine {@code CustomSkyLayer.render}: two setup rotations, then the same
     * Y=-100 quad six times with the 3×2 atlas UVs. A world-space cube with
     * naive face UVs shows seams and looks like a box.
     */
    private static void drawSkybox() {
        GlStateManager.rotate(90f, 1f, 0f, 0f);
        GlStateManager.rotate(-90f, 0f, 0f, 1f);
        renderSide(4);

        GlStateManager.pushMatrix();
        GlStateManager.rotate(90f, 1f, 0f, 0f);
        renderSide(1);
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.rotate(-90f, 1f, 0f, 0f);
        renderSide(0);
        GlStateManager.popMatrix();

        GlStateManager.rotate(90f, 0f, 0f, 1f);
        renderSide(5);
        GlStateManager.rotate(90f, 0f, 0f, 1f);
        renderSide(2);
        GlStateManager.rotate(90f, 0f, 0f, 1f);
        renderSide(3);
    }

    private static void renderSide(int side) {
        double tx = (side % 3) / 3.0;
        double ty = (side / 3) / 2.0;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(-100.0, -100.0, -100.0).tex(tx, ty).endVertex();
        wr.pos(-100.0, -100.0, 100.0).tex(tx, ty + 0.5).endVertex();
        wr.pos(100.0, -100.0, 100.0).tex(tx + 1.0 / 3.0, ty + 0.5).endVertex();
        wr.pos(100.0, -100.0, -100.0).tex(tx + 1.0 / 3.0, ty).endVertex();
        tess.draw();
    }

    static final class SkyLayer {
        final ResourceLocation source;
        final String blend;
        final boolean rotate;
        final float speed;
        final float axisX;
        final float axisY;
        final float axisZ;
        final int startFadeIn;
        final int endFadeIn;
        final int startFadeOut;
        final int endFadeOut;
        final boolean hasFade;
        final List<String> weather;
        final List<String> biomes;
        final String heights;
        final String days;
        final int daysLoop;
        final float transition;

        private float lastBrightness = 1f;

        SkyLayer(ResourceLocation source, String blend, boolean rotate, float speed,
                float axisX, float axisY, float axisZ,
                int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut, boolean hasFade,
                List<String> weather, List<String> biomes, String heights, String days, int daysLoop,
                float transition) {
            this.source = source;
            this.blend = blend;
            this.rotate = rotate;
            this.speed = speed;
            this.axisX = axisX;
            this.axisY = axisY;
            this.axisZ = axisZ;
            this.startFadeIn = startFadeIn;
            this.endFadeIn = endFadeIn;
            this.startFadeOut = startFadeOut;
            this.endFadeOut = endFadeOut;
            this.hasFade = hasFade;
            this.weather = weather;
            this.biomes = biomes;
            this.heights = heights;
            this.days = days;
            this.daysLoop = daysLoop;
            this.transition = transition;
        }

        static SkyLayer parse(ResourceLocation propsLoc) {
            PropertiesFile p = GraphicsPackRoots.loadProperties(propsLoc);
            String src = p.get("source");
            ResourceLocation tex = GraphicsPackRoots.resolveTexture(propsLoc, src);
            if (tex == null) {
                GnuLog.log("CustomSky skip missing texture " + propsLoc + " source=" + src);
                return null;
            }
            String blend = p.get("blend", "add");
            boolean rotate = PropertyValues.parseBoolean(p.get("rotate"), true);
            float speed = PropertyValues.parseFloat(p.get("speed"), 1f);
            float ax = 0f;
            float ay = 0f;
            float az = 1f;
            List<String> axisTok = PropertyValues.parseList(p.get("axis"));
            if (axisTok.size() >= 3) {
                ax = PropertyValues.parseFloat(axisTok.get(0), 0f);
                ay = PropertyValues.parseFloat(axisTok.get(1), 0f);
                az = PropertyValues.parseFloat(axisTok.get(2), 1f);
            }
            boolean hasFade = p.has("startFadeIn") || p.has("endFadeIn")
                    || p.has("startFadeOut") || p.has("endFadeOut");
            int sfi = PropertyValues.parseTimeTicks(p.get("startFadeIn"), 0);
            int efi = PropertyValues.parseTimeTicks(p.get("endFadeIn"), 0);
            int sfo = PropertyValues.parseTimeTicks(p.get("startFadeOut"), 0);
            int efo = PropertyValues.parseTimeTicks(p.get("endFadeOut"), 0);
            if (hasFade && !p.has("startFadeOut")) {
                int vis = wrap(efi - sfi);
                sfo = wrap(efo - vis);
            }
            List<String> weather = PropertyValues.parseList(p.get("weather", "clear"));
            List<String> biomes = PropertyValues.parseList(p.get("biomes"));
            String heights = p.get("heights");
            String days = p.get("days");
            int daysLoop = Math.max(1, PropertyValues.parseInt(p.get("daysLoop"), 8));
            float transition = PropertyValues.parseFloat(p.get("transition"), 1f);
            return new SkyLayer(tex, blend, rotate, speed, ax, ay, az,
                    sfi, efi, sfo, efo, hasFade, weather, biomes, heights, days, daysLoop, transition);
        }

        void bind(Minecraft mc) {
            mc.getTextureManager().bindTexture(source);
        }

        float brightness(long worldTime, int day, int y, String biomeName, String weatherNow) {
            float target = 1f;
            if (!weather.isEmpty() && !weather.contains(weatherNow)) {
                target = 0f;
            }
            if (target > 0f && !biomes.isEmpty()) {
                boolean ok = false;
                String bn = biomeName == null ? "" : biomeName.replace(' ', '_');
                for (String b : biomes) {
                    if (b.equalsIgnoreCase(biomeName) || b.equalsIgnoreCase(bn)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    target = 0f;
                }
            }
            if (target > 0f && heights != null && !PropertyValues.matchesIntRangeList(heights, y)) {
                target = 0f;
            }
            if (target > 0f && days != null && !days.isEmpty()) {
                int d = Math.abs(day) % daysLoop;
                if (!PropertyValues.matchesIntRangeList(days, d)) {
                    target = 0f;
                }
            }
            if (target > 0f && hasFade) {
                target *= fade(worldTime % 24000L);
            }
            float step = transition <= 0f ? 1f : (1f / Math.max(1f, transition * 20f));
            if (target > lastBrightness) {
                lastBrightness = Math.min(target, lastBrightness + step);
            } else if (target < lastBrightness) {
                lastBrightness = Math.max(target, lastBrightness - step);
            }
            return lastBrightness;
        }

        /**
         * Fade envelope from OptiFineDoc: 0→1 between startFadeIn/endFadeIn,
         * 1 until startFadeOut, 1→0 until endFadeOut, else 0.
         */
        float fade(long timeOfDay) {
            int t = (int) timeOfDay;
            if (between(t, startFadeIn, endFadeIn)) {
                return lerp(t, startFadeIn, endFadeIn);
            }
            if (between(t, endFadeIn, startFadeOut)) {
                return 1f;
            }
            if (between(t, startFadeOut, endFadeOut)) {
                return 1f - lerp(t, startFadeOut, endFadeOut);
            }
            return 0f;
        }

        static boolean between(int t, int a, int b) {
            if (a == b) {
                return t == a;
            }
            if (a < b) {
                return t >= a && t < b;
            }
            return t >= a || t < b;
        }

        static float lerp(int t, int a, int b) {
            int span = wrap(b - a);
            if (span == 0) {
                return 1f;
            }
            int d = wrap(t - a);
            return MathHelper.clamp_float(d / (float) span, 0f, 1f);
        }

        static int wrap(int v) {
            int x = v % 24000;
            if (x < 0) {
                x += 24000;
            }
            return x;
        }
    }
}
