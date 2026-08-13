package gnu.client.runtime.packet;

import gnu.client.mixin.impl.accessors.IAccessorC00PacketKeepAlive;
import gnu.client.mixin.impl.accessors.IAccessorC03PacketPlayer;
import gnu.client.mixin.impl.accessors.IAccessorC0CPacketInput;
import gnu.client.mixin.impl.accessors.IAccessorC0FPacketConfirmTransaction;
import gnu.client.mixin.impl.accessors.IAccessorS00PacketKeepAlive;
import gnu.client.mixin.impl.accessors.IAccessorS08PacketPlayerPosLook;
import gnu.client.mixin.impl.accessors.IAccessorS12PacketEntityVelocity;
import gnu.client.mixin.impl.accessors.IAccessorS19PacketEntityStatus;
import gnu.client.mixin.impl.accessors.IAccessorS27PacketExplosion;
import gnu.client.mixin.impl.accessors.IAccessorS32PacketConfirmTransaction;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C15PacketClientSettings;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2CPacketSpawnGlobalEntity;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.world.World;

/**
 * Packet type checks and field access via typed MCP packets.
 */
public final class PacketHelper {

    private PacketHelper() {}

    public static boolean isPacket(Object obj) {
        return obj instanceof Packet;
    }

    public static boolean isEntityVelocity(Object packet) {
        return packet instanceof S12PacketEntityVelocity;
    }

    public static boolean isEntityRelMove(Object packet) {
        return packet instanceof S14PacketEntity;
    }

    /** {@code S14} rel-move / look-move only — not rotation-only {@code S16}. */
    public static boolean isEntityPositionUpdate(Object packet) {
        if (isEntityTeleport(packet))
            return true;
        if (!(packet instanceof S14PacketEntity))
            return false;
        if (packet instanceof S14PacketEntity.S16PacketEntityLook)
            return false;
        String name = packet.getClass().getName();
        if (name.contains("S16PacketEntityLook") || name.contains("S16PacketEntity"))
            return false;
        if (name.contains("EntityLook") && !name.contains("LookMove"))
            return false;
        return true;
    }

    public static boolean isEntityTeleport(Object packet) {
        return packet instanceof S18PacketEntityTeleport;
    }

    /** S14 relative X delta in blocks (fixed-point / 32). */
    public static double s14DeltaX(Object packet) {
        return readS14Delta(packet, 0);
    }

    public static double s14DeltaY(Object packet) {
        return readS14Delta(packet, 1);
    }

    public static double s14DeltaZ(Object packet) {
        return readS14Delta(packet, 2);
    }

    private static double readS14Delta(Object packet, int axis) {
        if (!(packet instanceof S14PacketEntity))
            return 0.0;
        S14PacketEntity p = (S14PacketEntity) packet;
        byte delta;
        switch (axis) {
            case 0:
                delta = p.func_149062_c();
                break;
            case 1:
                delta = p.func_149061_d();
                break;
            default:
                delta = p.func_149064_e();
                break;
        }
        return delta / 32.0;
    }

    /** S18 absolute block coords (fixed-point / 32). */
    public static double s18PosX(Object packet) {
        if (!(packet instanceof S18PacketEntityTeleport))
            return 0.0;
        return ((S18PacketEntityTeleport) packet).getX() / 32.0;
    }

    public static double s18PosY(Object packet) {
        if (!(packet instanceof S18PacketEntityTeleport))
            return 0.0;
        return ((S18PacketEntityTeleport) packet).getY() / 32.0;
    }

    public static double s18PosZ(Object packet) {
        if (!(packet instanceof S18PacketEntityTeleport))
            return 0.0;
        return ((S18PacketEntityTeleport) packet).getZ() / 32.0;
    }

    public static boolean isPlayerMovement(Object packet) {
        return packet instanceof C03PacketPlayer
                || classNameContains(packet, "C03PacketPlayer");
    }

    private static IAccessorC03PacketPlayer asC03(Object packet) {
        return packet instanceof C03PacketPlayer ? (IAccessorC03PacketPlayer) packet : null;
    }

    public static double c03PosX(Object packet) {
        if (!c03HasPosition(packet))
            return Double.NaN;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        return c03 != null ? c03.getX() : Double.NaN;
    }

    public static double c03PosY(Object packet) {
        if (!c03HasPosition(packet))
            return Double.NaN;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        return c03 != null ? c03.getY() : Double.NaN;
    }

    public static double c03PosZ(Object packet) {
        if (!c03HasPosition(packet))
            return Double.NaN;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        return c03 != null ? c03.getZ() : Double.NaN;
    }

    /** Squared distance between a position C03 and the live player feet position. */
    public static double c03PositionDistSqToPlayer(Object packet, Object player) {
        if (!(player instanceof Entity) || !c03HasPosition(packet))
            return Double.POSITIVE_INFINITY;
        Entity p = (Entity) player;
        double px = p.posX;
        double py = p.posY;
        double pz = p.posZ;
        double x = c03PosX(packet);
        double y = c03PosY(packet);
        double z = c03PosZ(packet);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z))
            return Double.POSITIVE_INFINITY;
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** True for C04/C06 (or {@code isMoving}) — rotation-only C03 must not be queued (Simulation). */
    public static boolean c03HasPosition(Object packet) {
        if (!isPlayerMovement(packet))
            return false;
        if (packet instanceof C03PacketPlayer.C04PacketPlayerPosition
                || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook)
            return true;
        String name = packet.getClass().getName();
        if (name.contains("C04Packet") || name.contains("C06Packet"))
            return true;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        return c03 != null && c03.getMoving();
    }

    /** {@code C03PacketPlayer.onGround} — false while jumping/falling. */
    public static boolean c03OnGround(Object packet) {
        if (!isPlayerMovement(packet))
            return true;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        return c03 == null || c03.getOnGround();
    }

    public static void c03SetPosition(Object packet, double x, double y, double z) {
        if (!c03HasPosition(packet))
            return;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        if (c03 == null)
            return;
        c03.setX(x);
        c03.setY(y);
        c03.setZ(z);
    }

    public static void c03SetOnGround(Object packet, boolean onGround) {
        if (!isPlayerMovement(packet))
            return;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        if (c03 != null)
            c03.setOnGround(onGround);
    }

    public static boolean c03HasRotation(Object packet) {
        if (!isPlayerMovement(packet))
            return false;
        if (packet instanceof C03PacketPlayer.C05PacketPlayerLook
                || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook)
            return true;
        String name = packet.getClass().getName();
        if (name.contains("C05Packet") || name.contains("C06Packet"))
            return true;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        return c03 != null && c03.getRotating();
    }

    public static float c03Yaw(Object packet) {
        if (!isPlayerMovement(packet))
            return 0.0f;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        return c03 != null ? c03.getYaw() : 0.0f;
    }

    public static float c03Pitch(Object packet) {
        if (!isPlayerMovement(packet))
            return 0.0f;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        return c03 != null ? c03.getPitch() : 0.0f;
    }

    public static void c03SetRotation(Object packet, float yaw, float pitch) {
        if (!isPlayerMovement(packet))
            return;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        if (c03 == null)
            return;
        c03.setYaw(yaw);
        c03.setPitch(pitch);
        c03.setRotating(true);
    }

    public static void c03SetYaw(Object packet, float yaw) {
        if (!isPlayerMovement(packet))
            return;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        if (c03 == null)
            return;
        c03.setYaw(yaw);
        c03.setRotating(true);
    }

    public static void c03SetPitch(Object packet, float pitch) {
        if (!isPlayerMovement(packet))
            return;
        IAccessorC03PacketPlayer c03 = asC03(packet);
        if (c03 == null)
            return;
        c03.setPitch(pitch);
        c03.setRotating(true);
    }

    public static int entityId(Object packet) {
        if (packet instanceof S19PacketEntityStatus)
            return ((IAccessorS19PacketEntityStatus) packet).getEntityId();
        if (packet instanceof S12PacketEntityVelocity)
            return ((S12PacketEntityVelocity) packet).getEntityID();
        if (packet instanceof S18PacketEntityTeleport)
            return ((S18PacketEntityTeleport) packet).getEntityId();
        return -1;
    }

    /** Entity id on inbound play packets (S12/S14/S18/S19/S1C, etc.). */
    public static int packetEntityId(Object packet) {
        return packetEntityIdInWorld(packet, null);
    }

    /**
     * Entity id for inbound packets. {@code S14PacketEntity} has no int id field —
     * resolve via {@code getEntity(world)} (BetterPing / OpenMyau pattern).
     */
    public static int packetEntityIdInWorld(Object packet, Object world) {
        if (packet == null)
            return -1;
        if (packet instanceof S12PacketEntityVelocity) {
            int id = ((S12PacketEntityVelocity) packet).getEntityID();
            if (id > 0)
                return id;
        }
        if (packet instanceof S18PacketEntityTeleport) {
            int id = ((S18PacketEntityTeleport) packet).getEntityId();
            if (id > 0)
                return id;
        }
        if (packet instanceof S19PacketEntityStatus) {
            int id = ((IAccessorS19PacketEntityStatus) packet).getEntityId();
            if (id > 0)
                return id;
        }
        if (isEntityVelocity(packet))
            return velocityEntityId(packet);
        if (world instanceof World && packet instanceof S14PacketEntity) {
            Entity entity = ((S14PacketEntity) packet).getEntity((World) world);
            int id = Mc.entityId(entity);
            if (id > 0)
                return id;
        }
        return -1;
    }

    public static boolean isEntityMetadata(Object packet) {
        return packet instanceof S1CPacketEntityMetadata
                || classNameContains(packet, "S1CPacketEntityMetadata");
    }

    public static boolean isDestroyEntities(Object packet) {
        return packet instanceof S13PacketDestroyEntities
                || classNameContains(packet, "S13PacketDestroyEntities");
    }

    /** Inbound packets that must reach the client immediately during Backtrack. */
    public static boolean isBacktrackPassThrough(Object packet) {
        if (packet == null)
            return true;
        if (isInboundQueueExempt(packet))
            return true;
        if (isEntityVelocity(packet) || isAnimationPacket(packet))
            return true;
        if (isEntityStatus(packet) || isEntityMetadata(packet) || isDestroyEntities(packet))
            return true;
        if (packet instanceof S29PacketSoundEffect
                || classNameContains(packet, "S29PacketSoundEffect"))
            return true;
        return false;
    }

    /** Only S14/S18 movement for the active backtrack target may be queued. */
    public static boolean isBacktrackQueueCandidate(Object packet, int targetEntityId) {
        return isBacktrackQueueCandidate(packet, targetEntityId, null);
    }

    /** @param world required for reliable {@code S14} entity-id resolution */
    public static boolean isBacktrackQueueCandidate(Object packet, int targetEntityId, Object world) {
        if (packet == null || targetEntityId < 0)
            return false;
        if (!isEntityPositionUpdate(packet))
            return false;
        return packetEntityIdInWorld(packet, world) == targetEntityId;
    }

    public static int velocityEntityId(Object packet) {
        if (!(packet instanceof S12PacketEntityVelocity))
            return -1;
        return ((S12PacketEntityVelocity) packet).getEntityID();
    }

    /** S12 motion ints are velocity × 8000 (1.8.9). */
    public static final int MIN_MELEE_KB_HORIZONTAL_MOTION = 80;

    private static volatile long lastInboundExplosionAtMs;

    public static void noteInboundExplosion() {
        lastInboundExplosionAtMs = System.currentTimeMillis();
    }

    public static boolean isRecentExplosionKnockback() {
        return System.currentTimeMillis() - lastInboundExplosionAtMs < 100L;
    }

    public static int velocityMotionX(Object packet) {
        if (!(packet instanceof S12PacketEntityVelocity))
            return 0;
        return ((S12PacketEntityVelocity) packet).getMotionX();
    }

    public static int velocityMotionY(Object packet) {
        if (!(packet instanceof S12PacketEntityVelocity))
            return 0;
        return ((S12PacketEntityVelocity) packet).getMotionY();
    }

    public static int velocityMotionZ(Object packet) {
        if (!(packet instanceof S12PacketEntityVelocity))
            return 0;
        return ((S12PacketEntityVelocity) packet).getMotionZ();
    }

    public static void velocitySetMotionX(Object packet, int motionX) {
        if (!(packet instanceof S12PacketEntityVelocity))
            return;
        ((IAccessorS12PacketEntityVelocity) packet).setMotionX(motionX);
    }

    public static void velocitySetMotionY(Object packet, int motionY) {
        if (!(packet instanceof S12PacketEntityVelocity))
            return;
        ((IAccessorS12PacketEntityVelocity) packet).setMotionY(motionY);
    }

    public static void velocitySetMotionZ(Object packet, int motionZ) {
        if (!(packet instanceof S12PacketEntityVelocity))
            return;
        ((IAccessorS12PacketEntityVelocity) packet).setMotionZ(motionZ);
    }

    public static boolean isSteerVehicle(Object packet) {
        return packet instanceof C0CPacketInput
                || classNameContains(packet, "C0CPacketInput");
    }

    /** C0C strafe speed (1.8.9 {@code C0CPacketInput}). */
    public static float steerStrafe(Object packet) {
        if (!(packet instanceof C0CPacketInput))
            return 0.0f;
        return ((C0CPacketInput) packet).getStrafeSpeed();
    }

    public static float steerForward(Object packet) {
        if (!(packet instanceof C0CPacketInput))
            return 0.0f;
        return ((C0CPacketInput) packet).getForwardSpeed();
    }

    public static boolean steerJump(Object packet) {
        if (!(packet instanceof C0CPacketInput))
            return false;
        return ((C0CPacketInput) packet).isJumping();
    }

    public static void steerSetStrafe(Object packet, float strafe) {
        if (!(packet instanceof C0CPacketInput))
            return;
        ((IAccessorC0CPacketInput) packet).setStrafeSpeed(strafe);
    }

    public static void steerSetForward(Object packet, float forward) {
        if (!(packet instanceof C0CPacketInput))
            return;
        ((IAccessorC0CPacketInput) packet).setForwardSpeed(forward);
    }

    public static void steerSetJump(Object packet, boolean jump) {
        if (!(packet instanceof C0CPacketInput))
            return;
        ((IAccessorC0CPacketInput) packet).setJumping(jump);
    }

    public static boolean isFallDamageVelocity(int motionX, int motionY, int motionZ) {
        return motionX == 0 && motionZ == 0 && motionY < 0;
    }

    public static boolean hasMeleeHorizontalKnockback(int motionX, int motionZ) {
        return Math.abs(motionX) >= MIN_MELEE_KB_HORIZONTAL_MOTION
                || Math.abs(motionZ) >= MIN_MELEE_KB_HORIZONTAL_MOTION;
    }

    /** Horizontal player-hit KB — excludes fall, vertical-only, and negligible motion (fire/poison). */
    public static boolean isMeleeKnockbackVelocity(int motionX, int motionY, int motionZ) {
        if (isFallDamageVelocity(motionX, motionY, motionZ))
            return false;
        return hasMeleeHorizontalKnockback(motionX, motionZ);
    }

    public static boolean isMeleeKnockbackVelocity(Object packet) {
        if (!isEntityVelocity(packet))
            return false;
        return isMeleeKnockbackVelocity(
                velocityMotionX(packet),
                velocityMotionY(packet),
                velocityMotionZ(packet));
    }

    /** Melee KB from a player hit — not fall, explosion, or environmental hurt without horizontal S12. */
    public static boolean isPlayerHitKnockbackVelocity(Object packet) {
        return isMeleeKnockbackVelocity(packet) && !isRecentExplosionKnockback();
    }

    public static boolean isUseEntity(Object packet) {
        return packet instanceof C02PacketUseEntity
                || classNameContains(packet, "C02PacketUseEntity");
    }

    public static boolean isAttackUseEntity(Object packet) {
        if (!(packet instanceof C02PacketUseEntity))
            return false;
        return ((C02PacketUseEntity) packet).getAction() == C02PacketUseEntity.Action.ATTACK;
    }

    public static boolean isExplosion(Object packet) {
        return packet instanceof S27PacketExplosion
                || classNameContains(packet, "S27PacketExplosion");
    }

    public static float explosionMotionX(Object packet) {
        if (!(packet instanceof S27PacketExplosion))
            return 0.0f;
        return ((S27PacketExplosion) packet).func_149149_c();
    }

    public static float explosionMotionY(Object packet) {
        if (!(packet instanceof S27PacketExplosion))
            return 0.0f;
        return ((S27PacketExplosion) packet).func_149144_d();
    }

    public static float explosionMotionZ(Object packet) {
        if (!(packet instanceof S27PacketExplosion))
            return 0.0f;
        return ((S27PacketExplosion) packet).func_149147_e();
    }

    public static void explosionSetMotionX(Object packet, float motionX) {
        if (!(packet instanceof S27PacketExplosion))
            return;
        ((IAccessorS27PacketExplosion) packet).setMotionX(motionX);
    }

    public static void explosionSetMotionY(Object packet, float motionY) {
        if (!(packet instanceof S27PacketExplosion))
            return;
        ((IAccessorS27PacketExplosion) packet).setMotionY(motionY);
    }

    public static void explosionSetMotionZ(Object packet, float motionZ) {
        if (!(packet instanceof S27PacketExplosion))
            return;
        ((IAccessorS27PacketExplosion) packet).setMotionZ(motionZ);
    }

    public static boolean isAnimationPacket(Object packet) {
        return packet instanceof C0APacketAnimation
                || classNameContains(packet, "C0APacketAnimation");
    }

    public static boolean isLookOrRotationPacket(Object packet) {
        if (packet == null)
            return false;
        String name = packet.getClass().getName();
        return name.contains("Look") || name.contains("Rotation");
    }

    public static boolean isClientConfirmTransaction(Object packet) {
        return packet instanceof C0FPacketConfirmTransaction
                || classNameContains(packet, "C0FPacketConfirmTransaction");
    }

    /**
     * Modern Via / 1.17+ inventory-latency replies Grim waits on (pong / ping ack).
     * Class names vary by Via bundling; match by simple name fragments.
     */
    public static boolean isClientPingPong(Object packet) {
        if (packet == null)
            return false;
        return classNameContains(packet, "CommonPong")
                || classNameContains(packet, "PingPong")
                || classNameContains(packet, "ServerboundPong")
                || classNameContains(packet, "C2SPong");
    }

    public static boolean isServerConfirmTransaction(Object packet) {
        return packet instanceof S32PacketConfirmTransaction
                || classNameContains(packet, "S32PacketConfirmTransaction");
    }

    /** S2F set-slot or S30 window-items — inventory resync after offhand swap. */
    public static boolean isInventorySlotUpdate(Object packet) {
        if (packet == null)
            return false;
        return classNameContains(packet, "S2FPacketSetSlot")
                || classNameContains(packet, "S30PacketWindowItems")
                || classNameContains(packet, "SetSlot")
                || classNameContains(packet, "WindowItems");
    }

    public static boolean isEntityAction(Object packet) {
        return packet instanceof C0BPacketEntityAction
                || classNameContains(packet, "C0BPacketEntityAction");
    }

    /** C0B START_SPRINTING / STOP_SPRINTING (Grim BadPacketsX sprint window). */
    public static boolean isSprintEntityAction(Object packet) {
        C0BPacketEntityAction.Action action = entityAction(packet);
        return action == C0BPacketEntityAction.Action.START_SPRINTING
                || action == C0BPacketEntityAction.Action.STOP_SPRINTING;
    }

    public static boolean isStartSprintEntityAction(Object packet) {
        return entityAction(packet) == C0BPacketEntityAction.Action.START_SPRINTING;
    }

    public static boolean isStopSprintEntityAction(Object packet) {
        return entityAction(packet) == C0BPacketEntityAction.Action.STOP_SPRINTING;
    }

    /** C0B START_SNEAKING / STOP_SNEAKING (Grim BadPacketsX sneak window). */
    public static boolean isSneakEntityAction(Object packet) {
        C0BPacketEntityAction.Action action = entityAction(packet);
        return action == C0BPacketEntityAction.Action.START_SNEAKING
                || action == C0BPacketEntityAction.Action.STOP_SNEAKING;
    }

    private static C0BPacketEntityAction.Action entityAction(Object packet) {
        if (!(packet instanceof C0BPacketEntityAction))
            return null;
        return ((C0BPacketEntityAction) packet).getAction();
    }

    public static boolean isPlayerDigging(Object packet) {
        return packet instanceof C07PacketPlayerDigging
                || classNameContains(packet, "C07PacketPlayerDigging");
    }

    /** C07 block dig / drop — not RELEASE_USE_ITEM. */
    public static boolean isBlockDig(Object packet) {
        return isPlayerDigging(packet) && !isReleaseUseItem(packet);
    }

    public static boolean isHeldItemChange(Object packet) {
        return packet instanceof C09PacketHeldItemChange
                || classNameContains(packet, "C09PacketHeldItemChange");
    }

    /** Hotbar index from {@code C09PacketHeldItemChange}, or {@code -1}. */
    public static int c09Slot(Object packet) {
        if (!(packet instanceof C09PacketHeldItemChange))
            return -1;
        int slot = ((C09PacketHeldItemChange) packet).getSlotId();
        return slot >= 0 && slot <= 8 ? slot : -1;
    }

    /**
     * C07 RELEASE_USE_ITEM action — tells the server the player stopped blocking.
     * During autoblock lag windows, this MUST be cancelled to prevent the server
     * from seeing the block release (defeats autoblock). Checks both class name
     * and the action enum value.
     */
    public static boolean isReleaseUseItem(Object packet) {
        if (!(packet instanceof C07PacketPlayerDigging))
            return false;
        return ((C07PacketPlayerDigging) packet).getStatus()
                == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM;
    }

    /**
     * C08 use-item only (1.8 sword block / eat): {@code placedBlockDirection == 255}.
     * Real block interact/place uses direction 0–5.
     */
    public static boolean isUseItemOnlyC08(Object packet) {
        if (!(packet instanceof C08PacketPlayerBlockPlacement))
            return false;
        return ((C08PacketPlayerBlockPlacement) packet).getPlacedBlockDirection() == 255;
    }

    /**
     * C08 against a world face (interact/place), not the legacy use-item packet.
     * Grim RotationPlace validates these against flying look.
     */
    public static boolean isBlockInteractOrPlace(Object packet) {
        return isBlockPlacement(packet) && !isUseItemOnlyC08(packet);
    }

    public static boolean isBlockPlacement(Object packet) {
        return packet instanceof C08PacketPlayerBlockPlacement
                || classNameContains(packet, "C08PacketPlayerBlockPlacement");
    }

    /**
     * C08 use-item / block-place — matches any C08PacketPlayerBlockPlacement.
     * Used to drop C08 during autoblock lag windows (prevents MultiActions A from
     * buffered C08 bursts after C02 release). In 1.8.9, all C08s are the same class;
     * face=-1 distinguishes use-item from block-placement but both are caught here.
     */
    public static boolean isSendUseItem(Object packet) {
        return packet instanceof C08PacketPlayerBlockPlacement
                || classNameContains(packet, "C08PacketPlayerBlockPlacement");
    }

    /** Held stack is ender pearl or a placeable block item (C08 use / block place). */
    public static boolean isHeldPearlOrPlaceableBlock(Object player) {
        if (!(player instanceof EntityPlayer))
            return false;
        net.minecraft.item.ItemStack stack = ((EntityPlayer) player).getHeldItem();
        if (stack == null)
            return false;
        net.minecraft.item.Item item = stack.getItem();
        return item instanceof ItemEnderPearl || item instanceof ItemBlock;
    }

    /** raven Stasis: exact {@code instanceof C03PacketPlayer.C05PacketPlayerLook}. */
    public static boolean isC05PacketPlayerLook(Object packet) {
        return packet instanceof C03PacketPlayer.C05PacketPlayerLook;
    }

    public static boolean isPlayerPosLook(Object packet) {
        return packet instanceof S08PacketPlayerPosLook
                || classNameContains(packet, "S08PacketPlayerPosLook");
    }

    public static double posLookX(Object packet) {
        if (!(packet instanceof S08PacketPlayerPosLook))
            return Double.NaN;
        return ((S08PacketPlayerPosLook) packet).getX();
    }

    public static double posLookY(Object packet) {
        if (!(packet instanceof S08PacketPlayerPosLook))
            return Double.NaN;
        return ((S08PacketPlayerPosLook) packet).getY();
    }

    public static double posLookZ(Object packet) {
        if (!(packet instanceof S08PacketPlayerPosLook))
            return Double.NaN;
        return ((S08PacketPlayerPosLook) packet).getZ();
    }

    public static float posLookYaw(Object packet) {
        if (!(packet instanceof S08PacketPlayerPosLook))
            return Float.NaN;
        return ((S08PacketPlayerPosLook) packet).getYaw();
    }

    public static float posLookPitch(Object packet) {
        if (!(packet instanceof S08PacketPlayerPosLook))
            return Float.NaN;
        return ((S08PacketPlayerPosLook) packet).getPitch();
    }

    public static boolean isDisconnect(Object packet) {
        return packet instanceof S40PacketDisconnect
                || classNameContains(packet, "S40PacketDisconnect")
                || classNameContains(packet, "S00PacketDisconnect");
    }

    public static boolean isUpdateHealth(Object packet) {
        return packet instanceof S06PacketUpdateHealth
                || classNameContains(packet, "S06PacketUpdateHealth");
    }

    public static boolean isEntityStatus(Object packet) {
        return packet instanceof S19PacketEntityStatus
                || classNameContains(packet, "S19PacketEntityStatus");
    }

    /** S19 opCode 2 (hurt) for the local player — keep damage feedback immediate while lagging inbound. */
    public static boolean isSelfDamageEntityStatus(Object packet) {
        if (!(packet instanceof S19PacketEntityStatus))
            return false;
        S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
        if (status.getOpCode() != 2)
            return false;
        EntityPlayerSP player = Mc.player();
        return player != null && ((IAccessorS19PacketEntityStatus) status).getEntityId() == Mc.entityId(player);
    }

    /** Self {@code S12PacketEntityVelocity} — cancel outbound lag queue, do not burst stale C03. */
    public static boolean isSelfEntityVelocity(Object packet) {
        if (!isEntityVelocity(packet))
            return false;
        EntityPlayerSP player = Mc.player();
        return player != null && velocityEntityId(packet) == Mc.entityId(player);
    }

    /**
     * Lagrange: only connection-critical packets bypass lag (raven {@code UnifiedLagHandler} queues
     * C0A/C0B/C0F/C03 together — splitting them caused Grim packet-order flags).
     */
    public static boolean isLagrangeSendExempt(Object packet) {
        return isKeepAlive(packet) || isChat(packet) || isClientConfirmTransaction(packet);
    }

    /** Block interact must flush movement queue first (Lagrange/Blink), not bypass it. */
    public static boolean isBlockInteract(Object packet) {
        return isPlayerDigging(packet) || isBlockPlacement(packet);
    }

    /** Blink: keepalive/chat/C0F/C0B/C0A exempt; Lagrange uses {@link #isLagrangeSendExempt} only. */
    public static boolean isOutboundQueueExempt(Object packet) {
        return isKeepAlive(packet)
                || isChat(packet)
                || isClientConfirmTransaction(packet)
                || isEntityAction(packet)
                || isAnimationPacket(packet);
    }

    /**
     * Blink must not split {@code C0A} from {@code C02} — Vulcan BadPackets {@code swung=false}.
     */
    public static boolean isBlinkOutboundExempt(Object packet) {
        return isOutboundQueueExempt(packet) || isUseEntity(packet);
    }

    /**
     * Inbound packets that must not be delayed (Backtrack, etc.).
     * Transaction confirms must reach the client immediately.
     */
    public static boolean isInboundQueueExempt(Object packet) {
        if (packet == null)
            return true;
        if (isServerConfirmTransaction(packet) || isClientConfirmTransaction(packet))
            return true;
        if (isKeepAlive(packet))
            return true;
        if (isChat(packet))
            return true;
        if (isPlayerPosLook(packet))
            return true;
        if (isDisconnect(packet))
            return true;
        return isUpdateHealth(packet);
    }

    public static boolean isKeepAlive(Object packet) {
        return packet instanceof C00PacketKeepAlive
                || packet instanceof S00PacketKeepAlive
                || classNameContains(packet, "C00PacketKeepAlive")
                || classNameContains(packet, "S00PacketKeepAlive");
    }

    public static boolean isClientSettings(Object packet) {
        return packet instanceof C15PacketClientSettings
                || classNameContains(packet, "C15PacketClientSettings");
    }

    public static boolean isCustomPayload(Object packet) {
        return packet instanceof C17PacketCustomPayload
                || classNameContains(packet, "C17PacketCustomPayload");
    }

    public static boolean isChat(Object packet) {
        return packet instanceof C01PacketChatMessage
                || packet instanceof S02PacketChat
                || classNameContains(packet, "C01PacketChatMessage")
                || classNameContains(packet, "S02PacketChatMessage");
    }

    /** Outbound {@code C01PacketChatMessage} only. */
    public static boolean isChatSend(Object packet) {
        return packet instanceof C01PacketChatMessage
                || classNameContains(packet, "C01PacketChatMessage");
    }

    /**
     * Chat text from outbound {@code C01PacketChatMessage} or inbound {@code S02PacketChat}
     * (unformatted). Returns {@code null} when not a chat packet.
     */
    public static String chatMessage(Object packet) {
        if (packet instanceof C01PacketChatMessage)
            return ((C01PacketChatMessage) packet).getMessage();
        if (packet instanceof S02PacketChat) {
            S02PacketChat chat = (S02PacketChat) packet;
            if (chat.getChatComponent() == null)
                return null;
            return chat.getChatComponent().getUnformattedText();
        }
        return null;
    }

    /** Inbound {@code S02PacketChat} only. */
    public static boolean isChatReceive(Object packet) {
        return packet instanceof S02PacketChat
                || classNameContains(packet, "S02PacketChat");
    }

    /** Formatted inbound chat text, or {@code null}. */
    public static String chatFormattedText(Object packet) {
        if (!(packet instanceof S02PacketChat))
            return null;
        S02PacketChat chat = (S02PacketChat) packet;
        if (chat.getChatComponent() == null)
            return null;
        return chat.getChatComponent().getFormattedText();
    }

    /** S02 chat type byte, or {@code -1}. */
    public static byte chatType(Object packet) {
        if (!(packet instanceof S02PacketChat))
            return -1;
        return ((S02PacketChat) packet).getType();
    }

    public static void posLookSetYaw(Object packet, float yaw) {
        if (!(packet instanceof S08PacketPlayerPosLook))
            return;
        try {
            ((IAccessorS08PacketPlayerPosLook) packet).setYaw(yaw);
        } catch (ClassCastException e) {
            setFloatField(packet, "yaw", yaw);
        }
    }

    public static void posLookSetPitch(Object packet, float pitch) {
        if (!(packet instanceof S08PacketPlayerPosLook))
            return;
        try {
            ((IAccessorS08PacketPlayerPosLook) packet).setPitch(pitch);
        } catch (ClassCastException e) {
            setFloatField(packet, "pitch", pitch);
        }
    }

    public static void posLookSetRotation(Object packet, float yaw, float pitch) {
        posLookSetYaw(packet, yaw);
        posLookSetPitch(packet, pitch);
    }

    public static int keepAliveId(Object packet) {
        if (packet instanceof C00PacketKeepAlive)
            return ((C00PacketKeepAlive) packet).getKey();
        if (packet instanceof S00PacketKeepAlive)
            return ((S00PacketKeepAlive) packet).func_149134_c();
        return 0;
    }

    public static void keepAliveSetId(Object packet, int id) {
        if (packet instanceof C00PacketKeepAlive) {
            try {
                ((IAccessorC00PacketKeepAlive) packet).setKey(id);
            } catch (ClassCastException e) {
                setIntField(packet, "key", id);
            }
        } else if (packet instanceof S00PacketKeepAlive) {
            try {
                ((IAccessorS00PacketKeepAlive) packet).setId(id);
            } catch (ClassCastException e) {
                setIntField(packet, "id", id);
            }
        }
    }

    public static String entityActionName(Object packet) {
        C0BPacketEntityAction.Action action = entityAction(packet);
        return action == null ? "" : action.name();
    }

    public static String digActionName(Object packet) {
        if (!(packet instanceof C07PacketPlayerDigging))
            return "";
        C07PacketPlayerDigging.Action status = ((C07PacketPlayerDigging) packet).getStatus();
        return status == null ? "" : status.name();
    }

    public static int transactionWindowId(Object packet) {
        if (packet instanceof C0FPacketConfirmTransaction)
            return ((C0FPacketConfirmTransaction) packet).getWindowId();
        if (packet instanceof S32PacketConfirmTransaction)
            return ((S32PacketConfirmTransaction) packet).getWindowId();
        return -1;
    }

    public static short transactionUid(Object packet) {
        if (packet instanceof C0FPacketConfirmTransaction)
            return ((C0FPacketConfirmTransaction) packet).getUid();
        if (packet instanceof S32PacketConfirmTransaction)
            return ((S32PacketConfirmTransaction) packet).getActionNumber();
        return 0;
    }

    public static void transactionSetUid(Object packet, short uid) {
        if (packet instanceof C0FPacketConfirmTransaction) {
            try {
                ((IAccessorC0FPacketConfirmTransaction) packet).setUid(uid);
            } catch (ClassCastException e) {
                setShortField(packet, "uid", uid);
            }
        } else if (packet instanceof S32PacketConfirmTransaction) {
            try {
                ((IAccessorS32PacketConfirmTransaction) packet).setActionNumber(uid);
            } catch (ClassCastException e) {
                setShortField(packet, "actionNumber", uid);
            }
        }
    }

    public static boolean transactionAccepted(Object packet) {
        if (packet instanceof S32PacketConfirmTransaction)
            return ((S32PacketConfirmTransaction) packet).func_148888_e();
        if (packet instanceof C0FPacketConfirmTransaction)
            return true;
        return false;
    }

    public static boolean isSpawnGlobalEntity(Object packet) {
        return packet instanceof S2CPacketSpawnGlobalEntity
                || classNameContains(packet, "S2CPacketSpawnGlobalEntity")
                || classNameContains(packet, "SpawnGlobalEntity");
    }

    /** Global entity type; lightning bolt is {@code 1}. */
    public static int globalEntityType(Object packet) {
        if (!(packet instanceof S2CPacketSpawnGlobalEntity))
            return -1;
        return ((S2CPacketSpawnGlobalEntity) packet).func_149053_g();
    }

    private static void setIntField(Object obj, String name, int value) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setInt(obj, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setShortField(Object obj, String name, short value) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setShort(obj, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setFloatField(Object obj, String name, float value) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setFloat(obj, value);
        } catch (Throwable ignored) {
        }
    }

    private static boolean classNameContains(Object packet, String fragment) {
        return packet != null && packet.getClass().getName().contains(fragment);
    }
}
