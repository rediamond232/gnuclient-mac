package gnu.client.module.modules.combat;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.module.ModuleManager;
import gnu.client.module.setting.SliderSetting;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Extends entity targeting range (raven-bS raytrace + RainClient chance gate).
 *
 * Applied on tick-start (before click processing), render-world-last (after vanilla
 * raytrace), and Forge {@code MouseEvent} (manual / LWJGL clicks).
 */
public final class ReachModule extends Module {

    private final SliderSetting distance = addSetting(new SliderSetting("Distance", 4.5f, 3.0f, 6.0f));
    private final SliderSetting chance = addSetting(new SliderSetting("Chance", 100.0f, 0.0f, 100.0f));

    private final Random random = new Random();

    public ReachModule() {
        super("Reach", "Extends entity targeting range", Category.COMBAT);
    }

    public static void applyIfEnabled() {
        Module m = ModuleManager.INSTANCE.getModule("Reach");
        if (m instanceof ReachModule) {
            ReachModule reach = (ReachModule) m;
            if (reach.isEnabled())
                reach.applyReach();
        }
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public void onTickStart() {
        applyReach();
    }

    @Override
    public void onRender(float partialTicks) {
        applyReach();
    }

    private void applyReach() {
        if (random.nextDouble() * 100.0 > chance.getValue())
            return;

        Entity view = Mc.renderViewEntity();
        WorldClient world = Mc.world();
        if (view == null || world == null)
            return;

        if (Mc.pointedEntity() != null)
            return;

        Object[] hit = findEntityHit(view, world, distance.getValue(), 1.0f);
        if (hit == null)
            return;

        Entity entity = (Entity) hit[0];
        Vec3 hitVec = (Vec3) hit[1];
        MovingObjectPosition mop = createEntityMouseOver(entity, hitVec);
        if (mop == null)
            return;

        Mc.setObjectMouseOver(mop);
        Mc.setPointedEntity(entity);
    }

    private static Object[] findEntityHit(Entity view, WorldClient world, double reach, float partialTicks) {
        Vec3 eyes = view.getPositionEyes(partialTicks);
        Vec3 look = view.getLook(partialTicks);
        if (eyes == null || look == null)
            return null;

        double lx = look.xCoord;
        double ly = look.yCoord;
        double lz = look.zCoord;

        Vec3 end = eyes.addVector(lx * reach, ly * reach, lz * reach);

        AxisAlignedBB viewBox = view.getEntityBoundingBox();
        if (viewBox == null)
            return null;

        AxisAlignedBB searchBox = viewBox.addCoord(lx * reach, ly * reach, lz * reach)
                .expand(1.0, 1.0, 1.0);

        List<Entity> entities = world.getEntitiesWithinAABBExcludingEntity(view, searchBox);

        Entity bestEntity = null;
        Vec3 bestHitVec = null;
        double bestDist = reach;

        for (Entity entity : entities) {
            if (entity == null || !(entity instanceof EntityPlayer))
                continue;

            if (!entity.canBeCollidedWith())
                continue;

            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox();
            if (box == null)
                continue;
            box = box.expand(border, border, border);

            boolean inside = box.isVecInside(eyes);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, end);

            if (inside) {
                if (bestDist >= 0.0) {
                    bestEntity = entity;
                    bestHitVec = hitVecFromIntercept(intercept, eyes);
                    bestDist = 0.0;
                }
            } else if (intercept != null) {
                Vec3 hitVec = hitVecFromIntercept(intercept, eyes);
                double dist = eyes.distanceTo(hitVec);
                if (dist < bestDist || bestDist == 0.0) {
                    bestEntity = entity;
                    bestHitVec = hitVec;
                    bestDist = dist;
                }
            }
        }

        if (bestEntity == null || bestHitVec == null)
            return null;
        return new Object[] { bestEntity, bestHitVec };
    }

    private static Vec3 hitVecFromIntercept(MovingObjectPosition intercept, Vec3 fallback) {
        if (intercept != null && intercept.hitVec != null)
            return intercept.hitVec;
        return fallback;
    }

    private static MovingObjectPosition createEntityMouseOver(Entity entity, Vec3 hitVec) {
        MovingObjectPosition mop = new MovingObjectPosition(entity, hitVec);
        mop.typeOfHit = MovingObjectPosition.MovingObjectType.ENTITY;
        mop.entityHit = entity;
        mop.hitVec = hitVec;
        return mop;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { String.format("%.2f", distance.getValue()) };
    }
}
