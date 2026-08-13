package gnu.client.mixin.impl.render;

import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(TileEntityBeaconRenderer.class)
public abstract class MixinTileEntityBeaconRenderer {

    @Inject(
            method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFI)V",
            at = @At("HEAD"))
    private void gnu$shaderBeaconBegin(TileEntityBeacon te, double x, double y, double z,
            float partialTicks, int destroyStage, CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindBeacon();
    }

    @Inject(
            method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFI)V",
            at = @At("RETURN"))
    private void gnu$shaderBeaconEnd(TileEntityBeacon te, double x, double y, double z,
            float partialTicks, int destroyStage, CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindBlock();
    }
}
