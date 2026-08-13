package gnu.client.render.shaders;

import gnu.client.common.GnuLog;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OptiFine shader preprocessor: {@code #include}, option {@code #define}s, standard macros.
 *
 * @see <a href="https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.txt">shaders.txt</a>
 */
public final class ShaderPreprocessor {

    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
    private static final Pattern FLOAT_SUFFIX = Pattern.compile("(\\d+\\.\\d+|\\d+\\.|\\.\\d+)[fF]\\b");
    private static final Pattern INT_MODULO = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_.]*)\\s*%\\s*([A-Za-z0-9_.]+)");
    private static final Pattern SWITCH_CASE = Pattern.compile(
            "case\\s+([^:]+)\\s*:\\s*(.*?)\\s*break\\s*;", Pattern.DOTALL);
    private static final Pattern INTERP_QUAL = Pattern.compile(
            "^(\\s*)(?:flat|noperspective|smooth)\\s+");
    /**
     * Chocapic/LIGHT project the sun with {@code vec4 * mat4}. Spec-correct GLSL
     * (Apple) treats that as a row-vector multiply and puts {@code lightPos} off
     * screen, so godrays never streak toward the sun. NVIDIA often compiled it
     * as {@code mat4 * vec4}. Rewrite to the column-vector form OptiFine packs
     * actually meant.
     */
    private static final Pattern ROW_VEC_PROJECTION = Pattern.compile(
            "vec4\\s*\\(\\s*(sunPosition|moonPosition|shadowLightPosition)\\s*,\\s*1(?:\\.0)?\\s*\\)\\s*\\*\\s*(gbufferProjection)\\b");
    private static final int MAX_DEPTH = 48;

    private ShaderPreprocessor() {}

    public static String process(String source, String fileName, ShaderPack pack, Map<String, String> defines) {
        return process(source, fileName, pack, defines, null);
    }

    public static String process(String source, String fileName, ShaderPack pack, Map<String, String> defines,
            ShaderOptions options) {
        return process(source, fileName, pack, defines, options, true);
    }

    public static String process(String source, String fileName, ShaderPack pack, Map<String, String> defines,
            ShaderOptions options, boolean unpackDepth) {
        StringBuilder macros = new StringBuilder();
        macros.append("#define MC_VERSION 10800\n");
        macros.append("#define MC_GL_VERSION 210\n");
        if (defines == null || !defines.containsKey("MC_GLSL_VERSION")) {
            macros.append("#define MC_GLSL_VERSION 120\n");
        }
        macros.append("#define MC_OS_MAC ").append(isMac() ? "1" : "0").append('\n');
        if (defines != null) {
            for (Map.Entry<String, String> e : defines.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) {
                    macros.append("#define ").append(e.getKey()).append('\n');
                } else {
                    macros.append("#define ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
                }
            }
        }
        String body = expandIncludes(source, fileName, pack, 0);
        if (options != null) {
            body = options.apply(body);
        }
        body = rewriteForGlsl120(body, fileName, supportedGlsl(defines));
        body = rewriteRowVectorProjection(body);
        if (unpackDepth) {
            body = rewritePackedDepthSamples(body, fileName);
        }
        return injectAfterVersion(body, macros.toString());
    }

    private static int supportedGlsl(Map<String, String> defines) {
        if (defines == null) {
            return 120;
        }
        String raw = defines.get("MC_GLSL_VERSION");
        if (raw == null || raw.isEmpty()) {
            return 120;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 120;
        }
    }

    /**
     * Chocapic V6 uses {@code #version 400 compatibility} (OptiFine Windows trick).
     * Apple GL 2.1 only has GLSL 120, so clamp the version and rewrite 130+ syntax.
     * Function parameters ({@code in float x}) are left alone.
     */
    static String rewriteForGlsl120(String src, String fileName) {
        return rewriteForGlsl120(src, fileName, 120);
    }

    static String rewriteForGlsl120(String src, String fileName, int supportedGlsl) {
        if (src == null || src.isEmpty()) {
            return src;
        }
        int fileVer = parseVersionDirective(src);
        boolean clampVersion = fileVer > supportedGlsl;
        boolean target120 = supportedGlsl <= 120 || fileVer <= 120 || fileVer == 0 || clampVersion;
        if (!target120) {
            return src;
        }
        boolean fragment = fileName != null && fileName.endsWith(".fsh");
        StringBuilder out = new StringBuilder(src.length() + 16);
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String t = line.trim();
            if (t.startsWith("//")) {
                if (i > 0) {
                    out.append('\n');
                }
                out.append(line);
                continue;
            }
            if (clampVersion && t.startsWith("#version")) {
                line = line.replaceFirst("#version\\s+\\S+(?:\\s+\\S+)?", "#version 120");
                t = line.trim();
            }
            Matcher interp = INTERP_QUAL.matcher(line);
            if (interp.find()) {
                line = interp.replaceFirst(Matcher.quoteReplacement(interp.group(1)));
                t = line.trim();
            }
            if (isIoDecl(t, "in ")) {
                line = line.replaceFirst("\\bin\\b", fragment ? "varying" : "attribute");
            } else if (!fragment && isIoDecl(t, "out ")) {
                line = line.replaceFirst("\\bout\\b", "varying");
            }
            int cmt = line.indexOf("//");
            String code = cmt >= 0 ? line.substring(0, cmt) : line;
            String rest = cmt >= 0 ? line.substring(cmt) : "";
            if (!t.startsWith("#")) {
                code = FLOAT_SUFFIX.matcher(code).replaceAll("$1");
                code = INT_MODULO.matcher(code).replaceAll("int(mod(float($1), float($2)))");
            }
            line = code + rest;
            if (i > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        String rewritten = out.toString();
        rewritten = rewriteSwitches(rewritten);
        if (rewritten.contains("gl_FragData") && rewritten.contains("gl_FragColor")) {
            rewritten = rewritten.replace("gl_FragColor", "gl_FragData[0]");
        }
        rewritten = hoistAndStripExtensions(rewritten);
        rewritten = polyfillGlsl120Builtins(rewritten);
        return rewritten;
    }

    private static final String GLSL120_LOD_HELPER =
            "vec4 gnu_texture2DLod(sampler2D s, vec2 p, float lod) { return texture2D(s, p); }\n";
    private static final String GLSL120_TEXELFETCH_HELPER =
            "vec4 gnu_texelFetch(sampler2D s, ivec2 p, int lod) {\n"
                    + "  return texture2D(s, (vec2(p) + 0.5) / vec2(viewWidth, viewHeight));\n"
                    + "}\n";
    private static final String GLSL120_GATHER2_HELPER =
            "vec4 gnu_textureGather2(sampler2D s, vec2 p) {\n"
                    + "  vec2 t = vec2(1.0) / vec2(viewWidth, viewHeight);\n"
                    + "  vec2 q = p - t * 0.5;\n"
                    + "  return vec4(texture2D(s, q).x, texture2D(s, q + vec2(t.x, 0.0)).x,\n"
                    + "    texture2D(s, q + vec2(t.x, t.y)).x, texture2D(s, q + vec2(0.0, t.y)).x);\n"
                    + "}\n";
    private static final String GLSL120_GATHER3_HELPER =
            "vec4 gnu_textureGather3(sampler2D s, vec2 p, int comp) {\n"
                    + "  vec2 t = vec2(1.0) / vec2(viewWidth, viewHeight);\n"
                    + "  vec2 q = p - t * 0.5;\n"
                    + "  vec4 a = texture2D(s, q);\n"
                    + "  vec4 b = texture2D(s, q + vec2(t.x, 0.0));\n"
                    + "  vec4 c0 = texture2D(s, q + vec2(t.x, t.y));\n"
                    + "  vec4 d = texture2D(s, q + vec2(0.0, t.y));\n"
                    + "  if (comp == 0) return vec4(a.x, b.x, c0.x, d.x);\n"
                    + "  if (comp == 1) return vec4(a.y, b.y, c0.y, d.y);\n"
                    + "  if (comp == 2) return vec4(a.z, b.z, c0.z, d.z);\n"
                    + "  return vec4(a.w, b.w, c0.w, d.w);\n"
                    + "}\n";
    private static final String GLSL120_SHADOW_HELPER =
            "vec4 gnu_shadow2D(sampler2D s, vec3 p) {\n"
                    + "  vec4 e = texture2D(s, p.xy);\n"
                    + "  float d = dot(e.xyz, vec3(1.0, 1.0 / 255.0, 1.0 / 65025.0));\n"
                    + "  return vec4(step(p.z, d));\n"
                    + "}\n"
                    + "vec4 gnu_shadow2DProj(sampler2D s, vec4 p) {\n"
                    + "  vec3 q = p.xyz / max(p.w, 1.0e-6);\n"
                    + "  vec4 e = texture2D(s, q.xy);\n"
                    + "  float d = dot(e.xyz, vec3(1.0, 1.0 / 255.0, 1.0 / 65025.0));\n"
                    + "  return vec4(step(q.z, d));\n"
                    + "}\n";

    /**
     * Apple GL 2.1 has no {@code textureGather} / {@code texture2DLod} / {@code texelFetch}.
     * Replace calls and inject 2.1-safe helpers. Helpers use {@code viewWidth}/{@code viewHeight}.
     */
    private static String polyfillGlsl120Builtins(String src) {
        boolean needLod = src.contains("texture2DLod(") || src.contains("textureLod(");
        boolean needGather = src.contains("textureGather(");
        boolean needFetch = src.contains("texelFetch(");
        boolean needShadow = src.contains("shadow2D") || src.contains("sampler2DShadow");
        if (!needLod && !needGather && !needFetch && !needShadow) {
            return src;
        }
        String rewritten = src;
        if (needShadow) {
            rewritten = rewritten.replace("sampler2DShadow", "sampler2D");
            rewritten = rewriteNamedCalls(rewritten, "shadow2DProj", new CallMapper() {
                @Override
                public String map(int argc, String args) {
                    return "gnu_shadow2DProj(" + args + ")";
                }
            });
            rewritten = rewriteNamedCalls(rewritten, "shadow2D", new CallMapper() {
                @Override
                public String map(int argc, String args) {
                    return "gnu_shadow2D(" + args + ")";
                }
            });
        }
        if (needLod) {
            rewritten = rewriteNamedCalls(rewritten, "texture2DLod", new CallMapper() {
                @Override
                public String map(int argc, String args) {
                    return "gnu_texture2DLod(" + args + ")";
                }
            });
            rewritten = rewriteNamedCalls(rewritten, "textureLod", new CallMapper() {
                @Override
                public String map(int argc, String args) {
                    return "gnu_texture2DLod(" + args + ")";
                }
            });
        }
        if (needGather) {
            rewritten = rewriteNamedCalls(rewritten, "textureGather", new CallMapper() {
                @Override
                public String map(int argc, String args) {
                    if (argc >= 3) {
                        return "gnu_textureGather3(" + args + ")";
                    }
                    return "gnu_textureGather2(" + args + ")";
                }
            });
        }
        if (needFetch) {
            rewritten = rewriteNamedCalls(rewritten, "texelFetch", new CallMapper() {
                @Override
                public String map(int argc, String args) {
                    return "gnu_texelFetch(" + args + ")";
                }
            });
        }
        StringBuilder prototypes = new StringBuilder();
        StringBuilder defs = new StringBuilder();
        if (needLod) {
            prototypes.append("vec4 gnu_texture2DLod(sampler2D s, vec2 p, float lod);\n");
            defs.append(GLSL120_LOD_HELPER);
        }
        if (needGather) {
            prototypes.append("vec4 gnu_textureGather2(sampler2D s, vec2 p);\n");
            prototypes.append("vec4 gnu_textureGather3(sampler2D s, vec2 p, int comp);\n");
            defs.append(GLSL120_GATHER2_HELPER);
            defs.append(GLSL120_GATHER3_HELPER);
        }
        if (needFetch) {
            prototypes.append("vec4 gnu_texelFetch(sampler2D s, ivec2 p, int lod);\n");
            defs.append(GLSL120_TEXELFETCH_HELPER);
        }
        if (needShadow) {
            prototypes.append("vec4 gnu_shadow2D(sampler2D s, vec3 p);\n");
            prototypes.append("vec4 gnu_shadow2DProj(sampler2D s, vec4 p);\n");
            defs.append(GLSL120_SHADOW_HELPER);
        }
        rewritten = insertAfterPreamble(rewritten, prototypes.toString());
        if (!rewritten.endsWith("\n")) {
            rewritten = rewritten + "\n";
        }
        return rewritten + defs;
    }

    /**
     * Sky/far is the {@code vec4(1.0)} sentinel. Packed land keeps extra bits in
     * {@code .yz}, so a lone {@code e.x >= 0.999} (8-bit leftover) classified
     * distant terrain as sky and killed godray occlusion. Clamp land below
     * Chocapic's {@code 1.0-near/far/far} sky test.
     */
    private static final String PACKED_DEPTH_UNPACK =
            "float gnu_decodeDepth(vec4 e) {\n"
                    + "  if (min(e.x, min(e.y, e.z)) >= 0.999) return 1.0;\n"
                    + "  return clamp(dot(e.xyz, vec3(1.0, 1.0/255.0, 1.0/65025.0)), 0.0, 0.99999);\n"
                    + "}\n"
                    + "vec4 gnu_unpackDepth(sampler2D s, vec2 p) {\n"
                    + "  float z = gnu_decodeDepth(texture2D(s, p));\n"
                    + "  return vec4(z);\n"
                    + "}\n"
                    + "vec4 gnu_unpackDepthBias(sampler2D s, vec2 p, float bias) {\n"
                    + "  float z = gnu_decodeDepth(texture2D(s, p, bias));\n"
                    + "  return vec4(z);\n"
                    + "}\n"
                    + "vec4 gnu_unpackDepthLod(sampler2D s, vec2 p, float lod) {\n"
                    + "  float z = gnu_decodeDepth(texture2D(s, p));\n"
                    + "  return vec4(z);\n"
                    + "}\n"
                    + "vec4 gnu_unpackGather2(sampler2D s, vec2 p) {\n"
                    + "  vec2 t = vec2(1.0) / vec2(viewWidth, viewHeight);\n"
                    + "  vec2 q = p - t * 0.5;\n"
                    + "  return vec4(gnu_decodeDepth(texture2D(s, q)), gnu_decodeDepth(texture2D(s, q + vec2(t.x, 0.0))),\n"
                    + "    gnu_decodeDepth(texture2D(s, q + vec2(t.x, t.y))), gnu_decodeDepth(texture2D(s, q + vec2(0.0, t.y))));\n"
                    + "}\n"
                    + "vec4 gnu_unpackGather3(sampler2D s, vec2 p, int comp) {\n"
                    + "  return gnu_unpackGather2(s, p);\n"
                    + "}\n"
                    + "vec4 gnu_unpackFetch(sampler2D s, ivec2 p, int lod) {\n"
                    + "  float z = gnu_decodeDepth(texture2D(s, (vec2(p) + 0.5) / vec2(viewWidth, viewHeight)));\n"
                    + "  return vec4(z);\n"
                    + "}\n";

    /**
     * Chocapic {@code lightPos} uses {@code vec4(sunPosition,1.0)*gbufferProjection}.
     * On spec GLSL that is a row-vector multiply (wrong screen position).
     */
    static String rewriteRowVectorProjection(String src) {
        if (src == null || src.isEmpty() || !src.contains("gbufferProjection")) {
            return src;
        }
        return ROW_VEC_PROJECTION.matcher(src).replaceAll("gbufferProjection * vec4($1, 1.0)");
    }

    /**
     * Composite reads packed window depth from a color texture. Rewrite depth
     * samples so {@code texture2D(depthtex0).x} is a 24-bit float, not 8-bit.
     */
    static String rewritePackedDepthSamples(String src, String fileName) {
        if (src == null || src.isEmpty() || fileName == null || !fileName.endsWith(".fsh")) {
            return src;
        }
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        if (base.startsWith("gbuffers") || base.startsWith("shadow")) {
            return src;
        }
        if (!src.contains("depthtex") && !src.contains("gdepthtex")) {
            return src;
        }
        String rewritten = rewriteDepthSamplerCalls(src, "texture2D", "gnu_unpackDepth", "gnu_unpackDepthBias");
        rewritten = rewriteDepthSamplerCalls(rewritten, "gnu_texture2DLod", "gnu_unpackDepthLod", "gnu_unpackDepthLod");
        rewritten = rewriteDepthSamplerCalls(rewritten, "gnu_textureGather2", "gnu_unpackGather2", "gnu_unpackGather2");
        rewritten = rewriteDepthSamplerCalls(rewritten, "gnu_textureGather3", "gnu_unpackGather3", "gnu_unpackGather3");
        rewritten = rewriteDepthSamplerCalls(rewritten, "gnu_texelFetch", "gnu_unpackFetch", "gnu_unpackFetch");
        if (rewritten.equals(src)) {
            return src;
        }
        String prototypes = "float gnu_decodeDepth(vec4 e);\n"
                + "vec4 gnu_unpackDepth(sampler2D s, vec2 p);\n"
                + "vec4 gnu_unpackDepthBias(sampler2D s, vec2 p, float bias);\n"
                + "vec4 gnu_unpackDepthLod(sampler2D s, vec2 p, float lod);\n"
                + "vec4 gnu_unpackGather2(sampler2D s, vec2 p);\n"
                + "vec4 gnu_unpackGather3(sampler2D s, vec2 p, int comp);\n"
                + "vec4 gnu_unpackFetch(sampler2D s, ivec2 p, int lod);\n";
        rewritten = insertAfterPreamble(rewritten, prototypes);
        if (!rewritten.endsWith("\n")) {
            rewritten = rewritten + "\n";
        }
        return rewritten + PACKED_DEPTH_UNPACK;
    }

    private static String rewriteDepthSamplerCalls(String src, String func, final String twoArg,
            final String threeArg) {
        return rewriteNamedCalls(src, func, new CallMapper() {
            @Override
            public String map(int argc, String args) {
                if (!isPackedDepthSampler(firstArg(args))) {
                    return func + "(" + args + ")";
                }
                String name = argc >= 3 ? threeArg : twoArg;
                return name + "(" + args + ")";
            }
        });
    }

    private static String firstArg(String args) {
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return args.substring(0, i).trim();
            }
        }
        return args.trim();
    }

    private static boolean isPackedDepthSampler(String name) {
        return "depthtex0".equals(name) || "depthtex1".equals(name)
                || "depthtex2".equals(name) || "gdepthtex".equals(name);
    }

    private interface CallMapper {
        String map(int argc, String args);
    }

    private static String rewriteNamedCalls(String src, String func, CallMapper mapper) {
        int i = 0;
        StringBuilder out = new StringBuilder(src.length() + 32);
        while (i < src.length()) {
            int idx = indexOfKeyword(src, func, i);
            if (idx < 0) {
                out.append(src.substring(i));
                break;
            }
            out.append(src, i, idx);
            int open = skipWs(src, idx + func.length());
            if (open >= src.length() || src.charAt(open) != '(') {
                out.append(src, idx, idx + func.length());
                i = idx + func.length();
                continue;
            }
            int close = matchingDelim(src, open, '(', ')');
            if (close < 0) {
                out.append(src.substring(idx));
                break;
            }
            String args = src.substring(open + 1, close);
            out.append(mapper.map(countTopLevelArgs(args), args));
            i = close + 1;
        }
        return out.toString();
    }

    private static int countTopLevelArgs(String args) {
        boolean any = false;
        int depth = 0;
        int n = 1;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                n++;
            }
            if (!Character.isWhitespace(c)) {
                any = true;
            }
        }
        return any ? n : 0;
    }

    /**
     * GLSL requires {@code #extension} immediately after {@code #version}. Chocapic V6 High/Ultra
     * emit {@code GL_ARB_gpu_shader5} late; Apple GL 2.1 also does not support that extension.
     */
    private static String hoistAndStripExtensions(String src) {
        StringBuilder kept = new StringBuilder();
        StringBuilder rest = new StringBuilder(src.length());
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.startsWith("#extension")) {
                String name = extensionName(t);
                if (!dropExtension(name)) {
                    kept.append(lines[i]).append('\n');
                }
                continue;
            }
            if (i > 0) {
                rest.append('\n');
            }
            rest.append(lines[i]);
        }
        if (kept.length() == 0) {
            return rest.toString();
        }
        return insertAfterPreamble(rest.toString(), kept.toString());
    }

    private static String extensionName(String trimmed) {
        String s = trimmed.substring("#extension".length()).trim();
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(0, colon).trim();
        }
        int sp = s.indexOf(' ');
        return sp < 0 ? s : s.substring(0, sp);
    }

    private static boolean dropExtension(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        return n.contains("GPU_SHADER5")
                || n.contains("GPU_SHADER4")
                || n.contains("TEXTURE_GATHER")
                || n.contains("SHADER_TEXTURE_LOD")
                || n.contains("SHADOW_SAMPLERS");
    }

    /** Insert after {@code #version} and any following {@code #extension} / blank / comment lines. */
    private static String insertAfterPreamble(String body, String insert) {
        if (insert == null || insert.isEmpty()) {
            return body;
        }
        int idx = indexOfVersion(body);
        if (idx < 0) {
            return insert + body;
        }
        int nl = body.indexOf('\n', idx);
        if (nl < 0) {
            return body + "\n" + insert;
        }
        int i = skipPreambleExtras(body, nl + 1);
        return body.substring(0, i) + insert + body.substring(i);
    }

    private static int skipPreambleExtras(String body, int from) {
        int i = from;
        while (i < body.length()) {
            int n2 = body.indexOf('\n', i);
            String line = (n2 < 0 ? body.substring(i) : body.substring(i, n2)).trim();
            if (line.startsWith("#extension") || line.isEmpty() || line.startsWith("//")) {
                if (n2 < 0) {
                    return body.length();
                }
                i = n2 + 1;
                continue;
            }
            break;
        }
        return i;
    }

    private static String rewriteSwitches(String src) {
        int i = 0;
        int n = 0;
        StringBuilder out = new StringBuilder(src.length() + 32);
        while (i < src.length()) {
            int idx = indexOfKeyword(src, "switch", i);
            if (idx < 0) {
                out.append(src.substring(i));
                break;
            }
            out.append(src, i, idx);
            int exprOpen = skipWs(src, idx + "switch".length());
            if (exprOpen >= src.length() || src.charAt(exprOpen) != '(') {
                out.append(src, idx, Math.min(idx + 6, src.length()));
                i = idx + 6;
                continue;
            }
            int exprClose = matchingDelim(src, exprOpen, '(', ')');
            if (exprClose < 0) {
                out.append(src.substring(idx));
                break;
            }
            int braceOpen = skipWs(src, exprClose + 1);
            if (braceOpen >= src.length() || src.charAt(braceOpen) != '{') {
                out.append(src, idx, exprClose + 1);
                i = exprClose + 1;
                continue;
            }
            int braceClose = matchingDelim(src, braceOpen, '{', '}');
            if (braceClose < 0) {
                out.append(src.substring(idx));
                break;
            }
            String expr = src.substring(exprOpen + 1, exprClose);
            String body = src.substring(braceOpen + 1, braceClose);
            String converted = switchToIfs(expr, body, n++);
            if (converted == null) {
                out.append(src, idx, braceClose + 1);
            } else {
                out.append(converted);
            }
            i = braceClose + 1;
        }
        return out.toString();
    }

    private static int indexOfKeyword(String src, String word, int from) {
        int i = from;
        while (i < src.length()) {
            int idx = src.indexOf(word, i);
            if (idx < 0) {
                return -1;
            }
            boolean startOk = idx == 0 || !isIdentChar(src.charAt(idx - 1));
            int after = idx + word.length();
            boolean endOk = after >= src.length() || !isIdentChar(src.charAt(after));
            if (startOk && endOk) {
                return idx;
            }
            i = idx + word.length();
        }
        return -1;
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int skipWs(String src, int i) {
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int matchingDelim(String src, int open, char openCh, char closeCh) {
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == openCh) {
                depth++;
            } else if (c == closeCh) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String switchToIfs(String expr, String body, int n) {
        if (body.indexOf('{') >= 0 || body.contains("switch")) {
            return null;
        }
        Matcher c = SWITCH_CASE.matcher(body);
        StringBuilder out = new StringBuilder();
        String var = "gnu_sw" + n;
        out.append("{ int ").append(var).append(" = ").append(expr).append("; ");
        boolean first = true;
        boolean any = false;
        while (c.find()) {
            any = true;
            String label = c.group(1).trim();
            String stmt = c.group(2).trim();
            if (!stmt.endsWith(";")) {
                stmt = stmt + ";";
            }
            if (first) {
                out.append("if (").append(var).append(" == ").append(label).append(") { ");
                first = false;
            } else {
                out.append(" else if (").append(var).append(" == ").append(label).append(") { ");
            }
            out.append(stmt).append(" }");
        }
        if (!any) {
            return null;
        }
        String leftover = SWITCH_CASE.matcher(body).replaceAll("");
        leftover = leftover.replaceAll("default\\s*:\\s*.*?break\\s*;", "");
        if (!leftover.trim().isEmpty()) {
            return null;
        }
        out.append(" }");
        return out.toString();
    }

    private static boolean isIoDecl(String trimmed, String prefix) {
        if (!trimmed.startsWith(prefix)) {
            return false;
        }
        String rest = trimmed.substring(prefix.length()).trim();
        return rest.startsWith("vec") || rest.startsWith("float") || rest.startsWith("mat")
                || rest.startsWith("int") || rest.startsWith("bool") || rest.startsWith("sampler");
    }

    private static String expandIncludes(String source, String fileName, ShaderPack pack, int depth) {
        if (source == null) {
            return "";
        }
        if (depth > MAX_DEPTH) {
            GnuLog.log("Shaders: #include depth exceeded in " + fileName);
            return source;
        }
        StringBuilder out = new StringBuilder(source.length() + 256);
        String[] lines = source.split("\n", -1);
        for (String line : lines) {
            Matcher m = INCLUDE.matcher(line);
            if (m.matches()) {
                String inc = m.group(1);
                String resolved = resolve(fileName, inc);
                String incSrc = pack.read(resolved);
                if (incSrc == null) {
                    GnuLog.log("Shaders: missing include " + resolved + " from " + fileName);
                    continue;
                }
                out.append(expandIncludes(incSrc, resolved, pack, depth + 1));
                if (out.length() == 0 || out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static String resolve(String from, String inc) {
        String path = inc.replace('\\', '/');
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        int slash = from.lastIndexOf('/');
        String dir = slash >= 0 ? from.substring(0, slash + 1) : "";
        return normalize(dir + path);
    }

    private static String normalize(String path) {
        String[] parts = path.split("/");
        java.util.List<String> stack = new java.util.ArrayList<String>();
        for (String p : parts) {
            if (p.isEmpty() || ".".equals(p)) {
                continue;
            }
            if ("..".equals(p)) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            } else {
                stack.add(p);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(stack.get(i));
        }
        return sb.toString();
    }

    static String injectAfterVersion(String body, String macros) {
        int idx = indexOfVersion(body);
        if (idx < 0) {
            return "#version 120\n" + macros + body;
        }
        int nl = body.indexOf('\n', idx);
        if (nl < 0) {
            return body + "\n" + macros;
        }
        int i = skipPreambleExtras(body, nl + 1);
        return body.substring(0, i) + macros + body.substring(i);
    }

    private static int indexOfVersion(String body) {
        int i = 0;
        while (i < body.length()) {
            int nl = body.indexOf('\n', i);
            String line = (nl < 0 ? body.substring(i) : body.substring(i, nl)).trim();
            if (line.startsWith("#version")) {
                return i;
            }
            if (!line.isEmpty() && !line.startsWith("//") && !line.startsWith("#")) {
                return -1;
            }
            if (nl < 0) {
                break;
            }
            i = nl + 1;
        }
        return -1;
    }

    public static int parseVersionDirective(String source) {
        String line = firstVersionLine(source);
        if (line == null) {
            return 0;
        }
        return parseVersionNumber(line);
    }

    /**
     * Core-profile {@code #version} only. {@code #version 400 compatibility} is OptiFine's
     * Windows trick and is downgraded to 120 on GL 2.1, so it does not fail the pack.
     */
    public static int parseCoreProfileVersion(String source) {
        String line = firstVersionLine(source);
        if (line == null) {
            return 0;
        }
        if (line.toLowerCase(Locale.ROOT).contains("compatibility")) {
            return 0;
        }
        return parseVersionNumber(line);
    }

    private static String firstVersionLine(String source) {
        if (source == null) {
            return null;
        }
        String[] lines = source.split("\n", 32);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#version")) {
                return line;
            }
            if (!line.isEmpty() && !line.startsWith("//") && !line.startsWith("#")) {
                return null;
            }
        }
        return null;
    }

    private static int parseVersionNumber(String versionLine) {
        String num = versionLine.substring("#version".length()).trim().split("\\s+")[0];
        try {
            return Integer.parseInt(num.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int parseGlslVersionString(String glsl) {
        if (glsl == null || glsl.isEmpty()) {
            return 120;
        }
        try {
            String num = glsl.trim().split("\\s+")[0];
            String[] parts = num.split("\\.");
            int maj = Integer.parseInt(parts[0]);
            int min = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("[^0-9].*", "")) : 0;
            if (maj >= 100) {
                return maj;
            }
            return maj * 100 + min;
        } catch (Exception e) {
            return 120;
        }
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("mac");
    }

    public static Map<String, String> parseOptionDefines(String propertiesText) {
        Map<String, String> out = new HashMap<String, String>();
        if (propertiesText == null) {
            return out;
        }
        return out;
    }
}
