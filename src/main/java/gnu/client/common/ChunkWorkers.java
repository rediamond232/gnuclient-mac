package gnu.client.common;

/**
 * Sizing for chunk-build (meshing) thread pools.
 *
 * <p>Used by both the legacy vanilla {@code ChunkRenderDispatcher} scaling mixin and the
 * custom terrain mesher. One core is reserved for the render/main thread. The soft cap is
 * high enough that modern CPUs are not artificially limited once GPU upload is our own.
 */
public final class ChunkWorkers {

    /** Vanilla worker-thread count baked into {@code ChunkRenderDispatcher.<init>}. */
    public static final int VANILLA_WORKERS = 2;

    /** Vanilla {@code RegionRenderCacheBuilder} pool size (also the queue's capacity). */
    public static final int VANILLA_BUILDERS = 5;

    /** Soft ceiling once custom terrain owns upload; still bounds memory for builder pools. */
    private static final int MAX_WORKERS = 12;

    private ChunkWorkers() {}

    /** Threaded worker count for this machine; never below vanilla's 2. */
    public static int workerCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        int workers = cores - 1;
        if (workers < VANILLA_WORKERS)
            workers = VANILLA_WORKERS;
        if (workers > MAX_WORKERS)
            workers = MAX_WORKERS;
        return workers;
    }

    /**
     * Builder-pool size. Every threaded worker blocks on {@code allocateRenderBuilder()} until
     * one is free, so the pool must be at least as large as the worker count or the extra
     * threads just queue up behind each other. Never shrinks below vanilla's 5, because
     * {@code stopChunkUpdates()} drains exactly 5 builders and would deadlock on fewer.
     */
    public static int builderCount() {
        return Math.max(VANILLA_BUILDERS, workerCount());
    }
}
