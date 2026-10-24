package codes.castled.stall.shop;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every shop the server knows, found by the location of its container. Server thread only. */
public final class ShopRegistry<I>
{
    private final Map<ShopLocation, Shop<I>> byLocation = new HashMap<>();

    public Optional<Shop<I>> at(ShopLocation location)
    {
        return Optional.ofNullable(byLocation.get(location));
    }

    /** Whether {@code shop} is still the shop registered at its own location. */
    public boolean isCurrent(Shop<I> shop)
    {
        return byLocation.get(shop.location()) == shop;
    }

    public void add(Shop<I> shop)
    {
        Shop<I> existing = byLocation.putIfAbsent(shop.location(), shop);
        if (existing != null)
        {
            throw new IllegalStateException("There is already a shop at " + shop.location());
        }
    }

    public boolean remove(Shop<I> shop)
    {
        return byLocation.remove(shop.location(), shop);
    }

    public Collection<Shop<I>> all()
    {
        return List.copyOf(byLocation.values());
    }

    public long countOwnedBy(UUID owner)
    {
        return byLocation.values().stream().filter(shop -> shop.isOwner(owner)).count();
    }
}
