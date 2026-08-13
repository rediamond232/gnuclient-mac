package gnu.client.module.modules.visual;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.ui.hud.HudRenderer;
import gnu.client.ui.hud.NotificationQueue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * In-game HUD overlay: Tux watermark, ArrayList, TargetHUD, and toast notifications.
 * Rendering is delegated to {@link HudRenderer}; toggle callbacks are
 * identity-only dirty signals (the enabled boolean is untrusted).
 */
public final class HudModule extends Module {

    private static HudModule instance;

    private final BoolSetting showWatermark = addSetting(new BoolSetting("Watermark", true));
    private final BoolSetting showArray = addSetting(new BoolSetting("Array", true));
    private final BoolSetting showTargetHud = addSetting(new BoolSetting("TargetHUD", true));
    private final BoolSetting showNotifications = addSetting(new BoolSetting("Notifications", true));
    private final BoolSetting showSuffixes = addSetting(new BoolSetting("Show suffixes", true));

    public HudModule() {
        super("HUD", "Watermark, ArrayList, TargetHUD and toggle notifications", Category.VISUALS);
        instance = this;
        showSuffixes.visibleWhen(() -> showArray.getValue());
    }

    public static HudModule instance() {
        return instance;
    }

    /**
     * Kept for API stability from {@link Module#setEnabled}. Dirty identity is
     * marked there via {@code ModuleToggleSignals}; the {@code enabled} argument
     * is untrusted and must not be used. Final state is sampled by {@link HudRenderer}.
     */
    public static void onModuleToggled(Module module, boolean enabled) {
        // no-op — mark already done in Module.setEnabled on real transitions
    }

    public boolean wantsWatermark() {
        return showWatermark.getValue();
    }

    public boolean wantsArray() {
        return showArray.getValue();
    }

    public boolean wantsTargetHud() {
        return showTargetHud.getValue();
    }

    public boolean wantsNotifications() {
        return showNotifications.getValue();
    }

    public boolean wantsSuffixes() {
        return showSuffixes.getValue();
    }

    public static boolean hasActiveNotifications() {
        HudRenderer renderer = HudRenderer.instance();
        return renderer != null && renderer.hasActiveNotifications();
    }

    public static boolean shouldDrawOverlay() {
        HudModule hud = instance();
        if (hud == null || !hud.isEnabled()) {
            return false;
        }
        if (hud.wantsWatermark() || hud.wantsArray() || hud.wantsTargetHud()) {
            return true;
        }
        return hud.wantsNotifications() && hasActiveNotifications();
    }

    public static List<String> enabledModuleNames() {
        List<Module> enabled = new ArrayList<Module>();
        for (Module m : ModuleManager.INSTANCE.all()) {
            if (!m.isEnabled() || m instanceof HudModule) {
                continue;
            }
            if (m.getCategory() == Category.SETTINGS) {
                continue;
            }
            if (m.isHidden()) {
                continue;
            }
            enabled.add(m);
        }
        enabled.sort(Comparator.comparing(Module::getName));
        List<String> names = new ArrayList<String>(enabled.size());
        for (Module m : enabled) {
            names.add(m.getName());
        }
        return names;
    }

    public static int notificationCount() {
        NotificationQueue q = HudRenderer.instance().getNotificationQueue();
        return q.liveCount() + q.exitingCount();
    }

    public static String notificationText(int index) {
        List<NotificationQueue.Entry> entries = HudRenderer.instance().getNotificationQueue().bottomFirst();
        if (index < 0 || index >= entries.size()) {
            return "";
        }
        NotificationQueue.Entry n = entries.get(index);
        return n.moduleName + (n.enabled ? " enabled" : " disabled");
    }

    public static boolean notificationEnabled(int index) {
        List<NotificationQueue.Entry> entries = HudRenderer.instance().getNotificationQueue().bottomFirst();
        if (index < 0 || index >= entries.size()) {
            return false;
        }
        return entries.get(index).enabled;
    }

    public static float notificationAlpha(int index) {
        List<NotificationQueue.Entry> entries = HudRenderer.instance().getNotificationQueue().bottomFirst();
        if (index < 0 || index >= entries.size()) {
            return 0.0f;
        }
        return entries.get(index).alpha();
    }

    @Override
    public void onEnable() {
        HudRenderer.instance().onHudEnabled();
    }

    @Override
    public void onDisable() {
        HudRenderer.instance().onHudDisabled();
    }

    @Override
    public void onOverlay(Object scaledResolution) {
        if (!isEnabled()) {
            return;
        }
        HudRenderer.instance().render(scaledResolution);
    }
}
