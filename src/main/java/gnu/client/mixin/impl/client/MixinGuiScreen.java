package gnu.client.mixin.impl.client;

import gnu.client.ui.menu.MenuChrome;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void gnu$menuDrawStart(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        MenuChrome.onScreenDrawStart();
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void gnu$menuDrawEnd(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        MenuChrome.onScreenDrawEnd();
    }

    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true)
    private void gnu$luxWorldBackground(int tint, CallbackInfo ci) {
        if (!MenuChrome.shouldStyle((GuiScreen) (Object) this)) {
            return;
        }
        boolean inWorld = ((GuiScreen) (Object) this).mc != null
                && ((GuiScreen) (Object) this).mc.theWorld != null;
        MenuChrome.drawBackdrop(width, height, inWorld);
        ci.cancel();
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void gnu$luxBackground(int tint, CallbackInfo ci) {
        if (!MenuChrome.shouldStyle((GuiScreen) (Object) this)) {
            return;
        }
        MenuChrome.drawBackdrop(width, height, false);
        ci.cancel();
    }
}
