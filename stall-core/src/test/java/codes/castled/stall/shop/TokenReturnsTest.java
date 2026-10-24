package codes.castled.stall.shop;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenReturnsTest
{
    @Test
    void concurrentCleanupRefundsAShopOnlyOnce() throws Exception
    {
        Shop<String> shop = new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "Owner",
                new ShopLocation("minecraft:overworld", 0, 64, 0), true);
        TokenReturns returns = new TokenReturns();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(4))
        {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 100; i++) futures.add(workers.submit(() -> returns.refund(shop)));
            for (var future : futures) future.get();
        }
        assertEquals(1, returns.claim(shop.owner()));
        returns.refund(shop);
        assertEquals(0, returns.claim(shop.owner()), "claiming a refund must not allow its shop to be refunded again");
    }

    @Test
    void creativeShopsDoNotMintRefunds()
    {
        TokenReturns returns = new TokenReturns();
        Shop<String> shop = new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "Owner",
                new ShopLocation("minecraft:overworld", 0, 64, 0), false);
        returns.refund(shop);
        assertEquals(0, returns.owed(shop.owner()));
    }
    @Test
    void owedTokensAddUpAndAreClaimedOnce()
    {
        TokenReturns returns = new TokenReturns();
        UUID player = UUID.randomUUID();

        returns.add(player, 1);
        returns.add(player, 2);
        assertEquals(3, returns.owed(player));

        assertEquals(3, returns.claim(player));
        assertEquals(0, returns.claim(player));
        assertEquals(0, returns.owed(player));
    }

    @Test
    void playersAreKeptApart()
    {
        TokenReturns returns = new TokenReturns();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        returns.add(first, 1);
        assertEquals(0, returns.claim(second));
        assertEquals(1, returns.claim(first));
    }
}
