package codes.castled.stall.shop;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The menus open on each shop, so a trade or an edit can redraw every one of them. Bookkeeping is synchronized and snapshots can be read from other regions.
 *
 * @param <V> the platform's menu type
 */
public final class ShopViewers<V>
{
    private final Map<UUID, Set<V>> byShop = new HashMap<>();

    public synchronized void add(UUID shopId, V viewer)
    {
        byShop.computeIfAbsent(shopId, id -> new LinkedHashSet<>()).add(viewer);
    }

    public synchronized void remove(UUID shopId, V viewer)
    {
        Set<V> viewers = byShop.get(shopId);
        if (viewers != null && viewers.remove(viewer) && viewers.isEmpty())
        {
            byShop.remove(shopId);
        }
    }

    /** A copy, so callers may close menus while iterating. */
    public synchronized List<V> of(UUID shopId)
    {
        Set<V> viewers = byShop.get(shopId);
        return viewers == null ? List.of() : List.copyOf(viewers);
    }

    /** Every open menu on every shop, as a copy. */
    public synchronized List<V> all()
    {
        return byShop.values().stream().flatMap(Set::stream).toList();
    }
}
