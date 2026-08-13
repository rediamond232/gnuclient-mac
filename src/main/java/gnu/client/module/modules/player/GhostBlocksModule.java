package gnu.client.module.modules.player;

import gnu.client.event.RightClickMouseEvent;
import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.setting.BoolSetting;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import gnu.client.util.EspDraw;
import gnu.client.util.RenderHelper;
import gnu.client.utility.BlockUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ghost Blocks — place walkable phantom blocks that only exist on the client.
 * Right-click places a ghost block at the targeted position (even mid-air),
 * right-click a placed ghost to remove it, sneak + right-click clears all.
 * Nothing is sent to the server; the player can stand on ghosts via
 * {@code MixinWorld} collision injection.
 */
public final class GhostBlocksModule extends Module {

    private static final double GHOST_RAY_STEP = 0.1;

    private final SliderSetting red = addSetting(new SliderSetting("Red", 90.0f, 0.0f, 255.0f, 1.0f));
    private final SliderSetting green = addSetting(new SliderSetting("Green", 220.0f, 0.0f, 255.0f, 1.0f));
    private final SliderSetting blue = addSetting(new SliderSetting("Blue", 255.0f, 0.0f, 255.0f, 1.0f));
    private final SliderSetting alpha = addSetting(new SliderSetting("Opacity", 90.0f, 10.0f, 200.0f, 5.0f));
    private final BoolSetting rightClickRemove = addSetting(new BoolSetting("Right click remove", true));
    private final BoolSetting requireBlock = addSetting(new BoolSetting("Require block", true));
    private final SliderSetting maxBlocks = addSetting(new SliderSetting("Max blocks", 64.0f, 1.0f, 256.0f, 1.0f));

    private static final Set<BlockPos> GHOSTS = ConcurrentHashMap.newKeySet();
    private static volatile boolean active;

    public GhostBlocksModule() {
        super("Ghost Blocks", "Place walkable phantom blocks only you can see (client-side)",
                Category.PLAYER);
    }

    @Override
    public void onEnable() {
        GHOSTS.clear();
        active = true;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisable() {
        MinecraftForge.EVENT_BUS.unregister(this);
        active = false;
        GHOSTS.clear();
    }

    @Override
    public String[] getSuffix() {
        return GHOSTS.isEmpty() ? new String[0] : new String[] { String.valueOf(GHOSTS.size()) };
    }

    @SubscribeEvent
    public void onRightClickMouse(RightClickMouseEvent event) {
        EntityPlayerSP player = Mc.player();
        WorldClient world = Mc.world();
        if (player == null || world == null || !Mc.isInGame())
            return;

        if (Mc.isSneakKeyHeld()) {
            if (GHOSTS.isEmpty())
                return;
            GHOSTS.clear();
            event.setCanceled(true);
            player.swingItem();
            return;
        }

        if (rightClickRemove.getValue()) {
            BlockPos hitGhost = raycastGhost(player);
            if (hitGhost != null && GHOSTS.remove(hitGhost)) {
                event.setCanceled(true);
                player.swingItem();
                return;
            }
        }

        if (requireBlock.getValue()) {
            ItemStack held = player.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemBlock))
                return;
        }

        BlockPos target = placementTarget(player);
        if (target == null || !isValidTarget(player, world, target))
            return;

        if (!GHOSTS.add(target)) {
            GHOSTS.remove(target);
        } else if (GHOSTS.size() > maxBlocks.getValue().intValue()) {
            GHOSTS.remove(target);
            return;
        }

        event.setCanceled(true);
        player.swingItem();
    }

    private BlockPos placementTarget(EntityPlayerSP player) {
        MovingObjectPosition hit = Mc.objectMouseOver();
        if (hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            if (BlockUtils.isInteractable(hit))
                return null;
            return hit.getBlockPos().offset(hit.sideHit);
        }

        Vec3 eye = player.getPositionEyes(1.0f);
        Vec3 look = player.getLook(1.0f);
        double reach = Mc.controller().getBlockReachDistance();
        return new BlockPos(
                MathHelper.floor_double(eye.xCoord + look.xCoord * reach),
                MathHelper.floor_double(eye.yCoord + look.yCoord * reach),
                MathHelper.floor_double(eye.zCoord + look.zCoord * reach));
    }

    private boolean isValidTarget(EntityPlayerSP player, WorldClient world, BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() > 255)
            return false;
        if (!BlockUtils.replaceable(pos))
            return false;
        AxisAlignedBB box = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        if (player.getEntityBoundingBox().intersectsWith(box))
            return false;
        return true;
    }

    /** First ghost block the look ray hits before the vanilla target block, or null. */
    private BlockPos raycastGhost(EntityPlayerSP player) {
        Vec3 eye = player.getPositionEyes(1.0f);
        Vec3 look = player.getLook(1.0f);
        double len = Math.sqrt(look.xCoord * look.xCoord + look.yCoord * look.yCoord + look.zCoord * look.zCoord);
        if (len < 1.0E-6)
            return null;
        double reach = Mc.controller().getBlockReachDistance();
        double dx = look.xCoord / len * GHOST_RAY_STEP;
        double dy = look.yCoord / len * GHOST_RAY_STEP;
        double dz = look.zCoord / len * GHOST_RAY_STEP;
        int steps = (int) Math.ceil(reach / GHOST_RAY_STEP);
        double px = eye.xCoord;
        double py = eye.yCoord;
        double pz = eye.zCoord;

        double realHitDist = Double.MAX_VALUE;
        MovingObjectPosition hit = Mc.objectMouseOver();
        if (hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            Vec3 hitVec = hit.hitVec;
            if (hitVec != null)
                realHitDist = eye.distanceTo(hitVec);
        }

        for (int i = 1; i <= steps; i++) {
            px += dx;
            py += dy;
            pz += dz;
            double dist = i * GHOST_RAY_STEP;
            if (dist > realHitDist)
                return null;
            BlockPos bp = new BlockPos(
                    MathHelper.floor_double(px),
                    MathHelper.floor_double(py),
                    MathHelper.floor_double(pz));
            if (GHOSTS.contains(bp))
                return bp;
        }
        return null;
    }

    @Override
    public void onRender(float partialTicks) {
        if (GHOSTS.isEmpty() || !Mc.isInGame())
            return;

        double[] vp = Mc.getViewerPos(partialTicks);
        float fr = red.getValue() / 255.0f;
        float fg = green.getValue() / 255.0f;
        float fb = blue.getValue() / 255.0f;
        float fa = alpha.getValue() / 255.0f;

        RenderHelper.begin();
        for (BlockPos pos : GHOSTS) {
            double minX = pos.getX() - vp[0];
            double minY = pos.getY() - vp[1];
            double minZ = pos.getZ() - vp[2];
            double maxX = minX + 1.0;
            double maxY = minY + 1.0;
            double maxZ = minZ + 1.0;
            EspDraw.fill(minX, minY, minZ, maxX, maxY, maxZ, fr, fg, fb, fa);
            RenderHelper.drawBoundingBox(minX, minY, minZ, maxX, maxY, maxZ, fr, fg, fb, 0.8f, 2.0f);
        }
        RenderHelper.end();
    }

    // ---- shared with MixinWorld ----

    public static boolean isActive() {
        return active;
    }

    /**
     * Adds full-cube collision boxes for ghosts intersecting {@code query}.
     * Called from {@code MixinWorld} so the local player can stand on ghost blocks.
     */
    public static void addCollisionBoxes(List<AxisAlignedBB> out, AxisAlignedBB query) {
        if (GHOSTS.isEmpty())
            return;
        for (BlockPos pos : GHOSTS) {
            AxisAlignedBB box = new AxisAlignedBB(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
            if (box.intersectsWith(query))
                out.add(box);
        }
    }
}
