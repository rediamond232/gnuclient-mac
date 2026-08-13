package gnu.client.mixin.impl.render;

import gnu.client.render.graphics.font.HdFonts;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontRenderer.class)
public class MixinFontRendererHd {

    @Inject(method = "getCharWidth", at = @At("RETURN"), cancellable = true)
    private void gnu$hdWidth(char ch, CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValue() == null ? 0 : cir.getReturnValue().intValue();
        cir.setReturnValue(Integer.valueOf((int) HdFonts.overrideWidth(ch, vanilla)));
    }
}
