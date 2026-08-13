package gnu.client.render.graphics.math;

/**
 * OptiFine-style fast sin/cos lookup (4096 entries) used when Fast Math is on.
 */
public final class FastMath {

    private static final int SIZE = 4096;
    private static final float[] SIN = new float[SIZE];
    private static final float TO_INDEX = SIZE / (float) (Math.PI * 2.0);

    static {
        for (int i = 0; i < SIZE; i++) {
            SIN[i] = (float) Math.sin(i * Math.PI * 2.0 / SIZE);
        }
    }

    private FastMath() {}

    public static float sin(float v) {
        int i = (int) (v * TO_INDEX) & (SIZE - 1);
        return SIN[i];
    }

    public static float cos(float v) {
        int i = (int) (v * TO_INDEX + SIZE / 4) & (SIZE - 1);
        return SIN[i];
    }
}
