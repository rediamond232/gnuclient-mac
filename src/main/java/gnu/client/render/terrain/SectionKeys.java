package gnu.client.render.terrain;

import net.minecraft.util.BlockPos;

/**
 * Packs a 16³ section origin into a stable {@code long} key.
 *
 * <p>Layout (non-overlapping): Z 22 bits | Y 20 bits | X 22 bits.
 */
public final class SectionKeys {

    private static final int Z_BITS = 22;
    private static final int Y_BITS = 20;
    private static final int X_BITS = 22;
    private static final int Y_SHIFT = Z_BITS;
    private static final int X_SHIFT = Z_BITS + Y_BITS;
    private static final long Z_MASK = (1L << Z_BITS) - 1L;
    private static final long Y_MASK = (1L << Y_BITS) - 1L;
    private static final long X_MASK = (1L << X_BITS) - 1L;

    private SectionKeys() {}

    public static long ofOrigin(BlockPos origin) {
        return of(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4);
    }

    public static long ofBlock(int blockX, int blockY, int blockZ) {
        return of(blockX >> 4, blockY >> 4, blockZ >> 4);
    }

    public static long of(int sectionX, int sectionY, int sectionZ) {
        return ((sectionX & X_MASK) << X_SHIFT)
                | ((sectionY & Y_MASK) << Y_SHIFT)
                | (sectionZ & Z_MASK);
    }

    public static int sectionX(long key) {
        return signExtend((int) ((key >> X_SHIFT) & X_MASK), X_BITS);
    }

    public static int sectionY(long key) {
        return signExtend((int) ((key >> Y_SHIFT) & Y_MASK), Y_BITS);
    }

    public static int sectionZ(long key) {
        return signExtend((int) (key & Z_MASK), Z_BITS);
    }

    public static BlockPos originOf(long key) {
        return new BlockPos(sectionX(key) << 4, sectionY(key) << 4, sectionZ(key) << 4);
    }

    private static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }
}
