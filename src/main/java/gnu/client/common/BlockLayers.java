package gnu.client.common;

import net.minecraft.util.EnumWorldBlockLayer;

/**
 * Shared, pre-cloned {@code EnumWorldBlockLayer.values()} array.
 *
 * <p>{@code Enum.values()} clones its backing array on every call. Vanilla's
 * {@code RenderChunk.rebuildChunk} calls it once per block inside the 16x16x16 loop, so a
 * single chunk rebuild allocates ~4096 throwaway 4-element arrays; at a few hundred chunk
 * rebuilds per second while flying that is pure garbage.
 *
 * <p>The array is shared, so callers must treat it as read-only. Vanilla and Forge only
 * read from it ({@code arraylength} / index loads), which is why sharing is safe here.
 */
public final class BlockLayers {

    private static final EnumWorldBlockLayer[] VALUES = EnumWorldBlockLayer.values();

    private BlockLayers() {}

    /** The shared layer array. Do not mutate. */
    public static EnumWorldBlockLayer[] values() {
        return VALUES;
    }
}
