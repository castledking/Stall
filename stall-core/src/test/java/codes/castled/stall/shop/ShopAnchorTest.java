package codes.castled.stall.shop;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopAnchorTest
{
    @Test
    void unloadedChunksCannotOrphanAShop()
    {
        assertEquals(ShopAnchor.Status.UNAVAILABLE, ShopAnchor.inspect(false, false, null, UUID.randomUUID()));
    }

    @Test
    void missingContainersAndConflictingMarkersAreOrphanedButUnmarkedBarrelsPause()
    {
        UUID shop = UUID.randomUUID();
        assertEquals(ShopAnchor.Status.ORPHANED, ShopAnchor.inspect(true, false, null, shop));
        assertEquals(ShopAnchor.Status.ORPHANED, ShopAnchor.inspect(true, true, UUID.randomUUID().toString(), shop));
        assertEquals(ShopAnchor.Status.PAUSED, ShopAnchor.inspect(true, true, null, shop));
        assertEquals(ShopAnchor.Status.ACTIVE, ShopAnchor.inspect(true, true, shop.toString(), shop));
    }
}
