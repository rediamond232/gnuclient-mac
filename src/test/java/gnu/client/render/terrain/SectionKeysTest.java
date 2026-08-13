package gnu.client.render.terrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SectionKeysTest {

    @Test
    public void roundTripsSectionCoords() {
        long key = SectionKeys.of(12, -3, 45);
        assertEquals(12, SectionKeys.sectionX(key));
        assertEquals(-3, SectionKeys.sectionY(key));
        assertEquals(45, SectionKeys.sectionZ(key));
    }

    @Test
    public void ofBlockMatchesOriginShift() {
        long key = SectionKeys.ofBlock(100, 50, -20);
        assertEquals(100 >> 4, SectionKeys.sectionX(key));
        assertEquals(50 >> 4, SectionKeys.sectionY(key));
        assertEquals(-20 >> 4, SectionKeys.sectionZ(key));
    }

    @Test
    public void originOfMatchesPackedKey() {
        long key = SectionKeys.of(3, 4, 5);
        assertEquals(48, SectionKeys.originOf(key).getX());
        assertEquals(64, SectionKeys.originOf(key).getY());
        assertEquals(80, SectionKeys.originOf(key).getZ());
    }

    @Test
    public void distinctSectionsHaveDistinctKeys() {
        assertTrue(SectionKeys.of(0, 0, 0) != SectionKeys.of(1, 0, 0));
        assertTrue(SectionKeys.of(0, 0, 0) != SectionKeys.of(0, 1, 0));
        assertTrue(SectionKeys.of(0, 0, 0) != SectionKeys.of(0, 0, 1));
    }
}
