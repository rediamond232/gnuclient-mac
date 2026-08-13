package gnu.client.ui.menu;

import gnu.client.mixin.impl.accessors.IAccessorGuiOptionSlider;
import gnu.client.module.modules.settings.ClickGuiModule;
import gnu.client.ui.ClientTheme;
import gnu.client.ui.UiBlur;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import gnu.client.ui.clickgui.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MathHelper;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shared Lux chrome for vanilla menus (options, multiplayer, world select, pause, …).
 */
public final class MenuChrome {

    private static final float BTN_RADIUS = 7f;
    private static final float LABEL_SIZE = 8f;
    private static final float TITLE_SIZE = 10f;

    private static final UiKit.UiClock CLOCK = new UiKit.UiClock();
    private static final Map<GuiButton, UiKit.AnimatedFloat> HOVER =
            new IdentityHashMap<GuiButton, UiKit.AnimatedFloat>();

    private static boolean backdropDrawn;
    private static boolean clockTicked;
    private static boolean blurFrame;

    private MenuChrome() {
    }

    public static boolean shouldStyle() {
        return shouldStyle(Minecraft.getMinecraft().currentScreen);
    }

    public static boolean shouldStyle(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof GnuMainMenuScreen
                || screen instanceof AltManagerScreen
                || screen instanceof ClickGuiScreen
                || screen instanceof GuiContainer
                || screen instanceof GuiChat
                || screen instanceof GuiWinGame) {
            return false;
        }
        return true;
    }

    public static void onScreenDrawStart() {
        applyVisualSettings();
        if (!clockTicked) {
            CLOCK.tick();
            clockTicked = true;
        }
        if ((System.currentTimeMillis() & 63L) == 0L && HOVER.size() > 48) {
            HOVER.clear();
        }
    }

    public static void onScreenDrawEnd() {
        if (blurFrame) {
            UiBlur.endFrame();
            blurFrame = false;
        }
        backdropDrawn = false;
        clockTicked = false;
    }

    public static void applyVisualSettings() {
        ClickGuiModule gui = ClickGuiModule.instance();
        float speed = 1f;
        if (gui != null) {
            UiFont.setMode(gui.resolveFontMode());
            speed = gui.getAnimationSpeed();
        }
        CLOCK.setSpeed(speed);
        UiFont.ensureModernReady();
    }

    private static boolean beginWorldBlur() {
        ClickGuiModule gui = ClickGuiModule.instance();
        boolean want = gui != null && gui.isBlurEnabled();
        UiBlur.setEnabled(want);
        if (!want || !UiBlur.isUsable()) {
            return false;
        }
        UiBlur.beginFrame(true);
        if (!UiBlur.isUsable()) {
            return false;
        }
        blurFrame = true;
        return true;
    }

    public static void drawBackdrop(int width, int height, boolean inWorld) {
        if (!shouldStyle()) {
            return;
        }
        applyVisualSettings();
        if (!clockTicked) {
            CLOCK.tick();
            clockTicked = true;
        }
        if (backdropDrawn) {
            return;
        }
        backdropDrawn = true;
        final float alpha = 1f;
        UiKit.prepareFixedPipeline();
        if (inWorld) {
            boolean blurOk = beginWorldBlur();
            if (blurOk) {
                UiBlur.drawFullscreen(1f);
            }
            float dim = blurOk ? 0.42f : 0.88f;
            UiKit.drawRoundedPanel(0f, 0f, width, height, 0f, UiKit.withAlpha(0xFF080A12, dim));
            UiKit.drawSoftBloom(width * 0.5f - 80f, height * 0.2f, 160f, 120f, 70f,
                    ClientTheme.color1(), blurOk ? 0.08f : 0.12f);
            UiKit.drawSoftBloom(width * 0.55f, height * 0.55f, 180f, 140f, 80f,
                    ClientTheme.color2(), blurOk ? 0.06f : 0.10f);
        } else {
            UiKit.drawRoundedPanel(0f, 0f, width, height, 0f, UiKit.withAlpha(0xFF07080E, alpha));
            float cx = width * 0.5f;
            float cy = height * 0.38f;
            UiKit.drawSoftBloom(cx - 90f, cy - 70f, 180f, 140f, 70f, ClientTheme.color1(), 0.22f);
            UiKit.drawSoftBloom(cx - 40f, cy - 20f, 220f, 180f, 90f, ClientTheme.color2(), 0.16f);
            UiKit.drawSoftBloom(width * 0.15f, height * 0.7f, 160f, 120f, 60f,
                    ClientTheme.color1(), 0.10f);
            UiKit.drawSoftBloom(width * 0.7f, height * 0.65f, 180f, 140f, 70f,
                    ClientTheme.color2(), 0.10f);
            UiKit.drawVerticalGradient(0f, 0f, width, height * 0.28f,
                    UiKit.withAlpha(0xFF000000, 0.32f),
                    UiKit.withAlpha(0x00000000, 0f));
            UiKit.drawVerticalGradient(0f, height * 0.72f, width, height * 0.28f,
                    UiKit.withAlpha(0x00000000, 0f),
                    UiKit.withAlpha(0xFF000000, 0.42f));
        }
        UiKit.prepareFixedPipeline();
    }

    public static void drawOverlayStrip(int left, int startY, int endY, int right) {
        if (!shouldStyle() || endY <= startY) {
            return;
        }
        UiKit.drawRoundedPanel(left, startY, right - left, endY - startY, 0f,
                UiKit.withAlpha(0xFF07080E, 0.92f));
    }

    public static void drawListSelection(int left, int top, int right, int bottom) {
        if (!shouldStyle()) {
            return;
        }
        float x = left + 2f;
        float y = top - 1f;
        float w = Math.max(4f, right - left - 4f);
        float h = Math.max(4f, bottom - top + 2f);
        UiKit.drawAccentGlow(x, y, w, h, 6f, 0.35f);
        UiKit.drawRoundedPanel(x, y, w, h, 6f, UiKit.withAlpha(UiKit.ROW_HOVER, 0.95f));
        UiKit.drawRoundedPanel(x + 1f, y + 3f, 2.4f, h - 6f, 1.2f,
                ClientTheme.withAlpha(ClientTheme.color1(), 1f));
        UiKit.prepareFixedPipeline();
    }

    public static void drawButton(GuiButton button, int mouseX, int mouseY) {
        if (!button.visible) {
            return;
        }
        boolean hovered = mouseX >= button.xPosition && mouseY >= button.yPosition
                && mouseX < button.xPosition + button.width
                && mouseY < button.yPosition + button.height;
        UiKit.AnimatedFloat anim = hoverAnim(button);
        anim.setTarget(hovered && button.enabled ? 1f : 0f);
        anim.update(CLOCK.dt());
        float hover = anim.get();
        float x = button.xPosition;
        float y = button.yPosition;
        float w = button.width;
        float h = button.height;
        float radius = Math.min(BTN_RADIUS, h * 0.45f);
        float alpha = button.enabled ? 1f : 0.45f;

        Minecraft mc = Minecraft.getMinecraft();
        float scale = mc != null ? new ScaledResolution(mc).getScaleFactor() : 1f;
        x = UiKit.PixelAlign.snap(x, scale);
        y = UiKit.PixelAlign.snap(y, scale);

        if (hover > 0.02f && button.enabled) {
            UiKit.drawAccentGlow(x, y, w, h, radius, 0.5f * hover);
        }
        float idleA = alpha * (1f - hover * 0.85f);
        if (idleA > 0.02f) {
            int base = hover > 0.01f ? UiKit.ROW_HOVER : UiKit.ROW_IDLE;
            UiKit.drawRoundedPanel(x, y, w, h, radius, UiKit.withAlpha(base, idleA));
        }
        if (hover > 0.02f && button.enabled) {
            UiKit.drawVerticalGradient(x, y, w, h,
                    ClientTheme.withAlpha(ClientTheme.lighten(ClientTheme.color1(), 0.12f), alpha * hover),
                    ClientTheme.withAlpha(ClientTheme.color2(), alpha * hover));
        }
        UiKit.drawRoundedPanel(x + 1f, y + 4f, 2.4f, h - 8f, 1.2f,
                ClientTheme.withAlpha(ClientTheme.getRowFadeColor(button.id, 12, 160.0), alpha));

        String label = button.displayString == null ? "" : button.displayString;
        if (!label.isEmpty()) {
            int color = button.enabled
                    ? lerpColor(UiKit.MUTED, UiKit.TEXT, hover)
                    : UiKit.MUTED_DIM;
            if (button.packedFGColour != 0) {
                color = button.packedFGColour | 0xFF000000;
            }
            float labelW = UiFont.width(label, LABEL_SIZE);
            UiFont.draw(label,
                    UiKit.PixelAlign.snap(x + (w - labelW) * 0.5f, scale),
                    UiKit.PixelAlign.snap(y + (h - UiFont.height(LABEL_SIZE)) * 0.5f, scale),
                    LABEL_SIZE,
                    UiKit.withAlpha(color, alpha));
        }
        UiKit.prepareFixedPipeline();
    }

    /** Draw a vanilla {@link GuiButton} or {@link GuiOptionSlider} with Lux chrome. */
    public static void drawVanillaControl(GuiButton button, int mouseX, int mouseY) {
        if (button instanceof GuiOptionSlider) {
            GuiOptionSlider slider = (GuiOptionSlider) button;
            IAccessorGuiOptionSlider acc = (IAccessorGuiOptionSlider) slider;
            float next = updateOptionSlider(button, acc.getOptions(), acc.getSliderValue(),
                    slider.dragging, mouseX);
            acc.setSliderValue(next);
            drawSlider(button, acc.getSliderValue(), mouseX, mouseY);
            return;
        }
        drawButton(button, mouseX, mouseY);
    }

    public static void drawSlider(GuiButton button, float value, int mouseX, int mouseY) {
        if (!button.visible) {
            return;
        }
        value = UiKit.clamp01(value);
        boolean hovered = mouseX >= button.xPosition && mouseY >= button.yPosition
                && mouseX < button.xPosition + button.width
                && mouseY < button.yPosition + button.height;
        UiKit.AnimatedFloat anim = hoverAnim(button);
        anim.setTarget(hovered ? 1f : 0f);
        anim.update(CLOCK.dt());
        float hover = anim.get();
        float x = button.xPosition;
        float y = button.yPosition;
        float w = button.width;
        float h = button.height;
        float radius = Math.min(BTN_RADIUS, h * 0.45f);

        Minecraft mc = Minecraft.getMinecraft();
        float scale = mc != null ? new ScaledResolution(mc).getScaleFactor() : 1f;
        x = UiKit.PixelAlign.snap(x, scale);
        y = UiKit.PixelAlign.snap(y, scale);

        UiKit.drawRoundedPanel(x, y, w, h, radius, UiKit.withAlpha(UiKit.ROW_IDLE, 1f));
        float fillW = Math.max(8f, (w - 4f) * value);
        if (fillW > 2f) {
            UiKit.drawAccentGlow(x, y, fillW, h, radius, 0.35f + 0.2f * hover);
            UiKit.drawHorizontalGradient(x + 2f, y + 2f, fillW - 2f, h - 4f,
                    ClientTheme.withAlpha(ClientTheme.color1(), 0.85f),
                    ClientTheme.withAlpha(ClientTheme.color2(), 0.85f));
        }
        float knob = Math.min(10f, h - 4f);
        float kx = x + 2f + (w - 4f - knob) * value;
        float ky = y + (h - knob) * 0.5f;
        UiKit.drawRoundedPanel(kx, ky, knob, knob, knob * 0.5f, UiKit.withAlpha(UiKit.TEXT, 0.95f));

        String label = button.displayString == null ? "" : button.displayString;
        if (!label.isEmpty()) {
            float labelW = UiFont.width(label, LABEL_SIZE);
            UiFont.draw(label,
                    UiKit.PixelAlign.snap(x + (w - labelW) * 0.5f, scale),
                    UiKit.PixelAlign.snap(y + (h - UiFont.height(LABEL_SIZE)) * 0.5f, scale),
                    LABEL_SIZE,
                    UiKit.TEXT);
        }
        UiKit.prepareFixedPipeline();
    }

    public static void drawLockButton(int x, int y, int w, int h, boolean locked, boolean hovered) {
        float radius = 6f;
        int fill = hovered ? UiKit.ROW_HOVER : UiKit.ROW_IDLE;
        if (hovered) {
            UiKit.drawAccentGlow(x, y, w, h, radius, 0.4f);
        }
        UiKit.drawRoundedPanel(x, y, w, h, radius, fill);
        float pad = 5f;
        float bx = x + pad;
        float by = y + pad + 2f;
        float bw = w - pad * 2f;
        float bh = h - pad * 2f - 2f;
        int body = locked ? ClientTheme.color1() : UiKit.MUTED;
        UiKit.drawRoundedPanel(bx, by + bh * 0.35f, bw, bh * 0.65f, 2f, body);
        UiKit.drawRoundedPanel(bx + bw * 0.22f, by, bw * 0.56f, bh * 0.5f, 2.5f,
                UiKit.withAlpha(body, locked ? 1f : 0.7f));
        UiKit.prepareFixedPipeline();
    }

    public static void drawTextField(int x, int y, int w, int h, boolean focused) {
        UiKit.drawRoundedPanel(x - 1f, y - 1f, w + 2f, h + 2f, 6f, UiKit.withAlpha(UiKit.ROW_IDLE, 1f));
        if (focused) {
            UiKit.drawHorizontalGradient(x, y + h - 1.5f, w, 1.4f,
                    ClientTheme.withAlpha(ClientTheme.color1(), 0.9f),
                    ClientTheme.withAlpha(ClientTheme.color2(), 0.9f));
        }
        UiKit.prepareFixedPipeline();
    }

    public static void drawCenteredLabel(String text, float cx, float y, int vanillaColor) {
        if (text == null || text.isEmpty()) {
            return;
        }
        applyVisualSettings();
        float size = y < 32f ? TITLE_SIZE : LABEL_SIZE;
        int color = normalizeVanillaColor(vanillaColor);
        float w = UiFont.width(text, size);
        UiFont.draw(text, cx - w * 0.5f, y, size, color);
        UiKit.prepareFixedPipeline();
    }

    public static void drawLabel(String text, float x, float y, int vanillaColor) {
        if (text == null || text.isEmpty()) {
            return;
        }
        applyVisualSettings();
        UiFont.draw(text, x, y, LABEL_SIZE, normalizeVanillaColor(vanillaColor));
        UiKit.prepareFixedPipeline();
    }

    public static float updateOptionSlider(GuiButton button, GameSettings.Options option,
            float sliderValue, boolean dragging, int mouseX) {
        if (!dragging || option == null) {
            return sliderValue;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return sliderValue;
        }
        float next = (mouseX - (button.xPosition + 4)) / (float) (button.width - 8);
        next = MathHelper.clamp_float(next, 0f, 1f);
        float denorm = option.denormalizeValue(next);
        mc.gameSettings.setOptionFloatValue(option, denorm);
        button.displayString = mc.gameSettings.getKeyBinding(option);
        return option.normalizeValue(denorm);
    }

    private static UiKit.AnimatedFloat hoverAnim(GuiButton button) {
        UiKit.AnimatedFloat anim = HOVER.get(button);
        if (anim == null) {
            anim = new UiKit.AnimatedFloat(0f);
            anim.setDurationMs(UiKit.DURATION_FAST_MS, 1f);
            HOVER.put(button, anim);
        }
        return anim;
    }

    private static int normalizeVanillaColor(int color) {
        if ((color & 0xFF000000) == 0) {
            color |= 0xFF000000;
        }
        int rgb = color & 0x00FFFFFF;
        if (rgb == 0xFFFFFF || rgb == 0xE0E0E0) {
            return UiKit.TEXT;
        }
        if (rgb == 0xA0A0A0 || rgb == 0xAAAAAA || rgb == 0x808080) {
            return UiKit.MUTED;
        }
        return color;
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
}
