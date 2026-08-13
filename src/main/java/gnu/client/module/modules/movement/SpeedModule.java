package gnu.client.module.modules.movement;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.modules.combat.KeepSprintModule;
import gnu.client.module.modules.combat.WTapModule;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.List;

/**
 * Speed — vanilla auto-bhop with a Grim "entity push" boost.
 *
 * <p><b>Base (always safe):</b> auto-sprint (keybind, so the vanilla client
 * issues ENTITY_ACTION START_SPRINTING itself) + auto-jump while moving on
 * the ground. This is exactly what a player holding sprint + space does, so
 * Grim's simulation predicts every move with offset 0.00.</p>
 *
 * <p><b>Boost (GrimAC 1.9+ client):</b> Grim's {@code handleEntityCollisions}
 * counts pushable entities near the player and grants a horizontal prediction
 * envelope of {@code 0.08 * count} per axis
 * ({@code handleStartingVelocityUncertainty}). While we overlap a living
 * entity we push the client 0.08 * count blocks/tick forward (yaw direction).
 * The extra displacement lands inside the envelope Grim itself grants for the
 * collision, so the Simulation check stays at 0 offset. Requires the client
 * to present 1.9+ (e.g. ViaForge) and an actual entity nearby; without one
 * the module degrades to plain vanilla bhop.
 */
public final class SpeedModule extends Module {

    private final SliderSetting boost =
            addSetting(new SliderSetting("Boost", 0.08f, 0.01f, 0.08f, 0.01f));
    private final SliderSetting shrinkBox =
            addSetting(new SliderSetting("Shrink box", 0.25f, 0.05f, 2.0f, 0.05f));
    private final BoolSetting autoSprint =
            addSetting(new BoolSetting("Auto sprint", true));
    private final BoolSetting autoJump =
            addSetting(new BoolSetting("Auto jump", true));

    public SpeedModule() {
        super("Speed", "Vanilla bhop + entity push boost (Grim)",
                Category.PLAYER);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {
        restoreKeys();
    }

    /** Early client tick — runs before the player update consumes motion. */
    @Override
    public void onTickStart() {
        if (!isEnabled() || !Mc.isInGame())
            return;
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return;

        applyAutoSprint(player);
        applyAutoJump(player);
        applyEntityPush(player);
    }

    private void applyAutoSprint(EntityPlayerSP player) {
        if (!autoSprint.getValue())
            return;
        if (WTapModule.shouldSuppressSprintKey() || KeepSprintModule.shouldSuppressSprintKey()) {
            Mc.setSprintKeyState(false);
            return;
        }
        // Vanilla client refuses to sprint in water / without food, so key
        // holding alone cannot trigger SprintA/G.
        Mc.setSprintKeyState(true);
    }

    private void applyAutoJump(EntityPlayerSP player) {
        if (!autoJump.getValue())
            return;
        if (player.onGround && isMoving(player) && !player.isInWater() && !player.isInLava())
            Mc.setJumpInput(player, true);
    }

    /**
     * GrimCollide-style push: +boost per overlapping living entity each tick,
     * in the yaw (look) direction. Only active while moving, exactly like the
     * LiquidBounce {@code SpeedGrimCollide} mode this is modeled on.
     */
    private void applyEntityPush(EntityPlayerSP player) {
        if (!isMoving(player))
            return;
        if (player.isRiding() || player.isInWater() || player.isInLava())
            return;
        if (player.capabilities.isFlying)
            return;

        float boxSize = shrinkBox.getValue();
        // Match Grim's server-side counting box (0.2 + 0.03 movement threshold
        // expand, MovementTicker.handleEntityCollisions) so we only push while
        // the server grants the collidingEntities envelope. A bigger box pushes
        // before the server counts the entity -> offset -> Simulation flag.
        AxisAlignedBB box = player.getEntityBoundingBox().expand(
                boxSize, boxSize, boxSize);

        int collisions = countCollidingEntities(player, box);
        if (collisions <= 0)
            return;

        double[] dir = pushDir(player.rotationYaw, boost.getValue() * collisions);
        player.addVelocity(dir[0], 0.0, dir[1]);
    }

    private int countCollidingEntities(EntityPlayerSP player, AxisAlignedBB box) {
        World world = Mc.world();
        if (world == null)
            return 0;
        int count = 0;
        for (Entity entity : Mc.getWorldEntities(world)) {
            if (entity == null || entity == player || entity.isDead)
                continue;
            if (!(entity instanceof EntityLivingBase))
                continue;
            if (entity instanceof EntityArmorStand)
                continue;
            if (box.intersectsWith(entity.getEntityBoundingBox()))
                count++;
        }
        return count;
    }

    private static boolean isMoving(EntityPlayerSP player) {
        if (player == null || player.movementInput == null)
            return false;
        return player.movementInput.moveForward != 0.0f
                || player.movementInput.moveStrafe != 0.0f;
    }

    /**
     * Horizontal push components in world space from the player's yaw,
     * {@code (-sin(yaw) * boost, cos(yaw) * boost)}.
     */
    static double[] pushDir(float yaw, double boost) {
        double rad = Math.toRadians(yaw);
        return new double[] { -Math.sin(rad) * boost, Math.cos(rad) * boost };
    }

    private void restoreKeys() {
        Mc.setSprintKeyState(false);
        EntityPlayerSP player = Mc.player();
        if (player != null)
            Mc.setJumpInput(player, false);
    }

    @Override
    public String[] getSuffix() {
        return new String[] { String.format("%.2f", boost.getValue()) };
    }
}
