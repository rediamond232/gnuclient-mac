package gnu.client.module.modules.player;

import gnu.client.event.PostMotionEvent;
import gnu.client.event.PostUpdateEvent;
import gnu.client.event.PrePlayerMovementInputEvent;
import gnu.client.event.PreUpdateEvent;
import gnu.client.event.RightClickMouseEvent;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.modules.combat.KillAuraModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.mixin.impl.accessors.IAccessorKeyBinding;
import gnu.client.mixin.impl.accessors.IAccessorPlayerControllerMP;
import gnu.client.runtime.FloatManager;
import gnu.client.runtime.FloatModules;
import gnu.client.runtime.mc.Mc;
import gnu.client.runtime.packet.PacketEvents;
import gnu.client.runtime.packet.PacketListener;
import gnu.client.utility.BlockUtils;
import gnu.client.utility.TeamUtil;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * OpenMyau {@code NoSlow} — faithful port. All 13 sword / 11 food / 11 bow modes,
 * the Miau subsystem (NCP..OpalWatchdog), inlined BadPacketsComponent, and the Opal
 * 3-tick state machine.
 *
 * <p>Event mapping (OpenMyau {@code ->} gnuclient):
 * <ul>
 *   <li>{@code UpdateEvent PRE/POST} {@code ->} {@link PreUpdateEvent}/{@link PostUpdateEvent}</li>
 *   <li>{@code PostMotionEvent} {@code ->} {@link PostMotionEvent}</li>
 *   <li>{@code LivingUpdateEvent} {@code ->} {@link PrePlayerMovementInputEvent} (movement scaling)</li>
 *   <li>{@code PlayerUpdateEvent} {@code ->} {@code onTickStart} (FloatManager)</li>
 *   <li>{@code RightClickMouseEvent} {@code ->} {@link RightClickMouseEvent}</li>
 *   <li>{@code PacketEvent SEND/RECEIVE} {@code ->} {@link PacketListener#onSend}/{@link PacketListener#onReceive}</li>
 * </ul>
 *
 * <p>Movement scaling is consolidated into {@link PrePlayerMovementInputEvent}, which fires
 * right before {@code moveEntityWithHeading} (after {@code updatePlayerMoveState}), matching
 * OpenMyau's effective behavior.
 */
public final class NoSlowModule extends Module implements PacketListener {

    private static final List<String> SWORD_MODES = Arrays.asList(
            "None", "Vanilla", "Hypixel", "NCP", "NewNCP", "Watchdog", "Intave", "Grim",
            "NewGrim", "Verus", "AAC", "Spartan", "OpalWatchdog");
    private static final List<String> FOOD_MODES = Arrays.asList(
            "None", "Vanilla", "Float", "NCP", "NewNCP", "Watchdog", "Intave", "Grim",
            "NewGrim", "Verus", "AAC", "Spartan", "OpalWatchdog");
    private static final List<String> BOW_MODES = Arrays.asList(
            "None", "Vanilla", "Float", "NCP", "NewNCP", "Watchdog", "Intave", "Grim",
            "NewGrim", "Verus", "AAC", "Spartan", "OpalWatchdog");

    // Miau Client NoSlow mode indices (appended after the OpenMyau base modes).
    private static final int MIAU_BASE = 3;
    private static final int MIAU_NCP = 0;
    private static final int MIAU_NEW_NCP = 1;
    private static final int MIAU_WATCHDOG = 2;
    private static final int MIAU_INTAVE = 3;
    private static final int MIAU_GRIM = 4;
    private static final int MIAU_NEW_GRIM = 5;
    private static final int MIAU_VERUS = 6;
    private static final int MIAU_AAC = 7;
    private static final int MIAU_SPARTAN = 8;
    private static final int MIAU_OPAL_WATCHDOG = 9;

    private final ModeSetting swordMode = addSetting(new ModeSetting("Sword Mode", 1, SWORD_MODES));
    private final SliderSetting swapDelay = addSetting(new SliderSetting("Swap Delay", 0.0f, 0.0f, 3.0f, 1.0f)
            .visibleWhen(() -> swordMode.getValue() == 2));
    private final BoolSetting noAttack = addSetting(new BoolSetting("No Attack", false)
            .visibleWhen(() -> swordMode.getValue() == 2));
    private final SliderSetting swordMotion = addSetting(new SliderSetting("Sword Motion", 100.0f, 0.0f, 100.0f, 1.0f)
            .visibleWhen(() -> swordMode.getValue() != 0));
    private final BoolSetting swordSprint = addSetting(new BoolSetting("Sword Sprint", true)
            .visibleWhen(() -> swordMode.getValue() != 0));
    private final BoolSetting onlyKillAuraAutoBlock = addSetting(new BoolSetting("Only Kill Aura Auto Block", false)
            .visibleWhen(() -> swordMode.getValue() != 0));
    private final ModeSetting foodMode = addSetting(new ModeSetting("Food Mode", 0, FOOD_MODES));
    private final SliderSetting foodMotion = addSetting(new SliderSetting("Food Motion", 100.0f, 0.0f, 100.0f, 1.0f)
            .visibleWhen(() -> foodMode.getValue() != 0));
    private final BoolSetting foodSprint = addSetting(new BoolSetting("Food Sprint", true)
            .visibleWhen(() -> foodMode.getValue() != 0));
    private final ModeSetting bowMode = addSetting(new ModeSetting("Bow Mode", 0, BOW_MODES));
    private final SliderSetting bowMotion = addSetting(new SliderSetting("Bow Motion", 100.0f, 0.0f, 100.0f, 1.0f)
            .visibleWhen(() -> bowMode.getValue() != 0));
    private final BoolSetting bowSprint = addSetting(new BoolSetting("Bow Sprint", true)
            .visibleWhen(() -> bowMode.getValue() != 0));
    private final BoolSetting antiSwitch = addSetting(new BoolSetting("Anti-Switch", false)
            .visibleWhen(this::hasMiauMode));

    // Inlined BadPacketsComponent.
    private boolean bpSlot, bpAttack, bpSwing, bpBlock, bpInventory;
    private boolean savedSlot, savedAttack, savedSwing, savedBlock, savedInventory;

    // NewNCP / Intave / Spartan state.
    private int newNcpDisable;
    private int intaveDisable;
    private int spartanDisable;

    // Watchdog state.
    private int wdOffGroundTicks;
    private boolean wdStop;
    private boolean wdDisable;

    // NewGrim state.
    private int newGrimTicks;

    // OpalWatchdog state.
    private int opalNextCycleTick = -1;
    private boolean opalRunThisTick;
    private boolean opalStopUse;
    private boolean opalBlocking;
    private int opalSlotChangeTick = -1;

    private int delay;
    private boolean post;

    public NoSlowModule() {
        super("NoSlow", "Cancel item-use slowdown (sword/food/bow)", Category.PLAYER);
    }

    public static NoSlowModule instance() {
        Module module = ModuleManager.instance().getModule("NoSlow");
        return module instanceof NoSlowModule ? (NoSlowModule) module : null;
    }

    // ── OpenMyau base-mode API ────────────────────────────────────────────

    public boolean isSwordActive() {
        return swordMode.getValue() != 0 && Mc.isHoldingSword()
                && (!onlyKillAuraAutoBlock.getValue() || isKillAuraAutoBlocking());
    }

    public boolean isFoodActive() {
        return foodMode.getValue() != 0 && isEating();
    }

    public boolean isBowActive() {
        return bowMode.getValue() != 0 && Mc.isHoldingBow();
    }

    /** OpenMyau {@code ItemUtil.isEating} — EAT/DRINK, splash potions excluded. */
    private boolean isEating() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        return isEatingStack(player.getHeldItem());
    }

    public boolean isFloatMode() {
        return foodMode.getValue() == 2 && isEating()
                || bowMode.getValue() == 2 && Mc.isHoldingBow();
    }

    private boolean isKillAuraAutoBlocking() {
        KillAuraModule aura = killAura();
        if (!aura.isPlayerBlocking() || !aura.isEnabled())
            return false;
        return aura.isBlocking();
    }

    public boolean isAnyActive() {
        if (swordMode.getValue() != 2) {
            return Mc.isUsingItem() && (isSwordActive() || isFoodActive() || isBowActive());
        } else if (swordMode.getValue() == 2 && isSwordActive()) {
            KillAuraModule killAura = killAura();
            if (!noAttack.getValue() || !noAttackSuppressed(killAura)) {
                return delay == 0;
            }
        }
        return false;
    }

    public boolean canSprint() {
        return isSwordActive() && swordSprint.getValue()
                || isFoodActive() && foodSprint.getValue()
                || isBowActive() && bowSprint.getValue();
    }

    public int getMotionMultiplier() {
        if (Mc.isHoldingSword()) {
            return Math.round(swordMotion.getValue());
        } else if (isEating()) {
            return Math.round(foodMotion.getValue());
        } else {
            return Mc.isHoldingBow() ? Math.round(bowMotion.getValue()) : 100;
        }
    }

    // ── Miau API (consumed by MixinEntityPlayerSPNoSlow) ─────────────────

    public boolean isMiauAntiSwitchActive() {
        if (!isEnabled() || !antiSwitch.getValue() || Mc.player() == null || Mc.world() == null)
            return false;
        ItemStack heldItem = Mc.player().getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemSword))
            return false;
        return Mc.player().isUsingItem();
    }

    public boolean isMiauAnyActive() {
        EntityPlayerSP p = Mc.player();
        if (p == null || !p.isUsingItem())
            return false;
        int mode = heldItemMiauMode();
        return mode >= 0 && miauAnyActive(mode);
    }

    public float getMiauMotionMultiplier() {
        if (heldItemMiauMode() == MIAU_GRIM)
            return 0.35f;
        return 1.0f;
    }

    public boolean shouldCancelMiauSlowdown() {
        if (!isEnabled())
            return false;
        if (heldItemMiauMode() == MIAU_NEW_GRIM) {
            if (!miauAnyActive(MIAU_NEW_GRIM)) {
                newGrimTicks = 0;
                return false;
            }
            newGrimTicks++;
            if (newGrimTicks >= 2) {
                newGrimTicks = 0;
                return true;
            }
            return false;
        }
        return true;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        PacketEvents.register(this);
        MinecraftForge.EVENT_BUS.register(this);
        newGrimTicks = 0;
    }

    @Override
    public void onDisable() {
        PacketEvents.unregister(this);
        MinecraftForge.EVENT_BUS.unregister(this);
        newGrimTicks = 0;
        if (isMiauModeUsed(MIAU_OPAL_WATCHDOG) && Mc.player() != null) {
            opalRelease();
            opalResetCycle();
        }
    }

    @Override
    public void onTickStart() {
        // OpenMyau onPlayerUpdate(PlayerUpdateEvent): FloatManager float state.
        if (isFloatMode()) {
            FloatManager.INSTANCE.setFloatState(true, FloatModules.NO_SLOW);
        } else {
            FloatManager.INSTANCE.setFloatState(false, FloatModules.NO_SLOW);
        }
    }

    // ── Update events (OpenMyau UpdateEvent PRE/POST) ─────────────────────

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        EntityPlayerSP p = Mc.player();
        if (p == null || Mc.world() == null)
            return;
        // BadPackets bookkeeping: save on PRE.
        savedSlot = bpSlot;
        savedAttack = bpAttack;
        savedSwing = bpSwing;
        savedBlock = bpBlock;
        savedInventory = bpInventory;
        if (!isEnabled())
            return;
        updateMiauPre();
        if (Mc.isHoldingSword() && p.isUsingItem()) {
            if (isSwordActive()) {
                if (swordMode.getValue() == 2) {
                    delay--;
                    if (delay < 0) {
                        KillAuraModule killAura = killAura();
                        if (!noAttack.getValue() || !noAttackSuppressed(killAura)) {
                            int randomSlot = new Random().nextInt(9);
                            while (randomSlot == p.inventory.currentItem) {
                                randomSlot = new Random().nextInt(9);
                            }
                            sendPacket(new C09PacketHeldItemChange(randomSlot));
                            sendPacket(new C09PacketHeldItemChange(p.inventory.currentItem));
                        }
                        post = true;
                        delay = Math.round(swapDelay.getValue());
                    }
                }
            }
        } else {
            if (post) {
                post = false;
            }
        }
    }

    @SubscribeEvent
    public void onPostUpdate(PostUpdateEvent event) {
        if (Mc.player() == null || Mc.world() == null)
            return;
        // BadPackets reset on POST (runs even while disabled).
        resetBadPackets();
        if (!isEnabled())
            return;
        updateMiauPost();
    }

    @SubscribeEvent
    public void onPostMotion(PostMotionEvent event) {
        if (!isEnabled())
            return;
        EntityPlayerSP p = Mc.player();
        if (p == null || !Mc.isHoldingSword() || !p.isUsingItem())
            return;
        if (isSwordActive()) {
            if (swordMode.getValue() == 2) {
                if (post) {
                    post = false;
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerMovementInput(PrePlayerMovementInputEvent event) {
        // OpenMyau onLivingUpdate(LivingUpdateEvent) + Miau motion scaling, consolidated.
        if (!isEnabled())
            return;
        float forward = event.forward;
        float strafe = event.strafe;
        if (isAnyActive()) {
            float multiplier = (float) getMotionMultiplier() / 100.0f;
            forward *= multiplier;
            strafe *= multiplier;
            if (!canSprint()) {
                setSprinting(false);
            }
        }
        applyMiauMotionScaling();
        event.forward = forward;
        event.strafe = strafe;
    }

    @SubscribeEvent
    public void onRightClick(RightClickMouseEvent event) {
        if (!isEnabled())
            return;
        if (Mc.objectMouseOver() != null) {
            switch (Mc.objectMouseOver().typeOfHit) {
                case BLOCK:
                    BlockPos blockPos = Mc.objectMouseOver().getBlockPos();
                    if (BlockUtils.isInteractable(blockPos) && !Mc.isSneaking()) {
                        return;
                    }
                    break;
                case ENTITY:
                    Entity entityHit = Mc.objectMouseOver().entityHit;
                    if (entityHit instanceof EntityVillager) {
                        return;
                    }
                    if (entityHit instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) entityHit)) {
                        return;
                    }
            }
        }
        EntityPlayerSP p = Mc.player();
        if (isFloatMode() && !FloatManager.INSTANCE.isPredicted() && p != null && p.onGround) {
            event.setCanceled(true);
            p.motionY = 0.42f;
        }
        int heldMiauMode = heldItemMiauMode();
        if (heldMiauMode == MIAU_WATCHDOG) {
            watchdogOnRightClick(event);
        } else if (heldMiauMode == MIAU_OPAL_WATCHDOG && miauSwordActive(MIAU_OPAL_WATCHDOG)) {
            event.setCanceled(true);
        }
    }

    // ── Packet events (OpenMyau PacketEvent SEND/RECEIVE) ─────────────────

    @Override
    public boolean onSend(Object packet) {
        trackBadPackets(packet);
        if (!isEnabled() || Mc.player() == null || Mc.world() == null)
            return false;
        if (isMiauModeUsed(MIAU_INTAVE) && packet instanceof C08PacketPlayerBlockPlacement) {
            if (miauSwordActive(MIAU_INTAVE) && !bad(false, true, true, false, false)) {
                int currentSlot = Mc.player().inventory.currentItem;
                sendPacket(new C09PacketHeldItemChange(currentSlot % 8 + 1));
                sendPacket(new C09PacketHeldItemChange(currentSlot));
            }
        }
        if (isMiauModeUsed(MIAU_OPAL_WATCHDOG)) {
            opalOnPacket(packet);
        }
        return false;
    }

    @Override
    public boolean onReceive(Object packet) {
        if (!isEnabled() || Mc.player() == null || Mc.world() == null)
            return false;
        if (packet instanceof S08PacketPlayerPosLook) {
            if (isMiauModeUsed(MIAU_NEW_NCP)) {
                newNcpDisable = 0;
            }
            if (isMiauModeUsed(MIAU_SPARTAN)) {
                spartanDisable = 0;
            }
        }
        if (isMiauModeUsed(MIAU_OPAL_WATCHDOG)) {
            opalOnPacket(packet);
        }
        return false;
    }

    private void trackBadPackets(Object packet) {
        if (packet instanceof C09PacketHeldItemChange) {
            bpSlot = true;
        } else if (packet instanceof C0APacketAnimation) {
            bpSwing = true;
        } else if (packet instanceof C02PacketUseEntity) {
            bpAttack = true;
        } else if (packet instanceof C08PacketPlayerBlockPlacement || packet instanceof C07PacketPlayerDigging) {
            bpBlock = true;
        } else if (packet instanceof C0EPacketClickWindow
                || (packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)
                || packet instanceof C0DPacketCloseWindow) {
            bpInventory = true;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[] { Math.round(swordMotion.getValue()) + "%" };
    }

    // ── Miau helpers ──────────────────────────────────────────────────────

    private int miauIdx(ModeSetting property) {
        int v = property.getValue();
        return v >= MIAU_BASE ? v - MIAU_BASE : -1;
    }

    private boolean hasMiauMode() {
        return swordMode.getValue() >= MIAU_BASE
                || foodMode.getValue() >= MIAU_BASE
                || bowMode.getValue() >= MIAU_BASE;
    }

    private boolean isMiauModeUsed(int mode) {
        return miauIdx(swordMode) == mode
                || miauIdx(foodMode) == mode
                || miauIdx(bowMode) == mode;
    }

    private int heldItemMiauMode() {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return -1;
        ItemStack held = p.getHeldItem();
        if (held == null)
            return -1;
        if (Mc.isHoldingSword()) {
            return miauIdx(swordMode);
        }
        if (isEating() || held.getItem() instanceof ItemFood || held.getItem() instanceof ItemPotion) {
            return miauIdx(foodMode);
        }
        if (held.getItem() instanceof ItemBow) {
            return miauIdx(bowMode);
        }
        return -1;
    }

    private boolean miauSwordActive(int mode) {
        return miauIdx(swordMode) == mode && Mc.isHoldingSword()
                && (!onlyKillAuraAutoBlock.getValue() || isKillAuraAutoBlocking());
    }

    private boolean miauFoodActive(int mode) {
        return miauIdx(foodMode) == mode && isEating();
    }

    private boolean miauBowActive(int mode) {
        return miauIdx(bowMode) == mode && Mc.isHoldingBow();
    }

    private boolean miauPotionActive(int mode) {
        EntityPlayerSP p = Mc.player();
        return miauIdx(foodMode) == mode
                && p != null
                && p.isUsingItem()
                && p.getHeldItem() != null
                && p.getHeldItem().getItem() instanceof ItemPotion;
    }

    private boolean miauAnyActive(int mode) {
        EntityPlayerSP p = Mc.player();
        return p != null && p.isUsingItem()
                && (miauSwordActive(mode) || miauFoodActive(mode) || miauBowActive(mode) || miauPotionActive(mode));
    }

    private boolean bad(boolean slotCheck, boolean attackCheck, boolean swingCheck, boolean blockCheck, boolean inventoryCheck) {
        return (savedSlot && slotCheck)
                || (savedAttack && attackCheck)
                || (savedSwing && swingCheck)
                || (savedBlock && blockCheck)
                || (savedInventory && inventoryCheck);
    }

    private void resetBadPackets() {
        bpSlot = false;
        bpAttack = false;
        bpSwing = false;
        bpBlock = false;
        bpInventory = false;
        savedSlot = false;
        savedAttack = false;
        savedSwing = false;
        savedBlock = false;
        savedInventory = false;
    }

    private void applyMiauMotionScaling() {
        // OpenMyau applies miau motion in update events (PRE/POST), which is overwritten by
        // updatePlayerMoveState; here we apply it in the movement-input event so it survives.
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return;
        float multiplier = getMiauMotionMultiplier();
        if (isMiauScalingFor(multiplier)) {
            p.movementInput.moveForward *= multiplier;
            p.movementInput.moveStrafe *= multiplier;
        }
        // Watchdog/Opal *5.0 scaling.
        if (isMiauModeUsed(MIAU_WATCHDOG)) {
            if (!wdDisable) {
                if (miauFoodActive(MIAU_WATCHDOG) || miauBowActive(MIAU_WATCHDOG) || miauPotionActive(MIAU_WATCHDOG)) {
                    p.movementInput.moveForward *= 5.0f;
                    p.movementInput.moveStrafe *= 5.0f;
                }
            }
            if (miauSwordActive(MIAU_WATCHDOG)) {
                p.movementInput.moveForward *= 5.0f;
                p.movementInput.moveStrafe *= 5.0f;
            }
        }
        if (isMiauModeUsed(MIAU_OPAL_WATCHDOG)) {
            if (miauFoodActive(MIAU_OPAL_WATCHDOG) || miauBowActive(MIAU_OPAL_WATCHDOG) || miauPotionActive(MIAU_OPAL_WATCHDOG)) {
                p.movementInput.moveForward *= 5.0f;
                p.movementInput.moveStrafe *= 5.0f;
            }
        }
    }

    private boolean isMiauScalingFor(float multiplier) {
        if (multiplier != 1.0f) {
            // GRIM 0.35 — applies for any active miau use.
            return isMiauModeUsed(MIAU_GRIM) && miauAnyActive(MIAU_GRIM);
        }
        return isMiauModeUsed(MIAU_NCP) && miauAnyActive(MIAU_NCP)
                || isMiauModeUsed(MIAU_NEW_NCP) && miauAnyActive(MIAU_NEW_NCP)
                || isMiauModeUsed(MIAU_INTAVE) && miauAnyActive(MIAU_INTAVE)
                || isMiauModeUsed(MIAU_VERUS) && miauAnyActive(MIAU_VERUS)
                || isMiauModeUsed(MIAU_AAC) && miauAnyActive(MIAU_AAC)
                || isMiauModeUsed(MIAU_SPARTAN) && miauAnyActive(MIAU_SPARTAN);
    }

    private KillAuraModule killAura() {
        Module module = ModuleManager.instance().getModule("KillAura");
        return module instanceof KillAuraModule ? (KillAuraModule) module : null;
    }

    private void updateMiauPre() {
        if (isMiauModeUsed(MIAU_NCP)) {
            updateMiauNCP(true);
        }
        if (isMiauModeUsed(MIAU_NEW_NCP)) {
            newNcpDisable = updateMiauC09Bypass(MIAU_NEW_NCP, newNcpDisable, false);
        }
        if (isMiauModeUsed(MIAU_WATCHDOG)) {
            updateMiauWatchdog();
        }
        if (isMiauModeUsed(MIAU_INTAVE)) {
            intaveDisable = updateMiauC09Bypass(MIAU_INTAVE, intaveDisable, false);
        }
        if (isMiauModeUsed(MIAU_GRIM)) {
            updateMiauGrim();
        }
        if (isMiauModeUsed(MIAU_VERUS)) {
            updateMiauVerus(true);
        }
        if (isMiauModeUsed(MIAU_AAC)) {
            updateMiauAAC();
        }
        if (isMiauModeUsed(MIAU_SPARTAN)) {
            spartanDisable = updateMiauC09Bypass(MIAU_SPARTAN, spartanDisable, true);
        }
        if (isMiauModeUsed(MIAU_OPAL_WATCHDOG)) {
            updateMiauOpalWatchdog();
        }
    }

    private void updateMiauPost() {
        if (isMiauModeUsed(MIAU_NCP)) {
            updateMiauNCP(false);
        }
        if (isMiauModeUsed(MIAU_VERUS)) {
            updateMiauVerus(false);
        }
    }

    private void updateMiauNCP(boolean pre) {
        if (pre) {
            if (miauSwordActive(MIAU_NCP)) {
                sendPacket(new C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            }
        } else {
            if (miauSwordActive(MIAU_NCP)) {
                sendPacket(new C08PacketPlayerBlockPlacement(Mc.player().getHeldItem()));
            }
        }
    }

    private int updateMiauC09Bypass(int mode, int disable, boolean spartanSlotCheck) {
        // Runs on PRE only (OpenMyau sends inside PRE).
        disable++;
        if (miauAnyActive(mode)) {
            KillAuraModule aura = killAura();
            if (disable > 10
                    && !bad(false, true, true, false, false)
                    && (!spartanSlotCheck || !bad(true, false, false, false, false))
                    && (aura == null || aura.getTarget() == null)) {
                int currentSlot = Mc.player().inventory.currentItem;
                sendPacket(new C09PacketHeldItemChange(currentSlot % 8 + 1));
                sendPacket(new C09PacketHeldItemChange(currentSlot));
                sendPacket(new C08PacketPlayerBlockPlacement(Mc.player().getHeldItem()));
            }
        }
        KillAuraModule aura = killAura();
        if (aura != null && aura.getTarget() != null) {
            return disable;
        }
        return disable;
    }

    private void updateMiauWatchdog() {
        EntityPlayerSP p = Mc.player();
        if (p == null || Mc.world() == null)
            return;
        // Block-under check — reset disable while standing on a block.
        if (Mc.world().getBlockState(new BlockPos(p.posX, p.posY - 1, p.posZ)).getBlock() != Blocks.air
                && !p.isUsingItem()) {
            wdDisable = false;
        }
        // Slab check.
        double posY = p.posY;
        if (Math.abs(posY - Math.round(posY)) > 0.03 && p.onGround) {
            wdDisable = true;
        }
        // offGroundTicks tracking for non-sword items.
        if (p.isUsingItem()
                && !(p.getHeldItem() != null && p.getHeldItem().getItem() instanceof ItemSword)) {
            if (p.onGround) {
                wdOffGroundTicks = 0;
            } else {
                wdOffGroundTicks++;
            }
            if (wdOffGroundTicks >= 2) {
                wdStop = false;
            } else if (p.onGround && !wdDisable) {
                p.posY += 1E-14;
            }
        }
        // Sword NoSlow: C09 swap.
        if (miauSwordActive(MIAU_WATCHDOG)) {
            int currentSlot = p.inventory.currentItem;
            sendPacket(new C09PacketHeldItemChange(currentSlot % 7 + (int) (Math.random() * 2) + 1));
            sendPacket(new C09PacketHeldItemChange(currentSlot));
        }
    }

    private void watchdogOnRightClick(RightClickMouseEvent event) {
        EntityPlayerSP p = Mc.player();
        if (p == null || p.getHeldItem() == null)
            return;
        if (p.isUsingItem()
                || (p.getHeldItem().getItem() instanceof ItemPotion
                && !ItemPotion.isSplash(p.getHeldItem().getMetadata()))
                || p.getHeldItem().getItem() instanceof ItemFood
                || p.getHeldItem().getItem() instanceof ItemBow) {
            if (wdOffGroundTicks < 2 && wdOffGroundTicks != 0 && !wdDisable) {
                event.setCanceled(true);
            } else if (p.onGround) {
                p.jump();
                event.setCanceled(true);
            }
        }
    }

    private void updateMiauGrim() {
        if (miauAnyActive(MIAU_GRIM)) {
            // PRE only (OpenMyau sends in PRE).
            int currentSlot = Mc.player().inventory.currentItem;
            sendPacket(new C09PacketHeldItemChange(currentSlot % 8 + 1));
            sendPacket(new C09PacketHeldItemChange(currentSlot));
        }
    }

    private void updateMiauVerus(boolean pre) {
        if (miauSwordActive(MIAU_VERUS)) {
            if (pre) {
                sendPacket(new C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            } else {
                if (Mc.player().getHeldItem() != null && Mc.player().getHeldItem().getItem() instanceof ItemSword) {
                    sendPacket(new C08PacketPlayerBlockPlacement(Mc.player().getHeldItem()));
                }
            }
        }
    }

    private void updateMiauAAC() {
        if (miauSwordActive(MIAU_AAC)) {
            // PRE only.
            int currentSlot = Mc.player().inventory.currentItem;
            sendPacket(new C09PacketHeldItemChange(currentSlot % 8 + 1));
            sendPacket(new C09PacketHeldItemChange(currentSlot));
            if (Mc.player().getHeldItem() != null && Mc.player().getHeldItem().getItem() instanceof ItemSword) {
                sendPacket(new C08PacketPlayerBlockPlacement(Mc.player().getHeldItem()));
            }
        }
    }

    // ── OpalWatchdog ──────────────────────────────────────────────────────

    private void updateMiauOpalWatchdog() {
        EntityPlayerSP p = Mc.player();
        if (p == null || Mc.world() == null)
            return;
        if (Mc.mc().currentScreen != null) {
            opalResetCycle();
            opalRelease();
            return;
        }

        // stopUse: finish 1-tick item-use flicker.
        if (opalStopUse) {
            if (p.isUsingItem()) {
                opalBlock();
                p.stopUsingItem();
            }
            opalStopUse = false;
        } else if (!miauSwordActive(MIAU_OPAL_WATCHDOG)) {
            if (!p.isUsingItem()) {
                opalRelease();
            }
        }

        int age = p.ticksExisted;
        boolean rightPressed = Mc.settings().keyBindUseItem.isKeyDown();

        if (miauSwordActive(MIAU_OPAL_WATCHDOG)) {
            if (rightPressed) {
                if (opalNextCycleTick < 0) {
                    opalNextCycleTick = age;
                }
                if (age >= opalNextCycleTick) {
                    if (opalBlocking) {
                        opalRelease();
                    }
                    opalRunThisTick = true;
                    opalNextCycleTick = age + 2;
                } else if (!opalBlocking) {
                    opalBlock();
                }
            } else {
                opalResetCycle();
                if (!p.isUsingItem()) {
                    opalRelease();
                }
            }

            if (opalRunThisTick && miauSwordActive(MIAU_OPAL_WATCHDOG)) {
                if (rightPressed) {
                    if (!p.isUsingItem() || !opalBlocking) {
                        if (Mc.objectMouseOver() != null
                                && Mc.objectMouseOver().typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                                && Mc.objectMouseOver().getBlockPos() != null) {
                            Block block = Mc.world().getBlockState(Mc.objectMouseOver().getBlockPos()).getBlock();
                            PlayerControllerMP controller = Mc.controller();
                            IAccessorPlayerControllerMP accessor = controller instanceof IAccessorPlayerControllerMP
                                    ? (IAccessorPlayerControllerMP) controller : null;
                            if (isMiauInteractableBlock(block)
                                    || (accessor != null && accessor.getIsHittingBlock())) {
                                opalRunThisTick = false;
                                return;
                            }
                        }
                        opalStopUse = true;
                        ((IAccessorKeyBinding) Mc.settings().keyBindUseItem).setPressed(true);
                    } else {
                        ((IAccessorKeyBinding) Mc.settings().keyBindUseItem).setPressed(false);
                    }
                } else {
                    opalStopUse = false;
                }
                opalRunThisTick = false;
            }
        }
    }

    private void opalOnPacket(Object packet) {
        EntityPlayerSP p = Mc.player();
        if (p == null || Mc.world() == null)
            return;
        if (packet instanceof C09PacketHeldItemChange) {
            if (p.ticksExisted - opalSlotChangeTick != 1) {
                opalRelease();
                opalResetCycle();
            }
            opalSlotChangeTick = p.ticksExisted;
        }
        if (packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus statusPacket = (S19PacketEntityStatus) packet;
            if (statusPacket.getEntity(Mc.world()) == p && statusPacket.getOpCode() == 9) {
                opalRelease();
            }
        }
    }

    private void opalBlock() {
        EntityPlayerSP p = Mc.player();
        if (p != null && p.getHeldItem() != null && p.getHeldItem().getItem() instanceof ItemSword) {
            sendPacket(new C08PacketPlayerBlockPlacement(p.getHeldItem()));
            p.setItemInUse(p.getHeldItem(), p.getHeldItem().getMaxItemUseDuration());
            opalBlocking = true;
        }
    }

    private void opalRelease() {
        EntityPlayerSP p = Mc.player();
        if (p == null)
            return;
        sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        p.stopUsingItem();
        opalBlocking = false;
    }

    private void opalResetCycle() {
        opalStopUse = false;
        opalRunThisTick = false;
        opalNextCycleTick = -1;
    }

    private boolean isMiauInteractableBlock(Block block) {
        return block instanceof net.minecraft.block.BlockDoor
                || block instanceof net.minecraft.block.BlockChest
                || block instanceof net.minecraft.block.BlockFurnace
                || block instanceof net.minecraft.block.BlockWorkbench
                || block instanceof net.minecraft.block.BlockAnvil
                || block instanceof net.minecraft.block.BlockEnchantmentTable
                || block instanceof net.minecraft.block.BlockBrewingStand
                || block instanceof net.minecraft.block.BlockBeacon
                || block instanceof net.minecraft.block.BlockLever
                || block instanceof net.minecraft.block.BlockButtonWood
                || block instanceof net.minecraft.block.BlockButtonStone
                || block instanceof net.minecraft.block.BlockTrapDoor
                || block instanceof net.minecraft.block.BlockFenceGate
                || block instanceof net.minecraft.block.BlockRedstoneRepeater
                || block instanceof net.minecraft.block.BlockRedstoneComparator
                || block instanceof net.minecraft.block.BlockHopper
                || block instanceof net.minecraft.block.BlockDropper
                || block instanceof net.minecraft.block.BlockDispenser
                || block instanceof net.minecraft.block.BlockEnderChest
                || block == Blocks.anvil
                || block == Blocks.enchanting_table
                || block == Blocks.brewing_stand;
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /** OpenMyau {@code PacketUtil.sendPacket} — routes through the event bus (BadPackets tracking). */
    private void sendPacket(Packet<?> packet) {
        Mc.addToSendQueue(packet);
    }

    private void setSprinting(boolean sprinting) {
        EntityPlayerSP p = Mc.player();
        if (p != null)
            p.setSprinting(sprinting);
    }

    /** OpenMyau {@code isEatingStack}. */
    static boolean isEatingStack(ItemStack itemStack) {
        if (itemStack == null)
            return false;
        boolean splash = ItemPotion.isSplash(itemStack.getItem().getMetadata(itemStack));
        return matchesEatingUseAction(itemStack.getItemUseAction(), splash);
    }

    /** OpenMyau {@code matchesEatingUseAction}. */
    static boolean matchesEatingUseAction(EnumAction action, boolean splashPotion) {
        if (splashPotion)
            return false;
        return action == EnumAction.EAT || action == EnumAction.DRINK;
    }

    /**
     * OpenMyau {@code noAttack} suppression gate (isAnyActive / Hypixel PRE).
     * Reads KillAura blockTick / attackTick / autoBlock state.
     */
    private boolean noAttackSuppressed(KillAuraModule killAura) {
        if (killAura == null)
            return false;
        int ab = killAura.getAutoBlockMode();
        int blockTick = killAura.getBlockTick();
        int attackTick = killAura.getAttackTick();
        return (blockTick == 0 && ab == 2)
                || (ab == 6 && blockTick == attackTick)
                || (ab != 6 && ab != 2)
                || (ab == 5 && blockTick == 0)
                && killAura.isEnabled()
                && killAura.isPlayerBlocking();
    }
}