package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.sky.CustomSky;
import gnu.client.render.shaders.ShaderEngine;
import gnu.client.render.terrain.GnuTerrainDraw;
import gnu.client.render.terrain.GnuTerrainRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hijacks vanilla terrain draw / chunk upload when custom terrain owns the path.
 * Vanilla dirty marks are cancelled so {@code chunksToUpdate} cannot grow while uploads
 * are suppressed. Also resets leftover client-array state before sky/stars.
 */
@SideOnly(Side.CLIENT)
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    /**
     * Stars/sky VBOs only enable VERTEX_ARRAY. Leftover COLOR/TEXCOORD client state from the
     * previous frame (ESP, terrain, particles) makes stars draw as black cubes.
     */
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"))
    private void gnu$resetClientArraysBeforeSky(float partialTicks, int pass, CallbackInfo ci) {
        OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
        GnuTerrainDraw.disableClientStates();
        ShaderEngine.INSTANCE.bindSky();
    }

    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void gnu$skyOff(float partialTicks, int pass, CallbackInfo ci) {
        if (!GraphicsModule.sky()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At("RETURN"))
    private void gnu$customSky(float partialTicks, int pass, CallbackInfo ci) {
        CustomSky.render(partialTicks);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F"))
    private float gnu$stars(WorldClient world, float partialTicks) {
        if (!GraphicsModule.stars()) {
            return 0f;
        }
        return world.getStarBrightness(partialTicks);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V"))
    private void gnu$sunMoon(TextureManager tm, ResourceLocation loc) {
        if (!GraphicsModule.sunMoon()
                && loc != null
                && ("textures/environment/sun.png".equals(loc.getResourcePath())
                        || "textures/environment/moon_phases.png".equals(loc.getResourcePath()))) {
            GlStateManager.color(0f, 0f, 0f, 0f);
        }
        Minecraft mc = Minecraft.getMinecraft();
        int dim = mc != null && mc.theWorld != null ? mc.theWorld.provider.getDimensionId() : 0;
        ResourceLocation override = null;
        if (loc != null && "textures/environment/sun.png".equals(loc.getResourcePath())) {
            override = CustomSky.sunOverride(dim);
        } else if (loc != null && "textures/environment/moon_phases.png".equals(loc.getResourcePath())) {
            override = CustomSky.moonOverride(dim);
        }
        if (override != null && mc != null && !GraphicsPackRoots.exists(mc.getResourceManager(), override)) {
            override = null;
        }
        tm.bindTexture(override != null ? override : loc);
        if (loc != null && ("textures/environment/sun.png".equals(loc.getResourcePath())
                || "textures/environment/moon_phases.png".equals(loc.getResourcePath()))) {
            ShaderEngine.INSTANCE.bindSkyTextured();
        }
    }

    /**
     * Custom terrain installs a real {@link CompiledChunk} vis-graph on upload. Until that
     * first mesh, chunks stay on {@link CompiledChunk#DUMMY} whose {@code isVisible} is always
     * false — treat DUMMY as all-visible so BFS can discover sections to mesh. After upload,
     * use the real graph so walls occlude again.
     */
    @Redirect(
            method = "setupTerrain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/CompiledChunk;isVisible(Lnet/minecraft/util/EnumFacing;Lnet/minecraft/util/EnumFacing;)Z"),
            require = 1)
    private boolean gnu$customTerrainSkipOcclusion(CompiledChunk compiled, EnumFacing from,
            EnumFacing to) {
        if (!PerformanceModule.customTerrain()) {
            return compiled.isVisible(from, to);
        }
        if (compiled == CompiledChunk.DUMMY) {
            return true;
        }
        return compiled.isVisible(from, to);
    }

    @Inject(
            method = "renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"))
    private void gnu$shaderLayer(EnumWorldBlockLayer layer, double partialTicks, int pass,
            Entity entity, CallbackInfoReturnable<Integer> cir) {
        ShaderEngine.INSTANCE.bindLayer(layer);
    }

    @Inject(
            method = "renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;DILnet/minecraft/entity/Entity;)I",
            at = @At("HEAD"),
            cancellable = true)
    private void gnu$customTerrainDraw(EnumWorldBlockLayer layer, double partialTicks, int pass,
            Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (!PerformanceModule.customTerrain()) {
            return;
        }
        int drawn = GnuTerrainRenderer.INSTANCE.drawLayer((RenderGlobal) (Object) this, layer,
                partialTicks, entity);
        cir.setReturnValue(drawn);
    }

    @Inject(method = "updateChunks", at = @At("HEAD"), cancellable = true)
    private void gnu$customTerrainUploads(long finishTimeNano, CallbackInfo ci) {
        if (!PerformanceModule.customTerrain()) {
            return;
        }
        GnuTerrainRenderer.INSTANCE.pumpMainThread(finishTimeNano);
        ci.cancel();
    }

    @Inject(method = "setWorldAndLoadRenderers", at = @At("HEAD"))
    private void gnu$customTerrainWorld(net.minecraft.client.multiplayer.WorldClient worldClient,
            CallbackInfo ci) {
        GnuTerrainRenderer.INSTANCE.onWorldChanged(worldClient);
    }

    @Inject(method = "loadRenderers", at = @At("RETURN"))
    private void gnu$customTerrainReload(CallbackInfo ci) {
        if (PerformanceModule.customTerrain()) {
            GnuTerrainRenderer.INSTANCE.markAllDirty();
            GnuTerrainRenderer.INSTANCE.clearVanillaChunkBacklog();
        }
    }

    @Inject(method = "markBlockForUpdate", at = @At("HEAD"), cancellable = true)
    private void gnu$customTerrainBlockUpdate(BlockPos pos, CallbackInfo ci) {
        if (!PerformanceModule.customTerrain()) {
            return;
        }
        GnuTerrainRenderer.INSTANCE.markBlockDirty(pos.getX(), pos.getY(), pos.getZ());
        ci.cancel();
    }

    @Inject(method = "notifyLightSet", at = @At("HEAD"), cancellable = true)
    private void gnu$customTerrainLightUpdate(BlockPos pos, CallbackInfo ci) {
        if (!PerformanceModule.customTerrain()) {
            return;
        }
        // Vanilla light updates skip markBlockForUpdate; without this, lighting changes never
        // remesh — and once hooked, dirty coalescing in GnuChunkMesh prevents remesh thrash.
        GnuTerrainRenderer.INSTANCE.markBlockDirty(pos.getX(), pos.getY(), pos.getZ());
        ci.cancel();
    }

    @Inject(method = "markBlockRangeForRenderUpdate", at = @At("HEAD"), cancellable = true)
    private void gnu$customTerrainRangeUpdate(int x1, int y1, int z1, int x2, int y2, int z2,
            CallbackInfo ci) {
        if (!PerformanceModule.customTerrain()) {
            return;
        }
        GnuTerrainRenderer.INSTANCE.markRangeDirty(x1, y1, z1, x2, y2, z2);
        ci.cancel();
    }

    @Inject(
            method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/WorldRenderer;Lnet/minecraft/entity/Entity;F)V",
            at = @At("HEAD"))
    private void gnu$shaderDamagedBlock(CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindDamagedBlock();
    }
}
