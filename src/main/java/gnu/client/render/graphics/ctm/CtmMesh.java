package gnu.client.render.graphics.ctm;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;

/**
 * Emits a single BLOCK-format cube face using a CTM sprite (28-byte vanilla vertex).
 */
public final class CtmMesh {

    private CtmMesh() {}

    public static void putFace(WorldRenderer wr, IBlockAccess world, BlockPos pos, EnumFacing face,
            TextureAtlasSprite sprite, int light) {
        float x = pos.getX();
        float y = pos.getY();
        float z = pos.getZ();
        float u0 = sprite.getMinU();
        float u1 = sprite.getMaxU();
        float v0 = sprite.getMinV();
        float v1 = sprite.getMaxV();
        int shade = shade(face);
        int r = shade;
        int g = shade;
        int b = shade;
        int a = 255;
        switch (face) {
            case DOWN:
                vert(wr, x, y, z + 1, u0, v1, r, g, b, a, light);
                vert(wr, x, y, z, u0, v0, r, g, b, a, light);
                vert(wr, x + 1, y, z, u1, v0, r, g, b, a, light);
                vert(wr, x + 1, y, z + 1, u1, v1, r, g, b, a, light);
                break;
            case UP:
                vert(wr, x, y + 1, z, u0, v0, r, g, b, a, light);
                vert(wr, x, y + 1, z + 1, u0, v1, r, g, b, a, light);
                vert(wr, x + 1, y + 1, z + 1, u1, v1, r, g, b, a, light);
                vert(wr, x + 1, y + 1, z, u1, v0, r, g, b, a, light);
                break;
            case NORTH:
                vert(wr, x + 1, y, z, u0, v1, r, g, b, a, light);
                vert(wr, x + 1, y + 1, z, u0, v0, r, g, b, a, light);
                vert(wr, x, y + 1, z, u1, v0, r, g, b, a, light);
                vert(wr, x, y, z, u1, v1, r, g, b, a, light);
                break;
            case SOUTH:
                vert(wr, x, y, z + 1, u0, v1, r, g, b, a, light);
                vert(wr, x, y + 1, z + 1, u0, v0, r, g, b, a, light);
                vert(wr, x + 1, y + 1, z + 1, u1, v0, r, g, b, a, light);
                vert(wr, x + 1, y, z + 1, u1, v1, r, g, b, a, light);
                break;
            case WEST:
                vert(wr, x, y, z, u0, v1, r, g, b, a, light);
                vert(wr, x, y + 1, z, u0, v0, r, g, b, a, light);
                vert(wr, x, y + 1, z + 1, u1, v0, r, g, b, a, light);
                vert(wr, x, y, z + 1, u1, v1, r, g, b, a, light);
                break;
            case EAST:
                vert(wr, x + 1, y, z + 1, u0, v1, r, g, b, a, light);
                vert(wr, x + 1, y + 1, z + 1, u0, v0, r, g, b, a, light);
                vert(wr, x + 1, y + 1, z, u1, v0, r, g, b, a, light);
                vert(wr, x + 1, y, z, u1, v1, r, g, b, a, light);
                break;
            default:
                break;
        }
    }

    private static int shade(EnumFacing face) {
        switch (face) {
            case DOWN:
                return 127;
            case UP:
                return 255;
            case NORTH:
            case SOUTH:
                return 204;
            default:
                return 153;
        }
    }

    private static void vert(WorldRenderer wr, float x, float y, float z, float u, float v,
            int r, int g, int b, int a, int light) {
        wr.pos(x, y, z).color(r, g, b, a).tex(u, v).lightmap(light >> 16 & 65535, light & 65535).endVertex();
    }
}
