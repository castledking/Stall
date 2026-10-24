package codes.castled.stall.shop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tokens owed to players whose shops were removed while they were offline, waiting for them to collect
 * with {@code /stall reclaim}. Server thread only.
 */
public final class TokenReturns
{
    private final Map<UUID, Integer> owed = new HashMap<>();

    public void add(UUID player, int count)
    {
        if (count < 1)
        {
            throw new IllegalArgumentException("count must be at least 1, was " + count);
        }
        owed.merge(player, count, Integer::sum);
    }

    public int owed(UUID player)
    {
        return owed.getOrDefault(player, 0);
    }

    /** Everyone who is owed tokens, and how many. */
    public Map<UUID, Integer> snapshot()
    {
        return Map.copyOf(owed);
    }

    /** Replaces what is owed with what was saved. */
    public void restore(Map<UUID, Integer> saved)
    {
        owed.clear();
        saved.forEach((player, count) ->
        {
            if (count > 0)
            {
                owed.put(player, count);
            }
        });
    }

    /** Hands over everything {@code player} is owed, and forgets it. */
    public int claim(UUID player)
    {
        Integer count = owed.remove(player);
        return count == null ? 0 : count;
    }
}
