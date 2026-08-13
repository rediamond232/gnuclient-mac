package gnu.client.mixin.impl.render;

import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcher {

    @Inject(method = "renderTileEntity", at = @At("HEAD"))
    private void gnu$shaderBlock(TileEntity tileentityIn, float partialTicks, int destroyStage,
            CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindBlock();
    }
}
