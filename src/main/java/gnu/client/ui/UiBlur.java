package gnu.client.ui;

import gnu.client.GnuClientMod;
import gnu.client.common.GnuLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Optional Dual Kawase blur for ClickGUI, menus, and HUD backdrops.
 * Starts at half-res, then down/up-samples through a small FBO pyramid.
 * Permanent session fallback on probe/runtime failure.
 * <p>
 * Allocates private FBOs via {@link OpenGlHelper} directly so blur still works when
 * video-settings "Use FBOs" is off (Minecraft's {@code Framebuffer} class stubs in
 * that mode and leaves {@code framebufferObject == -1}).
 */
public final class UiBlur {

    private static final ResourceLocation VERT =
            new ResourceLocation(GnuClientMod.MOD_ID, "shaders/ui_blur.vert");
    private static final ResourceLocation FRAG_DOWN =
            new ResourceLocation(GnuClientMod.MOD_ID, "shaders/ui_kawase_down.frag");
    private static final ResourceLocation FRAG_UP =
            new ResourceLocation(GnuClientMod.MOD_ID, "shaders/ui_kawase_up.frag");
    private static final ResourceLocation FRAG_ROUND =
            new ResourceLocation(GnuClientMod.MOD_ID, "shaders/ui_blur_round.frag");
    /** Half-res plus two further downs (1/4, 1/8 of the window). */
    private static final int KAWASE_LEVELS = 3;
    private static final float KAWASE_OFFSET = 2.5f;

    private static boolean probed;
    private static boolean supported;
    private static boolean sessionFailed;
    private static boolean failLogged;
    private static boolean enabledSetting;

    private static int programDown;
    private static int programUp;
    private static int programRound;
    private static int downDiffuse;
    private static int downHalfPixel;
    private static int upDiffuse;
    private static int upHalfPixel;
    private static int roundDiffuse;
    private static int roundSize;
    private static int roundRadius;
    private static int roundAlpha;
    private static int roundUv0;
    private static int roundUv1;

    private static BlurPass[] levels;
    /** Full-res scratch for window capture when game FBOs are off. */
    private static BlurPass windowScratch;
    private static boolean frameActive;
    private static boolean captured;

    private UiBlur() {
    }

    /** User setting; blur still requires successful probe. Default off. */
    public static void setEnabled(boolean enabled) {
        boolean rising = enabled && !enabledSetting;
        enabledSetting = enabled;
        // Only re-probe on off→on (ClickGUI calls this every frame).
        if (rising && sessionFailed) {
            sessionFailed = false;
            failLogged = false;
            probed = false;
            supported = false;
        }
    }

    public static boolean isEnabled() {
        return enabledSetting;
    }

    public static boolean isUsable() {
        return enabledSetting && !sessionFailed && probeSupport();
    }

    public static boolean probeSupport() {
        if (sessionFailed) {
            return false;
        }
        if (probed) {
            return supported;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Not ready yet — do not lock a permanent failure; retry on next frame.
        if (mc == null || mc.getResourceManager() == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return false;
        }
        try {
            // Hardware must support FBOs. Do NOT require isFramebufferEnabled() — that is
            // the video-settings toggle and stubs Minecraft's Framebuffer class.
            if (!OpenGlHelper.framebufferSupported) {
                probed = true;
                supported = false;
                failSession("GL framebuffer extension unsupported");
                return false;
            }
            if (!compilePrograms()) {
                probed = true;
                supported = false;
                failSession("shader compile failed");
                return false;
            }
            ensureFramebuffers(Math.max(1, mc.displayWidth), Math.max(1, mc.displayHeight));
            if (!levelsValid()) {
                probed = true;
                supported = false;
                failSession("fbo alloc failed");
                return false;
            }
            probed = true;
            supported = true;
            return true;
        } catch (Throwable t) {
            probed = true;
            supported = false;
            failSession(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            GnuLog.logError("UiBlur probe failed", t);
            return false;
        }
    }

    public static void beginFrame(boolean needsBlur) {
        frameActive = false;
        captured = false;
        if (!needsBlur || !isUsable()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        if (OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() == null) {
            return;
        }
        try {
            ensureFramebuffers(Math.max(1, mc.displayWidth), Math.max(1, mc.displayHeight));
            frameActive = true;
        } catch (Throwable t) {
            failSession(t.getMessage());
            GnuLog.logError("UiBlur beginFrame failed", t);
            frameActive = false;
        }
    }

    public static void drawPanel(float x, float y, float w, float h, float radius, float alpha) {
        drawPanel(x, y, w, h, radius, alpha, 1f);
    }

    public static void drawPanel(float x, float y, float w, float h, float radius, float alpha,
            float contentScale) {
        drawBlurredRegion(x, y, w, h, radius, alpha, contentScale, 0.55f, true);
    }

    /**
     * Soft blurred backdrop with almost no solid fill — for ArrayList / glow-under text.
     */
    public static void drawSoftBehind(float x, float y, float w, float h, float radius, float alpha) {
        drawSoftBehind(x, y, w, h, radius, alpha, 1f);
    }

    public static void drawSoftBehind(float x, float y, float w, float h, float radius, float alpha,
            float contentScale) {
        // Light wash only; callers add glow themselves. No opaque pill fallback.
        drawBlurredRegion(x, y, w, h, radius, alpha, contentScale, 0f, false);
    }

    /**
     * Frosted-glass pill: Kawase blur plus a light surface wash. Used by ArrayList
     * rows so labels stay readable without a fully opaque backdrop.
     */
    public static void drawFrostedBehind(float x, float y, float w, float h, float radius, float alpha) {
        drawFrostedBehind(x, y, w, h, radius, alpha, 1f);
    }

    public static void drawFrostedBehind(float x, float y, float w, float h, float radius, float alpha,
            float contentScale) {
        drawBlurredRegion(x, y, w, h, radius, alpha, contentScale, 0.42f, true);
    }

    /** Full-screen composite of the current Kawase buffer (no extra tint). */
    public static void drawFullscreen(float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        drawBlurredRegion(0f, 0f, sr.getScaledWidth(), sr.getScaledHeight(), 0f, alpha, 1f, 0f, false);
    }

    private static void drawBlurredRegion(float x, float y, float w, float h, float radius,
            float alpha, float contentScale, float tintStrength, boolean solidFallback) {
        float tint = UiKit.clamp01(tintStrength) * UiKit.clamp01(alpha);
        int color = UiKit.withAlpha(UiKit.SURFACE, tint);
        if (!frameActive || sessionFailed) {
            if (solidFallback) {
                UiKit.drawRoundedPanel(x, y, w, h, radius, UiKit.withAlpha(UiKit.SURFACE, alpha));
            } else if (tint > 0.01f) {
                UiKit.drawRoundedPanel(x, y, w, h, radius, color);
            }
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            if (solidFallback) {
                UiKit.drawRoundedPanel(x, y, w, h, radius, UiKit.withAlpha(UiKit.SURFACE, alpha));
            } else if (tint > 0.01f) {
                UiKit.drawRoundedPanel(x, y, w, h, radius, color);
            }
            return;
        }
        try {
            if (!captured) {
                captureAndBlur(mc);
                captured = true;
            }
            compositePanel(mc, x, y, w, h, radius, alpha, contentScale, tintStrength);
        } catch (Throwable t) {
            failSession(t.getMessage());
            GnuLog.logError("UiBlur draw failed", t);
            restoreMain(mc);
            if (solidFallback) {
                UiKit.drawRoundedPanel(x, y, w, h, radius, UiKit.withAlpha(UiKit.SURFACE, alpha));
            } else if (tint > 0.01f) {
                UiKit.drawRoundedPanel(x, y, w, h, radius, color);
            }
        }
    }

    public static void endFrame() {
        if (!frameActive) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            restoreMain(mc);
        } catch (Throwable t) {
            failSession(t.getMessage());
            GnuLog.logError("UiBlur endFrame failed", t);
        } finally {
            frameActive = false;
            captured = false;
            GL20.glUseProgram(0);
        }
    }

    private static void captureAndBlur(Minecraft mc) {
        BlurPass half = levels[0];
        int halfW = half.width;
        int halfH = half.height;

        // Blur passes bind pyramid viewports; must restore before any UI draw.
        int[] prevViewport = new int[4];
        java.nio.IntBuffer vpBuf = org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, vpBuf);
        prevViewport[0] = vpBuf.get(0);
        prevViewport[1] = vpBuf.get(1);
        prevViewport[2] = vpBuf.get(2);
        prevViewport[3] = vpBuf.get(3);

        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();

        boolean depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWas = GL11.glIsEnabled(GL11.GL_BLEND);

        try {
            GlStateManager.disableBlend();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            if (OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() != null
                    && mc.getFramebuffer().framebufferTexture > 0) {
                half.clear();
                half.bind(true);
                setPassOrtho(halfW, halfH);
                GlStateManager.bindTexture(mc.getFramebuffer().framebufferTexture);
                drawTexturedQuad(0, 0, halfW, halfH, true);
            } else {
                copyWindowColorIntoHalf(mc, halfW, halfH);
            }

            for (int i = 0; i < levels.length - 1; i++) {
                kawaseBlit(levels[i], levels[i + 1], programDown, downDiffuse, downHalfPixel);
            }
            for (int i = levels.length - 2; i >= 0; i--) {
                kawaseBlit(levels[i + 1], levels[i], programUp, upDiffuse, upHalfPixel);
            }
        } finally {
            if (depthWas) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            if (blendWas) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            GL20.glUseProgram(0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            restoreMain(mc);
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        }
    }

    private static void kawaseBlit(BlurPass src, BlurPass dst, int program, int uniDiffuse,
            int uniHalfPixel) {
        dst.clear();
        dst.bind(true);
        setPassOrtho(dst.width, dst.height);
        GL20.glUseProgram(program);
        GL20.glUniform1i(uniDiffuse, 0);
        GL20.glUniform2f(uniHalfPixel,
                KAWASE_OFFSET * 0.5f / Math.max(1, src.width),
                KAWASE_OFFSET * 0.5f / Math.max(1, src.height));
        GlStateManager.bindTexture(src.tex);
        drawTexturedQuad(0, 0, dst.width, dst.height, false);
    }

    private static void setPassOrtho(int w, int h) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, w, 0.0, h, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
    }

    /**
     * When Minecraft is not rendering into an FBO, copy the window into a full-res scratch
     * texture ({@code glCopyTexSubImage2D} — does not reallocate / invalidate FBO attachments),
     * then downsample into the half-res Kawase root.
     */
    private static void copyWindowColorIntoHalf(Minecraft mc, int halfW, int halfH) {
        int dw = Math.max(1, mc.displayWidth);
        int dh = Math.max(1, mc.displayHeight);
        if (windowScratch == null) {
            windowScratch = new BlurPass();
        }
        windowScratch.ensure(dw, dh);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
        GlStateManager.bindTexture(windowScratch.tex);
        // SubImage keeps existing tex storage so pass FBOs stay complete.
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, dw, dh);

        levels[0].clear();
        levels[0].bind(true);
        setPassOrtho(halfW, halfH);
        GlStateManager.bindTexture(windowScratch.tex);
        // Window color is lower-left origin; flip V to match the game-FBO capture path.
        drawTexturedQuad(0, 0, halfW, halfH, true);
    }

    private static void compositePanel(Minecraft mc, float x, float y, float w, float h,
            float radius, float alpha, float contentScale, float tintStrength) {
        // Ensure we draw panels to the game target with a full-size viewport.
        restoreMain(mc);
        ScaledResolution sr = new ScaledResolution(mc);
        float scale = contentScale <= 0f ? 1f : contentScale;
        float sx = x * scale;
        float sy = y * scale;
        float sw = w * scale;
        float sh = h * scale;
        float u0 = sx / sr.getScaledWidth();
        float v0 = 1f - (sy + sh) / sr.getScaledHeight();
        float u1 = (sx + sw) / sr.getScaledWidth();
        float v1 = 1f - sy / sr.getScaledHeight();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, UiKit.clamp01(alpha));
        GlStateManager.bindTexture(levels[0].tex);

        float r = Math.max(0f, Math.min(radius, Math.min(w, h) * 0.5f));
        if (r > 0.25f && programRound != 0) {
            compositeRounded(x, y, w, h, r, alpha, u0, v0, u1, v1);
        } else {
            compositeScissored(mc, x, y, w, h, sr.getScaleFactor() * scale, u0, v0, u1, v1);
        }

        float tint = UiKit.clamp01(alpha) * UiKit.clamp01(tintStrength);
        if (tint > 0.01f) {
            UiKit.RoundedPanel.draw(x, y, w, h, radius, UiKit.withAlpha(UiKit.SURFACE, tint));
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** SDF-clipped blit so blur follows the same rounded corners as the frost wash. */
    private static void compositeRounded(float x, float y, float w, float h, float radius,
            float alpha, float u0, float v0, float u1, float v1) {
        boolean alphaWas = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(levels[0].tex);
        GL20.glUseProgram(programRound);
        GL20.glUniform1i(roundDiffuse, 0);
        GL20.glUniform2f(roundSize, w, h);
        GL20.glUniform1f(roundRadius, radius);
        GL20.glUniform1f(roundAlpha, UiKit.clamp01(alpha));
        // local (0,0) is GUI top-left; blur V is inverted vs GUI Y.
        GL20.glUniform2f(roundUv0, u0, v1);
        GL20.glUniform2f(roundUv1, u1, v0);

        float pad = 1f;
        float x0 = x - pad;
        float y0 = y - pad;
        float x1 = x + w + pad;
        float y1 = y + h + pad;
        float lu0 = -pad / w;
        float lv0 = -pad / h;
        float lu1 = 1f + pad / w;
        float lv1 = 1f + pad / h;

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x0, y1, 0.0).tex(lu0, lv1).endVertex();
        wr.pos(x1, y1, 0.0).tex(lu1, lv1).endVertex();
        wr.pos(x1, y0, 0.0).tex(lu1, lv0).endVertex();
        wr.pos(x0, y0, 0.0).tex(lu0, lv0).endVertex();
        tess.draw();

        GL20.glUseProgram(0);
        if (alphaWas) {
            GlStateManager.enableAlpha();
        }
    }

    private static void compositeScissored(Minecraft mc, float x, float y, float w, float h,
            float fbScale, float u0, float v0, float u1, float v1) {
        UiKit.FbRect scissor = UiKit.PixelAlign.toFramebufferRect(x, y, w, h, fbScale,
                mc.displayWidth, mc.displayHeight);
        boolean scissorWas = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntScissor prev = IntScissor.capture();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissor.x, scissor.y, Math.max(0, scissor.width), Math.max(0, scissor.height));

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();

        if (scissorWas) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(prev.x, prev.y, prev.w, prev.h);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static void restoreMain(Minecraft mc) {
        if (mc == null) {
            return;
        }
        if (OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            GL11.glViewport(0, 0, Math.max(1, mc.displayWidth), Math.max(1, mc.displayHeight));
        }
        GL20.glUseProgram(0);
    }

    private static void ensureFramebuffers(int displayW, int displayH) {
        if (levels == null) {
            levels = new BlurPass[KAWASE_LEVELS];
            for (int i = 0; i < KAWASE_LEVELS; i++) {
                levels[i] = new BlurPass();
            }
        }
        int w = Math.max(1, displayW / 2);
        int h = Math.max(1, displayH / 2);
        for (int i = 0; i < KAWASE_LEVELS; i++) {
            levels[i].ensure(w, h);
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
    }

    private static boolean levelsValid() {
        if (levels == null || levels.length != KAWASE_LEVELS) {
            return false;
        }
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == null || !levels[i].isValid()) {
                return false;
            }
        }
        return true;
    }

    private static boolean compilePrograms() throws Exception {
        if (programDown != 0 && programUp != 0) {
            if (programRound == 0) {
                String vertSrc = readResource(VERT);
                if (vertSrc != null) {
                    compileRoundProgram(vertSrc);
                }
            }
            return true;
        }
        String vertSrc = readResource(VERT);
        String downSrc = readResource(FRAG_DOWN);
        String upSrc = readResource(FRAG_UP);
        if (vertSrc == null || downSrc == null || upSrc == null) {
            return false;
        }
        int down = linkProgram(vertSrc, downSrc);
        if (down == 0) {
            return false;
        }
        int up = linkProgram(vertSrc, upSrc);
        if (up == 0) {
            GL20.glDeleteProgram(down);
            return false;
        }
        programDown = down;
        programUp = up;
        downDiffuse = GL20.glGetUniformLocation(programDown, "DiffuseSampler");
        downHalfPixel = GL20.glGetUniformLocation(programDown, "HalfPixel");
        upDiffuse = GL20.glGetUniformLocation(programUp, "DiffuseSampler");
        upHalfPixel = GL20.glGetUniformLocation(programUp, "HalfPixel");
        compileRoundProgram(vertSrc);
        return true;
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
            GnuLog.log("UiBlur link log: " + GL20.glGetProgramInfoLog(prog, 1024));
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
            GnuLog.log("UiBlur shader compile: " + GL20.glGetShaderInfoLog(id, 1024));
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
            GnuLog.logError("UiBlur resource read failed " + location, e);
            return null;
        }
    }

    private static void drawTexturedQuad(float x, float y, float w, float h, boolean flipV) {
        float v0 = flipV ? 1f : 0f;
        float v1 = flipV ? 0f : 1f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0f, v1);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(1f, v1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(1f, v0);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    private static void failSession(String reason) {
        sessionFailed = true;
        supported = false;
        frameActive = false;
        if (!failLogged) {
            failLogged = true;
            GnuLog.log("UiBlur disabled for session: " + reason);
        }
        disposeFramebuffers();
        if (programDown != 0) {
            GL20.glDeleteProgram(programDown);
            programDown = 0;
        }
        if (programUp != 0) {
            GL20.glDeleteProgram(programUp);
            programUp = 0;
        }
        if (programRound != 0) {
            GL20.glDeleteProgram(programRound);
            programRound = 0;
        }
    }

    /** Optional; scissor blit remains if this fails. */
    private static void compileRoundProgram(String vertSrc) {
        if (programRound != 0) {
            return;
        }
        String roundSrc = readResource(FRAG_ROUND);
        if (roundSrc == null) {
            GnuLog.log("UiBlur rounded composite shader missing; using scissor blit");
            return;
        }
        int prog = linkProgram(vertSrc, roundSrc);
        if (prog == 0) {
            GnuLog.log("UiBlur rounded composite link failed; using scissor blit");
            return;
        }
        programRound = prog;
        roundDiffuse = GL20.glGetUniformLocation(programRound, "DiffuseSampler");
        roundSize = GL20.glGetUniformLocation(programRound, "u_size");
        roundRadius = GL20.glGetUniformLocation(programRound, "u_radius");
        roundAlpha = GL20.glGetUniformLocation(programRound, "u_alpha");
        roundUv0 = GL20.glGetUniformLocation(programRound, "u_uv0");
        roundUv1 = GL20.glGetUniformLocation(programRound, "u_uv1");
    }

    private static void disposeFramebuffers() {
        if (levels != null) {
            for (int i = 0; i < levels.length; i++) {
                if (levels[i] != null) {
                    levels[i].delete();
                    levels[i] = null;
                }
            }
            levels = null;
        }
        if (windowScratch != null) {
            windowScratch.delete();
            windowScratch = null;
        }
    }

    /**
     * Private color-only FBO that ignores {@link OpenGlHelper#isFramebufferEnabled()}.
     */
    private static final class BlurPass {
        int fbo = -1;
        int tex = -1;
        int width;
        int height;

        boolean isValid() {
            return fbo > 0 && tex > 0 && width > 0 && height > 0;
        }

        void ensure(int w, int h) {
            if (isValid() && width == w && height == h) {
                return;
            }
            delete();
            width = w;
            height = h;
            tex = TextureUtil.glGenTextures();
            fbo = OpenGlHelper.glGenFramebuffers();
            GlStateManager.bindTexture(tex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
            int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
                delete();
                throw new IllegalStateException("blur FBO incomplete status=" + status);
            }
        }

        void bind(boolean setViewport) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
            if (setViewport) {
                GL11.glViewport(0, 0, width, height);
            }
        }

        void clear() {
            bind(true);
            GlStateManager.clearColor(0f, 0f, 0f, 0f);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        }

        void delete() {
            if (fbo > 0) {
                OpenGlHelper.glDeleteFramebuffers(fbo);
                fbo = -1;
            }
            if (tex > 0) {
                TextureUtil.deleteTexture(tex);
                tex = -1;
            }
            width = 0;
            height = 0;
        }
    }

    private static final class IntScissor {
        final int x;
        final int y;
        final int w;
        final int h;

        IntScissor(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        static IntScissor capture() {
            java.nio.IntBuffer buf = org.lwjgl.BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, buf);
            return new IntScissor(buf.get(0), buf.get(1), buf.get(2), buf.get(3));
        }
    }
}
