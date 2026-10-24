package codes.castled.stall.shop;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every shop the server knows, found by the location of its container. Bookkeeping methods are synchronized; world access still belongs to its owning region. */
public final class ShopRegistry<I>
{
    private final Map<ShopLocation, Shop<I>> byLocation = new HashMap<>();

    public synchronized Optional<Shop<I>> at(ShopLocation location)
    {
        return Optional.ofNullable(byLocation.get(location));
    }

    /** Whether {@code shop} is still the shop registered at its own location. */
    public synchronized boolean isCurrent(Shop<I> shop)
    {
        return byLocation.get(shop.location()) == shop;
    }

    public synchronized void add(Shop<I> shop)
    {
        Shop<I> existing = byLocation.putIfAbsent(shop.location(), shop);
        if (existing != null)
        {
            throw new IllegalStateException("There is already a shop at " + shop.location());
        }
    }

    public synchronized boolean remove(Shop<I> shop)
    {
        return byLocation.remove(shop.location(), shop);
    }

    public synchronized Collection<Shop<I>> all()
    {
        return List.copyOf(byLocation.values());
    }

    public synchronized List<Shop<I>> inChunk(String world, int x, int z)
    {
        return byLocation.values().stream().filter(shop -> shop.location().world().equals(world)
                && (shop.location().x() >> 4) == x && (shop.location().z() >> 4) == z).toList();
    }

    public synchronized long countOwnedBy(UUID owner)
    {
        return byLocation.values().stream().filter(shop -> shop.isOwner(owner)).count();
    }
}
