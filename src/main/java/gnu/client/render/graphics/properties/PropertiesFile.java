package gnu.client.render.graphics.properties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OptiFine / MCPatcher {@code name=value} properties file.
 *
 * <p>Format contract: <a href="https://optifine.readthedocs.io/syntax.html">OptiFineDoc syntax</a>
 * — case-sensitive keys, {@code #} comments, order of keys does not matter.
 */
public final class PropertiesFile {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final Map<String, String> values;

    private PropertiesFile(Map<String, String> values) {
        this.values = values;
    }

    public static PropertiesFile parse(String text) {
        try {
            return parse(new StringReader(text == null ? "" : text));
        } catch (IOException e) {
            return empty();
        }
    }

    public static PropertiesFile parse(InputStream in) throws IOException {
        if (in == null) {
            return empty();
        }
        return parse(new InputStreamReader(in, UTF8));
    }

    public static PropertiesFile parse(Reader reader) throws IOException {
        Map<String, String> map = new LinkedHashMap<String, String>();
        BufferedReader br = reader instanceof BufferedReader
                ? (BufferedReader) reader
                : new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null) {
            line = stripComment(line).trim();
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }
        return new PropertiesFile(map);
    }

    public static PropertiesFile empty() {
        return new PropertiesFile(new LinkedHashMap<String, String>());
    }

    /**
     * Strip an OptiFine {@code #} comment. {@code #RRGGBB} color values on the right-hand
     * side of {@code =} are preserved (the hash is not a comment there).
     */
    static String stripComment(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        int hash = indexOfComment(line);
        if (hash < 0) {
            return line;
        }
        return line.substring(0, hash);
    }

    private static int indexOfComment(String line) {
        int eq = line.indexOf('=');
        int start = eq >= 0 ? eq + 1 : 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != '#') {
                continue;
            }
            if (i < start) {
                return i;
            }
            // "#RRGGBB" immediately after '=' or whitespace after '=' is a color, not a comment.
            if (looksLikeColorHash(line, i)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static boolean looksLikeColorHash(String line, int hashIndex) {
        int hex = 0;
        for (int i = hashIndex + 1; i < line.length() && hex < 8; i++) {
            char c = line.charAt(i);
            if (isHex(c)) {
                hex++;
            } else {
                break;
            }
        }
        return hex == 6 || hex == 8 || hex == 3;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public String get(String key) {
        return values.get(key);
    }

    public String get(String key, String fallback) {
        String v = values.get(key);
        return v == null ? fallback : v;
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
