package gnu.client.render.shaders;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShaderPreprocessorTest {

    @Test
    public void injectsMacrosAfterVersion() {
        ShaderPack pack = ShaderPack.internal();
        Map<String, String> defines = new HashMap<String, String>();
        defines.put("SHADOW_DIST", "80");
        String out = ShaderPreprocessor.process("#version 120\nvoid main(){}\n", "final.fsh", pack, defines);
        assertTrue(out.contains("#version 120"));
        assertTrue(out.contains("#define MC_VERSION 10800"));
        assertTrue(!out.contains("#define BANDINGFIX"));
        assertTrue(out.contains("#define SHADOW_DIST 80"));
        int version = out.indexOf("#version");
        int macro = out.indexOf("#define MC_VERSION");
        assertTrue(macro > version);
    }

    @Test
    public void missingIncludeIsSkipped() {
        ShaderPack pack = ShaderPack.internal();
        String src = "#version 120\n#include \"missing.glsl\"\nvoid main(){}\n";
        String out = ShaderPreprocessor.process(src, "final.fsh", pack, new HashMap<String, String>());
        assertTrue(out.contains("void main()"));
        assertTrue(!out.contains("#include"));
    }

    @Test
    public void terrainFallsBackToTexturedLit() {
        assertEquals("gbuffers_textured_lit", ShaderEngine.fallbackOf("gbuffers_terrain"));
    }

    @Test
    public void complementaryProgramsAreUnderWorldFolder() {
        String[] c = ShaderPack.programCandidates("gbuffers_terrain.vsh", 0);
        assertEquals("world0/gbuffers_terrain.vsh", c[0]);
        assertEquals("gbuffers_terrain.vsh", c[1]);
        c = ShaderPack.programCandidates("final.fsh", -1);
        assertEquals("world-1/final.fsh", c[0]);
    }

    @Test
    public void parseVersionDirectiveReadsFirstVersion() {
        assertEquals(130, ShaderPreprocessor.parseVersionDirective("#version 130\nvoid main(){}\n"));
        assertEquals(400, ShaderPreprocessor.parseVersionDirective("#version 400 compatibility\nvoid main(){}\n"));
        assertEquals(0, ShaderPreprocessor.parseVersionDirective("void main(){}\n"));
        assertEquals(0, ShaderPreprocessor.parseVersionDirective(null));
    }

    @Test
    public void rewriteInVecToVaryingInFragment() {
        String out = ShaderPreprocessor.rewriteForGlsl120(
                "#version 120\nvarying vec2 texcoord;\nin vec2 coord0;\nvoid main(){}\n",
                "final.fsh");
        assertTrue(out.contains("varying vec2 coord0"));
        assertTrue(!out.contains("in vec2 coord0"));
    }

    @Test
    public void rewriteLeavesFunctionInParams() {
        String src = "float yDistAxis (in float degrees) {\n  return degrees;\n}\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "final.fsh");
        assertTrue(out.contains("in float degrees"));
    }

    @Test
    public void chocapicV6CompatibilityVersionIsClampedTo120() {
        String src = "#version 400 compatibility\nout vec4 color;\nvoid main(){}\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "gbuffers_weather.vsh");
        assertTrue(out.contains("#version 120"));
        assertTrue(!out.contains("#version 400"));
        assertTrue(out.contains("varying vec4 color"));
        assertTrue(!out.contains("out vec4 color"));
    }

    @Test
    public void stripsFloatSuffixForAppleGlsl120() {
        String src = "vec4 frag2 = vec4((normal), 1.0f);\nfloat masksize = 0.004f;\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "gbuffers_water.fsh");
        assertTrue(out.contains("1.0)"));
        assertTrue(out.contains("0.004;"));
        assertTrue(!out.contains("1.0f"));
        assertTrue(!out.contains("0.004f"));
    }

    @Test
    public void rewritesIntegerModulo() {
        String src = "vec2 offset = vec2(float(i/2 - 1), float(i%2 - 1)) * texel;\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "final.fsh");
        assertTrue(out.contains("int(mod(float(i), float(2)))"));
        assertTrue(!out.contains("i%2"));
    }

    @Test
    public void replacesGlFragColorWhenFragDataPresent() {
        String src = "void main() {\n gl_FragColor = col;\n gl_FragData[0] = texture2D(tex,texcoord.xy);\n}\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "shadow.fsh");
        assertTrue(out.contains("gl_FragData[0] = col;"));
        assertTrue(!out.contains("gl_FragColor"));
    }

    @Test
    public void rewritesSimpleSwitchToIf() {
        String src = "switch(int(mc_Entity.x)){\n\tcase 31 : wavy1=true; break;\n\tcase 18 : wavy2=true; break;\n}\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "shadow.vsh");
        assertTrue(!out.contains("switch"));
        assertTrue(out.contains("gnu_sw0 == 31"));
        assertTrue(out.contains("gnu_sw0 == 18"));
        assertTrue(out.contains("wavy1=true;"));
    }

    @Test
    public void coreProfile130IsNotTreatedAsCompatibility() {
        assertEquals(130, ShaderPreprocessor.parseCoreProfileVersion("#version 130\nvoid main(){}\n"));
        assertEquals(0, ShaderPreprocessor.parseCoreProfileVersion("#version 400 compatibility\nvoid main(){}\n"));
        assertEquals(120, ShaderPreprocessor.parseCoreProfileVersion("#version 120\nvoid main(){}\n"));
        assertEquals(400, ShaderPreprocessor.parseVersionDirective("#version 400 compatibility\nvoid main(){}\n"));
    }

    @Test
    public void processClampsV6WeatherHeader() {
        ShaderPack pack = ShaderPack.internal();
        Map<String, String> defines = new HashMap<String, String>();
        defines.put("MC_GLSL_VERSION", "120");
        String src = "#version 400 compatibility\nout vec4 color;\nout vec3 fragpos;\nvoid main(){}\n";
        String out = ShaderPreprocessor.process(src, "gbuffers_weather.vsh", pack, defines);
        assertTrue(out.startsWith("#version 120"));
        assertTrue(out.contains("varying vec4 color"));
        assertTrue(out.contains("#define MC_GLSL_VERSION 120"));
    }

    @Test
    public void polyfillsTextureGatherTwoArg() {
        String src = "#version 400 compatibility\nvoid main(){ vec4 Samplee = textureGather(gdepthtex, noisetc); }\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "composite.fsh");
        assertTrue(out.contains("gnu_textureGather2(gdepthtex, noisetc)"));
        assertTrue(!out.contains("textureGather("));
        assertTrue(out.contains("vec4 gnu_textureGather2(sampler2D s, vec2 p);"));
        assertTrue(out.contains("vec4 gnu_textureGather2(sampler2D s, vec2 p) {"));
        assertTrue(out.indexOf("vec4 gnu_textureGather2(sampler2D s, vec2 p);") < out.indexOf("void main()"));
        assertTrue(out.indexOf("void main()") < out.lastIndexOf("vec4 gnu_textureGather2(sampler2D s, vec2 p) {"));
    }

    @Test
    public void polyfillsTextureGatherComponent() {
        String src = "color += textureGather(gdepth, vec2(1.0)/vec2(viewWidth,viewHeight), 3);\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "final.fsh");
        assertTrue(out.contains("gnu_textureGather3(gdepth, vec2(1.0)/vec2(viewWidth,viewHeight), 3)"));
        assertTrue(out.contains("vec4 gnu_textureGather3"));
        assertTrue(!out.contains("textureGather("));
    }

    @Test
    public void polyfillsTexture2DLod() {
        String src = "gr += texture2DLod(gdepth, textCoord + deltaTextCoord, 1).a;\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "final.fsh");
        assertTrue(out.contains("gnu_texture2DLod(gdepth, textCoord + deltaTextCoord, 1)"));
        assertTrue(!out.contains("+= texture2DLod("));
        assertTrue(out.contains("vec4 gnu_texture2DLod"));
    }

    @Test
    public void dropsLateGpuShader5Extension() {
        String src = "#version 400 compatibility\nvoid foo(){}\n#line 1831\n#extension GL_ARB_gpu_shader5 : enable\nvoid main(){}\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "composite2.fsh");
        assertTrue(out.contains("#version 120"));
        assertTrue(!out.contains("#extension"));
        assertTrue(out.contains("void foo()"));
        assertTrue(out.contains("void main()"));
    }

    @Test
    public void processPutsMacrosBeforeGatherHelpers() {
        ShaderPack pack = ShaderPack.internal();
        Map<String, String> defines = new HashMap<String, String>();
        defines.put("MC_GLSL_VERSION", "120");
        String src = "#version 400 compatibility\n#extension GL_ARB_gpu_shader5 : enable\n"
                + "void main(){ vec4 s = textureGather(gdepthtex, uv); }\n";
        String out = ShaderPreprocessor.process(src, "composite.fsh", pack, defines);
        int version = out.indexOf("#version 120");
        int macro = out.indexOf("#define MC_VERSION");
        int proto = out.indexOf("vec4 gnu_textureGather2(sampler2D s, vec2 p);");
        int main = out.indexOf("void main()");
        int def = out.lastIndexOf("vec4 gnu_textureGather2(sampler2D s, vec2 p) {");
        assertTrue(version >= 0 && macro > version && proto > macro && main > proto && def > main);
        assertTrue(!out.contains("GL_ARB_gpu_shader5"));
    }

    @Test
    public void polyfillsTexelFetch() {
        String src = "#version 400 compatibility\n"
                + "void main(){\n"
                + "ivec2 nts = ivec2(floor(gl_FragCoord.xy)*2.);\n"
                + "vec3 albedo = pow(texelFetch(gaux1,nts,0).rgb,vec3(2.2));\n"
                + "albedo += pow(texelFetch(gaux1,nts + 1,0).rgb,vec3(2.2));\n"
                + "albedo += pow(texelFetch(gaux1,nts + ivec2(0,1),0).rgb,vec3(2.2));\n"
                + "}\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "composite2.fsh");
        assertTrue(out.contains("gnu_texelFetch(gaux1,nts,0)"));
        assertTrue(out.contains("gnu_texelFetch(gaux1,nts + 1,0)"));
        assertTrue(out.contains("gnu_texelFetch(gaux1,nts + ivec2(0,1),0)"));
        assertTrue(!out.contains("(texelFetch("));
        assertTrue(out.contains("vec4 gnu_texelFetch(sampler2D s, ivec2 p, int lod);"));
        assertTrue(out.contains("vec4 gnu_texelFetch(sampler2D s, ivec2 p, int lod) {"));
    }

    @Test
    public void polyfillsShadow2D() {
        String src = "uniform sampler2DShadow shadow;\n"
                + "void main(){ float s = shadow2D(shadow, pos).x; s += shadow2DProj(shadow, pos4).x; }\n";
        String out = ShaderPreprocessor.rewriteForGlsl120(src, "composite.fsh");
        assertTrue(out.contains("uniform sampler2D shadow;"));
        assertTrue(!out.contains("sampler2DShadow"));
        assertTrue(out.contains("gnu_shadow2D(shadow, pos)"));
        assertTrue(out.contains("gnu_shadow2DProj(shadow, pos4)"));
        assertTrue(out.contains("vec4 gnu_shadow2D(sampler2D s, vec3 p);"));
        assertTrue(out.contains("1.0 / 65025.0"));
    }

    @Test
    public void parseShadowConstsFromPackSource() {
        String src = "const int shadowMapResolution = 2048;\nconst float shadowDistance = 140.0;\n";
        assertEquals(2048, ShaderEngine.parseConstInt(src, "shadowMapResolution", 1024));
        assertEquals(140.0f, ShaderEngine.parseConstFloat(src, "shadowDistance", 80f), 0.01f);
        assertEquals(1024, ShaderEngine.parseConstInt("", "shadowMapResolution", 1024));
        assertEquals(2048, ShadowTarget.clampRes(3000));
        assertEquals(256, ShadowTarget.clampRes(100));
        assertEquals(1024, ShadowTarget.clampRes(1024));
    }

    @Test
    public void parseDrawBuffersUsesLastCommentAndMrt() {
        int att0 = ShaderProgram.parseDrawBuffers("/* DRAWBUFFERS:0 */\n")[0];
        int[] mrt = ShaderProgram.parseDrawBuffers("/* DRAWBUFFERS:0 */\n/* DRAWBUFFERS:34 */\n");
        assertEquals(2, mrt.length);
        assertEquals(att0 + 3, mrt[0]);
        assertEquals(att0 + 4, mrt[1]);
        int[] single = ShaderProgram.parseDrawBuffers("void main(){}\n");
        assertEquals(1, single.length);
        assertEquals(att0, single[0]);
    }

    @Test
    public void limitDrawBuffersKeepsAlbedoWhenSpecStartsAtZero() {
        String src = "/* DRAWBUFFERS:01 */\ngl_FragData[0]=a;\ngl_FragData[1]=b;\n";
        String out = ShaderProgram.limitDrawBuffers(src, 1);
        assertTrue(out.contains("DRAWBUFFERS:0"));
        assertTrue(!out.contains("DRAWBUFFERS:01"));
        assertTrue(out.contains("gl_FragData[1]=b"));
    }

    @Test
    public void limitDrawBuffersKeepsLastSceneOutput() {
        String src = "/* DRAWBUFFERS:34 */\ngl_FragData[0]=sky;\ngl_FragData[1]=scene;\n";
        String out = ShaderProgram.limitDrawBuffers(src, 1);
        assertTrue(out.contains("DRAWBUFFERS:4"));
        assertTrue(!out.contains("DRAWBUFFERS:34"));
        assertTrue(out.contains("gl_FragData[0]=scene"));
        assertTrue(!out.contains("gl_FragData[1]=scene"));
    }

    @Test
    public void limitDrawBuffersNoopWhenWithinCap() {
        String src = "/* DRAWBUFFERS:34 */\ngl_FragData[0]=sky;\ngl_FragData[1]=scene;\n";
        assertEquals(src, ShaderProgram.limitDrawBuffers(src, 8));
        assertEquals(src, ShaderProgram.limitDrawBuffers(src, 2));
    }

    @Test
    public void injectWindowDepthInsertsSecondTarget() {
        String src = "/* DRAWBUFFERS:01 */\nvoid main(){\n  gl_FragData[0]=a;\n  gl_FragData[1]=b;\n}\n";
        String out = ShaderProgram.injectWindowDepth(src, 3);
        assertTrue(out.contains("DRAWBUFFERS:031"));
        assertTrue(!out.contains("DRAWBUFFERS:013"));
        assertTrue(out.contains("gl_FragData[1] = gnu_packDepth(gl_FragCoord.z);"));
        assertTrue(out.contains("gl_FragData[2]=b;"));
        int[] bufs = ShaderProgram.parseDrawBuffers(out);
        assertEquals(3, bufs.length);
        int att0 = ShaderProgram.parseDrawBuffers("/* DRAWBUFFERS:0 */\n")[0];
        assertEquals(att0, bufs[0]);
        assertEquals(att0 + 3, bufs[1]);
        assertEquals(att0 + 1, bufs[2]);
    }

    @Test
    public void injectWindowDepthAfterLightMainDrawBuffers() {
        String src = "void main(){\n  gl_FragData[0]=a;\n  gl_FragData[1]=b;\n/* DRAWBUFFERS:01 */\n}\n";
        String out = ShaderProgram.injectWindowDepth(src, 3);
        assertTrue(out.contains("DRAWBUFFERS:031"));
        assertTrue(out.contains("gl_FragData[1] = gnu_packDepth(gl_FragCoord.z);"));
        assertTrue(out.contains("gl_FragData[2]=b;"));
        int[] bufs = ShaderProgram.parseDrawBuffers(out);
        assertEquals(3, bufs.length);
    }

    @Test
    public void shiftFragDataIndex1DoesNotMatchIndex10() {
        String src = "gl_FragData[1]=a; gl_FragData[10]=b;";
        String onlyOne = src.replaceAll("gl_FragData\\[1\\]", "gl_FragData[2]");
        assertTrue(onlyOne.contains("gl_FragData[2]=a;"));
        assertTrue(onlyOne.contains("gl_FragData[10]=b;"));
        String out = ShaderProgram.shiftFragDataFrom(src, 1);
        assertTrue(out.contains("gl_FragData[2]=a;"));
        assertTrue(out.contains("gl_FragData[11]=b;"));
    }

    @Test
    public void injectWindowDepthWeatherBecomesSecondTarget() {
        String src = "/* DRAWBUFFERS:7 */\nvoid main(){ gl_FragData[0]=a; }\n";
        String out = ShaderProgram.injectWindowDepth(src, 3);
        assertTrue(out.contains("DRAWBUFFERS:73"));
        assertTrue(out.contains("gl_FragData[1] = gnu_packDepth(gl_FragCoord.z);"));
    }

    @Test
    public void injectWindowDepthSkipsWhenSlotUsed() {
        String src = "/* DRAWBUFFERS:013 */\nvoid main(){ gl_FragData[0]=a; }\n";
        assertEquals(src, ShaderProgram.injectWindowDepth(src, 3));
    }

    @Test
    public void injectWindowDepthAddsSpecWhenMissing() {
        String src = "void main(){\n  gl_FragData[0]=a;\n}\n";
        String out = ShaderProgram.injectWindowDepth(src, 3);
        assertTrue(out.contains("DRAWBUFFERS:03"));
        assertTrue(out.contains("gnu_packDepth(gl_FragCoord.z)"));
    }

    @Test
    public void injectSkyFarDepthWritesOne() {
        String src = "/* DRAWBUFFERS:0 */\nvoid main(){\n  gl_FragData[0]=a;\n}\n";
        String out = ShaderProgram.injectSkyFarDepth(src, 3);
        assertTrue(out.contains("DRAWBUFFERS:03"));
        assertTrue(out.contains("gl_FragData[1] = vec4(1.0);"));
        assertTrue(!out.contains("gnu_packDepth"));
        assertTrue(ShaderProgram.isSkyGbuffer("gbuffers_skybasic"));
        assertTrue(ShaderProgram.isSkyGbuffer("gbuffers_skytextured"));
        assertTrue(ShaderProgram.isSkyGbuffer("gbuffers_clouds"));
        assertTrue(!ShaderProgram.isSkyGbuffer("gbuffers_terrain"));
    }

    @Test
    public void injectWindowDepthPacksFarAsOne() {
        String out = ShaderProgram.injectWindowDepth(
                "/* DRAWBUFFERS:0 */\nvoid main(){ gl_FragData[0]=a; }\n", 3);
        assertTrue(out.contains("if (z >= 0.99999) return vec4(1.0);"));
    }

    @Test
    public void packedDepthUnpackRewritesCompositeSamples() {
        String src = "#version 120\nuniform sampler2D depthtex0;\n"
                + "void main(){ gl_FragData[0] = texture2D(depthtex0, texcoord).xxxx; }\n";
        String out = ShaderPreprocessor.rewritePackedDepthSamples(src, "composite3.fsh");
        assertTrue(out.contains("gnu_unpackDepth(depthtex0, texcoord)"));
        assertTrue(out.contains("gnu_decodeDepth"));
        assertTrue(out.contains("min(e.x, min(e.y, e.z)) >= 0.999"));
        assertTrue(out.contains("0.99999"));
        assertTrue(!out.contains("if (e.x >= 0.999) return 1.0;"));
        assertTrue(!out.contains("texture2D(depthtex0"));
    }

    @Test
    public void chocapicLightPosUsesColumnVectorProjection() {
        String src = "vec4 tpos = vec4(sunPosition,1.0)*gbufferProjection;\n"
                + "tpos = vec4(tpos.xyz/tpos.w,1.0);\n";
        String out = ShaderPreprocessor.rewriteRowVectorProjection(src);
        assertTrue(out.contains("gbufferProjection * vec4(sunPosition, 1.0)"));
        assertTrue(!out.contains("vec4(sunPosition,1.0)*gbufferProjection"));
        String already = "vec4 tpos = gbufferProjection * vec4(sunPosition, 1.0);\n";
        assertEquals(already, ShaderPreprocessor.rewriteRowVectorProjection(already));
        String moon = "vec4 tpos = vec4(shadowLightPosition, 1.0) * gbufferProjection;\n";
        assertTrue(ShaderPreprocessor.rewriteRowVectorProjection(moon)
                .contains("gbufferProjection * vec4(shadowLightPosition, 1.0)"));
    }

    @Test
    public void packedDepthUnpackSkipsGbuffers() {
        String src = "#version 120\nvoid main(){ gl_FragData[0] = texture2D(depthtex0, texcoord); }\n";
        assertEquals(src, ShaderPreprocessor.rewritePackedDepthSamples(src, "gbuffers_terrain.fsh"));
    }

    @Test
    public void parseDrawBuffersKeepsSpecOrder() {
        int[] none = ShaderProgram.parseDrawBuffers("void main(){}\n");
        int[] packed = ShaderProgram.parseDrawBuffers("/* DRAWBUFFERS:013 */\n");
        int[] water = ShaderProgram.parseDrawBuffers("/* DRAWBUFFERS:526 */\n");
        assertEquals(1, none.length);
        assertEquals(3, packed.length);
        assertEquals(none[0], packed[0]);
        assertEquals(none[0] + 1, packed[1]);
        assertEquals(none[0] + 3, packed[2]);
        assertEquals(3, water.length);
        assertEquals(none[0] + 5, water[0]);
        assertEquals(none[0] + 2, water[1]);
        assertEquals(none[0] + 6, water[2]);
    }

    @Test
    public void pickPackedDepthSlotSkipsUsedAndAlbedo() {
        int used = ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:01 */\n");
        used |= ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:4 */\n");
        used |= ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:562 */\n");
        used |= ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:7 */\n");
        assertEquals(3, ShaderProgram.pickPackedDepthSlot(used, true));
        assertEquals(-1, ShaderProgram.pickPackedDepthSlot(used, false));
        assertEquals(-1, ShaderProgram.pickPackedDepthSlot(0xFF, true));
        assertEquals(7, ShaderProgram.pickPackedDepthSlot(1, true));
        assertEquals(0, ShaderProgram.drawBuffersMask(null));
        assertEquals(1, ShaderProgram.drawBuffersMask("void main(){}\n"));
    }

    @Test
    public void pickPackedDepthSlotPrefersUnusedCompositeSlot() {
        int gb = ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:01 */\n");
        gb |= ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:526 */\n");
        gb |= ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:7 */\n");
        gb |= ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:0 */\n");
        int post = ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:4 */\n");
        post |= ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:5 */\n");
        assertEquals(4, ShaderProgram.pickPackedDepthSlot(gb, true));
        assertEquals(3, ShaderProgram.pickPackedDepthSlot(gb, post, true));
    }

    @Test
    public void pickPackedDepthSlotFallsBackWhenCompositeFillsSpare() {
        int gb = 0xFF & ~(1 << 3);
        int post = ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:3 */\n");
        post |= ShaderProgram.drawBuffersMask("/* DRAWBUFFERS:34 */\n");
        assertEquals(-1, ShaderProgram.pickPackedDepthSlot(gb | post, true));
        assertEquals(3, ShaderProgram.pickPackedDepthSlot(gb, post, true));
    }

    @Test
    public void takeFreeUnitPrefersHighAndOverlaysWhenFull() {
        boolean[] used = new boolean[8];
        used[0] = true;
        assertEquals(7, ShaderProgram.takeFreeUnit(used, 3));
        assertEquals(6, ShaderProgram.takeFreeUnit(used, 3));
        for (int i = 0; i < 8; i++) {
            used[i] = true;
        }
        assertEquals(3, ShaderProgram.takeFreeUnit(used, 3));
        boolean[] empty = new boolean[8];
        assertEquals(7, ShaderProgram.takeFreeUnit(empty, 3));
        assertTrue(empty[7]);
    }

    @Test
    public void shadowFragOverwritesColor0WithPackedDepth() {
        String src = "#version 120\nvoid main(){ gl_FragData[0] = gl_Color; }\n";
        String out = ShaderProgram.overwriteFrag0PackedDepth(src);
        assertTrue(out.contains("gnu_packDepth(gl_FragCoord.z)"));
        assertTrue(out.contains("vec4 gnu_packDepth"));
        assertEquals(null, ShaderProgram.overwriteFrag0PackedDepth(null));
        assertEquals("", ShaderProgram.overwriteFrag0PackedDepth(""));
    }

    @Test
    public void injectSkippedWhenSlotAlreadyInSpec() {
        String src = "/* DRAWBUFFERS:07 */\nvoid main(){ gl_FragData[0]=a; }\n";
        assertEquals(src, ShaderProgram.injectWindowDepth(src, 7));
    }

    @Test
    public void internalPackShipsGbuffers() {
        ShaderPack pack = ShaderPack.internal();
        assertTrue(pack.read("gbuffers_basic.fsh").contains("DRAWBUFFERS:0"));
        assertTrue(pack.read("gbuffers_textured.fsh").contains("texture2D"));
        assertTrue(pack.read("gbuffers_skybasic.fsh").contains("gl_FragData[0]"));
        assertTrue(pack.read("gbuffers_skytextured.fsh").contains("texture2D"));
        assertTrue(pack.read("final.fsh").contains("colortex0"));
    }

    @Test
    public void bandingFixOnlyWhenRequested() {
        ShaderPack pack = ShaderPack.internal();
        Map<String, String> defines = new HashMap<String, String>();
        String off = ShaderPreprocessor.process("#version 120\nvoid main(){}\n", "final.fsh", pack, defines);
        assertTrue(!off.contains("#define BANDINGFIX"));
        defines.put("BANDINGFIX", "");
        String on = ShaderPreprocessor.process("#version 120\nvoid main(){}\n", "final.fsh", pack, defines);
        assertTrue(on.contains("#define BANDINGFIX"));
    }

    @Test
    public void unpackRewriteSkippedWhenDisabled() {
        ShaderPack pack = ShaderPack.internal();
        Map<String, String> defines = new HashMap<String, String>();
        String src = "#version 120\nuniform sampler2D depthtex0;\n"
                + "void main(){ gl_FragData[0] = texture2D(depthtex0, texcoord); }\n";
        String out = ShaderPreprocessor.process(src, "composite.fsh", pack, defines, null, false);
        assertTrue(out.contains("texture2D(depthtex0, texcoord)"));
        assertTrue(!out.contains("gnu_unpackDepth"));
    }

    @Test
    public void skyFarDepthNotPackedWindowZ() {
        String src = "/* DRAWBUFFERS:0 */\nvoid main(){\n  gl_FragData[0]=a;\n}\n";
        String sky = ShaderProgram.injectSkyFarDepth(src, 7);
        String land = ShaderProgram.injectWindowDepth(src, 7);
        assertTrue(sky.contains("DRAWBUFFERS:07"));
        assertTrue(sky.contains("vec4(1.0)"));
        assertTrue(!sky.contains("gnu_packDepth"));
        assertTrue(land.contains("gnu_packDepth(gl_FragCoord.z)"));
        assertTrue(ShaderProgram.isSkyGbuffer("gbuffers_skybasic"));
        assertTrue(!ShaderProgram.isSkyGbuffer("gbuffers_basic"));
    }

    @Test
    public void ycocgDetectionAndNearestProps() {
        assertTrue(ShaderProgram.usesYCoCg("vec3 c = RGB2YCoCg(albedo);"));
        assertTrue(!ShaderProgram.usesYCoCg("void main(){ gl_FragData[0]=a; }"));
        assertTrue(ShaderProgram.propertiesWantNearest("texture.gbuffers.colortex0=nearest\n"));
        assertTrue(!ShaderProgram.propertiesWantNearest("oldHandLight=true\n"));
    }
}
