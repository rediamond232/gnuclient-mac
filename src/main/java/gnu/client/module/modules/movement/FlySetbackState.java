package gnu.client.module.modules.movement;

/**
 * Soft setback-budget fly state for Grim Simulation.
 *
 * <p>Grim immediate-setback is {@code 0.1} and advantage setback is {@code 1.0}
 * cumulative offset ({@code OffsetHandler}). Full hover (~0.08 vertical) plus
 * fast horizontal exceeds 0.1 every tick → setback spam. We keep estimated
 * offset under {@link #MAX_SAFE_OFFSET}, pulse illegal ticks with vanilla rest
 * so advantage can decay, and lock longer after each S08.</p>
 */
public final class FlySetbackState {

    public static final int MODE_HOVER = 0;
    public static final int MODE_GLIDE = 1;

    /** Soft hover — slight fall so vertical offset stays ~0.06 vs full cancel ~0.08. */
    public static final double HOVER_MOTION_Y = -0.02;

    /** Glide downward cap — closer to gravity than a hard float. */
    public static final double GLIDE_MAX_FALL = 0.055;

    /**
     * Under Grim default {@code immediate-setback-threshold} (0.1).
     * Leaves headroom for prediction noise.
     */
    public static final double MAX_SAFE_OFFSET = 0.085;

    /** Typical mid-air motionY Grim expects after a fall tick (approx). */
    public static final double TYPICAL_FALL_MOTION_Y = -0.08;

    private boolean locked;
    private int remainingCooldown;
    private int pulseTick;
    private int activeTicks = 2;
    private int restTicks = 3;

    public boolean isLocked() {
        return locked;
    }

    public void setPulse(int activeTicks, int restTicks) {
        int nextActive = Math.max(0, activeTicks);
        int nextRest = Math.max(0, restTicks);
        // Do not reset pulseTick when only re-applying the same duty cycle each tick.
        if (nextActive != this.activeTicks || nextRest != this.restTicks)
            this.pulseTick = 0;
        this.activeTicks = nextActive;
        this.restTicks = nextRest;
    }

    public boolean canApplyAirControl() {
        if (locked)
            return false;
        int cycle = activeTicks + restTicks;
        if (cycle <= 0 || activeTicks <= 0)
            return activeTicks > 0;
        return (pulseTick % cycle) < activeTicks;
    }

    public void onSetbackReceived(int cooldownTicks) {
        locked = true;
        remainingCooldown = Math.max(0, cooldownTicks);
        pulseTick = 0;
    }

    public void onClientTick() {
        if (locked) {
            if (remainingCooldown <= 0) {
                locked = false;
            } else {
                remainingCooldown--;
                if (remainingCooldown <= 0)
                    locked = false;
            }
        }
        if (!locked)
            pulseTick++;
    }

    public void reset() {
        locked = false;
        remainingCooldown = 0;
        pulseTick = 0;
    }

    public static double verticalMotion(int mode, double currentMotionY) {
        if (mode == MODE_GLIDE) {
            if (currentMotionY < -GLIDE_MAX_FALL)
                return -GLIDE_MAX_FALL;
            return currentMotionY;
        }
        return HOVER_MOTION_Y;
    }

    public static double[] horizontalMotion(float yaw, float moveForward, float moveStrafe, double speed) {
        if (moveForward == 0.0f && moveStrafe == 0.0f)
            return null;
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double x = -sin * moveForward + cos * moveStrafe;
        double z = cos * moveForward + sin * moveStrafe;
        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0E-6)
            return null;
        x = (x / len) * speed;
        z = (z / len) * speed;
        return new double[] { x, z };
    }

    /**
     * Scale illegal motion toward {@code baselineMy} (and zero horizontal baseline)
     * so the 3D delta stays ≤ {@code maxOffset}.
     */
    public static double[] clampToBudget(double mx, double my, double mz, double baselineMy, double maxOffset) {
        double dx = mx;
        double dy = my - baselineMy;
        double dz = mz;
        double offset = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (offset <= maxOffset || offset < 1.0E-9)
            return new double[] { mx, my, mz };
        double scale = maxOffset / offset;
        return new double[] {
                dx * scale,
                baselineMy + dy * scale,
                dz * scale
        };
    }
}
