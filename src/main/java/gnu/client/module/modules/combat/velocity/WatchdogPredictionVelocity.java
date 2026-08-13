package gnu.client.module.modules.combat.velocity;

import gnu.client.event.StrafeEvent;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketUtil;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class WatchdogPredictionVelocity extends VelocityMode {

    private volatile boolean active;
    private volatile boolean receiving;
    private int offGroundTicks;
    /** Filled on the Netty I/O thread, drained on the client thread — see {@link HypixelVelocity}. */
    private final Queue<Object> packets = new ConcurrentLinkedQueue<>();
    private volatile float desiredYaw;
    private volatile double velX;
    private volatile double velZ;

    public WatchdogPredictionVelocity(VelocityModule parent) {
        super("WatchdogPrediction", parent);
    }

    @Override
    public void onDisable() {
        packets.clear();
        active = false;
        receiving = false;
        offGroundTicks = 0;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (receiving)
            return false;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
            if (velocity.getEntityID() != player.getEntityId())
                return false;

            active = true;
            double vX = velocity.getMotionX() / 8000.0D;
            double vZ = velocity.getMotionZ() / 8000.0D;
            desiredYaw = (float) Math.toDegrees(Math.atan2(vZ, vX));

            if (desiredYaw < -180.0F)
                desiredYaw += 360.0F;
            if (desiredYaw > 180.0F)
                desiredYaw -= 360.0F;

            packets.add(velocity);
            velX = vX;
            velZ = vZ;
            return true;
        }

        if (packet instanceof S32PacketConfirmTransaction) {
            if (active) {
                packets.add(packet);
                return true;
            }
        } else if (packet instanceof S00PacketKeepAlive) {
            if (active) {
                if (player.ticksExisted % 3 == 0)
                    packets.add(packet);
                return true;
            }
        }

        return false;
    }

    @Override
    public void onUpdate(boolean pre) {
        if (!pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        if (player.onGround) {
            offGroundTicks = 0;
        } else {
            offGroundTicks++;
        }

        if (active && player.onGround || receiving) {
            player.motionX = -velX;
            player.motionZ = -velZ;
        } else if (player.onGround && player.hurtTime > 7) {
            player.motionX *= -velX;
            player.motionZ *= -velZ;
            player.jump();
        }
    }

    @Override
    public void onStrafe(StrafeEvent event) {
        if (!active)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        float playerYaw = player.rotationYaw % 360.0F;
        if (playerYaw < -180.0F)
            playerYaw += 360.0F;
        if (playerYaw > 180.0F)
            playerYaw -= 360.0F;

        float yawDifference = Math.abs(playerYaw - desiredYaw);
        float leeway = 20.0F;

        if (yawDifference <= leeway || yawDifference >= (360.0F - leeway)) {
            flushPackets();
            offGroundTicks = 0;
        } else if (player.onGround) {
            player.motionX *= -1.0D;
            player.motionZ *= -1.0D;
            flushPackets();
        } else if (offGroundTicks > 12) {
            flushPackets();
            player.jump();
            player.motionX *= 0.6D;
            player.motionZ *= 0.6D;
        }
    }

    private void flushPackets() {
        active = false;
        receiving = true;
        Object packet;
        while ((packet = packets.poll()) != null) {
            PacketUtil.processInbound(packet);
        }
        receiving = false;
    }
}
