package codes.castled.stall.shop;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionalBookkeepingTest
{
    @Test
    void independentRegionsCanRegisterAndRemoveShopsWithoutLosingOtherShops() throws Exception
    {
        ShopRegistry<String> registry = new ShopRegistry<>();
        UUID owner = UUID.randomUUID();
        List<Callable<Void>> create = new ArrayList<>();
        for (int i = 0; i < 100; i++)
        {
            int x = i * 1000;
            create.add(() ->
            {
                registry.add(new Shop<>(UUID.randomUUID(), owner, "Owner", new ShopLocation("world", x, 64, 0), true));
                registry.all();
                return null;
            });
        }
        try (var regions = Executors.newFixedThreadPool(4))
        {
            for (var result : regions.invokeAll(create)) result.get();
            assertEquals(100, registry.countOwnedBy(owner));
            List<Callable<Boolean>> remove = registry.all().stream().<Callable<Boolean>>map(shop -> () -> registry.remove(shop)).toList();
            for (var result : regions.invokeAll(remove)) assertEquals(true, result.get());
        }
        assertEquals(0, registry.countOwnedBy(owner));
    }

    @Test
    void viewerSnapshotsRemainUsableWhileOtherRegionsCloseMenus() throws Exception
    {
        ShopViewers<Integer> viewers = new ShopViewers<>();
        UUID shop = UUID.randomUUID();
        List<Callable<Void>> open = new ArrayList<>();
        for (int i = 0; i < 100; i++)
        {
            int viewer = i;
            open.add(() -> { viewers.add(shop, viewer); viewers.all(); return null; });
        }
        try (var regions = Executors.newFixedThreadPool(4))
        {
            for (var result : regions.invokeAll(open)) result.get();
            List<Integer> snapshot = viewers.of(shop);
            for (var result : regions.invokeAll(snapshot.stream().<Callable<Void>>map(viewer -> () ->
            {
                viewers.remove(shop, viewer);
                return null;
            }).toList())) result.get();
            assertEquals(100, snapshot.size());
        }
        assertEquals(List.of(), viewers.of(shop));
    }
}
