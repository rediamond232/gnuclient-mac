package gnu.client.ui.clickgui;

import gnu.client.config.ConfigManager;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.settings.ClickGuiModule;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.ui.UiBlur;
import gnu.client.ui.UiFont;
import gnu.client.ui.UiKit;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Classic dropdown ClickGUI: z-ordered draggable category columns and settings.
 */
public class ClickGuiScreen extends GuiScreen {

    private final List<CategoryColumn> columns = new ArrayList<CategoryColumn>();
    private final UiKit.UiClock clock = new UiKit.UiClock();
    private final UiKit.ScissorStack scissors = new UiKit.ScissorStack();
    private final UiKit.AnimatedFloat openFade = new UiKit.AnimatedFloat(0f);

    private int nextZ = 1;
    private boolean layoutDirty;
    private List<CategoryColumn> zOrderCache;
    private boolean zOrderDirty = true;

    public ClickGuiScreen() {
        rebuild();
    }

    public void rebuild() {
        Map<Category, List<Module>> byCategory = new EnumMap<Category, List<Module>>(Category.class);
        for (Category cat : Category.values()) {
            byCategory.put(cat, new ArrayList<Module>());
        }
        for (Module module : ModuleManager.INSTANCE.all()) {
            if (module.getCategory() == Category.SETTINGS && "Settings".equals(module.getName())) {
                continue;
            }
            byCategory.get(module.getCategory()).add(module);
        }

        ClickGuiLayout layout = ConfigManager.instance().getClickGuiLayout();
        columns.clear();
        int z = 0;
        for (Category category : Category.values()) {
            List<Module> modules = byCategory.get(category);
            if (modules == null || modules.isEmpty()) {
                continue;
            }
            CategoryColumn column = new CategoryColumn(category);
            column.applyLayout(layout.get(category));
            column.setModules(modules);
            column.setZOrder(z++);
            columns.add(column);
        }
        nextZ = z;
        zOrderDirty = true;
    }

    @Override
    public void initGui() {
        rebuild();
        resetTransient();
        clock.reset();
        openFade.snap(0f);
        openFade.setDurationMs(280f, 1f);
        openFade.setTarget(1f);
    }

    private void resetTransient() {
        layoutDirty = false;
        ClientBootstrap.cancelRebind();
        for (CategoryColumn column : columns) {
            column.resetTransient();
        }
    }

    @Override
    public void onGuiClosed() {
        persistAllLayout();
        ClientBootstrap.cancelRebind();
        for (CategoryColumn column : columns) {
            column.resetTransient();
        }
        ConfigManager.instance().flush();
        UiBlur.endFrame();
    }

    private void persistAllLayout() {
        ClickGuiLayout layout = ConfigManager.instance().getClickGuiLayout();
        for (CategoryColumn column : columns) {
            column.persistTo(layout);
        }
        ConfigManager.instance().setClickGuiLayout(layout);
        layoutDirty = false;
    }

    private void applyVisualSettings() {
        ClickGuiModule gui = ClickGuiModule.instance();
        float speed = 1f;
        if (gui != null) {
            UiFont.setMode(gui.resolveFontMode());
            UiBlur.setEnabled(gui.isBlurEnabled());
            speed = gui.getAnimationSpeed();
        }
        clock.setSpeed(speed);
        for (CategoryColumn column : columns) {
            column.setAnimSpeed(speed);
        }
    }

    private float panelAlpha() {
        ClickGuiModule gui = ClickGuiModule.instance();
        return gui != null ? gui.getPanelOpacity() : 0.84f;
    }

    private float userScale() {
        float s = ClickGuiModule.resolveScale();
        return s <= 0f ? 1.0f : s;
    }

    private int logicalX(int mouseX) {
        return Math.round(mouseX / userScale());
    }

    private int logicalY(int mouseY) {
        return Math.round(mouseY / userScale());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        applyVisualSettings();
        clock.tick();
        float dt = clock.dt();
        openFade.update(dt);
        float alpha = panelAlpha() * UiKit.clamp01(openFade.get());
        boolean blur = UiBlur.isEnabled();

        int lx = logicalX(mouseX);
        int ly = logicalY(mouseY);
        for (CategoryColumn column : columns) {
            column.update(dt, lx, ly, "");
            column.mouseDragged(lx, ly);
        }

        final float panelAlpha = alpha;
        final boolean wantBlur = blur;
        final ScaledResolution sr = new ScaledResolution(mc);
        final float scale = sr.getScaleFactor();
        UiKit.GlGuard.run(new Runnable() {
            @Override
            public void run() {
                UiBlur.beginFrame(wantBlur);
                boolean blurOk = wantBlur && UiBlur.isUsable();
                if (blurOk) {
                    UiBlur.drawFullscreen(1f);
                }
                // Soft world dim behind panels (scaled by open fade)
                float dimA = UiKit.clamp01(openFade.get()) * (blurOk ? 0.22f : 0.40f);
                if (dimA > 0.01f) {
                    float logicalW = width / userScale();
                    float logicalH = height / userScale();
                    GlStateManager.pushMatrix();
                    try {
                        GlStateManager.scale(userScale(), userScale(), 1f);
                        UiKit.drawRoundedPanel(0f, 0f, logicalW, logicalH, 0f,
                                UiKit.withAlpha(0xFF000000, dimA));
                    } finally {
                        GlStateManager.popMatrix();
                    }
                }
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.scale(userScale(), userScale(), 1f);
                    List<CategoryColumn> ordered = sortedByZ();
                    for (CategoryColumn column : ordered) {
                        column.render(panelAlpha, scale, userScale(), "",
                                blurOk, scissors);
                    }
                } finally {
                    GlStateManager.popMatrix();
                    scissors.clear();
                    UiBlur.endFrame();
                }
            }
        });

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private List<CategoryColumn> sortedByZ() {
        if (!zOrderDirty && zOrderCache != null)
            return zOrderCache;
        List<CategoryColumn> ordered = new ArrayList<CategoryColumn>(columns);
        Collections.sort(ordered, new Comparator<CategoryColumn>() {
            @Override
            public int compare(CategoryColumn a, CategoryColumn b) {
                return Integer.compare(a.getZOrder(), b.getZOrder());
            }
        });
        zOrderCache = ordered;
        zOrderDirty = false;
        return ordered;
    }

    private List<CategoryColumn> sortedByZDesc() {
        List<CategoryColumn> ordered = sortedByZ();
        Collections.reverse(ordered);
        return ordered;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int lx = logicalX(mouseX);
        int ly = logicalY(mouseY);

        for (CategoryColumn column : sortedByZDesc()) {
            if (column.containsPoint(lx, ly)) {
                if (column.bringToFront(nextZ++))
                    zOrderDirty = true;
                column.mouseClicked(lx, ly, mouseButton, "");
                layoutDirty = true;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (CategoryColumn column : columns) {
            column.mouseReleased();
        }
        if (layoutDirty) {
            persistAllLayout();
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        int lx = logicalX(mouseX);
        int ly = logicalY(mouseY);
        for (CategoryColumn column : columns) {
            column.mouseDragged(lx, ly);
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int lx = logicalX(mouseX);
        int ly = logicalY(mouseY);
        for (CategoryColumn column : sortedByZDesc()) {
            if (column.handleScroll(lx, ly, wheel, "")) {
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (ClientBootstrap.isRebindActive()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                ClientBootstrap.cancelRebind();
                return;
            }
            if (keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK) {
                String name = ClientBootstrap.rebindModuleName();
                if (name != null) {
                    ClientBootstrap.setModuleKeyCode(name, -1);
                }
                ClientBootstrap.cancelRebind();
                return;
            }
            if (keyCode > 0 && !isModifier(keyCode)) {
                String name = ClientBootstrap.rebindModuleName();
                if (name != null) {
                    ClientBootstrap.setModuleKeyCode(name, keyCode);
                }
                ClientBootstrap.cancelRebind();
                return;
            }
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private static boolean isModifier(int keyCode) {
        return keyCode == Keyboard.KEY_LSHIFT || keyCode == Keyboard.KEY_RSHIFT
                || keyCode == Keyboard.KEY_LCONTROL || keyCode == Keyboard.KEY_RCONTROL
                || keyCode == Keyboard.KEY_LMENU || keyCode == Keyboard.KEY_RMENU;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
