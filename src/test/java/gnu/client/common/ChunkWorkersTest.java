package gnu.client.common;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChunkWorkersTest {

    @Test
    public void workerCountNeverDropsBelowVanilla() {
        assertTrue(ChunkWorkers.workerCount() >= ChunkWorkers.VANILLA_WORKERS);
    }

    @Test
    public void workerCountLeavesACoreForTheRenderThread() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores > ChunkWorkers.VANILLA_WORKERS + 1) {
            assertTrue(ChunkWorkers.workerCount() < cores);
        }
    }

    /** stopChunkUpdates drains the whole pool, so it must never be smaller than vanilla's 5. */
    @Test
    public void builderPoolCoversWorkersAndVanillaFloor()  {
        assertTrue(ChunkWorkers.builderCount() >= ChunkWorkers.VANILLA_BUILDERS);
        assertTrue(ChunkWorkers.builderCount() >= ChunkWorkers.workerCount());
    }

    @Test
    public void sizingIsStableAcrossCalls() {
        assertEquals(ChunkWorkers.workerCount(), ChunkWorkers.workerCount());
        assertEquals(ChunkWorkers.builderCount(), ChunkWorkers.builderCount());
    }
}
