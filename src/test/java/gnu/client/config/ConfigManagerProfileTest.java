package gnu.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConfigManagerProfileTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        ConfigManager.setLoading(true);
    }

    @After
    public void tearDown() {
        ModuleManager.INSTANCE.reset();
        ConfigManager.setLoading(false);
    }

    @Test
    public void defaultConfigPathUsesMinecraftDataDirectoryLayout() throws Exception {
        Path defaultConfigPath = ConfigManager.defaultConfigPath(temporaryFolder.getRoot().toPath());
        String normalized = defaultConfigPath.toString().replace('\\', '/');

        assertTrue("default config path should end with config/gnuclient/configs/default.json",
                normalized.endsWith("config/gnuclient/configs/default.json"));
        assertFalse("default config path should not use legacy ~/.config/gnuclient layout",
                normalized.contains("/.config/gnuclient/configs/default.json"));
    }

    @Test
    public void constructorSetsProfilesDirectoryToConfigPathParent() throws Exception {
        temporaryFolder.newFolder("profiles");
        Path configPath = temporaryFolder.getRoot().toPath().resolve("profiles/default.json");

        ConfigManager manager = new ConfigManager(configPath, () -> 0L, (path, root) -> {
        });

        assertEquals(configPath.getParent(), manager.profilesDirectory());
        assertEquals(configPath.getParent(), manager.getConfigsDirectory());
    }

    @Test
    public void saveProfileWritesNamedConfigRootWithoutSwitchingActivePath() throws Exception {
        registerProfileModules();
        Path configPath = temporaryFolder.newFolder("minecraft-home").toPath()
                .resolve("config/gnuclient/configs/default.json");
        RecordingWriter writer = new RecordingWriter();
        ConfigManager manager = new ConfigManager(configPath, () -> 0L, writer);

        manager.saveProfile("legit");

        assertNotNull(writer.lastPath);
        String normalizedPath = writer.lastPath.toString().replace('\\', '/');
        assertTrue(normalizedPath.endsWith("config/gnuclient/configs/legit.json"));
        assertEquals("default", manager.getActiveConfigName());
        assertTrue(normalizedPath.endsWith(manager.getConfigPath("legit").toString().replace('\\', '/')));
        assertTrue(writer.lastRoot.has("modules"));
        assertTrue(writer.lastRoot.has("clickGui"));
        assertTrue(writer.lastRoot.getAsJsonObject("modules").has("Profile Test Module"));
        assertTrue(writer.lastRoot.getAsJsonObject("modules").has("Visual Profile Module"));
    }

    @Test
    public void listProfilesIncludesSavedJsonNames() throws Exception {
        Path profilesDirectory = temporaryFolder.newFolder("profiles").toPath();
        Path configPath = profilesDirectory.resolve("default.json");
        ConfigManager manager = new ConfigManager(configPath, () -> 0L, (path, root) -> {
        });

        Files.write(manager.getConfigPath("legit"), new JsonObject().toString().getBytes(StandardCharsets.UTF_8));
        Files.write(manager.getConfigPath("a"), new JsonObject().toString().getBytes(StandardCharsets.UTF_8));
        Files.write(manager.getConfigPath("B"), new JsonObject().toString().getBytes(StandardCharsets.UTF_8));

        List<String> profiles = manager.listProfiles();

        assertEquals(Arrays.asList("a", "B", "legit"), profiles);
    }

    @Test
    public void loadProfileAppliesSettingsAndSwitchesActivePath() throws Exception {
        TestModule testModule = registerTestModule();
        VisualTestModule visualModule = registerVisualTestModule();
        Path configPath = temporaryFolder.newFolder("minecraft-home").toPath()
                .resolve("config/gnuclient/configs/default.json");
        ConfigManager manager = new ConfigManager(configPath, () -> 0L, (path, root) -> {
        });
        Files.createDirectories(manager.getConfigPath("legit").getParent());

        testModule.setKeyCode(42);
        visualModule.setKeyCode(55);
        visualModule.setEnabled(true);
        visualModule.resetEnableCounters();
        JsonObject root = profileRoot(
                moduleJson(true, 99, true),
                moduleJson(false, 77, true));
        writeJson(manager.getConfigPath("legit"), root);

        assertEquals(ConfigManager.ProfileLoadResult.LOADED, manager.loadProfile("legit"));

        assertEquals("legit", manager.getActiveConfigName());
        assertTrue(manager.getConfigPath().toString().replace('\\', '/').endsWith("config/gnuclient/configs/legit.json"));
        assertTrue(testModule.isEnabled());
        assertEquals(42, testModule.getKeyCode());
        assertTrue(testModule.getEnabledSetting().getValue());
        assertTrue(visualModule.isEnabled());
        assertEquals(55, visualModule.getKeyCode());
        assertTrue(visualModule.getVisualSetting().getValue());
        assertEquals(0, visualModule.onEnableCount);
        assertEquals(0, visualModule.onDisableCount);
    }

    @Test
    public void loadProfileDefaultRestoresDefaultPath() throws Exception {
        registerProfileModules();
        Path configPath = temporaryFolder.newFolder("minecraft-home").toPath()
                .resolve("config/gnuclient/configs/default.json");
        ConfigManager manager = new ConfigManager(configPath, () -> 0L, (path, root) -> {
        });
        Files.createDirectories(manager.getConfigsDirectory());
        writeJson(manager.getConfigPath("legit"), profileRoot(moduleJson(true, 99, true)));
        writeJson(manager.getConfigPath("default"), profileRoot(moduleJson(false, 1, false)));

        assertEquals(ConfigManager.ProfileLoadResult.LOADED, manager.loadProfile("legit"));
        assertTrue(manager.getConfigPath().toString().replace('\\', '/').endsWith("config/gnuclient/configs/legit.json"));

        assertEquals(ConfigManager.ProfileLoadResult.LOADED, manager.loadProfile("default"));

        assertEquals("default", manager.getActiveConfigName());
        assertTrue(manager.getConfigPath().toString().replace('\\', '/').endsWith("config/gnuclient/configs/default.json"));
    }

    @Test
    public void loadProfileFailureRestoresPreviousRootAndKeepsActivePath() throws Exception {
        FailingTestModule module = new FailingTestModule();
        ModuleManager.INSTANCE.reset();
        ModuleManager.INSTANCE.register(module);
        Path profilesDirectory = temporaryFolder.newFolder("profiles").toPath();
        Path configPath = profilesDirectory.resolve("default.json");
        ConfigManager manager = new ConfigManager(configPath, () -> 0L, (path, root) -> {
        });
        manager.setActiveConfigName("startup");
        module.setEnabled(true);
        module.setKeyCode(42);
        module.getEnabledSetting().setValue(true);
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        JsonObject moduleJson = moduleJson(true, 99, true);
        moduleJson.addProperty("throw", true);
        modules.add(module.getName(), moduleJson);
        root.add("modules", modules);
        writeJson(manager.getConfigPath("legit"), root);

        assertEquals(ConfigManager.ProfileLoadResult.FAILED, manager.loadProfile("legit"));

        assertEquals("startup", manager.getActiveConfigName());
        assertEquals(configPath, manager.getConfigPath());
        assertTrue(module.isEnabled());
        assertEquals(42, module.getKeyCode());
        assertTrue(module.getEnabledSetting().getValue());
    }

    @Test
    public void loadProfileMissingFileDoesNotChangeActiveConfigName() throws Exception {
        Path profilesDirectory = temporaryFolder.newFolder("profiles").toPath();
        Path configPath = profilesDirectory.resolve("default.json");
        ConfigManager manager = new ConfigManager(configPath, () -> 0L, (path, root) -> {
        });
        manager.setActiveConfigName("startup");

        assertEquals(ConfigManager.ProfileLoadResult.MISSING, manager.loadProfile("missing"));

        assertEquals("startup", manager.getActiveConfigName());
        assertEquals(configPath, manager.getConfigPath());
    }

    @Test
    public void loadProfileNullJsonReturnsFalseAndDoesNotChangeActiveConfigName() throws Exception {
        Path profilesDirectory = temporaryFolder.newFolder("profiles").toPath();
        Path configPath = profilesDirectory.resolve("default.json");
        ConfigManager manager = new ConfigManager(configPath, () -> 0L, (path, root) -> {
        });
        manager.setActiveConfigName("startup");
        Files.write(manager.getConfigPath("empty"), "null".getBytes(StandardCharsets.UTF_8));

        assertEquals(ConfigManager.ProfileLoadResult.INVALID_JSON, manager.loadProfile("empty"));

        assertEquals("startup", manager.getActiveConfigName());
        assertEquals(configPath, manager.getConfigPath());
    }

    @Test
    public void loadProfileInvalidJsonReturnsFalseAndDoesNotChangeActiveConfigName() throws Exception {
        Path profilesDirectory = temporaryFolder.newFolder("profiles").toPath();
        Path configPath = profilesDirectory.resolve("default.json");
        ConfigManager manager = new ConfigManager(configPath, () -> 0L, (path, root) -> {
        });
        manager.setActiveConfigName("startup");
        Files.write(manager.getConfigPath("invalid"), "{".getBytes(StandardCharsets.UTF_8));

        assertEquals(ConfigManager.ProfileLoadResult.INVALID_JSON, manager.loadProfile("invalid"));

        assertEquals("startup", manager.getActiveConfigName());
        assertEquals(configPath, manager.getConfigPath());
    }

    @Test
    public void loadRestoresKeyCodeForDefaultConfig() throws Exception {
        Path path = temporaryFolder.newFile("default.json").toPath();
        TestModule module = registerTestModule();
        module.setKeyCode(42);
        writeJson(path, profileRoot(moduleJson(false, 99, false)));
        ConfigManager manager = new ConfigManager(path, () -> 0L, (path1, root) -> {
        });

        manager.load();

        assertFalse(module.isEnabled());
        assertEquals(99, module.getKeyCode());
    }

    private static TestModule registerTestModule() {
        TestModule module = new TestModule();
        ModuleManager.INSTANCE.register(module);
        return module;
    }

    private static VisualTestModule registerVisualTestModule() {
        VisualTestModule module = new VisualTestModule();
        ModuleManager.INSTANCE.register(module);
        return module;
    }

    private static void registerProfileModules() {
        ModuleManager.INSTANCE.reset();
        registerTestModule();
        registerVisualTestModule();
    }

    private static JsonObject profileRoot(JsonObject... moduleRoots) {
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        if (moduleRoots.length > 0) {
            modules.add("Profile Test Module", moduleRoots[0]);
        }
        if (moduleRoots.length > 1) {
            modules.add("Visual Profile Module", moduleRoots[1]);
        }
        root.add("modules", modules);
        return root;
    }

    private static JsonObject moduleJson(boolean enabled, int keyCode, boolean settingValue) {
        JsonObject settings = new JsonObject();
        settings.addProperty("Enabled setting", settingValue);
        settings.addProperty("Visual setting", settingValue);

        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("keyCode", keyCode);
        root.add("settings", settings);
        return root;
    }

    private static void writeJson(Path path, JsonObject root) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            new Gson().toJson(root, writer);
        }
    }

    private static final class RecordingWriter implements ConfigManager.ConfigWriter {
        private Path lastPath;
        private JsonObject lastRoot;

        @Override
        public void write(Path path, JsonObject root) throws IOException {
            lastPath = path;
            lastRoot = new Gson().fromJson(root, JsonObject.class);
        }
    }

    private static final class TestModule extends Module {
        private final BoolSetting enabledSetting = addSetting(new BoolSetting("Enabled setting", false));

        private TestModule() {
            super("Profile Test Module", "Profile-safe test module", Category.MISC);
        }

        BoolSetting getEnabledSetting() {
            return enabledSetting;
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }
    }

    private static final class VisualTestModule extends Module {
        private final BoolSetting visualSetting = addSetting(new BoolSetting("Visual setting", false));
        private int onEnableCount;
        private int onDisableCount;

        private VisualTestModule() {
            super("Visual Profile Module", "Profile-safe visual test module", Category.VISUALS);
        }

        BoolSetting getVisualSetting() {
            return visualSetting;
        }

        void resetEnableCounters() {
            onEnableCount = 0;
            onDisableCount = 0;
        }

        @Override
        public void onEnable() {
            onEnableCount++;
        }

        @Override
        public void onDisable() {
            onDisableCount++;
        }
    }

    private static final class FailingTestModule extends Module {
        private final BoolSetting enabledSetting = addSetting(new BoolSetting("Enabled setting", false));

        private FailingTestModule() {
            super("Failing Profile Module", "Profile-load failure test module", Category.MISC);
        }

        BoolSetting getEnabledSetting() {
            return enabledSetting;
        }

        @Override
        public void deserialize(JsonObject root, boolean restoreKeyCode) {
            if (root.has("throw"))
                throw new IllegalStateException("expected profile load failure");
            super.deserialize(root, restoreKeyCode);
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }
    }
}
