package gnu.client.mixin.impl.accessors;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(RendererLivingEntity.class)
public interface IAccessorRendererLivingEntity {
    @Accessor("layerRenderers")
    List<LayerRenderer<?>> getLayerRenderers();
}
