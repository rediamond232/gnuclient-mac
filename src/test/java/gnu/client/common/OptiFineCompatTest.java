package gnu.client.common;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OptiFineCompatTest {

    @Test
    public void detectDoesNotThrow() {
        // Presence depends on the classpath; we only assert the API is safe to call.
        OptiFineCompat.isPresent();
        OptiFineCompat.warnUnsupportedIfPresent();
        OptiFineCompat.warnUnsupportedIfPresent(); // second call is a no-op
        assertTrue(true);
    }

    @Test
    public void absentOnNormalUnitTestClasspath() {
        // Unit tests do not ship OptiFine.
        assertFalse(OptiFineCompat.isPresent());
    }
}
