package gnu.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.ui.clickgui.ClickGuiLayout;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConfigManagerTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void resetModules() {
        ModuleManager.INSTANCE.reset();
        ConfigManager.setLoading(false);
    }

    @Test
    public void dirtySaveWaitsForDebounceThenWrites() throws Exception {
        FakeClock clock = new FakeClock();
        RecordingWriter writer = new RecordingWriter();
        ConfigManager manager = manager(clock, writer);

        manager.requestSave();
        manager.flushIfDue();
        assertEquals(0, writer.attempts);

        clock.advance(499);
        manager.flushIfDue();
        assertEquals(0, writer.attempts);

        clock.advance(1);
        manager.flushIfDue();
        assertEquals(1, writer.attempts);
    }

    @Test
    public void compatibilitySaveRetainsDirtyChangeDuringDebounce() throws Exception {
        FakeClock clock = new FakeClock();
        RecordingWriter writer = new RecordingWriter();
        ConfigManager manager = manager(clock, writer);

        manager.save();
        assertEquals(0, writer.attempts);

        clock.advance(500);
        manager.flushIfDue();
        assertEquals(1, writer.attempts);
    }

    @Test
    public void flushForcesImmediateWrite() throws Exception {
        FakeClock clock = new FakeClock();
        RecordingWriter writer = new RecordingWriter();
        ConfigManager manager = manager(clock, writer);

        manager.requestSave();
        manager.flush();

        assertEquals(1, writer.attempts);
    }

    @Test
    public void failedWriteRemainsDirtyAndRetriesAfterDebounce() throws Exception {
        FakeClock clock = new FakeClock();
        RecordingWriter writer = new RecordingWriter();
        writer.failuresRemaining = 1;
        ConfigManager manager = manager(clock, writer);

        manager.requestSave();
        clock.advance(500);
        manager.flushIfDue();
        assertEquals(1, writer.attempts);

        clock.advance(499);
        manager.flushIfDue();
        assertEquals(1, writer.attempts);

        clock.advance(1);
        manager.flushIfDue();
        assertEquals(2, writer.attempts);
        assertNotNull(writer.lastRoot);
    }

    @Test
    public void layoutUsesDefensiveCopiesAndIsSavedBesideModules() throws Exception {
        FakeClock clock = new FakeClock();
        RecordingWriter writer = new RecordingWriter();
        ConfigManager manager = manager(clock, writer);
        ClickGuiLayout supplied = ClickGuiLayout.defaults();
        supplied.set(Category.COMBAT, 21, 34, false);

        manager.setClickGuiLayout(supplied);
        supplied.set(Category.COMBAT, 999, 999, true);
        ClickGuiLayout returned = manager.getClickGuiLayout();
        returned.set(Category.COMBAT, 777, 777, true);
        manager.flush();

        assertEquals(21, manager.getClickGuiLayout().get(Category.COMBAT).getX());
        assertTrue(writer.lastRoot.has("modules"));
        assertTrue(writer.lastRoot.has("clickGui"));
        ClickGuiLayout persisted = ClickGuiLayout.fromJson(writer.lastRoot.getAsJsonObject("clickGui"));
        assertEquals(21, persisted.get(Category.COMBAT).getX());
        assertEquals(34, persisted.get(Category.COMBAT).getY());
        assertFalse(persisted.get(Category.COMBAT).isOpen());
    }

    @Test
    public void clickGuiLoadsWithoutModulesObject() throws Exception {
        Path path = temporaryFolder.newFile("clickgui-only.json").toPath();
        ClickGuiLayout saved = ClickGuiLayout.defaults();
        saved.set(Category.SCRIPTS, 301, 47, false);
        JsonObject root = new JsonObject();
        root.add("clickGui", saved.toJson());
        writeJson(path, root);
        ConfigManager manager = new ConfigManager(path, new FakeClock(), new RecordingWriter());

        manager.load();

        ClickGuiLayout loaded = manager.getClickGuiLayout();
        assertEquals(301, loaded.get(Category.SCRIPTS).getX());
        assertEquals(47, loaded.get(Category.SCRIPTS).getY());
        assertFalse(loaded.get(Category.SCRIPTS).isOpen());
    }

    @Test
    public void existingModulesOnlyConfigRemainsLoadable() throws Exception {
        Path path = temporaryFolder.newFile("modules-only.json").toPath();
        JsonObject moduleJson = new JsonObject();
        moduleJson.addProperty("enabled", false);
        moduleJson.addProperty("keyCode", 44);
        moduleJson.add("settings", new JsonObject());
        JsonObject modules = new JsonObject();
        modules.add("Test Module", moduleJson);
        JsonObject root = new JsonObject();
        root.add("modules", modules);
        writeJson(path, root);
        TestModule module = new TestModule();
        ModuleManager.INSTANCE.reset();
        ModuleManager.INSTANCE.register(module);
        ConfigManager manager = new ConfigManager(path, new FakeClock(), new RecordingWriter());

        manager.load();

        assertEquals(44, module.getKeyCode());
        assertEquals(8, manager.getClickGuiLayout().get(Category.COMBAT).getX());
    }

    private ConfigManager manager(FakeClock clock, RecordingWriter writer) throws IOException {
        return new ConfigManager(temporaryFolder.newFile().toPath(), clock, writer);
    }

    private static void writeJson(Path path, JsonObject root) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            new Gson().toJson(root, writer);
        }
    }

    private static final class FakeClock implements ConfigManager.Clock {
        private long now;

        @Override
        public long nowMs() {
            return now;
        }

        void advance(long millis) {
            now += millis;
        }
    }

    private static final class RecordingWriter implements ConfigManager.ConfigWriter {
        private int attempts;
        private int failuresRemaining;
        private JsonObject lastRoot;

        @Override
        public void write(Path path, JsonObject root) throws IOException {
            attempts++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IOException("expected test failure");
            }
            lastRoot = new Gson().fromJson(root, JsonObject.class);
        }
    }

    private static final class TestModule extends Module {
        private TestModule() {
            super("Test Module", "Test module", Category.MISC);
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }
    }
}
