package gnu.client.module.modules.combat;

/**
 * Stop sprint before a KA hit, then restore. No duty-cycle / gap / multi-hit window.
 */
public final class KeepSprintStopState {

    public enum Phase {
        /** Sprinting (or idle). */
        IDLE,
        /** Sprint released; waiting for packet clear + walk C03(s). */
        STOPPING,
        /** Cleared — next KA hit may skip {@code ×0.6}. */
        READY
    }

    private Phase phase = Phase.IDLE;
    private int walkC03sNeeded = 1;
    private int walkC03Count;

    public Phase getPhase() {
        return phase;
    }

    public void reset() {
        phase = Phase.IDLE;
        walkC03Count = 0;
    }

    public void setWalkC03s(int n) {
        walkC03sNeeded = Math.max(1, n);
    }

    public boolean shouldSuppressSprintKey() {
        return phase == Phase.STOPPING || phase == Phase.READY;
    }

    public boolean shouldDeferAttack() {
        return phase == Phase.STOPPING;
    }

    public boolean canOwnAttack() {
        return phase == Phase.READY;
    }

    /** KA wants to hit while we still need a stop window. */
    public void beginStop() {
        if (phase == Phase.READY)
            return;
        phase = Phase.STOPPING;
        walkC03Count = 0;
    }

    public void onPacketSprintCleared() {
        // Stay STOPPING until walk C03s counted; cleared is prerequisite for counting.
    }

    public void onWalkC03(boolean packetSprintCleared) {
        if (phase != Phase.STOPPING || !packetSprintCleared)
            return;
        walkC03Count++;
        if (walkC03Count >= walkC03sNeeded)
            phase = Phase.READY;
    }

    public void onOwnedHitFinished() {
        if (phase == Phase.READY)
            phase = Phase.IDLE;
    }

    /**
     * READY but packet/client sprint came back — cannot skip {@code ×0.6}; wait for a
     * fresh walk window.
     */
    public void invalidateReady() {
        if (phase == Phase.READY) {
            phase = Phase.STOPPING;
            walkC03Count = 0;
        }
    }

    public void onWtapOrDisable() {
        reset();
    }
}
