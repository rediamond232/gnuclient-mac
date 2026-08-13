package gnu.client.mixin.impl.accessors;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Set;

@Mixin(RenderGlobal.class)
public interface IAccessorRenderGlobal {

    @Accessor("renderInfos")
    List<?> getRenderInfos();

    @Accessor("viewFrustum")
    ViewFrustum getViewFrustum();

    @Accessor("theWorld")
    WorldClient getTheWorld();

    @Accessor("chunksToUpdate")
    Set<RenderChunk> getChunksToUpdate();

    @Accessor("renderDispatcher")
    ChunkRenderDispatcher getRenderDispatcher();

    @Accessor("vboEnabled")
    boolean isVboEnabled();
}
