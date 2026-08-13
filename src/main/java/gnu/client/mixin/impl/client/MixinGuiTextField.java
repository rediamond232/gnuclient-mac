package gnu.client.mixin.impl.client;

import gnu.client.ui.menu.MenuChrome;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField {

    @Shadow
    public int xPosition;

    @Shadow
    public int yPosition;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    public abstract boolean getVisible();

    @Shadow
    public abstract boolean getEnableBackgroundDrawing();

    @Shadow
    public abstract boolean isFocused();

    @Inject(method = "drawTextBox", at = @At("HEAD"))
    private void gnu$luxField(CallbackInfo ci) {
        if (!MenuChrome.shouldStyle() || !getVisible() || !getEnableBackgroundDrawing()) {
            return;
        }
        MenuChrome.drawTextField(xPosition, yPosition, width, height, isFocused());
    }

    @Redirect(
            method = "drawTextBox",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V")
    )
    private void gnu$skipVanillaFieldRect(int left, int top, int right, int bottom, int color) {
        if (!MenuChrome.shouldStyle()) {
            Gui.drawRect(left, top, right, bottom, color);
        }
    }
}
