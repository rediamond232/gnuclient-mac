package gnu.client.render;

import gnu.client.module.modules.settings.PerformanceModule;
import gnu.client.runtime.FreeLookHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.MathHelper;

/**
 * Render-only entity culling. Skips drawing work; never touches entity ticks, collision,
 * packets, or hit detection — so combat / physics stay vanilla.
 *
 * <p>Strategy (cheap → skip expensive model work):
 * <ol>
 *   <li>Type-scaled max distance (items/XP/armor stands drop out early on busy servers)</li>
 *   <li>Behind-camera reject with a soft margin so edge-of-screen entities do not pop</li>
 * </ol>
 *
 * <p>Vanilla frustum culling still runs afterward for anything we keep.
 */
public final class EntityCulling {

    /** Soft half-angle padding past FOV before behind-camera cull kicks in (degrees). */
    private static final float FOV_PADDING_DEG = 35.0f;

    /** Never behind-cull inside this radius — F5 / close entities stay visible. */
    private static final double CLOSE_RANGE_SQ = 6.0 * 6.0;

    /**
     * Vanilla {@code Entity.isInRangeToRenderDist} multiplies bounding-box size by this
     * (blocks). Reduced Entity Distance scales this, not chunk RD — vanilla never reads
     * {@code renderDistanceChunks} when deciding whether to draw an entity.
     */
    public static final double VANILLA_RANGE_BLOCKS = 64.0;

    private EntityCulling() {}

    /**
     * @return {@code true} if the entity should not be drawn this frame
     */
    public static boolean shouldCull(Entity entity, double camX, double camY, double camZ) {
        if (entity == null) {
            return true;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return false;
        }
        Entity viewer = mc.getRenderViewEntity();
        if (entity == viewer) {
            return false;
        }
        if (viewer != null && (entity.riddenByEntity == viewer || viewer.riddenByEntity == entity)) {
            return false;
        }
        if (entity.ignoreFrustumCheck) {
            return false;
        }

        double dx = entity.posX - camX;
        double dy = entity.posY + (double) (entity.height * 0.5f) - camY;
        double dz = entity.posZ - camZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        double maxSq = maxDistanceSq(entity, mc);
        if (distSq > maxSq) {
            return true;
        }

        if (!PerformanceModule.entityCulling()) {
            return false;
        }
        return isClearlyBehindCamera(mc, dx, dy, dz, distSq);
    }

    /**
     * Vanilla entity range (64) scaled by Reduced Entity Distance. 1.0 = vanilla;
     * 0.75 = 48 blocks. Chunk render distance is not used — 1.8.9 never consults it here.
     */
    public static double reducedCapBlocks(boolean reduced, float fraction) {
        if (!reduced) {
            return VANILLA_RANGE_BLOCKS;
        }
        if (fraction < 0.1f) {
            fraction = 0.1f;
        }
        if (fraction > 1.0f) {
            fraction = 1.0f;
        }
        return VANILLA_RANGE_BLOCKS * (double) fraction;
    }

    private static double maxDistanceSq(Entity entity, Minecraft mc) {
        double cap = reducedCapBlocks(PerformanceModule.reducedEntityDistance(),
                PerformanceModule.entityDistanceFraction());

        if (!PerformanceModule.entityCulling()) {
            return sq(cap);
        }

        int rd = 8;
        if (mc.gameSettings != null) {
            rd = Math.max(2, mc.gameSettings.renderDistanceChunks);
        }
        double fullBlocks = rd * 16.0;

        // Players: vanilla 64, or the reduced cap — PvP visibility, not chunk RD.
        if (entity instanceof EntityPlayer) {
            return sq(cap);
        }

        // Drop clutter fast: lobbies / skywars floors are dominated by these.
        if (entity instanceof EntityItem || entity instanceof EntityXPOrb) {
            return sq(Math.min(40.0, cap));
        }
        if (entity instanceof EntityArmorStand) {
            return sq(Math.min(48.0, cap));
        }
        if (entity instanceof EntityItemFrame) {
            return sq(Math.min(48.0, cap));
        }
        if (entity instanceof EntityArrow || entity instanceof EntityThrowable
                || entity instanceof EntityFireball) {
            return sq(Math.min(64.0, cap));
        }
        if (entity instanceof EntityLivingBase) {
            return sq(Math.min(fullBlocks * 0.85, cap));
        }
        return sq(Math.min(fullBlocks * 0.75, cap));
    }

    /**
     * Cull only when the entity is well outside the camera FOV cone (behind / far off-angle).
     * Uses a padded FOV so entities near the screen edge are not dropped.
     */
    private static boolean isClearlyBehindCamera(Minecraft mc, double dx, double dy, double dz,
            double distSq) {
        if (distSq <= CLOSE_RANGE_SQ) {
            return false;
        }
        RenderManager rm = mc.getRenderManager();
        if (rm == null) {
            return false;
        }

        float fov = 70.0f;
        if (mc.gameSettings != null) {
            fov = mc.gameSettings.fovSetting;
        }
        // Half-FOV + padding; clamp so we never require entities to be almost centered.
        float halfCone = Math.min(120.0f, fov * 0.5f + FOV_PADDING_DEG);
        double minDot = MathHelper.cos(halfCone * (float) Math.PI / 180.0f);

        float yaw = rm.playerViewY;
        float pitch = rm.playerViewX;
        // FreeLook updates RenderManager views in its render hook, but shouldRender can run
        // before that — prefer the active freelook camera when present.
        if (FreeLookHook.isActive()) {
            Entity view = mc.getRenderViewEntity();
            if (view != null) {
                yaw = FreeLookHook.redirectYaw(view);
                pitch = FreeLookHook.redirectPitch(view);
            }
        }
        // Same basis as Entity.getVectorForRotation — yaw 0 faces +Z (south).
        float yawRad = -yaw * 0.017453292F - (float) Math.PI;
        float pitchRad = -pitch * 0.017453292F;
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = -MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        double lookX = sinYaw * cosPitch;
        double lookY = sinPitch;
        double lookZ = cosYaw * cosPitch;

        double invLen = 1.0 / Math.sqrt(distSq);
        double dot = (dx * lookX + dy * lookY + dz * lookZ) * invLen;
        return dot < minDot;
    }

    private static double sq(double v) {
        return v * v;
    }
}
