package gnu.client.module.modules.player;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_15_2to1_16.packet.ServerboundPackets1_16;
import com.viaversion.viaversion.protocols.v1_20to1_20_2.packet.ServerboundPackets1_20_2;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_9;
import gnu.client.mixin.impl.accessors.IAccessorNetworkManager;
import gnu.client.runtime.mc.Mc;
import io.netty.channel.Channel;
import net.aspw.viaforgeplus.common.CommonViaForgePlus;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;

/**
 * Emit modern Via packets that 1.8 MCP cannot express (offhand swap).
 */
public final class ViaModernPackets {

    private static final int SWAP_ITEM_WITH_OFFHAND = 6;

    private ViaModernPackets() {}

    public static boolean sendSwapWithOffhand() {
        if (!ViaModernGate.supportsOffhandSwap())
            return false;
        UserConnection user = userConnection();
        if (user == null)
            return false;
        CommonViaForgePlus manager = CommonViaForgePlus.getManager();
        if (manager == null)
            return false;
        ProtocolVersion target = manager.getTargetVersion();
        try {
            ServerboundPacketType action = playerActionType(target);
            if (action == null)
                return false;
            PacketWrapper wrapper = PacketWrapper.create(action, user);
            wrapper.write(Types.VAR_INT, SWAP_ITEM_WITH_OFFHAND);
            if (target.newerThanOrEqualTo(ProtocolVersion.v1_14))
                wrapper.write(Types.BLOCK_POSITION1_14, new BlockPosition(0, 0, 0));
            else
                wrapper.write(Types.BLOCK_POSITION1_8, new BlockPosition(0, 0, 0));
            wrapper.write(Types.UNSIGNED_BYTE, (short) 0);
            if (target.newerThanOrEqualTo(ProtocolVersion.v1_19))
                wrapper.write(Types.VAR_INT, 0);
            wrapper.sendToServerRaw();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static ServerboundPacketType playerActionType(ProtocolVersion target) {
        // Prefer enums that match common ViaForge targets; packet id is what matters on the wire.
        if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_2))
            return ServerboundPackets1_20_2.PLAYER_ACTION;
        if (target.newerThanOrEqualTo(ProtocolVersion.v1_16))
            return ServerboundPackets1_16.PLAYER_ACTION;
        return ServerboundPackets1_9.PLAYER_ACTION;
    }

    private static UserConnection userConnection() {
        try {
            EntityPlayerSP player = Mc.player();
            if (player == null || player.sendQueue == null)
                return null;
            NetHandlerPlayClient nh = player.sendQueue;
            NetworkManager nm = nh.getNetworkManager();
            if (!(nm instanceof IAccessorNetworkManager))
                return null;
            Channel channel = ((IAccessorNetworkManager) nm).gnu$getChannel();
            if (channel == null)
                return null;
            return channel.attr(CommonViaForgePlus.LOCAL_VIA_USER).get();
        } catch (Throwable t) {
            return null;
        }
    }
}
