package gnu.client.ui.hud;

import gnu.client.GnuClientMod;
import gnu.client.config.ConfigManager;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.settings.ClickGuiModule;
import gnu.client.module.modules.visual.HudModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.ui.ClientTheme;
import gnu.client.ui.UiBlur;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Race-safe ArrayList + notification overlay. Drain dirty identities, sample
 * {@link Module#isEnabled()}, then animate.
 */
public final class HudRenderer {

    private static final float ARRAY_MARGIN = 6f;
    /** Small gap so rounded frost pills don't visually mash. */
    private static final float ARRAY_GAP = 2f;
    private static final float ARRAY_PAD_X = 3f;
    private static final float ARRAY_PAD_R = 3f;
    private static final float ARRAY_MIN_H = 13f;
    /** Rounded frost pill behind each ArrayList label. */
    private static final float ARRAY_BLUR_RADIUS = 5.5f;
    private static final float ARRAY_NAME_SIZE = 7.5f;
    private static final float ARRAY_SUFFIX_SIZE = 6.5f;
    private static final float ARRAY_SUFFIX_GAP = 2f;
    /** Vertical | accent bar behind each label (left of text). */
    private static final float ARRAY_BAR_W = 2.75f;
    private static final float ARRAY_TEXT_GLOW = 0.4f;
    private static final float ARRAY_BAR_GLOW = 0.35f;
    /** Phase gap between rows so Color1↔Color2 wave is obvious down the list. */
    private static final double ARRAY_WAVE_ROW_MS = 220.0;
    private static final float TOAST_GAP = 6f;
    private static final float TOAST_MARGIN = 12f;
    private static final float TOAST_ICON = 26f;
    private static final float TOAST_PAD = 10f;
    private static final float WATERMARK_MARGIN = 6f;
    /** On-screen size of the Tux watermark icon (px at GUI scale 1). */
    private static final float WATERMARK_ICON = 18f;
    private static final ResourceLocation WATERMARK_TUX =
            new ResourceLocation(GnuClientMod.MOD_ID, "textures/gui/tux.png");
    private static final float TARGET_W = 140f;
    private static final float TARGET_H = 42f;
    private static final int MAX_SUFFIX_LEN = 24;
    private static final int MAX_SUFFIX_PARTS = 2;
    private static final float SETTLE_EPS = 0.01f;

    private static final HudRenderer INSTANCE = new HudRenderer();

    private final UiKit.UiClock clock = new UiKit.UiClock();
    private final NotificationQueue notifications = new NotificationQueue();
    private final Map<Module, Boolean> baselines = new IdentityHashMap<Module, Boolean>();
    private final Map<String, ArrayRow> rows = new HashMap<String, ArrayRow>();
    private final Map<String, Module> enabledEligible = new HashMap<String, Module>();
    private final List<ArrayRow> sortedRows = new ArrayList<ArrayRow>();
    private boolean seeded;
    private boolean pendingSilentReseed = true;
    private boolean sawLoading;
    /** Smoothed TargetHUD health display. */
    private float targetHudDisplayHp = -1f;
    private int targetHudEntityId = Integer.MIN_VALUE;

    private HudRenderer() {
    }

    public static HudRenderer instance() {
        return INSTANCE;
    }

    public NotificationQueue getNotificationQueue() {
        return notifications;
    }

    /** HUD enabled — silent reseed next frame; do not toast config/script state. */
    public void onHudEnabled() {
        pendingSilentReseed = true;
        clock.reset();
    }

    /** HUD disabled — clear toasts and array animation state. */
    public void onHudDisabled() {
        notifications.clearAll();
        rows.clear();
        baselines.clear();
        seeded = false;
        pendingSilentReseed = true;
    }

    public void requestSilentReseed() {
        pendingSilentReseed = true;
    }

    public boolean hasActiveNotifications() {
        return notifications.hasActive();
    }

    public void render(Object scaledResolution) {
        if (!(scaledResolution instanceof ScaledResolution)) {
            return;
        }
        final ScaledResolution sr = (ScaledResolution) scaledResolution;
        final HudModule hud = HudModule.instance();
        if (hud == null || !hud.isEnabled()) {
            return;
        }

        applyFontAndSpeed();
        clock.tick();
        float dt = clock.dt();
        long nowNs = System.nanoTime();

        if (ConfigManager.instance().isLoading()) {
            sawLoading = true;
            notifications.setSuppress(true);
            pendingSilentReseed = true;
        } else {
            notifications.setSuppress(false);
            if (sawLoading) {
                sawLoading = false;
                pendingSilentReseed = true;
            }
        }

        Set<Module> drained = ModuleToggleSignals.drain();

        if (pendingSilentReseed || !seeded) {
            silentReseed();
            drained = CollectionsEmpty.modules();
        } else {
            reconcileNotifications(drained);
        }

        if (hud.wantsArray()) {
            reconcileArray(hud.wantsSuffixes(), dt);
        } else {
            rows.clear();
        }

        notifications.advance(nowNs);
        advanceToastAnims(dt, nowNs);

        final boolean drawWatermark = hud.wantsWatermark();
        final boolean drawArray = hud.wantsArray() && !rows.isEmpty();
        final boolean drawTarget = hud.wantsTargetHud();
        final boolean drawToasts = hud.wantsNotifications() && notifications.hasActive();
        if (!drawWatermark && !drawArray && !drawTarget && !drawToasts) {
            return;
        }

        final float scale = sr.getScaleFactor();
        final int sw = sr.getScaledWidth();
        final int sh = sr.getScaledHeight();

        UiKit.GlGuard.run(new Runnable() {
            @Override
            public void run() {
                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

                boolean prevBlur = UiBlur.isEnabled();
                boolean blurOk = false;
                try {
                    UiBlur.setEnabled(true);
                    blurOk = UiBlur.isUsable();
                    if (blurOk) {
                        UiBlur.beginFrame(true);
                    }
                    if (drawWatermark) {
                        drawWatermark(scale);
                    }
                    if (drawArray) {
                        drawArrayList(sw, scale, hud.wantsSuffixes(), blurOk);
                    }
                    if (drawTarget) {
                        drawTargetHud(sw, sh, scale, blurOk);
                    }
                    if (drawToasts) {
                        drawNotifications(sw, sh, scale, nowNs, blurOk);
                    }
                } finally {
                    if (blurOk) {
                        UiBlur.endFrame();
                    }
                    UiBlur.setEnabled(prevBlur);
                }
                // Depth/lighting restored by GlGuard.finally
            }
        });
    }

    private void applyFontAndSpeed() {
        ClickGuiModule gui = ClickGuiModule.instance();
        float speed = 1f;
        if (gui != null) {
            UiFont.setMode(gui.resolveFontMode());
            speed = gui.getAnimationSpeed();
        }
        clock.setSpeed(speed);
    }

    private void silentReseed() {
        seedBaselines(ModuleManager.INSTANCE.all(), baselines);
        ModuleToggleSignals.drain();
        seeded = true;
        pendingSilentReseed = false;
    }

    private void reconcileNotifications(Set<Module> drained) {
        boolean notify = HudModule.instance() != null && HudModule.instance().wantsNotifications();
        applyFinalStateDeltas(drained, baselines, notifications, notify);
    }

    /**
     * Drain-then-sample coalescing: for each dirty identity, read final
     * {@link Module#isEnabled()}, compare to baseline, enqueue only on real deltas.
     * Returns number of notifications enqueued (for tests).
     */
    public static int applyFinalStateDeltas(Set<Module> drained,
            Map<Module, Boolean> baselines,
            NotificationQueue queue,
            boolean enqueueNotifications) {
        if (drained == null || drained.isEmpty() || baselines == null || queue == null) {
            return 0;
        }
        int pushed = 0;
        for (Module module : drained) {
            if (module == null || module instanceof HudModule) {
                continue;
            }
            boolean finalEnabled = module.isEnabled();
            Boolean baseline = baselines.get(module);
            if (baseline == null) {
                // First-seen identity: seed silently, never toast.
                baselines.put(module, Boolean.valueOf(finalEnabled));
                continue;
            }
            if (baseline.booleanValue() == finalEnabled) {
                continue;
            }
            baselines.put(module, Boolean.valueOf(finalEnabled));
            if (enqueueNotifications) {
                queue.pushStateChange(module);
                pushed++;
            }
        }
        return pushed;
    }

    /** Seed baselines from current module states without enqueueing. */
    public static void seedBaselines(Iterable<Module> modules, Map<Module, Boolean> baselines) {
        if (modules == null || baselines == null) {
            return;
        }
        baselines.clear();
        for (Module m : modules) {
            if (m != null) {
                baselines.put(m, Boolean.valueOf(m.isEnabled()));
            }
        }
    }

    private void reconcileArray(boolean showSuffixes, float dt) {
        Map<String, Module> enabledEligible = this.enabledEligible;
        enabledEligible.clear();
        for (Module m : ModuleManager.INSTANCE.all()) {
            if (!isArrayEligible(m) || !m.isEnabled()) {
                continue;
            }
            enabledEligible.put(m.getName(), m);
        }

        for (Map.Entry<String, Module> e : enabledEligible.entrySet()) {
            ArrayRow row = rows.get(e.getKey());
            if (row == null) {
                row = new ArrayRow(e.getValue());
                rows.put(e.getKey(), row);
                row.visibility.snap(0f);
            } else {
                row.module = e.getValue();
            }
            row.exiting = false;
            row.refreshLabel(showSuffixes);
            row.visibility.setTarget(1f);
            row.visibility.setDurationMs(UiKit.DURATION_MED_MS, 1f);
        }

        Iterator<Map.Entry<String, ArrayRow>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ArrayRow> e = it.next();
            if (enabledEligible.containsKey(e.getKey())) {
                continue;
            }
            ArrayRow row = e.getValue();
            row.exiting = true;
            row.visibility.setTarget(0f);
            row.visibility.setDurationMs(UiKit.DURATION_MED_MS, 1f);
            row.visibility.update(dt);
            if (row.visibility.get() <= SETTLE_EPS && row.visibility.settled(SETTLE_EPS)) {
                it.remove();
            }
        }

        for (ArrayRow row : rows.values()) {
            if (!row.exiting) {
                row.visibility.update(dt);
            }
            row.refreshLabel(showSuffixes);
        }

        List<ArrayRow> sorted = sortedRows;
        sorted.clear();
        sorted.addAll(rows.values());
        CollectionsSort.sortRows(sorted);
        float y = ARRAY_MARGIN;
        for (ArrayRow row : sorted) {
            row.targetY = y;
            row.layoutY = UiKit.ExpEase.toward(row.layoutY, row.targetY,
                    UiKit.ExpEase.kForDurationMs(UiKit.DURATION_MED_MS, 1f), dt);
            if (!row.yInitialized) {
                row.layoutY = y;
                row.yInitialized = true;
            }
            y += (ARRAY_MIN_H + ARRAY_GAP) * Math.max(row.visibility.get(), 0.05f);
        }
    }

    private static boolean isArrayEligible(Module m) {
        if (m == null || m instanceof HudModule) {
            return false;
        }
        if (m.getCategory() == Category.SETTINGS) {
            return false;
        }
        if (m.isHidden()) {
            return false;
        }
        return true;
    }

    static String sanitizeSuffixes(String[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int parts = 0;
        for (String part : raw) {
            if (part == null) {
                continue;
            }
            String cleaned = part.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (cleaned.length() > MAX_SUFFIX_LEN) {
                cleaned = cleaned.substring(0, MAX_SUFFIX_LEN);
            }
            if (parts > 0) {
                sb.append(' ');
            }
            sb.append(cleaned);
            parts++;
            if (parts >= MAX_SUFFIX_PARTS) {
                break;
            }
        }
        return sb.toString();
    }

    static String displayLabel(Module module, boolean showSuffixes) {
        if (module == null) {
            return "";
        }
        if (!showSuffixes) {
            return module.getName();
        }
        String suffix = sanitizeSuffixes(module.getSuffix());
        if (suffix.isEmpty()) {
            return module.getName();
        }
        return module.getName() + " " + suffix;
    }

    private void drawWatermark(float scale) {
        float size = WATERMARK_ICON;
        float x = UiKit.PixelAlign.snap(WATERMARK_MARGIN, scale);
        float y = UiKit.PixelAlign.snap(WATERMARK_MARGIN, scale);
        size = UiKit.PixelAlign.snap(size, scale);

        Minecraft.getMinecraft().getTextureManager().bindTexture(WATERMARK_TUX);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + size, 0.0).tex(0.0, 1.0).endVertex();
        wr.pos(x + size, y + size, 0.0).tex(1.0, 1.0).endVertex();
        wr.pos(x + size, y, 0.0).tex(1.0, 0.0).endVertex();
        wr.pos(x, y, 0.0).tex(0.0, 0.0).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawTargetHud(int scaledWidth, int scaledHeight, float scale, boolean blurOk) {
        Entity target = KillAuraModule.getCurrentTarget();
        if (!(target instanceof EntityLivingBase)) {
            targetHudEntityId = Integer.MIN_VALUE;
            targetHudDisplayHp = -1f;
            return;
        }
        EntityLivingBase living = (EntityLivingBase) target;
        float health = Mc.getHealth(living);
        float max = Math.max(1f, living.getMaxHealth());
        int eid = living.getEntityId();
        if (eid != targetHudEntityId || targetHudDisplayHp < 0f) {
            targetHudEntityId = eid;
            targetHudDisplayHp = health;
        } else {
            float k = UiKit.ExpEase.kForDurationMs(UiKit.DURATION_MED_MS, 1f);
            targetHudDisplayHp = UiKit.ExpEase.toward(targetHudDisplayHp, health, k, clock.dt());
        }
        float displayHp = targetHudDisplayHp;
        float pct = UiKit.clamp01(displayHp / max);
        String name = living.getName();
        if (name == null || name.isEmpty()) {
            name = "Target";
        }
        double dist = Mc.distanceToPlayer(living);

        float w = TARGET_W;
        float h = TARGET_H;
        float x = ARRAY_MARGIN;
        float y = scaledHeight * 0.5f - h * 0.5f;
        x = UiKit.PixelAlign.snap(x, scale);
        y = UiKit.PixelAlign.snap(y, scale);
        w = UiKit.PixelAlign.snap(w, scale);
        h = UiKit.PixelAlign.snap(h, scale);

        if (blurOk) {
            UiBlur.drawSoftBehind(x, y, w, h, 6f, 1f);
        }

        int fadeColor = ClientTheme.getFadeColor(0);
        UiKit.drawRoundedPanel(x, y, w, h, 6f, UiKit.SURFACE);
        UiKit.drawAccentGlow(x, y, w, h, 6f, 0.45f);

        // Player Head preview
        float headSize = 28f;
        float headX = x + 7f;
        float headY = y + (h - headSize) * 0.5f;
        drawPlayerHead(living, headX, headY, headSize);

        // Target Info
        float infoX = headX + headSize + 8f;
        UiFont.draw(name, infoX, y + 6f, 9.5f, UiKit.TEXT);
        String meta = String.format("%.1f HP  ·  %.1fm", displayHp, dist);
        UiFont.draw(meta, infoX, y + 18f, 7.5f, UiKit.MUTED);

        // Smooth 2-color gradient animated health bar
        float barX = infoX;
        float barY = y + h - 10f;
        float barW = w - (infoX - x) - 8f;
        float barH = 4f;
        UiKit.drawRoundedPanel(barX, barY, barW, barH, 2f, ClientTheme.darken(UiKit.SURFACE_STRONG, 0.35f));
        float fill = Math.max(2f, barW * pct);
        UiKit.drawHorizontalGradient(barX, barY, fill, barH, ClientTheme.color1(), ClientTheme.color2());
        UiKit.drawGlowRect(barX, barY, fill, barH, 2f, fadeColor, 0.35f);
    }

    private void drawPlayerHead(EntityLivingBase living, float x, float y, float size) {
        float radius = 4f;
        // Rounded cradle so square skin blit sits on matching language
        UiKit.drawRoundedPanel(x - 1f, y - 1f, size + 2f, size + 2f, radius + 1f,
                UiKit.withAlpha(UiKit.PANEL_HEADER, 0.9f));
        if (living instanceof net.minecraft.client.entity.AbstractClientPlayer) {
            net.minecraft.client.entity.AbstractClientPlayer player =
                    (net.minecraft.client.entity.AbstractClientPlayer) living;
            ResourceLocation skin = player.getLocationSkin();
            if (skin != null) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
                GlStateManager.enableTexture2D();
                GlStateManager.color(1f, 1f, 1f, 1f);
                net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
                        (int) x, (int) y, 8f, 8f, 8, 8, (int) size, (int) size, 64f, 64f);
                net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(
                        (int) x, (int) y, 40f, 8f, 8, 8, (int) size, (int) size, 64f, 64f);
                return;
            }
        }
        int c = ClientTheme.getFadeColor(0);
        UiKit.drawRoundedPanel(x, y, size, size, radius, UiKit.withAlpha(c, 0.4f));
    }

    private void drawArrayList(int scaledWidth, float scale, boolean showSuffixes, boolean blurOk) {
        List<ArrayRow> sorted = sortedRows;
        sorted.clear();
        sorted.addAll(rows.values());
        CollectionsSort.sortRows(sorted);
        float screenRight = scaledWidth - ARRAY_MARGIN;

        int visible = 0;
        for (ArrayRow row : sorted) {
            if (UiKit.clamp01(row.visibility.get()) > 0.01f) {
                visible++;
            }
        }
        int vi = 0;

        for (ArrayRow row : sorted) {
            float vis = UiKit.clamp01(row.visibility.get());
            if (vis <= 0.01f) {
                continue;
            }
            String name = row.name;
            String suffix = row.suffix;
            float suffixW = 0f;
            if (showSuffixes && suffix != null && !suffix.isEmpty()) {
                int fixed = row.module.getFixedSuffixWidth();
                suffixW = (fixed >= 0 ? fixed : UiFont.width("[" + suffix + "]", ARRAY_SUFFIX_SIZE))
                        + ARRAY_SUFFIX_GAP;
            }
            float nameW = UiFont.width(name, ARRAY_NAME_SIZE);
            float w = nameW + suffixW + ARRAY_PAD_X + ARRAY_PAD_R + ARRAY_BAR_W;
            float h = ARRAY_MIN_H;
            float slide = (1f - vis) * 12f;
            // Snap X only — keep layout Y continuous to avoid choppy vertical jitter
            float x = UiKit.PixelAlign.snap(screenRight - w + slide, scale);
            float y = row.layoutY;
            w = UiKit.PixelAlign.snap(w, scale);

            if (blurOk) {
                UiBlur.drawFrostedBehind(x, y, w, h, ARRAY_BLUR_RADIUS, vis);
            } else {
                UiKit.drawRoundedPanel(x, y, w, h, ARRAY_BLUR_RADIUS,
                        UiKit.withAlpha(UiKit.SURFACE, vis * 0.85f));
            }

            int fade = ClientTheme.getRowFadeColor(vi, Math.max(1, visible), ARRAY_WAVE_ROW_MS);
            int barColor = ClientTheme.withAlpha(fade, vis);
            float[] bar = Pure.arrayBarRect(x, y, h, ARRAY_BAR_W);
            UiKit.drawGlowRect(bar[0], bar[1], bar[2], bar[3], 0f, barColor, ARRAY_BAR_GLOW * vis);
            UiKit.drawRoundedPanel(bar[0], bar[1], bar[2], bar[3], 1f, barColor);

            int nameColor = barColor;
            int suffixColor = ClientTheme.withAlpha(fade, vis * 0.55f);

            float textX = x + ARRAY_BAR_W + ARRAY_PAD_X;
            float textY = y + (h - UiFont.height(ARRAY_NAME_SIZE)) * 0.5f;
            UiKit.drawGlowText(name, textX, textY, ARRAY_NAME_SIZE, nameColor, ARRAY_TEXT_GLOW * vis);
            if (showSuffixes && suffix != null && !suffix.isEmpty()) {
                String formattedSuffix = "[" + suffix + "]";
                float suffixY = y + (h - UiFont.height(ARRAY_SUFFIX_SIZE)) * 0.5f;
                UiKit.drawGlowText(formattedSuffix, textX + nameW + ARRAY_SUFFIX_GAP, suffixY,
                        ARRAY_SUFFIX_SIZE, suffixColor, ARRAY_TEXT_GLOW * 0.7f * vis);
            }
            vi++;
        }
    }

    private void advanceToastAnims(float dt, long nowNs) {
        float kIn = UiKit.ExpEase.kForDurationMs(UiKit.DURATION_SLOW_MS, 1f);
        float kOut = UiKit.ExpEase.kForDurationMs(400f, 1f);
        float kY = UiKit.ExpEase.kForDurationMs(UiKit.DURATION_MED_MS, 1f);
        List<NotificationQueue.Entry> bottomFirst = notifications.bottomFirst();
        float yFromBottom = 0f;
        for (NotificationQueue.Entry e : bottomFirst) {
            if (e.isExiting()) {
                e.animOut = UiKit.ExpEase.toward(e.animOut, 1f, kOut, dt);
            } else {
                e.animIn = UiKit.ExpEase.toward(e.animIn, 1f, kIn, dt);
            }
            e.targetY = yFromBottom;
            e.layoutY = UiKit.ExpEase.toward(e.layoutY, e.targetY, kY, dt);
            float h = UiKit.TOAST_MAX_HEIGHT;
            yFromBottom += (h + TOAST_GAP) * Math.max(0.15f, e.alpha());
        }
    }

    private void drawNotifications(int scaledWidth, int scaledHeight, float scale, long nowNs,
            boolean blurOk) {
        List<NotificationQueue.Entry> bottomFirst = notifications.bottomFirst();
        float toastW = Math.min(UiKit.TOAST_MAX_WIDTH, scaledWidth - TOAST_MARGIN * 2f);
        for (NotificationQueue.Entry e : bottomFirst) {
            float alpha = UiKit.clamp01(e.alpha());
            if (alpha <= 0.01f) {
                continue;
            }
            float slideX = e.isExiting() ? e.animOut * 18f : (1f - e.animIn) * 0f;
            float slideY = e.isExiting() ? 0f : (1f - e.animIn) * 16f;
            float h = UiKit.TOAST_MAX_HEIGHT;
            float x = scaledWidth - TOAST_MARGIN - toastW + slideX;
            float y = scaledHeight - TOAST_MARGIN - h - e.layoutY - slideY;
            x = UiKit.PixelAlign.snap(x, scale);
            y = UiKit.PixelAlign.snap(y, scale);
            float w = UiKit.PixelAlign.snap(toastW, scale);
            h = UiKit.PixelAlign.snap(h, scale);

            if (blurOk) {
                UiBlur.drawSoftBehind(x, y, w, h, UiKit.RADIUS_TOAST, alpha);
            }
            // Solid surface so blur-off / failed blur never looks hollow
            UiKit.drawRoundedPanel(x, y, w, h, UiKit.RADIUS_TOAST, UiKit.withAlpha(UiKit.SURFACE, alpha));
            int accent = e.enabled ? UiKit.SUCCESS : UiKit.DANGER;
            UiKit.drawGlowRect(x, y, w, h, UiKit.RADIUS_TOAST, accent, 0.35f * alpha);

            float iconX = x + TOAST_PAD;
            float iconY = y + (h - TOAST_ICON) * 0.5f;
            int iconBg = UiKit.withAlpha(accent, alpha * 0.11f);
            UiKit.drawRoundedPanel(iconX, iconY, TOAST_ICON, TOAST_ICON, 7f, iconBg);
            drawToastMark(iconX, iconY, TOAST_ICON, e.enabled, UiKit.withAlpha(accent, alpha));

            float textX = iconX + TOAST_ICON + 8f;
            UiFont.draw(e.moduleName, textX, y + 14f, UiKit.withAlpha(UiKit.TEXT, alpha));
            String copy = e.enabled ? "Module enabled successfully" : "Module disabled successfully";
            UiFont.draw(copy, textX, y + 28f, 8f, UiKit.withAlpha(UiKit.MUTED, alpha));

            float railL = x + 12f;
            float railR = x + w - 12f;
            float railY = y + h - 3f;
            float railW = railR - railL;
            UiKit.drawRoundedPanel(railL, railY, railW, 2f, 3f,
                    UiKit.withAlpha(0x0AFFFFFF, alpha));
            float progress = e.progress(nowNs);
            if (progress > 0.001f) {
                drawProgressRail(railL, railY, railW * progress, 2f, alpha);
            }
        }
    }

    /** Geometric +/- marks (no glyph dependency). */
    private static void drawToastMark(float iconX, float iconY, float iconSize, boolean enabled, int color) {
        float cx = iconX + iconSize * 0.5f;
        float cy = iconY + iconSize * 0.5f;
        float bar = 2f;
        float half = 5f;
        if (enabled) {
            UiKit.drawRoundedPanel(cx - half, cy - bar * 0.5f, half * 2f, bar, 1f, color);
            UiKit.drawRoundedPanel(cx - bar * 0.5f, cy - half, bar, half * 2f, 1f, color);
        } else {
            UiKit.drawRoundedPanel(cx - half, cy - bar * 0.5f, half * 2f, bar, 1f, color);
        }
    }

    private void drawProgressRail(float x, float y, float w, float h, float alpha) {
        if (w <= 0f) {
            return;
        }
        float a = UiKit.clamp01(alpha);
        int c1 = ClientTheme.color1();
        int c2 = ClientTheme.color2();
        float r1 = ((c1 >> 16) & 0xFF) / 255f;
        float g1 = ((c1 >> 8) & 0xFF) / 255f;
        float b1 = (c1 & 0xFF) / 255f;
        float r2 = ((c2 >> 16) & 0xFF) / 255f;
        float g2 = ((c2 >> 8) & 0xFF) / 255f;
        float b2 = (c2 & 0xFF) / 255f;
        GlStateManager.disableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x, y + h, 0).color(r1, g1, b1, a).endVertex();
        wr.pos(x + w, y + h, 0).color(r2, g2, b2, a).endVertex();
        wr.pos(x + w, y, 0).color(r2, g2, b2, a).endVertex();
        wr.pos(x, y, 0).color(r1, g1, b1, a).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
    }

    /** Pure helpers exposed for unit tests. */
    public static final class Pure {
        private Pure() {
        }

        public static String sanitizeSuffixes(String[] raw) {
            return HudRenderer.sanitizeSuffixes(raw);
        }

        public static boolean isArrayEligible(Module m) {
            return HudRenderer.isArrayEligible(m);
        }

        /**
         * ArrayList sort key: width descending, then name ascending (ignore case).
         * Returns negative if {@code a} should sort before {@code b}.
         */
        public static int compareArrayOrder(float widthA, String nameA, float widthB, String nameB) {
            int byWidth = Float.compare(widthB, widthA);
            if (byWidth != 0) {
                return byWidth;
            }
            String na = nameA == null ? "" : nameA;
            String nb = nameB == null ? "" : nameB;
            return na.compareToIgnoreCase(nb);
        }

        /**
         * Vertical | bar behind an ArrayList label: {@code [x, y, w, h]}.
         * Inset 1px top/bottom so it sits inside the soft blur backdrop.
         */
        public static float[] arrayBarRect(float rowLeft, float rowTop, float rowH, float barW) {
            float w = Math.max(0.5f, barW);
            float h = Math.max(1f, rowH - 2f);
            return new float[] { rowLeft, rowTop + 1f, w, h };
        }

        /** Total row width including the left accent bar. */
        public static float arrayRowWidth(float nameW, float suffixW, float padL, float padR,
                float barW) {
            return nameW + Math.max(0f, suffixW) + padL + padR + Math.max(0f, barW);
        }
    }

    private static final class ArrayRow {
        Module module;
        String name;
        String suffix;
        String label;
        float measuredWidth;
        final UiKit.AnimatedFloat visibility = new UiKit.AnimatedFloat(0f);
        float layoutY;
        float targetY;
        boolean exiting;
        boolean yInitialized;

        ArrayRow(Module module) {
            this.module = module;
            refreshLabel(true);
        }

        void refreshLabel(boolean showSuffixes) {
            // Preserve casing to match ClickGUI ModuleRow labels
            name = module.getName() == null ? "" : module.getName();
            String rawSuffix = sanitizeSuffixes(module.getSuffix());
            suffix = rawSuffix;
            label = showSuffixes && !suffix.isEmpty() ? name + " " + suffix : name;
            float nameW = UiFont.width(name, ARRAY_NAME_SIZE);
            float suffixW;
            if (showSuffixes && !suffix.isEmpty()) {
                int fixed = module.getFixedSuffixWidth();
                suffixW = (fixed >= 0 ? fixed : UiFont.width("[" + suffix + "]", ARRAY_SUFFIX_SIZE))
                        + ARRAY_SUFFIX_GAP;
            } else {
                suffixW = 0f;
            }
            measuredWidth = Pure.arrayRowWidth(nameW, suffixW, ARRAY_PAD_X, ARRAY_PAD_R, ARRAY_BAR_W);
        }
    }

    /** Avoid importing java.util.Collections name clash with sort helper. */
    private static final class CollectionsEmpty {
        static Set<Module> modules() {
            return java.util.Collections.emptySet();
        }
    }

    private static final class CollectionsSort {
        static void sortRows(List<ArrayRow> sorted) {
            java.util.Collections.sort(sorted, new Comparator<ArrayRow>() {
                @Override
                public int compare(ArrayRow a, ArrayRow b) {
                    return Pure.compareArrayOrder(a.measuredWidth, a.name, b.measuredWidth, b.name);
                }
            });
        }
    }
}
