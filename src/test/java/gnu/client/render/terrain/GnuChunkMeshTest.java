package gnu.client.render.terrain;

import net.minecraft.util.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GnuChunkMeshTest {

    private static GnuChunkMesh mesh() {
        return new GnuChunkMesh(SectionKeys.of(0, 0, 0), new BlockPos(0, 0, 0));
    }

    @Test
    public void markDirtyCoalescesWhilePending() {
        GnuChunkMesh m = mesh();
        assertTrue(m.needsRebuild());
        int token = m.token();
        m.markDirty();
        m.markDirty();
        m.markDirty();
        assertTrue(m.needsRebuild());
        assertEquals("ordinary dirties must not invalidate generation", token, m.token());
    }

    @Test
    public void inFlightBuildIsNotCancelledByDirty() {
        GnuChunkMesh m = mesh();
        assertTrue(m.tryBeginBuild());
        int token = m.token();
        assertFalse(m.needsRebuild());
        assertTrue(m.isBuilding());

        m.markDirty();
        assertTrue(m.needsRebuild());
        assertEquals(token, m.token());
        assertTrue("build must keep running so light spam cannot thrash remeshes", m.isBuilding());
        assertFalse(m.tryBeginBuild());
    }

    @Test
    public void cancelBuildRequeuesRemesh() {
        GnuChunkMesh m = mesh();
        assertTrue(m.tryBeginBuild());
        m.cancelBuild();
        assertFalse(m.isBuilding());
        assertTrue(m.needsRebuild());
        assertTrue(m.tryBeginBuild());
    }

    @Test
    public void finishBuildFailedRequeuesRemesh() {
        GnuChunkMesh m = mesh();
        assertTrue(m.tryBeginBuild());
        m.finishBuildFailed();
        assertFalse(m.isBuilding());
        assertTrue(m.needsRebuild());
    }
}
