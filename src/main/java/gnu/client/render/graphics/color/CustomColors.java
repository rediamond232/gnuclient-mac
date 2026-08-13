package gnu.client.render.graphics.color;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.io.IOException;
import java.io.InputStream;

/**
 * OptiFine custom colors, colormaps, and lightmaps.
 *
 * @see <a href="https://optifine.readthedocs.io/custom_colors.html">Custom colors</a>
 */
public final class CustomColors {

    private static PropertiesFile colors = PropertiesFile.empty();
    private static int[] grassMap;
    private static int[] foliageMap;
    private static int[] waterMap;
    private static int[] fogMap;
    private static int[] skyMap;
    private static int[] underwaterMap;
    private static int[] lightmapOverworld;
    private static int lightmapWidth;
    private static int lightmapHeight;

    private CustomColors() {}

    public static void reload() {
        colors = GraphicsPackRoots.loadProperties("color.properties");
        grassMap = loadColormap("colormap/grass.png", "textures/colormap/grass.png");
        foliageMap = loadColormap("colormap/foliage.png", "textures/colormap/foliage.png");
        waterMap = loadColormap("colormap/water.png", null);
        fogMap = loadColormap("colormap/fog.png", null);
        skyMap = loadColormap("colormap/sky.png", null);
        underwaterMap = loadColormap("colormap/underwater.png", null);
        loadLightmap();
    }

    private static int[] loadColormap(String optifinePath, String vanillaFallback) {
        ResourceLocation loc = GraphicsPackRoots.find(optifinePath);
        if (loc == null && vanillaFallback != null) {
            loc = new ResourceLocation("minecraft", vanillaFallback);
        }
        return readRgb(loc, 256, 256);
    }

    private static void loadLightmap() {
        lightmapOverworld = null;
        lightmapWidth = 0;
        lightmapHeight = 0;
        ResourceLocation loc = GraphicsPackRoots.find("lightmap/world0.png");
        if (loc == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourceManager() == null) {
            return;
        }
        InputStream in = null;
        try {
            in = mc.getResourceManager().getResource(loc).getInputStream();
            java.awt.image.BufferedImage img = TextureUtil.readBufferedImage(in);
            if (img == null) {
                return;
            }
            lightmapWidth = img.getWidth();
            lightmapHeight = img.getHeight();
            lightmapOverworld = img.getRGB(0, 0, lightmapWidth, lightmapHeight, null, 0, lightmapWidth);
        } catch (Exception ignored) {
            lightmapOverworld = null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static int[] readRgb(ResourceLocation loc, int w, int h) {
        if (loc == null) {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourceManager() == null) {
            return null;
        }
        InputStream in = null;
        try {
            in = mc.getResourceManager().getResource(loc).getInputStream();
            java.awt.image.BufferedImage img = TextureUtil.readBufferedImage(in);
            if (img == null) {
                return null;
            }
            int iw = img.getWidth();
            int ih = img.getHeight();
            int[] data = img.getRGB(0, 0, iw, ih, null, 0, iw);
            if (iw == w && ih == h) {
                return data;
            }
            int[] out = new int[w * h];
            for (int y = 0; y < h; y++) {
                int sy = y * ih / h;
                for (int x = 0; x < w; x++) {
                    int sx = x * iw / w;
                    out[y * w + x] = data[sy * iw + sx];
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static boolean active() {
        return GraphicsModule.customColors();
    }

    public static int colorProperty(String key, int fallback) {
        if (!active() || !colors.has(key)) {
            return fallback;
        }
        return PropertyValues.parseColor(colors.get(key), fallback);
    }

    public static int fogEnd() {
        return colorProperty("fog.end", 0xFF181318);
    }

    public static int skyEnd() {
        return colorProperty("sky.end", 0xFF282828);
    }

    public static int lilyPad() {
        return colorProperty("lilypad", 0xFF208030);
    }

    public static int particleWater() {
        return colorProperty("particle.water", 0xFF334CFF);
    }

    public static int particlePortal() {
        return colorProperty("particle.portal", 0xFFFF4CE5);
    }

    public static float[] rgb(int argb) {
        return new float[] {
                ((argb >> 16) & 255) / 255f,
                ((argb >> 8) & 255) / 255f,
                (argb & 255) / 255f
        };
    }

    public static int biomeColor(int[] map, BiomeGenBase biome, int fallback) {
        if (map == null || biome == null) {
            return fallback;
        }
        double temp = MathHelper.clamp_float(biome.temperature, 0f, 1f);
        double rain = MathHelper.clamp_float(biome.rainfall, 0f, 1f);
        int x = (int) ((1.0 - temp) * 255.0);
        int y = (int) ((1.0 - rain) * 255.0);
        x = MathHelper.clamp_int(x, 0, 255);
        y = MathHelper.clamp_int(y, 0, 255);
        return map[y << 8 | x] | 0xFF000000;
    }

    public static int grassColor(BiomeGenBase biome, int vanilla) {
        if (!active()) {
            return vanilla;
        }
        return biomeColor(grassMap, biome, vanilla);
    }

    public static int foliageColor(BiomeGenBase biome, int vanilla) {
        if (!active()) {
            return vanilla;
        }
        return biomeColor(foliageMap, biome, vanilla);
    }

    public static int waterColor(BiomeGenBase biome, int vanilla) {
        if (!active()) {
            return vanilla;
        }
        if (waterMap != null) {
            return biomeColor(waterMap, biome, vanilla);
        }
        return vanilla;
    }

    public static int fogColor(World world, BlockPos pos, int vanilla) {
        if (!active() || world == null) {
            return vanilla;
        }
        if (world.provider.getDimensionId() == 1) {
            return fogEnd();
        }
        if (fogMap == null) {
            return vanilla;
        }
        return biomeColor(fogMap, world.getBiomeGenForCoords(pos), vanilla);
    }

    public static int skyColor(World world, BlockPos pos, int vanilla) {
        if (!active() || world == null) {
            return vanilla;
        }
        if (world.provider.getDimensionId() == 1) {
            return skyEnd();
        }
        if (skyMap == null) {
            return vanilla;
        }
        return biomeColor(skyMap, world.getBiomeGenForCoords(pos), vanilla);
    }

    public static int underwaterColor(World world, BlockPos pos, int vanilla) {
        if (!active() || underwaterMap == null || world == null) {
            return vanilla;
        }
        return biomeColor(underwaterMap, world.getBiomeGenForCoords(pos), vanilla);
    }

    public static int blockColorMultiplier(IBlockAccess world, BlockPos pos, Block block, int vanilla) {
        if (!active() || world == null || pos == null || block == null) {
            return vanilla;
        }
        BiomeGenBase biome = world.getBiomeGenForCoords(pos);
        Material mat = block.getMaterial();
        if (mat == Material.grass || block == net.minecraft.init.Blocks.grass
                || block == net.minecraft.init.Blocks.tallgrass
                || block == net.minecraft.init.Blocks.double_plant) {
            return grassColor(biome, vanilla);
        }
        if (block == net.minecraft.init.Blocks.leaves || block == net.minecraft.init.Blocks.leaves2
                || block == net.minecraft.init.Blocks.vine) {
            return foliageColor(biome, vanilla);
        }
        if (block == net.minecraft.init.Blocks.waterlily) {
            return lilyPad();
        }
        if (mat == Material.water) {
            return waterColor(biome, vanilla);
        }
        return vanilla;
    }

    public static boolean applyLightmap(int[] dest, float torchFlicker, float partialTicks) {
        if (!active() || lightmapOverworld == null || dest == null || dest.length < 256) {
            return false;
        }
        int w = lightmapWidth;
        int h = lightmapHeight;
        if (w < 16 || h < 16) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        float sun = 1f;
        if (mc != null && mc.theWorld != null) {
            sun = mc.theWorld.getSunBrightness(partialTicks);
        }
        int col = MathHelper.clamp_int((int) (sun * (w - 1)), 0, w - 1);
        for (int block = 0; block < 16; block++) {
            for (int sky = 0; sky < 16; sky++) {
                int y = Math.min(h - 1, sky * h / 16);
                int rgb = lightmapOverworld[y * w + col];
                int torch = block;
                if (torch > 0 && h > 16) {
                    int ty = Math.min(h - 1, 16 + torch * (h - 16) / 15);
                    int trgb = lightmapOverworld[ty * w + Math.min(w - 1, col)];
                    rgb = maxRgb(rgb, trgb);
                }
                dest[sky * 16 + block] = rgb | 0xFF000000;
            }
        }
        return true;
    }

    private static int maxRgb(int a, int b) {
        int r = Math.max((a >> 16) & 255, (b >> 16) & 255);
        int g = Math.max((a >> 8) & 255, (b >> 8) & 255);
        int bl = Math.max(a & 255, b & 255);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
