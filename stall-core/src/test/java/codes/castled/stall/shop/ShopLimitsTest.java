package codes.castled.stall.shop;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopLimitsTest
{
    @Test
    void withoutATierPlayersGetTheDefault()
    {
        assertEquals(ShopLimits.DEFAULT_LIMIT, ShopLimits.limitFor(permission -> false));
    }

    @Test
    void theHighestTierWins()
    {
        Set<String> granted = Set.of("limit.2", "limit.10", "limit.5");
        assertEquals(10, ShopLimits.limitFor(granted::contains));
    }

    @Test
    void unlimitedBeatsEveryTier()
    {
        Set<String> granted = Set.of("limit.2", "limit.unlimited");
        assertEquals(ShopLimits.UNLIMITED, ShopLimits.limitFor(granted::contains));
    }

    @Test
    void tierZeroAllowsNoShops()
    {
        assertEquals(0, ShopLimits.limitFor(Set.of("limit.0")::contains));
    }

    @Test
    void tiersAboveTheHighestAreIgnored()
    {
        Set<String> granted = Set.of("limit." + (ShopLimits.HIGHEST_TIER + 1));
        assertEquals(ShopLimits.DEFAULT_LIMIT, ShopLimits.limitFor(granted::contains));
    }
}
