package gnu.client.ui.menu;

import gnu.client.alt.AltAccount;
import gnu.client.alt.AltManager;
import gnu.client.alt.MicrosoftAuth;
import gnu.client.module.modules.settings.ClickGuiModule;
import gnu.client.ui.ClientTheme;
import gnu.client.ui.UiBlur;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lux-styled alt manager: browser Microsoft login, cracked username, cookie file.
 */
public class AltManagerScreen extends GuiScreen {

    private enum Mode {
        LIST,
        BROWSER,
        CRACKED,
        COOKIE
    }

    private static final float PANEL_W = 340f;
    private static final float PANEL_RADIUS = 10f;
    private static final float BTN_H = 26f;
    private static final float ROW_H = 28f;

    private final GuiScreen parent;
    private final UiKit.UiClock clock = new UiKit.UiClock();
    private final UiKit.AnimatedFloat openFade = new UiKit.AnimatedFloat(0f);
    private final List<UiButton> buttons = new ArrayList<UiButton>();
    private final List<AltRow> rows = new ArrayList<AltRow>();

    private Mode mode = Mode.LIST;
    private final LuxTextField inputField = new LuxTextField();
    private String status = "";
    private boolean statusError;
    private boolean busy;
    private float scroll;
    private float targetScroll;
    private float panelX;
    private float panelY;
    private float panelH;
    private float listTop;
    private float listH;

    private String browserAuthUrl = "";
    private volatile boolean cancelBrowser;

    private final AtomicReference<Runnable> mainThreadTask = new AtomicReference<Runnable>();

    public AltManagerScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        AltManager.instance().ensureLoaded();
        clock.reset();
        openFade.snap(0f);
        openFade.setDurationMs(UiKit.DURATION_SLOW_MS, 1f);
        openFade.setTarget(1f);
        Keyboard.enableRepeatEvents(true);
        rebuildLayout();
    }

    @Override
    public void onGuiClosed() {
        cancelBrowser = true;
        Keyboard.enableRepeatEvents(false);
        UiBlur.endFrame();
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

    private void rebuildLayout() {
        buttons.clear();
        rows.clear();

        panelH = Math.min(height - 36f, 320f);
        panelX = (width - PANEL_W) * 0.5f;
        panelY = (height - panelH) * 0.5f;

        float fieldY = panelY + 58f;
        inputField.setBounds(panelX + 14f, fieldY, PANEL_W - 28f, 18f);
        if (mode == Mode.CRACKED) {
            inputField.setMaxLength(16);
        } else if (mode == Mode.COOKIE) {
            inputField.setMaxLength(4096);
        }

        if (mode == Mode.LIST) {
            status = "Logged in as " + AltManager.instance().currentUsername();
            statusError = false;
            float actionY = panelY + panelH - 14f - BTN_H;
            float gap = 6f;
            float bw = (PANEL_W - 28f - gap * 3f) / 4f;
            float bx = panelX + 14f;
            buttons.add(btn("Browser", bx, actionY, bw, new Runnable() {
                @Override
                public void run() {
                    enterMode(Mode.BROWSER);
                }
            }));
            buttons.add(btn("Cracked", bx + (bw + gap), actionY, bw, new Runnable() {
                @Override
                public void run() {
                    enterMode(Mode.CRACKED);
                }
            }));
            buttons.add(btn("Cookie", bx + 2f * (bw + gap), actionY, bw, new Runnable() {
                @Override
                public void run() {
                    enterMode(Mode.COOKIE);
                }
            }));
            buttons.add(btn("Back", bx + 3f * (bw + gap), actionY, bw, new Runnable() {
                @Override
                public void run() {
                    mc.displayGuiScreen(parent);
                }
            }));

            listTop = panelY + 52f;
            listH = actionY - listTop - 10f;
            List<AltAccount> accounts = AltManager.instance().accounts();
            float ry = 0f;
            for (final AltAccount account : accounts) {
                final AltRow row = new AltRow(account, ry);
                rows.add(row);
                ry += ROW_H + 3f;
            }
        } else if (mode == Mode.BROWSER) {
            status = "Sign in with Microsoft, then allow Xbox access.";
            statusError = false;
            float y = panelY + panelH - 14f - BTN_H;
            float gap = 6f;
            float bw = (PANEL_W - 28f - gap) * 0.5f;
            float bx = panelX + 14f;
            buttons.add(btn("Open Link", bx, y, bw, new Runnable() {
                @Override
                public void run() {
                    openVerifyLink();
                }
            }));
            buttons.add(btn("Cancel", bx + bw + gap, y, bw, new Runnable() {
                @Override
                public void run() {
                    cancelBrowserLogin();
                }
            }));
            if (!busy && browserAuthUrl.isEmpty()) {
                startLocalhostBrowserLogin();
            }
        } else if (mode == Mode.CRACKED) {
            inputField.setText("");
            inputField.setFocused(true);
            status = "Enter an offline username (max 16).";
            statusError = false;
            float y = panelY + panelH - 14f - BTN_H;
            float gap = 6f;
            float bw = (PANEL_W - 28f - gap) * 0.5f;
            float bx = panelX + 14f;
            buttons.add(btn("Login", bx, y, bw, new Runnable() {
                @Override
                public void run() {
                    startCrackedLogin();
                }
            }));
            buttons.add(btn("Cancel", bx + bw + gap, y, bw, new Runnable() {
                @Override
                public void run() {
                    enterMode(Mode.LIST);
                }
            }));
        } else if (mode == Mode.COOKIE) {
            inputField.setText("");
            inputField.setFocused(true);
            status = "Path to cookies.txt, or paste a Cookie header.";
            statusError = false;
            float y = panelY + panelH - 14f - BTN_H;
            float gap = 6f;
            float bw = (PANEL_W - 28f - gap * 2f) / 3f;
            float bx = panelX + 14f;
            buttons.add(btn("Browse", bx, y, bw, new Runnable() {
                @Override
                public void run() {
                    browseCookieFile();
                }
            }));
            buttons.add(btn("Login", bx + bw + gap, y, bw, new Runnable() {
                @Override
                public void run() {
                    startCookieLogin();
                }
            }));
            buttons.add(btn("Cancel", bx + 2f * (bw + gap), y, bw, new Runnable() {
                @Override
                public void run() {
                    enterMode(Mode.LIST);
                }
            }));
        }
    }

    private void enterMode(Mode next) {
        if (busy && mode == Mode.BROWSER && next != Mode.BROWSER) {
            cancelBrowser = true;
        }
        if (busy && next != Mode.LIST && !(mode == Mode.BROWSER && next == Mode.BROWSER)) {
            return;
        }
        if (next != Mode.BROWSER) {
            browserAuthUrl = "";
            cancelBrowser = false;
        }
        if (next == Mode.LIST || next == Mode.BROWSER) {
            inputField.setFocused(false);
        }
        mode = next;
        rebuildLayout();
    }

    private UiButton btn(String label, float x, float y, float w, Runnable action) {
        UiButton b = new UiButton(label, x, y, w, BTN_H, action);
        b.hover.setDurationMs(UiKit.DURATION_FAST_MS, 1f);
        return b;
    }

    @Override
    public void updateScreen() {
        if (mode != Mode.LIST && mode != Mode.BROWSER) {
            inputField.tick();
        }
        Runnable task = mainThreadTask.getAndSet(null);
        if (task != null) {
            task.run();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        applyVisualSettings();
        clock.tick();
        float dt = clock.dt();
        openFade.update(dt);
        float alpha = UiKit.clamp01(openFade.get());

        float contentH = rows.isEmpty() ? 0f : rows.get(rows.size() - 1).y + ROW_H;
        float maxScroll = Math.max(0f, contentH - listH);
        if (targetScroll > maxScroll) {
            targetScroll = maxScroll;
        }
        if (targetScroll < 0f) {
            targetScroll = 0f;
        }
        scroll += (targetScroll - scroll) * Math.min(1f, dt * 14f);

        for (UiButton b : buttons) {
            b.hover.setTarget(contains(mouseX, mouseY, b.x, b.y, b.w, b.h) ? 1f : 0f);
            b.hover.update(dt);
        }
        for (AltRow row : rows) {
            float ry = listTop + row.y - scroll;
            boolean hovered = mode == Mode.LIST
                    && mouseY >= listTop && mouseY < listTop + listH
                    && contains(mouseX, mouseY, panelX + 12f, ry, PANEL_W - 24f, ROW_H);
            row.hover.setTarget(hovered ? 1f : 0f);
            row.hover.update(dt);
        }

        final float fade = alpha;
        final ScaledResolution sr = new ScaledResolution(mc);
        final float scale = sr.getScaleFactor();
        UiKit.GlGuard.run(new Runnable() {
            @Override
            public void run() {
                drawBackdrop(fade);
                boolean blurOk = false;
                ClickGuiModule gui = ClickGuiModule.instance();
                boolean want = gui != null && gui.isBlurEnabled();
                UiBlur.setEnabled(want);
                try {
                    if (want && UiBlur.isUsable()) {
                        UiBlur.beginFrame(true);
                        blurOk = UiBlur.isUsable();
                    }
                    drawPanel(fade, scale, mouseX, mouseY, blurOk);
                } finally {
                    if (blurOk) {
                        UiBlur.endFrame();
                    }
                }
            }
        });
        // Input glyphs MUST be outside GlGuard / SDF shader draws — otherwise the
        // active texture flickers onto the terrain atlas and looks like block pixels.
        if (mode == Mode.CRACKED || mode == Mode.COOKIE) {
            String hint = mode == Mode.CRACKED ? "Username" : "Cookie file path or header";
            inputField.drawOverlay(fade, hint);
        }
    }

    private void drawBackdrop(float alpha) {
        UiKit.drawRoundedPanel(0f, 0f, width, height, 0f, UiKit.withAlpha(0xFF07080E, alpha));
        float cx = width * 0.5f;
        float cy = height * 0.4f;
        UiKit.drawSoftBloom(cx - 100f, cy - 80f, 200f, 160f, 80f, ClientTheme.color1(), 0.18f * alpha);
        UiKit.drawSoftBloom(cx - 20f, cy - 10f, 220f, 170f, 90f, ClientTheme.color2(), 0.12f * alpha);
    }

    private void drawPanel(float alpha, float scale, int mouseX, int mouseY, boolean blurOk) {
        float px = UiKit.PixelAlign.snap(panelX, scale);
        float py = UiKit.PixelAlign.snap(panelY, scale);
        float pw = UiKit.PixelAlign.snap(PANEL_W, scale);
        float ph = UiKit.PixelAlign.snap(panelH, scale);

        UiKit.drawAccentGlow(px, py, pw, ph, PANEL_RADIUS, 0.4f * alpha);
        if (blurOk) {
            UiBlur.drawPanel(px, py, pw, ph, PANEL_RADIUS, alpha);
            UiKit.drawRoundedPanel(px + 2f, py + 2f, pw, ph, PANEL_RADIUS,
                    UiKit.withAlpha(0x66000000, alpha * 0.25f));
            UiKit.drawRoundedPanel(px, py, pw, ph, PANEL_RADIUS,
                    UiKit.withAlpha(UiKit.PANEL, alpha * 0.62f));
        } else {
            UiKit.drawRoundedPanel(px + 2f, py + 2f, pw, ph, PANEL_RADIUS,
                    UiKit.withAlpha(0x66000000, alpha * 0.35f));
            UiKit.drawRoundedPanel(px, py, pw, ph, PANEL_RADIUS, UiKit.withAlpha(UiKit.PANEL, alpha));
        }
        UiKit.drawRoundedPanel(px, py, pw, 28f, PANEL_RADIUS,
                UiKit.withAlpha(UiKit.PANEL_HEADER, alpha));
        UiKit.drawRoundedPanel(px, py + 22f, pw, 6f, 0f, UiKit.withAlpha(UiKit.PANEL_HEADER, alpha));
        UiKit.drawHorizontalGradient(px + 14f, py + 26f, pw - 28f, 1.5f,
                ClientTheme.withAlpha(ClientTheme.color1(), alpha),
                ClientTheme.withAlpha(ClientTheme.color2(), alpha));

        String title = mode == Mode.LIST ? "Alts"
                : mode == Mode.BROWSER ? "Browser Login"
                : mode == Mode.CRACKED ? "Cracked Login" : "Cookie Login";
        UiFont.draw(title, UiKit.PixelAlign.snap(px + 14f, scale),
                UiKit.PixelAlign.snap(py + 9f, scale), 8.5f, UiKit.withAlpha(UiKit.TEXT, alpha));

        if (mode == Mode.LIST) {
            drawAccountList(alpha, scale);
        } else if (mode == Mode.BROWSER) {
            drawBrowserChrome(alpha, scale);
        } else {
            drawInputChrome(alpha, scale);
        }

        if (status != null && !status.isEmpty() && mode != Mode.BROWSER) {
            float statusY = mode == Mode.LIST ? listTop - 16f : panelY + 86f;
            int col = statusError ? UiKit.DANGER : UiKit.MUTED;
            String msg = status;
            float maxW = PANEL_W - 28f;
            while (msg.length() > 3 && UiFont.width(msg, 6.5f) > maxW) {
                msg = msg.substring(0, msg.length() - 1);
            }
            if (!msg.equals(status) && msg.length() > 1) {
                msg = msg.substring(0, msg.length() - 1) + "…";
            }
            UiFont.draw(msg, UiKit.PixelAlign.snap(px + 14f, scale),
                    UiKit.PixelAlign.snap(statusY, scale), 6.5f, UiKit.withAlpha(col, alpha));
        }

        if (busy) {
            UiFont.draw("Working…", UiKit.PixelAlign.snap(px + pw - 60f, scale),
                    UiKit.PixelAlign.snap(py + 10f, scale), 6.5f,
                    UiKit.withAlpha(ClientTheme.getFadeColor(0), alpha));
        }

        for (UiButton b : buttons) {
            drawButton(b, alpha, scale);
        }
    }

    private void drawAccountList(float alpha, float scale) {
        MinecraftDisplayW dw = displaySize();
        UiKit.ScissorStack scissors = new UiKit.ScissorStack();
        scissors.pushScaled(panelX + 8f, listTop, PANEL_W - 16f, listH, scale, dw.w, dw.h);
        try {
            if (rows.isEmpty()) {
                UiFont.draw("No alts yet — add one below.",
                        UiKit.PixelAlign.snap(panelX + 16f, scale),
                        UiKit.PixelAlign.snap(listTop + 12f, scale),
                        7.5f, UiKit.withAlpha(UiKit.MUTED_DIM, alpha));
            }
            String activeId = AltManager.instance().getActiveId();
            for (AltRow row : rows) {
                float ry = listTop + row.y - scroll;
                if (ry + ROW_H < listTop - 4f || ry > listTop + listH + 4f) {
                    continue;
                }
                float rx = panelX + 12f;
                float rw = PANEL_W - 24f;
                float hover = row.hover.get();
                boolean active = row.account.getId() != null && row.account.getId().equals(activeId);
                if (active || hover > 0.02f) {
                    UiKit.drawAccentGlow(rx, ry, rw, ROW_H, 6f, 0.35f * alpha * Math.max(hover, active ? 0.7f : 0f));
                }
                int fill = hover > 0.01f ? UiKit.ROW_HOVER : UiKit.ROW_IDLE;
                UiKit.drawRoundedPanel(rx, ry, rw, ROW_H, 6f, UiKit.withAlpha(fill, alpha));
                if (active) {
                    UiKit.drawVerticalGradient(rx, ry, rw, ROW_H,
                            ClientTheme.withAlpha(ClientTheme.lighten(ClientTheme.color1(), 0.1f), alpha * 0.55f),
                            ClientTheme.withAlpha(ClientTheme.color2(), alpha * 0.55f));
                }
                UiKit.drawRoundedPanel(rx + 1f, ry + 5f, 2.4f, ROW_H - 10f, 1.2f,
                        ClientTheme.withAlpha(ClientTheme.getFadeColor(row.y * 8), alpha));

                UiFont.draw(row.account.getUsername(),
                        UiKit.PixelAlign.snap(rx + 10f, scale),
                        UiKit.PixelAlign.snap(ry + 5f, scale),
                        7.5f, UiKit.withAlpha(UiKit.TEXT, alpha));
                UiFont.draw(row.account.typeLabel(),
                        UiKit.PixelAlign.snap(rx + 10f, scale),
                        UiKit.PixelAlign.snap(ry + 15f, scale),
                        6f, UiKit.withAlpha(UiKit.MUTED, alpha));

                float delW = 44f;
                float delX = rx + rw - delW - 6f;
                boolean delHover = hover > 0.01f && contains(lastMx, lastMy, delX, ry + 4f, delW, ROW_H - 8f);
                UiKit.drawRoundedPanel(delX, ry + 5f, delW, ROW_H - 10f, 5f,
                        UiKit.withAlpha(delHover ? UiKit.DANGER : UiKit.TRACK, alpha * 0.9f));
                float dwLabel = UiFont.width("Delete", 6f);
                UiFont.draw("Delete",
                        UiKit.PixelAlign.snap(delX + (delW - dwLabel) * 0.5f, scale),
                        UiKit.PixelAlign.snap(ry + (ROW_H - UiFont.height(6f)) * 0.5f, scale),
                        6f, UiKit.withAlpha(UiKit.TEXT, alpha));
                row.deleteX = delX;
                row.deleteW = delW;
            }
        } finally {
            scissors.pop();
        }
    }

    private void drawBrowserChrome(float alpha, float scale) {
        float cx = panelX + PANEL_W * 0.5f;
        float top = panelY + 64f;

        UiKit.drawRoundedPanel(panelX + 18f, top, PANEL_W - 36f, 88f, 8f,
                UiKit.withAlpha(UiKit.ROW_IDLE, alpha));
        UiKit.drawAccentGlow(panelX + 18f, top, PANEL_W - 36f, 88f, 8f, 0.3f * alpha);

        String title = statusError ? "Login failed" : "Waiting for browser";
        float tw = UiFont.width(title, 8.5f);
        UiFont.draw(title, UiKit.PixelAlign.snap(cx - tw * 0.5f, scale),
                UiKit.PixelAlign.snap(top + 18f, scale), 8.5f,
                UiKit.withAlpha(statusError ? UiKit.DANGER : UiKit.TEXT, alpha));

        String line1 = statusError ? status
                : "Log in, then allow this app to access Xbox Live.";
        float maxW = PANEL_W - 56f;
        String shown = line1;
        while (shown.length() > 3 && UiFont.width(shown, 6.5f) > maxW) {
            shown = shown.substring(0, shown.length() - 1);
        }
        if (!shown.equals(line1)) {
            shown = shown + "…";
        }
        float l1w = UiFont.width(shown, 6.5f);
        UiFont.draw(shown, UiKit.PixelAlign.snap(cx - l1w * 0.5f, scale),
                UiKit.PixelAlign.snap(top + 40f, scale), 6.5f,
                UiKit.withAlpha(statusError ? UiKit.DANGER : UiKit.MUTED, alpha));

        if (busy && !statusError) {
            String wait = browserAuthUrl.isEmpty() ? "Opening Microsoft…" : "Complete login in your browser…";
            float ww = UiFont.width(wait, 7f);
            UiFont.draw(wait, UiKit.PixelAlign.snap(cx - ww * 0.5f, scale),
                    UiKit.PixelAlign.snap(top + 60f, scale), 7f,
                    UiKit.withAlpha(ClientTheme.getFadeColor(0), alpha));
        }
    }

    private void drawInputChrome(float alpha, float scale) {
        float fx = panelX + 12f;
        float fy = panelY + 54f;
        float fw = PANEL_W - 24f;
        float fh = 22f;
        UiKit.drawRoundedPanel(fx, fy, fw, fh, 6f, UiKit.withAlpha(UiKit.ROW_IDLE, alpha));
        UiKit.drawHorizontalGradient(fx + 2f, fy + fh - 1.5f, fw - 4f, 1.2f,
                ClientTheme.withAlpha(ClientTheme.color1(), alpha * 0.7f),
                ClientTheme.withAlpha(ClientTheme.color2(), alpha * 0.7f));
        // Text is drawn after GlGuard in drawOverlay — keeps glyphs off the SDF path.
    }

    private void drawButton(UiButton b, float alpha, float scale) {
        float hx = UiKit.PixelAlign.snap(b.x, scale);
        float hy = UiKit.PixelAlign.snap(b.y, scale);
        float hw = UiKit.PixelAlign.snap(b.w, scale);
        float hh = UiKit.PixelAlign.snap(b.h, scale);
        float hover = b.hover.get();
        if (hover > 0.02f) {
            UiKit.drawAccentGlow(hx, hy, hw, hh, 6f, 0.45f * alpha * hover);
        }
        float idleA = alpha * (1f - hover * 0.9f);
        if (idleA > 0.02f) {
            UiKit.drawRoundedPanel(hx, hy, hw, hh, 6f,
                    UiKit.withAlpha(hover > 0.01f ? UiKit.ROW_HOVER : UiKit.ROW_IDLE, idleA));
        }
        if (hover > 0.02f) {
            UiKit.drawVerticalGradient(hx, hy, hw, hh,
                    ClientTheme.withAlpha(ClientTheme.lighten(ClientTheme.color1(), 0.1f), alpha * hover),
                    ClientTheme.withAlpha(ClientTheme.color2(), alpha * hover));
        }
        float labelW = UiFont.width(b.label, 7.5f);
        UiFont.draw(b.label,
                UiKit.PixelAlign.snap(hx + (hw - labelW) * 0.5f, scale),
                UiKit.PixelAlign.snap(hy + (hh - UiFont.height(7.5f)) * 0.5f, scale),
                7.5f, UiKit.withAlpha(UiKit.TEXT, alpha));
    }

    private int lastMx;
    private int lastMy;

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        lastMx = mouseX;
        lastMy = mouseY;
        if (mouseButton != 0) {
            return;
        }
        // Allow Open Link / Cancel during browser login even while busy
        if (busy && mode == Mode.BROWSER) {
            for (UiButton b : buttons) {
                if (("Cancel".equals(b.label) || "Open Link".equals(b.label))
                        && contains(mouseX, mouseY, b.x, b.y, b.w, b.h)) {
                    b.action.run();
                    return;
                }
            }
            return;
        }
        if (busy) {
            return;
        }
        if (mode != Mode.LIST && mode != Mode.BROWSER) {
            inputField.mouseClicked(mouseX, mouseY);
        }
        for (UiButton b : buttons) {
            if (contains(mouseX, mouseY, b.x, b.y, b.w, b.h)) {
                b.action.run();
                return;
            }
        }
        if (mode == Mode.LIST) {
            for (AltRow row : rows) {
                float ry = listTop + row.y - scroll;
                if (mouseY < listTop || mouseY >= listTop + listH) {
                    continue;
                }
                if (contains(mouseX, mouseY, row.deleteX, ry + 4f, row.deleteW, ROW_H - 8f)) {
                    AltManager.instance().remove(row.account);
                    rebuildLayout();
                    return;
                }
                if (contains(mouseX, mouseY, panelX + 12f, ry, PANEL_W - 24f, ROW_H)) {
                    loginExisting(row.account);
                    return;
                }
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && mode == Mode.LIST) {
            targetScroll -= wheel / 120f * 22f;
        }
        if (mc != null) {
            lastMx = Mouse.getEventX() * width / mc.displayWidth;
            lastMy = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (mode == Mode.BROWSER) {
                cancelBrowserLogin();
            } else if (mode != Mode.LIST) {
                enterMode(Mode.LIST);
            } else {
                mc.displayGuiScreen(parent);
            }
            return;
        }
        if (busy) {
            return;
        }
        if (mode != Mode.LIST && mode != Mode.BROWSER) {
            if (inputField.keyTyped(typedChar, keyCode)) {
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                if (mode == Mode.CRACKED) {
                    startCrackedLogin();
                } else if (mode == Mode.COOKIE) {
                    startCookieLogin();
                }
            }
        }
    }

    private void openVerifyLink() {
        if (browserAuthUrl == null || browserAuthUrl.isEmpty()) {
            setStatus("Login link not ready yet.", true);
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(browserAuthUrl));
            } else {
                setStatus("Could not open browser automatically.", true);
            }
        } catch (Exception e) {
            setStatus("Open browser failed: " + e.getMessage(), true);
        }
    }

    private void cancelBrowserLogin() {
        cancelBrowser = true;
        busy = false;
        browserAuthUrl = "";
        mode = Mode.LIST;
        rebuildLayout();
        setStatus("Browser login cancelled.", false);
    }

    private void startLocalhostBrowserLogin() {
        if (busy) {
            return;
        }
        busy = true;
        cancelBrowser = false;
        browserAuthUrl = "";
        setStatus("Opening Microsoft login…", false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AltManager.instance().addMicrosoftBrowser(new MicrosoftAuth.BrowserHooks() {
                        @Override
                        public void onAuthorizeUrl(final String authorizeUrl) {
                            mainThreadTask.set(new Runnable() {
                                @Override
                                public void run() {
                                    browserAuthUrl = authorizeUrl;
                                    status = "Sign in with Microsoft, then allow Xbox access.";
                                    statusError = false;
                                    openVerifyLink();
                                }
                            });
                        }
                    }, new MicrosoftAuth.CancelFlag() {
                        @Override
                        public boolean isCancelled() {
                            return cancelBrowser;
                        }
                    });
                    mainThreadTask.set(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            browserAuthUrl = "";
                            mode = Mode.LIST;
                            rebuildLayout();
                            setStatus("Logged in as " + AltManager.instance().currentUsername(), false);
                        }
                    });
                } catch (final Exception e) {
                    mainThreadTask.set(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            if (cancelBrowser) {
                                browserAuthUrl = "";
                                mode = Mode.LIST;
                                rebuildLayout();
                                setStatus("Browser login cancelled.", false);
                                return;
                            }
                            String msg = e.getMessage();
                            if (msg == null || msg.isEmpty()) {
                                msg = e.getClass().getSimpleName();
                            }
                            setStatus(msg, true);
                        }
                    });
                }
            }
        }, "gnu-alt-browser").start();
    }

    private void browseCookieFile() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileDialog dialog = new FileDialog((Frame) null, "Select cookie file", FileDialog.LOAD);
                    dialog.setFile("*.txt");
                    dialog.setVisible(true);
                    String dir = dialog.getDirectory();
                    String file = dialog.getFile();
                    if (dir != null && file != null) {
                        final String path = new File(dir, file).getAbsolutePath();
                        mainThreadTask.set(new Runnable() {
                            @Override
                            public void run() {
                                if (inputField != null) {
                                    inputField.setText(path);
                                    inputField.setFocused(true);
                                }
                                setStatus("Cookie file selected.", false);
                            }
                        });
                    }
                } catch (Exception e) {
                    final String msg = e.getMessage();
                    mainThreadTask.set(new Runnable() {
                        @Override
                        public void run() {
                            setStatus("Browse failed: " + msg, true);
                        }
                    });
                }
            }
        }, "gnu-cookie-browse").start();
    }

    private void startCrackedLogin() {
        final String name = inputField.getText();
        runAuth("Logging in offline…", new AuthWork() {
            @Override
            public void run() throws Exception {
                AltManager.instance().addCracked(name);
            }
        });
    }

    private void startCookieLogin() {
        final String text = inputField.getText().trim();
        runAuth("Signing in with cookies…", new AuthWork() {
            @Override
            public void run() throws Exception {
                Path asPath = Paths.get(text);
                if (text.contains("=") && (text.contains(";") || !java.nio.file.Files.isRegularFile(asPath))) {
                    AltManager.instance().addFromCookieText(text);
                } else {
                    AltManager.instance().addFromCookieFile(asPath);
                }
            }
        });
    }

    private void loginExisting(final AltAccount account) {
        runAuth("Switching to " + account.getUsername() + "…", new AuthWork() {
            @Override
            public void run() throws Exception {
                AltManager.instance().login(account);
            }
        });
    }

    private void runAuth(String pending, final AuthWork work) {
        if (busy) {
            return;
        }
        busy = true;
        setStatus(pending, false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    work.run();
                    mainThreadTask.set(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            mode = Mode.LIST;
                            rebuildLayout();
                            setStatus("Logged in as " + AltManager.instance().currentUsername(), false);
                        }
                    });
                } catch (final Exception e) {
                    mainThreadTask.set(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            String msg = e.getMessage();
                            if (msg == null || msg.isEmpty()) {
                                msg = e.getClass().getSimpleName();
                            }
                            setStatus(msg, true);
                        }
                    });
                }
            }
        }, "gnu-alt-auth").start();
    }

    private void setStatus(String message, boolean error) {
        status = message == null ? "" : message;
        statusError = error;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static boolean contains(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private MinecraftDisplayW displaySize() {
        return new MinecraftDisplayW(mc != null ? mc.displayWidth : 0, mc != null ? mc.displayHeight : 0);
    }

    private interface AuthWork {
        void run() throws Exception;
    }

    private static final class MinecraftDisplayW {
        final int w;
        final int h;

        MinecraftDisplayW(int w, int h) {
            this.w = w;
            this.h = h;
        }
    }

    private static final class UiButton {
        final String label;
        final float x;
        final float y;
        final float w;
        final float h;
        final Runnable action;
        final UiKit.AnimatedFloat hover = new UiKit.AnimatedFloat(0f);

        UiButton(String label, float x, float y, float w, float h, Runnable action) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    private static final class AltRow {
        final AltAccount account;
        final float y;
        final UiKit.AnimatedFloat hover = new UiKit.AnimatedFloat(0f);
        float deleteX;
        float deleteW;

        AltRow(AltAccount account, float y) {
            this.account = account;
            this.y = y;
            hover.setDurationMs(UiKit.DURATION_FAST_MS, 1f);
        }
    }

    /**
     * Lux text input drawn with {@link UiFont} so UiKit GL state can't bind the
     * terrain atlas into vanilla {@code GuiTextField} glyphs.
     */
    private static final class LuxTextField {
        private String text = "";
        private boolean focused;
        private int cursor;
        private int maxLength = 256;
        private int blink;
        private float x;
        private float y;
        private float w;
        private float h;

        void setBounds(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        void setMaxLength(int maxLength) {
            this.maxLength = Math.max(1, maxLength);
            if (text.length() > this.maxLength) {
                text = text.substring(0, this.maxLength);
                cursor = Math.min(cursor, text.length());
            }
        }

        void setText(String value) {
            text = value == null ? "" : value;
            if (text.length() > maxLength) {
                text = text.substring(0, maxLength);
            }
            cursor = text.length();
        }

        String getText() {
            return text;
        }

        void setFocused(boolean focused) {
            this.focused = focused;
            if (focused) {
                blink = 0;
            }
        }

        boolean isFocused() {
            return focused;
        }

        void tick() {
            blink++;
        }

        void mouseClicked(int mouseX, int mouseY) {
            focused = contains(mouseX, mouseY, x - 2f, y - 2f, w + 4f, h + 4f);
            if (focused) {
                blink = 0;
                // Place cursor roughly by click x
                float rel = mouseX - (x + 2f);
                int pos = text.length();
                float acc = 0f;
                net.minecraft.client.gui.FontRenderer fr =
                        net.minecraft.client.Minecraft.getMinecraft().fontRendererObj;
                for (int i = 0; i < text.length(); i++) {
                    float cw = fr != null ? fr.getCharWidth(text.charAt(i)) : 6f;
                    if (acc + cw * 0.5f >= rel) {
                        pos = i;
                        break;
                    }
                    acc += cw;
                }
                cursor = pos;
            }
        }

        boolean keyTyped(char typedChar, int keyCode) {
            if (!focused) {
                return false;
            }
            if (GuiScreen.isKeyComboCtrlA(keyCode)) {
                cursor = text.length();
                return true;
            }
            if (GuiScreen.isKeyComboCtrlC(keyCode)) {
                GuiScreen.setClipboardString(text);
                return true;
            }
            if (GuiScreen.isKeyComboCtrlV(keyCode)) {
                write(GuiScreen.getClipboardString());
                return true;
            }
            if (GuiScreen.isKeyComboCtrlX(keyCode)) {
                GuiScreen.setClipboardString(text);
                text = "";
                cursor = 0;
                return true;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (cursor > 0 && !text.isEmpty()) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                }
                return true;
            }
            if (keyCode == Keyboard.KEY_DELETE) {
                if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                }
                return true;
            }
            if (keyCode == Keyboard.KEY_LEFT) {
                if (cursor > 0) {
                    cursor--;
                }
                blink = 0;
                return true;
            }
            if (keyCode == Keyboard.KEY_RIGHT) {
                if (cursor < text.length()) {
                    cursor++;
                }
                blink = 0;
                return true;
            }
            if (keyCode == Keyboard.KEY_HOME) {
                cursor = 0;
                blink = 0;
                return true;
            }
            if (keyCode == Keyboard.KEY_END) {
                cursor = text.length();
                blink = 0;
                return true;
            }
            if (typedChar >= 32 && typedChar != 127) {
                write(String.valueOf(typedChar));
                return true;
            }
            return keyCode != Keyboard.KEY_ESCAPE;
        }

        private void write(String insert) {
            if (insert == null || insert.isEmpty()) {
                return;
            }
            String cleaned = insert.replace("\r", "").replace("\n", "");
            int room = maxLength - text.length();
            if (room <= 0) {
                return;
            }
            if (cleaned.length() > room) {
                cleaned = cleaned.substring(0, room);
            }
            text = text.substring(0, cursor) + cleaned + text.substring(cursor);
            cursor += cleaned.length();
            blink = 0;
        }

        void drawOverlay(float alpha, String hint) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
            if (minecraft == null || minecraft.fontRendererObj == null) {
                return;
            }
            net.minecraft.client.gui.FontRenderer fr = minecraft.fontRendererObj;

            // Full fixed-pipeline reset after UiKit SDF / bloom draws.
            org.lwjgl.opengl.GL20.glUseProgram(0);
            net.minecraft.client.renderer.GlStateManager.setActiveTexture(
                    net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            net.minecraft.client.renderer.GlStateManager.enableTexture2D();
            net.minecraft.client.renderer.GlStateManager.disableLighting();
            net.minecraft.client.renderer.GlStateManager.disableFog();
            net.minecraft.client.renderer.GlStateManager.disableDepth();
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
            // Break GlStateManager texture cache so FontRenderer actually binds ascii.png
            UiKit.invalidateTextureBind();

            int textX = Math.round(x + 2f);
            int textY = Math.round(y + (h - fr.FONT_HEIGHT) * 0.5f);
            int color = withAlphaInt(0xFFF5F6FA, alpha);
            int hintColor = withAlphaInt(0xFF5A606E, alpha);

            if (text.isEmpty() && !focused) {
                fr.drawString(hint, textX, textY, hintColor, false);
                return;
            }

            String before = text.substring(0, cursor);
            int caretX = fr.getStringWidth(before);
            int viewW = Math.max(8, Math.round(w - 6f));
            int scroll = 0;
            if (caretX > viewW) {
                scroll = caretX - viewW;
            }

            // Clip by measuring a visible prefix/suffix window
            String visible = text;
            while (visible.length() > 0 && fr.getStringWidth(visible) - scroll > viewW + 12) {
                visible = visible.substring(0, visible.length() - 1);
            }

            // Scissor to the field so scrolled glyphs don't spill
            net.minecraft.client.gui.ScaledResolution sr =
                    new net.minecraft.client.gui.ScaledResolution(minecraft);
            int sf = sr.getScaleFactor();
            int sx = Math.round(x) * sf;
            int sy = (sr.getScaledHeight() - Math.round(y + h)) * sf;
            int sw = Math.round(w) * sf;
            int sh = Math.round(h) * sf;
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glScissor(sx, sy, Math.max(0, sw), Math.max(0, sh));
            try {
                fr.drawString(visible, textX - scroll, textY, color, false);
            } finally {
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            }

            if (focused && (blink / 6) % 2 == 0) {
                int cx = textX + caretX - scroll;
                drawCaret(cx, textY, fr.FONT_HEIGHT, color);
            }
        }

        private void drawCaret(int cx, int cy, int height, int argb) {
            float a = ((argb >>> 24) & 0xFF) / 255f;
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >> 8) & 0xFF) / 255f;
            float b = (argb & 0xFF) / 255f;
            org.lwjgl.opengl.GL20.glUseProgram(0);
            net.minecraft.client.renderer.GlStateManager.disableTexture2D();
            net.minecraft.client.renderer.GlStateManager.color(r, g, b, a);
            net.minecraft.client.renderer.Tessellator tess =
                    net.minecraft.client.renderer.Tessellator.getInstance();
            net.minecraft.client.renderer.WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(org.lwjgl.opengl.GL11.GL_QUADS,
                    net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
            wr.pos(cx, cy + height, 0.0).color(r, g, b, a).endVertex();
            wr.pos(cx + 1.0, cy + height, 0.0).color(r, g, b, a).endVertex();
            wr.pos(cx + 1.0, cy, 0.0).color(r, g, b, a).endVertex();
            wr.pos(cx, cy, 0.0).color(r, g, b, a).endVertex();
            tess.draw();
            net.minecraft.client.renderer.GlStateManager.enableTexture2D();
            net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
        }

        private static int withAlphaInt(int rgb, float alphaMul) {
            float a = ((rgb >>> 24) & 0xFF) / 255f * Math.max(0f, Math.min(1f, alphaMul));
            return (Math.round(a * 255f) << 24) | (rgb & 0x00FFFFFF);
        }
    }
}
