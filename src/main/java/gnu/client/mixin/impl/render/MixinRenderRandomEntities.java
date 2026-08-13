package gnu.client.mixin.impl.render;

import gnu.client.render.graphics.random.RandomEntities;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Render.class)
public abstract class MixinRenderRandomEntities {

    @Shadow
    protected RenderManager renderManager;

    @Shadow
    protected abstract ResourceLocation getEntityTexture(Entity entity);

    @Shadow
    public abstract void bindTexture(ResourceLocation location);

    @Inject(method = "bindEntityTexture", at = @At("HEAD"), cancellable = true)
    private void gnu$randomEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ResourceLocation vanilla = getEntityTexture(entity);
        ResourceLocation repl = RandomEntities.textureFor(entity, vanilla);
        if (repl != null && repl != vanilla) {
            bindTexture(repl);
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
