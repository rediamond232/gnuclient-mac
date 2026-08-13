package gnu.client.mixin.impl.render;

import gnu.client.common.BlockLayers;
import gnu.client.module.modules.settings.PerformanceModule;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes per-block garbage from the chunk meshing hot loop, and keeps vanilla
 * {@code needsUpdate} quiet while custom terrain owns meshing.
 *
 * <p>{@code rebuildChunk} calls {@code EnumWorldBlockLayer.values()} from inside its
 * 16x16x16 block loop to walk the render layers for each block. {@code Enum.values()} clones
 * its backing array on every call, so one chunk rebuild allocates ~4096 throwaway 4-element
 * arrays. While flying through fresh terrain that is a continuous stream of short-lived
 * garbage on the chunk-batcher threads, which is exactly where we are trying to buy headroom.
 *
 * <p>This is a {@code @Redirect} on the {@code values()} call rather than an overwrite of
 * {@code rebuildChunk}: Forge hooks that method ({@code MinecraftForgeClient.onRebuildChunk},
 * {@code ForgeHooksClient.setRenderLayer}), so replacing the method body would break them.
 * Redirecting a single static call leaves the rest of the method untouched. OptiFine is
 * unsupported; this injection is required.
 */
@SideOnly(Side.CLIENT)
@Mixin(RenderChunk.class)
public class MixinRenderChunk {

    @Redirect(
            method = "rebuildChunk",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/EnumWorldBlockLayer;values()[Lnet/minecraft/util/EnumWorldBlockLayer;"),
            require = 1)
    private EnumWorldBlockLayer[] gnu$cachedBlockLayers() {
        return BlockLayers.values();
    }

    /**
     * While custom terrain is active, vanilla CompiledChunks are never uploaded — ignore
     * dirty flags so {@code chunksToUpdate} / batcher threads stay idle.
     */
    @Inject(method = "setNeedsUpdate", at = @At("HEAD"), cancellable = true)
    private void gnu$skipVanillaDirtyWhenCustomTerrain(boolean needsUpdate, CallbackInfo ci) {
        if (PerformanceModule.customTerrain()) {
            ci.cancel();
        }
    }

    /** Prevent setupTerrain from re-queueing chunks that were constructed dirty. */
    @Inject(method = "isNeedsUpdate", at = @At("HEAD"), cancellable = true)
    private void gnu$neverNeedsVanillaUpdate(CallbackInfoReturnable<Boolean> cir) {
        if (PerformanceModule.customTerrain()) {
            cir.setReturnValue(false);
        }
    }
}
