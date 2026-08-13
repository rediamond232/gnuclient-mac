package gnu.client.module.modules.movement;

import gnu.client.module.Category;
import gnu.client.module.Module;
import gnu.client.runtime.mc.Mc;
import net.minecraft.client.entity.EntityPlayerSP;

import java.util.Arrays;
import java.util.List;

/**
 * Timewarp Fast Stop — opposite keybind tap on movement key release.
 * Legit = 1 tick; Blatant = hold opposite key until velocity < 0.01. Cancels if you press any movement key.
 */
public final class FastStopModule extends Module {

    private static final List<String> MODES = Arrays.asList("Blatant", "Legit");
    private static final double VELOCITY_STOP_THRESHOLD = 0.01;
    private static final int LEGIT_HOLD_TICKS = 1;
    private static final int HOLD_UNTIL_STOP = -1; // blatant: hold opposite key until velocity < 0.01

    private final gnu.client.module.setting.ModeSetting mode =
            addSetting(new gnu.client.module.setting.ModeSetting("Mode", 1, MODES));

    private boolean physicalForward;
    private boolean physicalBack;
    private boolean physicalLeft;
    private boolean physicalRight;

    private int holdForwardTicks;
    private int holdBackTicks;
    private int holdLeftTicks;
    private int holdRightTicks;

    public FastStopModule() {
        super("Instant Stop", "Stop instantly by counter-strafing", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        syncPhysicalKeys();
        resetHolds();
    }

    @Override
    public void onDisable() {
        resetHolds();
        restorePhysicalMovementKeys();
    }

    @Override
    public void onTickStart() {
        if (!isEnabled())
            return;

        if (Mc.currentScreen() != null) {
            resetHolds();
            restorePhysicalMovementKeys();
            syncPhysicalKeys();
            return;
        }

        cancelHoldsOnUserInput();
        detectKeyReleases();
        applyHoldKeybinds();
        syncPhysicalKeys();
    }

    private void cancelHoldsOnUserInput() {
        if (!hasActiveHold())
            return;
        if (Mc.isForwardKeyHeld()
                || Mc.isBackKeyHeld()
                || Mc.isLeftKeyHeld()
                || Mc.isRightKeyHeld()) {
            resetHolds();
            restorePhysicalMovementKeys();
        }
    }

    private void detectKeyReleases() {
        if (!hasHorizontalVelocity())
            return;

        boolean forward = Mc.isForwardKeyHeld();
        boolean back = Mc.isBackKeyHeld();
        boolean left = Mc.isLeftKeyHeld();
        boolean right = Mc.isRightKeyHeld();

        if (physicalForward && !forward)
            startOppositeHold(OppositeKey.BACK);
        if (physicalBack && !back)
            startOppositeHold(OppositeKey.FORWARD);
        if (physicalLeft && !left && !physicalRight)
            startOppositeHold(OppositeKey.RIGHT);
        if (physicalRight && !right && !physicalLeft)
            startOppositeHold(OppositeKey.LEFT);
    }

    private enum OppositeKey { FORWARD, BACK, LEFT, RIGHT }

    private void startOppositeHold(OppositeKey oppositeKey) {
        int ticks = isLegit() ? LEGIT_HOLD_TICKS : HOLD_UNTIL_STOP;
        switch (oppositeKey) {
            case BACK:
                holdBackTicks = ticks;
                break;
            case FORWARD:
                holdForwardTicks = ticks;
                break;
            case LEFT:
                holdLeftTicks = ticks;
                break;
            case RIGHT:
                holdRightTicks = ticks;
                break;
            default:
                break;
        }
    }

    private void applyHoldKeybinds() {
        if (holdForwardTicks > 0 || holdForwardTicks == HOLD_UNTIL_STOP)
            Mc.setForwardKeyState(true);
        else
            Mc.setForwardKeyState(Mc.isForwardKeyHeld());

        if (holdBackTicks > 0 || holdBackTicks == HOLD_UNTIL_STOP)
            Mc.setBackKeyState(true);
        else
            Mc.setBackKeyState(Mc.isBackKeyHeld());

        if (holdLeftTicks > 0 || holdLeftTicks == HOLD_UNTIL_STOP)
            Mc.setLeftKeyState(true);
        else
            Mc.setLeftKeyState(Mc.isLeftKeyHeld());

        if (holdRightTicks > 0 || holdRightTicks == HOLD_UNTIL_STOP)
            Mc.setRightKeyState(true);
        else
            Mc.setRightKeyState(Mc.isRightKeyHeld());

        // Decrement tick-based holds; check velocity for HOLD_UNTIL_STOP
        if (holdForwardTicks == HOLD_UNTIL_STOP) {
            if (!hasHorizontalVelocity())
                holdForwardTicks = 0;
        } else if (holdForwardTicks > 0) {
            holdForwardTicks--;
        }
        if (holdBackTicks == HOLD_UNTIL_STOP) {
            if (!hasHorizontalVelocity())
                holdBackTicks = 0;
        } else if (holdBackTicks > 0) {
            holdBackTicks--;
        }
        if (holdLeftTicks == HOLD_UNTIL_STOP) {
            if (!hasHorizontalVelocity())
                holdLeftTicks = 0;
        } else if (holdLeftTicks > 0) {
            holdLeftTicks--;
        }
        if (holdRightTicks == HOLD_UNTIL_STOP) {
            if (!hasHorizontalVelocity())
                holdRightTicks = 0;
        } else if (holdRightTicks > 0) {
            holdRightTicks--;
        }
    }

    private boolean hasActiveHold() {
        return holdForwardTicks > 0 || holdForwardTicks == HOLD_UNTIL_STOP
            || holdBackTicks > 0 || holdBackTicks == HOLD_UNTIL_STOP
            || holdLeftTicks > 0 || holdLeftTicks == HOLD_UNTIL_STOP
            || holdRightTicks > 0 || holdRightTicks == HOLD_UNTIL_STOP;
    }

    private boolean isLegit() {
        return mode.getValue() == 1;
    }

    private boolean hasHorizontalVelocity() {
        EntityPlayerSP player = Mc.player();
        if (player == null)
            return false;
        return Math.abs(player.motionX) >= VELOCITY_STOP_THRESHOLD
                || Math.abs(player.motionZ) >= VELOCITY_STOP_THRESHOLD;
    }

    private void syncPhysicalKeys() {
        physicalForward = Mc.isForwardKeyHeld();
        physicalBack = Mc.isBackKeyHeld();
        physicalLeft = Mc.isLeftKeyHeld();
        physicalRight = Mc.isRightKeyHeld();
    }

    private void restorePhysicalMovementKeys() {
        Mc.setForwardKeyState(Mc.isForwardKeyHeld());
        Mc.setBackKeyState(Mc.isBackKeyHeld());
        Mc.setLeftKeyState(Mc.isLeftKeyHeld());
        Mc.setRightKeyState(Mc.isRightKeyHeld());
    }

    private void resetHolds() {
        holdForwardTicks = 0;
        holdBackTicks = 0;
        holdLeftTicks = 0;
        holdRightTicks = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[] { mode.getCurrentMode() };
    }
}
