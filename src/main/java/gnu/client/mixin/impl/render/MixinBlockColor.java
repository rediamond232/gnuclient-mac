package gnu.client.mixin.impl.render;

import gnu.client.render.graphics.color.CustomColors;
import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class MixinBlockColor {

    @Inject(method = "colorMultiplier(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;I)I", at = @At("RETURN"), cancellable = true)
    private void gnu$customColor(IBlockAccess world, BlockPos pos, int renderPass,
            CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValue() == null ? 0xFFFFFF : cir.getReturnValue().intValue();
        cir.setReturnValue(Integer.valueOf(
                CustomColors.blockColorMultiplier(world, pos, (Block) (Object) this, vanilla)));
    }

    @Inject(method = "colorMultiplier(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private void gnu$customColor2(IBlockAccess world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValue() == null ? 0xFFFFFF : cir.getReturnValue().intValue();
        cir.setReturnValue(Integer.valueOf(
                CustomColors.blockColorMultiplier(world, pos, (Block) (Object) this, vanilla)));
    }
}
