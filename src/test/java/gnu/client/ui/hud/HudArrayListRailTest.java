package gnu.client.ui.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pure geometry for ArrayList vertical | accent bars behind each label.
 */
public class HudArrayListRailTest {

    private static final float EPS = 0.01f;

    @Test
    public void barInsetOnePxTopAndBottom() {
        float[] bar = HudRenderer.Pure.arrayBarRect(40f, 10f, 13f, 2.75f);
        assertEquals(40f, bar[0], EPS);
        assertEquals(11f, bar[1], EPS);
        assertEquals(2.75f, bar[2], EPS);
        assertEquals(11f, bar[3], EPS);
    }

    @Test
    public void barWidthClampedToHalfPixelMinimum() {
        float[] bar = HudRenderer.Pure.arrayBarRect(0f, 0f, 13f, 0.1f);
        assertEquals(0.5f, bar[2], EPS);
    }

    @Test
    public void barHeightUsesRowMinusTwo() {
        float[] bar = HudRenderer.Pure.arrayBarRect(0f, 5f, 20f, 3f);
        assertEquals(6f, bar[1], EPS);
        assertEquals(18f, bar[3], EPS);
    }

    @Test
    public void rowWidthIncludesAccentBar() {
        float w = HudRenderer.Pure.arrayRowWidth(50f, 12f, 3f, 3f, 2.75f);
        assertEquals(70.75f, w, EPS);
    }

    @Test
    public void rowWidthIgnoresNegativeSuffixAndBar() {
        float w = HudRenderer.Pure.arrayRowWidth(40f, -5f, 2f, 2f, -1f);
        assertEquals(44f, w, EPS);
    }
}
