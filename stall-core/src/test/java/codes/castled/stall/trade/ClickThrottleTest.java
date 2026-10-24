package codes.castled.stall.trade;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickThrottleTest
{
    @Test
    void ignoresClicksWithinTheCooldown()
    {
        ClickThrottle throttle = new ClickThrottle(4);
        UUID player = UUID.randomUUID();

        assertTrue(throttle.tryAcquire(player, 100));
        assertFalse(throttle.tryAcquire(player, 101));
        assertFalse(throttle.tryAcquire(player, 103));
        assertTrue(throttle.tryAcquire(player, 104));
    }

    @Test
    void tracksPlayersSeparately()
    {
        ClickThrottle throttle = new ClickThrottle(4);

        assertTrue(throttle.tryAcquire(UUID.randomUUID(), 100));
        assertTrue(throttle.tryAcquire(UUID.randomUUID(), 100));
    }

    @Test
    void forgettingAPlayerResetsTheirCooldown()
    {
        ClickThrottle throttle = new ClickThrottle(4);
        UUID player = UUID.randomUUID();

        assertTrue(throttle.tryAcquire(player, 100));
        throttle.forget(player);
        assertTrue(throttle.tryAcquire(player, 101));
    }
}
