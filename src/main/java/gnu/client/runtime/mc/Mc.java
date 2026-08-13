package gnu.client.runtime.mc;

import gnu.client.mixin.impl.accessors.IAccessorEntityPlayerSP;
import gnu.client.mixin.impl.accessors.IAccessorMinecraft;
import gnu.client.mixin.impl.accessors.IAccessorPlayerControllerMP;
import gnu.client.mixin.impl.accessors.IAccessorRenderManager;
import gnu.client.mixin.impl.accessors.IAccessorTimer;
import gnu.client.module.modules.combat.AntiBotModule;
import gnu.client.module.modules.combat.RavenAntiBot;
import gnu.client.runtime.AuraCombatPacketGuard;
import gnu.client.runtime.BlinkManager;
import gnu.client.runtime.ClientBootstrap;
import gnu.client.runtime.packet.PacketUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import gnu.client.ui.menu.GnuMainMenuScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Typed MCP facade replacing reflection-based {@code McAccess}.
 * Prefer calling Minecraft APIs directly in new code; use this for shared helpers.
 */
public final class Mc {
    private Mc() {}

    public static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    public static IAccessorMinecraft accessor() {
        return (IAccessorMinecraft) mc();
    }

    public static EntityPlayerSP player() {
        Minecraft m = mc();
        return m != null ? m.thePlayer : null;
    }

    public static WorldClient world() {
        return mc().theWorld;
    }

    public static GuiScreen currentScreen() {
        return mc().currentScreen;
    }

    public static GameSettings settings() {
        return mc().gameSettings;
    }

    public static PlayerControllerMP controller() {
        return mc().playerController;
    }

    public static FontRenderer fontRenderer() {
        return mc().fontRendererObj;
    }

    public static RenderManager renderManager() {
        return mc().getRenderManager();
    }

    public static Entity renderViewEntity() {
        Entity rve = mc().getRenderViewEntity();
        return rve != null ? rve : player();
    }

    public static boolean isInGame() {
        return player() != null && world() != null && currentScreen() == null;
    }

    public static boolean isResolved() {
        return mc() != null;
    }

    public static void addChatMessage(String message) {
        EntityPlayerSP p = player();
        if (p != null)
            p.addChatMessage(new ChatComponentText(message));
    }

    public static MovingObjectPosition objectMouseOver() {
        return mc().objectMouseOver;
    }

    public static void setObjectMouseOver(MovingObjectPosition mop) {
        accessor().setObjectMouseOver(mop);
    }

    public static Entity pointedEntity() {
        return accessor().getPointedEntity();
    }

    public static void setPointedEntity(Entity entity) {
        accessor().setPointedEntity(entity);
    }

    public static float getPartialTicks() {
        Timer timer = accessor().getTimer();
        return timer != null ? timer.renderPartialTicks : 1.0f;
    }

    public static float getTimerSpeed() {
        Timer timer = accessor().getTimer();
        return timer != null ? ((IAccessorTimer) timer).getTimerSpeed() : 1.0f;
    }

    public static void setTimerSpeed(float speed) {
        Timer timer = accessor().getTimer();
        if (timer != null)
            ((IAccessorTimer) timer).setTimerSpeed(speed);
    }

    public static boolean setTimerSpeedVerified(float speed) {
        setTimerSpeed(speed);
        return Math.abs(getTimerSpeed() - speed) < 0.0001f;
    }

    public static void resetTimer() {
        setTimerSpeed(1.0f);
    }

    public static void clearRightClickDelay() {
        accessor().setRightClickDelayTimer(0);
    }

    public static void setRightClickDelay(int delay) {
        accessor().setRightClickDelayTimer(delay);
    }

    public static void clearLeftClickCounter() {
        accessor().setLeftClickCounter(0);
    }

    public static ScaledResolution createScaledResolution() {
        return new ScaledResolution(mc());
    }

    public static float playerViewY() {
        return ((IAccessorRenderManager) renderManager()).getPlayerViewY();
    }

    public static float playerViewX() {
        return ((IAccessorRenderManager) renderManager()).getPlayerViewX();
    }

    public static double[] getViewerPos(float partialTicks) {
        Entity rve = renderViewEntity();
        double[] out = new double[3];
        if (rve == null)
            return out;
        out[0] = lerp(rve.lastTickPosX, rve.posX, partialTicks);
        out[1] = lerp(rve.lastTickPosY, rve.posY, partialTicks);
        out[2] = lerp(rve.lastTickPosZ, rve.posZ, partialTicks);
        return out;
    }

    public static double lerp(double last, double pos, double partialTicks) {
        return last + (pos - last) * partialTicks;
    }

    public static double interpX(Entity entity, double partialTicks) {
        return lerp(entity.lastTickPosX, entity.posX, partialTicks);
    }

    public static double interpY(Entity entity, double partialTicks) {
        return lerp(entity.lastTickPosY, entity.posY, partialTicks);
    }

    public static double interpZ(Entity entity, double partialTicks) {
        return lerp(entity.lastTickPosZ, entity.posZ, partialTicks);
    }

    public static float getMouseSensitivityGcd() {
        float sens = settings().mouseSensitivity * 0.6f + 0.2f;
        return sens * sens * sens * 8.0f * 0.15f;
    }

    public static void addToSendQueue(Packet<?> packet) {
        EntityPlayerSP p = player();
        if (p != null && p.sendQueue != null)
            p.sendQueue.addToSendQueue(packet);
    }

    public static NetHandlerPlayClient netHandler() {
        EntityPlayerSP p = player();
        return p != null ? p.sendQueue : null;
    }

    public static Set<String> getTablistNames() {
        NetHandlerPlayClient nh = netHandler();
        if (nh == null)
            return Collections.emptySet();
        Collection<NetworkPlayerInfo> infos = nh.getPlayerInfoMap();
        if (infos == null)
            return Collections.emptySet();
        Set<String> names = new HashSet<>();
        for (NetworkPlayerInfo info : infos) {
            if (info != null && info.getGameProfile() != null)
                names.add(info.getGameProfile().getName());
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    public static List<Entity> getWorldEntities(World w) {
        if (w == null)
            return Collections.emptyList();
        return new ArrayList<Entity>(w.loadedEntityList);
    }

    /**
     * Reuse-friendly variant: fills {@code out} in place with the filtered entity list
     * and returns it, avoiding a second per-tick allocation when bot filtering is active.
     * {@code out} is cleared first so callers may reuse the same buffer across ticks.
     */
    public static List<Entity> getWorldEntitiesFilteredInto(World w, List<Entity> out) {
        out.clear();
        if (w == null)
            return out;
        if (!AntiBotModule.isActive()) {
            out.addAll(w.loadedEntityList);
            return out;
        }
        for (Entity entity : w.loadedEntityList) {
            if (RavenAntiBot.isBot(entity))
                continue;
            out.add(entity);
        }
        return out;
    }

    /** Convenience copy (used by scripts / one-shot callers). */
    public static List<Entity> getWorldEntitiesFiltered(World w) {
        return getWorldEntitiesFilteredInto(w, new ArrayList<Entity>());
    }

    public static boolean isEntityPlayer(Entity entity) {
        return entity instanceof EntityPlayer;
    }

    public static double distanceToPlayer(Entity entity) {
        EntityPlayerSP p = player();
        if (p == null || entity == null)
            return Double.MAX_VALUE;
        return p.getDistanceToEntity(entity);
    }

    public static EntityPlayer getNearestPlayer(double range) {
        EntityPlayerSP self = player();
        WorldClient w = world();
        if (self == null || w == null)
            return null;
        EntityPlayer best = null;
        double bestDist = range;
        for (EntityPlayer other : w.playerEntities) {
            if (other == null || other == self || other.isDead)
                continue;
            double d = self.getDistanceToEntity(other);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    public static boolean attackEntity(Entity target) {
        return attackEntity(target, true);
    }

    public static boolean attackEntity(Entity target, boolean swing) {
        EntityPlayerSP p = player();
        PlayerControllerMP c = controller();
        if (p == null || c == null || target == null)
            return false;
        // 1.8 packet order: ANIMATION (swing) BEFORE INTERACT (attack).
        // Vanilla clickMouse calls swingItem() first, then attackEntity() sends
        // the C02. Reversing this makes Grim PacketOrderB flag "pre-attack".
        if (swing)
            p.swingItem();
        c.attackEntity(p, target);
        return true;
    }

    /**
     * OpenMyau attack-slow + Grim sprint sync.
     *
     * <p>Vanilla {@code attackTargetEntityWithCurrentItem} already applies
     * {@code motion *= 0.6} + {@code setSprinting(false)} when the client was
     * sprinting — do <b>not</b> gap-fill 0.6× when only packet-sprint is true:
     * that slows walk-speed motion and produces Simulation ~0.030 friction decay.
     *
     * <p>If {@code serverSprintState} is stale true while the client is not
     * sprinting, send a single STOP so Grim's {@code lastSprinting} can clear;
     * never multiply motion in that case.
     */
    public static void applyVanillaAttackSlowdown(EntityPlayerSP player,
            boolean wasClientSprinting, boolean wasServerSprinting) {
        if (player == null)
            return;
        if (player.hurtTime > 0)
            return;
        // Stale packet sprint without client sprint: sync STOP only (no motion scale).
        if (wasServerSprinting && !wasClientSprinting) {
            setSprintKeyState(false);
            setClientSprinting(player, false);
            sendSprintActionPacket(player, false);
            return;
        }
        if (wasClientSprinting) {
            setSprintKeyState(false);
            setClientSprinting(player, false);
            // STOP left to onUpdateWalkingPlayer (one C0B/tick — BadPacketsX).
        }
    }

    public static boolean sendUseItem() {
        return sendUseItem(false);
    }

    public static boolean sendUseItem(boolean clearBlockHitTimer) {
        EntityPlayerSP p = player();
        PlayerControllerMP c = controller();
        WorldClient w = world();
        if (p == null || c == null || w == null)
            return false;
        ItemStack stack = p.getHeldItem();
        if (stack == null)
            return false;
        if (clearBlockHitTimer)
            clearBlockHitDelay();
        return c.sendUseItem(p, w, stack);
    }

    public static void clearBlockHitDelay() {
        PlayerControllerMP c = controller();
        if (c != null)
            ((IAccessorPlayerControllerMP) c).setBlockHitDelay(0);
    }

    public static boolean isHoldingSword() {
        EntityPlayerSP p = player();
        if (p == null)
            return false;
        ItemStack stack = p.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    public static boolean isUsingItem() {
        EntityPlayerSP p = player();
        return p != null && p.isUsingItem();
    }

    public static boolean isUsingItem(EntityPlayer player) {
        return player != null && player.isUsingItem();
    }

    public static boolean isBlocking() {
        EntityPlayerSP p = player();
        return p != null && p.isBlocking();
    }

    public static boolean isBlocking(EntityPlayer player) {
        return player != null && player.isBlocking();
    }

    public static boolean isClientSprinting() {
        return isClientSprinting(player());
    }

    public static boolean isClientSprinting(EntityLivingBase entity) {
        return entity != null && entity.isSprinting();
    }

    public static void setClientSprinting(EntityLivingBase player, boolean sprinting) {
        if (player == null)
            return;
        player.setSprinting(sprinting);
    }

    /** Clears 1.8 double-tap-W sprint re-arm so forced walk sticks until C0B. */
    public static void clearSprintToggleTimer(EntityPlayerSP player) {
        if (player != null)
            ((IAccessorEntityPlayerSP) player).setSprintToggleTimer(0);
    }

    public static boolean getServerSprintState() {
        return getServerSprintState(player());
    }

    public static boolean getServerSprintState(EntityPlayerSP player) {
        return player != null && ((IAccessorEntityPlayerSP) player).getServerSprintState();
    }

    public static void setServerSprintState(EntityPlayerSP player, boolean sprinting) {
        if (player != null)
            ((IAccessorEntityPlayerSP) player).setServerSprintState(sprinting);
    }

    public static void sendSprintActionPacket(EntityPlayerSP player, boolean startSprinting) {
        if (player == null)
            return;
        if (getServerSprintState(player) == startSprinting)
            return;
        // Blink holds C0B/C03 off-wire; flipping serverSprintState here desyncs Grim
        // (KeepSprint would "confirm" a walk that never arrived → AttackSlow Simulation).
        if (BlinkManager.INSTANCE.isBlinking())
            return;
        C0BPacketEntityAction.Action action = startSprinting
            ? C0BPacketEntityAction.Action.START_SPRINTING
            : C0BPacketEntityAction.Action.STOP_SPRINTING;
        C0BPacketEntityAction packet = new C0BPacketEntityAction(player, action);
        // Peek only — slot commit is in AuraCombatPacketGuard.onSend (avoids mark-then-cancel).
        if (AuraCombatPacketGuard.shouldCancelEntityAction(packet))
            return;
        addToSendQueue(packet);
        setServerSprintState(player, startSprinting);
    }

    public static void sendSneakActionPacket(EntityPlayerSP player, boolean startSneaking) {
        if (player == null)
            return;
        C0BPacketEntityAction.Action action = startSneaking
            ? C0BPacketEntityAction.Action.START_SNEAKING
            : C0BPacketEntityAction.Action.STOP_SNEAKING;
        C0BPacketEntityAction packet = new C0BPacketEntityAction(player, action);
        if (!AuraCombatPacketGuard.shouldCancelEntityAction(packet)) {
            addToSendQueue(packet);
            ((IAccessorEntityPlayerSP) player).setServerSneakState(startSneaking);
        }
    }

    public static float getYaw() {
        EntityPlayerSP p = player();
        return p != null ? p.rotationYaw : 0.0f;
    }

    public static float getPitch() {
        EntityPlayerSP p = player();
        return p != null ? p.rotationPitch : 0.0f;
    }

    public static boolean isOnGround() {
        EntityPlayerSP p = player();
        return p != null && p.onGround;
    }

    public static void setOnGround(Entity entity, boolean onGround) {
        if (entity != null)
            entity.onGround = onGround;
    }

    public static boolean isSneaking() {
        EntityPlayerSP p = player();
        return p != null && p.isSneaking();
    }

    public static boolean isSneaking(Entity entity) {
        return entity != null && entity.isSneaking();
    }

    public static void setSneaking(Entity entity, boolean sneak) {
        if (entity != null)
            entity.setSneaking(sneak);
    }

    public static void setRotation(float yaw, float pitch) {
        EntityPlayerSP p = player();
        if (p == null)
            return;
        p.rotationYaw = yaw;
        p.rotationPitch = Math.max(-90.0f, Math.min(90.0f, pitch));
    }

    public static void setMotion(double x, double y, double z) {
        EntityPlayerSP p = player();
        if (p == null)
            return;
        p.motionX = x;
        p.motionY = y;
        p.motionZ = z;
    }

    public static double getMotionX() {
        EntityPlayerSP p = player();
        return p != null ? p.motionX : 0.0;
    }

    public static double getMotionY() {
        EntityPlayerSP p = player();
        return p != null ? p.motionY : 0.0;
    }

    public static double getMotionZ() {
        EntityPlayerSP p = player();
        return p != null ? p.motionZ : 0.0;
    }

    public static Entity getRidingEntity(Entity entity) {
        return entity != null ? entity.ridingEntity : null;
    }

    public static boolean isRiding() {
        return getRidingEntity(player()) != null;
    }

    public static void setEntityMotion(Entity entity, double x, double y, double z) {
        if (entity == null)
            return;
        entity.motionX = x;
        entity.motionY = y;
        entity.motionZ = z;
    }

    public static int getHurtTime() {
        EntityPlayerSP p = player();
        return p != null ? p.hurtTime : 0;
    }

    public static int getHurtTime(EntityLivingBase entity) {
        return entity != null ? entity.hurtTime : 0;
    }

    public static int getMaxHurtTime(EntityLivingBase entity) {
        return entity != null ? entity.maxHurtTime : 0;
    }

    public static int getDeathTime(EntityLivingBase entity) {
        return entity != null ? entity.deathTime : 0;
    }

    public static float getHealth() {
        EntityPlayerSP p = player();
        return p != null ? p.getHealth() : 0.0f;
    }

    public static float getHealth(EntityLivingBase entity) {
        return entity != null ? entity.getHealth() : 0.0f;
    }

    public static boolean isDead(Entity entity) {
        return entity == null || entity.isDead;
    }

    public static int entityId(Entity entity) {
        return entity != null ? entity.getEntityId() : -1;
    }

    public static int getHotbarSlot(EntityPlayer player) {
        return player != null ? player.inventory.currentItem : -1;
    }

    public static void setHotbarSlot(EntityPlayer player, int slot) {
        if (player != null)
            player.inventory.currentItem = slot;
    }

    public static ItemStack getStackInSlot(InventoryPlayer inv, int slot) {
        return inv != null ? inv.getStackInSlot(slot) : null;
    }

    public static void clearItemInUse(EntityPlayer player) {
        if (player != null)
            player.clearItemInUse();
    }

    public static void setItemInUse(EntityPlayer player, boolean active) {
        if (player == null)
            return;
        if (!active) {
            player.clearItemInUse();
            return;
        }
        ItemStack stack = player.getHeldItem();
        if (stack != null)
            player.setItemInUse(stack, stack.getMaxItemUseDuration());
    }

    public static void clearJumpTicks(EntityLivingBase living) {
        if (living != null)
            ((gnu.client.mixin.impl.accessors.IAccessorEntityLivingBase) living).setJumpTicks(0);
    }

    // ---- keybinds ----

    public static void setKeyBindState(KeyBinding bind, boolean pressed) {
        if (bind != null)
            KeyBinding.setKeyBindState(bind.getKeyCode(), pressed);
    }

    public static void setForwardKeyState(boolean pressed) {
        setKeyBindState(settings().keyBindForward, pressed);
    }

    public static void setBackKeyState(boolean pressed) {
        setKeyBindState(settings().keyBindBack, pressed);
    }

    public static void setLeftKeyState(boolean pressed) {
        setKeyBindState(settings().keyBindLeft, pressed);
    }

    public static void setRightKeyState(boolean pressed) {
        setKeyBindState(settings().keyBindRight, pressed);
    }

    public static void setAttackKeyState(boolean pressed) {
        setKeyBindState(settings().keyBindAttack, pressed);
    }

    public static void setUseItemKeyState(boolean pressed) {
        setKeyBindState(settings().keyBindUseItem, pressed);
    }

    public static void setSprintKeyState(boolean pressed) {
        setKeyBindState(settings().keyBindSprint, pressed);
    }

    public static void setJumpInput(EntityPlayerSP player, boolean jump) {
        if (player == null)
            return;
        if (player.movementInput != null)
            player.movementInput.jump = jump;
        setKeyBindState(settings().keyBindJump, jump);
    }

    public static void setMovementInput(net.minecraft.util.MovementInput movInput,
                                        float moveForward, float moveStrafe, boolean jump) {
        if (movInput == null)
            return;
        movInput.moveForward = moveForward;
        movInput.moveStrafe = moveStrafe;
        movInput.jump = jump;
    }

    public static void pressAttackKeyOnce() {
        KeyBinding.onTick(settings().keyBindAttack.getKeyCode());
    }

    public static void pressUseItemKeyOnce() {
        KeyBinding.onTick(settings().keyBindUseItem.getKeyCode());
    }

    public static void updateAttackKeyState() {
        setAttackKeyState(isPhysicalLmbDown());
    }

    public static boolean isAttackKeyDown() {
        return settings().keyBindAttack.isKeyDown();
    }

    public static boolean isForwardKeyHeld() {
        return isPhysicalKeyBindDown(settings().keyBindForward);
    }

    public static boolean isBackKeyHeld() {
        return isPhysicalKeyBindDown(settings().keyBindBack);
    }

    public static boolean isLeftKeyHeld() {
        return isPhysicalKeyBindDown(settings().keyBindLeft);
    }

    public static boolean isRightKeyHeld() {
        return isPhysicalKeyBindDown(settings().keyBindRight);
    }

    public static boolean isJumpKeyHeld() {
        return isPhysicalKeyBindDown(settings().keyBindJump);
    }

    public static boolean isSneakKeyHeld() {
        return isPhysicalKeyBindDown(settings().keyBindSneak);
    }

    public static boolean isMovementKeyHeld() {
        return isForwardKeyHeld() || isBackKeyHeld() || isLeftKeyHeld() || isRightKeyHeld();
    }

    public static boolean isUseItemKeyDown() {
        return settings().keyBindUseItem.isKeyDown();
    }

    public static boolean isPhysicalKeyBindDown(KeyBinding bind) {
        if (bind == null)
            return false;
        int code = bind.getKeyCode();
        try {
            if (code < 0)
                return Mouse.isButtonDown(code + 100);
            return Keyboard.isKeyDown(code);
        } catch (Throwable t) {
            return bind.isKeyDown();
        }
    }

    public static boolean isPhysicalLmbDown() {
        try {
            return Mouse.isButtonDown(0);
        } catch (Throwable t) {
            return ClientBootstrap.isLeftMouseDown();
        }
    }

    public static boolean isPhysicalRmbDown() {
        try {
            return Mouse.isButtonDown(1);
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- entity position / motion (script API) ----

    public static double entityPosX(Entity entity) {
        return entity != null ? entity.posX : 0.0;
    }

    public static double entityPosY(Entity entity) {
        return entity != null ? entity.posY : 0.0;
    }

    public static double entityPosZ(Entity entity) {
        return entity != null ? entity.posZ : 0.0;
    }

    public static void setEntityPosition(Entity entity, double x, double y, double z) {
        if (entity != null)
            entity.setPosition(x, y, z);
    }

    public static void setEntityVelocity(Entity entity, double x, double y, double z) {
        if (entity != null)
            entity.setVelocity(x, y, z);
    }

    public static void setEntityYaw(Entity entity, float yaw) {
        if (entity == null)
            return;
        entity.rotationYaw = yaw;
        entity.prevRotationYaw = yaw;
    }

    public static MovingObjectPosition raycastBlocks(double distance, float yaw, float pitch) {
        EntityPlayerSP p = player();
        WorldClient w = world();
        if (p == null || w == null)
            return null;
        Vec3 eyes = p.getPositionEyes(1.0f);
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosPitch = (float) Math.cos(pitchRad);
        float dx = -(float) Math.sin(yawRad) * cosPitch;
        float dy = -(float) Math.sin(pitchRad);
        float dz = (float) Math.cos(yawRad) * cosPitch;
        Vec3 end = new Vec3(
                eyes.xCoord + dx * distance,
                eyes.yCoord + dy * distance,
                eyes.zCoord + dz * distance);
        return w.rayTraceBlocks(eyes, end, false, false, false);
    }

    public static void sendSteerVehicle(float strafe, float forward, boolean jump, boolean unmount) {
        addToSendQueue(new C0CPacketInput(strafe, forward, jump, unmount));
    }

    public static void sendReleaseUseItem(EntityPlayer player) {
        if (player == null)
            return;
        // addToSendQueue (not PacketUtil.sendPacket) so BlinkManager can hold when AUTO_BLOCK.
        addToSendQueue(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN));
    }

    public static void sendUseItemBlockPlacement(ItemStack stack) {
        if (stack == null)
            return;
        // addToSendQueue (not PacketUtil.sendPacket) so BlinkManager can hold when AUTO_BLOCK.
        addToSendQueue(new C08PacketPlayerBlockPlacement(stack));
    }

    public static void startSwordBlock(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null)
            return;
        sendUseItemBlockPlacement(stack);
        player.setItemInUse(stack, stack.getMaxItemUseDuration());
    }

    public static void stopSwordBlock(EntityPlayer player) {
        sendReleaseUseItem(player);
        if (player != null)
            player.stopUsingItem();
    }

    public static void sendHeldItemChange(int slot) {
        if (slot < 0 || slot > 8)
            return;
        addToSendQueue(new C09PacketHeldItemChange(slot));
    }

    public static void sendHeldItemChangeFlicker() {
        EntityPlayerSP p = player();
        if (p == null)
            return;
        int slot = p.inventory.currentItem;
        if (slot < 0 || slot > 8)
            return;
        sendHeldItemChange((slot + 1) % 9);
        sendHeldItemChange(slot);
    }

    public static float getMaxHealth() {
        return getMaxHealth(player());
    }

    public static float getMaxHealth(EntityLivingBase entity) {
        return entity != null ? entity.getMaxHealth() : 0.0f;
    }

    public static float getAbsorption() {
        return getAbsorption(player());
    }

    public static float getAbsorption(EntityLivingBase entity) {
        return entity != null ? entity.getAbsorptionAmount() : 0.0f;
    }

    public static boolean isAlive() {
        return isAlive(player());
    }

    public static boolean isAlive(Entity entity) {
        if (entity == null || entity.isDead)
            return false;
        if (entity instanceof EntityLivingBase)
            return ((EntityLivingBase) entity).deathTime <= 0;
        return true;
    }

    public static boolean isSwingInProgress() {
        return isSwingInProgress(player());
    }

    public static boolean isSwingInProgress(EntityLivingBase entity) {
        return entity != null && entity.isSwingInProgress;
    }

    public static ItemStack getHeldItemStack() {
        return getHeldItemStack(player());
    }

    public static ItemStack getHeldItemStack(EntityPlayer player) {
        return player != null ? player.getHeldItem() : null;
    }

    public static boolean isHoldingBlock() {
        EntityPlayerSP p = player();
        if (p == null)
            return false;
        ItemStack stack = p.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemBlock;
    }

    public static boolean isHoldingBow() {
        EntityPlayerSP p = player();
        if (p == null)
            return false;
        ItemStack stack = p.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemBow;
    }

    public static boolean isHoldingConsumable() {
        EntityPlayerSP p = player();
        if (p == null)
            return false;
        ItemStack stack = p.getHeldItem();
        if (stack == null)
            return false;
        if (stack.getItem() instanceof ItemFood || stack.getItem() instanceof ItemBucketMilk)
            return true;
        if (stack.getItem() instanceof ItemPotion)
            return stack.getMetadata() <= 16384;
        return false;
    }

    public static boolean isInWater() {
        return isInWater(player());
    }

    public static boolean isInWater(Entity entity) {
        return entity != null && entity.isInWater();
    }

    public static IBlockState getBlockState(World world, int x, int y, int z) {
        return world != null ? world.getBlockState(new BlockPos(x, y, z)) : null;
    }

    private static final Random SHARED_RANDOM = new Random();

    public static int randomInt(int min, int max) {
        if (max < min)
            return min;
        return min + SHARED_RANDOM.nextInt(max - min + 1);
    }

    public static double randomDouble(double min, double max) {
        if (max < min)
            return min;
        return min + SHARED_RANDOM.nextDouble() * (max - min);
    }

    // -------------------- script-facing inventory / reconnect helpers --------------------

    private static ServerData lastServer;

    public static boolean hasScreen() {
        return currentScreen() != null;
    }

    public static boolean isInventoryScreen() {
        GuiScreen screen = currentScreen();
        return screen instanceof GuiInventory || screen instanceof GuiContainer;
    }

    /** Cache the current multiplayer server while connected. */
    public static void rememberServer() {
        ServerData current = mc().getCurrentServerData();
        if (current == null)
            return;
        if (lastServer == null)
            lastServer = new ServerData(current.serverName, current.serverIP, current.isOnLAN());
        lastServer.copyFrom(current);
    }

    public static boolean hasLastServer() {
        return lastServer != null && lastServer.serverIP != null && !lastServer.serverIP.isEmpty();
    }

    public static String getLastServerIp() {
        return lastServer == null ? "" : (lastServer.serverIP == null ? "" : lastServer.serverIP);
    }

    /** Open the connecting screen for the last remembered server. */
    public static boolean reconnectToLastServer() {
        if (!hasLastServer())
            return false;
        Minecraft minecraft = mc();
        minecraft.displayGuiScreen(new GuiConnecting(new GnuMainMenuScreen(), minecraft, lastServer));
        return true;
    }

    public static boolean isSoup(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSoup;
    }

    public static boolean isFood(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemFood;
    }

    public static boolean isArmor(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemArmor;
    }

    public static String getItemName(ItemStack stack) {
        if (stack == null || stack.getItem() == null)
            return "";
        try {
            return stack.getDisplayName();
        } catch (Throwable t) {
            return stack.getItem().getUnlocalizedName();
        }
    }

    /**
     * Armor inventory index 0=boots … 3=helmet, matching {@link InventoryPlayer#armorInventory}.
     */
    public static ItemStack getArmorStack(int armorSlot) {
        EntityPlayerSP player = player();
        if (player == null || player.inventory == null || armorSlot < 0 || armorSlot > 3)
            return null;
        return player.inventory.armorItemInSlot(armorSlot);
    }

    public static int getArmorValue(ItemStack stack) {
        if (!isArmor(stack))
            return -1;
        return ((ItemArmor) stack.getItem()).damageReduceAmount;
    }

    /** ItemArmor.armorType: 0=helmet, 1=chest, 2=legs, 3=boots. */
    public static int getArmorType(ItemStack stack) {
        if (!isArmor(stack))
            return -1;
        return ((ItemArmor) stack.getItem()).armorType;
    }

    public static boolean windowClick(int windowId, int slotId, int button, int mode) {
        EntityPlayerSP player = player();
        PlayerControllerMP controller = controller();
        if (player == null || controller == null)
            return false;
        controller.windowClick(windowId, slotId, button, mode, player);
        return true;
    }

    public static boolean clickPlayerSlot(int containerSlot, int button, int mode) {
        EntityPlayerSP player = player();
        if (player == null || player.openContainer == null)
            return false;
        return windowClick(player.openContainer.windowId, containerSlot, button, mode);
    }

    /**
     * Map InventoryPlayer index (0–35 main/hotbar, 36–39 armor boots→helmet)
     * to ContainerPlayer slot ids while the player inventory is open.
     */
    public static int inventoryToContainerSlot(int invSlot) {
        if (invSlot >= 0 && invSlot <= 8)
            return 36 + invSlot; // hotbar
        if (invSlot >= 9 && invSlot <= 35)
            return invSlot; // main
        if (invSlot >= 36 && invSlot <= 39) {
            int armorInv = invSlot - 36; // 0 boots .. 3 helmet
            return 8 - armorInv; // container 8 boots .. 5 helmet
        }
        return -1;
    }

    /** Container armor slot for ItemArmor.armorType (0 helmet → slot 5 … 3 boots → slot 8). */
    public static int armorContainerSlot(int armorType) {
        if (armorType < 0 || armorType > 3)
            return -1;
        return 5 + armorType;
    }

    /**
     * Equip the best available armor from main inventory into empty/worse slots.
     * Uses shift-click (mode 1) when the player inventory container is open; otherwise
     * opens nothing — caller should open inventory or use while already in GuiInventory.
     * Works with the default player container even without a GUI open.
     */
    public static int equipBestArmor() {
        EntityPlayerSP player = player();
        if (player == null || player.inventory == null || player.openContainer == null)
            return 0;
        int equipped = 0;
        for (int armorType = 0; armorType <= 3; armorType++) {
            int bestInvSlot = -1;
            int bestValue = -1;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (!isArmor(stack) || getArmorType(stack) != armorType)
                    continue;
                int value = getArmorValue(stack);
                if (value > bestValue) {
                    bestValue = value;
                    bestInvSlot = i;
                }
            }
            if (bestInvSlot < 0)
                continue;
            int armorInvIndex = 3 - armorType; // boots=0 … helmet=3
            ItemStack worn = player.inventory.armorItemInSlot(armorInvIndex);
            int wornValue = getArmorValue(worn);
            if (bestValue <= wornValue)
                continue;
            int fromSlot = inventoryToContainerSlot(bestInvSlot);
            if (fromSlot < 0)
                continue;
            // Shift-click moves armor into the matching armor slot when possible.
            if (clickPlayerSlot(fromSlot, 0, 1))
                equipped++;
        }
        return equipped;
    }

    public static Block getBlockFromHit(MovingObjectPosition mop) {
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return null;
        BlockPos pos = mop.getBlockPos();
        World w = world();
        if (pos == null || w == null)
            return null;
        IBlockState state = w.getBlockState(pos);
        return state == null ? null : state.getBlock();
    }

    public static int[] raycastBlockPos(double distance, float yaw, float pitch) {
        MovingObjectPosition hit = raycastBlocks(distance, yaw, pitch);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.getBlockPos() == null)
            return null;
        BlockPos pos = hit.getBlockPos();
        return new int[] { pos.getX(), pos.getY(), pos.getZ() };
    }

    public static float getDigSpeed(ItemStack stack, Block block) {
        if (block == null)
            return 0f;
        return gnu.client.utility.BlockUtils.getBlockHardness(block, stack, false, false);
    }

    /** Best hotbar slot (0–8) for digging {@code block}, or {@code -1}. */
    public static int findBestHotbarTool(Block block) {
        EntityPlayerSP player = player();
        if (player == null || player.inventory == null || block == null)
            return -1;
        int bestSlot = -1;
        float best = -1f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            float speed = getDigSpeed(stack, block);
            if (speed > best) {
                best = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static void sendConfirmTransaction(int windowId, short uid, boolean accepted) {
        addToSendQueue(new C0FPacketConfirmTransaction(windowId, uid, accepted));
    }

    public static void sendDig(C07PacketPlayerDigging.Action action, int x, int y, int z, int faceOrdinal) {
        if (action == null)
            return;
        EnumFacing face = EnumFacing.VALUES[Math.max(0, Math.min(faceOrdinal, EnumFacing.VALUES.length - 1))];
        addToSendQueue(new C07PacketPlayerDigging(action, new BlockPos(x, y, z), face));
    }
}
