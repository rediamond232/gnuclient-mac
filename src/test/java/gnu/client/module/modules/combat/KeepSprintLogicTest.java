package gnu.client.module.modules.combat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeepSprintLogicTest {

    @Test
    public void beginStopWhenNeeded() {
        assertTrue(KeepSprintLogic.shouldBeginStop(true, false, false, true));
        assertFalse(KeepSprintLogic.shouldBeginStop(true, false, true, true));
        assertFalse(KeepSprintLogic.shouldBeginStop(true, false, false, false));
        assertFalse(KeepSprintLogic.shouldBeginStop(true, true, false, true));
    }

    @Test
    public void armOnlyWithKillAuraTarget() {
        assertTrue(KeepSprintLogic.shouldArmForKillAuraTarget(true));
        assertFalse(KeepSprintLogic.shouldArmForKillAuraTarget(false));
    }

    @Test
    public void ownHitWhenReady() {
        assertTrue(KeepSprintLogic.shouldOwnHit(true, false, true));
        assertFalse(KeepSprintLogic.shouldOwnHit(true, false, false));
    }

    @Test
    public void jumpSuppressOnlyInStopWindow() {
        assertTrue(KeepSprintLogic.shouldSuppressJump(true, true, false));
        assertFalse(KeepSprintLogic.shouldSuppressJump(true, true, true));
        assertFalse(KeepSprintLogic.shouldSuppressJump(true, false, false));
        assertFalse(KeepSprintLogic.shouldSuppressJump(false, true, false));
    }

    @Test
    public void skipWhileHurt() {
        assertTrue(KeepSprintLogic.shouldSkipForHurt(true, 1));
        assertTrue(KeepSprintLogic.shouldSkipForHurt(true, 10));
        assertFalse(KeepSprintLogic.shouldSkipForHurt(true, 0));
        assertFalse(KeepSprintLogic.shouldSkipForHurt(false, 5));
    }

    @Test
    public void isPredictionModeRecognized() {
        assertTrue(KeepSprintLogic.isPredictionMode("Prediction"));
        assertTrue(KeepSprintLogic.isStopWalkMode("Prediction"));
        assertTrue(KeepSprintLogic.isStopWalkMode("StopWalk")); // legacy alias
        assertFalse(KeepSprintLogic.isPredictionMode("Grim"));
    }

    @Test
    public void isGrimModeRecognized() {
        assertTrue(KeepSprintLogic.isGrimMode("Grim"));
        assertFalse(KeepSprintLogic.isGrimMode("Prediction"));
    }

    @Test
    public void usesStopWalkStateMachine() {
        assertTrue(KeepSprintLogic.usesStopWalkStateMachine(true, "Prediction"));
        assertFalse(KeepSprintLogic.usesStopWalkStateMachine(true, "Grim"));
        assertFalse(KeepSprintLogic.usesStopWalkStateMachine(false, "Prediction"));
    }

    @Test
    public void shouldRestoreSprintAfterGrimHit() {
        assertTrue(KeepSprintLogic.shouldRestoreSprintAfterGrimHit(true, "Grim", false));
        assertFalse(KeepSprintLogic.shouldRestoreSprintAfterGrimHit(true, "Grim", true));
        assertFalse(KeepSprintLogic.shouldRestoreSprintAfterGrimHit(true, "Prediction", false));
        assertFalse(KeepSprintLogic.shouldRestoreSprintAfterGrimHit(false, "Grim", false));
    }

    @Test
    public void modeHelpersRejectUnknownNames() {
        assertFalse(KeepSprintLogic.isPredictionMode(null));
        assertFalse(KeepSprintLogic.isPredictionMode(""));
        assertFalse(KeepSprintLogic.isPredictionMode("grim"));
        assertFalse(KeepSprintLogic.isGrimMode(null));
        assertFalse(KeepSprintLogic.isGrimMode(""));
        assertFalse(KeepSprintLogic.isGrimMode("prediction"));
        assertFalse(KeepSprintLogic.usesStopWalkStateMachine(true, "Unknown"));
    }

    @Test
    public void shouldRestoreSprintAfterGrimHitYieldingOrWrongMode() {
        assertFalse(KeepSprintLogic.shouldRestoreSprintAfterGrimHit(false, "Grim", true));
        assertFalse(KeepSprintLogic.shouldRestoreSprintAfterGrimHit(false, "Prediction", true));
        assertFalse(KeepSprintLogic.shouldRestoreSprintAfterGrimHit(true, "Prediction", true));
    }

    @Test
    public void softRestoreAfterOwnedPredictionHit() {
        assertTrue(KeepSprintLogic.shouldSoftRestoreAfterOwnedHit(true));
        assertFalse(KeepSprintLogic.shouldSoftRestoreAfterOwnedHit(false));
    }

    @Test
    public void canSkipAttackSlowOnlyWhenFullyCleared() {
        assertTrue(KeepSprintLogic.canSkipAttackSlow(true, false, false));
        assertFalse(KeepSprintLogic.canSkipAttackSlow(true, true, false));
        assertFalse(KeepSprintLogic.canSkipAttackSlow(true, false, true));
        assertFalse(KeepSprintLogic.canSkipAttackSlow(false, false, false));
    }

    @Test
    public void hardRestoreOnlyInGrimMode() {
        assertTrue(KeepSprintLogic.shouldHardRestoreSprint(true));
        assertFalse(KeepSprintLogic.shouldHardRestoreSprint(false));
    }

    @Test
    public void predictionSoftVsGrimHardRestore() {
        assertTrue(KeepSprintLogic.shouldSoftRestoreAfterOwnedHit(
                KeepSprintLogic.isPredictionMode("Prediction")));
        assertFalse(KeepSprintLogic.shouldHardRestoreSprint(
                KeepSprintLogic.isGrimMode("Prediction")));
        assertTrue(KeepSprintLogic.shouldHardRestoreSprint(
                KeepSprintLogic.isGrimMode("Grim")));
        assertFalse(KeepSprintLogic.shouldSoftRestoreAfterOwnedHit(false));
    }

    /** Prediction-mode helpers; module gates them with {@link KeepSprintLogic#usesStopWalkStateMachine}. */
    @Test
    public void predictionRegressionWhenStateMachineActive() {
        assertTrue(KeepSprintLogic.usesStopWalkStateMachine(true, "Prediction"));
        assertTrue(KeepSprintLogic.shouldBeginStop(true, false, false, true));
        assertTrue(KeepSprintLogic.shouldOwnHit(true, false, true));
        assertFalse(KeepSprintLogic.shouldOwnHit(true, false, false));
        assertTrue(KeepSprintLogic.shouldSuppressJump(true, true, false));
        assertFalse(KeepSprintLogic.shouldSuppressJump(true, false, false));
        assertFalse(KeepSprintLogic.usesStopWalkStateMachine(true, "Grim"));
    }
}
