package gnu.client.render.graphics.properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PropertiesFileTest {

    @Test
    public void parsesNameValueAndSkipsComments() {
        PropertiesFile p = PropertiesFile.parse(
                "# comment\nblend=add\nsource=sky1.png\n\nrotate=true\n");
        assertEquals("add", p.get("blend"));
        assertEquals("sky1.png", p.get("source"));
        assertEquals("true", p.get("rotate"));
        assertFalse(p.has("comment"));
    }

    @Test
    public void preservesColorHashValues() {
        PropertiesFile p = PropertiesFile.parse("fog.end=#181318\nsky.end=282828\n");
        assertEquals("#181318", p.get("fog.end"));
        assertEquals("282828", p.get("sky.end"));
    }

    @Test
    public void emptyAndMissingKeys() {
        PropertiesFile p = PropertiesFile.parse("");
        assertTrue(p.isEmpty());
        assertEquals("fallback", p.get("nope", "fallback"));
    }
}
