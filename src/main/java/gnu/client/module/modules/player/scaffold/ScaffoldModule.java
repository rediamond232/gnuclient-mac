package gnu.client.module.modules.player.scaffold;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.ModeSetting;
import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.RotationState;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;

import java.util.Arrays;

/**
 * Scaffold — places blocks beneath the player while bridging, with automatic telly
 * towering (Tower on + hold jump while standing still).
 *
 * <p>Silent rotation mirrors KillAura (see {@link ScaffoldRotation}): the camera stays
 * wherever the user points it while the sent C03 look aims at the bridge, with MoveFix
 * remapping WASD relative to the sent look. Target selection is raycast-driven (see
 * {@link ScaffoldTarget}) so the placed cell always trails the player — no expand, no
 * impossible placements. Slot handling is attack-safe: ItemSpoof (default on) only
 * switches the hotbar slot on the server (C09) while the visible hotbar keeps the user's
 * selection; with it off the hotbar switches normally. Placement goes through the
 * vanilla controller path ({@code PlayerControllerMP.onPlayerRightClick}), producing
 * the same C08 the client sends for a real click — never a bare use-item packet.
 */
public final class ScaffoldModule extends Module {

    private final ModeSetting aimMode = addSetting(new ModeSetting(
        "Aim", ScaffoldTarget.AIM_BACKWARDS, Arrays.asList("Backwards", "Godbridge")));
    private final BoolSetting tower = addSetting(new BoolSetting("Tower", true));
    private final BoolSetting itemSpoof = addSetting(new BoolSetting("ItemSpoof", true));
    private final BoolSetting moveFix = addSetting(new BoolSetting("MoveFix", true));

    /** Placement computed in onPreUpdate, executed in onBeforeWalkingPlayer. */
    private ScaffoldTarget pending;
    /** True while towering (Tower on + jump held + standing still). */
    private boolean towerActive;
    /** Bridge level while grounded (last seen) — used for jump-bridging while airborne. */
    private int bridgeLevel = -1;
    /** The hotbar slot the server currently believes we hold (spoof target). */
    private int lastServerSlot = -1;

    public ScaffoldModule() {
        super("Scaffold", "Places blocks beneath the player", Category.PLAYER);
    }

    public static ScaffoldModule instance() {
        Module module = ModuleManager.INSTANCE.getModule("Scaffold");
        return module instanceof ScaffoldModule ? (ScaffoldModule) module : null;
    }

    @Override
    public void onEnable() {
        pending = null;
        towerActive = false;
        bridgeLevel = -1;
        lastServerSlot = -1;
    }

    @Override
    public void onDisable() {
        pending = null;
        towerActive = false;
        bridgeLevel = -1;
        lastServerSlot = -1;
        ScaffoldRotation.disarmIfOwned();
    }

    @Override
    public String[] getSuffix() {
        return new String[] { aimMode.getCurrentMode() };
    }

    /**
     * OpenMyau fixStrafe remap — converts camera-relative WASD into input relative to the
     * sent scaffold look, so movement feels normal while the C03 look aims at the bridge.
     * Mirrors KillAura's patch; gated on the Scaffold MoveFix priority so only the module
     * that owns RotationState this tick remaps the input.
     */
    public static void patchMovementInput(Object movInput) {
        if (movInput == null)
            return;
        Module module = ModuleManager.instance().getModule("Scaffold");
        ScaffoldModule scaffold = module instanceof ScaffoldModule && module.isEnabled()
            ? (ScaffoldModule) module : null;
        if (scaffold == null)
            return;
        if (!scaffold.moveFix.getValue()
                || !MoveFixUtil.hasMoveFixPriority(MoveFixUtil.SCAFFOLD_MOVE_FIX_PRIORITY)
                || !MoveFixUtil.isForwardPressed())
            return;

        MovementInput input = (MovementInput) movInput;
        float[] fixed = MoveFixUtil.fixStrafe(
            Mc.getYaw(), RotationState.getSmoothedYaw(), input.sneak);
        input.moveForward = fixed[0];
        input.moveStrafe = fixed[1];
    }

    /** Hooked from {@code PlayerUpdateHook.onUpdateHead} (after KillAura). */
    public static void onPreUpdate(Object entity) {
        ScaffoldModule scaffold = instance();
        if (scaffold == null || !scaffold.isEnabled() || !(entity instanceof EntityPlayerSP))
            return;

        EntityPlayerSP player = (EntityPlayerSP) entity;
        if (Mc.currentScreen() != null || player.capabilities.isFlying
                || Mc.isUsingItem(player) || Mc.isBlocking(player)) {
            scaffold.pending = null;
            scaffold.towerActive = false;
            ScaffoldRotation.disarmIfOwned();
            return;
        }

        // Refuse to rotate while moving when MoveFix is off — WASD would fight the sent
        // look (same rule as KillAura).
        if (MoveFixUtil.isForwardPressed() && !scaffold.moveFix.getValue()) {
            scaffold.pending = null;
            scaffold.towerActive = false;
            ScaffoldRotation.disarmIfOwned();
            return;
        }

        int slot = scaffold.findBlockSlot(player);
        if (slot < 0) {
            scaffold.pending = null;
            scaffold.towerActive = false;
            ScaffoldRotation.disarmIfOwned();
            return;
        }

        double reach = Mc.controller().getBlockReachDistance();
        ItemStack stack = player.inventory.getStackInSlot(slot);
        ScaffoldTarget target = scaffold.computeTarget(player, reach, stack);
        scaffold.pending = target;
        if (target == null) {
            // Still aim at the bridge direction when nothing is placeable this tick
            // (e.g. raycast blocked) so the sent look and MoveFix stay consistent;
            // placement simply does not run.
            float[] fallback = scaffold.towerActive
                    ? new float[] { Mc.getYaw(), 90.0f }
                    : ScaffoldTarget.fallbackAim(player, scaffold.aimMode.getIndex());
            if (fallback != null) {
                ScaffoldRotation.arm(player, fallback[0], fallback[1], scaffold.moveFix.getValue());
            } else {
                ScaffoldRotation.disarmIfOwned();
            }
            return;
        }

        ScaffoldRotation.arm(player, target.yaw, target.pitch, scaffold.moveFix.getValue());
    }

    /** Placement target from the player's CURRENT position (tower / bridge / jump-bridge). */
    private ScaffoldTarget computeTarget(EntityPlayerSP player, double reach, ItemStack stack) {
        if (player.onGround)
            this.bridgeLevel = MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1;

        // Automatic tower: Tower on → tower whenever the player is airborne (jump held
        // or mid-jump), so it works while moving and with tapped jumps; on the ground
        // the aim presets bridge normally. Airborne otherwise fell through to the
        // bridge fallback look, which points forward (movement direction) instead of
        // down.
        boolean airborne = !player.onGround;
        boolean jumpTower = this.tower.getValue() && (Mc.isJumpKeyHeld() || airborne);
        this.towerActive = jumpTower;
        if (jumpTower) {
            ScaffoldTarget t = ScaffoldTarget.towerTarget(player, stack, reach);
            if (t != null)
                return t;
            // Jump-bridging: nothing to tower against (e.g. over the void ahead) —
            // extend the bridge at the last grounded level under the flight path.
            if (this.bridgeLevel >= 0)
                return ScaffoldTarget.findAtLevel(
                        player, this.aimMode.getIndex(), this.bridgeLevel, stack, reach);
            return null;
        }
        return ScaffoldTarget.find(player, this.aimMode.getIndex(), stack, reach);
    }

    /** Hooked from {@code PlayerUpdateHook.beforeWalkingPlayer} (after the rotation swap). */
    public static void onBeforeWalkingPlayer(Object entity) {
        ScaffoldModule scaffold = instance();
        if (scaffold == null || !scaffold.isEnabled() || !(entity instanceof EntityPlayerSP))
            return;

        EntityPlayerSP player = (EntityPlayerSP) entity;
        ScaffoldTarget target = scaffold.pending;
        if (target == null)
            return;

        int slot = scaffold.findBlockSlot(player);
        if (slot < 0)
            return;

        // Server-side slot sync — C09 only when the block slot actually changes.
        int userSlot = player.inventory.currentItem;
        if (scaffold.lastServerSlot != slot) {
            Mc.sendHeldItemChange(slot);
            scaffold.lastServerSlot = slot;
            // Separate the C09 slot change from the placement C08 with a movement
            // packet (Grim BadPacketsA flags a slot change + place in the same tick).
            return;
        }

        // Client-side: hold the block slot for the placement call, restore afterwards
        // when spoofing so the visible hotbar stays on the user's selection.
        if (userSlot != slot)
            Mc.setHotbarSlot(player, slot);

        boolean placed = scaffold.place(player, target);

        if (userSlot != slot && scaffold.itemSpoof.getValue())
            Mc.setHotbarSlot(player, userSlot);

        if (placed)
            player.swingItem();
    }

    /** Vanilla placement path — same C08 a real right-click produces. */
    private boolean place(EntityPlayerSP player, ScaffoldTarget target) {
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemBlock))
            return false;
        WorldClient world = Mc.world();
        if (world == null)
            return false;
        return Mc.controller().onPlayerRightClick(
                player, world, stack, target.clickPos, target.face, target.hitVec);
    }

    private int findBlockSlot(EntityPlayerSP player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock)
                return i;
        }
        return -1;
    }
}
