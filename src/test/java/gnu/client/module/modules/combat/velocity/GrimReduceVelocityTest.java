package gnu.client.module.modules.combat.velocity;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class GrimReduceVelocityTest {

    @Test
    public void reduceAttackOrderIsC02ThenC0A() {
        String[] order = GrimReduceVelocity.reduceAttackPacketOrder();
        assertEquals(2, order.length);
        assertArrayEquals(
                new String[] {"C02PacketUseEntity", "C0APacketAnimation"},
                order);
    }
}
