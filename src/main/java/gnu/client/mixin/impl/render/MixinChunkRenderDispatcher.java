package gnu.client.mixin.impl.render;

import com.google.common.collect.Queues;
import gnu.client.common.ChunkWorkers;
import gnu.client.common.GnuLog;
import gnu.client.module.modules.settings.PerformanceModule;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderWorker;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Scales vanilla's chunk-meshing thread pool to the machine when custom terrain is off.
 * When custom terrain owns meshing, leave vanilla at 2 workers / 5 builders so we do not
 * double-allocate {@code RegionRenderCacheBuilder} pools.
 */
@SideOnly(Side.CLIENT)
@Mixin(ChunkRenderDispatcher.class)
public abstract class MixinChunkRenderDispatcher {

    @Shadow @Final private List<ChunkRenderWorker> listThreadedWorkers;

    @Shadow @Final private BlockingQueue<RegionRenderCacheBuilder> queueFreeRenderBuilders;

    /** Actual builders placed in the free queue (vanilla 5, or scaled). Drain target. */
    @Unique
    private int gnu$builderPoolSize = ChunkWorkers.VANILLA_BUILDERS;

    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Queues;newArrayBlockingQueue(I)Ljava/util/concurrent/ArrayBlockingQueue;", ordinal = 0),
            require = 1)
    private java.util.concurrent.ArrayBlockingQueue<Object> gnu$widenUpdateQueue(int capacity) {
        if (!PerformanceModule.scaleVanillaChunkWorkers()) {
            return Queues.newArrayBlockingQueue(capacity);
        }
        return Queues.newArrayBlockingQueue(Math.max(capacity, 256));
    }

    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Queues;newArrayBlockingQueue(I)Ljava/util/concurrent/ArrayBlockingQueue;", ordinal = 1),
            require = 1)
    private java.util.concurrent.ArrayBlockingQueue<Object> gnu$widenBuilderPool(int capacity) {
        if (!PerformanceModule.scaleVanillaChunkWorkers()) {
            return Queues.newArrayBlockingQueue(capacity);
        }
        return Queues.newArrayBlockingQueue(Math.max(capacity, ChunkWorkers.builderCount()));
    }

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void gnu$addExtraWorkers(CallbackInfo ci) {
        gnu$builderPoolSize = ChunkWorkers.VANILLA_BUILDERS;
        if (!PerformanceModule.scaleVanillaChunkWorkers()) {
            GnuLog.log("Performance: skipping vanilla chunk batcher scale (custom terrain or pre-init)");
            return;
        }
        int workers = ChunkWorkers.workerCount();
        int extraWorkers = workers - ChunkWorkers.VANILLA_WORKERS;
        int extraBuilders = ChunkWorkers.builderCount() - ChunkWorkers.VANILLA_BUILDERS;
        if (extraWorkers <= 0 && extraBuilders <= 0) {
            return;
        }

        int addedBuilders = 0;
        for (int i = 0; i < extraBuilders; i++) {
            if (!queueFreeRenderBuilders.offer(new RegionRenderCacheBuilder())) {
                break;
            }
            addedBuilders++;
        }
        gnu$builderPoolSize = ChunkWorkers.VANILLA_BUILDERS + addedBuilders;

        ChunkRenderDispatcher self = (ChunkRenderDispatcher) (Object) this;
        int started = 0;
        for (int i = 0; i < extraWorkers; i++) {
            ChunkRenderWorker worker = new ChunkRenderWorker(self);
            Thread thread = new Thread(worker, "Chunk Batcher " + (ChunkWorkers.VANILLA_WORKERS + i));
            thread.setDaemon(true);
            thread.start();
            listThreadedWorkers.add(worker);
            started++;
        }
        GnuLog.log("Performance: chunk batchers " + (ChunkWorkers.VANILLA_WORKERS + started)
                + " (vanilla " + ChunkWorkers.VANILLA_WORKERS + "), builder pool "
                + gnu$builderPoolSize + ", cores " + Runtime.getRuntime().availableProcessors());
    }

    /**
     * {@code stopChunkUpdates} reclaim target must match builders actually created — not
     * {@link ChunkWorkers#builderCount()} when scaling was skipped for custom terrain.
     */
    @ModifyConstant(method = "stopChunkUpdates", constant = @Constant(intValue = ChunkWorkers.VANILLA_BUILDERS), require = 1)
    private int gnu$drainWholeBuilderPool(int original) {
        return Math.max(ChunkWorkers.VANILLA_BUILDERS, gnu$builderPoolSize);
    }
}
