package gnu.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import gnu.client.common.GnuLog;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.ui.clickgui.ClickGuiLayout;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConfigManager {

    public enum ProfileLoadResult {
        LOADED,
        MISSING,
        INVALID_JSON,
        FAILED
    }

    private static final String CONFIG_DIR = "config";
    private static final String CLIENT_DIR = "gnuclient";
    private static final String CONFIGS_DIR = "configs";
    private static final String DEFAULT_CONFIG = "default.json";
    private static final String DEFAULT_CONFIG_NAME = "default";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SAVE_DEBOUNCE_MS = 500L;
    private static final Clock SYSTEM_CLOCK = System::currentTimeMillis;
    private static final ConfigWriter FILE_WRITER = (path, root) -> {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(root, writer);
        }
    };

    public static final ConfigManager INSTANCE = new ConfigManager();

    private Path configPath;
    private final Path profilesDirectory;
    private final Clock clock;
    private final ConfigWriter writer;
    private boolean loading;
    private boolean dirty;
    private long lastWriteAttemptAtMs;
    private String lastProfileLoadError;
    private ClickGuiLayout clickGuiLayout = ClickGuiLayout.defaults();
    private String activeConfigName = DEFAULT_CONFIG_NAME;

    ConfigManager() {
        this(defaultConfig());
    }

    private ConfigManager(DefaultConfig defaultConfig) {
        this(defaultConfig.configPath, defaultConfig.profilesDirectory, SYSTEM_CLOCK, FILE_WRITER);
    }

    private static DefaultConfig defaultConfig() {
        Path path = defaultConfigPath();
        return new DefaultConfig(path, path.getParent());
    }

    private static final class DefaultConfig {
        private final Path configPath;
        private final Path profilesDirectory;

        private DefaultConfig(Path configPath, Path profilesDirectory) {
            this.configPath = configPath;
            this.profilesDirectory = profilesDirectory;
        }
    }

    ConfigManager(Path configPath, Clock clock, ConfigWriter writer) {
        this(configPath, profilesDirectoryFor(configPath), clock, writer);
    }

    private ConfigManager(Path configPath, Path profilesDirectory, Clock clock, ConfigWriter writer) {
        if (configPath == null || clock == null || writer == null) {
            throw new IllegalArgumentException("config path, clock, and writer are required");
        }
        this.configPath = configPath;
        this.profilesDirectory = profilesDirectory;
        this.clock = clock;
        this.writer = writer;
        this.lastWriteAttemptAtMs = clock.nowMs();
    }

    private static Path profilesDirectoryFor(Path configPath) {
        return configPath == null || configPath.getParent() == null ? configPath : configPath.getParent();
    }

    static Path defaultConfigPath(Path base) {
        return base.resolve(CONFIG_DIR).resolve(CLIENT_DIR).resolve(CONFIGS_DIR).resolve(DEFAULT_CONFIG);
    }

    private static Path defaultConfigPath() {
        return defaultConfigPath(minecraftDataDir());
    }

    private static Path minecraftDataDir() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc != null && mc.mcDataDir != null)
                return mc.mcDataDir.toPath();
        } catch (Throwable ignored) {
        }
        String home = System.getProperty("user.home");
        if (home != null)
            return Paths.get(home);
        return Paths.get("/tmp");
    }

    public static ConfigManager instance() {
        return INSTANCE;
    }

    Path profilesDirectory() {
        return profilesDirectory;
    }

    public Path getConfigsDirectory() {
        return profilesDirectory;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public String getActiveConfigName() {
        return activeConfigName;
    }

    public void setActiveConfigName(String activeConfigName) {
        this.activeConfigName = sanitizeProfileName(activeConfigName);
    }

    public Path getConfigPath(String name) {
        return getConfigsDirectory().resolve(sanitizeProfileName(name) + ".json");
    }

    public synchronized boolean isLoading() {
        return loading;
    }

    public static void setLoading(boolean loading) {
        synchronized (INSTANCE) {
            INSTANCE.loading = loading;
        }
    }

    public synchronized ClickGuiLayout getClickGuiLayout() {
        return clickGuiLayout.copy();
    }

    public synchronized void setClickGuiLayout(ClickGuiLayout layout) {
        clickGuiLayout = layout == null ? ClickGuiLayout.defaults() : layout.copy();
        requestSave();
    }

    public synchronized void load() {
        if (!Files.isRegularFile(configPath)) {
            GnuLog.log("config: no file at " + configPath);
            return;
        }

        loading = true;
        try (Reader reader = Files.newBufferedReader(configPath)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null)
                return;

            applyRoot(root, true);
            GnuLog.log("config: loaded from " + configPath);
        } catch (Exception e) {
            GnuLog.log("config: load failed " + e);
        } finally {
            loading = false;
        }
    }

    public synchronized ProfileLoadResult loadProfile(String name) {
        String sanitizedName = sanitizeProfileName(name);
        Path path = getConfigPath(sanitizedName);
        if (!Files.isRegularFile(path)) {
            lastProfileLoadError = "config " + sanitizedName + " not found";
            GnuLog.log("config profile: no file at " + path);
            return ProfileLoadResult.MISSING;
        }

        loading = true;
        try (Reader reader = Files.newBufferedReader(path)) {
            com.google.gson.JsonElement el = GSON.fromJson(reader, com.google.gson.JsonElement.class);
            if (el == null || !el.isJsonObject()) {
                lastProfileLoadError = "invalid JSON in " + sanitizedName;
                GnuLog.log("config profile: no JSON root at " + path);
                return ProfileLoadResult.INVALID_JSON;
            }
            JsonObject root = el.getAsJsonObject();

            JsonObject previousRoot = buildRoot();
            String previousActiveConfigName = activeConfigName;
            Path previousConfigPath = configPath;
            try {
                applyRoot(root, false);
                activeConfigName = sanitizedName;
                configPath = path;
                lastProfileLoadError = null;
                GnuLog.log("config profile: loaded from " + path);
                return ProfileLoadResult.LOADED;
            } catch (Exception e) {
                lastProfileLoadError = e.getMessage();
                activeConfigName = previousActiveConfigName;
                configPath = previousConfigPath;
                GnuLog.log("config profile: load failed " + e);
                try {
                    applyRoot(previousRoot, true);
                } catch (Exception restoreException) {
                    GnuLog.log("config profile: restore after failed load failed " + restoreException);
                }
                return ProfileLoadResult.FAILED;
            }
        } catch (JsonSyntaxException e) {
            lastProfileLoadError = e.getMessage();
            GnuLog.log("config profile: invalid JSON " + e);
            return ProfileLoadResult.INVALID_JSON;
        } catch (Exception e) {
            lastProfileLoadError = e.getMessage();
            GnuLog.log("config profile: load failed " + e);
            return ProfileLoadResult.FAILED;
        } finally {
            loading = false;
        }
    }

    public synchronized String getLastProfileLoadError() {
        return lastProfileLoadError;
    }

    public synchronized void requestSave() {
        if (loading)
            return;
        dirty = true;
    }

    public synchronized void save() {
        requestSave();
        flushIfDue();
    }

    public synchronized void flushIfDue() {
        if (!dirty)
            return;
        long now = clock.nowMs();
        if (now - lastWriteAttemptAtMs < SAVE_DEBOUNCE_MS)
            return;
        writeNow(now);
    }

    public synchronized void flush() {
        if (dirty)
            writeNow(clock.nowMs());
    }

    private JsonObject buildRoot() {
        JsonObject root = new JsonObject();
        JsonObject modulesJson = new JsonObject();
        for (Module module : ModuleManager.INSTANCE.all()) {
            modulesJson.add(module.getName(), module.serialize());
        }
        root.add("modules", modulesJson);
        root.add("clickGui", clickGuiLayout.toJson());
        return root;
    }

    private void writeNow(long now) {
        lastWriteAttemptAtMs = now;
        try {
            writer.write(configPath, buildRoot());
            dirty = false;
            GnuLog.log("config: saved to " + configPath);
        } catch (Exception e) {
            GnuLog.log("config: save failed " + e);
        }
    }

    public synchronized void saveProfile(String name) throws IOException {
        Path path = getConfigPath(name);
        JsonObject root = buildRoot();
        writer.write(path, root);
        GnuLog.log("config profile: saved to " + path);
    }

    public synchronized List<String> listProfiles() throws IOException {
        if (!Files.isDirectory(profilesDirectory))
            return Collections.emptyList();

        List<String> profiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDirectory, "*.json")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                profiles.add(fileName.substring(0, fileName.length() - ".json".length()));
            }
        }
        Collections.sort(profiles, String.CASE_INSENSITIVE_ORDER);
        return profiles;
    }

    private void applyRoot(JsonObject root, boolean restoreKeyCode) {
        clickGuiLayout = ClickGuiLayout.fromJson(asJsonObject(root.get("clickGui")));

        JsonObject modulesJson = asJsonObject(root.get("modules"));
        if (modulesJson == null)
            return;

        for (Module module : ModuleManager.INSTANCE.all()) {
            JsonObject moduleJson = asJsonObject(modulesJson.get(module.getName()));
            if (moduleJson == null)
                continue;

            if (!restoreKeyCode && module.getCategory() == Category.VISUALS) {
                module.deserializeSettings(moduleJson);
            } else {
                module.deserialize(moduleJson, restoreKeyCode);
            }
        }
    }

    private static String sanitizeProfileName(String requested) {
        if (requested == null || requested.trim().isEmpty())
            return DEFAULT_CONFIG_NAME;
        if ("default".equalsIgnoreCase(requested) || "!".equals(requested))
            return DEFAULT_CONFIG_NAME;

        String raw = requested.replaceAll("[^A-Za-z0-9._ -]", "_");
        String sanitized = raw.replaceAll("[. ]+", " ").trim();
        if (sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized))
            return DEFAULT_CONFIG_NAME;
        return sanitized;
    }

    private static JsonObject asJsonObject(com.google.gson.JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    interface Clock {
        long nowMs();
    }

    interface ConfigWriter {
        void write(Path path, JsonObject root) throws IOException;
    }
}
