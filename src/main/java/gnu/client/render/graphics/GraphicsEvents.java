package gnu.client.render.graphics;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.cit.CustomItems;
import gnu.client.render.graphics.ctm.ConnectedTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/**
 * Texture stitch + anisotropic filtering for the block atlas.
 */
public final class GraphicsEvents {

    @SubscribeEvent
    public void onStitchPre(TextureStitchEvent.Pre event) {
        ConnectedTextures.stitch(event);
        CustomItems.stitch(event);
    }

    @SubscribeEvent
    public void onStitchPost(TextureStitchEvent.Post event) {
        applyAnisotropic(event.map);
    }

    public static void applyAnisotropic(TextureMap map) {
        int level = GraphicsModule.anisotropic();
        if (level <= 1 || map == null) {
            return;
        }
        if (!GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        float max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D,
                EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                Math.min(level, max));
    }
}
