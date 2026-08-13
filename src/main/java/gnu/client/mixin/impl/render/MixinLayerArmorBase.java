package gnu.client.mixin.impl.render;

import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(LayerArmorBase.class)
public abstract class MixinLayerArmorBase {

    @Inject(
            method = "renderGlint(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V",
            at = @At("HEAD"))
    private void gnu$shaderGlintBegin(CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindGlint();
    }

    @Inject(
            method = "renderGlint(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V",
            at = @At("RETURN"))
    private void gnu$shaderGlintEnd(CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindEntities();
    }
}
