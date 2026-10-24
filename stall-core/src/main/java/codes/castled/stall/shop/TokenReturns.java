package codes.castled.stall.shop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

/**
 * Tokens owed to players whose shops were removed while they were offline, waiting for them to collect
 * with {@code /stall reclaim}. Bookkeeping methods are synchronized; world access still belongs to its owning region.
 */
public final class TokenReturns
{
    private final Map<UUID, Integer> owed = new HashMap<>();
    private final Set<UUID> refundedShops = new HashSet<>();

    /** Records an orphan once, even if a crash leaves its old shop file behind after saving the refund. */
    public synchronized boolean refund(Shop<?> shop)
    {
        if (!refundedShops.add(shop.id())) return false;
        if (shop.tokenConsumed()) add(shop.owner(), 1);
        return true;
    }

    public synchronized boolean wasRefunded(UUID shop)
    {
        return refundedShops.contains(shop);
    }

    public synchronized Set<UUID> refundedShops()
    {
        return Set.copyOf(refundedShops);
    }

    public synchronized void restoreRefunded(Set<UUID> shops)
    {
        refundedShops.clear();
        refundedShops.addAll(shops);
    }

    public synchronized void add(UUID player, int count)
    {
        if (count < 1)
        {
            throw new IllegalArgumentException("count must be at least 1, was " + count);
        }
        owed.merge(player, count, Integer::sum);
    }

    public synchronized int owed(UUID player)
    {
        return owed.getOrDefault(player, 0);
    }

    /** Everyone who is owed tokens, and how many. */
    public synchronized Map<UUID, Integer> snapshot()
    {
        return Map.copyOf(owed);
    }

    /** Replaces what is owed with what was saved. */
    public synchronized void restore(Map<UUID, Integer> saved)
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
    public synchronized int claim(UUID player)
    {
        Integer count = owed.remove(player);
        return count == null ? 0 : count;
    }

    /** Takes one queued return on the owner's entity thread; a retired task leaves it queued. */
    public synchronized boolean claimOne(UUID player)
    {
        int count = owed(player);
        if (count == 0) return false;
        if (count == 1) owed.remove(player);
        else owed.put(player, count - 1);
        return true;
    }
}
