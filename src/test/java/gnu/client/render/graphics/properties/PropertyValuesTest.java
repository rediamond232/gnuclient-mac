package gnu.client.render.graphics.properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PropertyValuesTest {

    @Test
    public void parseColorRgbAndArgb() {
        assertEquals(0xFF181318, PropertyValues.parseColor("#181318", 0));
        assertEquals(0xFF282828, PropertyValues.parseColor("282828", 0));
        assertEquals(0x80FFFFFF, PropertyValues.parseColor("#80FFFFFF", 0));
        assertEquals(42, PropertyValues.parseColor("nope", 42));
    }

    @Test
    public void parseTimeTicksMatchesOptiFineClock() {
        assertEquals(0, PropertyValues.parseTimeTicks("06:00", -1));
        assertEquals(6000, PropertyValues.parseTimeTicks("12:00", -1));
        assertEquals(12000, PropertyValues.parseTimeTicks("18:00", -1));
        assertEquals(18000, PropertyValues.parseTimeTicks("00:00", -1));
        assertEquals(0, PropertyValues.parseTimeTicks("6:00", -1));
    }

    @Test
    public void parseBooleanAndNumbers() {
        assertTrue(PropertyValues.parseBoolean("true", false));
        assertTrue(PropertyValues.parseBoolean("on", false));
        assertFalse(PropertyValues.parseBoolean("off", true));
        assertEquals(1.5f, PropertyValues.parseFloat("1.5", 0f), 0.001f);
        assertEquals(8, PropertyValues.parseInt("8", 0));
    }

    @Test
    public void intRangesIncludeNegatives() {
        assertTrue(PropertyValues.matchesIntRangeList("0-64", 32));
        assertTrue(PropertyValues.matchesIntRangeList("(-3)-16", -1));
        assertFalse(PropertyValues.matchesIntRangeList("0-10 20-30", 15));
        assertTrue(PropertyValues.matchesIntRangeList(null, 99));
    }
}
