package gnu.client;

import gnu.client.command.ChatCommandHandler;
import gnu.client.common.GnuLog;
import gnu.client.config.ConfigManager;
import gnu.client.helper.RotationHelper;
import gnu.client.lag.handler.UnifiedLagHandler;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.AimAssistModule;
import gnu.client.module.modules.combat.AntiBotModule;
import gnu.client.module.modules.combat.AutoClickerModule;
import gnu.client.module.modules.combat.HitSelectModule;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.combat.KeepSprintModule;
import gnu.client.module.modules.combat.MoreKBModule;
import gnu.client.module.modules.combat.ReachModule;
import gnu.client.module.modules.combat.DisplaceModule;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.module.modules.combat.WTapModule;
import gnu.client.module.modules.movement.FastStopModule;
import gnu.client.module.modules.movement.FlyModule;
import gnu.client.module.modules.movement.LongjumpModule;
import gnu.client.module.modules.movement.SpeedModule;
import gnu.client.module.modules.movement.SpiderModule;
import gnu.client.module.modules.movement.SprintModule;
import gnu.client.module.modules.movement.StasisModule;
import gnu.client.module.modules.movement.TimerModule;
import gnu.client.module.modules.network.BacktrackModule;
import gnu.client.module.modules.network.BlinkModule;
import gnu.client.module.modules.network.KnockbackDelayModule;
import gnu.client.module.modules.network.LagrangeModule;
import gnu.client.module.modules.network.PingFixModule;
import gnu.client.module.modules.player.AutoPlaceModule;
import gnu.client.module.modules.player.BridgeAssistModule;
import gnu.client.module.modules.player.DelayRemoverModule;
import gnu.client.module.modules.player.FastPlaceModule;
import gnu.client.module.modules.player.GhostBlocksModule;
import gnu.client.module.modules.player.NoFallModule;
import gnu.client.module.modules.player.NoSlowModule;
import gnu.client.module.modules.player.scaffold.ScaffoldModule;
import gnu.client.module.impl.client.Settings;
import gnu.client.module.modules.settings.ClickGuiModule;
import gnu.client.module.modules.settings.ConfigManagerModule;
import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.module.modules.settings.ShadersModule;
import gnu.client.module.modules.settings.ThemeModule;
import gnu.client.render.graphics.GraphicsEvents;
import gnu.client.render.graphics.GraphicsReload;
import gnu.client.module.modules.visual.BedEspModule;
import gnu.client.module.modules.visual.EspModule;
import gnu.client.module.modules.visual.FreeLookModule;
import gnu.client.module.modules.visual.AnimationsModule;
import gnu.client.module.modules.visual.HudModule;
import gnu.client.module.modules.visual.ItemEspModule;
import gnu.client.module.modules.visual.MotionBlurModule;
import gnu.client.module.modules.visual.NameTagsModule;
import gnu.client.module.modules.visual.TracersModule;
import gnu.client.module.modules.visual.ZoomModule;
import gnu.client.runtime.ClientEventListener;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.runtime.PacketEventsBridge;
import gnu.client.runtime.mc.Mc;
import gnu.client.script.ScriptManager;
import gnu.client.ui.clickgui.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = GnuClientMod.MOD_ID, name = "GNUClient", version = GnuClientMod.VERSION, acceptedMinecraftVersions = "[1.8.9]")
public class GnuClientMod {

    public static final String MOD_ID = "gnuclient";
    public static final String VERSION = "1.9.0";

    public static GnuClientMod instance;
    public static ModuleManager moduleManager;
    public static UnifiedLagHandler lagHandler;
    public static ClickGuiScreen clickGui;

    public GnuClientMod() {
        instance = this;
        moduleManager = ModuleManager.INSTANCE;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Runtime.getRuntime().addShutdownHook(new Thread(ConfigManager.instance()::flush));

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ClientEventListener());
        MinecraftForge.EVENT_BUS.register(RotationHelper.get());
        MinecraftForge.EVENT_BUS.register(lagHandler = new UnifiedLagHandler());
        MinecraftForge.EVENT_BUS.register(new PacketEventsBridge());
        // Coexistence: when user Blink module is also on, both may hold; prefer not combining with KA blink modes.
        // Lagrange pause is deferred to KA autoblock activate (Task 7/8), not required at register.
        gnu.client.runtime.packet.PacketEvents.register(gnu.client.runtime.PlayerStateManager.INSTANCE);
        gnu.client.runtime.packet.PacketEvents.register(gnu.client.runtime.BlinkManager.INSTANCE);
        gnu.client.runtime.FloatManager.init();

        MinecraftForge.EVENT_BUS.register(new GraphicsEvents());
        registerModules();
        GraphicsReload.reload();
        ConfigManager.setLoading(false);
        ConfigManager.instance().load();
        ScriptManager.instance().reloadAll();
        ChatCommandHandler.register();

        clickGui = new ClickGuiScreen();
        ClientBootstrap.markInitialized();
        GnuLog.log("GNUClient Forge mod initialized modules=" + moduleManager.all().size());
    }

    private void registerModules() {
        ConfigManager.setLoading(true);
        moduleManager.reset();
        moduleManager.init();
        safeRegister(new Settings());
        safeRegister(new ThemeModule());
        safeRegister(new ClickGuiModule());
        safeRegister(new ConfigManagerModule());
        safeRegister(new PerformanceModule());
        safeRegister(new GraphicsModule());
        safeRegister(new ShadersModule());
        safeRegister(new WTapModule());
        safeRegister(new KeepSprintModule());
        safeRegister(new MoreKBModule());
        safeRegister(new SprintModule());
        safeRegister(new SpeedModule());
        safeRegister(new FlyModule());
        safeRegister(new NoSlowModule());
        safeRegister(new AutoPlaceModule());
        safeRegister(new BridgeAssistModule());
        safeRegister(new FastStopModule());
        safeRegister(new TimerModule());
        safeRegister(new LongjumpModule());
        safeRegister(new VelocityModule());
        safeRegister(new DisplaceModule());
        safeRegister(new FastPlaceModule());
        safeRegister(new DelayRemoverModule());
        safeRegister(new StasisModule());
        safeRegister(new SpiderModule());
        safeRegister(new AntiBotModule());
        safeRegister(new AimAssistModule());
        safeRegister(new AutoClickerModule());
        safeRegister(new HitSelectModule());
        safeRegister(new KillAuraModule());
        safeRegister(new ReachModule());
        safeRegister(new EspModule());
        safeRegister(new TracersModule());
        safeRegister(new ItemEspModule());
        safeRegister(new BedEspModule());
        safeRegister(new NameTagsModule());
        safeRegister(new HudModule());
        safeRegister(new AnimationsModule());
        safeRegister(new BlinkModule());
        safeRegister(new KnockbackDelayModule());
        safeRegister(new BacktrackModule());
        safeRegister(new LagrangeModule());
        safeRegister(new PingFixModule());
        safeRegister(new FreeLookModule());
        safeRegister(new ZoomModule());
        safeRegister(new MotionBlurModule());
        safeRegister(new NoFallModule());
        safeRegister(new ScaffoldModule());
        safeRegister(new GhostBlocksModule());
    }

    private void safeRegister(gnu.client.module.Module module) {
        try {
            moduleManager.register(module);
        } catch (Throwable t) {
            GnuLog.log("MODULE FAIL " + module.getClass().getSimpleName() + ": " + t);
        }
    }

    public static void openClickGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.displayGuiScreen(clickGui);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Mc.isInGame()) {
            TimerModule.maintain();
        }
    }
}
