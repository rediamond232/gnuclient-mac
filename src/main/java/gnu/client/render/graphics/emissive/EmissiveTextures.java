package gnu.client.render.graphics.emissive;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * OptiFine emissive overlays ({@code *_e.png}).
 *
 * @see <a href="https://optifine.readthedocs.io/emissive.html">Emissive textures</a>
 */
public final class EmissiveTextures {

    private static String suffix = "_e";

    private EmissiveTextures() {}

    public static void reload() {
        PropertiesFile p = GraphicsPackRoots.loadProperties("emissive.properties");
        suffix = p.get("suffix.emissive", "_e");
        if (suffix == null || suffix.isEmpty()) {
            suffix = "_e";
        }
    }

    public static String suffix() {
        return suffix;
    }

    public static ResourceLocation overlay(ResourceLocation base) {
        if (!GraphicsModule.emissive() || base == null) {
            return null;
        }
        String path = base.getResourcePath();
        if (!path.endsWith(".png")) {
            return new ResourceLocation(base.getResourceDomain(), path + suffix);
        }
        String over = path.substring(0, path.length() - 4) + suffix + ".png";
        ResourceLocation loc = new ResourceLocation(base.getResourceDomain(), over);
        if (GraphicsPackRoots.exists(Minecraft.getMinecraft() == null
                ? null : Minecraft.getMinecraft().getResourceManager(), loc)) {
            return loc;
        }
        return null;
    }

    public static void drawOverlayQuad(ResourceLocation overlay, float x, float y, float w, float h) {
        if (overlay == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        mc.getTextureManager().bindTexture(overlay);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(0, 1).endVertex();
        wr.pos(x + w, y + h, 0).tex(1, 1).endVertex();
        wr.pos(x + w, y, 0).tex(1, 0).endVertex();
        wr.pos(x, y, 0).tex(0, 0).endVertex();
        tess.draw();
        GlStateManager.enableLighting();
    }
}
