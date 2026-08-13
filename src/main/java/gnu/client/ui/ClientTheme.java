package gnu.client.ui;

import gnu.client.module.modules.settings.ThemeModule;
import gnu.client.module.setting.ColorSetting;

/**
 * Live client accent colors. Defaults: blue + violet (plan).
 */
public final class ClientTheme {

    public static final int DEFAULT_COLOR1 = 0xFFD042F3;
    public static final int DEFAULT_COLOR2 = 0xFF4296F3;

    private ClientTheme() {}

    public static int color1() {
        ThemeModule theme = ThemeModule.instance();
        if (theme != null)
            return theme.getResolvedColor1();
        return DEFAULT_COLOR1;
    }

    public static int color2() {
        ThemeModule theme = ThemeModule.instance();
        if (theme != null)
            return theme.getResolvedColor2();
        return DEFAULT_COLOR2;
    }

    public static float speed() {
        ThemeModule theme = ThemeModule.instance();
        if (theme != null)
            return theme.getFadeSpeed();
        return 1.0f;
    }

    public static int lerp(float t) {
        return lerpArgb(color1(), color2(), t);
    }

    /**
     * Always oscillates between Color 1 and Color 2 (ignores Fade Mode static options).
     * Prefer {@link #getFadeColor} / {@link #getRowFadeColor} for surfaces that should
     * respect Theme Fade Mode (ArrayList, TargetHUD accents).
     */
    public static int getWaveColor(double offsetMs) {
        double sec = (System.currentTimeMillis() + offsetMs) * 0.0018 * speed();
        float t = (float) (Math.sin(sec) * 0.5 + 0.5);
        return lerpArgb(color1(), color2(), t);
    }

    /**
     * Theme Fade Mode aware color. Wave = time sine; Gradient = position-based C1→C2
     * from {@code offsetMs} (period 1000ms, clamped); static modes ignore offset.
     */
    public static int getFadeColor(double offsetMs) {
        ThemeModule theme = ThemeModule.instance();
        String mode = theme != null ? theme.getFadeMode() : "Wave";
        if ("Color 1".equalsIgnoreCase(mode)) {
            return color1();
        }
        if ("Color 2".equalsIgnoreCase(mode)) {
            return color2();
        }
        if ("Static Blend".equalsIgnoreCase(mode)) {
            return lerpArgb(color1(), color2(), 0.5f);
        }
        if ("Gradient".equalsIgnoreCase(mode)) {
            float t = (float) (offsetMs / 1000.0);
            if (t < 0f) {
                t = 0f;
            } else if (t > 1f) {
                t = 1f;
            }
            return lerpArgb(color1(), color2(), t);
        }

        double sec = (System.currentTimeMillis() + offsetMs) * 0.0018 * speed();
        float t = (float) (Math.sin(sec) * 0.5 + 0.5);
        return lerpArgb(color1(), color2(), t);
    }

    /**
     * Vertical fade down an ordered list (ArrayList rows, ClickGUI panels).
     * Respects Fade Mode via {@link #getFadeColor}. In Gradient mode, maps
     * {@code index / (total - 1)} across Color1→Color2 so the list spans the blend.
     */
    public static int getRowFadeColor(int index, int total, double rowSpacing) {
        if (total <= 1) {
            return getFadeColor(0);
        }
        ThemeModule theme = ThemeModule.instance();
        String mode = theme != null ? theme.getFadeMode() : "Wave";
        if ("Gradient".equalsIgnoreCase(mode)) {
            float t = index / (float) (total - 1);
            return lerpArgb(color1(), color2(), clamp(t));
        }
        double indexOffset = (double) index * rowSpacing;
        return getFadeColor(indexOffset);
    }

    public static int withAlpha(int argb, float alphaMul) {
        return UiKit.withAlpha(argb, alphaMul);
    }

    public static int glow(int argb, float strength) {
        return withAlpha(argb, Math.max(0.05f, Math.min(1f, strength)));
    }

    public static float[] rgbFloats(int argb) {
        return new float[] {
            ((argb >> 16) & 0xFF) / 255f,
            ((argb >> 8) & 0xFF) / 255f,
            (argb & 0xFF) / 255f
        };
    }

    public static int lerpArgb(int a, int b, float t) {
        if (t <= 0f)
            return a;
        if (t >= 1f)
            return b;
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t) & 0xFF;
        int rr = Math.round(ar + (br - ar) * t) & 0xFF;
        int rg = Math.round(ag + (bg - ag) * t) & 0xFF;
        int rb = Math.round(ab + (bb - ab) * t) & 0xFF;
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** Hue color at full sat/bri for picker spectrum. */
    public static int hueRgb(float hue01) {
        return ColorSetting.hsbToRgb(hue01, 1f, 1f);
    }

    /** Darken RGB toward black (keeps alpha). Used so mono themes still show a vertical fade. */
    public static int darken(int argb, float amount) {
        return lerpArgb(argb, (argb & 0xFF000000) | 0x000000, clamp(amount));
    }

    /** Lighten RGB toward white (keeps alpha). */
    public static int lighten(int argb, float amount) {
        return lerpArgb(argb, (argb & 0xFF000000) | 0xFFFFFF, clamp(amount));
    }

    private static float clamp(float v) {
        if (v < 0f)
            return 0f;
        if (v > 1f)
            return 1f;
        return v;
    }
}
