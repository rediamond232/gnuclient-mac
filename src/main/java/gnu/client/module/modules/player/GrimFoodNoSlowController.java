package gnu.client.module.modules.player;

import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.utility.PacketUtils;
import net.minecraft.network.Packet;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Via food Grim NoSlow (NoC0F): short confirm-hold + {@code SWAP_ITEM_WITH_OFFHAND},
 * then mixin full-speed only after an inventory slot update proves the desync formed.
 * <p>
 * Target topology: ViaForgePlus ≥ 1.16 → <b>modern</b> Grim server (real offhand NMS).
 * On 1.8 NMS backends Via cancels status 6 → no slot update → SWAP timeout → TEARDOWN
 * without claiming full speed (no NoSlow spam / BadPacketsN from false full-speed).
 * <p>
 * Never uses C09 Path A (that triggers {@code resetItemUsage} and cancels the consumable).
 */
public final class GrimFoodNoSlowController implements PacketListener {

    private final GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
    private final Queue<Object> heldConfirms = new ArrayDeque<Object>();
    private int swapTicks;
    private int idleTicks;
    private boolean didSwap;

    public void onEnable() {
        PacketEvents.register(this);
    }

    public void onDisable() {
        if (fsm.state() != GrimFoodNoSlowFsm.State.NONE)
            fsm.onForceTeardown();
        finishTeardown();
        PacketEvents.unregister(this);
    }

    public void onClientTickStart() {
        // Swap / idle counters advanced in onTick.
    }

    public boolean shouldFullSpeed() {
        return fsm.shouldFullSpeed();
    }

    public void onTick(NoSlowModule ns) {
        if (ns == null || !ns.isEnabled() || !ns.isFoodGrimMode()) {
            if (fsm.state() != GrimFoodNoSlowFsm.State.NONE)
                forceTeardown();
            return;
        }
        if (!ViaModernGate.supportsOffhandSwap()) {
            if (fsm.state() != GrimFoodNoSlowFsm.State.NONE)
                forceTeardown();
            return;
        }

        if (fsm.state() == GrimFoodNoSlowFsm.State.NONE && Mc.isUsingItem() && ns.isFoodGrimSelected()) {
            fsm.onStartUse(true, true, oppositeHandUsable());
        }

        if (fsm.state() == GrimFoodNoSlowFsm.State.SWAP) {
            if (fsm.consumeSendSwap()) {
                didSwap = ViaModernPackets.sendSwapWithOffhand();
                if (!didSwap) {
                    fsm.onSwapTimeout();
                } else {
                    swapTicks = 0;
                }
            } else {
                swapTicks++;
                if (swapTicks > GrimFoodNoSlowFsm.SWAP_TIMEOUT_TICKS)
                    fsm.onSwapTimeout();
            }
        }

        if (fsm.state() == GrimFoodNoSlowFsm.State.EATING) {
            if (Mc.isUsingItem())
                idleTicks = 0;
            else
                idleTicks++;
            fsm.onTickEating(Mc.isUsingItem(), idleTicks);
        }

        if (fsm.state() == GrimFoodNoSlowFsm.State.TEARDOWN)
            finishTeardown();
    }

    @Override
    public boolean onSend(Object packet) {
        if (!fsm.shouldHoldConfirms())
            return false;
        if (!isHoldableConfirm(packet))
            return false;
        heldConfirms.add(packet);
        fsm.onConfirmHeld();
        return true; // cancel send — flush on TEARDOWN
    }

    @Override
    public boolean onReceive(Object packet) {
        if (fsm.state() != GrimFoodNoSlowFsm.State.SWAP)
            return false;
        if (!PacketHelper.isInventorySlotUpdate(packet))
            return false;
        fsm.onSwapSlotUpdate();
        swapTicks = 0;
        return false;
    }

    private void forceTeardown() {
        fsm.onForceTeardown();
        finishTeardown();
    }

    private void finishTeardown() {
        while (!heldConfirms.isEmpty())
            flushHeld(heldConfirms.poll());
        if (didSwap)
            ViaModernPackets.sendSwapWithOffhand();
        didSwap = false;
        swapTicks = 0;
        idleTicks = 0;
        fsm.afterTeardownComplete();
    }

    private static void flushHeld(Object packet) {
        if (packet instanceof Packet)
            PacketUtils.sendPacketNoEvent((Packet) packet);
    }

    private static boolean isHoldableConfirm(Object packet) {
        return PacketHelper.isClientConfirmTransaction(packet)
                || PacketHelper.isClientPingPong(packet);
    }

    /**
     * 1.8 client has no native offhand stack. Without a Via offhand peek, treat as
     * not usable so setup can arm (LB aborts only when opposite hand is also eat/drink).
     */
    static boolean oppositeHandUsable() {
        return false;
    }
}
