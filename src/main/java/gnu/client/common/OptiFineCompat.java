package gnu.client.common;

/**
 * OptiFine detection only.
 *
 * <p>GNUClient no longer coexists with OptiFine: the custom terrain path and performance
 * mixins own vanilla render bytecode. When OptiFine classes are present we log once that
 * the combination is unsupported (crashes / visual corruption are expected).
 */
public final class OptiFineCompat {

    private static final boolean PRESENT = detect();
    private static boolean warned;

    private OptiFineCompat() {}

    /** True if OptiFine's coremod classes are present on the classpath. */
    public static boolean isPresent() {
        return PRESENT;
    }

    /** Log once at startup / first use that OptiFine is unsupported. */
    public static void warnUnsupportedIfPresent() {
        if (!PRESENT || warned) {
            return;
        }
        warned = true;
        GnuLog.log("Performance: OptiFine detected — unsupported. Custom terrain / render mixins "
                + "own the vanilla path; remove OptiFine to avoid crashes or visual corruption.");
    }

    private static boolean detect() {
        return classExists("net.optifine.Config")
                || classExists("net.optifine.OptiFineClassTransformer");
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, OptiFineCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
