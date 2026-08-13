package gnu.client.module.modules.network;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.InboundLagCoordinator;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.OutboundLagQueue;
import gnu.client.runtime.packet.PacketUtil;
import gnu.client.utility.CombatTargeting;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;

/**
 * raven-bS {@code KnockbackDelay} — uniform inbound freeze ({@code UnifiedLagHandler} semantics).
 *
 * <p>While a session is active, <em>all</em> inbound packets are queued (including self
 * {@code S12} and target {@code S14/S18}). Aged packets batch-release each tick via
 * {@link OutboundLagQueue#releaseExpired}. Session ends when raven conditions fail or on
 * {@code S08}. No OpenMyau-style selective pass-through.
 */
public final class KnockbackDelayModule extends Module implements PacketListener {

    private final SliderSetting distanceToTarget = addSetting(new SliderSetting("Distance", 6.0f, 3.0f, 12.0f));
    private final SliderSetting chance = addSetting(new SliderSetting("Chance", 100.0f, 0.0f, 100.0f));
    private final SliderSetting maximumDelay = addSetting(new SliderSetting("Maximum delay", 200.0f, 50.0f, 1000.0f));
    private final BoolSetting inAir = addSetting(new BoolSetting("In air", true));
    private final BoolSetting lookingAtPlayer = addSetting(new BoolSetting("Looking at player", false));
    private final BoolSetting requireLeftMouse = addSetting(new BoolSetting("Require LMB", false));
    private final BoolSetting flushLagrangeOnKb = addSetting(new BoolSetting("Flush Lagrange on KB", false));

    private final OutboundLagQueue inbound = new OutboundLagQueue();
    private boolean sessionActive;

    public KnockbackDelayModule() {
        super("KnockbackDelay", "Delay knockback packets", Category.COMBAT);
    }

    public static boolean isOwningInboundQueue() {
        return InboundLagCoordinator.knockbackDelayOwns();
    }

    public static boolean isBlockingBacktrack() {
        KnockbackDelayModule kd = activeInstance();
        return kd != null && kd.sessionActive;
    }

    /**
     * raven {@code ReceivePacketEvent} HIGHEST — must run before Velocity (default 0% H
     * cancels self S12) so a trade knockback can still open a delay session.
     */
    @Override
    public int sendPriority() {
        return 100;
    }

    private static KnockbackDelayModule activeInstance() {
        Module mod = ModuleManager.INSTANCE.getModule("KnockbackDelay");
        if (mod instanceof KnockbackDelayModule && mod.isEnabled())
            return (KnockbackDelayModule) mod;
        return null;
    }

    @Override
    public void onEnable() {
        inbound.clear();
        inbound.deactivate();
        sessionActive = false;
        InboundLagCoordinator.forceReleaseAll();
        flushLagrangeIfEnabled();
        // Re-register so priority 100 wins even if Velocity was enabled first.
        PacketEvents.unregister(this);
        PacketEvents.register(this);
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        flushInboundAndClear();
    }

    @Override
    public void onTick() {
        if (!isEnabled())
            return;

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null || player.isDead) {
            flushInboundAndClear();
            return;
        }

        if (!sessionActive)
            return;

        if (conditionsFailureReason(player, world) != null) {
            flushInboundAndClear();
            return;
        }

        inbound.releaseExpired(activeDelayMs(), PacketUtil::processInbound);
    }

    @Override
    public boolean onSend(Object packet) {
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!isEnabled() || PacketUtil.isDispatching())
            return false;

        if (PacketHelper.isPlayerPosLook(packet)) {
            flushInboundAndClear();
            return false;
        }

        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();

        if (PacketHelper.isEntityVelocity(packet) && player != null
                && PacketHelper.velocityEntityId(packet) == Mc.entityId(player)) {
            flushLagrangeIfEnabled();
            if (!sessionActive) {
                if (conditionsFailureReason(player, world) != null)
                    return false;
                if (chance.getValue() < 100.0f && Math.random() * 100.0 >= chance.getValue())
                    return false;
                startSession();
            }
        }

        if (!sessionActive)
            return false;

        inbound.offer(packet);
        return true;
    }

    private void startSession() {
        inbound.activate();
        sessionActive = true;
        InboundLagCoordinator.tryAcquire(InboundLagCoordinator.Owner.KNOCKBACK_DELAY);
    }

    /**
     * Raven {@code KnockbackDelay.conditionsFailureReason} — uses
     * {@link CombatTargeting} (no hurtTime skip; trading hits must still count as a target).
     */
    private String conditionsFailureReason(EntityPlayerSP player, WorldClient world) {
        if (player == null || world == null)
            return "null";

        double maxSq = distanceToTarget.getValue() * distanceToTarget.getValue();
        if (CombatTargeting.findTarget(maxSq) == null)
            return "no target";

        // "In air" ON = require airborne (raven parity). OFF = allow ground.
        if (inAir.getValue() && player.onGround)
            return "on ground";

        if (lookingAtPlayer.getValue() && CombatTargeting.getMouseOverTarget(maxSq) == null)
            return "not looking at player";

        if (requireLeftMouse.getValue() && !ClientBootstrap.isLeftMouseDown())
            return "lmb";

        return null;
    }

    private long activeDelayMs() {
        return maximumDelay.getValue().longValue();
    }

    private void flushInboundAndClear() {
        inbound.deactivate();
        sessionActive = false;
        InboundLagCoordinator.release(InboundLagCoordinator.Owner.KNOCKBACK_DELAY);
        inbound.drainAll(PacketUtil::processInbound);
    }

    private void flushLagrangeIfEnabled() {
        if (!flushLagrangeOnKb.getValue())
            return;
        Module lag = ModuleManager.INSTANCE.getModule("Lagrange");
        if (lag instanceof LagrangeModule && lag.isEnabled())
            ((LagrangeModule) lag).flushQueueNow();
    }

    @Override
    public String[] getSuffix() {
        return new String[] { ((long) (float) maximumDelay.getValue()) + "ms" };
    }
}
