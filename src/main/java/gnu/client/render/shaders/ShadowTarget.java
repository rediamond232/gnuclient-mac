package gnu.client.render.shaders;

import gnu.client.common.GnuLog;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;

/**
 * Sun-view shadow framebuffer: depth (for {@code shadow2D}) plus optional color
 * ({@code shadowcolor0} / {@code gl_FragData[0]} in {@code shadow.fsh}).
 */
public final class ShadowTarget {

    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;

    private int fbo;
    private int depth;
    private int color;
    private int size;

    public void resize(int resolution) {
        int want = clampRes(resolution);
        if (want == size && fbo != 0) {
            return;
        }
        delete();
        size = want;
        fbo = OpenGlHelper.glGenFramebuffers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);

        color = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, size, size, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);

        depth = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depth);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, size, size, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                OpenGlHelper.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);

        int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
        if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE && status != GL_FRAMEBUFFER_COMPLETE) {
            GnuLog.log("Shaders: shadow FBO incomplete status=" + status + " size=" + size);
            delete();
            return;
        }
        GnuLog.log("Shaders: shadow FBO ready " + size + "x" + size);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public boolean ready() {
        return fbo != 0 && depth != 0;
    }

    public int size() {
        return size;
    }

    public int depthTex() {
        return depth;
    }

    public int colorTex() {
        return color;
    }

    public void bind() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, Math.max(1, size), Math.max(1, size));
    }

    public void clear() {
        bind();
        GL11.glClearColor(1f, 1f, 1f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void delete() {
        if (fbo != 0) {
            OpenGlHelper.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (depth != 0) {
            GL11.glDeleteTextures(depth);
            depth = 0;
        }
        if (color != 0) {
            GL11.glDeleteTextures(color);
            color = 0;
        }
        size = 0;
    }

    static int clampRes(int resolution) {
        int r = resolution;
        if (r < 256) {
            r = 256;
        }
        if (r > 2048) {
            r = 2048;
        }
        int p2 = 256;
        while (p2 * 2 <= r) {
            p2 *= 2;
        }
        return p2;
    }
}
