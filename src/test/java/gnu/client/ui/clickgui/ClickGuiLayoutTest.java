package gnu.client.ui.clickgui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gnu.client.module.Category;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClickGuiLayoutTest {

    @Test
    public void defaultsCoverAllCategoriesIncludingScripts() {
        ClickGuiLayout layout = ClickGuiLayout.defaults();

        for (Category category : Category.values()) {
            ClickGuiLayout.Column column = layout.get(category);
            assertNotNull(column);
            assertEquals(8 + category.ordinal() * 168, column.getX());
            assertEquals(8, column.getY());
            assertTrue(column.isOpen());
        }
    }

    @Test
    public void jsonRoundTripPersistsOnlyPositionAndOpenState() {
        ClickGuiLayout layout = ClickGuiLayout.defaults();
        layout.set(Category.COMBAT, 42, 73, false);

        JsonObject json = layout.toJson();
        ClickGuiLayout restored = ClickGuiLayout.fromJson(json);

        assertEquals(1, json.get("version").getAsInt());
        assertEquals(2, json.entrySet().size());
        JsonObject combatJson = json.getAsJsonObject("columns").getAsJsonObject("COMBAT");
        assertEquals(3, combatJson.entrySet().size());
        assertTrue(combatJson.has("x"));
        assertTrue(combatJson.has("y"));
        assertTrue(combatJson.has("open"));
        assertEquals(42, restored.get(Category.COMBAT).getX());
        assertEquals(73, restored.get(Category.COMBAT).getY());
        assertFalse(restored.get(Category.COMBAT).isOpen());
    }

    @Test
    public void missingMalformedAndUnknownColumnsAreHandledSafely() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject columns = new JsonObject();
        JsonObject combat = new JsonObject();
        combat.addProperty("x", "not-an-integer");
        combat.addProperty("y", 37);
        combat.add("open", new JsonArray());
        columns.add("COMBAT", combat);
        JsonObject unknown = new JsonObject();
        unknown.addProperty("x", 999);
        unknown.addProperty("y", 999);
        unknown.addProperty("open", false);
        columns.add("FUTURE_CATEGORY", unknown);
        columns.addProperty("PLAYER", "malformed");
        root.add("columns", columns);

        ClickGuiLayout layout = ClickGuiLayout.fromJson(root);

        assertEquals(8, layout.get(Category.COMBAT).getX());
        assertEquals(37, layout.get(Category.COMBAT).getY());
        assertTrue(layout.get(Category.COMBAT).isOpen());
        assertEquals(176, layout.get(Category.PLAYER).getX());
        assertNotNull(layout.get(Category.SCRIPTS));
    }

    @Test
    public void malformedOrUnsupportedVersionFallsBackToDefaults() {
        JsonObject malformed = new JsonObject();
        malformed.addProperty("version", "bad");
        JsonObject unsupported = new JsonObject();
        unsupported.addProperty("version", 2);

        assertEquals(8, ClickGuiLayout.fromJson(malformed).get(Category.COMBAT).getX());
        assertEquals(8, ClickGuiLayout.fromJson(unsupported).get(Category.COMBAT).getX());
        assertEquals(8, ClickGuiLayout.fromJson(null).get(Category.COMBAT).getX());
    }
}
