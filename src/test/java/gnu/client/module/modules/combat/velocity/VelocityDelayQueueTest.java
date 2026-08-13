package gnu.client.module.modules.combat.velocity;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VelocityDelayQueueTest {

    private VelocityDelayQueue queue;

    @Before
    public void setUp() {
        queue = new VelocityDelayQueue();
    }

    @Test
    public void offerThenClearEmptiesQueueAndResetsDelayState() {
        queue.offer(new Object());
        queue.startDelay(10L);
        assertTrue(queue.isDelaying());

        queue.clear();

        assertFalse(queue.isDelaying());
        assertEquals(0L, queue.ticksHeld(20L));
    }

    @Test
    public void startDelayAndTicksHeld() {
        queue.startDelay(5L);
        assertTrue(queue.isDelaying());
        assertEquals(0L, queue.ticksHeld(5L));
        assertEquals(3L, queue.ticksHeld(8L));
    }

    @Test
    public void stopDelayAndFlushClearsDelayingWithoutPlayer() {
        queue.offer(new Object());
        queue.startDelay(1L);
        assertTrue(queue.isDelaying());

        queue.stopDelayAndFlush();

        assertFalse(queue.isDelaying());
        assertEquals(0L, queue.ticksHeld(100L));
    }

    @Test
    public void ticksHeldZeroWhenNotDelaying() {
        assertFalse(queue.isDelaying());
        assertEquals(0L, queue.ticksHeld(99L));
    }

    /**
     * Regression: offers arrive on the Netty I/O thread while the client thread drains, which
     * threw {@code ConcurrentModificationException} when the backing store was an
     * {@code ArrayDeque}.
     */
    @Test
    public void concurrentOfferWhileFlushingDoesNotThrow() throws Exception {
        final int count = 20_000;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < count; i++)
                    queue.offer(new Object());
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "test-netty-thread");

        producer.start();
        try {
            while (done.getCount() > 0) {
                queue.startDelay(0L);
                queue.stopDelayAndFlush();
            }
            queue.stopDelayAndFlush();
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        }

        assertTrue(done.await(10, TimeUnit.SECONDS));
        producer.join(10_000L);
        assertNull(String.valueOf(failure.get()), failure.get());
        assertFalse(queue.isDelaying());
    }
}
