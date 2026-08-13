package gnu.client.render.terrain;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

/**
 * Shared GL client-array setup matching vanilla {@code VboRenderList} for BLOCK format (28 bytes).
 */
public final class GnuTerrainDraw {

    private GnuTerrainDraw() {}

    public static void enableClientStates() {
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
    }

    public static void disableClientStates() {
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        // Sky/star VBOs only enable VERTEX_ARRAY; leftover COLOR/TEXCOORD from terrain makes
        // stars draw as black/colored cubes on the next frame.
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    public static void setupArrayPointers() {
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 28, 0L);
        GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, 12L);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 28, 16L);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glTexCoordPointer(2, GL11.GL_SHORT, 28, 24L);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
    }
}
