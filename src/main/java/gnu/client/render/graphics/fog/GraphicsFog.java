package gnu.client.render.graphics.fog;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.color.CustomColors;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/**
 * Video-settings fog (start, fancy/fast/off, clear water, void fog) plus pack fog colors.
 */
public final class GraphicsFog {

    private GraphicsFog() {}

    public static void applyDistanceFog(int pass, float farPlane) {
        if (GraphicsModule.fogOff()) {
            GlStateManager.disableFog();
            return;
        }
        GlStateManager.setFog(GL11.GL_LINEAR);
        if (pass == -1) {
            GlStateManager.setFogStart(0f);
            GlStateManager.setFogEnd(farPlane);
        } else {
            GlStateManager.setFogStart(farPlane * GraphicsModule.fogStart());
            GlStateManager.setFogEnd(farPlane);
        }
        if (!GraphicsModule.fogFast() && GLContext.getCapabilities().GL_NV_fog_distance) {
            GL11.glFogi(34138, 34139);
        }
    }

    public static float waterDensity(float vanilla) {
        if (GraphicsModule.clearWater()) {
            return Math.min(vanilla, 0.02f);
        }
        return vanilla;
    }

    public static boolean skipVoidFog() {
        return !GraphicsModule.voidFog();
    }

    public static Vec3 tintFog(Vec3 vanilla) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return vanilla;
        }
        Entity view = mc.getRenderViewEntity();
        BlockPos pos = new BlockPos(view);
        int packed = rgb(vanilla);
        int tinted = CustomColors.fogColor(mc.theWorld, pos, packed);
        if (tinted == packed) {
            return vanilla;
        }
        float[] c = CustomColors.rgb(tinted);
        return new Vec3(c[0], c[1], c[2]);
    }

    public static Vec3 tintSky(Vec3 vanilla) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return vanilla;
        }
        BlockPos pos = new BlockPos(mc.getRenderViewEntity());
        int packed = rgb(vanilla);
        int tinted = CustomColors.skyColor(mc.theWorld, pos, packed);
        if (tinted == packed) {
            return vanilla;
        }
        float[] c = CustomColors.rgb(tinted);
        return new Vec3(c[0], c[1], c[2]);
    }

    public static Vec3 tintUnderwater(Vec3 vanilla) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return vanilla;
        }
        BlockPos pos = new BlockPos(mc.getRenderViewEntity());
        int packed = rgb(vanilla);
        int tinted = CustomColors.underwaterColor(mc.theWorld, pos, packed);
        if (tinted == packed) {
            return vanilla;
        }
        float[] c = CustomColors.rgb(tinted);
        return new Vec3(c[0], c[1], c[2]);
    }

    private static int rgb(Vec3 v) {
        int r = (int) (clamp(v.xCoord) * 255);
        int g = (int) (clamp(v.yCoord) * 255);
        int b = (int) (clamp(v.zCoord) * 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static double clamp(double x) {
        if (x < 0) {
            return 0;
        }
        if (x > 1) {
            return 1;
        }
        return x;
    }
}
