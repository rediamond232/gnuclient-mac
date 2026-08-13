package gnu.client.module.modules.player;

/**
 * Pure FSM for Via food Grim NoSlow (transaction hold → offhand swap → eating).
 * Arm only when offhand swap is emitable (ViaForge target ≥ 1.16).
 */
public final class GrimFoodNoSlowFsm {

    public static final int SWAP_TIMEOUT_TICKS = 10;
    public static final int IDLE_ABORT_TICKS = 5;

    public enum State {
        NONE,
        HOLD_CONFIRM,
        SWAP,
        EATING,
        TEARDOWN
    }

    private State state = State.NONE;
    private boolean sendSwapPending;

    public State state() {
        return state;
    }

    /**
     * @param supportsOffhandSwap ViaForge target ≥ 1.16 ({@link ViaModernGate#supportsOffhandSwap()})
     */
    public void onStartUse(boolean supportsOffhandSwap, boolean foodGrimEnabled, boolean oppositeHandUsable) {
        if (state != State.NONE)
            return;
        if (!supportsOffhandSwap || !foodGrimEnabled || oppositeHandUsable)
            return;
        state = State.HOLD_CONFIRM;
        sendSwapPending = false;
    }

    public void onConfirmHeld() {
        if (state != State.HOLD_CONFIRM)
            return;
        state = State.SWAP;
        sendSwapPending = true;
    }

    public void onSwapSlotUpdate() {
        if (state != State.SWAP)
            return;
        state = State.EATING;
        sendSwapPending = false;
    }

    public void onSwapTimeout() {
        if (state != State.SWAP)
            return;
        state = State.TEARDOWN;
        sendSwapPending = false;
    }

    public void onTickEating(boolean stillUsing, int idleTicks) {
        if (state != State.EATING)
            return;
        if (!stillUsing && idleTicks >= IDLE_ABORT_TICKS)
            state = State.TEARDOWN;
    }

    public void onForceTeardown() {
        if (state == State.NONE)
            return;
        state = State.TEARDOWN;
        sendSwapPending = false;
    }

    public void afterTeardownComplete() {
        state = State.NONE;
        sendSwapPending = false;
    }

    public boolean shouldFullSpeed() {
        // Full-speed only while EATING — after a real inventory desync cleared Grim slow.
        // On 1.8+Via, SWAP is cancelled; no slot update → timeout → never EATING.
        return state == State.EATING;
    }

    public boolean shouldHoldConfirms() {
        // Hold only through setup. Holding through EATING stalls Grim transactions and
        // causes BadPacketsN when setback S08 teleports are skipped.
        return state == State.HOLD_CONFIRM || state == State.SWAP;
    }

    /** @return true once when entering SWAP; subsequent calls false until re-armed */
    public boolean consumeSendSwap() {
        if (!sendSwapPending || state != State.SWAP)
            return false;
        sendSwapPending = false;
        return true;
    }
}
