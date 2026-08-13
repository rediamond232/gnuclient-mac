package gnu.client.module.modules.combat;

public final class KeepSprintLogic {

    private KeepSprintLogic() {}

    /**
     * During an active Prediction window only — blocks jump while forced-walking so
     * KeepSprint feels like sprint-only jumping (Rise-style). Not used outside the window.
     */
    public static boolean shouldSuppressJump(
            boolean moduleEnabled, boolean stopWindowActive, boolean clientSprinting) {
        return moduleEnabled && stopWindowActive && !clientSprinting;
    }

    public static boolean shouldYieldToWtap(boolean moduleEnabled, boolean wtapSuppress) {
        return moduleEnabled && wtapSuppress;
    }

    /** While hurt, leave sprint alone (vanilla KB / AttackSlow interaction). */
    public static boolean shouldSkipForHurt(boolean moduleEnabled, int hurtTime) {
        return moduleEnabled && hurtTime > 0;
    }

    public static boolean shouldOwnHit(boolean moduleEnabled, boolean wtapSuppress, boolean canOwnAttack) {
        return moduleEnabled && !wtapSuppress && canOwnAttack;
    }

    /** Need to begin a stop when about to attack and not already READY. */
    public static boolean shouldBeginStop(
            boolean moduleEnabled, boolean wtapSuppress, boolean alreadyReady, boolean needsStop) {
        return moduleEnabled && !wtapSuppress && !alreadyReady && needsStop;
    }

    /** Prediction/StopWalk must not arm or hold a walk window without a KA target. */
    public static boolean shouldArmForKillAuraTarget(boolean hasKillAuraTarget) {
        return hasKillAuraTarget;
    }

    public static boolean isStopWalkMode(String modeName) {
        return isPredictionMode(modeName);
    }

    public static boolean isPredictionMode(String modeName) {
        return "Prediction".equals(modeName) || "StopWalk".equals(modeName);
    }

    public static boolean isGrimMode(String modeName) {
        return "Grim".equals(modeName);
    }

    public static boolean usesStopWalkStateMachine(boolean enabled, String modeName) {
        return enabled && isPredictionMode(modeName);
    }

    public static boolean shouldRestoreSprintAfterGrimHit(boolean enabled, String modeName, boolean yielding) {
        return enabled && isGrimMode(modeName) && !yielding;
    }

    /**
     * Grim hard-restores client sprint after {@code ×0.6}; Prediction restores the sprint key only
     * so START goes through walking sync.
     */
    public static boolean shouldHardRestoreSprint(boolean grimMode) {
        return grimMode;
    }

    /**
     * Soft key-only restore after an owned Prediction hit — applied in {@code onAfterWalking},
     * not at attack HEAD, so the attack tick's living + C03 stay walk (avoids START thrash /
     * Simulation right after a no-{@code ×0.6} hit).
     */
    public static boolean shouldSoftRestoreAfterOwnedHit(boolean predictionOwned) {
        return predictionOwned;
    }

    /** Own only when walk-cleared and neither client nor packet sprint is true. */
    public static boolean canSkipAttackSlow(boolean ready, boolean clientSprinting, boolean serverSprinting) {
        return ready && !clientSprinting && !serverSprinting;
    }
}
