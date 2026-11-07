package codes.castled.stall.shop;

import java.util.function.Predicate;

/**
 * How many shops a player may own, from their permissions: any number with {@code stall.limit.unlimited},
 * otherwise the highest {@code stall.limit.<n>} they have, otherwise {@link #DEFAULT_LIMIT}.
 *
 * <p>Permissions are named here without their {@code stall.} prefix, because each platform spells the full
 * node its own way: {@code stall.limit.5} on Paper, {@code stall:limit.5} through Fabric API's permission
 * API, which LuckPerms reads as {@code stall.limit.5} again.
 */
public final class ShopLimits
{
    /** Shops a player may own without any {@code stall.limit} permission. */
    public static final int DEFAULT_LIMIT = 3;

    /** The highest numbered tier checked. Higher limits need {@code stall.limit.unlimited}. */
    public static final int HIGHEST_TIER = 100;

    public static final int UNLIMITED = Integer.MAX_VALUE;

    public static final String UNLIMITED_PERMISSION = "limit.unlimited";

    private ShopLimits()
    {
    }

    public static String tierPermission(int limit)
    {
        return "limit." + limit;
    }

    public static String offersPermission(int offers)
    {
        return "offers." + offers;
    }

    /**
     * Resolves the maximum offers per shop for a player from permissions (e.g. {@code stall.offers.6}),
     * falling back to {@code defaultOffers}.
     */
    public static int offersFor(Predicate<String> hasPermission, int defaultOffers)
    {
        for (int offers = Shop.MAX_OFFERS; offers >= 1; offers--)
        {
            if (hasPermission.test(offersPermission(offers)))
            {
                return offers;
            }
        }
        return Math.clamp(defaultOffers, 1, Shop.MAX_OFFERS);
    }

    /**
     * @param hasPermission whether the player has a permission, named without the {@code stall.} prefix
     */
    public static int limitFor(Predicate<String> hasPermission)
    {
        return limitFor(hasPermission, DEFAULT_LIMIT, HIGHEST_TIER);
    }

    /**
     * @param hasPermission whether the player has a permission, named without the {@code stall.} prefix
     * @param defaultLimit shops a player may own without any {@code stall.limit.<n>} permission
     * @param highestTier the highest numbered {@code stall.limit.<n>} tier checked
     */
    public static int limitFor(Predicate<String> hasPermission, int defaultLimit, int highestTier)
    {
        if (hasPermission.test(UNLIMITED_PERMISSION))
        {
            return UNLIMITED;
        }
        for (int limit = highestTier; limit >= 0; limit--)
        {
            if (hasPermission.test(tierPermission(limit)))
            {
                return limit;
            }
        }
        return defaultLimit;
    }
}
