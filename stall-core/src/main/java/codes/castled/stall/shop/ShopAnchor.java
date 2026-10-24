package codes.castled.stall.shop;

import java.util.UUID;

/** Shared reconciliation policy. An unmarked replacement barrel can be reactivated; another shop's cannot. */
public final class ShopAnchor
{
    public enum Status { UNAVAILABLE, ACTIVE, PAUSED, ORPHANED }

    private ShopAnchor() { }

    public static Status inspect(boolean loaded, boolean container, String marker, UUID shop)
    {
        if (!loaded) return Status.UNAVAILABLE;
        if (!container) return Status.ORPHANED;
        if (marker == null) return Status.PAUSED;
        return marker.equals(shop.toString()) ? Status.ACTIVE : Status.ORPHANED;
    }
}
