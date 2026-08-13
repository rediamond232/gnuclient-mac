package gnu.client.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EntityCullingTest {

    @Test
    public void reducedOffKeepsVanilla64() {
        assertEquals(64.0, EntityCulling.reducedCapBlocks(false, 0.75f), 0.001);
    }

    @Test
    public void reducedScalesVanillaRange() {
        assertEquals(48.0, EntityCulling.reducedCapBlocks(true, 0.75f), 0.001);
        assertEquals(32.0, EntityCulling.reducedCapBlocks(true, 0.5f), 0.001);
        assertEquals(64.0, EntityCulling.reducedCapBlocks(true, 1.0f), 0.001);
    }

    @Test
    public void reducedClampsFraction() {
        assertEquals(6.4, EntityCulling.reducedCapBlocks(true, 0.0f), 0.001);
        assertEquals(64.0, EntityCulling.reducedCapBlocks(true, 2.0f), 0.001);
    }
}
