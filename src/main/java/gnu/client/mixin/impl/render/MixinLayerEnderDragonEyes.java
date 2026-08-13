package gnu.client.mixin.impl.render;

import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.renderer.entity.layers.LayerEnderDragonEyes;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(LayerEnderDragonEyes.class)
public abstract class MixinLayerEnderDragonEyes {

    @Inject(
            method = "doRenderLayer(Lnet/minecraft/entity/boss/EntityDragon;FFFFFFF)V",
            at = @At("HEAD"))
    private void gnu$shaderEyesBegin(EntityDragon entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
            CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindSpiderEyes();
    }

    @Inject(
            method = "doRenderLayer(Lnet/minecraft/entity/boss/EntityDragon;FFFFFFF)V",
            at = @At("RETURN"))
    private void gnu$shaderEyesEnd(EntityDragon entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
            CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindEntities();
    }
}
