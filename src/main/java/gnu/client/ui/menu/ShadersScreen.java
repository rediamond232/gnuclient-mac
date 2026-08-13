package gnu.client.ui.menu;

import gnu.client.module.modules.settings.ShadersModule;
import gnu.client.render.shaders.ShaderEngine;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shader pack picker (Off / Internal / zips and folders in {@code shaderpacks/}).
 */
public final class ShadersScreen extends GuiScreen {

    private PackList list;
    private final List<String> packs = new ArrayList<String>();

    @Override
    public void initGui() {
        packs.clear();
        packs.add("OFF");
        packs.add("Internal");
        File dir = ShaderEngine.shaderPacksDir();
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                List<String> names = new ArrayList<String>();
                for (File f : files) {
                    String n = f.getName();
                    if (f.isDirectory() || n.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        names.add(n);
                    }
                }
                Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
                packs.addAll(names);
            }
        }
        list = new PackList();
        buttonList.add(new GuiButton(0, width / 2 - 154, height - 28, 100, 20, "Done"));
        buttonList.add(new GuiButton(1, width / 2 - 50, height - 28, 204, 20, "Shader Options..."));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (list != null) {
            list.handleMouseInput();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (list != null) {
            list.drawScreen(mouseX, mouseY, partialTicks);
        }
        drawCenteredString(fontRendererObj, "Shaders", width / 2, 12, 0xFFFFFF);
        String cur = ShadersModule.instance() == null ? "OFF" : ShadersModule.instance().packName();
        drawCenteredString(fontRendererObj, "Current: " + cur, width / 2, 24, 0xAAAAAA);
        String st = ShaderEngine.INSTANCE.status();
        if (st != null && !st.isEmpty() && !"OFF".equals(st)) {
            List lines = fontRendererObj.listFormattedStringToWidth(st, width - 24);
            int y = height - 28 - lines.size() * 10;
            for (int i = 0; i < lines.size(); i++) {
                drawCenteredString(fontRendererObj, (String) lines.get(i), width / 2, y + i * 10, 0xFFAA55);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            mc.displayGuiScreen(new ShaderOptionsScreen(this));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private final class PackList extends GuiSlot {
        PackList() {
            super(ShadersScreen.this.mc, ShadersScreen.this.width, ShadersScreen.this.height, 36,
                    ShadersScreen.this.height - 56, 18);
        }

        @Override
        protected int getSize() {
            return packs.size();
        }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            if (index < 0 || index >= packs.size() || ShadersModule.instance() == null) {
                return;
            }
            ShadersModule.instance().setPack(packs.get(index));
        }

        @Override
        protected boolean isSelected(int index) {
            if (ShadersModule.instance() == null || index < 0 || index >= packs.size()) {
                return false;
            }
            return packs.get(index).equalsIgnoreCase(ShadersModule.instance().packName());
        }

        @Override
        protected void drawBackground() {}

        @Override
        protected void drawSlot(int index, int x, int y, int height, int mouseX, int mouseY) {
            if (index < 0 || index >= packs.size()) {
                return;
            }
            drawCenteredString(fontRendererObj, packs.get(index), width / 2, y + 2, 0xFFFFFF);
        }
    }
}
