package gnu.client.module.modules.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class GrimFoodNoSlowFsmTest {
    @Test
    public void offhandSwapUnsupportedDoesNotArm() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(false, true, false);
        assertEquals(GrimFoodNoSlowFsm.State.NONE, fsm.state());
        assertFalse(fsm.shouldFullSpeed());
        assertFalse(fsm.shouldHoldConfirms());
    }

    @Test
    public void oppositeUsableDoesNotArm() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, true, true);
        assertEquals(GrimFoodNoSlowFsm.State.NONE, fsm.state());
    }

    @Test
    public void foodGrimDisabledDoesNotArm() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, false, false);
        assertEquals(GrimFoodNoSlowFsm.State.NONE, fsm.state());
    }

    @Test
    public void happyPathToEating() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, true, false);
        assertEquals(GrimFoodNoSlowFsm.State.HOLD_CONFIRM, fsm.state());
        assertTrue(fsm.shouldHoldConfirms());
        assertFalse(fsm.shouldFullSpeed());
        fsm.onConfirmHeld();
        assertEquals(GrimFoodNoSlowFsm.State.SWAP, fsm.state());
        assertTrue(fsm.consumeSendSwap());
        assertFalse(fsm.consumeSendSwap());
        fsm.onSwapSlotUpdate();
        assertEquals(GrimFoodNoSlowFsm.State.EATING, fsm.state());
        assertTrue(fsm.shouldFullSpeed());
        assertFalse(fsm.shouldHoldConfirms());
    }

    @Test
    public void swapTimeoutTeardownWithoutFullSpeed() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, true, false);
        fsm.onConfirmHeld();
        assertTrue(fsm.consumeSendSwap());
        fsm.onSwapTimeout();
        assertEquals(GrimFoodNoSlowFsm.State.TEARDOWN, fsm.state());
        assertFalse(fsm.shouldFullSpeed());
        assertFalse(fsm.shouldHoldConfirms());
    }

    @Test
    public void idleFiveTicksEndsEating() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, true, false);
        fsm.onConfirmHeld();
        fsm.onSwapSlotUpdate();
        fsm.onTickEating(false, 5);
        assertEquals(GrimFoodNoSlowFsm.State.TEARDOWN, fsm.state());
    }

    @Test
    public void holdConfirmsOnlyDuringSetup() {
        GrimFoodNoSlowFsm fsm = new GrimFoodNoSlowFsm();
        fsm.onStartUse(true, true, false);
        assertTrue(fsm.shouldHoldConfirms());
        fsm.onConfirmHeld();
        assertTrue(fsm.shouldHoldConfirms());
        fsm.onSwapSlotUpdate();
        assertFalse(fsm.shouldHoldConfirms());
    }
}
