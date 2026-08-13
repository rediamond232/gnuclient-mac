package gnu.client.module.modules.player;

import net.aspw.viaforgeplus.common.CommonViaForgePlus;

/**
 * ViaForgePlus modern-protocol gate (same threshold as MixinEntityPlayerSP).
 */
public final class ViaModernGate {

    private ViaModernGate() {}

    /** {@code true} when ViaForgePlus target protocol &gt; 47 (1.9+). */
    public static boolean isViaModern() {
        try {
            CommonViaForgePlus manager = CommonViaForgePlus.getManager();
            if (manager == null)
                return false;
            return manager.getTargetVersion().getVersion() > 47;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Offhand swap action exists from 1.16+. Food GRIM swap path requires this;
     * 1.9–1.15 Via targets cannot emit {@code SWAP_ITEM_WITH_OFFHAND}.
     */
    public static boolean supportsOffhandSwap() {
        try {
            CommonViaForgePlus manager = CommonViaForgePlus.getManager();
            if (manager == null)
                return false;
            return manager.getTargetVersion().newerThanOrEqualTo(
                    com.viaversion.viaversion.api.protocol.version.ProtocolVersion.v1_16);
        } catch (Throwable t) {
            return false;
        }
    }
}
