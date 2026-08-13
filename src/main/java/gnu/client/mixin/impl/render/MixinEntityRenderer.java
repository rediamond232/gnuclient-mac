package gnu.client.mixin.impl.render;

import gnu.client.event.PostMouseSelectionEvent;
import gnu.client.helper.RotationHelper;
import gnu.client.mixin.impl.accessors.IAccessorEntityRenderer;
import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.render.graphics.color.CustomColors;
import gnu.client.render.graphics.fog.GraphicsFog;
import gnu.client.render.MotionBlur;
import gnu.client.render.shaders.ShaderEngine;
import gnu.client.runtime.FreeLookHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F"))
    private float freelookRotationYaw(Entity entity) {
        return FreeLookHook.isActive() ? FreeLookHook.redirectYaw(entity) : entity.rotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F"))
    private float freelookPrevRotationYaw(Entity entity) {
        return FreeLookHook.isActive() ? FreeLookHook.redirectYaw(entity) : entity.prevRotationYaw;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F"))
    private float freelookRotationPitch(Entity entity) {
        return FreeLookHook.isActive() ? FreeLookHook.redirectPitch(entity) : entity.rotationPitch;
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F"))
    private float freelookPrevRotationPitch(Entity entity) {
        return FreeLookHook.isActive() ? FreeLookHook.redirectPitch(entity) : entity.prevRotationPitch;
    }

    @Redirect(method = "updateCameraAndRender", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;inGameHasFocus:Z"))
    private boolean freelookOverrideMouse(Minecraft mc) {
        return FreeLookHook.overrideMouse(mc);
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.AFTER))
    private void onRenderWorld(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PostMouseSelectionEvent());
    }

    @Inject(method = "getMouseOver", at = @At("HEAD"))
    private void onGetMouseOverHead(float partialTicks, CallbackInfo ci) {
        RotationHelper rh = RotationHelper.get();
        if (rh.swappedForMouseOver) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view != null && rh.isActive()) {
            Float yaw = rh.getServerYaw();
            Float pitch = rh.getServerPitch();
            if (yaw != null && !yaw.isNaN() && pitch != null && !pitch.isNaN()) {
                rh.beginSwap(view, yaw, pitch, true);
                rh.swappedForMouseOver = true;
            }
        }
    }

    @Inject(method = "getMouseOver", at = @At("RETURN"))
    private void onGetMouseOverReturn(float partialTicks, CallbackInfo ci) {
        RotationHelper rh = RotationHelper.get();
        if (rh.swappedForMouseOver) {
            Entity view = Minecraft.getMinecraft().getRenderViewEntity();
            if (view != null) {
                rh.endSwap(view);
            }
            rh.swappedForMouseOver = false;
        }
    }

    /**
     * Dynamic FOV: when disabled, sprint / fly / bow / speed potions no longer scale FOV.
     * Replaces OptiFine's Dynamic FOV toggle for the vanilla {@code getFovModifier} path.
     */
    @Redirect(
            method = "updateFovModifierHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/AbstractClientPlayer;getFovModifier()F"),
            require = 1)
    private float gnu$dynamicFovModifier(AbstractClientPlayer player) {
        if (!PerformanceModule.dynamicFov()) {
            return 1.0F;
        }
        return player.getFovModifier();
    }

    /**
     * Clear Weather: skip rain/snow rendering entirely.
     */
    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true)
    private void gnu$clearWeather(float partialTicks, CallbackInfo ci) {
        if (PerformanceModule.clearWeather()) {
            ci.cancel();
        }
    }

    /**
     * Skip heavy weather (rain/snow) pass while a fullscreen GUI (e.g. ClickGUI) covers the
     * world — the world is hidden anyway, so the cost is pure waste.
     */
    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true)
    private void gnu$skipWeatherWhenGuiOpen(CallbackInfo ci) {
        if (!PerformanceModule.skipWorldWhenGuiOpen())
            return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null) {
            ci.cancel();
        }
    }

    /**
     * Skip cloud rendering while a fullscreen GUI covers the world — the player can't see
     * the sky anyway, so the cloud pass is pure waste. Mirrors the weather-skip toggle.
     * Note: the MCP method is {@code renderCloudsCheck(RenderGlobal, float, int)}, not
     * {@code renderClouds}.
     */
    @Inject(method = "renderCloudsCheck", at = @At("HEAD"), cancellable = true)
    private void gnu$skipCloudsWhenGuiOpen(CallbackInfo ci) {
        if (!PerformanceModule.skipCloudsWhenGuiOpen())
            return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null) {
            ci.cancel();
        }
    }

    /**
     * No Hurt Cam: cancel the camera shake/zoom applied when the player takes damage.
     * Purely a view effect — no world/entity state touched, so it's safe alongside OptiFine.
     */
    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    private void gnu$noHurtCam(CallbackInfo ci) {
        if (PerformanceModule.noHurtCam()) {
            ci.cancel();
        }
    }

    /**
     * Chunk Build Budget: widen the per-frame time slice given to chunk uploads.
     *
     * <p>Vanilla computes the deadline passed to {@code renderWorld(F, J)} as
     * {@code 1_000_000_000 / max(60, min(getDebugFPS(), limitFramerate)) / 4} nanoseconds — one
     * quarter of a frame, and the FPS term is the *actual* frame rate, so the higher your FPS
     * the smaller the slice: ~4.2 ms at 60 FPS but only ~1.0 ms at 240. That is why chunks
     * visibly trickle in on a fast machine. Raising the share of the frame lets
     * {@code RenderGlobal.updateChunks} drain more of {@code chunksToUpdate} per frame.
     *
     * <p>The literal {@code 4} occurs exactly once in {@code updateCameraAndRender}, so this
     * constant target is unambiguous. OptiFine is unsupported; this injection is required.
     */
    @ModifyConstant(method = "updateCameraAndRender", constant = @Constant(intValue = 4), require = 1)
    private int gnu$chunkBuildBudgetDivisor(int original) {
        return PerformanceModule.chunkBuildBudgetDivisor();
    }

    @Redirect(
            method = "setupFog",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;setFogStart(F)V"))
    private void gnu$fogStart(float start) {
        float far = ((IAccessorEntityRenderer) this).getFarPlaneDistance();
        if (start <= 0.001f) {
            GlStateManager.setFogStart(0f);
            return;
        }
        if (start <= far * 0.1f && GraphicsFog.skipVoidFog()) {
            GlStateManager.setFogStart(far * GraphicsModule.fogStart());
            return;
        }
        GlStateManager.setFogStart(far * GraphicsModule.fogStart());
    }

    @Redirect(
            method = "setupFog",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;setFogDensity(F)V"))
    private void gnu$fogDensity(float density) {
        float d = density;
        if (GraphicsModule.clearWater() && d > 0.005f && d < 0.5f) {
            d = Math.min(d, 0.02f);
        }
        GlStateManager.setFogDensity(d);
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void gnu$fogReturn(int pass, float partialTicks, CallbackInfo ci) {
        if (GraphicsModule.fogOff()) {
            GlStateManager.disableFog();
        }
    }

    @Redirect(
            method = "updateFogColor",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getFogColor(F)Lnet/minecraft/util/Vec3;"))
    private Vec3 gnu$customFogColor(WorldClient world, float partialTicks) {
        return GraphicsFog.tintFog(world.getFogColor(partialTicks));
    }

    @Redirect(
            method = "updateFogColor",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getSkyColor(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;"))
    private Vec3 gnu$customSkyColor(WorldClient world, Entity entity, float partialTicks) {
        return GraphicsFog.tintSky(world.getSkyColor(entity, partialTicks));
    }

    @Inject(method = "updateLightmap", at = @At("HEAD"), cancellable = true)
    private void gnu$customLightmap(float partialTicks, CallbackInfo ci) {
        IAccessorEntityRenderer acc = (IAccessorEntityRenderer) this;
        int[] colors = acc.getLightmapColors();
        if (CustomColors.applyLightmap(colors, acc.getTorchFlickerX(), partialTicks)) {
            acc.getLightmapTexture().updateDynamicTexture();
            acc.setLightmapUpdateNeeded(false);
            ci.cancel();
        }
    }

    @Inject(method = "renderWorldPass", at = @At("HEAD"))
    private void gnu$aa(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (GraphicsModule.antialiasing() > 0) {
            GL11.glEnable(org.lwjgl.opengl.GL13.GL_MULTISAMPLE);
        }
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V",
                    shift = At.Shift.AFTER))
    private void gnu$shaderBegin(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        ShaderEngine.INSTANCE.beginWorldPass(partialTicks);
    }

    @Inject(method = "renderWorldPass", at = @At("RETURN"))
    private void gnu$shaderWorldPassEnd(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        ShaderEngine.INSTANCE.onWorldPassReturn();
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void gnu$shaderHandBegin(float partialTicks, int xOffset, CallbackInfo ci) {
        ShaderEngine.INSTANCE.beginHand();
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void gnu$shaderHandEnd(float partialTicks, int xOffset, CallbackInfo ci) {
        ShaderEngine.INSTANCE.endHand();
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void gnu$motionBlur(float partialTicks, int xOffset, CallbackInfo ci) {
        MotionBlur.apply();
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void gnu$shaderWorldEnd(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        ShaderEngine.INSTANCE.onRenderWorldReturn();
    }

    @Inject(method = "renderRainSnow", at = @At("HEAD"))
    private void gnu$shaderWeather(float partialTicks, CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindWeather();
    }

    @Inject(method = "renderCloudsCheck", at = @At("HEAD"))
    private void gnu$shaderClouds(CallbackInfo ci) {
        ShaderEngine.INSTANCE.bindClouds();
    }

    @Inject(method = "updateCameraAndRender", at = @At("RETURN"))
    private void gnu$smoothFps(float partialTicks, long nanoTime, CallbackInfo ci) {
        if (GraphicsModule.smoothFps()) {
            GL11.glFinish();
        }
    }
}
