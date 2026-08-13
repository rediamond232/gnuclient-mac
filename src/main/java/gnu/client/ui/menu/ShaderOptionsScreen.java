package gnu.client.ui.menu;

import gnu.client.render.shaders.ShaderEngine;
import gnu.client.render.shaders.ShaderOptions;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * OptiFine-style shader option screen (screens from {@code shaders.properties}).
 */
public final class ShaderOptionsScreen extends GuiScreen {

    private final GuiScreen parent;
    private final String screenKey;
    private final List<String> tokens = new ArrayList<String>();

    public ShaderOptionsScreen(GuiScreen parent) {
        this(parent, "");
    }

    public ShaderOptionsScreen(GuiScreen parent, String screenKey) {
        this.parent = parent;
        this.screenKey = screenKey == null ? "" : screenKey;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        tokens.clear();
        ShaderOptions opts = ShaderEngine.INSTANCE.options();
        String spec = opts.screen(screenKey);
        if (spec == null || spec.isEmpty()) {
            spec = "<empty>";
        }
        String[] parts = spec.trim().split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                tokens.add(parts[i]);
            }
        }
        int cols = 2;
        int bw = 150;
        int bh = 20;
        int gap = 4;
        int left = width / 2 - bw - gap / 2;
        int top = 32;
        for (int i = 0; i < tokens.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = left + col * (bw + gap);
            int y = top + row * (bh + gap);
            GuiButton b = new GuiButton(i, x, y, bw, bh, labelFor(tokens.get(i), opts));
            if ("<empty>".equals(tokens.get(i))) {
                b.enabled = false;
            }
            buttonList.add(b);
        }
        buttonList.add(new GuiButton(900, width / 2 - 154, height - 28, 100, 20, "Done"));
        buttonList.add(new GuiButton(901, width / 2 - 50, height - 28, 100, 20, "Reset"));
        buttonList.add(new GuiButton(902, width / 2 + 54, height - 28, 100, 20, "Apply"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String title = screenKey.isEmpty() ? "Shader Options" : screenKey.replace('_', ' ');
        drawCenteredString(fontRendererObj, title, width / 2, 12, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        ShaderOptions opts = ShaderEngine.INSTANCE.options();
        if (button.id == 900) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 901) {
            opts.reset();
            ShaderEngine.INSTANCE.reloadCurrent();
            initGui();
            return;
        }
        if (button.id == 902) {
            ShaderEngine.INSTANCE.reloadCurrent();
            initGui();
            return;
        }
        if (button.id < 0 || button.id >= tokens.size()) {
            return;
        }
        String token = tokens.get(button.id);
        if ("<empty>".equals(token) || "<profile>".equals(token)) {
            return;
        }
        if (token.startsWith("[") && token.endsWith("]")) {
            String sub = token.substring(1, token.length() - 1);
            mc.displayGuiScreen(new ShaderOptionsScreen(this, sub));
            return;
        }
        opts.cycle(token);
        ShaderEngine.INSTANCE.reloadCurrent();
        button.displayString = labelFor(token, ShaderEngine.INSTANCE.options());
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String labelFor(String token, ShaderOptions opts) {
        if ("<empty>".equals(token)) {
            return "";
        }
        if ("<profile>".equals(token)) {
            return "Profile";
        }
        if (token.startsWith("[") && token.endsWith("]")) {
            return token.substring(1, token.length() - 1).replace('_', ' ') + "...";
        }
        ShaderOptions.Option o = opts.get(token);
        if (o == null) {
            return token;
        }
        return o.label();
    }
}
