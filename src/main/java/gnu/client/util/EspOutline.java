package gnu.client.util;

import gnu.client.mixin.impl.accessors.IAccessorRendererLivingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Batched silhouette highlight (spectral-arrow style).
 *
 * <p>Mask is stamped during living Post with the skinMap slim/default RenderPlayer
 * (Alex arms). Flush builds a thin rim = dilate(mask) − mask — same idea as
 * vanilla {@code entity_sobel} (1–2px edge), then composites once solid.
 */
public final class EspOutline {

    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;

    /** 1px ring — matches vanilla entity_sobel neighbor samples (spectral glowing). */
    private static final float[][] DILATE = {
            { 1f, 0f }, { -1f, 0f }, { 0f, 1f }, { 0f, -1f },
            { 1f, 1f }, { 1f, -1f }, { -1f, 1f }, { -1f, -1f }
    };

    private static final ColorPass MASK = new ColorPass();
    private static final ColorPass RIM = new ColorPass();
    private static final IntBuffer VP_BUF = BufferUtils.createIntBuffer(16);

    private static boolean maskActive;
    private static boolean flushing;
    private static boolean stamping;
    private static int maskCount;
    private static float colorR = 1f, colorG = 1f, colorB = 1f;

    private EspOutline() {}

    public static boolean isBusy() {
        return flushing || stamping;
    }

    public static boolean isFlushing() {
        return flushing;
    }

    public static void renderModelOutline(
            RendererLivingEntity renderer,
            EntityLivingBase entity,
            double x, double y, double z,
            float yaw, float partialTicks,
            float r, float g, float b) {
        if (flushing || stamping || renderer == null || entity == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        colorR = r;
        colorG = g;
        colorB = b;

        VP_BUF.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VP_BUF);
        int vpX = VP_BUF.get(0);
        int vpY = VP_BUF.get(1);
        int vpW = Math.max(1, VP_BUF.get(2));
        int vpH = Math.max(1, VP_BUF.get(3));
        int prevFbo = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);

        try {
            MASK.ensure(vpW, vpH);
        } catch (Throwable t) {
            return;
        }

        if (!maskActive) {
            MASK.clear();
            maskActive = true;
            maskCount = 0;
        }

        renderer = resolvePlayerRenderer(renderer, entity);

        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, MASK.fbo);
        GL11.glViewport(0, 0, MASK.width, MASK.height);
        GL11.glColorMask(true, true, true, true);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);

        List<LayerRenderer<?>> layers = null;
        List<LayerRenderer<?>> layerBackup = null;
        if (renderer instanceof IAccessorRendererLivingEntity) {
            layers = ((IAccessorRendererLivingEntity) (Object) renderer).getLayerRenderers();
            if (layers != null && !layers.isEmpty()) {
                layerBackup = new ArrayList<LayerRenderer<?>>(layers);
                layers.clear();
            }
        }

        // Hurt flash uses setBrightness and will blacken the mask / leak texenv into the world.
        int prevHurt = entity.hurtTime;
        int prevHurtRes = entity.hurtResistantTime;
        entity.hurtTime = 0;
        entity.hurtResistantTime = 0;

        try {
            stamping = true;
            // Re-bind in case OptiFine/doRender flips back to the game FBO mid-call.
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, MASK.fbo);
            GL11.glViewport(0, 0, MASK.width, MASK.height);
            forceFlatColorEnv();
            GlStateManager.color(1f, 1f, 1f, 1f);
            @SuppressWarnings({ "unchecked", "rawtypes" })
            RendererLivingEntity raw = (RendererLivingEntity) renderer;
            raw.doRender(entity, x, y, z, yaw, partialTicks);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, MASK.fbo);
            maskCount++;
        } finally {
            stamping = false;
            entity.hurtTime = prevHurt;
            entity.hurtResistantTime = prevHurtRes;
            if (layers != null && layerBackup != null) {
                layers.addAll(layerBackup);
            }
            try {
                GL11.glPopAttrib();
            } catch (Throwable ignored) {
                // attrib stack underflow if doRender unbalanced push/pop — fall through to manual restore
            }
            restoreWorldRenderState();
            restoreMain(mc, prevFbo, vpX, vpY, vpW, vpH);
        }
    }

    /** Undo flat-color / lightmap damage so the real player never stays black after a hit. */
    private static void restoreWorldRenderState() {
        restoreFlatColorEnv();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableLighting();
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
    }

    public static void flush() {
        if (!maskActive || maskCount <= 0 || flushing) {
            clearQueue();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            clearQueue();
            return;
        }

        int w = MASK.width;
        int h = MASK.height;

        int prevFbo = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
        VP_BUF.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VP_BUF);
        int vpX = VP_BUF.get(0);
        int vpY = VP_BUF.get(1);
        int vpW = Math.max(1, VP_BUF.get(2));
        int vpH = Math.max(1, VP_BUF.get(3));

        try {
            RIM.ensure(w, h);
        } catch (Throwable t) {
            clearQueue();
            return;
        }

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean tex = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int prevSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        int prevDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        flushing = true;
        try {
            // Rim = dilate(mask) − mask (simple path that actually composites).
            RIM.clear();
            pushOrtho(w, h);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            restoreModulate();
            GlStateManager.bindTexture(MASK.tex);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (float[] d : DILATE) {
                drawTexturedRect(d[0], d[1], w, h);
            }
            GL11.glBlendFunc(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawTexturedRect(0f, 0f, w, h);
            popOrtho();

            restoreMain(mc, prevFbo, vpX, vpY, vpW, vpH);
            pushOrtho(vpW, vpH);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            // Solid tint like team-colored glowing outline (no soft double-pass bloom).
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            restoreModulate();
            GlStateManager.bindTexture(RIM.tex);
            GlStateManager.color(colorR, colorG, colorB, 1.0f);
            drawTexturedRect(0f, 0f, vpW, vpH);
            popOrtho();
        } catch (Throwable t) {
            restoreMain(mc, prevFbo, vpX, vpY, vpW, vpH);
        } finally {
            clearQueue();
            flushing = false;
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glDepthMask(depthMask);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (tex) GL11.glEnable(GL11.GL_TEXTURE_2D); else GL11.glDisable(GL11.GL_TEXTURE_2D);
            if (blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            GL11.glBlendFunc(prevSrc, prevDst);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.enableTexture2D();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            restoreMain(mc, prevFbo, vpX, vpY, vpW, vpH);
        }
    }

    public static void clearQueue() {
        maskActive = false;
        maskCount = 0;
    }

    private static RendererLivingEntity resolvePlayerRenderer(
            RendererLivingEntity fallback, EntityLivingBase entity) {
        if (!(entity instanceof AbstractClientPlayer)) {
            return fallback;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getRenderManager() == null) {
            return fallback;
        }
        Map<String, RenderPlayer> skinMap = mc.getRenderManager().getSkinMap();
        if (skinMap == null || skinMap.isEmpty()) {
            return fallback;
        }
        String type = ((AbstractClientPlayer) entity).getSkinType();
        RenderPlayer matched = type != null ? skinMap.get(type) : null;
        if (matched == null && type != null && type.toLowerCase().contains("slim")) {
            matched = skinMap.get("slim");
        }
        return matched != null ? matched : fallback;
    }

    private static void pushOrtho(int w, int h) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, w, 0.0, h, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
    }

    private static void popOrtho() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    private static void restoreMain(Minecraft mc, int prevFbo, int x, int y, int w, int h) {
        if (mc.getFramebuffer() != null && OpenGlHelper.isFramebufferEnabled()) {
            mc.getFramebuffer().bindFramebuffer(false);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFbo);
        }
        GL11.glViewport(x, y, w, h);
    }

    private static void drawTexturedRect(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private static void forceFlatColorEnv() {
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
    }

    private static void restoreFlatColorEnv() {
        restoreModulate();
    }

    private static void restoreModulate() {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
    }

    private static final class ColorPass {
        int fbo = -1;
        int tex = -1;
        int width;
        int height;

        void ensure(int w, int h) {
            if (fbo > 0 && tex > 0 && width == w && height == h) {
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
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
            int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
                delete();
                throw new IllegalStateException("ESP FBO incomplete: " + status);
            }
        }

        void clear() {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
            GL11.glViewport(0, 0, width, height);
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
}
