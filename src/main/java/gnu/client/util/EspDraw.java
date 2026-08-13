package gnu.client.util;

/**
 * World ESP helpers. Callers own {@link RenderHelper#begin()}/{@link RenderHelper#end()}.
 */
public final class EspDraw {

    public static final float DEFAULT_FILL_ALPHA = 0.16f;

    private EspDraw() {}

    /** Returns {@code alpha} if > 0, otherwise {@link #DEFAULT_FILL_ALPHA}. */
    public static float resolveAlpha(float alpha) {
        return alpha > 0f ? alpha : DEFAULT_FILL_ALPHA;
    }

    /**
     * Soft fill using {@link #DEFAULT_FILL_ALPHA}.
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fill(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b) {
        fill(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, DEFAULT_FILL_ALPHA);
    }

    /**
     * Soft fill with explicit alpha (≤ 0 falls back to default).
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fill(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float alpha) {
        RenderHelper.drawFilledBox(
                minX, minY, minZ, maxX, maxY, maxZ,
                r, g, b, resolveAlpha(alpha));
    }

    /**
     * Batched soft fill: draws {@code boxCount} boxes from a flat
     * {@code float[6 * boxCount]} buffer (minX,minY,minZ,maxX,maxY,maxZ each) in a
     * single draw call. {@code alpha} ≤ 0 falls back to default.
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void fillBatched(
            float[] boxes, int boxCount,
            float r, float g, float b, float alpha) {
        RenderHelper.drawFilledBoxes(boxes, boxCount, r, g, b, resolveAlpha(alpha));
    }

    /**
     * Outline-only glow: nested AABB wireframes with decaying alpha (no fill).
     * Must be called between {@link RenderHelper#begin()} and {@link RenderHelper#end()}.
     */
    public static void outlineGlow(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b) {
        // Outer soft blooms
        for (int i = 3; i >= 1; i--) {
            double expand = i * 0.018;
            float a = 0.12f / i;
            float width = 1.2f + i * 0.7f;
            RenderHelper.drawBoundingBox(
                    minX - expand, minY - expand, minZ - expand,
                    maxX + expand, maxY + expand, maxZ + expand,
                    r, g, b, a, width);
        }
        // Crisp core outline
        RenderHelper.drawBoundingBox(minX, minY, minZ, maxX, maxY, maxZ, r, g, b, 0.95f, 1.6f);
    }
}
