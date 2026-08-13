package gnu.client.module.modules.settings;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.render.shaders.ShaderEngine;
import gnu.client.ui.menu.ShadersScreen;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Shader-pack loader. Always-on settings module; keybind opens the pack screen.
 */
public final class ShadersModule extends Module {

    public static final String NAME = "Shaders";

    private static ShadersModule instance;

    private final BoolSetting useShaders = addSetting(new BoolSetting("Use Shaders", false));
    private final BoolSetting oldLighting = addSetting(new BoolSetting("Old Lighting", false));
    private final SliderSetting shadowQuality = addSetting(
            new SliderSetting("Shadow Quality", 1f, 0.5f, 2f, 0.25f));

    private String packName = "OFF";

    public ShadersModule() {
        super(NAME, "OptiFine-format shader packs from the shaderpacks folder", Category.SETTINGS);
        instance = this;
        packName = readSavedPack();
        useShaders.onChanged(() -> {
            if (useShaders.isToggled()) {
                ShaderEngine.INSTANCE.load(packName);
            } else {
                ShaderEngine.INSTANCE.unload();
            }
        });
        setEnabled(true);
    }

    public static ShadersModule instance() {
        return instance;
    }

    public static boolean shadersOn() {
        return instance != null && instance.useShaders.isToggled()
                && instance.packName != null && !"OFF".equalsIgnoreCase(instance.packName);
    }

    public static boolean oldLighting() {
        return instance != null && instance.oldLighting.isToggled();
    }

    public static float shadowQuality() {
        return instance == null ? 1f : instance.shadowQuality.getValue();
    }

    public String packName() {
        return packName;
    }

    public void setPack(String name) {
        packName = name == null ? "OFF" : name;
        writeSavedPack(packName);
        if ("OFF".equalsIgnoreCase(packName)) {
            useShaders.setToggled(false);
            ShaderEngine.INSTANCE.unload();
            return;
        }
        if (!useShaders.isToggled()) {
            useShaders.setToggled(true);
        } else {
            ShaderEngine.INSTANCE.load(packName);
        }
    }

    @Override
    public void toggle() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        if (mc.currentScreen instanceof ShadersScreen) {
            mc.displayGuiScreen(null);
        } else {
            mc.displayGuiScreen(new ShadersScreen());
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(true);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    private static File saveFile() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.mcDataDir == null) {
            return null;
        }
        return new File(mc.mcDataDir, "gnuclient-shaderpack.txt");
    }

    private static String readSavedPack() {
        File f = saveFile();
        if (f == null || !f.isFile()) {
            return "OFF";
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));
            String line = br.readLine();
            return line == null || line.trim().isEmpty() ? "OFF" : line.trim();
        } catch (IOException e) {
            return "OFF";
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void writeSavedPack(String name) {
        File f = saveFile();
        if (f == null) {
            return;
        }
        FileWriter w = null;
        try {
            w = new FileWriter(f);
            w.write(name);
        } catch (IOException ignored) {
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
