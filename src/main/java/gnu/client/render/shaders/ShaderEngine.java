package gnu.client.render.shaders;

import gnu.client.common.GnuLog;
import gnu.client.mixin.impl.accessors.IAccessorEntityRenderer;
import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.module.modules.settings.ShadersModule;
import gnu.client.render.terrain.GnuTerrainRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classic 1.8.9 OptiFine shader-pack engine: gbuffers → composite → final.
 */
public final class ShaderEngine {

    public static final ShaderEngine INSTANCE = new ShaderEngine();

    private static final String[] PROGRAMS = {
            "gbuffers_basic", "gbuffers_textured", "gbuffers_textured_lit",
            "gbuffers_skybasic", "gbuffers_skytextured", "gbuffers_clouds",
            "gbuffers_terrain", "gbuffers_damagedblock", "gbuffers_block",
            "gbuffers_beaconbeam", "gbuffers_item", "gbuffers_entities",
            "gbuffers_armor_glint", "gbuffers_spidereyes", "gbuffers_hand",
            "gbuffers_weather", "gbuffers_water", "gbuffers_hand_water",
            "shadow", "deferred", "deferred1", "composite", "composite1", "composite2",
            "composite3", "composite4", "composite5", "composite6", "composite7",
            "final"
    };

    private static final Map<String, String> FALLBACK = new HashMap<String, String>();

    static {
        FALLBACK.put("gbuffers_textured", "gbuffers_basic");
        FALLBACK.put("gbuffers_textured_lit", "gbuffers_textured");
        FALLBACK.put("gbuffers_skybasic", "gbuffers_basic");
        FALLBACK.put("gbuffers_skytextured", "gbuffers_textured");
        FALLBACK.put("gbuffers_terrain", "gbuffers_textured_lit");
        FALLBACK.put("gbuffers_damagedblock", "gbuffers_terrain");
        FALLBACK.put("gbuffers_block", "gbuffers_terrain");
        FALLBACK.put("gbuffers_beaconbeam", "gbuffers_textured");
        FALLBACK.put("gbuffers_item", "gbuffers_textured_lit");
        FALLBACK.put("gbuffers_entities", "gbuffers_textured_lit");
        FALLBACK.put("gbuffers_armor_glint", "gbuffers_textured");
        FALLBACK.put("gbuffers_spidereyes", "gbuffers_textured");
        FALLBACK.put("gbuffers_hand", "gbuffers_textured_lit");
        FALLBACK.put("gbuffers_weather", "gbuffers_textured_lit");
        FALLBACK.put("gbuffers_water", "gbuffers_terrain");
        FALLBACK.put("gbuffers_hand_water", "gbuffers_hand");
        FALLBACK.put("gbuffers_clouds", "gbuffers_textured");
    }

    private static final Pattern CONST_INT = Pattern.compile(
            "const\\s+int\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(-?\\d+)");
    private static final Pattern CONST_FLOAT = Pattern.compile(
            "const\\s+float\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(-?[0-9]*\\.?[0-9]+)");

    private ShaderPack pack;
    private ShaderOptions options = new ShaderOptions("OFF");
    private final Map<String, ShaderProgram> programs = new HashMap<String, ShaderProgram>();
    private final GbufferTarget gbuffer = new GbufferTarget();
    private final ShadowTarget shadow = new ShadowTarget();
    private final FloatBuffer matrixBuf = BufferUtils.createFloatBuffer(16);
    private final float[] modelView = new float[16];
    private final float[] projection = new float[16];
    private final float[] modelViewInv = new float[16];
    private final float[] projectionInv = new float[16];
    private final float[] shadowModelView = new float[16];
    private final float[] shadowProjection = new float[16];
    private final float[] shadowModelViewInv = new float[16];
    private final float[] shadowProjectionInv = new float[16];
    private boolean rendering;
    private boolean gbufferPass;
    private boolean shadowPass;
    private long startNanos;
    private float frameTimeCounter;
    private float lastPartialTicks;
    private int frameCounter;
    private int noiseTex;
    private int shadowMapResolution = 1024;
    private float shadowDistance = 120f;
    private float prevCamX;
    private float prevCamY;
    private float prevCamZ;
    private boolean hasPrevCam;
    private String status = "OFF";
    private int packedDepthSlot = -1;
    private boolean samplesDepthtex1;
    private boolean samplesDepthtex2;
    private boolean snappedOpaque;

    private ShaderEngine() {
        identity(shadowModelView);
        identity(shadowProjection);
        identity(shadowModelViewInv);
        identity(shadowProjectionInv);
    }

    private static void identity(float[] m) {
        java.util.Arrays.fill(m, 0f);
        m[0] = 1f;
        m[5] = 1f;
        m[10] = 1f;
        m[15] = 1f;
    }

    public String status() {
        return status;
    }

    public ShaderOptions options() {
        return options;
    }

    public void reloadCurrent() {
        if (pack == null) {
            return;
        }
        load(pack.name);
    }

    public int programCount() {
        return programs.size();
    }

    public boolean active() {
        if (PerformanceModule.fboOff()) {
            return false;
        }
        return ShadersModule.shadersOn() && pack != null && !programs.isEmpty();
    }

    public boolean isRendering() {
        return rendering && active();
    }

    public void load(String packName) {
        unload();
        if (packName == null || packName.isEmpty() || "OFF".equalsIgnoreCase(packName)) {
            pack = null;
            status = "OFF";
            return;
        }
        if ("Internal".equalsIgnoreCase(packName)) {
            pack = ShaderPack.internal();
            compileAll();
            return;
        }
        File dir = shaderPacksDir();
        if (dir == null) {
            pack = null;
            status = "no shaderpacks folder";
            return;
        }
        File folder = new File(dir, packName);
        File zip = packName.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? folder : new File(dir, packName + ".zip");
        File chosen = folder.isDirectory() ? folder : (zip.isFile() ? zip : null);
        if (chosen == null) {
            GnuLog.log("Shaders: pack not found " + packName);
            pack = null;
            status = "pack not found: " + packName;
            return;
        }
        pack = new ShaderPack(packName, chosen);
        compileAll();
    }

    public void unload() {
        for (ShaderProgram p : programs.values()) {
            p.delete();
        }
        programs.clear();
        gbuffer.delete();
        shadow.delete();
        deleteTex(noiseTex);
        noiseTex = 0;
        pack = null;
        rendering = false;
        gbufferPass = false;
        hasPrevCam = false;
        packedDepthSlot = -1;
        samplesDepthtex1 = false;
        samplesDepthtex2 = false;
        snappedOpaque = false;
        status = "OFF";
    }

    private static void deleteTex(int id) {
        if (id != 0) {
            GL11.glDeleteTextures(id);
        }
    }

    private void compileAll() {
        if (pack == null) {
            return;
        }
        ensureAuxTextures();
        options = ShaderOptions.load(pack, PROGRAMS);
        Map<String, String> defines = glDefines();
        int dim = 0;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null) {
            dim = mc.theWorld.provider.getDimensionId();
        }
        packedDepthSlot = -1;
        samplesDepthtex1 = false;
        samplesDepthtex2 = false;
        int gbufferMask = 0;
        int compositeMask = 0;
        boolean samplesDepth = false;
        boolean banding = false;
        boolean nearest = ShaderProgram.propertiesWantNearest(pack.read("shaders.properties"));
        pack.beginRead();
        try {
            int supported = ShaderPreprocessor.parseGlslVersionString(
                    GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
            int neededCore = 0;
            shadowMapResolution = 1024;
            shadowDistance = 120f;
            for (String name : PROGRAMS) {
                String vsh = pack.readProgram(name + ".vsh", dim);
                String fsh = pack.readProgram(name + ".fsh", dim);
                neededCore = Math.max(neededCore, ShaderPreprocessor.parseCoreProfileVersion(vsh));
                neededCore = Math.max(neededCore, ShaderPreprocessor.parseCoreProfileVersion(fsh));
                applyPackConsts(vsh);
                applyPackConsts(fsh);
                if (containsToken(vsh, "BANDINGFIX") || containsToken(fsh, "BANDINGFIX")) {
                    banding = true;
                }
                if (ShaderProgram.usesYCoCg(vsh) || ShaderProgram.usesYCoCg(fsh)) {
                    nearest = true;
                }
                if (ShaderProgram.samplesDepth(vsh) || ShaderProgram.samplesDepth(fsh)) {
                    samplesDepth = true;
                }
                if (ShaderProgram.samplesDepthtex1(vsh) || ShaderProgram.samplesDepthtex1(fsh)) {
                    samplesDepthtex1 = true;
                }
                if (ShaderProgram.samplesDepthtex2(vsh) || ShaderProgram.samplesDepthtex2(fsh)) {
                    samplesDepthtex2 = true;
                }
                if (name.startsWith("gbuffers")) {
                    gbufferMask |= ShaderProgram.drawBuffersMask(fsh);
                } else if (name.startsWith("composite") || name.startsWith("deferred")) {
                    compositeMask |= ShaderProgram.drawBuffersMask(fsh);
                }
            }
            applyOptionConsts();
            if (neededCore > supported) {
                status = "This pack needs GLSL " + neededCore + " but the game is OpenGL 2.1 / GLSL "
                        + supported + ". Use a GLSL 120 pack.";
                GnuLog.log("Shaders: " + status);
                return;
            }
            packedDepthSlot = ShaderProgram.pickPackedDepthSlot(gbufferMask, compositeMask, samplesDepth);
            if (banding) {
                defines.put("BANDINGFIX", "");
            }
            gbuffer.setColorNearest(nearest);
            GnuLog.log("Shaders: caps MAX_DRAW_BUFFERS=" + GbufferTarget.maxDrawBuffers()
                    + " MAX_COLOR_ATTACHMENTS=" + GbufferTarget.maxColorAttachments()
                    + " MAX_TEXTURE_IMAGE_UNITS=" + GbufferTarget.maxTextureImageUnits()
                    + " MAX_TEXTURE_UNITS=" + GbufferTarget.maxTextureUnits()
                    + " GLSL=" + supported
                    + " packedDepth=" + packedDepthSlot
                    + " nearest=" + nearest
                    + " banding=" + banding);
        } finally {
            pack.endRead();
        }
        boolean unpackDepth = packedDepthSlot >= 0;
        pack.beginRead();
        int failed = 0;
        StringBuilder failedNames = new StringBuilder();
        try {
            for (String name : PROGRAMS) {
                String vsh = pack.readProgram(name + ".vsh", dim);
                String fsh = pack.readProgram(name + ".fsh", dim);
                if (fsh == null) {
                    continue;
                }
                ShaderProgram prog = compileProgram(name, vsh, fsh, defines, unpackDepth);
                if (prog != null) {
                    programs.put(name, prog);
                } else {
                    failed++;
                    if (failedNames.length() > 0) {
                        failedNames.append(',');
                    }
                    failedNames.append(name);
                }
            }
            synthesizeSky("gbuffers_skybasic", "gbuffers_basic", dim, defines, unpackDepth);
            synthesizeSky("gbuffers_skytextured", "gbuffers_textured", dim, defines, unpackDepth);
        } finally {
            pack.endRead();
        }
        status = "pack=" + pack.name + " programs=" + programs.size()
                + (failed > 0 ? " failed=" + failed + " (" + failedNames + ")" : "");
        GnuLog.log("Shaders: " + status);
        if (programs.isEmpty() && !pack.isInternal()) {
            int supported = ShaderPreprocessor.parseGlslVersionString(
                    GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
            status = "0 programs compiled (GLSL " + supported
                    + "). This pack is too new for OpenGL 2.1. Try Sildur's or Chocapic for 1.8.9.";
            GnuLog.log("Shaders: " + status);
        }
    }

    private ShaderProgram compileProgram(String name, String vsh, String fsh, Map<String, String> defines,
            boolean unpackDepth) {
        String vshFile = name + ".vsh";
        String fshFile = name + ".fsh";
        vsh = ShaderPreprocessor.process(vsh == null ? "" : vsh, vshFile, pack, defines, options, unpackDepth);
        fsh = ShaderPreprocessor.process(fsh, fshFile, pack, defines, options, unpackDepth);
        fsh = ShaderProgram.limitDrawBuffers(fsh, GbufferTarget.maxDrawBuffers());
        if (packedDepthSlot >= 0 && name.startsWith("gbuffers")) {
            if (ShaderProgram.isSkyGbuffer(name)) {
                fsh = ShaderProgram.injectSkyFarDepth(fsh, packedDepthSlot);
            } else {
                String injected = ShaderProgram.injectWindowDepth(fsh, packedDepthSlot);
                if (!injected.equals(fsh) && programs.isEmpty()) {
                    GnuLog.log("Shaders: packed 24-bit window depth in colortex" + packedDepthSlot);
                }
                fsh = injected;
            }
            if ("gbuffers_terrain".equals(name)) {
                GnuLog.log("Shaders: gbuffers_terrain DRAWBUFFERS targets="
                        + ShaderProgram.parseDrawBuffers(fsh).length
                        + " packedSlot=" + packedDepthSlot);
            }
        }
        if ("shadow".equals(name)) {
            fsh = ShaderProgram.overwriteFrag0PackedDepth(fsh);
        }
        return ShaderProgram.compile(name, vsh, fsh);
    }

    /**
     * Packs without {@code gbuffers_skybasic} must not reuse terrain-packed {@code gbuffers_basic}
     * (window-z ≈ 0.9998 looks like land). Compile a sky copy that writes far depth.
     */
    private void synthesizeSky(String name, String from, int dim, Map<String, String> defines,
            boolean unpackDepth) {
        if (programs.containsKey(name)) {
            return;
        }
        String fsh = pack.readProgram(name + ".fsh", dim);
        String vsh = pack.readProgram(name + ".vsh", dim);
        if (fsh == null) {
            fsh = pack.readProgram(from + ".fsh", dim);
        }
        if (vsh == null) {
            vsh = pack.readProgram(from + ".vsh", dim);
        }
        if (fsh == null) {
            return;
        }
        ShaderProgram prog = compileProgram(name, vsh, fsh, defines, unpackDepth);
        if (prog != null) {
            programs.put(name, prog);
            GnuLog.log("Shaders: synthesized " + name + " from " + from);
        }
    }

    private static boolean containsToken(String src, String token) {
        return src != null && src.contains(token);
    }

    private static Map<String, String> glDefines() {
        Map<String, String> defines = new HashMap<String, String>();
        int glslVer = ShaderPreprocessor.parseGlslVersionString(
                GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));
        defines.put("MC_GLSL_VERSION", Integer.toString(glslVer));
        String vendor = GL11.glGetString(GL11.GL_VENDOR);
        if (vendor != null) {
            String v = vendor.toLowerCase(Locale.ROOT);
            if (v.contains("nvidia")) {
                defines.put("MC_GL_VENDOR_NVIDIA", "");
            } else if (v.contains("amd") || v.contains("ati")) {
                defines.put("MC_GL_VENDOR_AMD", "");
            } else if (v.contains("intel")) {
                defines.put("MC_GL_VENDOR_INTEL", "");
            } else if (v.contains("apple")) {
                defines.put("MC_GL_VENDOR_APPLE", "");
            }
        }
        return defines;
    }

    public static File shaderPacksDir() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.mcDataDir == null) {
            return null;
        }
        File dir = new File(mc.mcDataDir, "shaderpacks");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public void beginWorldPass(float partialTicks) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        lastPartialTicks = partialTicks;
        captureMatrices();
        renderShadowPass(mc, partialTicks);
        restorePlayerMatrices();
        int w = Math.max(1, mc.displayWidth);
        int h = Math.max(1, mc.displayHeight);
        gbuffer.resize(w, h);
        if (!gbuffer.ready()) {
            status = "gbuffer FBO failed — shaders cannot run on this GL context";
            return;
        }
        if (packedDepthSlot >= 0) {
            gbuffer.setSlotFilter(packedDepthSlot, GL11.GL_NEAREST);
        }
        boolean[] keep = new boolean[8];
        for (int i = 0; i < 8; i++) {
            keep[i] = options != null && !options.colortexClear(i);
        }
        gbuffer.attachRealDepth();
        GlStateManager.colorMask(true, true, true, true);
        gbuffer.clear(keep, packedDepthSlot);
        rendering = true;
        gbufferPass = true;
        snappedOpaque = false;
        startNanos = System.nanoTime();
        useProgram("gbuffers_basic");
        GlErrors.check("beginWorldPass");
    }

    public void bindTerrain() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_terrain");
    }

    public void bindSky() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_skybasic");
    }

    public void bindSkyTextured() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_skytextured");
    }

    public void bindEntities() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_entities");
    }

    public void bindWater() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_water");
    }

    public void bindHand() {
        if (!isRendering()) {
            return;
        }
        useProgram(isHandWater() ? "gbuffers_hand_water" : "gbuffers_hand");
    }

    public void bindBlock() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_block");
    }

    public void bindTextured() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_textured");
    }

    public void bindTexturedLit() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_textured_lit");
    }

    public void bindWeather() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_weather");
    }

    public void bindClouds() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_clouds");
    }

    public void bindDamagedBlock() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_damagedblock");
    }

    public void bindBeacon() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_beaconbeam");
    }

    public void bindGlint() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_armor_glint");
    }

    public void bindSpiderEyes() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_spidereyes");
    }

    public void bindItem() {
        if (!isRendering()) {
            return;
        }
        useProgram("gbuffers_item");
    }

    public void bindLayer(EnumWorldBlockLayer layer) {
        if (layer == EnumWorldBlockLayer.TRANSLUCENT) {
            snapshotOpaqueDepth();
            bindWater();
        } else {
            bindTerrain();
        }
    }

    /**
     * OptiFine copies opaque depth before water. Call at the start of the translucent layer.
     */
    public void snapshotOpaqueDepth() {
        if (!isRendering() || packedDepthSlot < 0 || !samplesDepthtex1 || snappedOpaque) {
            return;
        }
        gbuffer.snapshotDepthFromColor(packedDepthSlot, 1);
        snappedOpaque = true;
    }

    /**
     * World geometry is done. If first-person hand was skipped, composite now.
     */
    public void onWorldPassReturn() {
        if (rendering) {
            finishComposite();
        }
    }

    public void beginHand() {
        if (!rendering || !active()) {
            return;
        }
        if (packedDepthSlot >= 0) {
            if (!snappedOpaque && samplesDepthtex1) {
                gbuffer.snapshotDepthFromColor(packedDepthSlot, 1);
                snappedOpaque = true;
            }
            if (samplesDepthtex2) {
                gbuffer.snapshotDepthFromColor(packedDepthSlot, 2);
            }
        }
        gbuffer.bind();
        gbuffer.attachRealDepth();
        gbufferPass = true;
        bindHand();
    }

    public void endHand() {
        if (!rendering) {
            return;
        }
        finishComposite();
    }

    /**
     * Safety net if {@code renderHand} was skipped after we deferred composite.
     */
    public void onRenderWorldReturn() {
        if (rendering) {
            finishComposite();
        }
    }

    public void endWorldPass() {
        finishComposite();
    }

    private void finishComposite() {
        if (!rendering) {
            return;
        }
        rendering = false;
        gbufferPass = false;
        if (!active()) {
            GbufferTarget.bindMc();
            GL20.glUseProgram(0);
            unbindExtraUnits();
            return;
        }
        frameTimeCounter += (System.nanoTime() - startNanos) / 1.0e9f;
        frameCounter++;
        if (packedDepthSlot >= 0) {
            if (!snappedOpaque && samplesDepthtex1) {
                gbuffer.snapshotDepthFromColor(packedDepthSlot, 1);
                snappedOpaque = true;
            }
            gbuffer.snapshotDepthFromColor(packedDepthSlot, 0);
        }
        gbuffer.detachDepthForSampling();
        gbuffer.bind();
        String[] post = {
                "deferred", "deferred1",
                "composite", "composite1", "composite2", "composite3",
                "composite4", "composite5", "composite6", "composite7"
        };
        int blitIndex = 0;
        for (int i = 0; i < post.length; i++) {
            ShaderProgram prog = programs.get(post[i]);
            if (prog == null) {
                continue;
            }
            runFullscreen(prog, true);
            int written = firstDrawIndex(prog);
            if (written >= 0) {
                blitIndex = written;
            }
        }
        GbufferTarget.bindMc();
        ShaderProgram fin = programs.get("final");
        if (fin != null) {
            runFullscreen(fin, false);
        } else {
            blitColor(blitIndex);
        }
        GlErrors.check("endWorldPass");
        GL20.glUseProgram(0);
        unbindExtraUnits();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.enableTexture2D();
        rememberCamera(Minecraft.getMinecraft());
    }

    private static boolean isHandWater() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null) {
            return false;
        }
        if (mc.thePlayer.isInsideOfMaterial(Material.water)) {
            return true;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) held.getItem()).getBlock();
        if (block == null) {
            return false;
        }
        if (block.getMaterial() == Material.water || block.getMaterial() == Material.ice) {
            return true;
        }
        return block.getBlockLayer() == EnumWorldBlockLayer.TRANSLUCENT;
    }

    public void useProgram(String name) {
        ShaderProgram prog = resolve(name);
        if (prog == null) {
            GL20.glUseProgram(0);
            return;
        }
        prog.use();
        if (gbufferPass) {
            GlStateManager.colorMask(true, true, true, true);
            gbuffer.beginGbuffer(prog.drawBuffers, gbuffer.colorCount());
        } else if (!shadowPass) {
            prog.applyDrawBuffers(gbuffer.colorCount());
        }
        applyUniforms(prog);
    }

    private ShaderProgram resolve(String name) {
        ShaderProgram p = programs.get(name);
        if (p != null) {
            return p;
        }
        String fb = FALLBACK.get(name);
        int guard = 0;
        while (p == null && fb != null && guard++ < 8) {
            p = programs.get(fb);
            fb = FALLBACK.get(fb);
        }
        return p;
    }

    private void applyUniforms(ShaderProgram prog) {
        Minecraft mc = Minecraft.getMinecraft();
        if (gbufferPass || shadowPass) {
            set1i(prog, "gtexture", 0);
            set1i(prog, "tex", 0);
            set1i(prog, "texture", 0);
            if (gbufferPass) {
                set1i(prog, "lightmap", 1);
            }
        }
        set1i(prog, "fogMode", 0);
        set1i(prog, "heldItemId", 0);
        set1i(prog, "heldBlockLightValue", 0);
        set2i(prog, "eyeBrightnessSmooth", 240, 240);
        if (mc != null) {
            set1f(prog, "viewWidth", mc.displayWidth);
            set1f(prog, "viewHeight", mc.displayHeight);
            set1f(prog, "aspectRatio", mc.displayHeight == 0 ? 1f : mc.displayWidth / (float) mc.displayHeight);
            set1f(prog, "far", mc.gameSettings.renderDistanceChunks * 16f);
            set1f(prog, "screenBrightness", mc.gameSettings.gammaSetting);
        }
        set1f(prog, "near", 0.05f);
        set1f(prog, "frameTimeCounter", frameTimeCounter);
        set1i(prog, "frameCounter", frameCounter);
        setMat(prog, "gbufferModelView", modelView);
        setMat(prog, "gbufferModelViewInverse", modelViewInv);
        setMat(prog, "gbufferProjection", projection);
        setMat(prog, "gbufferProjectionInverse", projectionInv);
        setMat(prog, "gbufferPreviousModelView", modelView);
        setMat(prog, "gbufferPreviousProjection", projection);
        setMat(prog, "shadowModelView", shadowModelView);
        setMat(prog, "shadowModelViewInverse", shadowModelViewInv);
        setMat(prog, "shadowProjection", shadowProjection);
        setMat(prog, "shadowProjectionInverse", shadowProjectionInv);
        set1f(prog, "shadowMapResolution", shadowMapResolution);
        set1f(prog, "shadowDistance", shadowDistance);
        if (mc != null && mc.theWorld != null) {
            Entity view = mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : mc.thePlayer;
            float pt = lastPartialTicks;
            double x = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
            double y = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
            double z = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
            set3f(prog, "cameraPosition", (float) x, (float) y, (float) z);
            if (hasPrevCam) {
                set3f(prog, "previousCameraPosition", prevCamX, prevCamY, prevCamZ);
            } else {
                set3f(prog, "previousCameraPosition", (float) x, (float) y, (float) z);
            }
            set3f(prog, "eyePosition", (float) x, (float) y, (float) z);
            set1f(prog, "eyeAltitude", (float) y);
            int wt = (int) (mc.theWorld.getWorldTime() % 24000L);
            set1i(prog, "worldTime", wt);
            set1i(prog, "worldDay", (int) (mc.theWorld.getWorldTime() / 24000L));
            float celestial = mc.theWorld.getCelestialAngle(pt);
            set1f(prog, "sunAngle", celestial);
            float rain = mc.theWorld.getRainStrength(pt);
            set1f(prog, "rainStrength", rain);
            set1f(prog, "wetness", rain);
            set1f(prog, "rainFactor", rain);
            set1i(prog, "moonPhase", mc.theWorld.getMoonPhase());
            set1i(prog, "isEyeInWater", view.isInsideOfMaterial(net.minecraft.block.material.Material.water) ? 1 : 0);
            float sunX = -MathHelper.sin(celestial * (float) Math.PI * 2f);
            float sunY = MathHelper.cos(celestial * (float) Math.PI * 2f);
            float vx = modelView[0] * sunX + modelView[4] * sunY;
            float vy = modelView[1] * sunX + modelView[5] * sunY;
            float vz = modelView[2] * sunX + modelView[6] * sunY;
            float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (len < 1.0e-6f) {
                len = 1f;
            }
            float scale = 100f / len;
            float sx = vx * scale;
            float sy = vy * scale;
            float sz = vz * scale;
            set3f(prog, "sunPosition", sx, sy, sz);
            set3f(prog, "moonPosition", -sx, -sy, -sz);
            if (celestial <= 0.5f) {
                set3f(prog, "shadowLightPosition", sx, sy, sz);
            } else {
                set3f(prog, "shadowLightPosition", -sx, -sy, -sz);
            }
            set3f(prog, "upPosition", modelView[4] * 100f, modelView[5] * 100f, modelView[6] * 100f);
            set3f(prog, "fogColor", 0.7f, 0.8f, 1f);
            set3f(prog, "skyColor", 0.4f, 0.6f, 1f);
        }
        bindSamplers(prog);
    }

    /**
     * Composite keeps colortex0–7 on units 0–7. Depth/shadow/noise use units 8–15
     * ({@code GL_MAX_TEXTURE_IMAGE_UNITS=16} on Apple). Never bind
     * {@code DEPTH_COMPONENT} as {@code sampler2D}, and never use
     * {@code GlStateManager} above unit 7 (1.8.9 only tracks eight units).
     */
    private void bindSamplers(ShaderProgram prog) {
        if (shadowPass) {
            restoreDefaultTexUnit();
            return;
        }
        if (gbufferPass) {
            bindGbufferAuxSamplers(prog);
            restoreDefaultTexUnit();
            return;
        }
        for (int i = 0; i < 8; i++) {
            bindImageUnit(i, rgbaOrDummy(gbuffer.colorTex(i)));
            set1i(prog, "colortex" + i, i);
            setColorAlias(prog, i, i);
        }
        bindPackedDepthUnit(8, packedDepthTex(0));
        set1i(prog, "depthtex0", 8);
        set1i(prog, "gdepthtex", 8);
        bindPackedDepthUnit(9, packedDepthTex(samplesDepthtex1 ? 1 : 0));
        set1i(prog, "depthtex1", 9);
        bindPackedDepthUnit(12, packedDepthTex(samplesDepthtex2 ? 2 : 0));
        set1i(prog, "depthtex2", 12);
        int shadowTex = shadowPackedOrDummy();
        bindImageUnit(10, shadowTex);
        set1i(prog, "shadow", 10);
        set1i(prog, "shadowtex0", 10);
        set1i(prog, "shadowtex1", 10);
        bindImageUnit(11, shadow.ready() && shadow.colorTex() != 0
                ? shadow.colorTex() : gbuffer.dummyFarDepth());
        set1i(prog, "shadowcolor0", 11);
        set1i(prog, "shadowcolor", 11);
        if (noiseTex != 0) {
            bindImageUnit(15, noiseTex);
            set1i(prog, "noisetex", 15);
        }
        restoreDefaultTexUnit();
    }

    private void bindGbufferAuxSamplers(ShaderProgram prog) {
        if (samplerUsed(prog, "shadow") || samplerUsed(prog, "shadowtex0")
                || samplerUsed(prog, "shadowtex1")) {
            bindImageUnit(10, shadowPackedOrDummy());
            set1i(prog, "shadow", 10);
            set1i(prog, "shadowtex0", 10);
            set1i(prog, "shadowtex1", 10);
        }
        if (samplerUsed(prog, "noisetex") && noiseTex != 0) {
            bindImageUnit(15, noiseTex);
            set1i(prog, "noisetex", 15);
        }
    }

    private void bindImageUnit(int unit, int tex) {
        int id = tex != 0 ? tex : gbuffer.dummyFarDepth();
        if (unit < 8) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
            GlStateManager.bindTexture(id);
            return;
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    private void bindPackedDepthUnit(int unit, int tex) {
        bindImageUnit(unit, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    private void restoreDefaultTexUnit() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private void unbindExtraUnits() {
        try {
            for (int i = 15; i >= 8; i--) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            for (int i = 7; i >= 0; i--) {
                if (i == 1) {
                    continue;
                }
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + i);
                GlStateManager.bindTexture(0);
            }
            restoreLightmap();
        } finally {
            restoreDefaultTexUnit();
        }
    }

    private void restoreLightmap() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.entityRenderer == null) {
            return;
        }
        DynamicTexture lightmap = ((IAccessorEntityRenderer) mc.entityRenderer).getLightmapTexture();
        if (lightmap == null) {
            return;
        }
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.bindTexture(lightmap.getGlTextureId());
    }

    private int rgbaOrDummy(int tex) {
        return tex != 0 ? tex : gbuffer.dummyFarDepth();
    }

    private int packedDepthTex(int which) {
        // Always sample the snapshot. Binding the live packed colortex as
        // depthtex0 while High still uses that slot as colortex3 / composite
        // (and composite2 writes DRAWBUFFERS:3) is framebuffer feedback on
        // Apple — the frame splits into a 2x2 tiled view.
        int snap = gbuffer.depthTex(which);
        if (snap != 0) {
            return snap;
        }
        return gbuffer.dummyFarDepth();
    }

    private int shadowPackedOrDummy() {
        if (programs.containsKey("shadow") && shadow.ready() && shadow.colorTex() != 0) {
            return shadow.colorTex();
        }
        return gbuffer.dummyFarDepth();
    }

    private boolean samplerUsed(ShaderProgram prog, String name) {
        return prog.uniform(name) >= 0;
    }

    private boolean colorSamplerUsed(ShaderProgram prog, int slot) {
        if (samplerUsed(prog, "colortex" + slot)) {
            return true;
        }
        if (slot == 0) {
            return samplerUsed(prog, "gcolor");
        }
        if (slot == 1) {
            return samplerUsed(prog, "gdepth");
        }
        if (slot == 2) {
            return samplerUsed(prog, "gnormal");
        }
        if (slot == 3) {
            return samplerUsed(prog, "composite");
        }
        return samplerUsed(prog, "gaux" + (slot - 3));
    }

    private void setColorAlias(ShaderProgram prog, int slot, int unit) {
        if (slot == 0) {
            set1i(prog, "gcolor", unit);
        } else if (slot == 1) {
            set1i(prog, "gdepth", unit);
        } else if (slot == 2) {
            set1i(prog, "gnormal", unit);
        } else if (slot == 3) {
            set1i(prog, "composite", unit);
        } else {
            set1i(prog, "gaux" + (slot - 3), unit);
        }
    }

    private void runFullscreen(ShaderProgram prog, boolean pingPong) {
        if (pingPong) {
            gbuffer.beginComposite(prog.drawBuffers, gbuffer.colorCount());
        }
        prog.use();
        applyUniforms(prog);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.disableCull();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_FOG);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, 1, 0, 1, -1, 1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(0, 0, 0).tex(0, 0).endVertex();
        wr.pos(1, 0, 0).tex(1, 0).endVertex();
        wr.pos(1, 1, 0).tex(1, 1).endVertex();
        wr.pos(0, 1, 0).tex(0, 1).endVertex();
        tess.draw();
        GlErrors.check(pingPong ? "compositeDraw " + prog.name : "finalDraw");
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        if (pingPong) {
            gbuffer.endComposite(prog.drawBuffers, gbuffer.colorCount());
        }
    }

    private void blitColor(int index) {
        GL20.glUseProgram(0);
        restoreDefaultTexUnit();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        int tex = gbuffer.colorTex(index);
        if (tex == 0) {
            tex = gbuffer.colorTex(0);
        }
        GlStateManager.bindTexture(tex != 0 ? tex : gbuffer.dummyFarDepth());
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, 1, 0, 1, -1, 1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(0, 0, 0).tex(0, 0).endVertex();
        wr.pos(1, 0, 0).tex(1, 0).endVertex();
        wr.pos(1, 1, 0).tex(1, 1).endVertex();
        wr.pos(0, 1, 0).tex(0, 1).endVertex();
        tess.draw();
        GlStateManager.bindTexture(0);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.enableDepth();
    }

    private void captureMatrices() {
        matrixBuf.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrixBuf);
        matrixBuf.rewind();
        matrixBuf.get(modelView);
        invert4(modelView, modelViewInv);
        matrixBuf.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrixBuf);
        matrixBuf.rewind();
        matrixBuf.get(projection);
        invert4(projection, projectionInv);
    }

    private static void invert4(float[] m, float[] out) {
        float[] inv = new float[16];
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15]
                + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15]
                - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15]
                + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14]
                - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15]
                - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15]
                + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15]
                - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14]
                + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15]
                + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15]
                - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15]
                + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14]
                - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11]
                - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11]
                + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11]
                - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10]
                + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
        float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        if (Math.abs(det) < 1.0e-8f) {
            System.arraycopy(m, 0, out, 0, 16);
            return;
        }
        det = 1f / det;
        for (int i = 0; i < 16; i++) {
            out[i] = inv[i] * det;
        }
    }

    private void set1i(ShaderProgram prog, String name, int v) {
        int loc = prog.uniform(name);
        if (loc >= 0) {
            GL20.glUniform1i(loc, v);
        }
    }

    private void set2i(ShaderProgram prog, String name, int x, int y) {
        int loc = prog.uniform(name);
        if (loc >= 0) {
            GL20.glUniform2i(loc, x, y);
        }
    }

    private void set1f(ShaderProgram prog, String name, float v) {
        int loc = prog.uniform(name);
        if (loc >= 0) {
            GL20.glUniform1f(loc, v);
        }
    }

    private void set3f(ShaderProgram prog, String name, float x, float y, float z) {
        int loc = prog.uniform(name);
        if (loc >= 0) {
            GL20.glUniform3f(loc, x, y, z);
        }
    }

    private void setMat(ShaderProgram prog, String name, float[] m) {
        int loc = prog.uniform(name);
        if (loc < 0) {
            return;
        }
        matrixBuf.clear();
        matrixBuf.put(m);
        matrixBuf.flip();
        GL20.glUniformMatrix4(loc, false, matrixBuf);
    }

    private void ensureAuxTextures() {
        if (noiseTex == 0) {
            noiseTex = GL11.glGenTextures();
            ByteBuffer data = BufferUtils.createByteBuffer(256 * 256 * 4);
            java.util.Random rng = new java.util.Random(0xC0FFEE);
            for (int i = 0; i < 256 * 256; i++) {
                data.put((byte) rng.nextInt(256));
                data.put((byte) rng.nextInt(256));
                data.put((byte) rng.nextInt(256));
                data.put((byte) 255);
            }
            data.flip();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, noiseTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 256, 256, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void renderShadowPass(Minecraft mc, float partialTicks) {
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return;
        }
        shadow.resize(shadowMapResolution);
        if (!shadow.ready()) {
            return;
        }
        shadowPass = true;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_VIEWPORT_BIT | GL11.GL_POLYGON_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            shadow.clear();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.disableCull();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(1.0f, 4096.0f);
            setupShadowCamera(mc, partialTicks);
            ShaderProgram prog = programs.get("shadow");
            if (prog != null) {
                prog.use();
                applyUniforms(prog);
            } else {
                GL20.glUseProgram(0);
            }
            GnuTerrainRenderer.INSTANCE.drawShadow(mc.renderGlobal, partialTicks, mc.getRenderViewEntity());
            GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GlErrors.check("shadowPass");
        } finally {
            shadowPass = false;
            GL11.glPopAttrib();
        }
    }

    private void setupShadowCamera(Minecraft mc, float partialTicks) {
        float dist = shadowDistance;
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(-dist, dist, -dist, dist, 0.05f, dist * 3f);
        captureNamed(GL11.GL_PROJECTION_MATRIX, shadowProjection);
        invert4(shadowProjection, shadowProjectionInv);

        // Rotation only. Terrain is already camera-relative (origin - cam), matching
        // gbufferModelView. Putting -camera here would double-translate and empty the map.
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0f, 0f, -dist);
        GL11.glRotatef(90f, 1f, 0f, 0f);
        float celestial = mc.theWorld.getCelestialAngle(partialTicks);
        GL11.glRotatef(celestial * 360f, 0f, 0f, 1f);
        captureNamed(GL11.GL_MODELVIEW_MATRIX, shadowModelView);
        invert4(shadowModelView, shadowModelViewInv);
    }

    private void restorePlayerMatrices() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        loadMat(projection);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        loadMat(modelView);
    }

    private void captureNamed(int pname, float[] out) {
        matrixBuf.clear();
        GL11.glGetFloat(pname, matrixBuf);
        matrixBuf.rewind();
        matrixBuf.get(out);
    }

    private void loadMat(float[] m) {
        matrixBuf.clear();
        matrixBuf.put(m);
        matrixBuf.flip();
        GL11.glLoadMatrix(matrixBuf);
    }

    private void rememberCamera(Minecraft mc) {
        if (mc == null) {
            return;
        }
        Entity view = mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : mc.thePlayer;
        if (view == null) {
            return;
        }
        float pt = lastPartialTicks;
        prevCamX = (float) (view.lastTickPosX + (view.posX - view.lastTickPosX) * pt);
        prevCamY = (float) (view.lastTickPosY + (view.posY - view.lastTickPosY) * pt);
        prevCamZ = (float) (view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt);
        hasPrevCam = true;
    }

    private void applyPackConsts(String src) {
        if (src == null) {
            return;
        }
        Matcher mi = CONST_INT.matcher(src);
        while (mi.find()) {
            if ("shadowMapResolution".equals(mi.group(1))) {
                shadowMapResolution = ShadowTarget.clampRes(Integer.parseInt(mi.group(2)));
            }
        }
        Matcher mf = CONST_FLOAT.matcher(src);
        while (mf.find()) {
            if ("shadowDistance".equals(mf.group(1))) {
                shadowDistance = Math.max(16f, Float.parseFloat(mf.group(2)));
            }
        }
    }

    private void applyOptionConsts() {
        if (options == null) {
            return;
        }
        ShaderOptions.Option res = options.get("shadowMapResolution");
        if (res != null && res.value != null) {
            try {
                shadowMapResolution = ShadowTarget.clampRes(Integer.parseInt(res.value.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        ShaderOptions.Option dist = options.get("shadowDistance");
        if (dist != null && dist.value != null) {
            try {
                shadowDistance = Math.max(16f, Float.parseFloat(dist.value.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    static int parseConstInt(String src, String name, int fallback) {
        if (src == null) {
            return fallback;
        }
        Matcher m = CONST_INT.matcher(src);
        while (m.find()) {
            if (name.equals(m.group(1))) {
                return Integer.parseInt(m.group(2));
            }
        }
        return fallback;
    }

    static float parseConstFloat(String src, String name, float fallback) {
        if (src == null) {
            return fallback;
        }
        Matcher m = CONST_FLOAT.matcher(src);
        while (m.find()) {
            if (name.equals(m.group(1))) {
                return Float.parseFloat(m.group(2));
            }
        }
        return fallback;
    }

    public static String fallbackOf(String name) {
        return FALLBACK.get(name);
    }

    private static int firstDrawIndex(ShaderProgram prog) {
        if (prog == null || prog.drawBuffers == null || prog.drawBuffers.length == 0) {
            return -1;
        }
        return prog.drawBuffers[0] - OpenGlHelper.GL_COLOR_ATTACHMENT0;
    }
}
