package gnu.client.mixin.impl.client;

import gnu.client.ui.menu.MenuChrome;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class MixinGui {

    @Inject(method = "drawCenteredString", at = @At("HEAD"), cancellable = true)
    private void gnu$luxCentered(FontRenderer fontRenderer, String text, int x, int y, int color,
            CallbackInfo ci) {
        if (!MenuChrome.shouldStyle()) {
            return;
        }
        MenuChrome.drawCenteredLabel(text, x, y, color);
        ci.cancel();
    }

    @Inject(method = "drawString", at = @At("HEAD"), cancellable = true)
    private void gnu$luxString(FontRenderer fontRenderer, String text, int x, int y, int color,
            CallbackInfo ci) {
        if (!MenuChrome.shouldStyle()) {
            return;
        }
        MenuChrome.drawLabel(text, x, y, color);
        ci.cancel();
    }
}
