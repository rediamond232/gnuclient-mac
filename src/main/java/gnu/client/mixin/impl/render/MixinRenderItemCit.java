package gnu.client.mixin.impl.render;

import gnu.client.render.graphics.cit.CustomItems;
import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public abstract class MixinRenderItemCit {

    @Shadow
    protected abstract void renderModel(IBakedModel model, ItemStack stack);

    @Inject(method = "renderItemIntoGUI", at = @At("HEAD"), cancellable = true)
    private void gnu$citGui(ItemStack stack, int x, int y, CallbackInfo ci) {
        ResourceLocation tex = CustomItems.textureFor(stack);
        if (tex == null) {
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GlStateManager.disableLighting();
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 16, 16, 16, 16);
        GlStateManager.enableLighting();
        ci.cancel();
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V",
            at = @At("RETURN"))
    private void gnu$citEmissive(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        TextureAtlasSprite sprite = CustomItems.spriteFor(stack);
        if (sprite == null) {
            return;
        }
        // Atlas sprite already registered; GUI path handles standalone PNGs.
    }

    @Inject(method = "renderEffect", at = @At("HEAD"))
    private void gnu$shaderGlintBegin(IBakedModel model, CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindGlint();
    }

    @Inject(method = "renderEffect", at = @At("RETURN"))
    private void gnu$shaderGlintEnd(IBakedModel model, CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindItem();
    }
}
