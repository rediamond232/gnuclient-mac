package gnu.client.render.terrain;

import gnu.client.common.ChunkWorkers;
import gnu.client.common.GnuLog;
import gnu.client.common.OptiFineCompat;
import gnu.client.mixin.impl.accessors.IAccessorContainerLocalRenderInformation;
import gnu.client.mixin.impl.accessors.IAccessorRenderGlobal;
import gnu.client.mixin.impl.accessors.IAccessorViewFrustum;
import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom terrain orchestrator: own mesh / upload / draw while reusing vanilla
 * {@code setupTerrain} visibility ({@code renderInfos}).
 */
public final class GnuTerrainRenderer {

    public static final GnuTerrainRenderer INSTANCE = new GnuTerrainRenderer();

    private final Map<Long, GnuChunkMesh> sections = new ConcurrentHashMap<Long, GnuChunkMesh>();
    private final ConcurrentLinkedQueue<GnuBuiltMesh> uploadQueue = new ConcurrentLinkedQueue<GnuBuiltMesh>();
    private final ConcurrentLinkedQueue<Long> dirtyQueue = new ConcurrentLinkedQueue<Long>();
    private final List<GnuChunkMesh> drawList = new ArrayList<GnuChunkMesh>();
    private boolean pumpedThisFrame;

    private ExecutorService workers;
    private BlockingQueue<RegionRenderCacheBuilder> builders;
    private World boundWorld;
    private boolean started;
    private boolean loggedStart;

    private GnuTerrainRenderer() {}

    public boolean active() {
        return PerformanceModule.customTerrain();
    }

    public synchronized void ensureStarted() {
        if (started) {
            return;
        }
        OptiFineCompat.warnUnsupportedIfPresent();
        int n = ChunkWorkers.workerCount();
        builders = new LinkedBlockingQueue<RegionRenderCacheBuilder>(n);
        for (int i = 0; i < n; i++) {
            builders.add(new RegionRenderCacheBuilder());
        }
        final AtomicInteger idx = new AtomicInteger();
        workers = Executors.newFixedThreadPool(n, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Gnu Terrain Mesher " + idx.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
        started = true;
        if (!loggedStart) {
            loggedStart = true;
            GnuLog.log("Performance: custom terrain mesher workers=" + n
                    + " cores=" + Runtime.getRuntime().availableProcessors());
        }
    }

    public void onWorldChanged(World world) {
        clearGpu();
        boundWorld = world;
        if (world != null && active()) {
            ensureStarted();
            clearVanillaChunkBacklog();
        }
    }

    /**
     * GUI / config toggled Custom Terrain. When enabling, suppress the idle vanilla
     * compile queue and remesh. When disabling, free our VBOs and force vanilla
     * {@code loadRenderers()} so CompiledChunks are rebuilt (they were stale while we
     * owned the path).
     */
    public void onCustomTerrainSettingChanged(boolean enabled) {
        Minecraft mc = Minecraft.getMinecraft();
        if (enabled) {
            if (mc != null && mc.theWorld != null) {
                boundWorld = mc.theWorld;
                ensureStarted();
                markAllDirty();
                clearVanillaChunkBacklog();
            }
            return;
        }
        clearGpu();
        if (mc != null && mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    public void clearGpu() {
        for (GnuChunkMesh mesh : sections.values()) {
            mesh.delete();
        }
        sections.clear();
        GnuBuiltMesh orphan;
        while ((orphan = uploadQueue.poll()) != null) {
            orphan.release();
        }
        dirtyQueue.clear();
    }

    /**
     * Drop vanilla {@code chunksToUpdate} so cancelled {@code updateChunks} cannot grow an
     * unbounded dirty set while custom terrain owns meshing.
     */
    public void clearVanillaChunkBacklog() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
            return;
        }
        Set<?> pending = ((IAccessorRenderGlobal) mc.renderGlobal).getChunksToUpdate();
        if (pending != null) {
            pending.clear();
        }
    }

    public void markBlockDirty(int x, int y, int z) {
        if (!active()) {
            return;
        }
        // Match vanilla RenderGlobal.markBlockForUpdate: dirty the 3x3x3 around the block so
        // section-boundary faces rebuild. Do not offer the same section key seven times — that
        // used to bump rebuild tokens repeatedly and cancel in-flight meshes.
        markSectionsInBlockRange(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }

    public void markRangeDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!active()) {
            return;
        }
        // Match vanilla markBlockRangeForRenderUpdate expansion (±1).
        markSectionsInBlockRange(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
    }

    private void markSectionsInBlockRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int sx0 = minX >> 4;
        int sy0 = minY >> 4;
        int sz0 = minZ >> 4;
        int sx1 = maxX >> 4;
        int sy1 = maxY >> 4;
        int sz1 = maxZ >> 4;
        for (int sy = sy0; sy <= sy1; sy++) {
            for (int sz = sz0; sz <= sz1; sz++) {
                for (int sx = sx0; sx <= sx1; sx++) {
                    dirtyQueue.offer(SectionKeys.of(sx, sy, sz));
                }
            }
        }
    }

    public void markAllDirty() {
        for (GnuChunkMesh mesh : sections.values()) {
            mesh.markDirty();
        }
    }

    /** Drain upload queue + schedule rebuilds. Called from cancelled {@code updateChunks}. */
    public void pumpMainThread(long finishTimeNano) {
        if (!active()) {
            return;
        }
        ensureStarted();
        clearVanillaChunkBacklog();
        flushDirtyFlags();
        long deadline = finishTimeNano;
        if (deadline <= 0L) {
            deadline = System.nanoTime() + 2_000_000L;
        }
        GnuBuiltMesh built;
        while (System.nanoTime() < deadline && (built = uploadQueue.poll()) != null) {
            GnuChunkMesh mesh = sections.get(built.key);
            if (mesh == null) {
                built.release();
                continue;
            }
            if (mesh.upload(built)) {
                applyCompiledChunk(built);
            }
            // Do not Cleaner.clean() after upload — macOS GL can still be reading the staging
            // buffer; freeing it early corrupts VBOs (black cubes / "broken stars" in the sky).
            built.discard();
        }
        pumpedThisFrame = true;
    }

    /** Install vis-graph + TESRs onto the vanilla RenderChunk for setupTerrain BFS. */
    private void applyCompiledChunk(GnuBuiltMesh built) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
            return;
        }
        ViewFrustum frustum = ((IAccessorRenderGlobal) mc.renderGlobal).getViewFrustum();
        if (frustum == null) {
            return;
        }
        RenderChunk rc = ((IAccessorViewFrustum) frustum).invokeGetRenderChunk(built.origin);
        if (rc == null) {
            return;
        }
        rc.setCompiledChunk(built.toCompiledChunk());
    }

    private void flushDirtyFlags() {
        if (dirtyQueue.isEmpty()) {
            return;
        }
        // Deduplicate: one block update can enqueue the same section several times (and light /
        // multi-block updates pile on). markDirty is coalesced, but skipping duplicate lookups
        // keeps the main-thread pump cheap under bursty world updates.
        Set<Long> unique = new HashSet<Long>();
        Long key;
        while ((key = dirtyQueue.poll()) != null) {
            unique.add(key);
        }
        for (Long k : unique) {
            GnuChunkMesh mesh = sections.get(k);
            if (mesh != null) {
                mesh.markDirty();
            }
        }
    }

    public int drawLayer(RenderGlobal renderGlobal, EnumWorldBlockLayer layer, double partialTicks,
            Entity viewEntity) {
        if (!active()) {
            return 0;
        }
        ensureStarted();
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || viewEntity == null) {
            return 0;
        }
        if (boundWorld != world) {
            onWorldChanged(world);
        }

        boolean solid = layer == EnumWorldBlockLayer.SOLID;
        if (solid) {
            if (!pumpedThisFrame) {
                pumpMainThread(System.nanoTime() + 1_500_000L);
            }
            pumpedThisFrame = false;
            pruneFarSections(viewEntity, mc.gameSettings.renderDistanceChunks);
        }

        IAccessorRenderGlobal rg = (IAccessorRenderGlobal) renderGlobal;
        List<?> infos = rg.getRenderInfos();
        if (infos == null || infos.isEmpty()) {
            restoreOverlayFriendlyState();
            return 0;
        }

        double camX = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks;
        double camY = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks;
        double camZ = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks;

        drawList.clear();
        for (Object info : infos) {
            RenderChunk rc = ((IAccessorContainerLocalRenderInformation) info).getRenderChunk();
            if (rc == null) {
                continue;
            }
            BlockPos origin = rc.getPosition();
            long key = SectionKeys.ofOrigin(origin);
            GnuChunkMesh mesh = sections.get(key);
            if (mesh == null) {
                mesh = new GnuChunkMesh(key, origin);
                sections.put(key, mesh);
            }
            if (solid && mesh.needsRebuild()) {
                scheduleBuild(world, mesh, (float) camX, (float) camY, (float) camZ);
            }
            if (mesh.hasGpuData() && !mesh.isLayerEmpty(layer)) {
                drawList.add(mesh);
            }
        }

        if (layer == EnumWorldBlockLayer.TRANSLUCENT) {
            final double fx = camX;
            final double fy = camY;
            final double fz = camZ;
            drawList.sort(new Comparator<GnuChunkMesh>() {
                @Override
                public int compare(GnuChunkMesh a, GnuChunkMesh b) {
                    double da = distSq(a.origin, fx, fy, fz);
                    double db = distSq(b.origin, fx, fy, fz);
                    return Double.compare(db, da); // far to near
                }
            });
        }

        RenderHelper.disableStandardItemLighting();
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        mc.entityRenderer.enableLightmap();
        ShaderEngine.INSTANCE.bindLayer(layer);
        GnuTerrainDraw.enableClientStates();

        int drawn = 0;
        try {
            for (GnuChunkMesh mesh : drawList) {
                GlStateManager.pushMatrix();
                BlockPos o = mesh.origin;
                GlStateManager.translate((float) (o.getX() - camX), (float) (o.getY() - camY),
                        (float) (o.getZ() - camZ));
                mesh.drawLayer(layer);
                GlStateManager.popMatrix();
                drawn++;
            }
        } finally {
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
            GnuTerrainDraw.disableClientStates();
            mc.entityRenderer.disableLightmap();
            GlStateManager.resetColor();
            restoreOverlayFriendlyState();
        }
        return drawn;
    }

    /**
     * Depth-only (plus albedo for {@code shadow.fsh}) draw from the sun. Uses the same
     * camera-relative chunk translation as the main pass so {@code shadowModelView}
     * can share OptiFine's {@code -camera} convention.
     */
    public int drawShadow(RenderGlobal renderGlobal, double partialTicks, Entity viewEntity) {
        if (!active() || viewEntity == null) {
            return 0;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || renderGlobal == null) {
            return 0;
        }
        IAccessorRenderGlobal rg = (IAccessorRenderGlobal) renderGlobal;
        List<?> infos = rg.getRenderInfos();
        if (infos == null || infos.isEmpty()) {
            return 0;
        }
        double camX = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks;
        double camY = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks;
        double camZ = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks;
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GnuTerrainDraw.enableClientStates();
        EnumWorldBlockLayer[] layers = {
                EnumWorldBlockLayer.SOLID,
                EnumWorldBlockLayer.CUTOUT_MIPPED,
                EnumWorldBlockLayer.CUTOUT
        };
        int drawn = 0;
        try {
            for (int li = 0; li < layers.length; li++) {
                EnumWorldBlockLayer layer = layers[li];
                for (Object info : infos) {
                    RenderChunk rc = ((IAccessorContainerLocalRenderInformation) info).getRenderChunk();
                    if (rc == null) {
                        continue;
                    }
                    GnuChunkMesh mesh = sections.get(SectionKeys.ofOrigin(rc.getPosition()));
                    if (mesh == null || !mesh.hasGpuData() || mesh.isLayerEmpty(layer)) {
                        continue;
                    }
                    GlStateManager.pushMatrix();
                    BlockPos o = mesh.origin;
                    GlStateManager.translate((float) (o.getX() - camX), (float) (o.getY() - camY),
                            (float) (o.getZ() - camZ));
                    mesh.drawLayer(layer);
                    GlStateManager.popMatrix();
                    drawn++;
                }
            }
        } finally {
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
            GnuTerrainDraw.disableClientStates();
        }
        return drawn;
    }

    private static double distSq(BlockPos origin, double x, double y, double z) {
        double cx = origin.getX() + 8.0 - x;
        double cy = origin.getY() + 8.0 - y;
        double cz = origin.getZ() + 8.0 - z;
        return cx * cx + cy * cy + cz * cz;
    }

    /**
     * Leave depth/blend/texture in a state overlays (ESP via RenderWorldLastEvent) expect
     * after vanilla terrain: depth on, texture on, blend typically off until translucent pass
     * re-enables it — EntityRenderer manages blend around layers, so we only ensure basics.
     */
    public void restoreOverlayFriendlyState() {
        OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
        GnuTerrainDraw.disableClientStates();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void scheduleBuild(final World world, final GnuChunkMesh mesh, final float viewX,
            final float viewY, final float viewZ) {
        if (!mesh.tryBeginBuild()) {
            return;
        }
        final int token = mesh.token();
        workers.execute(new Runnable() {
            @Override
            public void run() {
                RegionRenderCacheBuilder builder = null;
                try {
                    builder = builders.take();
                    if (mesh.token() != token) {
                        mesh.cancelBuild();
                        return;
                    }
                    GnuBuiltMesh built = GnuMeshBuilder.build(world, mesh, builder, viewX, viewY, viewZ);
                    if (mesh.token() != token) {
                        built.release();
                        mesh.cancelBuild();
                        return;
                    }
                    mesh.finishBuildScheduled();
                    uploadQueue.offer(built);
                } catch (Throwable t) {
                    mesh.finishBuildFailed();
                    GnuLog.log("Custom terrain mesh failed at " + mesh.origin + ": " + t);
                } finally {
                    if (builder != null) {
                        builders.offer(builder);
                    }
                }
            }
        });
    }

    /** Drop sections that are no longer near the player (optional GC). */
    public void pruneFarSections(Entity view, int renderDistanceChunks) {
        if (view == null) {
            return;
        }
        int margin = (renderDistanceChunks + 2) << 4;
        double px = view.posX;
        double pz = view.posZ;
        Iterator<Map.Entry<Long, GnuChunkMesh>> it = sections.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, GnuChunkMesh> e = it.next();
            BlockPos o = e.getValue().origin;
            if (Math.abs(o.getX() + 8 - px) > margin || Math.abs(o.getZ() + 8 - pz) > margin) {
                e.getValue().delete();
                it.remove();
            }
        }
    }
}
