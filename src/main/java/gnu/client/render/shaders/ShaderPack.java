package gnu.client.render.shaders;

import gnu.client.common.GnuLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loaded shader pack (folder or zip) with files under {@code shaders/}.
 */
public final class ShaderPack {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public final String name;
    private final File root;
    private final boolean zip;
    private final Map<String, String> cache = new HashMap<String, String>();
    private Map<String, String> zipIndex;
    private ZipFile openZip;

    public ShaderPack(String name, File root) {
        this.name = name;
        this.root = root;
        this.zip = root != null && root.isFile();
    }

    public static ShaderPack internal() {
        return new ShaderPack("Internal", null);
    }

    public boolean isInternal() {
        return root == null;
    }

    public String read(String relative) {
        if (relative == null) {
            return null;
        }
        String path = normalize(relative);
        if (cache.containsKey(path)) {
            return cache.get(path);
        }
        String src = readUncached(path);
        cache.put(path, src);
        return src;
    }

    /**
     * Complementary and modern packs keep programs in {@code world0/} (overworld),
     * {@code world-1/} (nether), {@code world1/} (end), then the shaders root.
     */
    public String readProgram(String fileName, int dimension) {
        for (String candidate : programCandidates(fileName, dimension)) {
            String src = read(candidate);
            if (src != null && !src.isEmpty()) {
                return src;
            }
        }
        return null;
    }

    public static String[] programCandidates(String fileName, int dimension) {
        return new String[] {
                "world" + dimension + "/" + fileName,
                fileName,
                "program/" + fileName
        };
    }

    public void beginRead() {
        if (!zip || root == null || openZip != null) {
            return;
        }
        try {
            openZip = new ZipFile(root);
            buildZipIndex(openZip);
        } catch (IOException e) {
            GnuLog.log("Shaders: zip open failed: " + e);
            openZip = null;
        }
    }

    public void endRead() {
        if (openZip != null) {
            try {
                openZip.close();
            } catch (IOException ignored) {
            }
            openZip = null;
        }
    }

    public boolean has(String relative) {
        String s = read(relative);
        return s != null && !s.isEmpty();
    }

    private String readUncached(String path) {
        if (root == null) {
            return internalSource(path);
        }
        if (zip) {
            return readZip(path);
        }
        File f = new File(root, path.replace('/', File.separatorChar));
        if (!f.isFile()) {
            f = new File(root, ("shaders/" + path).replace('/', File.separatorChar));
        }
        if (!f.isFile()) {
            return null;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            return slurp(in);
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String readZip(String path) {
        ZipFile zf = openZip;
        boolean close = false;
        try {
            if (zf == null) {
                zf = new ZipFile(root);
                close = true;
                if (zipIndex == null) {
                    buildZipIndex(zf);
                }
            }
            ZipEntry e = findEntry(zf, path);
            if (e == null) {
                return null;
            }
            InputStream in = zf.getInputStream(e);
            try {
                return slurp(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            GnuLog.log("Shaders: zip read failed " + path + ": " + e);
            return null;
        } finally {
            if (close && zf != null) {
                try {
                    zf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void buildZipIndex(ZipFile zf) {
        zipIndex = new HashMap<String, String>();
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry z = en.nextElement();
            if (z.isDirectory()) {
                continue;
            }
            String n = z.getName().replace('\\', '/');
            zipIndex.put(n, n);
            int shaders = n.indexOf("shaders/");
            if (shaders >= 0) {
                zipIndex.put(n.substring(shaders + "shaders/".length()), n);
            }
        }
    }

    private ZipEntry findEntry(ZipFile zf, String path) {
        if (zipIndex != null) {
            String mapped = zipIndex.get(path);
            if (mapped == null) {
                mapped = zipIndex.get("shaders/" + path);
            }
            if (mapped != null) {
                return zf.getEntry(mapped);
            }
            return null;
        }
        String a = path;
        String b = "shaders/" + path;
        ZipEntry e = zf.getEntry(a);
        if (e != null) {
            return e;
        }
        e = zf.getEntry(b);
        if (e != null) {
            return e;
        }
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry z = en.nextElement();
            String n = z.getName().replace('\\', '/');
            if (n.equals(a) || n.equals(b) || n.endsWith("/" + path) || n.endsWith("/shaders/" + path)) {
                return z;
            }
            if (n.toLowerCase(Locale.ROOT).endsWith("/" + path.toLowerCase(Locale.ROOT))) {
                return z;
            }
        }
        return null;
    }

    private static String slurp(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), UTF8);
    }

    private static String normalize(String relative) {
        String s = relative.replace('\\', '/');
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.startsWith("shaders/")) {
            s = s.substring("shaders/".length());
        }
        return s;
    }

    private static final String INTERNAL_VSH = "#version 120\n"
            + "void main(){\n"
            + "  gl_TexCoord[0] = gl_MultiTexCoord0;\n"
            + "  gl_TexCoord[1] = gl_MultiTexCoord1;\n"
            + "  gl_FrontColor = gl_Color;\n"
            + "  gl_Position = ftransform();\n"
            + "}\n";

    private static final String INTERNAL_BASIC_FSH = "#version 120\n"
            + "/* DRAWBUFFERS:0 */\n"
            + "void main(){\n"
            + "  gl_FragData[0] = gl_Color;\n"
            + "}\n";

    private static final String INTERNAL_TEX_FSH = "#version 120\n"
            + "uniform sampler2D texture;\n"
            + "/* DRAWBUFFERS:0 */\n"
            + "void main(){\n"
            + "  gl_FragData[0] = texture2D(texture, gl_TexCoord[0].st) * gl_Color;\n"
            + "}\n";

    private static final String INTERNAL_LIT_FSH = "#version 120\n"
            + "uniform sampler2D texture;\n"
            + "uniform sampler2D lightmap;\n"
            + "/* DRAWBUFFERS:0 */\n"
            + "void main(){\n"
            + "  gl_FragData[0] = texture2D(texture, gl_TexCoord[0].st) * gl_Color"
            + " * texture2D(lightmap, gl_TexCoord[1].st);\n"
            + "}\n";

    private static String internalSource(String path) {
        if ("gbuffers_basic.vsh".equals(path)
                || "gbuffers_textured.vsh".equals(path)
                || "gbuffers_textured_lit.vsh".equals(path)
                || "gbuffers_skybasic.vsh".equals(path)
                || "gbuffers_skytextured.vsh".equals(path)
                || "final.vsh".equals(path)) {
            return INTERNAL_VSH;
        }
        if ("gbuffers_basic.fsh".equals(path) || "gbuffers_skybasic.fsh".equals(path)) {
            return INTERNAL_BASIC_FSH;
        }
        if ("gbuffers_textured.fsh".equals(path) || "gbuffers_skytextured.fsh".equals(path)) {
            return INTERNAL_TEX_FSH;
        }
        if ("gbuffers_textured_lit.fsh".equals(path)) {
            return INTERNAL_LIT_FSH;
        }
        if ("final.fsh".equals(path)) {
            return "#version 120\nuniform sampler2D colortex0;\nvoid main(){\n"
                    + "  gl_FragColor = texture2D(colortex0, gl_TexCoord[0].st);\n}\n";
        }
        return null;
    }
}
