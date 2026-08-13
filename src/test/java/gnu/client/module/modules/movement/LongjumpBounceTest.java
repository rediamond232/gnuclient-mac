package gnu.client.module.modules.movement;

import net.minecraft.block.Block;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LongjumpBounceTest {

    @Test
    public void nullBlockIsNotBounce() {
        assertFalse(LongjumpBounce.isBounceBlock(null));
    }

    @Test
    public void motionFlipDetectsBounce() {
        assertTrue(LongjumpBounce.isBounceMotionFlip(-0.5, 0.8));
        assertFalse(LongjumpBounce.isBounceMotionFlip(-0.5, -0.1));
        assertFalse(LongjumpBounce.isBounceMotionFlip(0.1, 0.8));
    }

    @Test
    public void isBounceBlockRejectsNonBounceByTypeCheck() {
        // Avoid Blocks.* bootstrap in unit tests; stone-like anonymous Block is not slime/bed.
        Block stoneLike = new Block(net.minecraft.block.material.Material.rock);
        assertFalse(LongjumpBounce.isBounceBlock(stoneLike));
    }
}
