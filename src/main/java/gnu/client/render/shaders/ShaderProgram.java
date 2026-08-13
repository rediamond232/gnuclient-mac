package gnu.client.render.shaders;

import gnu.client.common.GnuLog;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiled GLSL program (vertex + fragment).
 */
public final class ShaderProgram {

    private static final Pattern DRAWBUFFERS = Pattern.compile("DRAWBUFFERS:([0-9A-Fa-f]+)");

    public final String name;
    public final int id;
    public final int[] drawBuffers;
    private final Map<String, Integer> uniforms = new HashMap<String, Integer>();

    private ShaderProgram(String name, int id, int[] drawBuffers) {
        this.name = name;
        this.id = id;
        this.drawBuffers = drawBuffers;
    }

    public static ShaderProgram compile(String name, String vsh, String fsh) {
        if (fsh == null || fsh.isEmpty()) {
            return null;
        }
        if (vsh == null || vsh.isEmpty()) {
            vsh = "#version 120\nvoid main(){ gl_TexCoord[0]=gl_MultiTexCoord0; gl_Position=ftransform(); }\n";
        }
        int vs = compileShader(name + ".vsh", vsh, GL20.GL_VERTEX_SHADER);
        int fs = compileShader(name + ".fsh", fsh, GL20.GL_FRAGMENT_SHADER);
        if (vs == 0 || fs == 0) {
            if (vs != 0) {
                GL20.glDeleteShader(vs);
            }
            if (fs != 0) {
                GL20.glDeleteShader(fs);
            }
            return null;
        }
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vs);
        GL20.glAttachShader(prog, fs);
        GL20.glLinkProgram(prog);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            GnuLog.log("Shaders: link failed " + name + ": " + GL20.glGetProgramInfoLog(prog, 2048));
            GL20.glDeleteProgram(prog);
            return null;
        }
        return new ShaderProgram(name, prog, parseDrawBuffers(fsh));
    }

    /**
     * Apple GL 2.1 often reports {@code GL_MAX_DRAW_BUFFERS=1}. Keep albedo for
     * {@code DRAWBUFFERS:0…} and the last (usually lit scene) output otherwise.
     */
    static String limitDrawBuffers(String fsh, int maxDraw) {
        if (maxDraw < 1 || fsh == null || fsh.isEmpty()) {
            return fsh;
        }
        Matcher m = DRAWBUFFERS.matcher(fsh);
        String spec = null;
        while (m.find()) {
            spec = m.group(1);
        }
        if (spec == null || spec.length() <= maxDraw) {
            return fsh;
        }
        String keep;
        String out = fsh;
        if (spec.charAt(0) == '0') {
            keep = spec.substring(0, maxDraw);
        } else {
            keep = spec.substring(spec.length() - maxDraw);
            if (maxDraw == 1 && spec.length() > 1) {
                int last = spec.length() - 1;
                out = out.replace("gl_FragData[" + last + "]", "gl_FragData[0]");
            }
        }
        return out.replace("DRAWBUFFERS:" + spec, "DRAWBUFFERS:" + keep);
    }

    /**
     * Apple cannot sample a {@code DEPTH_COMPONENT} texture. Write 24-bit packed
     * window depth into a colortex slot (default 3) so composite can read it as
     * {@code depthtex0}. RGBA8 {@code vec4(z)} only has 8 bits — distant fragments
     * quantize to 1.0 and Chocapic treats them as sky (blown-out fog/godrays).
     */
    static final String PACK_DEPTH_HELPER =
            "vec4 gnu_packDepth(float z) {\n"
                    + "  if (z >= 0.99999) return vec4(1.0);\n"
                    + "  z = clamp(z, 0.0, 0.99998);\n"
                    + "  vec3 enc = fract(vec3(1.0, 255.0, 65025.0) * z);\n"
                    + "  enc -= enc.yzz * vec3(1.0/255.0, 1.0/255.0, 0.0);\n"
                    + "  return vec4(enc, 1.0);\n"
                    + "}\n";

    static String injectWindowDepth(String fsh, int colortex) {
        return injectWindowDepth(fsh, colortex, "gnu_packDepth(gl_FragCoord.z)", true);
    }

    /**
     * Sky/cloud programs must write far depth (1.0), not {@code gl_FragCoord.z}. Vanilla sky
     * is a ~100-block dome, so window-z is ~0.9998 and Chocapic classifies it as land.
     */
    static String injectSkyFarDepth(String fsh, int colortex) {
        return injectWindowDepth(fsh, colortex, "vec4(1.0)", false);
    }

    static boolean isSkyGbuffer(String name) {
        if (name == null) {
            return false;
        }
        return name.contains("sky") || name.contains("cloud");
    }

    private static String injectWindowDepth(String fsh, int colortex, String expr, boolean packHelper) {
        if (fsh == null || fsh.isEmpty() || colortex < 0 || colortex > 15) {
            return fsh;
        }
        Matcher m = DRAWBUFFERS.matcher(fsh);
        String spec = null;
        while (m.find()) {
            spec = m.group(1);
        }
        if (spec == null) {
            spec = "0";
        }
        char slot = colortex < 10 ? (char) ('0' + colortex) : (char) ('A' + (colortex - 10));
        if (spec.indexOf(slot) >= 0) {
            return fsh;
        }
        // Apple GL 2.1 / Metal often only writes gl_FragData[0..1]. Appending packed
        // depth as the third target (DRAWBUFFERS:013) left that colortex at the
        // far-white clear, so Chocapic classified the whole frame as sky.
        String newSpec;
        int packedFragIndex;
        if (spec.length() <= 1) {
            newSpec = spec + slot;
            packedFragIndex = spec.length();
        } else {
            newSpec = spec.charAt(0) + String.valueOf(slot) + spec.substring(1);
            packedFragIndex = 1;
            fsh = shiftFragDataFrom(fsh, 1);
        }
        String write = "  gl_FragData[" + packedFragIndex + "] = " + expr + ";\n";
        String out = fsh.replace("DRAWBUFFERS:" + spec, "DRAWBUFFERS:" + newSpec);
        if (out.equals(fsh) && spec.equals("0")) {
            int last = out.lastIndexOf('}');
            if (last < 0) {
                return out;
            }
            out = out.substring(0, last)
                    + "/* DRAWBUFFERS:" + newSpec + " */\n"
                    + write
                    + out.substring(last);
        } else {
            int last = out.lastIndexOf('}');
            if (last < 0) {
                return out;
            }
            out = out.substring(0, last) + write + out.substring(last);
        }
        if (!packHelper || !out.contains("gnu_packDepth(")) {
            return out;
        }
        return ShaderPreprocessor.injectAfterVersion(out, PACK_DEPTH_HELPER);
    }

    /**
     * Bump {@code gl_FragData[n]} to {@code [n+1]} for {@code n >= fromIndex}, high to
     * low so {@code [1]} does not collide with a just-shifted {@code [2]}. The pattern
     * {@code gl_FragData[1]} does not match {@code gl_FragData[10]}.
     */
    static String shiftFragDataFrom(String src, int fromIndex) {
        if (src == null || src.isEmpty() || fromIndex < 0) {
            return src;
        }
        for (int i = 15; i >= fromIndex; i--) {
            src = src.replaceAll("gl_FragData\\[" + i + "\\]", "gl_FragData[" + (i + 1) + "]");
        }
        return src;
    }

    /**
     * Apple cannot sample the shadow depth attachment. Overwrite {@code gl_FragData[0]}
     * with packed window-z so {@code shadow2D} can read an RGBA texture.
     */
    static String overwriteFrag0PackedDepth(String fsh) {
        if (fsh == null || fsh.isEmpty()) {
            return fsh;
        }
        int last = fsh.lastIndexOf('}');
        if (last < 0) {
            return fsh;
        }
        String out = fsh.substring(0, last)
                + "  gl_FragData[0] = gnu_packDepth(gl_FragCoord.z);\n"
                + fsh.substring(last);
        return ShaderPreprocessor.injectAfterVersion(out, PACK_DEPTH_HELPER);
    }

    /**
     * Bitmask of colortex slots a gbuffer {@code DRAWBUFFERS} spec writes. Missing spec
     * means colortex0 only.
     */
    static int drawBuffersMask(String fsh) {
        if (fsh == null || fsh.isEmpty()) {
            return 0;
        }
        Matcher m = DRAWBUFFERS.matcher(fsh);
        String spec = null;
        while (m.find()) {
            spec = m.group(1);
        }
        if (spec == null) {
            return 1;
        }
        int mask = 0;
        for (int i = 0; i < spec.length(); i++) {
            char c = spec.charAt(i);
            int idx = c <= '9' ? c - '0' : 10 + (Character.toUpperCase(c) - 'A');
            if (idx >= 0 && idx < 16) {
                mask |= 1 << idx;
            }
        }
        return mask;
    }

    static boolean samplesDepth(String src) {
        if (src == null) {
            return false;
        }
        return src.contains("depthtex") || src.contains("gdepthtex");
    }

    static boolean samplesDepthtex1(String src) {
        return src != null && src.contains("depthtex1");
    }

    static boolean samplesDepthtex2(String src) {
        return src != null && src.contains("depthtex2");
    }

    /**
     * Unused colortex for Apple packed window-depth. Never steals colortex0. {@code -1}
     * means the pack does not sample depth, or every spare slot is taken.
     */
    static int pickPackedDepthSlot(int usedMask, boolean samplesDepth) {
        if (!samplesDepth) {
            return -1;
        }
        int[] prefer = { 7, 6, 5, 4, 3, 2, 1 };
        for (int i = 0; i < prefer.length; i++) {
            int slot = prefer[i];
            if ((usedMask & (1 << slot)) == 0) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Prefer a slot unused by gbuffers and composite/deferred. If that set is full
     * (Chocapic writes the spare slot in composite), fall back to gbuffer-only.
     */
    static int pickPackedDepthSlot(int gbufferMask, int compositeMask, boolean samplesDepth) {
        if (!samplesDepth) {
            return -1;
        }
        int combined = pickPackedDepthSlot(gbufferMask | compositeMask, true);
        if (combined >= 0) {
            return combined;
        }
        return pickPackedDepthSlot(gbufferMask, true);
    }

    /**
     * Next free texture unit in {@code used[0..7]}. Prefers 7 down to 1 so albedo
     * stays on unit 0. When every unit is taken, overlays {@code overlayWhenFull}.
     */
    static int takeFreeUnit(boolean[] used, int overlayWhenFull) {
        if (used == null || used.length == 0) {
            return 1;
        }
        for (int u = used.length - 1; u >= 1; u--) {
            if (!used[u]) {
                used[u] = true;
                return u;
            }
        }
        if (overlayWhenFull >= 1 && overlayWhenFull < used.length) {
            used[overlayWhenFull] = true;
            return overlayWhenFull;
        }
        if (!used[0]) {
            used[0] = true;
            return 0;
        }
        int last = used.length - 1;
        used[last] = true;
        return last;
    }

    static boolean usesYCoCg(String src) {
        if (src == null) {
            return false;
        }
        String u = src.toUpperCase(Locale.ROOT);
        return u.contains("YCOCG") || u.contains("RGB2YCOCG") || u.contains("YCOCG2RGB");
    }

    static boolean propertiesWantNearest(String props) {
        if (props == null || props.isEmpty()) {
            return false;
        }
        return props.toLowerCase(Locale.ROOT).contains("nearest");
    }

    static int[] parseDrawBuffers(String fsh) {
        Matcher m = DRAWBUFFERS.matcher(fsh);
        String spec = null;
        while (m.find()) {
            spec = m.group(1);
        }
        if (spec == null) {
            return new int[] { OpenGlHelper.GL_COLOR_ATTACHMENT0 };
        }
        int[] out = new int[spec.length()];
        for (int i = 0; i < spec.length(); i++) {
            char c = spec.charAt(i);
            int idx = c <= '9' ? c - '0' : 10 + (Character.toUpperCase(c) - 'A');
            out[i] = OpenGlHelper.GL_COLOR_ATTACHMENT0 + idx;
        }
        return out;
    }

    public void applyDrawBuffers() {
        applyDrawBuffers(8);
    }

    public void applyDrawBuffers(int maxAttachments) {
        if (drawBuffers == null || drawBuffers.length == 0) {
            return;
        }
        int maxDraw = Math.min(maxAttachments, GbufferTarget.maxDrawBuffers());
        int n = 0;
        int base = OpenGlHelper.GL_COLOR_ATTACHMENT0;
        for (int i = 0; i < drawBuffers.length && n < maxDraw; i++) {
            int idx = drawBuffers[i] - base;
            if (idx >= 0 && idx < maxAttachments) {
                n++;
            }
        }
        if (n == 0) {
            return;
        }
        IntBuffer buf = ByteBuffer.allocateDirect(n * 4)
                .order(ByteOrder.nativeOrder()).asIntBuffer();
        int written = 0;
        for (int i = 0; i < drawBuffers.length && written < n; i++) {
            int idx = drawBuffers[i] - base;
            if (idx >= 0 && idx < maxAttachments) {
                buf.put(drawBuffers[i]);
                written++;
            }
        }
        buf.flip();
        GL20.glDrawBuffers(buf);
        GlErrors.check("applyDrawBuffers " + name);
    }

    private static int compileShader(String file, String src, int type) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            GnuLog.log("Shaders: compile failed " + file + ": " + GL20.glGetShaderInfoLog(id, 2048));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }

    public void use() {
        GL20.glUseProgram(id);
    }

    public int uniform(String name) {
        Integer cached = uniforms.get(name);
        if (cached != null) {
            return cached.intValue();
        }
        int loc = GL20.glGetUniformLocation(id, name);
        uniforms.put(name, Integer.valueOf(loc));
        return loc;
    }

    public void delete() {
        if (id != 0) {
            GL20.glDeleteProgram(id);
        }
        uniforms.clear();
    }
}
