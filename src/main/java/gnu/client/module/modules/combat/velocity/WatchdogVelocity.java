package gnu.client.module.modules.combat.velocity;

import gnu.client.event.JumpEvent;
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

public final class WatchdogVelocity extends VelocityMode {

    private volatile boolean active;
    private volatile boolean receiving;
    private int offGroundTicks;
    /** Filled on the Netty I/O thread, drained on the client thread — see {@link HypixelVelocity}. */
    private final Queue<Object> packets = new ConcurrentLinkedQueue<>();
    private int amount;

    public WatchdogVelocity(VelocityModule parent) {
        super("Watchdog", parent);
    }

    @Override
    public void onDisable() {
        packets.clear();
        active = false;
        receiving = false;
        offGroundTicks = 0;
        amount = 0;
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

            if (amount < 1 && !player.onGround && parent.random.nextDouble() < 0.73 && offGroundTicks <= 13) {
                amount++;
                return true;
            }

            if (!player.onGround || !VelocityMove.isMoving()) {
                amount = 0;
                active = true;
                packets.add(velocity);
                return true;
            }

            player.motionY = velocity.getMotionY() / 8000.0D;
            return true;
        }

        if (packet instanceof S32PacketConfirmTransaction) {
            if (active) {
                packets.add(packet);
                return true;
            }
        } else if (packet instanceof S00PacketKeepAlive) {
            if (active)
                return true;
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
    }

    @Override
    public void onJump(JumpEvent event) {
        EntityPlayerSP player = Mc.player();
        if (player == null || !player.onGround || !active)
            return;
        flushPreservingMotion(player);
    }

    @Override
    public void onStrafe(StrafeEvent event) {
        EntityPlayerSP player = Mc.player();
        if (player == null || !active)
            return;

        if (player.onGround && !player.movementInput.jump) {
            flushPreservingMotion(player);
            player.jump();
        } else if (offGroundTicks > 12) {
            flushPreservingMotion(player);
        }
    }

    private void flushPreservingMotion(EntityPlayerSP player) {
        active = false;
        receiving = true;
        double mX = player.motionX;
        double mZ = player.motionZ;
        Object packet;
        while ((packet = packets.poll()) != null) {
            PacketUtil.processInbound(packet);
        }
        player.motionX = mX;
        player.motionZ = mZ;
        receiving = false;
    }
}
