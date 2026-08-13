package gnu.client.ui.clickgui;

import gnu.client.command.KeyNames;
import gnu.client.module.Module;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.ui.ClientTheme;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;

/**
 * Rise-style module pill: rounded Color1→Color2 vertical fade + soft bloom (no hard borders).
 */
public final class ModuleRow {

    private static final float PAD = 4f;
    private static final float SETTINGS_PAD = 2f;
    private static final float CHEVRON_W = 10f;
    private static final float ROW_H = 16f;
    private static final float RADIUS = 4f;

    private final Module module;
    private final SettingInteraction settings = new SettingInteraction();

    private final UiKit.AnimatedFloat hover = new UiKit.AnimatedFloat(0f);
    private final UiKit.AnimatedFloat enabled = new UiKit.AnimatedFloat(0f);
    private final UiKit.AnimatedFloat expand = new UiKit.AnimatedFloat(0f);

    private boolean expanded;

    public ModuleRow(Module module) {
        this.module = module;
        enabled.snap(module.isEnabled() ? 1f : 0f);
    }

    public Module getModule() {
        return module;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }

    public void resetTransient() {
        expanded = false;
        expand.snap(0f);
        hover.snap(0f);
        settings.reset();
        enabled.snap(module.isEnabled() ? 1f : 0f);
    }

    public void setAnimSpeed(float ignoredSpeed) {
        hover.setDurationMs(220f, 1f);
        enabled.setDurationMs(320f, 1f);
        expand.setDurationMs(UiKit.DURATION_SLOW_MS, 1f);
    }

    public void update(float dt, boolean hovered) {
        hover.setTarget(hovered ? 1f : 0f);
        enabled.setTarget(module.isEnabled() ? 1f : 0f);
        expand.setTarget(expanded ? 1f : 0f);
        hover.update(dt);
        enabled.update(dt);
        expand.update(dt);
        if (settings.isDragging()) {
            expand.setTarget(1f);
            expand.update(dt);
        }
    }

    public float visibleHeight() {
        return ROW_H + expand.get() * (settings.contentHeight(module) + SETTINGS_PAD);
    }

    public float hitHeight() {
        if (!expanded) {
            return ROW_H;
        }
        return ROW_H + settings.contentHeight(module) + SETTINGS_PAD;
    }

    public boolean mouseClicked(float x, float y, float width, int mouseX, int mouseY, int button) {
        float headerH = ROW_H;
        if (contains(mouseX, mouseY, x, y, width, headerH)) {
            float chevronX = x + width - PAD - CHEVRON_W;
            if (button == 0 && contains(mouseX, mouseY, chevronX, y, CHEVRON_W, headerH)) {
                toggleExpanded();
                return true;
            }
            if (button == 0) {
                module.toggle();
                return true;
            }
            if (button == 1) {
                toggleExpanded();
                return true;
            }
            if (button == 2) {
                ClientBootstrap.beginRebind(module.getName());
                expanded = true;
                return true;
            }
            return true;
        }
        if (!expanded) {
            return false;
        }
        float expandAmt = expand.get();
        float settingsY = y + headerH + SETTINGS_PAD * 0.5f;
        float settingsBand = settings.contentHeight(module) + SETTINGS_PAD;
        if (!contains(mouseX, mouseY, x, y + headerH, width, settingsBand)) {
            return false;
        }
        if (expandAmt < 0.5f) {
            return false;
        }
        return settings.mouseClicked(module, x + PAD, settingsY, width - PAD * 2f,
                mouseX, mouseY, button);
    }

    public void mouseDragged(int mouseX, int mouseY) {
        settings.mouseDragged(mouseX, mouseY);
    }

    public void mouseReleased() {
        settings.mouseReleased();
    }

    public boolean isDraggingSetting() {
        return settings.isDragging();
    }

    public void render(float x, float y, float width, float alpha, float scale) {
        float hx = UiKit.PixelAlign.snap(x, scale);
        float hy = UiKit.PixelAlign.snap(y, scale);
        float hw = UiKit.PixelAlign.snap(width, scale);
        float en = enabled.get();
        float hoverA = hover.get();

        // Soft bloom behind enabled / hover
        if (en > 0.02f) {
            UiKit.drawAccentGlow(hx, hy, hw, ROW_H, RADIUS, 0.75f * alpha * en);
        } else if (hoverA > 0.02f) {
            UiKit.drawGlowRect(hx, hy, hw, ROW_H, RADIUS, ClientTheme.lerp(0.45f), 0.35f * alpha * hoverA);
        }

        // Inactive dark pill
        float inactiveA = alpha * (1f - en * 0.9f);
        if (inactiveA > 0.02f) {
            int base = hoverA > 0.01f ? UiKit.ROW_HOVER : UiKit.ROW_IDLE;
            UiKit.drawRoundedPanel(hx, hy, hw, ROW_H, RADIUS, UiKit.withAlpha(base, inactiveA));
        }

        // Enabled: smooth vertical Color1 → Color2 with rounded corners
        if (en > 0.02f) {
            int top = ClientTheme.withAlpha(ClientTheme.lighten(ClientTheme.color1(), 0.15f), alpha * en);
            int bot = ClientTheme.withAlpha(ClientTheme.color2(), alpha * en);
            // Smooth GL gradient (strip-based "rounded" path bands badly at ROW_H).
            UiKit.drawVerticalGradient(hx, hy, hw, ROW_H, top, bot);
        }

        float chevronX = hx + hw - PAD - CHEVRON_W;
        float textLeft = hx + 6f;
        String name = module.getName();
        float maxNameW = Math.max(8f, chevronX - 3f - textLeft);
        if (UiFont.width(name, 7.5f) > maxNameW) {
            while (name.length() > 1 && UiFont.width(name + "…", 7.5f) > maxNameW) {
                name = name.substring(0, name.length() - 1);
            }
            name = name + "…";
        }
        float nameY = UiKit.PixelAlign.snap(hy + (ROW_H - UiFont.height(7.5f)) * 0.5f, scale);
        int nameColor = UiKit.withAlpha(lerpColor(UiKit.MUTED, UiKit.TEXT, en), alpha);
        UiFont.draw(name, UiKit.PixelAlign.snap(textLeft, scale), nameY, 7.5f, nameColor);

        int code = module.getKeyCode();
        boolean listening = ClientBootstrap.rebindModuleName() != null
                && ClientBootstrap.rebindModuleName().equalsIgnoreCase(module.getName());
        if (listening || code > 0) {
            String bind = listening ? "..." : KeyNames.format(code);
            float bw = UiFont.width(bind, 6f);
            UiFont.draw(bind,
                    UiKit.PixelAlign.snap(chevronX - 3f - bw, scale),
                    UiKit.PixelAlign.snap(hy + (ROW_H - UiFont.height(6f)) * 0.5f, scale),
                    6f,
                    UiKit.withAlpha(listening ? UiKit.WARN : 0x88FFFFFF, alpha));
        }

        drawChevron(chevronX + CHEVRON_W * 0.5f, hy + ROW_H * 0.5f, expand.get(),
                UiKit.withAlpha(UiKit.TEXT, alpha * 0.75f), scale);

        float expandAmt = expand.get();
        if (expandAmt > 0.01f) {
            float settingsY = y + ROW_H + SETTINGS_PAD * 0.5f;
            float settingsAlpha = alpha * UiKit.clamp01(expandAmt);
            float sh = settings.contentHeight(module);
            // Same dark panel language as category body (not old Lux wash)
            UiKit.drawRoundedPanel(hx, hy + ROW_H, hw, sh * expandAmt + SETTINGS_PAD, 3f,
                    UiKit.withAlpha(UiKit.PANEL, settingsAlpha));
            // Thin Color1→Color2 separator under the module header
            UiKit.drawHorizontalGradient(hx + 2f, hy + ROW_H, hw - 4f, 1f,
                    ClientTheme.withAlpha(ClientTheme.color1(), settingsAlpha * 0.7f),
                    ClientTheme.withAlpha(ClientTheme.color2(), settingsAlpha * 0.7f));
            settings.render(module, x + PAD, settingsY, width - PAD * 2f, settingsAlpha, scale);
        }
    }

    private static int lerpColor(int from, int to, float t) {
        t = UiKit.clamp01(t);
        int af = (from >>> 24) & 0xFF, rf = (from >>> 16) & 0xFF, gf = (from >>> 8) & 0xFF, bf = from & 0xFF;
        int at = (to >>> 24) & 0xFF, rt = (to >>> 16) & 0xFF, gt = (to >>> 8) & 0xFF, bt = to & 0xFF;
        return (Math.round(af + (at - af) * t) << 24)
                | (Math.round(rf + (rt - rf) * t) << 16)
                | (Math.round(gf + (gt - gf) * t) << 8)
                | Math.round(bf + (bt - bf) * t);
    }

    private static void drawChevron(float cx, float cy, float openAmt, int argb, float scale) {
        float size = 4f;
        float x = UiKit.PixelAlign.snap(cx - 1.5f + openAmt * 1.5f, scale);
        float y = UiKit.PixelAlign.snap(cy - size * 0.5f, scale);
        UiKit.drawRoundedPanel(x, y, 1.2f, size, 0.5f, argb);
        UiKit.drawRoundedPanel(x + 2.5f, y + 1f, 1.2f, size - 2f, 0.5f,
                UiKit.withAlpha(argb, ((argb >>> 24) & 0xFF) / 255f * 0.5f));
    }

    private static boolean contains(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my < y + h;
    }
}
