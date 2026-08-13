package gnu.client.render.graphics;

import gnu.client.common.GnuLog;
import gnu.client.render.graphics.cit.CustomItems;
import gnu.client.render.graphics.color.CustomColors;
import gnu.client.render.graphics.ctm.ConnectedTextures;
import gnu.client.render.graphics.emissive.EmissiveTextures;
import gnu.client.render.graphics.font.HdFonts;
import gnu.client.render.graphics.grass.BetterGrass;
import gnu.client.render.graphics.lights.DynamicLights;
import gnu.client.render.graphics.natural.NaturalTextures;
import gnu.client.render.graphics.random.RandomEntities;
import gnu.client.render.graphics.sky.CustomSky;
import gnu.client.render.terrain.GnuTerrainRenderer;

/**
 * Reloads OptiFine-format resource-pack features after {@code Minecraft.refreshResources}.
 */
public final class GraphicsReload {

    private GraphicsReload() {}

    public static void reload() {
        try {
            CustomSky.reload();
            CustomColors.reload();
            ConnectedTextures.reload();
            BetterGrass.reload();
            CustomItems.reload();
            RandomEntities.reload();
            EmissiveTextures.reload();
            DynamicLights.reload();
            NaturalTextures.reload();
            HdFonts.reload();
            GnuTerrainRenderer.INSTANCE.markAllDirty();
        } catch (Throwable t) {
            GnuLog.log("Graphics reload failed: " + t);
        }
    }
}
