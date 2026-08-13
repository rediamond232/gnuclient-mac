package gnu.client.mixin.impl.client;

import gnu.client.ui.menu.MenuChrome;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot {

    @Shadow
    public int width;

    @Shadow
    public int top;

    @Shadow
    public int bottom;

    @Shadow
    public int left;

    @Shadow
    public int headerPadding;

    @Shadow
    public int slotHeight;

    @Shadow
    protected boolean showSelectionBox;

    @Shadow
    protected abstract int getSize();

    @Shadow
    protected abstract boolean isSelected(int index);

    @Shadow
    protected abstract void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY);

    @Shadow
    protected abstract void func_178040_a(int slotIndex, int x, int y);

    @Shadow
    public abstract int getListWidth();

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
    private void gnu$luxOverlay(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
        if (!MenuChrome.shouldStyle()) {
            return;
        }
        GuiSlot self = (GuiSlot) (Object) this;
        MenuChrome.drawOverlayStrip(self.left, startY, endY, self.right);
        ci.cancel();
    }

    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void gnu$luxContainerBg(Tessellator tessellator, CallbackInfo ci) {
        if (!MenuChrome.shouldStyle()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "drawSelectionBox", at = @At("HEAD"), cancellable = true)
    private void gnu$luxSelection(int x, int y, int mouseX, int mouseY, CallbackInfo ci) {
        if (!MenuChrome.shouldStyle()) {
            return;
        }
        int size = getSize();
        for (int i = 0; i < size; i++) {
            int slotY = y + i * this.slotHeight + this.headerPadding;
            int slotH = this.slotHeight - 4;
            if (slotY > this.bottom || slotY + slotH < this.top) {
                func_178040_a(i, x, slotY);
            }
            if (this.showSelectionBox && isSelected(i)) {
                int listLeft = this.left + (this.width / 2 - getListWidth() / 2);
                int listRight = this.left + this.width / 2 + getListWidth() / 2;
                MenuChrome.drawListSelection(listLeft, slotY - 2, listRight, slotY + slotH + 2);
            }
            drawSlot(i, x, slotY, slotH, mouseX, mouseY);
        }
        ci.cancel();
    }
}
