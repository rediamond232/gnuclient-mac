package gnu.client.render.graphics;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GraphicsPackRootsTest {

    private static final String PROPS = "optifine/sky/world0/sky1.properties";

    @Test
    public void omittedSourceIsPngBesideProperties() {
        List<String> c = GraphicsPackRoots.textureCandidates(PROPS, null);
        assertEquals("optifine/sky/world0/sky1.png", c.get(0));
    }

    @Test
    public void dotSlashIsPropertiesFolder() {
        List<String> c = GraphicsPackRoots.textureCandidates(PROPS, "./sky1.png");
        assertEquals(1, c.size());
        assertEquals("optifine/sky/world0/sky1.png", c.get(0));
        assertFalse(c.get(0).contains("./"));
    }

    @Test
    public void tildeIsOptifineThenMcpatcher() {
        List<String> c = GraphicsPackRoots.textureCandidates(PROPS, "~/sky/world0/sky1.png");
        assertEquals("optifine/sky/world0/sky1.png", c.get(0));
        assertEquals("mcpatcher/sky/world0/sky1.png", c.get(1));
    }

    @Test
    public void bareFilenameTriesPropertiesFolderFirst() {
        List<String> c = GraphicsPackRoots.textureCandidates(PROPS, "sky1.png");
        assertEquals("optifine/sky/world0/sky1.png", c.get(0));
        assertTrue(c.contains("optifine/sky1.png"));
        assertTrue(c.contains("mcpatcher/sky1.png"));
    }

    @Test
    public void slashedPathTriesOptifineRoot() {
        List<String> c = GraphicsPackRoots.textureCandidates(PROPS, "sky/world0/sky1.png");
        assertEquals("optifine/sky/world0/sky1.png", c.get(0));
        assertEquals("mcpatcher/sky/world0/sky1.png", c.get(1));
    }

    @Test
    public void mcpatcherPropertiesKeepRelativeDotSlash() {
        List<String> c = GraphicsPackRoots.textureCandidates(
                "mcpatcher/sky/world0/sky0.properties", "./sky0.png");
        assertEquals("mcpatcher/sky/world0/sky0.png", c.get(0));
    }

    @Test
    public void addsPngWhenExtensionMissing() {
        List<String> c = GraphicsPackRoots.textureCandidates(PROPS, "./sky1");
        assertEquals("optifine/sky/world0/sky1.png", c.get(0));
    }
}
