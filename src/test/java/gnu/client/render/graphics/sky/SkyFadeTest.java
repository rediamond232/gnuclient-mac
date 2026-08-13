package gnu.client.render.graphics.sky;

import gnu.client.render.graphics.properties.PropertyValues;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SkyFadeTest {

    @Test
    public void fadeInRisesFromZeroToOne() {
        int start = PropertyValues.parseTimeTicks("06:00", 0);
        int endIn = PropertyValues.parseTimeTicks("07:00", 0);
        int startOut = PropertyValues.parseTimeTicks("18:00", 0);
        int endOut = PropertyValues.parseTimeTicks("19:00", 0);
        assertEquals(0f, SkyFade.brightness(start, start, endIn, startOut, endOut), 0.001f);
        assertEquals(1f, SkyFade.brightness(endIn, start, endIn, startOut, endOut), 0.001f);
        float mid = SkyFade.brightness((start + endIn) / 2, start, endIn, startOut, endOut);
        assertTrue(mid > 0.4f && mid < 0.6f);
    }

    @Test
    public void fullBrightnessBetweenFadeInAndOut() {
        int start = PropertyValues.parseTimeTicks("06:00", 0);
        int endIn = PropertyValues.parseTimeTicks("07:00", 0);
        int startOut = PropertyValues.parseTimeTicks("18:00", 0);
        int endOut = PropertyValues.parseTimeTicks("19:00", 0);
        int noon = PropertyValues.parseTimeTicks("12:00", 0);
        assertEquals(1f, SkyFade.brightness(noon, start, endIn, startOut, endOut), 0.001f);
    }

    @Test
    public void wrapMidnight() {
        assertEquals(0, SkyFade.wrap(24000));
        assertEquals(23999, SkyFade.wrap(-1));
    }

    @Test
    public void defaultSouthAxisRemapsToPositiveX() {
        float[] a = CustomSky.remapAxis(0f, 0f, 1f);
        assertEquals(1f, a[0], 0.0001f);
        assertEquals(0f, a[1], 0.0001f);
        assertEquals(0f, a[2], 0.0001f);
    }

    @Test
    public void eastAxisRemapsToNegativeZ() {
        float[] a = CustomSky.remapAxis(1f, 0f, 0f);
        assertEquals(0f, a[0], 0.0001f);
        assertEquals(0f, a[1], 0.0001f);
        assertEquals(-1f, a[2], 0.0001f);
    }
}
