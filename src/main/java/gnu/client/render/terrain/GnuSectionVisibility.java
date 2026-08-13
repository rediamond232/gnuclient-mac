package gnu.client.render.terrain;

import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.util.BlockPos;

/**
 * Thin wrapper around vanilla {@link VisGraph} so the SRG opaque-mark call lives in one place.
 */
public final class GnuSectionVisibility {

    private GnuSectionVisibility() {}

    /** Same call vanilla {@code RenderChunk.rebuildChunk} uses for opaque cubes. */
    public static void markOpaque(VisGraph graph, BlockPos pos) {
        graph.func_178606_a(pos);
    }

    public static SetVisibility compute(VisGraph graph) {
        return graph.computeVisibility();
    }
}
