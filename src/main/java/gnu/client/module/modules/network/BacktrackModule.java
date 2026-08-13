package gnu.client.module.modules.network;

import gnu.client.mixin.RealPosAccess;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketHelper;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.runtime.packet.PacketUtil;
import gnu.client.runtime.packet.InboundLagCoordinator;
import gnu.client.runtime.packet.OutboundLagQueue;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import gnu.client.ui.UiFont;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.MathHelper;

/**
 * gnuclient-recode BackTrack — attack-driven, players-only, faithful to the original.
 *
 * <p>BackTrack tracks the entity you last attacked (from the outgoing C02 ATTACK packet) and
 * only ever backtracks players — it never pulls KillAura's target or holds packets for mobs,
 * animals, villagers or armor stands. Target {@code S14}/{@code S18} packets are queued only
 * within the post-attack window ({@code HurtTime} ms after the initiating hit) and each queued
 * packet is age-released after the rolled [MinDelay, MaxDelay] ms so the entity trails by that
 * lag. When the hold ends, queued packets are replayed via processInbound (vanilla
 * interpolation) rather than a teleport snap. Taking knockback flushes. The ESP draws the
 * smooth true server position.
 */
public final class BacktrackModule extends Module implements PacketListener {

    private final SliderSetting hitRange = addSetting(new SliderSetting("MaxHitRange", 6.0f, 3.0f, 6.0f));
    /** How long (ms) each queued target S14/S18 sits before processInbound. Each hold rolls
     *  a value in [MinDelay, MaxDelay] — e.g. 300-400 rolls 353, so movement lags by 353ms. */
    private final SliderSetting minTime = addSetting(new SliderSetting("MinDelay", 1000.0f, 0.0f, 10000.0f, 10.0f));
    private final SliderSetting maxTime = addSetting(new SliderSetting("MaxDelay", 4000.0f, 0.0f, 10000.0f, 10.0f));
    /** How long (ms) after the initiating hit BackTrack keeps accepting / delaying S14/S18.
     *  When the window ends the queue is flushed to the live server pos in one step. */
    private final SliderSetting hurtTime = addSetting(new SliderSetting("HurtTime", 250.0f, 0.0f, 1000.0f, 10.0f));
    /** When on, hard-flush if the live server position is closer than the delayed entity. */
    private final BoolSetting smartFlush = addSetting(new BoolSetting("Smart Flush", false));
    /** When on, hard-flush if the live server pos is too far sideways from look — avoids
     *  hitting a delayed hitbox while the real body is off-angle (hitbox flags). */
    private final BoolSetting hitboxFix = addSetting(new BoolSetting("Hitbox Fix", false));
    private final BoolSetting esp = addSetting(new BoolSetting("Esp", true));

    /** Max absolute yaw offset (deg) from look to real pos before Hitbox Fix flushes. */
    private static final float HITBOX_FIX_MAX_YAW = 30.0f;

    private final OutboundLagQueue inbound = new OutboundLagQueue();
    /** The entity you last attacked — BackTrack only ever tracks this (players only),
     *  mirroring the original gnuclient BackTrack's attack-packet-driven target. */
    private EntityLivingBase entity;
    private int targetEntityId = -1;
    /** True while the post-attack window is open and target S14/S18 are being delayed. */
    private boolean blockPackets;

    /** Per-hold packet age (ms), randomized between MinDelay and MaxDelay at hold start. */
    private long currentDelay;

    /**
     * Timestamp of the most recent hit on the tracked player. The queuing window stays open
     * for {@code HurtTime} ms after this — refreshed on each attack so KillAura keeps a
     * steady delay trail instead of pulse-freezing every HurtTime.
     */
    private long lastOwnAttackMs = 0L;

    /**
     * Live true server position of the target (plain double world coords), tracked for the
     * ESP from the same S14/S18 packets we intercept. Unlike {@code realPos}, this is NOT
     * frozen during a hold — it shows where the target actually is server-side right now,
     * smoothly interpolated Lagrange-style so the ghost box doesn't snap.
     */
    private double espServerX, espServerY, espServerZ;
    private boolean espServerValid;
    private double espIndFromX, espIndFromY, espIndFromZ;
    private double espIndToX, espIndToY, espIndToZ;
    private boolean espIndValid;
    private long espIndStartMs;
    private static final long ESP_INTERP_MS = 80L;
    private static final double ESP_POS_EPS = 1.0e-6;

    /** True while inbound packets are being held (used by PingFix to keep ping in sync). */
    public boolean isLagging() {
        return isEnabled() && (blockPackets || !inbound.isEmpty());
    }

    public BacktrackModule() {
        super("Back Track", "Hit players at their past position", Category.COMBAT);
    }

    private static RealPosAccess realPos(EntityLivingBase entity) {
        return (RealPosAccess) (Object) entity;
    }

    @Override
    public void onEnable() {
        blockPackets = false;
        inbound.clear();
        inbound.deactivate();
        currentDelay = (long) (float) maxTime.getValue();
        PacketEvents.register(this);
        WorldClient world = Mc.world();
        EntityPlayerSP player = Mc.player();
        if (world != null && player != null) {
            for (Entity e : world.loadedEntityList) {
                if (e instanceof EntityLivingBase) {
                    EntityLivingBase elb = (EntityLivingBase) e;
                    RealPosAccess rp = realPos(elb);
                    rp.setRealPosX(elb.serverPosX);
                    rp.setRealPosY(elb.serverPosY);
                    rp.setRealPosZ(elb.serverPosZ);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        hardFlush();
        entity = null;
        targetEntityId = -1;
        lastOwnAttackMs = 0L;
    }

    @Override
    public void onTick() {
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null) {
            hardFlush();
            entity = null;
            return;
        }

        // Target = the entity you last attacked (set from the outgoing C02 ATTACK packet in
        // onSend), resolved against the current world. BackTrack tracks players only and
        // never pulls KillAura's target — this matches the original gnuclient behavior and
        // avoids holding packets for anything you aren't actually hitting.
        if (entity == null || entity.isDead || entity.getEntityId() != targetEntityId
                || Mc.world().getEntityByID(targetEntityId) != entity) {
            entity = targetEntityId >= 0
                    ? (EntityLivingBase) Mc.world().getEntityByID(targetEntityId)
                    : null;
            if (entity != null && !(entity instanceof EntityPlayer))
                entity = null;
        }

        if (entity == null) {
            hardFlush();
            lastOwnAttackMs = 0L;
            return;
        }

        long now = System.currentTimeMillis();

        // Queuing window = HurtTime ms after the initiating hit. Packet age = currentDelay.
        // Window end / knockback / out-of-range → replay queued packets (no teleport snap).
        boolean inRange = player.getDistanceToEntity(entity) < hitRange.getValue();
        boolean withinAttackWindow = now - lastOwnAttackMs <= (long) (float) hurtTime.getValue();
        boolean takingKnockback = player.hurtTime >= 3 && player.hurtTime <= 9;
        boolean shouldQueue = inRange && withinAttackWindow && !takingKnockback;

        if (!shouldQueue) {
            if (blockPackets || !inbound.isEmpty())
                hardFlush();
            return;
        }

        // Advantage / anti-flag flushes while a hold is active.
        if (blockPackets || !inbound.isEmpty()) {
            if (smartFlush.getValue() && shouldSmartFlush(player)) {
                hardFlush();
                lastOwnAttackMs = 0L;
                return;
            }
            if (hitboxFix.getValue() && shouldHitboxFixFlush(player)) {
                hardFlush();
                lastOwnAttackMs = 0L;
                return;
            }
        }

        if (!blockPackets) {
            float lo = Math.min(minTime.getValue(), maxTime.getValue());
            float hi = Math.max(minTime.getValue(), maxTime.getValue());
            currentDelay = (long) (lo + Math.random() * Math.max(0.0f, hi - lo));
            inbound.activate();
            InboundLagCoordinator.tryAcquire(InboundLagCoordinator.Owner.BACKTRACK);
            blockPackets = true;
        }

        // Trail by currentDelay during the window. Expired packets arrive ~1/tick in steady
        // state, so a full releaseExpired pass stays smooth (no soft-drain backlog burst).
        inbound.releaseExpired(currentDelay, PacketUtil::processInbound);
    }

    @Override
    public boolean onSend(Object packet) {
        if (!(packet instanceof C02PacketUseEntity))
            return false;
        C02PacketUseEntity use = (C02PacketUseEntity) packet;
        if (use.getAction() != C02PacketUseEntity.Action.ATTACK)
            return false;
        // Record the attacked player as BackTrack's target (original gnuclient behavior:
        // target is whoever you last hit, players only). Refresh the post-attack window on
        // every hit — with age-release, extending the window trails by currentDelay instead
        // of permanently freezing, and avoids HurtTime pulse stutter under KillAura.
        Entity attacked = use.getEntityFromWorld(Mc.world());
        if (attacked instanceof EntityPlayer) {
            entity = (EntityLivingBase) attacked;
            targetEntityId = attacked.getEntityId();
            lastOwnAttackMs = System.currentTimeMillis();
        }
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!(packet instanceof net.minecraft.network.Packet))
            return false;
        if (PacketUtil.isDispatching())
            return false;
        if (Mc.currentScreen() != null)
            return false;

        // Sync via the shared coordinator: only the highest-priority owner may hold the
        // inbound stream. Yield to KnockbackDelay (highest) and Lagrange (middle) so the
        // three lag modules never queue packets simultaneously.
        if (InboundLagCoordinator.isBlockedFor(InboundLagCoordinator.Owner.BACKTRACK)) {
            hardFlush();
            return false;
        }

        if (packet instanceof S08PacketPlayerPosLook) {
            hardFlush();
            return false;
        }

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            WorldClient world = Mc.world();
            Entity e = world != null ? p.getEntity(world) : null;
            if (e instanceof EntityLivingBase) {
                EntityLivingBase elb = (EntityLivingBase) e;
                RealPosAccess rp = realPos(elb);
                rp.setRealPosX(rp.getRealPosX() + p.func_149062_c());
                rp.setRealPosY(rp.getRealPosY() + p.func_149061_d());
                rp.setRealPosZ(rp.getRealPosZ() + p.func_149064_e());
                if (elb == entity)
                    trackEspServer(
                            rp.getRealPosX() / 32.0,
                            rp.getRealPosY() / 32.0,
                            rp.getRealPosZ() / 32.0);
            }
        }

        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
            Entity e = Mc.world() != null ? Mc.world().getEntityByID(p.getEntityId()) : null;
            if (e instanceof EntityLivingBase) {
                EntityLivingBase elb = (EntityLivingBase) e;
                RealPosAccess rp = realPos(elb);
                rp.setRealPosX(p.getX());
                rp.setRealPosY(p.getY());
                rp.setRealPosZ(p.getZ());
                if (elb == entity)
                    trackEspServer(
                            rp.getRealPosX() / 32.0,
                            rp.getRealPosY() / 32.0,
                            rp.getRealPosZ() / 32.0);
            }
        }

        if (entity == null)
            return false;

        if (blockPackets && shouldQueue(packet)) {
            inbound.offer(packet);
            return true;
        }
        return false;
    }

    @Override
    public void onRender(float partialTicks) {
        if (!esp.getValue() || entity == null || !isLagging() || !espServerValid || !Mc.isInGame())
            return;

        double[] server = espServerPos(partialTicks);
        double rx = server[0];
        double ry = server[1];
        double rz = server[2];
        float f = entity.width / 2.0f;

        double[] vp = Mc.getViewerPos(partialTicks);
        float r = 0.0f;
        float g = 1.0f;
        float bl = 0.0f;
        float lineWidth = 3.0f;
        float alpha = 0.15f;

        EntityPlayerSP player = Mc.player();
        if (player != null && player.getDistanceToEntity(entity) > 1.0f) {
            double d = 1.0f - player.getDistanceToEntity(entity) / 20.0f;
            if (d < 0.3)
                d = 0.3;
            lineWidth *= (float) d;
        }

        RenderHelper.begin();
        EspDraw.fill(
                rx - f - vp[0], ry - vp[1], rz - f - vp[2],
                rx + f - vp[0], ry + entity.height - vp[1], rz + f - vp[2],
                r, g, bl, alpha);
        RenderHelper.end();
    }

    /** Records the latest true server position of the target (world coords). */
    private void trackEspServer(double x, double y, double z) {
        if (!espServerValid || serverPosChanged(x, y, z, espServerX, espServerY, espServerZ)) {
            espIndFromX = espServerValid ? espServerX : x;
            espIndFromY = espServerValid ? espServerY : y;
            espIndFromZ = espServerValid ? espServerZ : z;
            espIndToX = x;
            espIndToY = y;
            espIndToZ = z;
            espIndValid = true;
            espIndStartMs = System.currentTimeMillis();
        }
        espServerX = x;
        espServerY = y;
        espServerZ = z;
        espServerValid = true;
    }

    /** Returns the interpolated true server position for the ESP (Lagrange-style lerp). */
    private double[] espServerPos(float partialTicks) {
        if (!espIndValid)
            return new double[] { espServerX, espServerY, espServerZ };
        long elapsed = System.currentTimeMillis() - espIndStartMs;
        float t = elapsed >= ESP_INTERP_MS ? 1.0f : (float) elapsed / (float) ESP_INTERP_MS;
        return new double[] {
                lerp(espIndFromX, espIndToX, t),
                lerp(espIndFromY, espIndToY, t),
                lerp(espIndFromZ, espIndToZ, t)
        };
    }

    private static boolean serverPosChanged(double ax, double ay, double az,
                                            double bx, double by, double bz) {
        return Math.abs(ax - bx) > ESP_POS_EPS
                || Math.abs(ay - by) > ESP_POS_EPS
                || Math.abs(az - bz) > ESP_POS_EPS;
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /** Whether a packet should be queued this hold. Only the target's S14/S18 movement
     *  packets are delayed — all other packets pass through immediately. */
    private boolean shouldQueue(Object packet) {
        return targetEntityId >= 0
                && PacketHelper.isBacktrackQueueCandidate(packet, targetEntityId, Mc.world());
    }

    /** True when live server position is strictly closer to the player than the delayed entity. */
    private boolean shouldSmartFlush(EntityPlayerSP player) {
        if (player == null || entity == null || !espServerValid)
            return false;
        double backDistSq = player.getDistanceSq(entity.posX, entity.posY, entity.posZ);
        double realDistSq = player.getDistanceSq(espServerX, espServerY, espServerZ);
        return realDistSq < backDistSq;
    }

    /** True when the live server body is too far sideways from the (silent) look yaw. */
    private boolean shouldHitboxFixFlush(EntityPlayerSP player) {
        if (player == null || !espServerValid)
            return false;
        double dx = espServerX - player.posX;
        double dz = espServerZ - player.posZ;
        if (dx * dx + dz * dz < 1.0e-6)
            return false;
        float yawToReal = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float lookYaw = RotationState.isActived()
                ? RotationState.getRotationYawHead()
                : player.rotationYaw;
        float yawDiff = MathHelper.wrapAngleTo180_float(yawToReal - lookYaw);
        return Math.abs(yawDiff) > HITBOX_FIX_MAX_YAW;
    }

    /**
     * End the hold by replaying queued S14/S18 through vanilla handling (interpolation)
     * instead of teleporting — {@code snapToServerPos} was the visible hitch.
     */
    private void hardFlush() {
        inbound.deactivate();
        blockPackets = false;
        if (!inbound.isEmpty())
            inbound.drainAll(PacketUtil::processInbound);
        InboundLagCoordinator.release(InboundLagCoordinator.Owner.BACKTRACK);
    }

    @Override
    public String[] getSuffix() {
        // Show the rolled packet age for the current hold (e.g. 353ms).
        return new String[]{currentDelay + "ms"};
    }

    /**
     * Pin the ArrayList row width so the entry does not re-sort/bob as the rolled delay
     * changes length between holds. Uses the widest possible MaxDelay value as reference.
     */
    @Override
    public int getFixedSuffixWidth() {
        String widest = ((long) (float) maxTime.getValue()) + "ms";
        return (int) UiFont.width(widest);
    }
}
