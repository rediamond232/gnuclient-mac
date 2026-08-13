package gnu.client.ui.clickgui;

import gnu.client.command.KeyNames;
import gnu.client.config.ConfigManager;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ColorSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.Setting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.ui.ClientTheme;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;

import java.util.List;

/**
 * Compact settings panel matching the classic ClickGUI module tiles.
 */
public final class SettingInteraction {

    private static final float BOOL_H = 16f;
    private static final float SLIDER_H = 22f;
    private static final float BIND_H = 16f;
    private static final float COLOR_H = 78f;
    private static final float COLOR_SV = 46f;
    private static final float COLOR_HUE_H = 8f;
    private static final float CHIP_H = 12f;
    private static final float CHIP_PAD_X = 4f;
    private static final float CHIP_GAP = 2f;
    private static final float CHIP_RADIUS = 3f;
    private static final float MODE_LABEL_H = 10f;
    private static final float MODE_PAD_Y = 2f;
    private static final float FONT = 7f;
    private static final float FONT_SM = 6.5f;
    private static final float PAD = 5f;

    private SliderSetting dragging;
    private float dragTrackX;
    private float dragTrackW;
    private ColorSetting draggingColor;
    private boolean draggingHue;
    private float colorSvX;
    private float colorSvY;
    private float colorSvW;
    private float colorSvH;
    private float colorHueX;
    private float colorHueY;
    private float colorHueW;

    public void reset() {
        dragging = null;
        draggingColor = null;
    }

    public boolean isDragging() {
        return dragging != null || draggingColor != null;
    }

    public float contentHeight(Module module) {
        if (module == null) {
            return 0f;
        }
        module.guiUpdate();
        float h = 0f;
        float estimateW = CategoryColumn.WIDTH - CategoryColumn.BODY_PAD * 2f - 8f;
        for (Setting<?> setting : module.getSettings()) {
            if (setting.isVisible()) {
                h += settingHeight(setting, estimateW);
            }
        }
        return h + BIND_H;
    }

    public void render(Module module, float x, float y, float width, float alpha, float scale) {
        if (module == null) {
            return;
        }
        module.guiUpdate();
        float rowY = y;
        for (Setting<?> setting : module.getSettings()) {
            if (!setting.isVisible()) {
                continue;
            }
            float h = settingHeight(setting, width);
            renderSetting(setting, x, rowY, width, h, alpha, scale);
            rowY += h;
        }
        renderBindRow(module, x, rowY, width, alpha, scale);
    }

    public boolean mouseClicked(Module module, float x, float y, float width,
            int mouseX, int mouseY, int button) {
        if (module == null) {
            return false;
        }
        module.guiUpdate();
        float rowY = y;
        for (Setting<?> setting : module.getSettings()) {
            if (!setting.isVisible()) {
                continue;
            }
            float h = settingHeight(setting, width);
            if (contains(mouseX, mouseY, x, rowY, width, h)) {
                return clickSetting(module, setting, x, rowY, width, h, mouseX, mouseY, button);
            }
            rowY += h;
        }
        if (contains(mouseX, mouseY, x, rowY, width, BIND_H)) {
            return clickBind(module, button);
        }
        return false;
    }

    public void mouseDragged(int mouseX, int mouseY) {
        if (draggingColor != null) {
            if (draggingHue) {
                float pct = (mouseX - colorHueX) / Math.max(1f, colorHueW);
                draggingColor.setFromPicker(UiKit.clamp01(pct), draggingColor.getSaturation(),
                        draggingColor.getBrightness());
            } else {
                float s = (mouseX - colorSvX) / Math.max(1f, colorSvW);
                float b = 1f - (mouseY - colorSvY) / Math.max(1f, colorSvH);
                draggingColor.setFromPicker(draggingColor.getHue(), UiKit.clamp01(s), UiKit.clamp01(b));
            }
            ConfigManager.instance().requestSave();
            return;
        }
        if (dragging == null || dragTrackW <= 0f) {
            return;
        }
        float pct = (mouseX - dragTrackX) / dragTrackW;
        pct = UiKit.clamp01(pct);
        float val = dragging.getMin() + pct * (dragging.getMax() - dragging.getMin());
        dragging.setValue(val);
        ConfigManager.instance().requestSave();
    }

    public void mouseReleased() {
        dragging = null;
        draggingColor = null;
    }

    private boolean clickSetting(Module module, Setting<?> setting, float x, float y, float width,
            float height, int mouseX, int mouseY, int button) {
        if (setting instanceof BoolSetting) {
            BoolSetting bool = (BoolSetting) setting;
            if (button != 0 || bool.isDisabled()) {
                return true;
            }
            bool.setValue(!bool.getValue());
            ConfigManager.instance().requestSave();
            return true;
        }
        if (setting instanceof ModeSetting) {
            ModeSetting mode = (ModeSetting) setting;
            int hit = hitModeChip(mode, x, y, width, mouseX, mouseY);
            if (hit >= 0) {
                mode.setValue(hit);
                ConfigManager.instance().requestSave();
                return true;
            }
            if (button == 0 || button == 1) {
                List<String> modes = mode.getModes();
                if (!modes.isEmpty()) {
                    int cur = mode.getIndex();
                    if (button == 0) {
                        mode.setValue((cur + 1) % modes.size());
                    } else {
                        mode.setValue((cur - 1 + modes.size()) % modes.size());
                    }
                    ConfigManager.instance().requestSave();
                }
            }
            return true;
        }
        if (setting instanceof SliderSetting) {
            if (button != 0) {
                return true;
            }
            SliderSetting slider = (SliderSetting) setting;
            dragging = slider;
            dragTrackX = x + PAD;
            dragTrackW = Math.max(1f, width - PAD * 2f);
            float pct = (mouseX - dragTrackX) / dragTrackW;
            pct = UiKit.clamp01(pct);
            float val = slider.getMin() + pct * (slider.getMax() - slider.getMin());
            slider.setValue(val);
            ConfigManager.instance().requestSave();
            return true;
        }
        if (setting instanceof ColorSetting) {
            if (button != 0) {
                return true;
            }
            ColorSetting color = (ColorSetting) setting;
            float svX = x + PAD;
            float svY = y + 12f;
            float svW = Math.max(8f, width - PAD * 2f);
            float svH = COLOR_SV;
            float hueY = svY + svH + 4f;
            if (contains(mouseX, mouseY, svX, hueY, svW, COLOR_HUE_H)) {
                draggingColor = color;
                draggingHue = true;
                colorHueX = svX;
                colorHueY = hueY;
                colorHueW = svW;
                float pct = (mouseX - svX) / svW;
                color.setFromPicker(UiKit.clamp01(pct), color.getSaturation(), color.getBrightness());
                ConfigManager.instance().requestSave();
                return true;
            }
            if (contains(mouseX, mouseY, svX, svY, svW, svH)) {
                draggingColor = color;
                draggingHue = false;
                colorSvX = svX;
                colorSvY = svY;
                colorSvW = svW;
                colorSvH = svH;
                float s = (mouseX - svX) / svW;
                float b = 1f - (mouseY - svY) / svH;
                color.setFromPicker(color.getHue(), UiKit.clamp01(s), UiKit.clamp01(b));
                ConfigManager.instance().requestSave();
                return true;
            }
            return true;
        }
        return true;
    }

    private boolean clickBind(Module module, int button) {
        if (button == 0) {
            ClientBootstrap.beginRebind(module.getName());
            return true;
        }
        if (button == 1) {
            module.setKeyCode(-1);
            ClientBootstrap.cancelRebind();
            return true;
        }
        return true;
    }

    private void renderSetting(Setting<?> setting, float x, float y, float width, float height,
            float alpha, float scale) {
        if (setting instanceof ModeSetting) {
            renderModeSetting((ModeSetting) setting, x, y, width, alpha, scale);
            return;
        }
        if (setting instanceof BoolSetting) {
            renderBoolSetting((BoolSetting) setting, x, y, width, alpha, scale);
            return;
        }
        if (setting instanceof SliderSetting) {
            renderSliderSetting((SliderSetting) setting, x, y, width, alpha, scale);
            return;
        }
        if (setting instanceof ColorSetting) {
            renderColorSetting((ColorSetting) setting, x, y, width, alpha, scale);
            return;
        }
        float sx = UiKit.PixelAlign.snap(x + PAD, scale);
        float sy = UiKit.PixelAlign.snap(y + (height - UiFont.height(FONT)) * 0.5f, scale);
        UiFont.draw(setting.getName(), sx, sy, FONT, UiKit.withAlpha(UiKit.MUTED, alpha));
    }

    private void renderColorSetting(ColorSetting color, float x, float y, float width,
            float alpha, float scale) {
        float labelX = UiKit.PixelAlign.snap(x + PAD, scale);
        float labelY = UiKit.PixelAlign.snap(y + 1f, scale);
        UiFont.draw(color.getName(), labelX, labelY, FONT, UiKit.withAlpha(UiKit.TEXT, alpha * 0.85f));

        float svX = x + PAD;
        float svY = y + 12f;
        float svW = Math.max(8f, width - PAD * 2f);
        float svH = COLOR_SV;
        int hueColor = ClientTheme.hueRgb(color.getHue());

        // Soft rounded cradle, then continuous SV / hue fills (no block cells)
        UiKit.drawRoundedPanel(svX, svY, svW, svH, 3f, UiKit.withAlpha(UiKit.PANEL_HEADER, alpha));
        UiKit.drawSvColorField(svX, svY, svW, svH, hueColor, alpha);

        float kx = svX + color.getSaturation() * svW;
        float ky = svY + (1f - color.getBrightness()) * svH;
        UiKit.drawRoundedPanel(kx - 2.5f, ky - 2.5f, 5f, 5f, 2.5f, UiKit.withAlpha(0xFF000000, alpha * 0.55f));
        UiKit.drawRoundedPanel(kx - 2f, ky - 2f, 4f, 4f, 2f, UiKit.withAlpha(UiKit.TEXT, alpha));
        UiKit.drawRoundedPanel(kx - 1f, ky - 1f, 2f, 2f, 1f,
                UiKit.withAlpha(color.getValue() | 0xFF000000, alpha));

        float hueY = svY + svH + 4f;
        UiKit.drawRoundedPanel(svX, hueY, svW, COLOR_HUE_H, 2f, UiKit.withAlpha(UiKit.PANEL_HEADER, alpha));
        UiKit.drawHueSpectrum(svX, hueY, svW, COLOR_HUE_H, alpha);
        float hx = svX + color.getHue() * svW;
        UiKit.drawRoundedPanel(hx - 1.5f, hueY - 1.5f, 3f, COLOR_HUE_H + 3f, 1.5f,
                UiKit.withAlpha(0xFF000000, alpha * 0.45f));
        UiKit.drawRoundedPanel(hx - 1.2f, hueY - 1f, 2.4f, COLOR_HUE_H + 2f, 1f,
                UiKit.withAlpha(UiKit.TEXT, alpha));
    }

    private void renderModeSetting(ModeSetting mode, float x, float y, float width,
            float alpha, float scale) {
        float labelX = UiKit.PixelAlign.snap(x + PAD, scale);
        float labelY = UiKit.PixelAlign.snap(y + MODE_PAD_Y, scale);
        UiFont.draw(mode.getName(), labelX, labelY, FONT, UiKit.withAlpha(UiKit.MUTED, alpha));

        ChipLayout layout = layoutChips(mode, x, y, width);
        List<String> modes = mode.getModes();
        int selected = mode.getIndex();
        for (int i = 0; i < layout.count; i++) {
            float cx = layout.xs[i];
            float cy = layout.ys[i];
            float cw = layout.ws[i];
            boolean on = i == selected;
            if (on) {
                UiKit.drawGlowRect(cx, cy, cw, CHIP_H, CHIP_RADIUS, ClientTheme.color1(), 0.35f * alpha);
                int top = ClientTheme.withAlpha(ClientTheme.lighten(ClientTheme.color1(), 0.12f), alpha);
                int bot = ClientTheme.withAlpha(ClientTheme.color2(), alpha);
                UiKit.drawVerticalGradient(cx, cy, cw, CHIP_H, top, bot);
            } else {
                UiKit.drawRoundedPanel(cx, cy, cw, CHIP_H, CHIP_RADIUS,
                        UiKit.withAlpha(UiKit.ROW_IDLE, alpha));
            }
            String label = modes.get(i);
            float tw = UiFont.width(label, FONT_SM);
            UiFont.draw(label,
                    UiKit.PixelAlign.snap(cx + (cw - tw) * 0.5f, scale),
                    UiKit.PixelAlign.snap(cy + (CHIP_H - UiFont.height(FONT_SM)) * 0.5f, scale),
                    FONT_SM, UiKit.withAlpha(on ? UiKit.TEXT : UiKit.MUTED_DIM, alpha));
        }
    }

    private void renderBoolSetting(BoolSetting bool, float x, float y, float width,
            float alpha, float scale) {
        boolean locked = bool.isDisabled();
        int labelColor = locked ? UiKit.MUTED_DIM : UiKit.MUTED;
        float sx = UiKit.PixelAlign.snap(x + PAD, scale);
        float sy = UiKit.PixelAlign.snap(y + (BOOL_H - UiFont.height(FONT)) * 0.5f, scale);
        UiFont.draw(bool.getName(), sx, sy, FONT, UiKit.withAlpha(labelColor, alpha));

        float tw = 18f;
        float th = 10f;
        float tx = x + width - PAD - tw;
        float ty = y + (BOOL_H - th) * 0.5f;
        boolean on = bool.isToggled();
        if (on && !locked) {
            UiKit.drawGlowRect(tx, ty, tw, th, th * 0.5f, ClientTheme.color1(), 0.35f * alpha);
            UiKit.drawVerticalGradient(tx, ty, tw, th,
                    ClientTheme.withAlpha(ClientTheme.lighten(ClientTheme.color1(), 0.1f), alpha),
                    ClientTheme.withAlpha(ClientTheme.color2(), alpha));
        } else {
            UiKit.drawRoundedPanel(tx, ty, tw, th, th * 0.5f,
                    UiKit.withAlpha(locked ? UiKit.TRACK : UiKit.TRACK_DIM, alpha));
        }
        float kw = 7f;
        float kx = on ? tx + tw - kw - 1.5f : tx + 1.5f;
        float ky = ty + (th - kw) * 0.5f;
        int knob = locked ? UiKit.MUTED_DIM : (on ? UiKit.TEXT : 0xFFA8ADBA);
        UiKit.drawRoundedPanel(kx, ky, kw, kw, kw * 0.5f, UiKit.withAlpha(knob, alpha));
    }

    private void renderSliderSetting(SliderSetting slider, float x, float y, float width,
            float alpha, float scale) {
        float sx = UiKit.PixelAlign.snap(x + PAD, scale);
        float sy = UiKit.PixelAlign.snap(y + 2f, scale);
        UiFont.draw(slider.getName(), sx, sy, FONT, UiKit.withAlpha(UiKit.MUTED, alpha));

        String value = String.format("%.2f", slider.getValue());
        float vw = UiFont.width(value, FONT_SM);
        UiFont.draw(value, UiKit.PixelAlign.snap(x + width - PAD - vw, scale), sy, FONT_SM,
                UiKit.withAlpha(ClientTheme.lerp(0.5f), alpha));

        float trackX = x + PAD;
        float trackW = width - PAD * 2f;
        float trackY = y + SLIDER_H - 7f;
        float range = slider.getMax() - slider.getMin();
        float pct = range <= 0f ? 0f : (slider.getValue() - slider.getMin()) / range;
        pct = UiKit.clamp01(pct);
        UiKit.drawRoundedPanel(trackX, trackY, trackW, 2.5f, 2f,
                UiKit.withAlpha(UiKit.TRACK, alpha));
        float fillW = Math.max(2.5f, trackW * pct);
        UiKit.drawHorizontalGradient(trackX, trackY, fillW, 2.5f,
                ClientTheme.withAlpha(ClientTheme.color1(), alpha),
                ClientTheme.withAlpha(ClientTheme.color2(), alpha));
        float knob = 7f;
        float kx = trackX + trackW * pct - knob * 0.5f;
        float ky = trackY + 1.25f - knob * 0.5f;
        UiKit.drawGlowRect(kx, ky, knob, knob, knob * 0.5f, ClientTheme.color2(), 0.3f * alpha);
        UiKit.drawRoundedPanel(kx, ky, knob, knob, knob * 0.5f,
                UiKit.withAlpha(UiKit.TEXT, alpha));
    }

    private void renderBindRow(Module module, float x, float y, float width,
            float alpha, float scale) {
        float sx = UiKit.PixelAlign.snap(x + PAD, scale);
        float sy = UiKit.PixelAlign.snap(y + (BIND_H - UiFont.height(FONT)) * 0.5f, scale);
        boolean listening = ClientBootstrap.rebindModuleName() != null
                && ClientBootstrap.rebindModuleName().equalsIgnoreCase(module.getName());
        int color = UiKit.withAlpha(listening ? UiKit.WARN : ClientTheme.lerp(0.4f), alpha);
        UiFont.draw(bindLabel(module), sx, sy, FONT, color);
    }

    private static String bindLabel(Module module) {
        String listening = ClientBootstrap.rebindModuleName();
        if (listening != null && listening.equalsIgnoreCase(module.getName())) {
            return "Bind: ...";
        }
        int code = module.getKeyCode();
        if (code <= 0) {
            return "Bind: NONE";
        }
        return "Bind: " + KeyNames.format(code);
    }

    private static float settingHeight(Setting<?> setting, float width) {
        if (setting instanceof ModeSetting) {
            return modeHeight((ModeSetting) setting, width);
        }
        if (setting instanceof SliderSetting) {
            return SLIDER_H;
        }
        if (setting instanceof ColorSetting) {
            return COLOR_H;
        }
        return BOOL_H;
    }

    private static float modeHeight(ModeSetting mode, float width) {
        ChipLayout layout = layoutChips(mode, 0f, 0f, width);
        if (layout.count <= 0) {
            return BOOL_H;
        }
        float bottom = 0f;
        for (int i = 0; i < layout.count; i++) {
            bottom = Math.max(bottom, layout.ys[i] + CHIP_H);
        }
        return Math.max(BOOL_H, bottom + MODE_PAD_Y);
    }

    private static int hitModeChip(ModeSetting mode, float x, float y, float width,
            int mouseX, int mouseY) {
        ChipLayout layout = layoutChips(mode, x, y, width);
        for (int i = 0; i < layout.count; i++) {
            if (contains(mouseX, mouseY, layout.xs[i], layout.ys[i], layout.ws[i], CHIP_H)) {
                return i;
            }
        }
        return -1;
    }

    private static ChipLayout layoutChips(ModeSetting mode, float x, float y, float width) {
        List<String> modes = mode.getModes();
        int n = modes == null ? 0 : modes.size();
        ChipLayout out = new ChipLayout(n);
        if (n == 0) {
            return out;
        }

        float[] chipW = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            chipW[i] = UiFont.width(modes.get(i), FONT_SM) + CHIP_PAD_X * 2f;
            total += chipW[i];
            if (i > 0) {
                total += CHIP_GAP;
            }
        }

        float labelW = UiFont.width(mode.getName(), FONT) + 8f;
        float innerL = x + PAD;
        float innerR = x + width - PAD;
        float availSameRow = innerR - innerL - labelW - 4f;
        float chipY0 = y + MODE_PAD_Y + (MODE_LABEL_H - CHIP_H) * 0.5f;

        if (total <= availSameRow && availSameRow > 16f) {
            float cx = innerR - total;
            for (int i = 0; i < n; i++) {
                out.xs[i] = cx;
                out.ys[i] = chipY0;
                out.ws[i] = chipW[i];
                cx += chipW[i] + CHIP_GAP;
            }
            out.count = n;
            return out;
        }

        float rowY = y + MODE_PAD_Y + MODE_LABEL_H + 2f;
        float cx = innerL;
        float rowRight = innerR;
        for (int i = 0; i < n; i++) {
            if (cx > innerL && cx + chipW[i] > rowRight) {
                cx = innerL;
                rowY += CHIP_H + CHIP_GAP;
            }
            out.xs[i] = cx;
            out.ys[i] = rowY;
            out.ws[i] = chipW[i];
            cx += chipW[i] + CHIP_GAP;
        }
        out.count = n;
        return out;
    }

    private static boolean contains(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my < y + h;
    }

    private static final class ChipLayout {
        final float[] xs;
        final float[] ys;
        final float[] ws;
        int count;

        ChipLayout(int n) {
            xs = new float[Math.max(0, n)];
            ys = new float[Math.max(0, n)];
            ws = new float[Math.max(0, n)];
            count = 0;
        }
    }
}
