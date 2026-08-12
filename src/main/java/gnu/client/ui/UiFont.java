package gnu.client.ui;

import gnu.client.GnuClientMod;
import gnu.client.common.GnuLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Dual-mode UI font facade: Modern (Inter atlas) or Minecraft. Lazy, never NPE.
 */
public final class UiFont {

    public enum Mode {
        MODERN,
        MINECRAFT
    }

    private static final ResourceLocation FONT_LOCATION =
            new ResourceLocation(GnuClientMod.MOD_ID, "fonts/inter-medium.ttf");
    private static final float SIZE_LABEL = 7.5f;
    private static final float SIZE_UI = 8.5f;
    /** Extra bake resolution for smoother AA when the atlas is filtered linearly. */
    private static final float BAKE_SUPERSAMPLE = 2f;
    private static final int ATLAS_SIZE = 2048;
    private static final int GLYPH_COUNT = 256;

    private static Mode mode = Mode.MODERN;
    private static boolean atlasFailed;
    private static boolean failLogged;
    private static Font baseFont;
    private static Atlas atlas8;
    private static Atlas atlas9;
    private static int bakedScaleFactor = -1;

    private UiFont() {
    }

    public static void setMode(Mode newMode) {
        mode = newMode == null ? Mode.MINECRAFT : newMode;
    }

    public static Mode getMode() {
        return mode;
    }

    /** Bake Inter atlases if needed; {@code true} when modern glyphs are usable. */
    public static boolean ensureModernReady() {
        ensureReady();
        return !atlasFailed && atlas8 != null && atlas9 != null;
    }

    public static float width(String text) {
        return width(text, SIZE_UI);
    }

    public static float width(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        ensureReady();
        FontRenderer fr = mcFont();
        if (useMinecraft()) {
            return fr == null ? 0f : fr.getStringWidth(text);
        }
        Atlas atlas = pickAtlas(size);
        if (atlas == null) {
            return fr == null ? 0f : fr.getStringWidth(text);
        }
        float w = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < GLYPH_COUNT && atlas.glyphs[c] != null) {
                w += atlas.glyphs[c].advance;
            } else {
                w += fr == null ? 0f : fr.getCharWidth(c);
            }
        }
        return w;
    }

    public static float height() {
        return height(SIZE_UI);
    }

    public static float height(float size) {
        ensureReady();
        FontRenderer fr = mcFont();
        if (useMinecraft()) {
            return fr == null ? 9f : fr.FONT_HEIGHT;
        }
        Atlas atlas = pickAtlas(size);
        if (atlas == null) {
            return fr == null ? 9f : fr.FONT_HEIGHT;
        }
        return atlas.lineHeight;
    }

    public static void draw(String text, float x, float y, int argb) {
        draw(text, x, y, SIZE_UI, argb);
    }

    public static void draw(String text, float x, float y, float size, int argb) {
        if (text == null || text.isEmpty() || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        ensureReady();
        float sx = UiKit.PixelAlign.snap(x, currentScale());
        float sy = UiKit.PixelAlign.snap(y, currentScale());
        UiKit.prepareFixedPipeline();
        FontRenderer fr = mcFont();
        if (useMinecraft()) {
            if (fr != null) {
                fr.drawString(text, sx, sy, argb, false);
            }
            return;
        }
        Atlas atlas = pickAtlas(size);
        if (atlas == null || atlas.texture == null) {
            if (fr != null) {
                fr.drawString(text, sx, sy, argb, false);
            }
            return;
        }
        drawModern(text, sx, sy, atlas, argb);
    }

    private static void drawModern(String text, float x, float y, Atlas atlas, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float scale = currentScale();

        UiKit.prepareFixedPipeline();
        int texId = atlas.texture.getGlTextureId();
        UiKit.syncTextureBind(texId);
        applyLinearFilter(atlas.texture);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, g, b, a);

        GL11.glPushMatrix();
        GL11.glScalef(1f / scale, 1f / scale, 1f);
        float px = x * scale;
        float py = y * scale;

        boolean modernBound = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < GLYPH_COUNT && atlas.glyphs[c] != null) {
                if (!modernBound) {
                    UiKit.prepareFixedPipeline();
                    GlStateManager.color(r, g, b, a);
                    UiKit.syncTextureBind(texId);
                    modernBound = true;
                }
                Glyph glyph = atlas.glyphs[c];
                drawGlyph(px, py, glyph, ATLAS_SIZE, scale);
                px += glyph.advance * scale;
            } else {
                GL11.glPopMatrix();
                float logicalX = px / scale;
                float logicalY = py / scale;
                FontRenderer fr = mcFont();
                if (fr != null) {
                    UiKit.prepareFixedPipeline();
                    UiKit.invalidateTextureBind();
                    fr.drawString(String.valueOf(c), logicalX, logicalY, argb, false);
                    px += fr.getCharWidth(c) * scale;
                }
                GL11.glPushMatrix();
                GL11.glScalef(1f / scale, 1f / scale, 1f);
                modernBound = false;
            }
        }
        GL11.glPopMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void drawGlyph(float x, float y, Glyph glyph, float atlasSize, float guiScale) {
        float u0 = glyph.u / atlasSize;
        float v0 = glyph.v / atlasSize;
        float u1 = (glyph.u + glyph.w) / atlasSize;
        float v1 = (glyph.v + glyph.h) / atlasSize;
        float x2 = x + glyph.drawW * guiScale;
        float y2 = y + glyph.drawH * guiScale;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y2);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x2, y2);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x2, y);
        GL11.glEnd();
    }

    private static void ensureReady() {
        if (atlasFailed || mode == Mode.MINECRAFT) {
            return;
        }
        int scale = Math.max(1, currentScaleInt());
        if (atlas8 != null && atlas9 != null && bakedScaleFactor == scale) {
            return;
        }
        rebuildAtlases(scale);
    }

    private static void rebuildAtlases(int scaleFactor) {
        deleteAtlases();
        try {
            Font base = loadBaseFont();
            if (base == null) {
                // Not ready yet (MC/resources unavailable) or already permanently failed.
                // Leave atlases null so ensureReady() can retry when ready.
                return;
            }
            atlas8 = bake(base, SIZE_LABEL * scaleFactor * BAKE_SUPERSAMPLE, SIZE_LABEL);
            atlas9 = bake(base, SIZE_UI * scaleFactor * BAKE_SUPERSAMPLE, SIZE_UI);
            bakedScaleFactor = scaleFactor;
        } catch (Throwable t) {
            deleteAtlases();
            failModern(t.getMessage());
            GnuLog.logError("UiFont atlas bake failed", t);
        }
    }

    private static Font loadBaseFont() {
        if (baseFont != null) {
            return baseFont;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourceManager() == null) {
            // Early call before client/resources exist — retry later, do not fail permanently.
            return null;
        }
        try {
            InputStream in = mc.getResourceManager().getResource(FONT_LOCATION).getInputStream();
            try {
                baseFont = Font.createFont(Font.TRUETYPE_FONT, in);
            } finally {
                in.close();
            }
            return baseFont;
        } catch (Exception e) {
            failModern(e.getMessage());
            GnuLog.logError("UiFont load failed", e);
            return null;
        }
    }

    private static Atlas bake(Font base, float pixelSize, float logicalSize) {
        Font font = base.deriveFont(Font.PLAIN, pixelSize);
        Glyph[] glyphs = new Glyph[GLYPH_COUNT];
        BufferedImage image = new BufferedImage(ATLAS_SIZE, ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setFont(font);
        g.setColor(new Color(255, 255, 255, 0));
        g.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE);
        g.setColor(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        FontMetrics fm = g.getFontMetrics();
        int pad = 3;
        int x = pad;
        int y = pad;
        int rowH = 0;
        float invBake = 1f / (Math.max(1, currentScale()) * BAKE_SUPERSAMPLE);
        float lineHeight = fm.getHeight() * invBake;

        for (int i = 0; i < GLYPH_COUNT; i++) {
            char c = (char) i;
            if (Character.isISOControl(c) && c != ' ') {
                continue;
            }
            Rectangle2D bounds = fm.getStringBounds(String.valueOf(c), g);
            int gw = Math.max(1, (int) Math.ceil(bounds.getWidth()) + pad * 2);
            int gh = Math.max(1, (int) Math.ceil(bounds.getHeight()) + pad);
            if (x + gw >= ATLAS_SIZE) {
                x = pad;
                y += rowH + pad;
                rowH = 0;
            }
            if (y + gh >= ATLAS_SIZE) {
                break;
            }
            g.drawString(String.valueOf(c), x + pad, y + fm.getAscent());
            Glyph glyph = new Glyph();
            glyph.u = x;
            glyph.v = y;
            glyph.w = gw;
            glyph.h = gh;
            // Draw size in GUI-logical pixels (atlas is supersampled).
            glyph.drawW = gw * invBake;
            glyph.drawH = gh * invBake;
            glyph.advance = (float) bounds.getWidth() * invBake;
            glyphs[i] = glyph;
            x += gw + pad;
            if (gh > rowH) {
                rowH = gh;
            }
        }
        g.dispose();

        Atlas atlas = new Atlas();
        atlas.glyphs = glyphs;
        atlas.lineHeight = lineHeight;
        atlas.logicalSize = logicalSize;
        atlas.texture = new DynamicTexture(image);
        applyLinearFilter(atlas.texture);
        return atlas;
    }

    /** Minecraft DynamicTexture defaults to NEAREST — that makes Inter look bitmap/pixely. */
    private static void applyLinearFilter(DynamicTexture texture) {
        if (texture == null) {
            return;
        }
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
    }

    private static void deleteAtlases() {
        if (atlas8 != null && atlas8.texture != null) {
            atlas8.texture.deleteGlTexture();
        }
        if (atlas9 != null && atlas9.texture != null) {
            atlas9.texture.deleteGlTexture();
        }
        atlas8 = null;
        atlas9 = null;
        bakedScaleFactor = -1;
    }

    private static void failModern(String reason) {
        atlasFailed = true;
        mode = Mode.MINECRAFT;
        if (!failLogged) {
            failLogged = true;
            GnuLog.log("UiFont falling back to Minecraft mode: " + reason);
        }
    }

    private static boolean useMinecraft() {
        return mode == Mode.MINECRAFT || atlasFailed || atlas8 == null;
    }

    private static Atlas pickAtlas(float size) {
        if (size <= SIZE_LABEL + 0.01f) {
            return atlas8 != null ? atlas8 : atlas9;
        }
        return atlas9 != null ? atlas9 : atlas8;
    }

    private static FontRenderer mcFont() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            return mc.fontRendererObj;
        }
        return null;
    }

    private static float currentScale() {
        return Math.max(1, currentScaleInt());
    }

    private static int currentScaleInt() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.displayWidth <= 0) {
            return 2;
        }
        try {
            return new ScaledResolution(mc).getScaleFactor();
        } catch (Throwable t) {
            return 2;
        }
    }

    private static final class Glyph {
        int u;
        int v;
        int w;
        int h;
        float drawW;
        float drawH;
        float advance;
    }

    private static final class Atlas {
        Glyph[] glyphs;
        float lineHeight;
        float logicalSize;
        DynamicTexture texture;
    }
}
