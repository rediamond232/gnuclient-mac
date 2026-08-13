package gnu.client.runtime;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlinkManagerTest {
    private BlinkManager mgr;

    @Before
    public void setUp() {
        mgr = new BlinkManager();
    }

    @Test
    public void releaseWrongOwnerReturnsFalseAndKeepsOwner() {
        assertTrue(mgr.setBlinkState(true, BlinkModules.AUTO_BLOCK));
        assertEquals(BlinkModules.AUTO_BLOCK, mgr.getBlinkingModule());
        assertFalse(mgr.setBlinkState(false, BlinkModules.NO_SLOW));
        assertEquals(BlinkModules.AUTO_BLOCK, mgr.getBlinkingModule());
        assertTrue(mgr.isBlinking());
    }

    @Test
    public void releaseCorrectOwnerClearsBlinking() {
        mgr.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        assertTrue(mgr.setBlinkState(false, BlinkModules.AUTO_BLOCK));
        // wsamiaw: empty queue leaves blinkModule set, only clears blinking flag
        assertFalse(mgr.isBlinking());
    }

    @Test
    public void releaseWithQueuedPacketsClearsOwner() {
        mgr.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        mgr.offerPacket(new Object());
        assertTrue(mgr.setBlinkState(false, BlinkModules.AUTO_BLOCK));
        assertEquals(BlinkModules.NONE, mgr.getBlinkingModule());
        assertFalse(mgr.isBlinking());
        assertEquals(0, mgr.queuedCount());
    }

    @Test
    public void noneModuleRejected() {
        assertFalse(mgr.setBlinkState(true, BlinkModules.NONE));
    }

    @Test
    public void offerIgnoredWhenNotBlinking() {
        assertFalse(mgr.offerPacket(new Object()));
        assertEquals(0, mgr.queuedCount());
    }

    @Test
    public void offerQueuesWhenBlinking() {
        mgr.setBlinkState(true, BlinkModules.AUTO_BLOCK);
        assertTrue(mgr.offerPacket(new Object()));
        assertEquals(1, mgr.queuedCount());
    }
}
