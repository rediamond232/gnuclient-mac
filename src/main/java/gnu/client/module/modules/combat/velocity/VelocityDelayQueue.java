package gnu.client.module.modules.combat.velocity;

import gnu.client.runtime.packet.PacketUtil;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Local S12 hold for OMDelay — not Blink/Lagrange. */
public final class VelocityDelayQueue {

    /**
     * {@link #offer} is called from the Netty I/O thread (packet receive) while
     * {@link #stopDelayAndFlush} drains on the client thread, so this cannot be an
     * {@code ArrayDeque}.
     */
    private final Deque<Object> held = new ConcurrentLinkedDeque<>();

    private volatile boolean delaying;
    private volatile long delayStartTick = -1L;

    public void clear() {
        held.clear();
        delaying = false;
        delayStartTick = -1L;
    }

    public boolean isDelaying() {
        return delaying;
    }

    public void startDelay(long tickNow) {
        delaying = true;
        delayStartTick = tickNow;
    }

    /** Flush via {@link PacketUtil#processInbound} — project inbound re-inject (no receivePacket API). */
    public void stopDelayAndFlush() {
        delaying = false;
        delayStartTick = -1L;
        while (!held.isEmpty()) {
            Object pkt = held.pollFirst();
            if (pkt != null)
                PacketUtil.processInbound(pkt);
        }
    }

    public long ticksHeld(long tickNow) {
        if (!delaying || delayStartTick < 0L)
            return 0L;
        return Math.max(0L, tickNow - delayStartTick);
    }

    public void offer(Object packet) {
        held.addLast(packet);
    }
}
