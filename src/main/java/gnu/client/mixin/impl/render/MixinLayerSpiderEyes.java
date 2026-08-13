package gnu.client.mixin.impl.render;

import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.renderer.entity.layers.LayerSpiderEyes;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(LayerSpiderEyes.class)
public abstract class MixinLayerSpiderEyes {

    @Inject(
            method = "doRenderLayer(Lnet/minecraft/entity/monster/EntitySpider;FFFFFFF)V",
            at = @At("HEAD"))
    private void gnu$shaderEyesBegin(EntitySpider entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
            CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindSpiderEyes();
    }

    @Inject(
            method = "doRenderLayer(Lnet/minecraft/entity/monster/EntitySpider;FFFFFFF)V",
            at = @At("RETURN"))
    private void gnu$shaderEyesEnd(EntitySpider entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
            CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindEntities();
    }
}
