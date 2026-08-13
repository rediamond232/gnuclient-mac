package gnu.client.runtime;

import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.PacketUtil;
import gnu.client.utility.PacketUtils;
import net.minecraft.network.Packet;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Shared outbound blink ownership — matched to wsamiaw {@code BlinkManager}.
 * AUTO_BLOCK used by KillAura blink modes; NO_SLOW reserved.
 *
 * <p>Critical wsamiaw behavior: while blinking, <b>C03 movement is held</b>
 * (only keepalive/chat and the first empty-queue C0F are exempt). Flush uses
 * {@link PacketUtils#sendPacketNoEvent} so releases bypass the send-event bus.
 */
public final class BlinkManager implements PacketListener {

    public static final BlinkManager INSTANCE = new BlinkManager();

    private BlinkModules blinkModule = BlinkModules.NONE;
    private boolean blinking;
    private final Deque<Object> blinkedPackets = new ArrayDeque<>();
    private Consumer<Object> flushSender = BlinkManager::flushPacketNoEvent;

    public BlinkManager() {}

    /** Test hook — default wsamiaw-style no-event flush. */
    public void setFlushSender(Consumer<Object> sender) {
        this.flushSender = sender != null ? sender : BlinkManager::flushPacketNoEvent;
    }

    private static void flushPacketNoEvent(Object packet) {
        if (packet instanceof Packet)
            PacketUtils.sendPacketNoEvent((Packet) packet);
    }

    /**
     * wsamiaw offer: hold everything except keepalive/chat; when the queue is
     * empty, do not hold C0F. Movement (C03) is held — unlike older OpenMyau ports.
     */
    public boolean offerPacket(Object packet) {
        if (blinkModule == BlinkModules.NONE || packet == null)
            return false;
        if (PacketHelper.isKeepAlive(packet) || PacketHelper.isChat(packet))
            return false;
        if (blinkedPackets.isEmpty() && PacketHelper.isClientConfirmTransaction(packet))
            return false;
        blinkedPackets.offer(packet);
        return true;
    }

    public boolean setBlinkState(boolean state, BlinkModules module) {
        if (module == null || module == BlinkModules.NONE)
            return false;
        if (state) {
            blinkModule = module;
            blinking = true;
            return true;
        }
        if (blinkModule != module)
            return false;
        blinking = false;
        // wsamiaw: empty queue returns without clearing blinkModule.
        if (blinkedPackets.isEmpty())
            return true;
        while (!blinkedPackets.isEmpty()) {
            Object p = blinkedPackets.poll();
            if (p != null)
                flushSender.accept(p);
        }
        blinkModule = BlinkModules.NONE;
        return true;
    }

    public BlinkModules getBlinkingModule() {
        return blinkModule;
    }

    public boolean isBlinking() {
        return blinking;
    }

    public int queuedCount() {
        return blinkedPackets.size();
    }

    /** wsamiaw {@code countMovement} — C03s currently held. */
    public long countMovement() {
        long n = 0L;
        for (Object p : blinkedPackets) {
            if (PacketHelper.isPlayerMovement(p))
                n++;
        }
        return n;
    }

    @Override
    public boolean onSend(Object packet) {
        if (PacketUtil.isDispatching() || PacketUtil.consumeFastTrack(packet))
            return false;
        if (!blinking)
            return false;
        return offerPacket(packet);
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    /** Above typical module listeners so AUTO_BLOCK wins when active. */
    @Override
    public int sendPriority() {
        return 100;
    }
}
