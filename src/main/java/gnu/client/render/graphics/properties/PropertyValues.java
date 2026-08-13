package gnu.client.render.graphics.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parsers for OptiFine property value types (colors, {@code hh:mm} times, int ranges).
 *
 * @see <a href="https://optifine.readthedocs.io/syntax.html">OptiFineDoc syntax</a>
 * @see <a href="https://optifine.readthedocs.io/custom_sky.html">Custom sky time format</a>
 */
public final class PropertyValues {

    private PropertyValues() {}

    /** Parse {@code #RRGGBB}, {@code RRGGBB}, or {@code #AARRGGBB} to 0xAARRGGBB (alpha 0xFF if omitted). */
    public static int parseColor(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return fallback;
        }
        if (s.charAt(0) == '#') {
            s = s.substring(1);
        }
        if (s.length() != 6 && s.length() != 8) {
            return fallback;
        }
        try {
            long v = Long.parseLong(s, 16);
            if (s.length() == 6) {
                return (int) (0xFF000000L | v);
            }
            return (int) v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * World time in ticks [0, 24000) from {@code hh:mm} 24-hour clock.
     * OptiFine: 06:00 = 0, 12:00 = 6000, 18:00 = 12000, 00:00 = 18000.
     */
    public static int parseTimeTicks(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim();
        int colon = s.indexOf(':');
        if (colon <= 0 || colon >= s.length() - 1) {
            return fallback;
        }
        try {
            int hour = Integer.parseInt(s.substring(0, colon).trim());
            int minute = Integer.parseInt(s.substring(colon + 1).trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return fallback;
            }
            int ticks = ((hour - 6) * 1000 + (minute * 1000) / 60) % 24000;
            if (ticks < 0) {
                ticks += 24000;
            }
            return ticks;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(s) || "on".equals(s) || "yes".equals(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "off".equals(s) || "no".equals(s) || "0".equals(s)) {
            return false;
        }
        return fallback;
    }

    public static float parseFloat(String raw, float fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int parseInt(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Space-separated tokens. */
    public static List<String> parseList(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = raw.trim().split("\\s+");
        List<String> out = new ArrayList<String>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Space-separated integers or inclusive ranges ({@code 0-64}, {@code (-3)-16}).
     */
    public static boolean matchesIntRangeList(String raw, int value) {
        if (raw == null || raw.trim().isEmpty()) {
            return true;
        }
        for (IntRange range : parseIntRanges(raw)) {
            if (range.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public static List<IntRange> parseIntRanges(String raw) {
        List<IntRange> out = new ArrayList<IntRange>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String token : parseList(raw)) {
            IntRange r = parseOneRange(token);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    private static IntRange parseOneRange(String token) {
        try {
            int dash = indexOfRangeDash(token);
            if (dash < 0) {
                int v = parseIntToken(token);
                return new IntRange(v, v);
            }
            int lo = parseIntToken(token.substring(0, dash));
            int hi = parseIntToken(token.substring(dash + 1));
            if (lo > hi) {
                int t = lo;
                lo = hi;
                hi = t;
            }
            return new IntRange(lo, hi);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntToken(String raw) {
        String s = raw.trim();
        if (s.startsWith("(") && s.endsWith(")") && s.length() > 2) {
            s = s.substring(1, s.length() - 1);
        }
        return Integer.parseInt(s.trim());
    }

    /** Dash that separates two integers, not the leading minus of a negative number. */
    private static int indexOfRangeDash(String token) {
        for (int i = 1; i < token.length(); i++) {
            if (token.charAt(i) != '-') {
                continue;
            }
            if (token.charAt(i - 1) == '(') {
                continue;
            }
            return i;
        }
        return -1;
    }

    public static final class IntRange {
        public final int lo;
        public final int hi;

        public IntRange(int lo, int hi) {
            this.lo = lo;
            this.hi = hi;
        }

        public boolean contains(int v) {
            return v >= lo && v <= hi;
        }
    }
}
