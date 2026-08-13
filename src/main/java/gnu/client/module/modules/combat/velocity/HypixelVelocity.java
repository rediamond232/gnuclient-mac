package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketHelper;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.AxisAlignedBB;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Hypixel KB: delay self {@code S12} (+ {@code S00}/{@code S32}) for 5 ticks, then flush.
 * After flush, while hurt and near a solid, dampen horizontal (collision absorb — not jump/sprint).
 * Setback {@code S08}→{@code S12} is never delayed (grace after S08).
 */
public final class HypixelVelocity extends VelocityMode {

    static final int DELAY_TICKS = 5;
    /** Ticks after {@code S08} where self {@code S12} is passed through (setback velocity). */
    static final int SETBACK_GRACE_TICKS = 3;
    /** Horizontal scale while hurt and colliding with a nearby solid. */
    static final double WALL_ABSORB = 0.35D;

    private final Queue<Packet<INetHandlerPlayClient>> packets = new ConcurrentLinkedQueue<>();

    private volatile boolean delayActive;
    private volatile int timeout;
    private volatile int setbackGraceTicks;
    /** Hurt ticks left to attempt wall absorb after a delayed flush. */
    private int absorbTicks;

    public HypixelVelocity(VelocityModule parent) {
        super("Hypixel", parent);
    }

    /**
     * Delay every self {@code S12} except during setback grace.
     * Do not require {@code hurtTime} — velocity often arrives before hurt is applied.
     */
    static boolean shouldDelaySelfVelocity(boolean setbackGrace) {
        return !setbackGrace;
    }

    static boolean shouldQueue(boolean delayActive, boolean delayableSelfVelocity, boolean keepaliveOrTransaction) {
        if (delayableSelfVelocity)
            return true;
        return delayActive && keepaliveOrTransaction;
    }

    static boolean shouldWallAbsorb(int absorbTicksLeft, int hurtTime, boolean nearSolid) {
        return absorbTicksLeft > 0 && hurtTime > 0 && nearSolid;
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null || mc.theWorld == null)
            return false;

        if (PacketHelper.isPlayerPosLook(packet)) {
            abortHold();
            setbackGraceTicks = SETBACK_GRACE_TICKS;
            return false;
        }

        boolean delayableSelfVelocity = false;
        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
            if (vel.getEntityID() != player.getEntityId())
                return false;
            delayableSelfVelocity = shouldDelaySelfVelocity(setbackGraceTicks > 0);
            if (!delayableSelfVelocity)
                return false;
        }

        boolean keepaliveOrTransaction = packet instanceof S00PacketKeepAlive
                || packet instanceof S32PacketConfirmTransaction;

        if (!shouldQueue(delayActive, delayableSelfVelocity, keepaliveOrTransaction))
            return false;

        @SuppressWarnings("unchecked")
        Packet<INetHandlerPlayClient> p = (Packet<INetHandlerPlayClient>) packet;
        packets.add(p);
        if (delayableSelfVelocity) {
            if (!delayActive)
                timeout = 0;
            delayActive = true;
        }
        return true;
    }

    @Override
    public void onUpdate(boolean pre) {
        if (pre) {
            if (setbackGraceTicks > 0)
                setbackGraceTicks--;
            EntityPlayerSP player = Mc.player();
            if (player != null && shouldWallAbsorb(absorbTicks, player.hurtTime, isNearSolid(player))) {
                player.motionX *= WALL_ABSORB;
                player.motionZ *= WALL_ABSORB;
            }
            if (absorbTicks > 0)
                absorbTicks--;
            return;
        }

        if (!delayActive)
            return;

        if (++timeout >= DELAY_TICKS)
            flush();
    }

    private void flush() {
        NetHandlerPlayClient netHandler = Mc.netHandler();
        if (netHandler == null) {
            abortHold();
            return;
        }

        Packet<INetHandlerPlayClient> p;
        while ((p = packets.poll()) != null) {
            try {
                p.processPacket(netHandler);
            } catch (Exception ignored) {
            }
        }
        delayActive = false;
        timeout = 0;
        // Match remaining hurt window roughly — wall absorb only while still hurt.
        absorbTicks = 10;
    }

    private boolean isNearSolid(EntityPlayerSP player) {
        if (mc.theWorld == null)
            return false;
        AxisAlignedBB bb = player.getEntityBoundingBox().expand(0.35D, 0.0D, 0.35D);
        return !mc.theWorld.getCollidingBoundingBoxes(player, bb).isEmpty();
    }

    private void abortHold() {
        packets.clear();
        delayActive = false;
        timeout = 0;
    }

    @Override
    public void onDisable() {
        abortHold();
        setbackGraceTicks = 0;
        absorbTicks = 0;
    }
}
