package gnu.client.render.shaders;

import gnu.client.common.GnuLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Gbuffer framebuffer (colortex0–N + depth). Composite passes ping-pong written
 * color attachments so bloom/godrays can sample the previous image on Apple GL 2.1.
 */
public final class GbufferTarget {

    private static final int GL_MAX_COLOR_ATTACHMENTS = 0x8CDF;
    private static final int GL_MAX_DRAW_BUFFERS = 0x8824;
    private static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    private static final int GL_MAX_TEXTURE_UNITS = 0x84E2;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;

    private static int cachedMaxDraw = -1;
    private static int cachedMaxColor = -1;
    private static int cachedMaxTexImage = -1;
    private static int cachedMaxTexUnits = -1;

    private int fbo;
    private final int[] color = new int[8];
    private final int[] ping = new int[8];
    private final int[] dummyColor = new int[8];
    private int depth;
    private int dummyDepth;
    private int dummyFar;
    private final int[] sceneDepth = new int[3];
    private boolean depthDetached;
    private int colorFilter = GL11.GL_LINEAR;
    private int width;
    private int height;
    private int colorCount;
    private boolean loggedCompositeIncomplete;
    private boolean loggedSnapshotIncomplete;
    private boolean loggedGbufferIncomplete;
    private final int[] compositeTargets = new int[8];
    private int compositeTargetCount;

    public static int maxDrawBuffers() {
        if (cachedMaxDraw < 0) {
            cachedMaxDraw = Math.max(1, Math.min(8, glGet(GL_MAX_DRAW_BUFFERS, 1)));
        }
        return cachedMaxDraw;
    }

    public static int maxColorAttachments() {
        if (cachedMaxColor < 0) {
            cachedMaxColor = Math.max(1, Math.min(8, glGet(GL_MAX_COLOR_ATTACHMENTS, 8)));
        }
        return cachedMaxColor;
    }

    public static int maxTextureImageUnits() {
        if (cachedMaxTexImage < 0) {
            cachedMaxTexImage = Math.max(1, glGet(GL_MAX_TEXTURE_IMAGE_UNITS, 8));
        }
        return cachedMaxTexImage;
    }

    public static int maxTextureUnits() {
        if (cachedMaxTexUnits < 0) {
            cachedMaxTexUnits = Math.max(1, glGet(GL_MAX_TEXTURE_UNITS, 8));
        }
        return cachedMaxTexUnits;
    }

    public void setColorNearest(boolean nearest) {
        colorFilter = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
    }

    /**
     * Packed window-depth must stay NEAREST. Linear filtering on RGBA8 encoding
     * turns land into sky for Chocapic's {@code Depth < 1.0-near/far/far} test.
     */
    public void setSlotFilter(int slot, int filter) {
        if (slot < 0 || slot >= colorCount || color[slot] == 0) {
            return;
        }
        bindUploadUnit(color[slot]);
        try {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        } finally {
            unbindUploadUnit();
        }
    }

    public void resize(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (w == width && h == height && fbo != 0) {
            return;
        }
        delete();
        width = w;
        height = h;
        int want = Math.min(8, Math.max(1, maxColorAttachments()));
        int[] tryCounts = want >= 8 ? new int[] { 8, 4, 1 } : new int[] { want, 1 };
        for (int t = 0; t < tryCounts.length; t++) {
            if (tryCreate(w, h, tryCounts[t])) {
                return;
            }
        }
        GnuLog.log("Shaders: gbuffer FBO failed for all attachment counts");
        bindMc();
    }

    private boolean tryCreate(int w, int h, int count) {
        delete();
        width = w;
        height = h;
        colorCount = count;
        fbo = OpenGlHelper.glGenFramebuffers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
        for (int i = 0; i < count; i++) {
            color[i] = allocColorTex(w, h, colorFilter);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    OpenGlHelper.GL_COLOR_ATTACHMENT0 + i, GL11.GL_TEXTURE_2D, color[i], 0);
        }
        setDrawBuffers(Math.min(count, maxDrawBuffers()));
        depth = allocDepthTex(w, h);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                OpenGlHelper.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
        depthDetached = false;
        int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
        if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE && status != GL_FRAMEBUFFER_COMPLETE) {
            GnuLog.log("Shaders: gbuffer FBO incomplete status=" + status + " attachments=" + count);
            delete();
            return false;
        }
        GnuLog.log("Shaders: gbuffer FBO ready attachments=" + count
                + " drawBuffers=" + maxDrawBuffers() + " " + w + "x" + h);
        GlErrors.check("gbufferCreate");
        bindMc();
        return true;
    }

    public boolean ready() {
        return fbo != 0 && colorCount > 0;
    }

    public int colorCount() {
        return colorCount;
    }

    public void bind() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
    }

    public void clear(boolean[] keepColor) {
        clear(keepColor, -1);
    }

    public void clear(boolean[] keepColor, int farDepthSlot) {
        detachColortexSamplers();
        reattachLatest();
        attachRealDepth();
        GL11.glClearColor(0f, 0f, 0f, 1f);
        if (maxDrawBuffers() == 1) {
            setDrawBuffers(1);
            boolean keep0 = keepColor != null && keepColor.length > 0 && keepColor[0];
            int bits = GL11.GL_DEPTH_BUFFER_BIT;
            if (!keep0) {
                bits |= GL11.GL_COLOR_BUFFER_BIT;
            }
            GL11.glClear(bits);
            clearFarDepth(farDepthSlot);
            GlErrors.check("gbufferClear");
            return;
        }
        boolean clearedAny = false;
        for (int i = 0; i < colorCount; i++) {
            if (keepColor != null && i < keepColor.length && keepColor[i]) {
                continue;
            }
            setDrawBuffersOne(i);
            int bits = GL11.GL_COLOR_BUFFER_BIT;
            if (!clearedAny) {
                bits |= GL11.GL_DEPTH_BUFFER_BIT;
            }
            GL11.glClear(bits);
            clearedAny = true;
        }
        if (!clearedAny) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }
        clearFarDepth(farDepthSlot);
        setDrawBuffers(Math.min(colorCount, maxDrawBuffers()));
        GlErrors.check("gbufferClear");
    }

    private void clearFarDepth(int slot) {
        if (slot < 0 || slot >= colorCount) {
            return;
        }
        GL11.glClearColor(1f, 1f, 1f, 1f);
        setDrawBuffersOne(slot);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glClearColor(0f, 0f, 0f, 1f);
    }

    public int colorTex(int i) {
        if (i < 0 || i >= colorCount) {
            return 0;
        }
        return color[i];
    }

    public int depthTex() {
        return depthTex(0);
    }

    public int depthTex(int which) {
        if (which >= 0 && which < sceneDepth.length && sceneDepth[which] != 0) {
            return sceneDepth[which];
        }
        if (sceneDepth[0] != 0) {
            return sceneDepth[0];
        }
        return 0;
    }

    /**
     * 1×1 opaque white RGBA. Apple GL 2.1 cannot sample {@code DEPTH_COMPONENT} as
     * {@code sampler2D}; far depth unpacks to 1.0 from this color.
     */
    public int dummyFarDepth() {
        if (dummyFar == 0) {
            dummyFar = GL11.glGenTextures();
            bindUploadUnit(dummyFar);
            try {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
                ByteBuffer px = ByteBuffer.allocateDirect(4);
                px.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
                px.flip();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
            } finally {
                unbindUploadUnit();
            }
        }
        return dummyFar;
    }

    public boolean depthIsColor() {
        for (int i = 0; i < sceneDepth.length; i++) {
            if (sceneDepth[i] != 0) {
                return true;
            }
        }
        return false;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * Copy window depth stored in a colortex (written by gbuffer programs) into a
     * detached texture. Composite samples that as {@code depthtex0} — Apple cannot
     * sample a {@code DEPTH_COMPONENT} attachment.
     */
    public void snapshotDepthFromColor(int src) {
        snapshotDepthFromColor(src, 0);
    }

    /**
     * {@code dest} 0 = depthtex0 (full), 1 = opaque, 2 = no-hand. Packed depth is always
     * NEAREST — linear filtering would destroy the 24-bit encoding.
     */
    public void snapshotDepthFromColor(int src, int dest) {
        if (fbo == 0 || src < 0 || src >= colorCount || color[src] == 0 || width <= 0) {
            return;
        }
        if (dest < 0 || dest >= sceneDepth.length) {
            dest = 0;
        }
        detachFromSamplers(color[src]);
        bind();
        if (sceneDepth[dest] == 0) {
            sceneDepth[dest] = allocColorTex(width, height, GL11.GL_NEAREST);
        }
        // Apple GL 2.1 / Metal rejects glReadBuffer(ATTACHMENT n) for n>0 (1282).
        // Copy from ATT0 only — and detach the packed image from every other
        // attachment first. beginGbuffer leaves it on ATT1 for DRAWBUFFERS:031
        // (or ATT1 for :73); the same texture on two attachments makes the FBO
        // incomplete, and the copy then reads albedo. Bright land decodes as
        // far/sky and Chocapic blows out the frame.
        int base = OpenGlHelper.GL_COLOR_ATTACHMENT0;
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                base, GL11.GL_TEXTURE_2D, color[src], 0);
        for (int i = 1; i < colorCount; i++) {
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    base + i, GL11.GL_TEXTURE_2D, ensureDummyColor(i), 0);
        }
        setDrawBuffers(1);
        GL11.glReadBuffer(OpenGlHelper.GL_COLOR_ATTACHMENT0);
        int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
        if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE && status != GL_FRAMEBUFFER_COMPLETE) {
            if (!loggedSnapshotIncomplete) {
                GnuLog.log("Shaders: packed-depth snapshot FBO incomplete status=" + status);
                loggedSnapshotIncomplete = true;
            }
            reattachLatest();
            return;
        }
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneDepth[dest]);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            restoreDefaultTexUnit();
            reattachLatest();
        }
        GlErrors.check("snapshotDepth");
    }

    /**
     * Detach the real depth texture so composite can sample {@code depthtex0}.
     * A same-size dummy stays attached so the FBO remains complete. Never uses
     * {@code glCopyTexSubImage2D} (1282 on Apple when the source is depth).
     */
    public void detachDepthForSampling() {
        if (fbo == 0 || depth == 0 || width <= 0 || height <= 0) {
            return;
        }
        bind();
        if (dummyDepth == 0) {
            dummyDepth = allocDepthTex(width, height);
        }
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                OpenGlHelper.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, dummyDepth, 0);
        depthDetached = true;
        GlErrors.check("detachDepth");
    }

    public void attachRealDepth() {
        if (fbo == 0 || depth == 0) {
            return;
        }
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                OpenGlHelper.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
        depthDetached = false;
    }

    /**
     * Gbuffer MRT on Apple GL 2.1 only writes sequential attachments 0..N-1.
     * Attach the pack's DRAWBUFFERS slots (e.g. {@code 031} or {@code 526}) there
     * so packed window-depth and water aux actually land in those colortex images.
     */
    public void beginGbuffer(int[] drawBuffers, int maxAttachments) {
        detachColortexSamplers();
        bind();
        int base = OpenGlHelper.GL_COLOR_ATTACHMENT0;
        int n = 0;
        if (drawBuffers != null) {
            for (int i = 0; i < drawBuffers.length; i++) {
                int idx = drawBuffers[i] - base;
                if (idx < 0 || idx >= colorCount || idx >= maxAttachments) {
                    continue;
                }
                if (n >= compositeTargets.length) {
                    break;
                }
                compositeTargets[n] = idx;
                n++;
            }
        }
        if (n == 0) {
            compositeTargets[0] = 0;
            n = 1;
        }
        int maxDraw = maxDrawBuffers();
        if (n > maxDraw) {
            int skip = n - maxDraw;
            for (int i = 0; i < maxDraw; i++) {
                compositeTargets[i] = compositeTargets[i + skip];
            }
            n = maxDraw;
        }
        compositeTargetCount = n;
        for (int i = 0; i < colorCount; i++) {
            int tex;
            if (i < n) {
                int idx = compositeTargets[i];
                tex = color[idx] != 0 ? color[idx] : ensureDummyColor(i);
            } else {
                tex = ensureDummyColor(i);
            }
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    base + i, GL11.GL_TEXTURE_2D, tex, 0);
        }
        setDrawBuffers(n);
        int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
        if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE && status != GL_FRAMEBUFFER_COMPLETE) {
            if (!loggedGbufferIncomplete) {
                GnuLog.log("Shaders: gbuffer MRT incomplete status=" + status + " targets=" + n);
                loggedGbufferIncomplete = true;
            }
        }
        GlErrors.check("beginGbuffer");
    }

    /**
     * Draw composite into sequential attachments 0..N so Apple GL 2.1 MRT works,
     * then {@link #endComposite} remaps those outputs onto the pack's colortex slots.
     * Sampled {@link #colorTex} images stay detached (no framebuffer feedback).
     * Unused slots get a dummy texture — never texture 0 (1282 on Apple).
     */
    public void beginComposite(int[] drawBuffers, int maxAttachments) {
        detachColortexSamplers();
        bind();
        int base = OpenGlHelper.GL_COLOR_ATTACHMENT0;
        compositeTargetCount = 0;
        if (drawBuffers != null) {
            for (int i = 0; i < drawBuffers.length; i++) {
                int idx = drawBuffers[i] - base;
                if (idx < 0 || idx >= colorCount || idx >= maxAttachments) {
                    continue;
                }
                compositeTargets[compositeTargetCount] = idx;
                compositeTargetCount++;
            }
        }
        if (compositeTargetCount == 0) {
            compositeTargets[0] = 0;
            compositeTargetCount = 1;
        }
        int maxDraw = maxDrawBuffers();
        if (compositeTargetCount > maxDraw) {
            int skip = compositeTargetCount - maxDraw;
            for (int i = 0; i < maxDraw; i++) {
                compositeTargets[i] = compositeTargets[i + skip];
            }
            compositeTargetCount = maxDraw;
        }
        for (int i = 0; i < colorCount; i++) {
            int tex;
            if (i < compositeTargetCount) {
                ensurePing(i);
                tex = ping[i];
            } else {
                tex = ensureDummyColor(i);
            }
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    base + i, GL11.GL_TEXTURE_2D, tex, 0);
        }
        setDrawBuffers(compositeTargetCount);
        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
        if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE && status != GL_FRAMEBUFFER_COMPLETE) {
            if (!loggedCompositeIncomplete) {
                GnuLog.log("Shaders: composite FBO incomplete status=" + status);
                loggedCompositeIncomplete = true;
            }
        }
        GlErrors.check("beginComposite");
    }

    public void endComposite(int[] drawBuffers, int maxAttachments) {
        for (int i = 0; i < compositeTargetCount; i++) {
            int idx = compositeTargets[i];
            if (idx < 0 || idx >= colorCount) {
                continue;
            }
            int tmp = color[idx];
            color[idx] = ping[i];
            ping[i] = tmp;
        }
        compositeTargetCount = 0;
        reattachLatest();
    }

    public void reattachLatest() {
        if (fbo == 0) {
            return;
        }
        detachColortexSamplers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
        int base = OpenGlHelper.GL_COLOR_ATTACHMENT0;
        for (int i = 0; i < colorCount; i++) {
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    base + i, GL11.GL_TEXTURE_2D, color[i], 0);
        }
        if (!depthDetached && depth != 0) {
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    OpenGlHelper.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
        }
        setDrawBuffers(Math.min(colorCount, maxDrawBuffers()));
        GL11.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
    }

    /**
     * Apple Metal marks an FBO attachment unloadable if that image is still bound
     * to a sampler unit. Unbind our color/depth textures only — never the atlas.
     */
    public void detachColortexSamplers() {
        try {
            for (int u = 15; u >= 0; u--) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + u);
                int bound = textureBinding2D();
                if (!isFramebufferImage(bound)) {
                    continue;
                }
                if (u < 8) {
                    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + u);
                    GlStateManager.bindTexture(0);
                } else {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                }
            }
        } finally {
            restoreDefaultTexUnit();
        }
    }

    private void detachFromSamplers(int tex) {
        if (tex == 0) {
            return;
        }
        try {
            for (int u = 15; u >= 0; u--) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + u);
                if (textureBinding2D() != tex) {
                    continue;
                }
                if (u < 8) {
                    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + u);
                    GlStateManager.bindTexture(0);
                } else {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                }
            }
        } finally {
            restoreDefaultTexUnit();
        }
    }

    private boolean isFramebufferImage(int tex) {
        if (tex == 0) {
            return false;
        }
        for (int i = 0; i < color.length; i++) {
            if (tex == color[i] || tex == ping[i] || tex == dummyColor[i]) {
                return true;
            }
        }
        if (tex == depth || tex == dummyDepth || tex == dummyFar) {
            return true;
        }
        for (int i = 0; i < sceneDepth.length; i++) {
            if (tex == sceneDepth[i]) {
                return true;
            }
        }
        return false;
    }

    private static int textureBinding2D() {
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private static void restoreDefaultTexUnit() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private void setDrawBuffers(int n) {
        n = Math.max(1, Math.min(n, maxDrawBuffers()));
        n = Math.min(n, Math.max(1, colorCount));
        IntBuffer bufs = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        int base = OpenGlHelper.GL_COLOR_ATTACHMENT0;
        for (int i = 0; i < n; i++) {
            bufs.put(base + i);
        }
        bufs.flip();
        GL20.glDrawBuffers(bufs);
    }

    private void setDrawBuffersOne(int index) {
        IntBuffer bufs = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        bufs.put(OpenGlHelper.GL_COLOR_ATTACHMENT0 + index);
        bufs.flip();
        GL20.glDrawBuffers(bufs);
    }

    private void ensurePing(int i) {
        if (ping[i] != 0) {
            return;
        }
        ping[i] = allocColorTex(width, height, colorFilter);
    }

    private int ensureDummyColor(int i) {
        if (dummyColor[i] == 0) {
            dummyColor[i] = allocColorTex(width, height, colorFilter);
        }
        return dummyColor[i];
    }

    private static int allocColorTex(int w, int h, int filter) {
        int id = GL11.glGenTextures();
        bindUploadUnit(id);
        try {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        } finally {
            unbindUploadUnit();
        }
        return id;
    }

    private static int allocDepthTex(int w, int h) {
        int id = GL11.glGenTextures();
        bindUploadUnit(id);
        try {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, w, h, 0,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        } finally {
            unbindUploadUnit();
        }
        return id;
    }

    private static void bindUploadUnit(int id) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    private static void unbindUploadUnit() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        restoreDefaultTexUnit();
    }

    /**
     * Call only while the depth texture is detached from the FBO. Apple GL 2.1
     * defaults depth textures to shadow-compare, so {@code texture2D} returns 0/1
     * and Chocapic treats the whole frame as sky.
     */
    static void prepareDepthSampler(int tex) {
        if (tex == 0) {
            return;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
    }

    public void delete() {
        detachColortexSamplers();
        if (fbo != 0) {
            OpenGlHelper.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        for (int i = 0; i < color.length; i++) {
            if (color[i] != 0) {
                GL11.glDeleteTextures(color[i]);
                color[i] = 0;
            }
            if (ping[i] != 0) {
                GL11.glDeleteTextures(ping[i]);
                ping[i] = 0;
            }
            if (dummyColor[i] != 0) {
                GL11.glDeleteTextures(dummyColor[i]);
                dummyColor[i] = 0;
            }
        }
        if (depth != 0) {
            GL11.glDeleteTextures(depth);
            depth = 0;
        }
        if (dummyDepth != 0) {
            GL11.glDeleteTextures(dummyDepth);
            dummyDepth = 0;
        }
        if (dummyFar != 0) {
            GL11.glDeleteTextures(dummyFar);
            dummyFar = 0;
        }
        for (int i = 0; i < sceneDepth.length; i++) {
            if (sceneDepth[i] != 0) {
                GL11.glDeleteTextures(sceneDepth[i]);
                sceneDepth[i] = 0;
            }
        }
        depthDetached = false;
        width = 0;
        height = 0;
        colorCount = 0;
        compositeTargetCount = 0;
        loggedCompositeIncomplete = false;
        loggedSnapshotIncomplete = false;
        loggedGbufferIncomplete = false;
    }

    public static void bindMc() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            if (mc != null) {
                GL11.glViewport(0, 0, Math.max(1, mc.displayWidth), Math.max(1, mc.displayHeight));
            }
        }
    }

    private static int glGet(int pname, int fallback) {
        try {
            IntBuffer buf = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(pname, buf);
            int v = buf.get(0);
            return v > 0 ? v : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
