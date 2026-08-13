package gnu.client.mixin.impl.accessors;

import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.RenderGlobal$ContainerLocalRenderInformation")
public interface IAccessorContainerLocalRenderInformation {

    @Accessor("renderChunk")
    RenderChunk getRenderChunk();
}
