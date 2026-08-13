package gnu.client.module.modules.combat.velocity;

import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.modules.combat.VelocityModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.utility.PacketUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class GrimReduceVelocity extends VelocityMode {

    public GrimReduceVelocity(VelocityModule parent) {
        super("GrimReduce", parent);
    }

    @Override
    public boolean onReceive(Object packet) {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        if (!(packet instanceof S12PacketEntityVelocity))
            return false;

        S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
        if (vel.getEntityID() != player.getEntityId())
            return false;

        Entity target = getClosestEntity();
        if (target == null)
            return false;
        // MultiInteractA: do not inject a second ATTACK while KillAura already owns a target
        // in this flying window (often a different entity than KA's Switch target).
        if (KillAuraModule.getCurrentTarget() != null)
            return false;
        // Grim reduce: C02 ATTACK then C0A swing (C02 > C0A) — both required.
        for (Packet<?> outbound : reduceAttackPackets(target))
            PacketUtils.sendPacketNoEvent(outbound);
        return false;
    }

    /** Ordered outbound pair for GrimReduce: C02 then C0A. */
    static List<Packet<?>> reduceAttackPackets(Entity target) {
        return Arrays.asList(
                new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK),
                new C0APacketAnimation());
    }

    /** Simple names in the order {@link #reduceAttackPackets} sends (C02 > C0A). */
    static String[] reduceAttackPacketOrder() {
        return new String[] {
                C02PacketUseEntity.class.getSimpleName(),
                C0APacketAnimation.class.getSimpleName()
        };
    }

    @Override
    public void onUpdate(boolean pre) {
        if (!pre)
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null || player.ticksExisted <= 20)
            return;

        if (player.hurtTime > 0) {
            if (isNearBlock()) {
                player.motionX *= 0.02;
                player.motionZ *= 0.02;
            }

            if (player.hurtTime == 9 && player.getHeldItem() != null
                    && player.getHeldItem().getItem() instanceof ItemSword) {
                PacketUtils.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(player.getHeldItem()));
            }
        }
    }

    private Entity getClosestEntity() {
        EntityPlayerSP player = Mc.player();
        if (player == null || mc.theWorld == null)
            return null;

        return mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityLivingBase && e != player && player.getDistanceToEntity(e) <= 6.0f)
                .min(Comparator.comparingDouble(player::getDistanceToEntity))
                .orElse(null);
    }

    private boolean isNearBlock() {
        EntityPlayerSP player = Mc.player();
        if (player == null || mc.theWorld == null)
            return false;
        AxisAlignedBB bb = player.getEntityBoundingBox().expand(0.5, 0.0, 0.5);
        return !mc.theWorld.getCollidingBoundingBoxes(player, bb).isEmpty();
    }
}
