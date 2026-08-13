package gnu.client.module.modules.player;

import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.utility.PacketUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

/**
 * Sole owner of Grim NoSlow Path A slot-spoof C09. Sends after use/block packets, before C03.
 */
public final class GrimNoSlowController implements PacketListener {

    public static final class SkipContext {
        public boolean usingItem;
        public int preGrimPhase;
        /** When false, GRIM switch did not run — ignore stale phase skips. */
        public boolean preGrimAttackAllowed;
        public boolean willGrimAttackThisTick;
        public boolean attackSentThisTick;
        public boolean releaseUseItemThisTick;
        public boolean entityActionSentThisTick;
    }

    private boolean attackSentThisTick;
    private boolean releaseUseItemThisTick;
    private boolean entityActionSentThisTick;

    private boolean spoofingSlot;
    private boolean toggleSlot;
    private int lastSentSlot = -1;

    public void onEnable() {
        PacketEvents.register(this);
    }

    public void onDisable() {
        PacketEvents.unregister(this);
        restoreRealSlotIfNeeded();
        clearSpoofState();
    }

    public void onClientTickStart() {
        attackSentThisTick = false;
        releaseUseItemThisTick = false;
        entityActionSentThisTick = false;
    }

    @Override
    public boolean onSend(Object packet) {
        NoSlowModule ns = NoSlowModule.instance();
        if (ns == null || !ns.isEnabled() || !isGrimActive(ns))
            return false;
        if (PacketHelper.isAttackUseEntity(packet))
            attackSentThisTick = true;
        if (PacketHelper.isReleaseUseItem(packet))
            releaseUseItemThisTick = true;
        if (PacketHelper.isSprintEntityAction(packet) || PacketHelper.isSneakEntityAction(packet))
            entityActionSentThisTick = true;
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        return false;
    }

    public static boolean isGrimActive(NoSlowModule ns) {
        return ns != null && ns.isEnabled() && ns.isSwordGrimActive();
    }

    public static boolean shouldSendSlotSpoof(SkipContext ctx) {
        if (!ctx.usingItem)
            return false;
        if (ctx.attackSentThisTick)
            return false;
        if (ctx.releaseUseItemThisTick)
            return false;
        if (ctx.entityActionSentThisTick)
            return false;
        if (ctx.preGrimAttackAllowed) {
            if (ctx.willGrimAttackThisTick)
                return false;
            if (ctx.preGrimPhase == 0 || ctx.preGrimPhase == 2)
                return false;
            if (ctx.preGrimPhase == 3 || ctx.preGrimPhase == 4)
                return false;
        }
        return true;
    }

    public static int nextSlot(int currentSlot, int swapSlot, boolean toggle, int lastSentSlot) {
        int target = toggle ? swapSlot : currentSlot;
        if (target == lastSentSlot)
            target = (target + 1) % 9;
        return target;
    }

    public void onGrimPreMovement(NoSlowModule ns) {
        if (!isGrimActive(ns)) {
            restoreRealSlotIfNeeded();
            clearSpoofState();
            return;
        }
        if (!Mc.isUsingItem()) {
            restoreRealSlotIfNeeded();
            clearSpoofState();
            return;
        }

        SkipContext ctx = new SkipContext();
        ctx.usingItem = true;
        ctx.preGrimPhase = KillAuraModule.getPreTickGrimPhase();
        ctx.preGrimAttackAllowed = KillAuraModule.getPreTickGrimAttackAllowed();
        ctx.willGrimAttackThisTick = KillAuraModule.willGrimAttackThisTick();
        ctx.attackSentThisTick = attackSentThisTick;
        ctx.releaseUseItemThisTick = releaseUseItemThisTick;
        ctx.entityActionSentThisTick = entityActionSentThisTick;

        if (!shouldSendSlotSpoof(ctx))
            return;

        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;
        int item = player.inventory.currentItem;
        if (!spoofingSlot) {
            spoofingSlot = true;
            toggleSlot = true;
            lastSentSlot = -1;
        }
        int target = nextSlot(item, swapSlot(item), toggleSlot, lastSentSlot);
        sendSlotSpoof(target);
        lastSentSlot = target;
        toggleSlot = !toggleSlot;
    }

    void sendSlotSpoof(int target) {
        PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(target));
    }

    void restoreRealSlotIfNeeded() {
        EntityPlayerSP player = Mc.player();
        if (spoofingSlot && player != null) {
            int item = player.inventory.currentItem;
            if (item != lastSentSlot)
                sendSlotSpoof(item);
        }
    }

    private void clearSpoofState() {
        spoofingSlot = false;
        toggleSlot = false;
        lastSentSlot = -1;
    }

    private static int swapSlot(int currentItem) {
        return currentItem == 0 ? 1 : 0;
    }
}
