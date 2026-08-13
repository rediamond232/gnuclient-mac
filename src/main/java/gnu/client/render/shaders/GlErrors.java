package gnu.client.render.shaders;

import gnu.client.common.GnuLog;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Set;

/**
 * Logs the first OpenGL error at a named site. Apple GL 2.1 reports
 * {@code GL_INVALID_OPERATION} (1282) one frame later as "Post render".
 */
final class GlErrors {

    private static final Set<String> logged = new HashSet<String>();

    private GlErrors() {}

    static void check(String site) {
        int err = GL11.glGetError();
        if (err == GL11.GL_NO_ERROR) {
            return;
        }
        if (logged.add(site)) {
            GnuLog.log("Shaders: GL error " + err + " at " + site);
        }
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
    }
}
