package gnu.client.mixin.impl.render;

import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Caps live particles to reduce per-frame tessellation and GC churn on busy servers.
 *
 * <p>Count is refreshed once per {@code updateEffects} (and on first spawn after that), so
 * spawn spam does not walk every particle list on each {@code addEffect}.
 */
@SideOnly(Side.CLIENT)
@Mixin(EffectRenderer.class)
public abstract class MixinEffectRenderer {

    @Shadow
    private List<EntityFX>[][] fxLayers;

    @Unique
    private int gnu$cachedParticleCount;

    @Unique
    private boolean gnu$particleCountValid;

    @Unique
    private int gnu$countParticles() {
        List<EntityFX>[][] layers = this.fxLayers;
        if (layers == null) {
            return 0;
        }
        int total = 0;
        for (List<EntityFX>[] row : layers) {
            if (row == null) {
                continue;
            }
            for (List<EntityFX> bucket : row) {
                if (bucket != null) {
                    total += bucket.size();
                }
            }
        }
        return total;
    }

    @Inject(method = "updateEffects", at = @At("RETURN"))
    private void gnu$refreshParticleCount(CallbackInfo ci) {
        gnu$cachedParticleCount = gnu$countParticles();
        gnu$particleCountValid = true;
    }

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void gnu$capParticles(EntityFX effect, CallbackInfo ci) {
        if (!PerformanceModule.reducedParticles()) {
            return;
        }
        if (!gnu$particleCountValid) {
            gnu$cachedParticleCount = gnu$countParticles();
            gnu$particleCountValid = true;
        }
        if (gnu$cachedParticleCount >= PerformanceModule.particleLimit()) {
            ci.cancel();
            return;
        }
        // Optimistic: updateEffects will reconcile removals next tick.
        gnu$cachedParticleCount++;
    }

    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void gnu$shaderParticles(Entity entityIn, float partialTicks, CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindTextured();
    }

    @Inject(method = "renderLitParticles", at = @At("HEAD"))
    private void gnu$shaderLitParticles(Entity entityIn, float partialTicks, CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindTexturedLit();
    }
}
