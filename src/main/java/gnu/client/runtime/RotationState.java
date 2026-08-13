package gnu.client.runtime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;

/**
 * OpenMyau {@code RotationState} — silent rotation activation, movefix yaw, and
 * F5/FreeLook body/head render angles.
 *
 * <p>{@link #getSmoothedYaw()} is the <b>move yaw</b> ({@code pervYaw}) for
 * {@code fixStrafe}/{@code moveFlying}. Packet yaw/pitch drive C03 and the
 * rendered head via {@link #getRotationYawHead()} / {@link #getRotationPitch()}.
 */
public final class RotationState {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static int state = -1;
    private static float smoothYaw;
    private static int priority = -1;

    private static float prevRenderYawOffset;
    private static float renderYawOffset;
    private static float prevRotationYawHead;
    private static float rotationYawHead;
    private static float prevRotationPitch;
    private static float rotationPitch;

    private RotationState() {}

    /**
     * @param active whether silent rotation is active this tick
     * @param yaw packet / look yaw (C03 + rendered head)
     * @param pitch packet pitch (C03 + rendered head)
     * @param pervYaw <b>move yaw</b> for fixStrafe / moveFlying
     * @param rotPriority KillAura=1, Displace=2 when MoveFix armed; use {@code -1}
     *                    for render-only silent rotations (no moveFlying swap)
     */
    public static void applyState(boolean active, float yaw, float pitch, float pervYaw, int rotPriority) {
        state = active ? 0 : state + 1;
        EntityPlayerSP player = mc.thePlayer;
        if (player != null) {
            prevRenderYawOffset = renderYawOffset;
            renderYawOffset = active
                ? calculateRenderYawOffset(yaw, renderYawOffset)
                : player.renderYawOffset;
            prevRotationYawHead = rotationYawHead;
            rotationYawHead = active ? yaw : player.rotationYawHead;
            prevRotationPitch = rotationPitch;
            rotationPitch = active ? pitch : player.rotationPitch;
        } else if (active) {
            prevRenderYawOffset = renderYawOffset;
            renderYawOffset = calculateRenderYawOffset(yaw, renderYawOffset);
            prevRotationYawHead = rotationYawHead;
            rotationYawHead = yaw;
            prevRotationPitch = rotationPitch;
            rotationPitch = pitch;
        }
        smoothYaw = pervYaw;
        priority = rotPriority;
    }

    public static boolean isActived() {
        return isRotated(0);
    }

    /** True for {@code state} ticks after last active apply (OpenMyau render gate uses 1). */
    public static boolean isRotated(int maxAge) {
        return state >= 0 && state <= maxAge;
    }

    public static float getSmoothedYaw() {
        return smoothYaw;
    }

    public static float getPriority() {
        return priority;
    }

    public static float getPrevRenderYawOffset() {
        return prevRenderYawOffset;
    }

    public static float getRenderYawOffset() {
        return renderYawOffset;
    }

    public static float getPrevRotationYawHead() {
        return prevRotationYawHead;
    }

    public static float getRotationYawHead() {
        return rotationYawHead;
    }

    public static float getPrevRotationPitch() {
        return prevRotationPitch;
    }

    public static float getRotationPitch() {
        return rotationPitch;
    }

    public static void reset() {
        state = -1;
        priority = -1;
    }

    /** OpenMyau body-yaw blend toward silent look while moving / swinging. */
    private static float calculateRenderYawOffset(float targetYaw, float currentYawOffset) {
        EntityPlayerSP player = mc.thePlayer;
        float newYawOffset = currentYawOffset;
        if (player != null) {
            double deltaX = player.posX - player.prevPosX;
            double deltaZ = player.posZ - player.prevPosZ;
            if ((float) (deltaX * deltaX + deltaZ * deltaZ) > 0.0025000002f)
                newYawOffset = (float) MathHelper.atan2(deltaZ, deltaX) * 180.0f / (float) Math.PI - 90.0f;
            if (player.swingProgress > 0.0f)
                newYawOffset = targetYaw;
        }
        float wrapped = MathHelper.wrapAngleTo180_float(newYawOffset - currentYawOffset);
        currentYawOffset += wrapped * 0.3f;
        float headBodyDiff = MathHelper.wrapAngleTo180_float(targetYaw - currentYawOffset);
        if (headBodyDiff < -75.0f)
            headBodyDiff = -75.0f;
        if (headBodyDiff >= 75.0f)
            headBodyDiff = 75.0f;
        newYawOffset = targetYaw - headBodyDiff;
        if (headBodyDiff * headBodyDiff > 2500.0f)
            newYawOffset += headBodyDiff * 0.2f;
        return newYawOffset;
    }
}
