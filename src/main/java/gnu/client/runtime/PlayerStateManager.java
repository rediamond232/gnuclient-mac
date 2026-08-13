package gnu.client.runtime;

import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.PacketEvents;

/**
 * wsamiaw {@code PlayerStateManager} — tracks outbound combat/place state until the
 * next C03 clears it. KillAura autoblock uses {@link #digging}/{@link #placing}
 * the same way as the reference {@code performAttack} gate.
 */
public final class PlayerStateManager implements PacketListener {

    public static final PlayerStateManager INSTANCE = new PlayerStateManager();

    public boolean attacking;
    public boolean digging;
    public boolean placing;
    public boolean swapping;
    public boolean swinging;

    private PlayerStateManager() {}

    public static void register() {
        PacketEvents.register(INSTANCE);
    }

    public void handlePacket(Object packet) {
        if (PacketHelper.isUseEntity(packet))
            attacking = true;
        // wsamiaw: any C07 → digging (includes RELEASE_USE_ITEM)
        if (PacketHelper.isPlayerDigging(packet))
            digging = true;
        // wsamiaw: any C08 → placing (includes sword use-item)
        if (PacketHelper.isBlockPlacement(packet))
            placing = true;
        if (PacketHelper.isHeldItemChange(packet))
            swapping = true;
        if (PacketHelper.isAnimationPacket(packet))
            swinging = true;
        if (PacketHelper.isPlayerMovement(packet)) {
            attacking = false;
            digging = false;
            placing = false;
            swapping = false;
            swinging = false;
        }
    }

    @Override
    public boolean onSend(Object packet) {
        handlePacket(packet);
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    /** Run before BlinkManager so state is updated even when blink cancels the send. */
    @Override
    public int sendPriority() {
        return 1000;
    }
}
