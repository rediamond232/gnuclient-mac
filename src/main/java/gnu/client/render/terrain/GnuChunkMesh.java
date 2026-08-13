package gnu.client.render.terrain;

import gnu.client.common.BlockLayers;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * GPU-resident mesh for one 16³ section (four world-block layers).
 */
public final class GnuChunkMesh {

    public final long key;
    public final BlockPos origin;

    private final VertexBuffer[] buffers = new VertexBuffer[BlockLayers.values().length];
    private final boolean[] empty = new boolean[BlockLayers.values().length];
    private final int[] vertexCounts = new int[BlockLayers.values().length];

    /**
     * Generation counter. Bumped on discard ({@link #delete()}) so late uploads from a
     * destroyed section are ignored. Not bumped on ordinary block dirties — cancelling
     * in-flight builds on every light/block update is what caused remesh thrashing.
     */
    private volatile int rebuildToken;
    private volatile boolean needsRebuild = true;
    private volatile boolean building;
    private volatile boolean hasGpuData;

    public GnuChunkMesh(long key, BlockPos origin) {
        this.key = key;
        this.origin = origin;
        for (int i = 0; i < empty.length; i++) {
            empty[i] = true;
        }
    }

    public int token() {
        return rebuildToken;
    }

    public boolean needsRebuild() {
        return needsRebuild;
    }

    /**
     * Request a remesh. Coalesces: repeated calls while already dirty are free, and an
     * in-flight build is allowed to finish. If the section was dirtied during that build,
     * {@link #needsRebuild} stays/sets true and one follow-up build runs after upload.
     */
    public synchronized void markDirty() {
        needsRebuild = true;
    }

    public synchronized boolean tryBeginBuild() {
        if (!needsRebuild || building) {
            return false;
        }
        building = true;
        needsRebuild = false;
        return true;
    }

    public synchronized void finishBuildFailed() {
        building = false;
        needsRebuild = true;
    }

    public void finishBuildScheduled() {
        // building stays true until upload applied or cancelled
    }

    public boolean isBuilding() {
        return building;
    }

    public synchronized void cancelBuild() {
        building = false;
        needsRebuild = true;
    }

    public boolean hasGpuData() {
        return hasGpuData;
    }

    public boolean isLayerEmpty(EnumWorldBlockLayer layer) {
        return empty[layer.ordinal()];
    }

    /** Must run on the GL thread. */
    public void ensureBuffers() {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] == null) {
                buffers[i] = new VertexBuffer(DefaultVertexFormats.BLOCK);
            }
        }
    }

    /**
     * Must run on the GL thread.
     *
     * @return {@code true} if this upload was applied (caller should install compiled-chunk vis)
     */
    public synchronized boolean upload(GnuBuiltMesh built) {
        if (built.token != rebuildToken) {
            building = false;
            needsRebuild = true;
            return false;
        }
        ensureBuffers();
        for (EnumWorldBlockLayer layer : BlockLayers.values()) {
            int i = layer.ordinal();
            ByteBuffer data = built.layers[i];
            int verts = built.vertexCounts[i];
            if (data == null || verts <= 0) {
                empty[i] = true;
                vertexCounts[i] = 0;
                // Clear stale GPU geometry so a later empty-flag glitch cannot redraw old quads
                // as black cubes against the sky.
                if (buffers[i] != null) {
                    buffers[i].deleteGlBuffers();
                    buffers[i] = null;
                }
                continue;
            }
            if (buffers[i] == null) {
                buffers[i] = new VertexBuffer(DefaultVertexFormats.BLOCK);
            }
            buffers[i].bindBuffer();
            buffers[i].bufferData(data);
            buffers[i].unbindBuffer();
            empty[i] = false;
            vertexCounts[i] = verts;
        }
        hasGpuData = true;
        building = false;
        // needsRebuild may already be true if markDirty ran during the build — leave it so
        // the next frame schedules exactly one follow-up remesh.
        return true;
    }

    /**
     * Must run on the GL thread. Draw one layer; caller sets client arrays / matrix and unbinds
     * the array buffer once after the whole layer pass (vanilla {@code VboRenderList}).
     */
    public void drawLayer(EnumWorldBlockLayer layer) {
        int i = layer.ordinal();
        if (empty[i] || buffers[i] == null) {
            return;
        }
        buffers[i].bindBuffer();
        GnuTerrainDraw.setupArrayPointers();
        buffers[i].drawArrays(GL11.GL_QUADS);
    }

    /** Must run on the GL thread. */
    public synchronized void delete() {
        rebuildToken++;
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null) {
                buffers[i].deleteGlBuffers();
                buffers[i] = null;
            }
            empty[i] = true;
            vertexCounts[i] = 0;
        }
        hasGpuData = false;
        building = false;
        needsRebuild = true;
    }
}
