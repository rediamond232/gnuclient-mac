package gnu.client.module.modules.player.scaffold;

import gnu.client.runtime.MoveFixUtil;
import gnu.client.runtime.mc.Mc;
import gnu.client.utility.BlockUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * Placement target selection for Scaffold — computed directly from geometry, mirroring
 * the original gnuclient (Augustus NewScaffold "Basic"): the support block and the
 * clicked face are derived from the player's position and the look direction, and the
 * hit vector is crafted on the clicked face. No raycast is involved — a ray from the
 * player's eye can never hit a bridge block's side face (the top face always occludes
 * it from eye height), which is why raycast-gated placement could never place anything.
 * The placed cell is always the cell under the player's feet (or the next cell in the
 * look direction), so it can never be beyond reach or around a corner.
 */
final class ScaffoldTarget {

    static final int AIM_BACKWARDS = 0;
    static final int AIM_GODBRIDGE = 1;

    /** The block whose face we click (C08 blockPos). */
    final BlockPos clickPos;
    /** The clicked face of {@link #clickPos}; the new block lands at clickPos.offset(face). */
    final EnumFacing face;
    /** World position the new block lands on (clickPos.offset(face)). */
    final BlockPos placePos;
    /** Hit point on the clicked face (passed to the vanilla placement call). */
    final Vec3 hitVec;
    /** Exact look (yaw/pitch) to that face — this is the rotation that gets sent. */
    final float yaw;
    final float pitch;

    private ScaffoldTarget(BlockPos clickPos, EnumFacing face, Vec3 hitVec, float yaw, float pitch) {
        this.clickPos = clickPos;
        this.face = face;
        this.placePos = clickPos.offset(face);
        this.hitVec = hitVec;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * @return the placement for the aim mode, or {@code null} if nothing is placeable
     *         this tick. The look yaw must equal the movement direction for the keys
     *         being pressed (Backwards: S/W → straight behind; Godbridge: A+S / D+S →
     *         the diagonal) so the placement lands under the player's next step and
     *         fixStrafe reproduces the same movement.
     */
    static ScaffoldTarget find(EntityPlayerSP player, int aimMode, ItemStack stack, double reach) {
        if (player == null || stack == null || reach <= 0.0)
            return null;
        int levelY = MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1;
        return findAtLevel(player, aimMode, levelY, stack, reach);
    }

    /**
     * Bridge placement at a specific level — used for jump-bridging while airborne
     * (level = the last grounded bridge level, tracked by the module).
     */
    static ScaffoldTarget findAtLevel(EntityPlayerSP player, int aimMode, int levelY,
                                      ItemStack stack, double reach) {
        if (player == null || stack == null || reach <= 0.0)
            return null;
        float yaw = aimMode == AIM_GODBRIDGE
                ? wrapAngle(MoveFixUtil.movementFacingYaw())
                : wrapAngle(Mc.getYaw() + 180.0f);
        return bridgeTarget(player, yaw, levelY, stack, reach);
    }

    /**
     * Tower (telly) target — places the cell below the player's feet (the cell they
     * vacated by jumping), clicking the top face of the block below it. That cell is
     * below the player's bounding box, so the client's entity-collision check inside
     * {@code World.canBlockBePlaced} passes (a body-cell placement would be rejected).
     * Because the placement tracks the player's horizontal position, towering works
     * while moving — over flat ground and over the scaffold's own bridge column. Over
     * an unsupported void there is nothing to click, so no placement (you fall — jump
     * bridging is a separate mechanic). Look is straight down.
     */
    static ScaffoldTarget towerTarget(EntityPlayerSP player, ItemStack stack, double reach) {
        int px = MathHelper.floor_double(player.posX);
        int pz = MathHelper.floor_double(player.posZ);
        int y = MathHelper.floor_double(player.posY) - 1;
        BlockPos placePos = new BlockPos(px, y, pz);
        if (!BlockUtils.replaceable(placePos))
            return null;

        BlockPos support = placePos.down();
        if (BlockUtils.replaceable(support))
            return null;

        EnumFacing face = EnumFacing.UP;
        Vec3 hit = BlockUtils.getFaceCenter(support, face);
        return validTarget(player, support, face, placePos, hit, Mc.getYaw(), 90.0f, stack, reach);
    }

    private static ScaffoldTarget bridgeTarget(EntityPlayerSP player, float yaw, int levelY,
                                               ItemStack stack, double reach) {
        EnumFacing step = cardinalStep(yaw);

        // Only place cells under the player's bounding box footprint — never ahead or
        // beside (anti-cheats flag "Expand" for blocks not under the player). Using the
        // box instead of the center cell fixes the diagonal fall: cutting a cell corner
        // puts the center in a diagonal cell whose neighbours are all still air, but the
        // box already overlaps the neighbouring path cells, so they get placed (and the
        // box overlapping a cell one tick before the center crosses removes the
        // placement lag).
        AxisAlignedBB box = player.getEntityBoundingBox();
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ);

        // Grim PositionPlace expects placements in the player's movement direction
        // (vanilla bridging places blocks ahead of motion). Filter the box cells to the
        // forward half-plane of the look yaw so corner-widening cells beside the path
        // are never placed; the center cell (under the player) always qualifies.
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);

        ScaffoldTarget best = null;
        float bestScore = Float.MAX_VALUE;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos feet = new BlockPos(x, levelY, z);
                if (!BlockUtils.replaceable(feet))
                    continue;

                double dcx = x + 0.5 - player.posX;
                double dcz = z + 0.5 - player.posZ;
                double len = Math.sqrt(dcx * dcx + dcz * dcz);
                if (len > 1.0E-4 && (dcx * lookX + dcz * lookZ) / len < 0.0)
                    continue;

                EnumFacing[] order = { step.getOpposite(), step.rotateY(), step.rotateYCCW(), step };
                for (EnumFacing dir : order) {
                    BlockPos support = feet.offset(dir);
                    if (BlockUtils.replaceable(support))
                        continue;

                    ScaffoldTarget t = validTarget(player, support, dir.getOpposite(), feet,
                            BlockUtils.getFaceCenter(support, dir.getOpposite()),
                            yaw, 0.0f, stack, reach);
                    if (t == null)
                        continue;

                    // Prefer the cell closest to the player's center (the one they are
                    // stepping onto); when that one has no support yet, the next box
                    // cell fills the staircase.
                    float score = (float) (dcx * dcx + dcz * dcz);
                    if (score < bestScore) {
                        bestScore = score;
                        best = t;
                    }
                }
            }
        }
        return best;
    }

    /** Shared validity + look computation for a placement (support, face, placePos). */
    private static ScaffoldTarget validTarget(EntityPlayerSP player, BlockPos support,
                                              EnumFacing face, BlockPos placePos, Vec3 hit,
                                              float yaw, float pitch, ItemStack stack, double reach) {
        if (BlockUtils.replaceable(support))
            return null;
        if (!BlockUtils.replaceable(placePos))
            return null;
        if (!BlockUtils.canPlaceBlockOnSide(stack, placePos, face))
            return null;

        // The placement cell itself must be within reach — no far/expand blocks.
        double dx = placePos.getX() + 0.5 - player.posX;
        double dy = placePos.getY() + 0.5 - (player.posY + player.getEyeHeight());
        double dz = placePos.getZ() + 0.5 - player.posZ;
        if (dx * dx + dy * dy + dz * dz > reach * reach)
            return null;

        if (pitch == 0.0f) {
            // Aim from the predicted C03 eye (current position + velocity): the C03 is
            // sent one tick after the aim is computed, and a look computed from the
            // current eye would pass over the placement face from the moved eye (Grim
            // PositionPlace). The yaw/pitch must hit the clicked face from where the
            // C03 will actually be sent.
            Vec3 eye = player.getPositionEyes(1.0f)
                    .addVector(player.motionX, player.motionY, player.motionZ);
            float[] rot = rotationsToPoint(eye, hit);
            if (rot == null)
                return null;
            yaw = rot[0];
            pitch = rot[1];
        }
        return new ScaffoldTarget(support, face, hit, yaw, pitch);
    }

    /** Fallback look for when no placement is possible this tick — still aims so the
     *  sent C03 look and MoveFix stay consistent; placement simply does not run. */
    static float[] fallbackAim(EntityPlayerSP player, int aimMode) {
        if (player == null)
            return null;
        float yaw = aimMode == AIM_GODBRIDGE
                ? wrapAngle(MoveFixUtil.movementFacingYaw())
                : wrapAngle(Mc.getYaw() + 180.0f);
        return new float[] { yaw, 80.0f };
    }

    /** The horizontal cardinal direction nearest the given yaw (MC convention: 0=south). */
    private static EnumFacing cardinalStep(float yaw) {
        float y = MathHelper.wrapAngleTo180_float(yaw);
        if (y >= -45.0f && y < 45.0f)
            return EnumFacing.SOUTH;
        if (y >= 45.0f && y < 135.0f)
            return EnumFacing.WEST;
        if (y >= 135.0f || y < -135.0f)
            return EnumFacing.NORTH;
        return EnumFacing.EAST;
    }

    /** Exact yaw/pitch from an eye position to a point (mirrors Mc.raycastBlocks). */
    private static float[] rotationsToPoint(Vec3 eye, Vec3 point) {
        double dx = point.xCoord - eye.xCoord;
        double dy = point.yCoord - eye.yCoord;
        double dz = point.zCoord - eye.zCoord;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0E-6)
            return null;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(dy, horiz) * 180.0 / Math.PI));
        return new float[] { wrapAngle(yaw), pitch };
    }

    private static float wrapAngle(float angle) {
        angle %= 360.0f;
        if (angle >= 180.0f)
            angle -= 360.0f;
        if (angle < -180.0f)
            angle += 360.0f;
        return angle;
    }
}
