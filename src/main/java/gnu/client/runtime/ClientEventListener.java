package gnu.client.runtime;

import gnu.client.config.ConfigManager;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.event.JumpEvent;
import gnu.client.event.PreAttackEvent;
import gnu.client.event.RightClickMouseEvent;
import gnu.client.event.StrafeEvent;
import gnu.client.module.modules.combat.AntiBotModule;
import gnu.client.module.modules.combat.DisplaceModule;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.module.modules.combat.RavenAntiBot;
import gnu.client.module.modules.combat.ReachModule;
import gnu.client.module.modules.network.LagrangeModule;
import gnu.client.module.modules.settings.ConfigManagerModule;
import gnu.client.common.GnuLog;
import gnu.client.runtime.mc.Mc;
import gnu.client.script.ScriptManager;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Drives module lifecycle on the Forge event bus.
 */
public final class ClientEventListener {

    /** Previous state of the RShift+R reload combo for edge detection. */
    private static boolean prevReloadCombo = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!Mc.isResolved())
            return;
        if (event.phase == TickEvent.Phase.START) {
            // ClickGuiScreen owns rebind key capture while it is open.
            if (!(Mc.currentScreen() instanceof gnu.client.ui.clickgui.ClickGuiScreen)) {
                ClientBootstrap.handleRebindKeyboard();
            }
            if (Mc.isInGame())
                ClientBootstrap.drainPendingAttackClicks();
            if (Mc.currentScreen() == null
                    && !ClientBootstrap.isRebindActive()) {
                ModuleManager.INSTANCE.handleKeybinds();
                checkScriptReloadCombo();
                if (Mc.isInGame())
                    ModuleManager.INSTANCE.tickStart();
            }
            return;
        }
        if (event.phase != TickEvent.Phase.END)
            return;
        ModuleManager.INSTANCE.tick();
        if (ConfigManagerModule.isAutoSaveEnabled())
            ConfigManager.instance().flushIfDue();
    }

    /**
     * Edge-triggered RShift+R combo to manually reload user scripts via
     * {@link ScriptManager#reloadAll()}. Runs after {@link ModuleManager#handleKeybinds()}
     * so {@link Keyboard#poll()} has already refreshed LWJGL state. Only fires when no
     * screen is open and no rebind is active (guarded by the caller in onClientTick).
     */
    private static void checkScriptReloadCombo() {
        boolean combo = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
                && Keyboard.isKeyDown(Keyboard.KEY_R);
        if (combo && !prevReloadCombo) {
            GnuLog.log("GUI_ scripts: manual reload triggered");
            try {
                ScriptManager.instance().reloadAll();
            } catch (Throwable t) {
                GnuLog.log("GUI_ scripts: manual reload failed: " + t);
            }
        }
        prevReloadCombo = combo;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent event) {
        if (!Mc.isInGame())
            return;
        if (event.button != 0 || !event.buttonstate)
            return;
        ReachModule.applyIfEnabled();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!Mc.isInGame())
            return;
        ModuleManager.INSTANCE.renderWorld(event.partialTicks);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL)
            return;
        if (!Mc.isInGame())
            return;
        ModuleManager.INSTANCE.overlay(event.resolution);
    }

    @SubscribeEvent
    public void onRenderName(RenderLivingEvent.Specials.Pre<?> event) {
        Module nametags = ModuleManager.INSTANCE.getModule("NameTags");
        if (nametags == null || !nametags.isEnabled())
            return;
        // NameTags skips AntiBot-flagged bots — leave vanilla nametags for them.
        if (AntiBotModule.isActive() && RavenAntiBot.isBot(event.entity))
            return;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPreAttack(PreAttackEvent event) {
        if (KillAuraModule.shouldCancelVanillaClick())
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickMouse(RightClickMouseEvent event) {
        if (KillAuraModule.shouldCancelVanillaClick())
            event.setCanceled(true);
    }

    @SubscribeEvent
    public void onStrafe(StrafeEvent event) {
        VelocityModule.patchStrafe(event);
        DisplaceModule.patchStrafe(event);
        try {
            ScriptManager.instance().dispatchStrafe(event.getForward(), event.getStrafe());
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onJump(JumpEvent event) {
        VelocityModule.patchJump(event);
        try {
            ScriptManager.instance().dispatchJump();
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!Mc.isInGame())
            return;
        // Single notify path — CombatAttackNotify dedupes vs KillAura noteAttack.
        CombatAttackNotify.onForgeAttack(event.target);

        try {
            ScriptManager.instance().dispatchAttack(event.target);
        } catch (Throwable ignored) {
        }

        Module lagrange = ModuleManager.INSTANCE.getModule("Lagrange");
        if (lagrange instanceof LagrangeModule && lagrange.isEnabled())
            ((LagrangeModule) lagrange).noteForgeAttack(event.target);
    }
}
