package gnu.client.render;

import gnu.client.GnuClientMod;
import gnu.client.common.GnuLog;
import gnu.client.module.modules.visual.MotionBlurModule;
import gnu.client.runtime.FreeLookHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/**
 * High-frame-rate frame blending. Each frame is composited into a persistent
 * accumulation buffer with an exponential weight, which is what a 240 FPS
 * capture downsampled to display rate looks like: everything that moves on
 * screen trails, not just camera rotation.
 *
 * <p>Two things run per frame:
 * <ul>
 *   <li><b>Intra-frame streak</b> — the camera sweep inside a single frame
 *       interval is integrated with a box filter. Accumulation alone produces
 *       countable discrete ghosts; this fills the gap between them.</li>
 *   <li><b>Exponential accumulation</b> — the blended result is copied back and
 *       reused next frame, so the trail extends over many frames.</li>
 * </ul>
 *
 * <p>Both are driven by wall-clock shutter time, so the trail has the same
 * real-world length at 30 FPS and 240 FPS. Blending happens in linear light —
 * averaging gamma-encoded pixels darkens the trail and looks muddy.
 *
 * <p>HUD is not touched: {@code apply()} runs at {@code renderHand} RETURN,
 * before the overlay pass.
 */
public final class MotionBlur {

    /** Amount (0, 10] maps onto this shutter range, in seconds. */
    static final float SHUTTER_MIN_SEC = 0.010f;
    static final float SHUTTER_MAX_SEC = 0.090f;
    static final float DT_MIN = 1f / 300f;
    static final float DT_MAX = 1f / 20f;
    static final float AMOUNT_MAX = 10f;
    /**
     * Intra-frame streak cap at screen center, in UV. The shader scales this up
     * to {@code EDGE_GAIN_CLAMP}x toward the corners.
     */
    static final float STREAK_UV_MAX = 0.06f;
    /** Time constant for turn-rate smoothing — kills per-frame mouse jitter. */
    static final float RATE_TAU_SEC = 0.025f;
    /** Must match MAX_TAPS in motion_blur.frag. */
    static final int MAX_TAPS = 48;
    static final int MIN_TAPS = 2;
    /** Screen pixels per tap; above this the streak bands visibly. */
    static final float PIXELS_PER_TAP = 2.0f;
    /** Must match EDGE_GAIN_CLAMP in motion_blur.frag. */
    static final float EDGE_GAIN_CLAMP = 3f;

    private static final ResourceLocation VERT =
            new ResourceLocation(GnuClientMod.MOD_ID, "shaders/ui_blur.vert");
    private static final ResourceLocation FRAG =
            new ResourceLocation(GnuClientMod.MOD_ID, "shaders/motion_blur.frag");

    private static final IntBuffer VP_BUF = BufferUtils.createIntBuffer(16);
    private static final float[] UV = new float[2];

    private static int curTex = -1;
    private static int accumTex = -1;
    private static int texW;
    private static int texH;
    private static int program;
    private static int uniCur = -1;
    private static int uniAccum = -1;
    private static int uniStreakVec = -1;
    private static int uniTanHalfFov = -1;
    private static int uniSamples = -1;
    private static int uniHistoryWeight = -1;
    private static boolean programReady;
    private static boolean accumValid;
    private static boolean haveAngles;
    private static float prevYaw;
    private static float prevPitch;
    private static float yawRate;
    private static float pitchRate;
    private static long lastNs;
    private static boolean sessionFailed;
    private static boolean failLogged;

    private MotionBlur() {
    }

    /** Shutter time in seconds for the Amount slider. */
    public static float shutterSec(float amount) {
        if (amount <= 0f) {
            return 0f;
        }
        float t = amount >= AMOUNT_MAX ? 1f : amount / AMOUNT_MAX;
        return SHUTTER_MIN_SEC + t * (SHUTTER_MAX_SEC - SHUTTER_MIN_SEC);
    }

    static float clampDt(float dt) {
        if (dt < DT_MIN) {
            return DT_MIN;
        }
        return dt > DT_MAX ? DT_MAX : dt;
    }

    /**
     * Weight given to the accumulation buffer this frame. Derived from
     * {@code exp(-dt / shutter)} so the trail decays over the same wall-clock
     * time at any frame rate: 240 FPS blends more frames than 60 FPS to reach
     * the identical result. Always below 1 because {@code dt} is floored at
     * {@link #DT_MIN}, so the trail can never become permanent.
     */
    public static float historyWeight(float amount, float dt) {
        float shutter = shutterSec(amount);
        if (shutter <= 0f || dt <= 0f) {
            return 0f;
        }
        float w = (float) Math.exp(-clampDt(dt) / shutter);
        return w < 0f ? 0f : w;
    }

    /**
     * Seconds of camera sweep the intra-frame streak must cover. The shutter is
     * open for at most one frame interval; anything longer is the accumulation
     * buffer's job.
     */
    public static float streakSec(float amount, float dt) {
        float shutter = shutterSec(amount);
        if (shutter <= 0f || dt <= 0f) {
            return 0f;
        }
        float clamped = clampDt(dt);
        return shutter < clamped ? shutter : clamped;
    }

    /**
     * Intra-frame streak vector in UV, from angular velocity in degrees/sec.
     * The shader walks backwards along it.
     */
    public static void streakUv(float yawPerSec, float pitchPerSec, float dt, float amount,
            float fov, float aspect, float[] out) {
        out[0] = 0f;
        out[1] = 0f;
        if (fov < 1f || aspect < 0.01f) {
            return;
        }
        float span = streakSec(amount, dt);
        if (span <= 0f) {
            return;
        }
        float hFov = horizontalFov(fov, aspect);
        if (hFov < 1f) {
            return;
        }
        float uvX = -(yawPerSec * span) / hFov;
        float uvY = (pitchPerSec * span) / fov;
        float len = (float) Math.sqrt(uvX * uvX + uvY * uvY);
        if (len > STREAK_UV_MAX && len > 0f) {
            float s = STREAK_UV_MAX / len;
            uvX *= s;
            uvY *= s;
        }
        out[0] = uvX;
        out[1] = uvY;
    }

    /**
     * Exponential smoothing of the angular rate. Raw frame-to-frame deltas are
     * noisy enough that the streak direction flickers; this is what makes the
     * blur read as camera motion instead of shimmer.
     */
    public static float smoothRate(float prev, float sample, float dt) {
        if (dt <= 0f) {
            return sample;
        }
        float a = 1f - (float) Math.exp(-dt / RATE_TAU_SEC);
        if (a < 0f) {
            a = 0f;
        } else if (a > 1f) {
            a = 1f;
        }
        return prev + (sample - prev) * a;
    }

    /**
     * Tap count for a streak of this UV length. Sized against the longest streak
     * on screen (the corner, where the shader's edge gain peaks) so corners do
     * not band while the center still gets cheap short blurs.
     */
    public static int tapCount(float uvX, float uvY, int width, int height) {
        float px = uvX * width * EDGE_GAIN_CLAMP;
        float py = uvY * height * EDGE_GAIN_CLAMP;
        float len = (float) Math.sqrt(px * px + py * py);
        int taps = (int) Math.ceil(len / PIXELS_PER_TAP);
        if (taps < MIN_TAPS) {
            taps = MIN_TAPS;
        } else if (taps > MAX_TAPS) {
            taps = MAX_TAPS;
        }
        return taps;
    }

    static float horizontalFov(float vFov, float aspect) {
        double v = Math.toRadians(vFov);
        double h = 2.0 * Math.atan(Math.tan(v * 0.5) * aspect);
        return (float) Math.toDegrees(h);
    }

    static float wrapDegrees(float value) {
        float f = value % 360f;
        if (f >= 180f) {
            f -= 360f;
        }
        if (f < -180f) {
            f += 360f;
        }
        return f;
    }

    /** Drop camera and accumulation history so the next frame passes through. */
    public static void reset() {
        haveAngles = false;
        accumValid = false;
        lastNs = 0L;
        yawRate = 0f;
        pitchRate = 0f;
    }

    /** Free the capture textures. */
    public static void dispose() {
        reset();
        deleteTargets();
    }

    /** Called at {@code renderHand} RETURN after shader composite. */
    public static void apply() {
        if (sessionFailed) {
            return;
        }
        MotionBlurModule module = MotionBlurModule.instance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return;
        }
        int dw = mc.displayWidth;
        int dh = mc.displayHeight;
        if (dw <= 0 || dh <= 0) {
            return;
        }
        float amount = module.getAmount();
        if (amount <= 0f || mc.currentScreen != null) {
            reset();
            return;
        }
        try {
            if (!ensureProgram(mc)) {
                return;
            }
            ensureTargets(dw, dh);
            long now = System.nanoTime();
            float dt = lastNs == 0L ? 0f : (now - lastNs) * 1e-9f;
            lastNs = now;

            Entity view = mc.getRenderViewEntity();
            if (view == null) {
                view = mc.thePlayer;
            }
            float yaw = view != null ? FreeLookHook.redirectYaw(view) : 0f;
            float pitch = view != null ? FreeLookHook.redirectPitch(view) : 0f;

            int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                copyScreenTo(curTex, dw, dh);
                if (!accumValid || !haveAngles || dt <= 0f) {
                    copyScreenTo(accumTex, dw, dh);
                    accumValid = true;
                } else {
                    float clamped = clampDt(dt);
                    yawRate = smoothRate(yawRate, wrapDegrees(yaw - prevYaw) / clamped, clamped);
                    pitchRate = smoothRate(pitchRate, (pitch - prevPitch) / clamped, clamped);

                    float fov = mc.gameSettings.fovSetting;
                    if (fov < 1f) {
                        fov = 70f;
                    }
                    float aspect = dw / (float) dh;
                    streakUv(yawRate, pitchRate, dt, amount, fov, aspect, UV);
                    float tanHalfV = (float) Math.tan(Math.toRadians(fov) * 0.5);
                    draw(dw, dh, UV[0], UV[1], tanHalfV * aspect, tanHalfV,
                            tapCount(UV[0], UV[1], dw, dh), historyWeight(amount, dt));
                    copyScreenTo(accumTex, dw, dh);
                }
            } finally {
                GlStateManager.bindTexture(prevTex);
            }
            prevYaw = yaw;
            prevPitch = pitch;
            haveAngles = view != null;
        } catch (Throwable t) {
            failSession(t);
        }
    }

    private static boolean ensureProgram(Minecraft mc) {
        if (programReady && program != 0) {
            return true;
        }
        if (mc.getResourceManager() == null) {
            return false;
        }
        String vertSrc = readResource(VERT);
        String fragSrc = readResource(FRAG);
        if (vertSrc == null || fragSrc == null) {
            failSession(new IllegalStateException("motion blur shader missing"));
            return false;
        }
        int linked = linkProgram(vertSrc, fragSrc);
        if (linked == 0) {
            failSession(new IllegalStateException("motion blur shader link failed"));
            return false;
        }
        program = linked;
        uniCur = GL20.glGetUniformLocation(program, "CurSampler");
        uniAccum = GL20.glGetUniformLocation(program, "AccumSampler");
        uniStreakVec = GL20.glGetUniformLocation(program, "StreakVec");
        uniTanHalfFov = GL20.glGetUniformLocation(program, "TanHalfFov");
        uniSamples = GL20.glGetUniformLocation(program, "Samples");
        uniHistoryWeight = GL20.glGetUniformLocation(program, "HistoryWeight");
        programReady = true;
        return true;
    }

    private static void ensureTargets(int w, int h) {
        if (curTex > 0 && accumTex > 0 && texW == w && texH == h) {
            return;
        }
        deleteTargets();
        texW = w;
        texH = h;
        curTex = allocTex(w, h);
        accumTex = allocTex(w, h);
        accumValid = false;
    }

    private static int allocTex(int w, int h) {
        int id = TextureUtil.glGenTextures();
        GlStateManager.bindTexture(id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return id;
    }

    private static void copyScreenTo(int tex, int w, int h) {
        GlStateManager.bindTexture(tex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
    }

    private static void deleteTargets() {
        if (curTex > 0) {
            TextureUtil.deleteTexture(curTex);
            curTex = -1;
        }
        if (accumTex > 0) {
            TextureUtil.deleteTexture(accumTex);
            accumTex = -1;
        }
        texW = 0;
        texH = 0;
        accumValid = false;
    }

    private static void draw(int w, int h, float streakX, float streakY,
            float tanHalfH, float tanHalfV, int taps, float historyWeight) {
        VP_BUF.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VP_BUF);
        int vpX = VP_BUF.get(0);
        int vpY = VP_BUF.get(1);
        int vpW = Math.max(1, VP_BUF.get(2));
        int vpH = Math.max(1, VP_BUF.get(3));
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean tex2d = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int prevSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        int prevDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
        int prevProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        // GlStateManager tracks bindings per active unit, so switching units must
        // go through GlStateManager too or its cache desyncs from GL.
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int prevLightmapTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        boolean pushed = false;
        try {
            GL11.glViewport(0, 0, w, h);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            pushed = true;

            GL20.glUseProgram(program);
            GL20.glUniform1i(uniCur, 0);
            GL20.glUniform1i(uniAccum, 1);
            GL20.glUniform2f(uniStreakVec, streakX, streakY);
            GL20.glUniform2f(uniTanHalfFov, tanHalfH, tanHalfV);
            GL20.glUniform1f(uniSamples, taps);
            GL20.glUniform1f(uniHistoryWeight, historyWeight);

            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.bindTexture(accumTex);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(curTex);

            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0f, 0f);
            GL11.glVertex2f(0f, 0f);
            GL11.glTexCoord2f(1f, 0f);
            GL11.glVertex2f(1f, 0f);
            GL11.glTexCoord2f(1f, 1f);
            GL11.glVertex2f(1f, 1f);
            GL11.glTexCoord2f(0f, 1f);
            GL11.glVertex2f(0f, 1f);
            GL11.glEnd();
        } finally {
            GL20.glUseProgram(prevProg);
            if (pushed) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(matrixMode);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glDepthMask(depthMask);
            setEnabled(GL11.GL_DEPTH_TEST, depth);
            setEnabled(GL11.GL_BLEND, blend);
            GL11.glBlendFunc(prevSrc, prevDst);
            setEnabled(GL11.GL_TEXTURE_2D, tex2d);
            setEnabled(GL11.GL_LIGHTING, lighting);
            setEnabled(GL11.GL_FOG, fog);
            setEnabled(GL11.GL_CULL_FACE, cull);
            setEnabled(GL11.GL_ALPHA_TEST, alphaTest);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.bindTexture(prevLightmapTex);
            if (lightmap) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glViewport(vpX, vpY, vpW, vpH);
        }
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    private static int linkProgram(String vertSrc, String fragSrc) {
        int vert = compileShader(vertSrc, GL20.GL_VERTEX_SHADER);
        int frag = compileShader(fragSrc, GL20.GL_FRAGMENT_SHADER);
        if (vert == 0 || frag == 0) {
            if (vert != 0) {
                GL20.glDeleteShader(vert);
            }
            if (frag != 0) {
                GL20.glDeleteShader(frag);
            }
            return 0;
        }
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vert);
        GL20.glAttachShader(prog, frag);
        GL20.glLinkProgram(prog);
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) {
            GnuLog.log("MotionBlur link log: " + GL20.glGetProgramInfoLog(prog, 1024));
            GL20.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

    private static int compileShader(String source, int type) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
            GnuLog.log("MotionBlur shader compile: " + GL20.glGetShaderInfoLog(id, 1024));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }

    private static String readResource(ResourceLocation location) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourceManager() == null) {
            return null;
        }
        try {
            InputStream in = mc.getResourceManager().getResource(location).getInputStream();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            } finally {
                in.close();
            }
        } catch (Exception e) {
            GnuLog.logError("MotionBlur resource read failed " + location, e);
            return null;
        }
    }

    private static void failSession(Throwable t) {
        sessionFailed = true;
        dispose();
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        programReady = false;
        if (!failLogged) {
            failLogged = true;
            GnuLog.logError("MotionBlur disabled for session", t);
        }
    }
}
