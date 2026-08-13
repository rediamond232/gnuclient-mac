package gnu.client.module.modules.player;

import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;

public class GrimNoSlowControllerTest {

    private static GrimNoSlowController.SkipContext baseUsingContext() {
        GrimNoSlowController.SkipContext ctx = new GrimNoSlowController.SkipContext();
        ctx.usingItem = true;
        ctx.preGrimPhase = -1;
        ctx.preGrimAttackAllowed = false;
        ctx.willGrimAttackThisTick = false;
        ctx.attackSentThisTick = false;
        ctx.releaseUseItemThisTick = false;
        ctx.entityActionSentThisTick = false;
        return ctx;
    }

    @Test
    public void nextSlotNeverRepeatsLastSentSlot() {
        assertEquals(1, GrimNoSlowController.nextSlot(0, 1, true, -1));
        assertEquals(0, GrimNoSlowController.nextSlot(0, 1, false, 1));
        assertEquals(2, GrimNoSlowController.nextSlot(1, 1, false, 1));

        int last = -1;
        for (int i = 0; i < 20; i++) {
            boolean toggle = (i % 2) == 0;
            int next = GrimNoSlowController.nextSlot(0, 1, toggle, last);
            if (last >= 0)
                assertNotEquals(last, next);
            last = next;
        }
    }

    @Test
    public void nextGrimSlotDelegatesToControllerNextSlot() {
        assertEquals(
                GrimNoSlowController.nextSlot(3, 2, true, 5),
                NoSlowModule.nextGrimSlot(3, 2, true, 5));
    }

    @Test
    public void shouldNotSendWhenNotUsingItem() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.usingItem = false;
        assertFalse(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSkipWhenAttackSentThisTick() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.attackSentThisTick = true;
        assertFalse(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSkipWhenReleaseUseItemSentThisTick() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.releaseUseItemThisTick = true;
        assertFalse(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSkipWhenEntityActionSentThisTick() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.entityActionSentThisTick = true;
        assertFalse(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSkipWhenPreGrimPhaseZeroAndAttackAllowed() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.preGrimAttackAllowed = true;
        ctx.preGrimPhase = 0;
        assertFalse(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSkipWhenPreGrimPhaseTwoAndAttackAllowed() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.preGrimAttackAllowed = true;
        ctx.preGrimPhase = 2;
        assertFalse(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSendWhenPreGrimPhaseTwoButAttackNotAllowed() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.preGrimAttackAllowed = false;
        ctx.preGrimPhase = 2;
        assertTrue(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSkipWhenWillGrimAttackThisTick() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.preGrimAttackAllowed = true;
        ctx.willGrimAttackThisTick = true;
        assertFalse(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSkipWhenPreGrimPhaseThreeOrFourAndAttackAllowed() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.preGrimAttackAllowed = true;
        ctx.preGrimPhase = 3;
        assertFalse(GrimNoSlowController.shouldSendSlotSpoof(ctx));
        ctx.preGrimPhase = 4;
        assertFalse(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSendWhenPreGrimPhaseOneUsingAndNoFlags() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.preGrimAttackAllowed = true;
        ctx.preGrimPhase = 1;
        assertTrue(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void shouldSendFoodGrimEatingNoKaPhaseAndNoFlags() {
        GrimNoSlowController.SkipContext ctx = baseUsingContext();
        ctx.preGrimPhase = -1;
        ctx.preGrimAttackAllowed = false;
        assertTrue(GrimNoSlowController.shouldSendSlotSpoof(ctx));
    }

    @Test
    public void onClientTickStartClearsOutboundConflictFlags() throws Exception {
        GrimNoSlowController controller = new GrimNoSlowController();
        setFlag(controller, "attackSentThisTick", true);
        setFlag(controller, "releaseUseItemThisTick", true);
        setFlag(controller, "entityActionSentThisTick", true);

        controller.onClientTickStart();

        assertFalse(getFlag(controller, "attackSentThisTick"));
        assertFalse(getFlag(controller, "releaseUseItemThisTick"));
        assertFalse(getFlag(controller, "entityActionSentThisTick"));
    }

    private static void setFlag(GrimNoSlowController controller, String name, boolean value)
            throws Exception {
        Field field = GrimNoSlowController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(controller, value);
    }

    private static boolean getFlag(GrimNoSlowController controller, String name) throws Exception {
        Field field = GrimNoSlowController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(controller);
    }
}
