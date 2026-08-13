package gnu.client.mixin.impl.accessors;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderer.class)
public interface IAccessorEntityRenderer {
    @Accessor("farPlaneDistance")
    float getFarPlaneDistance();

    @Accessor("lightmapColors")
    int[] getLightmapColors();

    @Accessor("lightmapTexture")
    DynamicTexture getLightmapTexture();

    @Accessor("lightmapUpdateNeeded")
    void setLightmapUpdateNeeded(boolean needed);

    @Accessor("torchFlickerX")
    float getTorchFlickerX();
}
