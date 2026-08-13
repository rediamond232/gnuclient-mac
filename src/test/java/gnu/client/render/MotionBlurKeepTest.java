package gnu.client.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MotionBlurKeepTest {

    private static final float EPS = 1e-4f;

    @Test
    public void amountZeroIsNoBlur() {
        float[] uv = new float[2];
        MotionBlur.streakUv(500f, 200f, 1f / 60f, 0f, 70f, 16f / 9f, uv);
        assertEquals(0f, uv[0], 0f);
        assertEquals(0f, uv[1], 0f);
        assertEquals(0f, MotionBlur.shutterSec(0f), 0f);
        assertEquals(0f, MotionBlur.historyWeight(0f, 1f / 60f), 0f);
    }

    @Test
    public void higherAmountHoldsHistoryLonger() {
        float dt = 1f / 60f;
        float low = MotionBlur.historyWeight(1f, dt);
        float mid = MotionBlur.historyWeight(4f, dt);
        float high = MotionBlur.historyWeight(10f, dt);
        assertTrue(low > 0f);
        assertTrue(mid > low);
        assertTrue(high > mid);
        assertTrue(high < 1f);
    }

    /**
     * The trail must decay over the same wall-clock time regardless of frame
     * rate: blending 4 frames at 240 FPS equals 1 frame at 60 FPS.
     */
    @Test
    public void trailDecayIsFrameRateIndependent() {
        float amount = 6f;
        float at60 = MotionBlur.historyWeight(amount, 1f / 60f);
        float w240 = MotionBlur.historyWeight(amount, 1f / 240f);
        float compounded = w240 * w240 * w240 * w240;
        assertEquals(at60, compounded, 1e-3f);
    }

    @Test
    public void historyWeightStaysBelowOneAtMaxFrameRate() {
        float w = MotionBlur.historyWeight(MotionBlur.AMOUNT_MAX, 1f / 1000f);
        assertTrue(w < 1f);
        assertTrue(w > 0.9f);
    }

    @Test
    public void streakNeverExceedsOneFrameInterval() {
        assertEquals(1f / 240f, MotionBlur.streakSec(10f, 1f / 240f), EPS);
        assertEquals(MotionBlur.SHUTTER_MIN_SEC, MotionBlur.streakSec(0.01f, 1f / 30f), EPS);
    }

    @Test
    public void streakGrowsWithTurnRateAndIsCapped() {
        float[] slow = new float[2];
        float[] fast = new float[2];
        float[] absurd = new float[2];
        MotionBlur.streakUv(60f, 0f, 1f / 60f, 6f, 70f, 16f / 9f, slow);
        MotionBlur.streakUv(240f, 0f, 1f / 60f, 6f, 70f, 16f / 9f, fast);
        MotionBlur.streakUv(100000f, 0f, 1f / 60f, 6f, 70f, 16f / 9f, absurd);
        assertTrue(Math.abs(fast[0]) > Math.abs(slow[0]));
        assertEquals(MotionBlur.STREAK_UV_MAX, Math.abs(absurd[0]), EPS);
    }

    @Test
    public void streakOpposesCameraMotion() {
        float[] uv = new float[2];
        MotionBlur.streakUv(120f, 60f, 1f / 60f, 6f, 70f, 16f / 9f, uv);
        assertTrue(uv[0] < 0f);
        assertTrue(uv[1] > 0f);
    }

    @Test
    public void rateSmoothingConvergesAndDampsSpikes() {
        float dt = 1f / 60f;
        float spike = MotionBlur.smoothRate(0f, 600f, dt);
        assertTrue(spike > 0f);
        assertTrue(spike < 600f);
        float rate = 0f;
        for (int i = 0; i < 200; i++) {
            rate = MotionBlur.smoothRate(rate, 600f, dt);
        }
        assertEquals(600f, rate, 1f);
    }

    @Test
    public void tapCountScalesWithStreakLength() {
        int tiny = MotionBlur.tapCount(0.00005f, 0f, 1920, 1080);
        int mid = MotionBlur.tapCount(0.004f, 0f, 1920, 1080);
        int huge = MotionBlur.tapCount(0.5f, 0f, 1920, 1080);
        assertEquals(MotionBlur.MIN_TAPS, tiny);
        assertTrue(mid > MotionBlur.MIN_TAPS);
        assertTrue(mid < MotionBlur.MAX_TAPS);
        assertEquals(MotionBlur.MAX_TAPS, huge);
    }

    @Test
    public void dtIsClampedToSaneRange() {
        assertEquals(MotionBlur.DT_MIN, MotionBlur.clampDt(0f), EPS);
        assertEquals(MotionBlur.DT_MAX, MotionBlur.clampDt(5f), EPS);
        assertEquals(1f / 60f, MotionBlur.clampDt(1f / 60f), EPS);
    }

    @Test
    public void wrapDegreesFoldsAround() {
        assertEquals(0f, MotionBlur.wrapDegrees(360f), EPS);
        assertEquals(-10f, MotionBlur.wrapDegrees(350f), EPS);
        assertEquals(10f, MotionBlur.wrapDegrees(-350f), EPS);
    }

    @Test
    public void horizontalFovExceedsVerticalOnWideAspect() {
        float h = MotionBlur.horizontalFov(70f, 16f / 9f);
        assertTrue(h > 70f);
        assertEquals(70f, MotionBlur.horizontalFov(70f, 1f), EPS);
    }
}
