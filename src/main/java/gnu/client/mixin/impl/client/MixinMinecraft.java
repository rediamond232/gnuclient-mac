package gnu.client.mixin.impl.client;

import gnu.client.event.*;
import gnu.client.helper.RotationHelper;
import gnu.client.mixin.impl.accessors.IAccessorRenderGlobal;
import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.runtime.FreeLookHook;
import gnu.client.ui.menu.GnuMainMenuScreen;
import gnu.client.render.graphics.GraphicsReload;
import gnu.client.render.graphics.lights.DynamicLights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.BEFORE))
    public void onBeforeGetMouseOver(CallbackInfo ci) {
        RotationHelper.get().updateServerRotations();
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V", shift = At.Shift.AFTER))
    public void onRunTickMouseOver(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PostMouseSelectionEvent());
    }

    @Inject(method = "runTick", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/settings/GameSettings;chatVisibility:Lnet/minecraft/entity/player/EntityPlayer$EnumChatVisibility;"))
    private void injectBeforeChatVisibility(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PrePlayerInteractEvent());
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", ordinal = 2))
    private void onRunTick(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new PreInputEvent());
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    public void onRunGameLoop(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new RunGameLoopEvent());
    }

    @Inject(method = "clickMouse", at = @At("HEAD"), cancellable = true)
    public void injectClickMouse(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        PreAttackEvent preAttack = new PreAttackEvent(mc.objectMouseOver);
        MinecraftForge.EVENT_BUS.post(preAttack);
        if (preAttack.isCanceled()) {
            ci.cancel();
            return;
        }
        MinecraftForge.EVENT_BUS.post(new ClickMouseEvent());
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"), cancellable = true)
    public void injectRightClickMouse(CallbackInfo ci) {
        RightClickMouseEvent event = new RightClickMouseEvent();
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    public void onRunTickStart(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new GameTickEvent());
    }

    /**
     * Performance: once per tick, force vanilla quality settings that are expensive.
     * Smooth lighting is the biggest non-chunk CPU cost in block rendering; disabling it
     * skips the per-vertex ambient-occlusion pass. Done here (not via redirects scattered
     * across WorldRenderer/BlockModelRenderer) so the user's other settings are respected
     * whenever the toggle is off.
     */
    @Inject(method = "runTick", at = @At("HEAD"))
    public void gnu$applyPerfSettings(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        GameSettings settings = mc.gameSettings;
        if (settings == null)
            return;
        if (PerformanceModule.disableSmoothLighting()) {
            settings.ambientOcclusion = 0;
        }
        if (PerformanceModule.cloudsOff()) {
            settings.clouds = 0;
        }
        if (PerformanceModule.fastGraphics()) {
            settings.fancyGraphics = false;
        }
        if (PerformanceModule.minimalParticles()) {
            settings.particleSetting = 2;
        }
        if (PerformanceModule.forceVbo() && OpenGlHelper.vboSupported) {
            gnu$ensureVboSkySynced(mc, settings);
        }
        if (PerformanceModule.entityShadowsOff()) {
            settings.entityShadows = false;
        }
        if (PerformanceModule.fboOff()) {
            settings.fboEnable = false;
        }
        if (PerformanceModule.viewBobbingOff()) {
            settings.viewBobbing = false;
        }
        if (PerformanceModule.mipmapsOff()) {
            settings.mipmapLevels = 0;
        }
        if (PerformanceModule.limitFps()) {
            settings.enableVsync = false;
            settings.limitFramerate = PerformanceModule.fpsCap();
        }
        if (PerformanceModule.reducedRenderDistance()
                && settings.renderDistanceChunks > PerformanceModule.renderDistanceChunks()) {
            settings.renderDistanceChunks = PerformanceModule.renderDistanceChunks();
        }
        if (PerformanceModule.dynamicRenderDistance()) {
            gnu$applyDynamicRenderDistance(settings);
        }
        DynamicLights.tick();
    }

    @Inject(method = "refreshResources", at = @At("RETURN"))
    private void gnu$graphicsReload(CallbackInfo ci) {
        GraphicsReload.reload();
    }

    /**
     * Force VBO without rebuilding sky/star geometry leaves RenderGlobal.vboEnabled out of
     * sync with GameSettings.useVbo — stars then draw as black cubes (especially on macOS).
     * Only call loadRenderers when the mode actually flips.
     */
    @Unique
    private static void gnu$ensureVboSkySynced(Minecraft mc, GameSettings settings) {
        settings.useVbo = true;
        if (mc.renderGlobal == null) {
            return;
        }
        // Sky/star meshes are built for either VBO or display-list mode. Flipping useVbo
        // without loadRenderers leaves stars drawing with the wrong path → black cubes.
        if (!((IAccessorRenderGlobal) mc.renderGlobal).isVboEnabled() && OpenGlHelper.useVbo()) {
            mc.renderGlobal.loadRenderers();
        }
    }

    /**
     * Dynamic Render Distance: adapt {@code renderDistanceChunks} to the current frame rate
     * so the game stays responsive under load without the player manually tweaking it.
     *
     * <p>Hysteresis: drop one chunk when FPS is below the low threshold, raise one chunk
     * when FPS is above the high threshold. A per-tick cooldown prevents thrashing. The
     * configured {@code Render Distance} slider is the ceiling and {@code Dynamic RD Min}
     * is the floor.
     */
    private static int gnu$dynRdCooldown = 0;

    private void gnu$applyDynamicRenderDistance(GameSettings settings) {
        if (gnu$dynRdCooldown > 0) {
            gnu$dynRdCooldown--;
            return;
        }
        int fps = Minecraft.getDebugFPS();
        int min = PerformanceModule.dynamicRenderDistanceMin();
        int max = PerformanceModule.renderDistanceChunks();
        int current = settings.renderDistanceChunks;
        if (current < min)
            current = min;
        if (current > max)
            current = max;
        if (fps < 30 && current > min) {
            settings.renderDistanceChunks = current - 1;
            gnu$dynRdCooldown = 40;
        } else if (fps > 55 && current < max) {
            settings.renderDistanceChunks = current + 1;
            gnu$dynRdCooldown = 80;
        }
    }

    @Inject(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/profiler/Profiler;startSection(Ljava/lang/String;)V",
            ordinal = 0,
            shift = At.Shift.BEFORE
        )
    )
    public void onRunTickAfterRightClickDelay(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new RightClickDelayTickEvent());
    }

    /**
     * Replace title screens ({@link GuiMainMenu} or mod subclasses such as OptiFine).
     * Does not cover {@code displayGuiScreen(null)}: that builds {@code new GuiMainMenu()}
     * inside the method after HEAD, so {@link #gnu$ensureCustomMainMenu} catches it.
     */
    @ModifyVariable(method = "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", at = @At("HEAD"), argsOnly = true)
    private GuiScreen gnu$replaceMainMenu(GuiScreen screen) {
        if (screen instanceof GuiMainMenu) {
            return new GnuMainMenuScreen();
        }
        return screen;
    }

    /**
     * First boot / disconnect uses {@code displayGuiScreen(null)} which constructs
     * {@link GuiMainMenu} after args are read. Replace any {@link GuiMainMenu} (including
     * subclasses); {@link GnuMainMenuScreen} is not a {@code GuiMainMenu}, so no recurse.
     */
    @Inject(method = "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", at = @At("RETURN"))
    private void gnu$ensureCustomMainMenu(GuiScreen screen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        GuiScreen current = mc.currentScreen;
        if (current instanceof GuiMainMenu) {
            mc.displayGuiScreen(new GnuMainMenuScreen());
        }
    }

    @Inject(method = "displayGuiScreen(Lnet/minecraft/client/gui/GuiScreen;)V", at = @At("HEAD"))
    public void onDisplayGuiScreen(GuiScreen guiScreen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        GuiScreen previousGui = mc.currentScreen;
        GuiScreen setGui = guiScreen != null ? guiScreen : previousGui;
        MinecraftForge.EVENT_BUS.post(new GuiUpdateEvent(setGui, guiScreen != null));
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"))
    public void changeCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
        PreSlotScrollEvent event = new PreSlotScrollEvent(slot, inventoryPlayer.currentItem);
        MinecraftForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            inventoryPlayer.changeCurrentItem(slot);
        }
    }

    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I", opcode = Opcodes.PUTFIELD))
    private void onSetThirdPersonView(GameSettings gameSettings, int value) {
        if (!FreeLookHook.isActive()) {
            gameSettings.thirdPersonView = value;
        }
    }

    @Redirect(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I", opcode = Opcodes.PUTFIELD))
    private void onSetCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
        SlotUpdateEvent e = new SlotUpdateEvent(slot);
        MinecraftForge.EVENT_BUS.post(e);
        if (!e.isCanceled()) {
            inventoryPlayer.currentItem = slot;
        }
    }
}
