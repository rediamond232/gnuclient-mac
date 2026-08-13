package gnu.client.mixin.impl.client;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.math.FastMath;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MathHelper.class)
public class MixinMathHelperFast {

    @Inject(method = "sin", at = @At("HEAD"), cancellable = true)
    private static void gnu$fastSin(float value, CallbackInfoReturnable<Float> cir) {
        if (GraphicsModule.fastMath()) {
            cir.setReturnValue(Float.valueOf(FastMath.sin(value)));
        }
    }

    @Inject(method = "cos", at = @At("HEAD"), cancellable = true)
    private static void gnu$fastCos(float value, CallbackInfoReturnable<Float> cir) {
        if (GraphicsModule.fastMath()) {
            cir.setReturnValue(Float.valueOf(FastMath.cos(value)));
        }
    }
}
