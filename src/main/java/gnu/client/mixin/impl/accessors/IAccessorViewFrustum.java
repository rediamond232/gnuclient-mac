package gnu.client.mixin.impl.accessors;

import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewFrustum.class)
public interface IAccessorViewFrustum {

    @Invoker("getRenderChunk")
    RenderChunk invokeGetRenderChunk(BlockPos pos);
}
