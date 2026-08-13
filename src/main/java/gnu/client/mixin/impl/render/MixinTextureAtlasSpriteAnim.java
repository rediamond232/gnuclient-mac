package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.GraphicsModule;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlasSprite.class)
public class MixinTextureAtlasSpriteAnim {

    @Inject(method = "updateAnimation", at = @At("HEAD"), cancellable = true)
    private void gnu$customAnimations(CallbackInfo ci) {
        if (!GraphicsModule.customAnimations()) {
            ci.cancel();
        }
    }
}
