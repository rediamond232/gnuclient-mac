package gnu.client.render.shaders;

import gnu.client.common.GnuLog;
import gnu.client.render.graphics.properties.PropertiesFile;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OptiFine shader options from {@code #define}/{@code const} lines and {@code shaders.properties} screens.
 *
 * @see <a href="https://optifine.readthedocs.io/shaders.html">OptiFine shaders user doc</a>
 */
public final class ShaderOptions {

    private static final Pattern DEFINE = Pattern.compile(
            "^(//)?#define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(\\S+))?\\s*(//.*)?$");
    private static final Pattern CONST = Pattern.compile(
            "^const\\s+(?:float|int|bool)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;]+);\\s*(//.*)?$");
    private static final Pattern BRACKETS = Pattern.compile("\\[([^\\]]+)]");
    private static final Pattern TEX_CLEAR = Pattern.compile("colortex(\\d)Clear\\s*=\\s*false");

    private final String packName;
    private final Map<String, Option> options = new LinkedHashMap<String, Option>();
    private final Map<String, String> screens = new LinkedHashMap<String, String>();
    private final boolean[] colortexClear = new boolean[] {
            true, true, true, true, true, true, true, true
    };

    public ShaderOptions(String packName) {
        this.packName = packName == null ? "pack" : packName;
        screens.put("", "<empty>");
    }

    public static ShaderOptions load(ShaderPack pack, String[] programNames) {
        ShaderOptions opts = new ShaderOptions(pack == null ? "OFF" : pack.name);
        if (pack == null) {
            return opts;
        }
        for (int i = 0; i < programNames.length; i++) {
            String name = programNames[i];
            opts.scan(pack.readProgram(name + ".vsh", 0));
            opts.scan(pack.readProgram(name + ".fsh", 0));
        }
        opts.scanProperties(pack.read("shaders.properties"));
        opts.readSaved();
        return opts;
    }

    public void scan(String source) {
        if (source == null) {
            return;
        }
        Matcher clear = TEX_CLEAR.matcher(source);
        while (clear.find()) {
            int idx = Integer.parseInt(clear.group(1));
            if (idx >= 0 && idx < colortexClear.length) {
                colortexClear[idx] = false;
            }
        }
        String[] lines = source.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            Matcher d = DEFINE.matcher(t);
            if (d.matches()) {
                addDefine(d.group(1) != null, d.group(2), d.group(3), d.group(4));
                continue;
            }
            Matcher c = CONST.matcher(t);
            if (c.matches()) {
                addConst(c.group(1), c.group(2).trim(), c.group(3));
            }
        }
    }

    public void scanProperties(String text) {
        if (text == null) {
            return;
        }
        Matcher clear = TEX_CLEAR.matcher(text);
        while (clear.find()) {
            int idx = Integer.parseInt(clear.group(1));
            if (idx >= 0 && idx < colortexClear.length) {
                colortexClear[idx] = false;
            }
        }
        PropertiesFile props = PropertiesFile.parse(text);
        for (Map.Entry<String, String> e : props.asMap().entrySet()) {
            String key = e.getKey();
            if ("screen".equals(key)) {
                screens.put("", e.getValue());
            } else if (key.startsWith("screen.")) {
                screens.put(key.substring("screen.".length()), e.getValue());
            } else if ("sliders".equals(key) && e.getValue() != null) {
                String[] names = e.getValue().trim().split("\\s+");
                for (int i = 0; i < names.length; i++) {
                    Option o = options.get(names[i]);
                    if (o != null) {
                        o.slider = true;
                    }
                }
            }
        }
        ensureScreenOptions();
    }

    private void ensureScreenOptions() {
        for (String spec : screens.values()) {
            if (spec == null) {
                continue;
            }
            String[] parts = spec.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String tok = parts[i];
                if (tok.isEmpty() || tok.startsWith("<") || tok.startsWith("[")) {
                    continue;
                }
                if (options.containsKey(tok)) {
                    continue;
                }
                Option o = new Option(tok, true, false);
                o.value = "false";
                o.defaultValue = "false";
                options.put(tok, o);
            }
        }
    }

    public boolean colortexClear(int i) {
        return i < 0 || i >= colortexClear.length || colortexClear[i];
    }

    public Option get(String name) {
        return options.get(name);
    }

    public Map<String, Option> all() {
        return options;
    }

    public String screen(String key) {
        String s = screens.get(key == null ? "" : key);
        return s == null ? "" : s;
    }

    public boolean hasScreen(String key) {
        return screens.containsKey(key == null ? "" : key);
    }

    public String apply(String source) {
        if (source == null || options.isEmpty()) {
            return source;
        }
        StringBuilder out = new StringBuilder(source.length() + 64);
        String[] lines = source.split("\n", -1);
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String t = line.trim();
            Matcher d = DEFINE.matcher(t);
            if (d.matches()) {
                String name = d.group(2);
                Option o = options.get(name);
                if (o != null) {
                    seen.add(name);
                    line = rewriteDefineLine(line, o);
                }
            } else {
                Matcher c = CONST.matcher(t);
                if (c.matches()) {
                    String name = c.group(1);
                    Option o = options.get(name);
                    if (o != null && o.isConst) {
                        seen.add(name);
                        line = rewriteConstLine(line, o);
                    }
                }
            }
            if (i > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        StringBuilder inject = new StringBuilder();
        for (Option o : options.values()) {
            if (seen.contains(o.name) || o.isConst) {
                continue;
            }
            if (o.booleanSwitch) {
                if (o.enabled()) {
                    inject.append("#define ").append(o.name).append('\n');
                }
            } else if (o.value != null && !o.value.isEmpty()) {
                inject.append("#define ").append(o.name).append(' ').append(o.value).append('\n');
            }
        }
        if (inject.length() == 0) {
            return out.toString();
        }
        return ShaderPreprocessor.injectAfterVersion(out.toString(), inject.toString());
    }

    public void cycle(String name) {
        Option o = options.get(name);
        if (o == null) {
            return;
        }
        o.cycle();
        writeSaved();
    }

    public void reset() {
        for (Option o : options.values()) {
            o.reset();
        }
        writeSaved();
    }

    private void addDefine(boolean commented, String name, String value, String comment) {
        if (!usableName(name) || options.containsKey(name)) {
            return;
        }
        String[] allowed = parseAllowed(comment);
        boolean boolSwitch = value == null || value.isEmpty();
        if (!boolSwitch && allowed == null) {
            return;
        }
        Option o = new Option(name, boolSwitch, false);
        o.allowed = allowed;
        if (boolSwitch) {
            o.value = commented ? "false" : "true";
            o.defaultValue = o.value;
        } else {
            o.value = stripTrailing(value);
            o.defaultValue = o.value;
        }
        options.put(name, o);
    }

    private void addConst(String name, String value, String comment) {
        if (!usableName(name) || options.containsKey(name)) {
            return;
        }
        String[] allowed = parseAllowed(comment);
        if (allowed == null) {
            return;
        }
        Option o = new Option(name, false, true);
        o.allowed = allowed;
        o.value = stripTrailing(value);
        o.defaultValue = o.value;
        options.put(name, o);
    }

    private static boolean usableName(String name) {
        if (name == null || name.length() < 2) {
            return false;
        }
        if (name.startsWith("MC_") || name.startsWith("ENTITY_")) {
            return false;
        }
        return true;
    }

    private static String[] parseAllowed(String comment) {
        if (comment == null) {
            return null;
        }
        Matcher m = BRACKETS.matcher(comment);
        if (!m.find()) {
            return null;
        }
        String[] parts = m.group(1).trim().split("\\s+");
        return parts.length == 0 ? null : parts;
    }

    private static String stripTrailing(String v) {
        if (v == null) {
            return "";
        }
        if (v.endsWith("f") || v.endsWith("F")) {
            return v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static String rewriteDefineLine(String line, Option o) {
        int def = line.indexOf("#define");
        String indent = def > 0 ? line.substring(0, def) : "";
        String comment = "";
        int sl = line.indexOf("//", def >= 0 ? def : 0);
        if (sl >= 0 && line.indexOf("#define") >= 0) {
            int after = line.indexOf("#define") + "#define".length();
            int sl2 = line.indexOf("//", after);
            if (sl2 >= 0) {
                comment = " " + line.substring(sl2);
            }
        }
        if (o.booleanSwitch) {
            if (o.enabled()) {
                return indent + "#define " + o.name + comment;
            }
            return indent + "//#define " + o.name + comment;
        }
        return indent + "#define " + o.name + " " + o.value + comment;
    }

    private static String rewriteConstLine(String line, Option o) {
        int eq = line.indexOf('=');
        int sc = line.indexOf(';');
        if (eq < 0 || sc < 0 || sc < eq) {
            return line;
        }
        return line.substring(0, eq + 1) + " " + o.value + line.substring(sc);
    }

    private File saveFile() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.mcDataDir == null) {
            return null;
        }
        File dir = new File(mc.mcDataDir, "gnuclient-shaderoptions");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String safe = packName.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(dir, safe + ".txt");
    }

    private void readSaved() {
        File f = saveFile();
        if (f == null || !f.isFile()) {
            return;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                Option o = options.get(line.substring(0, eq).trim());
                if (o != null) {
                    o.value = line.substring(eq + 1).trim();
                }
            }
        } catch (IOException e) {
            GnuLog.log("Shaders: option load failed: " + e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void writeSaved() {
        File f = saveFile();
        if (f == null) {
            return;
        }
        FileWriter w = null;
        try {
            w = new FileWriter(f);
            for (Option o : options.values()) {
                w.write(o.name);
                w.write('=');
                w.write(o.value == null ? "" : o.value);
                w.write('\n');
            }
        } catch (IOException e) {
            GnuLog.log("Shaders: option save failed: " + e);
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static final class Option {
        public final String name;
        public final boolean booleanSwitch;
        public final boolean isConst;
        public boolean slider;
        public String[] allowed;
        public String value;
        public String defaultValue;

        Option(String name, boolean booleanSwitch, boolean isConst) {
            this.name = name;
            this.booleanSwitch = booleanSwitch;
            this.isConst = isConst;
        }

        public boolean enabled() {
            return !"false".equalsIgnoreCase(value) && !"off".equalsIgnoreCase(value)
                    && !"0".equals(value);
        }

        public String label() {
            if (booleanSwitch) {
                return name + ": " + (enabled() ? "ON" : "OFF");
            }
            return name + ": " + value;
        }

        void cycle() {
            if (booleanSwitch) {
                value = enabled() ? "false" : "true";
                return;
            }
            if (allowed == null || allowed.length == 0) {
                return;
            }
            int idx = indexOfValue();
            value = allowed[(idx + 1) % allowed.length];
        }

        void reset() {
            value = defaultValue;
        }

        private int indexOfValue() {
            if (allowed == null) {
                return -1;
            }
            for (int i = 0; i < allowed.length; i++) {
                if (allowed[i].equals(value) || stripTrailing(allowed[i]).equals(stripTrailing(value))) {
                    return i;
                }
            }
            return -1;
        }
    }
}
