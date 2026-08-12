package gnu.client.ui.menu;

import gnu.client.GnuClientMod;
import gnu.client.module.modules.settings.ClickGuiModule;
import gnu.client.ui.ClientTheme;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.GuiModList;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Title screen restyled to match ClickGUI / HUD Lux language:
 * dark surfaces, dual-accent glow, rounded pills, Inter typography.
 */
public class GnuMainMenuScreen extends GuiScreen {

    private static final ResourceLocation LOGO =
            new ResourceLocation(GnuClientMod.MOD_ID, "textures/gui/gnu_logo.png");

    private static final float PANEL_W = 196f;
    private static final float BTN_H = 26f;
    private static final float BTN_GAP = 3f;
    private static final float PANEL_PAD = 10f;
    private static final float PANEL_RADIUS = 10f;
    private static final float BTN_RADIUS = 7f;
    private static final float LOGO_SIZE = 46f;
    private static final float TITLE_SCALE = 2.4f;
    private static final float LABEL_SIZE = 8f;

    private final UiKit.UiClock clock = new UiKit.UiClock();
    private final UiKit.AnimatedFloat openFade = new UiKit.AnimatedFloat(0f);
    private final List<MenuButton> buttons = new ArrayList<MenuButton>();

    private float panelX;
    private float panelY;
    private float panelH;
    private float brandCenterY;
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    private boolean layoutFontReady;

    @Override
    public void initGui() {
        // Match GuiMainMenu: reset debug overlay and wipe chat when entering title.
        if (this.mc != null && this.mc.gameSettings != null) {
            this.mc.gameSettings.showDebugInfo = false;
        }
        if (this.mc != null && this.mc.ingameGUI != null) {
            this.mc.ingameGUI.getChatGUI().clearChatMessages();
        }
        clock.reset();
        openFade.snap(0f);
        openFade.setDurationMs(UiKit.DURATION_SLOW_MS, 1f);
        openFade.setTarget(1f);
        layoutWidth = -1;
        layoutHeight = -1;
        layoutFontReady = false;
        applyVisualSettings();
        UiFont.ensureModernReady();
        ensureLayout();
    }

    @Override
    public void onGuiClosed() {
        // nothing persistent
    }

    private void applyVisualSettings() {
        ClickGuiModule gui = ClickGuiModule.instance();
        float speed = 1f;
        if (gui != null) {
            UiFont.setMode(gui.resolveFontMode());
            speed = gui.getAnimationSpeed();
        }
        clock.setSpeed(speed);
    }

    /** Rebuild button geometry when size changes or Inter atlas becomes ready mid-frame. */
    private void ensureLayout() {
        boolean fontReady = UiFont.ensureModernReady() || UiFont.getMode() == UiFont.Mode.MINECRAFT;
        if (layoutWidth == width && layoutHeight == height && layoutFontReady == fontReady
                && !buttons.isEmpty()) {
            return;
        }
        layoutWidth = width;
        layoutHeight = height;
        layoutFontReady = fontReady;
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttons.clear();
        buttons.add(new MenuButton(Action.SINGLEPLAYER, I18n.format("menu.singleplayer")));
        buttons.add(new MenuButton(Action.MULTIPLAYER, I18n.format("menu.multiplayer")));
        buttons.add(new MenuButton(Action.ALTS, "Alts"));
        buttons.add(new MenuButton(Action.MODS, "Mods"));
        buttons.add(new MenuButton(Action.OPTIONS, I18n.format("menu.options")));
        buttons.add(new MenuButton(Action.QUIT, I18n.format("menu.quit")));

        float contentH = buttons.size() * BTN_H + Math.max(0, buttons.size() - 1) * BTN_GAP;
        panelH = contentH + PANEL_PAD * 2f;

        float brandBlock = LOGO_SIZE + 10f + UiFont.height(LABEL_SIZE) * TITLE_SCALE + 6f
                + UiFont.height(7f) + 18f;
        float totalH = brandBlock + panelH;
        float startY = Math.max(18f, (height - totalH) * 0.42f);

        brandCenterY = startY + LOGO_SIZE * 0.5f;
        panelX = (width - PANEL_W) * 0.5f;
        panelY = startY + brandBlock;

        float by = panelY + PANEL_PAD;
        for (MenuButton button : buttons) {
            button.x = panelX + PANEL_PAD;
            button.y = by;
            button.w = PANEL_W - PANEL_PAD * 2f;
            button.h = BTN_H;
            by += BTN_H + BTN_GAP;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        applyVisualSettings();
        ensureLayout();
        UiKit.prepareFixedPipeline();
        UiKit.invalidateTextureBind();
        clock.tick();
        float dt = clock.dt();
        openFade.update(dt);
        float alpha = UiKit.clamp01(openFade.get());

        for (MenuButton button : buttons) {
            boolean hovered = contains(mouseX, mouseY, button.x, button.y, button.w, button.h);
            button.hover.setTarget(hovered ? 1f : 0f);
            button.hover.update(dt);
        }

        final float fade = alpha;
        final ScaledResolution sr = new ScaledResolution(mc);
        final float scale = sr.getScaleFactor();
        UiKit.GlGuard.run(new Runnable() {
            @Override
            public void run() {
                UiKit.prepareFixedPipeline();
                drawBackdrop(fade);
                drawBrand(fade, scale);
                drawPanel(fade, scale);
                drawFooter(fade, scale);
                UiKit.prepareFixedPipeline();
            }
        });
    }

    private void drawBackdrop(float alpha) {
        // Deep near-black base
        UiKit.drawRoundedPanel(0f, 0f, width, height, 0f, UiKit.withAlpha(0xFF07080E, alpha));

        // Soft dual-accent atmosphere (large blooms, low strength)
        float cx = width * 0.5f;
        float cy = height * 0.38f;
        UiKit.drawSoftBloom(cx - 90f, cy - 70f, 180f, 140f, 70f, ClientTheme.color1(), 0.22f * alpha);
        UiKit.drawSoftBloom(cx - 40f, cy - 20f, 220f, 180f, 90f, ClientTheme.color2(), 0.16f * alpha);
        UiKit.drawSoftBloom(width * 0.15f, height * 0.7f, 160f, 120f, 60f,
                ClientTheme.color1(), 0.10f * alpha);
        UiKit.drawSoftBloom(width * 0.7f, height * 0.65f, 180f, 140f, 70f,
                ClientTheme.color2(), 0.10f * alpha);

        // Subtle top-to-bottom vignette wash
        UiKit.drawVerticalGradient(0f, 0f, width, height * 0.35f,
                UiKit.withAlpha(0xFF000000, 0.35f * alpha),
                UiKit.withAlpha(0x00000000, 0f));
        UiKit.drawVerticalGradient(0f, height * 0.7f, width, height * 0.3f,
                UiKit.withAlpha(0x00000000, 0f),
                UiKit.withAlpha(0xFF000000, 0.45f * alpha));
    }

    private void drawBrand(float alpha, float scale) {
        float logoX = UiKit.PixelAlign.snap((width - LOGO_SIZE) * 0.5f, scale);
        float logoY = UiKit.PixelAlign.snap(brandCenterY - LOGO_SIZE * 0.5f, scale);
        float logoS = UiKit.PixelAlign.snap(LOGO_SIZE, scale);

        UiKit.drawAccentGlow(logoX, logoY, logoS, logoS, logoS * 0.5f, 0.55f * alpha);
        drawLogo(logoX, logoY, logoS, alpha);

        String title = "GNU";
        float titleW = UiFont.width(title, LABEL_SIZE) * TITLE_SCALE;
        float titleX = (width - titleW) * 0.5f;
        float titleY = logoY + logoS + 10f;
        drawScaledGradientText(title, titleX, titleY, LABEL_SIZE, TITLE_SCALE, alpha);

        String tag = "client  ·  " + GnuClientMod.VERSION;
        float tagSize = 7f;
        float tagW = UiFont.width(tag, tagSize);
        float tagY = titleY + UiFont.height(LABEL_SIZE) * TITLE_SCALE + 6f;
        UiFont.draw(tag,
                UiKit.PixelAlign.snap((width - tagW) * 0.5f, scale),
                UiKit.PixelAlign.snap(tagY, scale),
                tagSize,
                UiKit.withAlpha(UiKit.MUTED, alpha * 0.9f));

        // Thin accent underline under brand block
        float lineW = Math.max(titleW, 72f);
        float lineX = (width - lineW) * 0.5f;
        float lineY = tagY + UiFont.height(tagSize) + 10f;
        UiKit.drawHorizontalGradient(lineX, lineY, lineW, 1.5f,
                ClientTheme.withAlpha(ClientTheme.color1(), alpha * 0.85f),
                ClientTheme.withAlpha(ClientTheme.color2(), alpha * 0.85f));
    }

    private void drawPanel(float alpha, float scale) {
        float px = UiKit.PixelAlign.snap(panelX, scale);
        float py = UiKit.PixelAlign.snap(panelY, scale);
        float pw = UiKit.PixelAlign.snap(PANEL_W, scale);
        float ph = UiKit.PixelAlign.snap(panelH, scale);

        UiKit.drawAccentGlow(px, py, pw, ph, PANEL_RADIUS, 0.4f * alpha);
        UiKit.drawRoundedPanel(px + 2f, py + 2f, pw, ph, PANEL_RADIUS,
                UiKit.withAlpha(0x66000000, alpha * 0.35f));
        UiKit.drawRoundedPanel(px, py, pw, ph, PANEL_RADIUS,
                UiKit.withAlpha(UiKit.PANEL, alpha));

        // Header strip with C1→C2 underline (mirrors CategoryColumn)
        float headerH = 3f;
        UiKit.drawRoundedPanel(px, py, pw, 8f, PANEL_RADIUS,
                UiKit.withAlpha(UiKit.PANEL_HEADER, alpha * 0.55f));
        UiKit.drawHorizontalGradient(px + 12f, py + 5f, pw - 24f, headerH,
                ClientTheme.withAlpha(ClientTheme.color1(), alpha),
                ClientTheme.withAlpha(ClientTheme.color2(), alpha));

        for (int i = 0; i < buttons.size(); i++) {
            drawButton(buttons.get(i), i, buttons.size(), alpha, scale);
        }
    }

    private void drawButton(MenuButton button, int index, int total, float alpha, float scale) {
        float hx = UiKit.PixelAlign.snap(button.x, scale);
        float hy = UiKit.PixelAlign.snap(button.y, scale);
        float hw = UiKit.PixelAlign.snap(button.w, scale);
        float hh = UiKit.PixelAlign.snap(button.h, scale);
        float hover = button.hover.get();

        if (hover > 0.02f) {
            UiKit.drawAccentGlow(hx, hy, hw, hh, BTN_RADIUS, 0.55f * alpha * hover);
        }

        float idleA = alpha * (1f - hover * 0.9f);
        if (idleA > 0.02f) {
            int base = hover > 0.01f ? UiKit.ROW_HOVER : UiKit.ROW_IDLE;
            UiKit.drawRoundedPanel(hx, hy, hw, hh, BTN_RADIUS, UiKit.withAlpha(base, idleA));
        }

        if (hover > 0.02f) {
            int top = ClientTheme.withAlpha(ClientTheme.lighten(ClientTheme.color1(), 0.12f), alpha * hover);
            int bot = ClientTheme.withAlpha(ClientTheme.color2(), alpha * hover);
            UiKit.drawVerticalGradient(hx, hy, hw, hh, top, bot);
        }

        // Left accent tick (arraylist language)
        int tick = ClientTheme.withAlpha(ClientTheme.getRowFadeColor(index, total, 180.0), alpha);
        UiKit.drawRoundedPanel(hx + 1f, hy + 5f, 2.5f, hh - 10f, 1.2f, tick);

        float labelY = UiKit.PixelAlign.snap(hy + (hh - UiFont.height(LABEL_SIZE)) * 0.5f, scale);
        int labelColor = UiKit.withAlpha(lerpColor(UiKit.MUTED, UiKit.TEXT, hover), alpha);
        float labelW = UiFont.width(button.label, LABEL_SIZE);
        UiFont.draw(button.label,
                UiKit.PixelAlign.snap(hx + (hw - labelW) * 0.5f, scale),
                labelY,
                LABEL_SIZE,
                labelColor);
    }

    private void drawFooter(float alpha, float scale) {
        String left = "GNU Client";
        String right = "Minecraft 1.8.9";
        float size = 6.5f;
        float y = height - 12f - UiFont.height(size);
        UiFont.draw(left, UiKit.PixelAlign.snap(10f, scale), UiKit.PixelAlign.snap(y, scale),
                size, UiKit.withAlpha(UiKit.MUTED_DIM, alpha));
        float rw = UiFont.width(right, size);
        UiFont.draw(right, UiKit.PixelAlign.snap(width - 10f - rw, scale), UiKit.PixelAlign.snap(y, scale),
                size, UiKit.withAlpha(UiKit.MUTED_DIM, alpha));

        // Small language chip — bottom center, clickable via mouseClicked hit
        String lang = "Language";
        float lw = UiFont.width(lang, size) + 16f;
        float lh = UiFont.height(size) + 8f;
        float lx = (width - lw) * 0.5f;
        float ly = y - 2f;
        languageX = lx;
        languageY = ly;
        languageW = lw;
        languageH = lh;
        boolean langHover = contains(lastMouseX, lastMouseY, lx, ly, lw, lh);
        UiKit.drawRoundedPanel(lx, ly, lw, lh, 6f,
                UiKit.withAlpha(langHover ? UiKit.ROW_HOVER : UiKit.ROW_IDLE, alpha * 0.85f));
        UiFont.draw(lang,
                UiKit.PixelAlign.snap(lx + (lw - UiFont.width(lang, size)) * 0.5f, scale),
                UiKit.PixelAlign.snap(ly + (lh - UiFont.height(size)) * 0.5f, scale),
                size,
                UiKit.withAlpha(langHover ? UiKit.TEXT : UiKit.MUTED, alpha));
    }

    private float languageX;
    private float languageY;
    private float languageW;
    private float languageH;
    private int lastMouseX;
    private int lastMouseY;

    private void drawLogo(float x, float y, float size, float alpha) {
        mc.getTextureManager().bindTexture(LOGO);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, UiKit.clamp01(alpha));
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + size, 0.0).tex(0.0, 1.0).endVertex();
        wr.pos(x + size, y + size, 0.0).tex(1.0, 1.0).endVertex();
        wr.pos(x + size, y, 0.0).tex(1.0, 0.0).endVertex();
        wr.pos(x, y, 0.0).tex(0.0, 0.0).endVertex();
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawScaledGradientText(String text, float x, float y, float size, float scaleMul,
            float alpha) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0f);
            GlStateManager.scale(scaleMul, scaleMul, 1f);
            float cursor = 0f;
            float total = UiFont.width(text, size);
            for (int i = 0; i < text.length(); i++) {
                String ch = text.substring(i, i + 1);
                float cw = UiFont.width(ch, size);
                float t = total <= 0f ? 0f : (cursor + cw * 0.5f) / total;
                int col = ClientTheme.withAlpha(ClientTheme.lerp(t), alpha);
                UiFont.draw(ch, cursor, 0f, size, col);
                cursor += cw;
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (mouseButton != 0) {
            return;
        }
        if (contains(mouseX, mouseY, languageX, languageY, languageW, languageH)) {
            mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager()));
            return;
        }
        for (MenuButton button : buttons) {
            if (contains(mouseX, mouseY, button.x, button.y, button.w, button.h)) {
                activate(button.action);
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (mc != null) {
            lastMouseX = org.lwjgl.input.Mouse.getEventX() * width / mc.displayWidth;
            lastMouseY = height - org.lwjgl.input.Mouse.getEventY() * height / mc.displayHeight - 1;
        }
    }

    private void activate(Action action) {
        switch (action) {
            case SINGLEPLAYER:
                mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case MULTIPLAYER:
                mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case ALTS:
                mc.displayGuiScreen(new AltManagerScreen(this));
                break;
            case MODS:
                mc.displayGuiScreen(new GuiModList(this));
                break;
            case OPTIONS:
                mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                break;
            case LANGUAGE:
                mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager()));
                break;
            case QUIT:
                mc.shutdown();
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Title screen: Escape should not close (vanilla behavior)
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static boolean contains(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static int lerpColor(int from, int to, float t) {
        t = UiKit.clamp01(t);
        int af = (from >>> 24) & 0xFF, rf = (from >>> 16) & 0xFF, gf = (from >>> 8) & 0xFF, bf = from & 0xFF;
        int at = (to >>> 24) & 0xFF, rt = (to >>> 16) & 0xFF, gt = (to >>> 8) & 0xFF, bt = to & 0xFF;
        return (Math.round(af + (at - af) * t) << 24)
                | (Math.round(rf + (rt - rf) * t) << 16)
                | (Math.round(gf + (gt - gf) * t) << 8)
                | Math.round(bf + (bt - bf) * t);
    }

    private enum Action {
        SINGLEPLAYER,
        MULTIPLAYER,
        ALTS,
        MODS,
        OPTIONS,
        LANGUAGE,
        QUIT
    }

    private static final class MenuButton {
        final Action action;
        final String label;
        float x;
        float y;
        float w;
        float h;
        final UiKit.AnimatedFloat hover = new UiKit.AnimatedFloat(0f);

        MenuButton(Action action, String label) {
            this.action = action;
            this.label = label;
            hover.setDurationMs(UiKit.DURATION_FAST_MS, 1f);
        }
    }
}
