package codes.castled.stall.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ignores a player's buy clicks that arrive too soon after the last accepted one, so a double click
 * does not buy twice by accident. Each accepted click is still a complete, separate trade.
 */
public final class ClickThrottle
{
    private final long cooldownTicks;
    private final Map<UUID, Long> lastAccepted = new HashMap<>();

    public ClickThrottle(long cooldownTicks)
    {
        if (cooldownTicks < 0)
        {
            throw new IllegalArgumentException("cooldownTicks must not be negative, was " + cooldownTicks);
        }
        this.cooldownTicks = cooldownTicks;
    }

    /** Records a click at {@code tick} and returns whether it should be acted on. */
    public boolean tryAcquire(UUID player, long tick)
    {
        Long last = lastAccepted.get(player);
        if (last != null && tick - last < cooldownTicks)
        {
            return false;
        }
        lastAccepted.put(player, tick);
        return true;
    }

    public void forget(UUID player)
    {
        lastAccepted.remove(player);
    }
}
