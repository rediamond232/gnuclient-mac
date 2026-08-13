package gnu.client.render.terrain;

import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GnuBuiltMeshTest {

    @Test
    public void releaseNullsLayerPayloads() {
        ByteBuffer[] layers = new ByteBuffer[4];
        layers[0] = ByteBuffer.allocateDirect(64);
        layers[1] = ByteBuffer.allocate(32);
        int[] counts = new int[] {4, 2, 0, 0};
        GnuBuiltMesh built = new GnuBuiltMesh(SectionKeys.of(0, 0, 0), new BlockPos(0, 0, 0), 1,
                layers, counts);
        assertTrue(built.hasLayer(net.minecraft.util.EnumWorldBlockLayer.SOLID));
        built.release();
        assertNull(layers[0]);
        assertNull(layers[1]);
        assertTrue(counts[0] == 0);
        assertTrue(counts[1] == 0);
        built.release(); // idempotent
    }

    @Test
    public void discardNullsWithoutRequiringDirectFree() {
        ByteBuffer[] layers = new ByteBuffer[4];
        ByteBuffer direct = ByteBuffer.allocateDirect(64);
        layers[0] = direct;
        int[] counts = new int[] {4, 0, 0, 0};
        GnuBuiltMesh built = new GnuBuiltMesh(SectionKeys.of(0, 0, 0), new BlockPos(0, 0, 0), 1,
                layers, counts);
        built.discard();
        assertNull(layers[0]);
        // Buffer object itself is not cleaned — safe for post-upload staging on macOS.
        assertTrue(direct.capacity() > 0);
    }

    @Test
    public void toCompiledChunkCarriesVisibilityAndTesrsWithoutGl() {
        SetVisibility vis = new SetVisibility();
        vis.setAllVisible(true);
        List<TileEntity> tesrs = Collections.emptyList();
        ByteBuffer[] layers = new ByteBuffer[4];
        int[] counts = new int[] {0, 0, 0, 0};
        GnuBuiltMesh built = new GnuBuiltMesh(SectionKeys.of(0, 0, 0), new BlockPos(0, 0, 0), 1,
                layers, counts, vis, tesrs);
        assertSame(vis, built.visibility);
        assertSame(tesrs, built.tileEntities);

        CompiledChunk compiled = built.toCompiledChunk();
        assertTrue(compiled.isVisible(EnumFacing.NORTH, EnumFacing.SOUTH));
        assertTrue(compiled.getTileEntities().isEmpty());

        built.discard();
        assertTrue("discard must not drop vis used by setCompiledChunk",
                built.visibility.isVisible(EnumFacing.NORTH, EnumFacing.SOUTH));
        assertSame(tesrs, built.tileEntities);
    }
}
