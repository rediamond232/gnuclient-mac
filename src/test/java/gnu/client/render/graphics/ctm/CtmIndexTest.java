package gnu.client.render.graphics.ctm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CtmIndexTest {

    @Test
    public void isolatedTileIsZero() {
        assertEquals(0, CtmIndex.index47(0));
    }

    @Test
    public void singleEdgeNeighbors() {
        assertEquals(1, CtmIndex.index47(1));
        assertEquals(2, CtmIndex.index47(2));
        assertEquals(3, CtmIndex.index47(4));
        assertEquals(4, CtmIndex.index47(8));
    }

    @Test
    public void fullyConnectedUsesInteriorIndex() {
        int all = 1 | 2 | 4 | 8 | 16 | 32 | 64 | 128;
        int idx = CtmIndex.index47(all);
        assertTrue(idx >= 0 && idx <= 46);
        assertEquals(26, idx);
    }
}
