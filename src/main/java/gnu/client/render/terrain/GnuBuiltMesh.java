package gnu.client.render.terrain;

import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

/**
 * Worker-thread mesh result ready for main-thread VBO upload.
 */
public final class GnuBuiltMesh {

    public final long key;
    public final BlockPos origin;
    public final int token;
    /** Per-layer vertex payload; null = empty layer. */
    public final ByteBuffer[] layers;
    public final int[] vertexCounts;
    public final SetVisibility visibility;
    public final List<TileEntity> tileEntities;

    public GnuBuiltMesh(long key, BlockPos origin, int token, ByteBuffer[] layers, int[] vertexCounts) {
        this(key, origin, token, layers, vertexCounts, new SetVisibility(),
                Collections.<TileEntity>emptyList());
    }

    public GnuBuiltMesh(long key, BlockPos origin, int token, ByteBuffer[] layers, int[] vertexCounts,
            SetVisibility visibility, List<TileEntity> tileEntities) {
        this.key = key;
        this.origin = origin;
        this.token = token;
        this.layers = layers;
        this.vertexCounts = vertexCounts;
        this.visibility = visibility != null ? visibility : new SetVisibility();
        this.tileEntities = tileEntities != null ? tileEntities : Collections.<TileEntity>emptyList();
    }

    public boolean hasLayer(EnumWorldBlockLayer layer) {
        int i = layer.ordinal();
        return layers[i] != null && vertexCounts[i] > 0;
    }

    /**
     * Main-thread compiled chunk for vanilla {@code setupTerrain} BFS + TESR collection.
     * Does not touch GL.
     */
    public CompiledChunk toCompiledChunk() {
        CompiledChunk compiled = new CompiledChunk();
        compiled.setVisibility(visibility);
        for (int i = 0; i < tileEntities.size(); i++) {
            TileEntity te = tileEntities.get(i);
            if (te != null) {
                compiled.addTileEntity(te);
            }
        }
        return compiled;
    }

    /**
     * Drop Java references without eagerly freeing native memory. Use after a successful
     * {@code glBufferData} upload — on macOS the driver may still read the staging buffer.
     */
    public void discard() {
        if (layers == null) {
            return;
        }
        for (int i = 0; i < layers.length; i++) {
            layers[i] = null;
            if (vertexCounts != null && i < vertexCounts.length) {
                vertexCounts[i] = 0;
            }
        }
    }

    /**
     * Drop payloads for meshes that were never uploaded (orphan / cancelled). Eager free is
     * safe here because GL never saw the buffer.
     */
    public void release() {
        if (layers == null) {
            return;
        }
        for (int i = 0; i < layers.length; i++) {
            ByteBuffer buf = layers[i];
            layers[i] = null;
            if (vertexCounts != null && i < vertexCounts.length) {
                vertexCounts[i] = 0;
            }
            freeDirect(buf);
        }
    }

    private static void freeDirect(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) {
            return;
        }
        try {
            Method cleanerMethod = buffer.getClass().getMethod("cleaner");
            cleanerMethod.setAccessible(true);
            Object cleaner = cleanerMethod.invoke(buffer);
            if (cleaner != null) {
                Method clean = cleaner.getClass().getMethod("clean");
                clean.setAccessible(true);
                clean.invoke(cleaner);
            }
        } catch (Throwable ignored) {
            // Phantom-cleaner / GC still reclaim eventually.
        }
    }
}
