package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.render.EntityCulling;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity render cutoffs — reduced distance + strong render-only culling.
 *
 * <p>Neither path touches gameplay state (ticks / collision / combat raytraces).
 */
@SideOnly(Side.CLIENT)
@Mixin(RenderManager.class)
public abstract class MixinRenderManagerCull {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void gnu$entityCulling(Entity entityIn, ICamera camera, double camX, double camY,
            double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (!PerformanceModule.entityCulling() && !PerformanceModule.reducedEntityDistance()) {
            return;
        }
        if (EntityCulling.shouldCull(entityIn, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }
}
