package gnu.client.module.modules.movement;

/**
 * Idle → Arming (Timer pulse) → one-shot capture → Cooldown.
 * Spec: docs/superpowers/specs/2026-08-05-grim-bounce-longjump-design.md
 */
public final class LongjumpState {

    public enum Phase { IDLE, ARMING, COOLDOWN }

    private Phase phase = Phase.IDLE;
    private int armingRemaining;
    private int cooldownRemaining;
    private int cooldownLength = 30;
    private boolean captured;

    public Phase getPhase() {
        return phase;
    }

    public boolean canArm() {
        return phase == Phase.IDLE;
    }

    public boolean hasCaptured() {
        return captured;
    }

    public boolean isTimerPulseActive() {
        return phase == Phase.ARMING && armingRemaining > 0 && !captured;
    }

    public void setCooldownLength(int ticks) {
        cooldownLength = Math.max(0, ticks);
    }

    public void reset() {
        phase = Phase.IDLE;
        armingRemaining = 0;
        cooldownRemaining = 0;
        captured = false;
    }

    public boolean tryStartArming(int timerTicks) {
        if (phase != Phase.IDLE)
            return false;
        phase = Phase.ARMING;
        armingRemaining = Math.max(1, timerTicks);
        captured = false;
        return true;
    }

    public boolean tryCapture() {
        if (phase != Phase.ARMING || captured)
            return false;
        captured = true;
        armingRemaining = 0;
        return true;
    }

    /** After a successful capture multiply — enter cooldown. */
    public void beginCooldownAfterCapture() {
        phase = Phase.COOLDOWN;
        cooldownRemaining = cooldownLength;
        armingRemaining = 0;
    }

    public void onClientTick() {
        if (phase == Phase.ARMING) {
            if (captured)
                return;
            if (armingRemaining > 0)
                armingRemaining--;
            if (armingRemaining <= 0 && !captured) {
                phase = Phase.COOLDOWN;
                cooldownRemaining = cooldownLength;
            }
            return;
        }
        if (phase == Phase.COOLDOWN) {
            if (cooldownRemaining > 0)
                cooldownRemaining--;
            if (cooldownRemaining <= 0) {
                phase = Phase.IDLE;
                captured = false;
            }
        }
    }
}
