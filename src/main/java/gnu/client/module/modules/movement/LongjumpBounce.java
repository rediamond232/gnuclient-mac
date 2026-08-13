package gnu.client.module.modules.movement;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockSlime;

/** Slime/bed bounce detection helpers for Longjump. */
public final class LongjumpBounce {

    private LongjumpBounce() {}

    public static boolean isBounceBlock(Block block) {
        if (block == null)
            return false;
        return block instanceof BlockSlime || block instanceof BlockBed;
    }

    /** prev falling hard, now strong upward — slime-style reverse. */
    public static boolean isBounceMotionFlip(double prevMotionY, double motionY) {
        return prevMotionY < -0.08 && motionY > 0.2;
    }
}
