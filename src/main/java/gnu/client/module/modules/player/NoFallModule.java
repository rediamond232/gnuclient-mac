package gnu.client.module.modules.player;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.utility.PacketUtils;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.Arrays;

public final class NoFallModule extends Module implements PacketListener {

    private static final int SPOOF = 0;
    private static final int GRIM = 1;
    private static final int GRIM_SNAP = 2;

    private final ModeSetting mode = addSetting(new ModeSetting("Mode", SPOOF,
        Arrays.asList("Spoof", "Grim", "GrimSnap")));

    private final SliderSetting minimumFallDistance = addSetting(
        new SliderSetting("MinFallDist", 0.0f, 0.0f, 10.0f, 0.5f));

    private boolean grimTriggered;

    public NoFallModule() {
        super("NoFall", "Prevents fall damage", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        grimTriggered = false;
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        grimTriggered = false;
    }

    @Override
    public boolean onSend(Object packet) {
        if (!PacketHelper.isPlayerMovement(packet))
            return false;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;

        if (mode.getIndex() == SPOOF) {
            if (!player.onGround && player.motionY < -0.5
                    && player.fallDistance >= minimumFallDistance.getValue())
                PacketHelper.c03SetOnGround(packet, true);
            return false;
        }

        if (mode.getIndex() == GRIM) {
            if (player.onGround) {
                if (grimTriggered) {
                    PacketUtils.sendPacketNoEvent(new C0BPacketEntityAction(
                        player, C0BPacketEntityAction.Action.STOP_SNEAKING));
                }
                grimTriggered = false;
                return true;
            }
            if (grimTriggered || player.motionY >= 0.0
                    || player.fallDistance < minimumFallDistance.getValue())
                return true;

            double groundY = findGroundY(player);
            if (Double.isNaN(groundY) || player.posY - groundY <= 0.0
                    || player.posY - groundY > 2.0)
                return true;

            PacketUtils.sendPacketNoEvent(new C0BPacketEntityAction(
                player, C0BPacketEntityAction.Action.START_SNEAKING));
            PacketHelper.c03SetOnGround(packet, true);
            player.fallDistance = 0.0f;
            player.motionY = 0;
            grimTriggered = false;
            return false;
        }

        if (mode.getIndex() == GRIM_SNAP) {
            if (!PacketHelper.c03HasPosition(packet))
                return false;
            if (player.motionY >= 0.0
                    || player.fallDistance < minimumFallDistance.getValue())
                return false;

            double groundY = findGroundY(player);
            if (Double.isNaN(groundY))
                return false;

            double distToGround = player.posY - groundY;

            if (distToGround <= 0.0 || distToGround > 2.0)
                return false;

            PacketHelper.c03SetPosition(packet, player.posX, groundY, player.posZ);
            PacketHelper.c03SetOnGround(packet, true);
            return false;
        }

        return false;
    }

    private double findGroundY(EntityPlayerSP player) {
        World world = player.worldObj;
        if (world == null)
            return Double.NaN;

        int blockX = MathHelper.floor_double(player.posX);
        int feetBlockY = MathHelper.floor_double(player.posY);
        int blockZ = MathHelper.floor_double(player.posZ);

        for (int y = feetBlockY; y >= 0; y--) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);
            Block block = world.getBlockState(pos).getBlock();
            if (block.isFullBlock() || block.getMaterial().isSolid())
                return y + 1;
        }
        return Double.NaN;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }
}
