package gnu.client.mixin.impl.client;

import gnu.client.ui.menu.MenuChrome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLockIconButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiButton.class)
public abstract class MixinGuiButton {

    @Shadow
    protected boolean hovered;

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void gnu$luxButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        if (!MenuChrome.shouldStyle()) {
            return;
        }
        GuiButton self = (GuiButton) (Object) this;
        if (self instanceof GuiLockIconButton) {
            return;
        }
        this.hovered = mouseX >= self.xPosition && mouseY >= self.yPosition
                && mouseX < self.xPosition + self.width
                && mouseY < self.yPosition + self.height;
        MenuChrome.drawVanillaControl(self, mouseX, mouseY);
        ci.cancel();
    }
}
