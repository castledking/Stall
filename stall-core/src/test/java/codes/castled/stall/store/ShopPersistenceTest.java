package codes.castled.stall.store;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.ShopLocation;
import codes.castled.stall.shop.ShopRegistry;
import codes.castled.stall.shop.TokenReturns;
import codes.castled.stall.store.TestItems.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPersistenceTest
{
    @TempDir
    Path directory;

    private final List<String> warnings = new ArrayList<>();
    private final StoreLog log = new StoreLog()
    {
        @Override
        public void info(String message)
        {
        }

        @Override
        public void warn(String message, Throwable error)
        {
            warnings.add(message);
        }
    };

    @Test
    void savedShopsAndOwedTokensComeBack()
    {
        Shop<Item> first = shop(0);
        Shop<Item> second = shop(10);
        UUID owed = UUID.randomUUID();
        TokenReturns returns = new TokenReturns();
        returns.add(owed, 2);
        try (ShopPersistence<Item> store = store())
        {
            store.saveShop(first);
            store.saveShop(second);
            store.saveReturns(returns);
        }

        ShopRegistry<Item> registry = new ShopRegistry<>();
        TokenReturns loadedReturns = new TokenReturns();
        ShopPersistence.LoadReport report = load(registry, loadedReturns);

        assertEquals(new ShopPersistence.LoadReport(2, 0, 0, 0), report);
        assertEquals(first.offers(), registry.at(first.location()).orElseThrow().offers());
        assertEquals(second.id(), registry.at(second.location()).orElseThrow().id());
        assertEquals(Map.of(owed, 2), loadedReturns.snapshot());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void theNewestSaveWins()
    {
        Shop<Item> shop = shop(0);
        try (ShopPersistence<Item> store = store())
        {
            store.saveShop(shop);
            shop.addOffer(offer("minecraft:diamond"));
            store.saveShop(shop);
        }

        ShopRegistry<Item> registry = new ShopRegistry<>();
        load(registry, new TokenReturns());
        assertEquals(2, registry.at(shop.location()).orElseThrow().offers().size());
    }

    @Test
    void deletedShopsStayDeleted()
    {
        Shop<Item> shop = shop(0);
        try (ShopPersistence<Item> store = store())
        {
            store.saveShop(shop);
            store.flush();
            store.deleteShop(shop);
        }

        ShopRegistry<Item> registry = new ShopRegistry<>();
        assertEquals(0, load(registry, new TokenReturns()).shops());
        assertFalse(Files.exists(directory.resolve("shops").resolve(shop.id() + ".json")));
    }

    @Test
    void brokenFilesAreMovedAsideAndTheRestLoads() throws IOException
    {
        Shop<Item> good = shop(0);
        try (ShopPersistence<Item> store = store())
        {
            store.saveShop(good);
        }
        Path shops = directory.resolve("shops");
        Files.writeString(shops.resolve(UUID.randomUUID() + ".json"), "{ not json");

        ShopRegistry<Item> registry = new ShopRegistry<>();
        ShopPersistence.LoadReport report = load(registry, new TokenReturns());

        assertEquals(1, report.shops());
        assertEquals(1, report.quarantined());
        try (var broken = Files.list(shops.resolve("_broken")))
        {
            assertEquals(1, broken.count());
        }
        assertEquals(1, warnings.size());
    }

    @Test
    void filesFromANewerVersionAreLeftAlone() throws IOException
    {
        Shop<Item> shop = shop(0);
        try (ShopPersistence<Item> store = store())
        {
            store.saveShop(shop);
        }
        Path file = directory.resolve("shops").resolve(shop.id() + ".json");
        Files.writeString(file, Files.readString(file).replace("\"schema\": 1", "\"schema\": 99"));

        ShopRegistry<Item> registry = new ShopRegistry<>();
        ShopPersistence.LoadReport report = load(registry, new TokenReturns());

        assertEquals(new ShopPersistence.LoadReport(0, 0, 1, 0), report);
        assertTrue(Files.exists(file));
    }

    @Test
    void aSecondShopAtTheSameSpotIsMovedAside()
    {
        Shop<Item> first = shop(0);
        Shop<Item> sameSpot = shop(0);
        try (ShopPersistence<Item> store = store())
        {
            store.saveShop(first);
            store.saveShop(sameSpot);
        }

        ShopPersistence.LoadReport report = load(new ShopRegistry<>(), new TokenReturns());
        assertEquals(1, report.shops());
        assertEquals(1, report.quarantined());
    }

    @Test
    void interruptedWritesAreIgnored() throws IOException
    {
        Shop<Item> shop = shop(0);
        try (ShopPersistence<Item> store = store())
        {
            store.saveShop(shop);
        }
        Files.writeString(directory.resolve("shops").resolve(UUID.randomUUID() + ".json.tmp"), "{ half");

        ShopPersistence.LoadReport report = load(new ShopRegistry<>(), new TokenReturns());
        assertEquals(new ShopPersistence.LoadReport(1, 0, 0, 0), report);
    }

    @Test
    void unreadableOffersAreCountedAndKept()
    {
        Shop<Item> shop = shop(0);
        shop.addOffer(offer("gone:ruby"));
        try (ShopPersistence<Item> store = store())
        {
            store.saveShop(shop);
        }

        ShopRegistry<Item> registry = new ShopRegistry<>();
        ShopPersistence.LoadReport report = load(registry, new TokenReturns());
        assertEquals(1, report.unreadableOffers());
        Shop<Item> loaded = registry.at(shop.location()).orElseThrow();
        assertEquals(1, loaded.offers().size());
        assertEquals(1, loaded.preservedOffers().size());
    }

    private ShopPersistence<Item> store()
    {
        return new ShopPersistence<>(directory, TestItems.CODEC, log);
    }

    private ShopPersistence.LoadReport load(ShopRegistry<Item> registry, TokenReturns returns)
    {
        try (ShopPersistence<Item> store = store())
        {
            return store.load(registry, returns);
        }
    }

    private static Shop<Item> shop(int x)
    {
        Shop<Item> shop = new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "Steve",
                new ShopLocation("minecraft:overworld", x, 64, 0), true);
        shop.addOffer(offer("minecraft:emerald"));
        return shop;
    }

    private static Offer<Item> offer(String cost)
    {
        return new Offer<>(UUID.randomUUID(),
                OfferSlots.of(List.of(new OfferItem<>(new Item(cost, ""), 1))),
                OfferSlots.of(List.of(new OfferItem<>(new Item("minecraft:bread", ""), 4))));
    }
}
