package gnu.client.render.terrain;

import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GnuSectionVisibilityTest {

    @Test
    public void emptySectionIsFullyVisible() {
        VisGraph graph = new VisGraph();
        SetVisibility vis = GnuSectionVisibility.compute(graph);
        for (EnumFacing from : EnumFacing.values()) {
            for (EnumFacing to : EnumFacing.values()) {
                assertTrue(from + " -> " + to, vis.isVisible(from, to));
            }
        }
    }

    @Test
    public void solidSectionHasNoThroughVisibility() {
        VisGraph graph = new VisGraph();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    GnuSectionVisibility.markOpaque(graph, pos.set(x, y, z));
                }
            }
        }
        SetVisibility vis = GnuSectionVisibility.compute(graph);
        for (EnumFacing from : EnumFacing.values()) {
            for (EnumFacing to : EnumFacing.values()) {
                assertFalse(from + " -> " + to, vis.isVisible(from, to));
            }
        }
    }
}
