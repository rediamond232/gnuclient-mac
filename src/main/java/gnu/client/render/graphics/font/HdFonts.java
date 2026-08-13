package gnu.client.render.graphics.font;

import gnu.client.module.modules.settings.GraphicsModule;
import gnu.client.render.graphics.GraphicsPackRoots;
import gnu.client.render.graphics.properties.PropertiesFile;
import gnu.client.render.graphics.properties.PropertyValues;

/**
 * OptiFine HD font width overrides from {@code font/glyph_sizes.bin} companion
 * {@code optifine/font.properties} / {@code mcpatcher/font/unicode.properties}.
 *
 * @see <a href="https://optifine.readthedocs.io/hd_fonts.html">HD fonts</a>
 */
public final class HdFonts {

    private static float[] widths = new float[256];
    private static boolean loaded;

    private HdFonts() {}

    public static void reload() {
        loaded = false;
        for (int i = 0; i < 256; i++) {
            widths[i] = -1f;
        }
        PropertiesFile p = GraphicsPackRoots.loadProperties("font.properties");
        if (p.isEmpty()) {
            p = GraphicsPackRoots.loadProperties("font/ascii.properties");
        }
        if (p.isEmpty()) {
            return;
        }
        loaded = true;
        for (int i = 0; i < 256; i++) {
            String key = "width." + i;
            if (p.has(key)) {
                widths[i] = PropertyValues.parseFloat(p.get(key), -1f);
            }
        }
    }

    public static float overrideWidth(int ch, float vanilla) {
        if (!GraphicsModule.hdFonts() || !loaded) {
            return vanilla;
        }
        int i = ch & 255;
        if (widths[i] >= 0f) {
            return widths[i];
        }
        return vanilla;
    }
}
