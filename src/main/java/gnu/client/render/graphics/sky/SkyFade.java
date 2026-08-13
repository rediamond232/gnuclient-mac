package gnu.client.render.graphics.sky;

/**
 * OptiFine custom-sky fade envelope (world time ticks 0–23999).
 *
 * @see <a href="https://optifine.readthedocs.io/custom_sky.html">Custom sky time format</a>
 */
public final class SkyFade {

    private SkyFade() {}

    public static float brightness(int timeOfDay, int startFadeIn, int endFadeIn,
            int startFadeOut, int endFadeOut) {
        int t = wrap(timeOfDay);
        if (between(t, startFadeIn, endFadeIn)) {
            return lerp(t, startFadeIn, endFadeIn);
        }
        if (between(t, endFadeIn, startFadeOut)) {
            return 1f;
        }
        if (between(t, startFadeOut, endFadeOut)) {
            return 1f - lerp(t, startFadeOut, endFadeOut);
        }
        return 0f;
    }

    public static boolean between(int t, int a, int b) {
        t = wrap(t);
        a = wrap(a);
        b = wrap(b);
        if (a == b) {
            return t == a;
        }
        if (a < b) {
            return t >= a && t < b;
        }
        return t >= a || t < b;
    }

    public static float lerp(int t, int a, int b) {
        int span = wrap(b - a);
        if (span == 0) {
            return 1f;
        }
        int d = wrap(t - a);
        if (d > span) {
            return 1f;
        }
        return d / (float) span;
    }

    public static int wrap(int v) {
        int x = v % 24000;
        if (x < 0) {
            x += 24000;
        }
        return x;
    }
}
