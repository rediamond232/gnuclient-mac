package gnu.client.render.terrain;

import gnu.client.common.BlockLayers;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.RegionRenderCache;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combat-first section mesher: walks 16³ with {@link BlockRendererDispatcher}. Sets Forge's
 * render-layer ThreadLocal so {@code canRenderInLayer} overrides see the active layer.
 * Smooth AO stays off via PerformanceModule.
 */
public final class GnuMeshBuilder {

    private GnuMeshBuilder() {}

    public static GnuBuiltMesh build(World world, GnuChunkMesh mesh, RegionRenderCacheBuilder builders,
            float viewX, float viewY, float viewZ) {
        BlockPos origin = mesh.origin;
        long key = mesh.key;
        int token = mesh.token();

        BlockPos from = origin.add(-1, -1, -1);
        BlockPos to = origin.add(16, 16, 16);
        RegionRenderCache cache = new RegionRenderCache(world, from, to, 1);

        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        int layerCount = BlockLayers.values().length;
        boolean[] layerStarted = new boolean[layerCount];
        boolean[] layerUsed = new boolean[layerCount];
        VisGraph visGraph = new VisGraph();
        List<TileEntity> tesrs = null;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        try {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        IBlockState state = cache.getBlockState(pos);
                        Block block = state.getBlock();
                        if (block.isOpaqueCube()) {
                            GnuSectionVisibility.markOpaque(visGraph, pos);
                        }
                        if (block.hasTileEntity(state)) {
                            tesrs = collectTesr(cache, pos, tesrs);
                        }
                        if (block.getRenderType() == -1) {
                            continue;
                        }
                        EnumWorldBlockLayer primary = block.getBlockLayer();
                        meshBlockLayer(block, state, pos, origin, dispatcher, cache, builders,
                                layerStarted, layerUsed, primary);
                        for (EnumWorldBlockLayer layer : BlockLayers.values()) {
                            if (layer == primary) {
                                continue;
                            }
                            meshBlockLayer(block, state, pos, origin, dispatcher, cache, builders,
                                    layerStarted, layerUsed, layer);
                        }
                    }
                }
            }
        } finally {
            ForgeHooksClient.setRenderLayer(null);
        }

        ByteBuffer[] layers = new ByteBuffer[layerCount];
        int[] counts = new int[layerCount];

        for (EnumWorldBlockLayer layer : BlockLayers.values()) {
            int li = layer.ordinal();
            if (!layerStarted[li]) {
                continue;
            }
            WorldRenderer wr = builders.getWorldRendererByLayer(layer);
            if (layer == EnumWorldBlockLayer.TRANSLUCENT && layerUsed[li] && wr.getVertexCount() > 0) {
                wr.sortVertexData(viewX - origin.getX(), viewY - origin.getY(), viewZ - origin.getZ());
            }
            wr.finishDrawing();
            if (!layerUsed[li] || wr.getVertexCount() <= 0) {
                continue;
            }
            ByteBuffer src = wr.getByteBuffer();
            ByteBuffer copy = ByteBuffer.allocateDirect(src.remaining());
            int posMark = src.position();
            copy.put(src);
            src.position(posMark);
            copy.flip();
            layers[li] = copy;
            counts[li] = wr.getVertexCount();
        }

        SetVisibility visibility = GnuSectionVisibility.compute(visGraph);
        List<TileEntity> tesrList = tesrs != null ? tesrs : Collections.<TileEntity>emptyList();
        return new GnuBuiltMesh(key, origin, token, layers, counts, visibility, tesrList);
    }

    private static void meshBlockLayer(Block block, IBlockState state, BlockPos pos, BlockPos origin,
            BlockRendererDispatcher dispatcher, RegionRenderCache cache,
            RegionRenderCacheBuilder builders, boolean[] layerStarted, boolean[] layerUsed,
            EnumWorldBlockLayer layer) {
        ForgeHooksClient.setRenderLayer(layer);
        if (!block.canRenderInLayer(layer)) {
            return;
        }
        int li = layer.ordinal();
        WorldRenderer wr = builders.getWorldRendererByLayer(layer);
        if (!layerStarted[li]) {
            layerStarted[li] = true;
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            wr.setTranslation(-origin.getX(), -origin.getY(), -origin.getZ());
        }
        if (dispatcher.renderBlock(state, pos, cache, wr)) {
            layerUsed[li] = true;
        }
    }

    private static List<TileEntity> collectTesr(RegionRenderCache cache, BlockPos pos,
            List<TileEntity> tesrs) {
        TileEntity te = cache.getTileEntity(new BlockPos(pos));
        if (te == null) {
            return tesrs;
        }
        if (TileEntityRendererDispatcher.instance.getSpecialRenderer(te) == null) {
            return tesrs;
        }
        if (tesrs == null) {
            tesrs = new ArrayList<TileEntity>();
        }
        tesrs.add(te);
        return tesrs;
    }
}
