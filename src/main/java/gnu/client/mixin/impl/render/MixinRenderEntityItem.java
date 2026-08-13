package gnu.client.mixin.impl.render;

import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.entity.item.EntityItem;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(RenderEntityItem.class)
public abstract class MixinRenderEntityItem {

    @Inject(
            method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
            at = @At("HEAD"))
    private void gnu$shaderItem(EntityItem entity, double x, double y, double z, float entityYaw,
            float partialTicks, CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindItem();
    }
}
