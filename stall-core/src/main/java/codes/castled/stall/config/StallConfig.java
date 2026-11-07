package codes.castled.stall.config;

import codes.castled.stall.menu.RemoveConfirmation;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.ShopContainers;
import codes.castled.stall.shop.ShopLimits;
import codes.castled.stall.shop.Tokens;
import codes.castled.stall.store.StoreLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The server's {@code config.yml} settings. A fresh {@code StallConfig} is read on startup and on
 * {@code /stall admin reload}; replacing the reference swaps every setting at once.
 */
public final class StallConfig
{
    public static final String FILE_NAME = "config.yml";

    private static final String TEMPLATE = readBundled(FILE_NAME);
    private static final List<Migration> MIGRATIONS = List.of();
    private static final StallConfig DEFAULTS = new StallConfig(ConfigFile.defaults(TEMPLATE), null);

    private final int defaultShopLimit;
    private final int maxLimitTier;
    private final int maxOffers;
    private final long offerRemoveCooldownTicks;
    private final boolean barrelsAllowed;
    private final boolean chestsAllowed;
    private final boolean trappedChestsAllowed;
    private final boolean doubleChestsAllowed;
    private final boolean largeBarrelsAllowed;
    private final Set<String> disabledWorlds;
    private final boolean protectExplosions;
    private final boolean protectHopperExtract;
    private final boolean allowHopperInsert;
    private final long buyCooldownTicks;
    private final boolean notifyOwnerOnSale;
    private final int menuRefreshTicks;
    private final int maxTokensPerCommand;
    private final boolean consumeTokenInCreative;
    private final boolean tokenGlint;

    private StallConfig(Map<String, Object> values, StoreLog log)
    {
        this.defaultShopLimit = clamped(values, "shops.default-limit", ShopLimits.DEFAULT_LIMIT, 0, 1000, log);
        this.maxLimitTier = clamped(values, "shops.max-limit-tier", ShopLimits.HIGHEST_TIER, 1, 1000, log);
        this.maxOffers = clamped(values, "shops.max-offers", Shop.MAX_OFFERS, 1, Shop.MAX_OFFERS, log);
        this.offerRemoveCooldownTicks = clamped(values, "shops.offer-remove-cooldown-ticks",
                (int) RemoveConfirmation.MIN_GAP_TICKS, 0, 200, log);
        this.barrelsAllowed = bool(values, "shops.containers.barrels", true);
        this.chestsAllowed = bool(values, "shops.containers.chests", true);
        this.trappedChestsAllowed = bool(values, "shops.containers.trapped-chests", true);
        this.doubleChestsAllowed = bool(values, "shops.containers.double-chests", true);
        this.largeBarrelsAllowed = bool(values, "shops.containers.large-barrels", true);
        this.disabledWorlds = worlds(values.get("shops.disabled-worlds"));
        this.protectExplosions = bool(values, "protection.explosions", true);
        this.protectHopperExtract = bool(values, "protection.hopper-extract", true);
        this.allowHopperInsert = bool(values, "protection.hopper-insert", true);
        this.buyCooldownTicks = clamped(values, "trades.buy-cooldown-ticks", 4, 0, 200, log);
        this.notifyOwnerOnSale = bool(values, "trades.notify-owner", false);
        this.menuRefreshTicks = clamped(values, "trades.menu-refresh-ticks", 20, 1, 1200, log);
        this.maxTokensPerCommand = clamped(values, "tokens.max-per-command", Tokens.MAX_PER_COMMAND, 1,
                Tokens.MAX_PER_COMMAND, log);
        this.consumeTokenInCreative = bool(values, "tokens.consume-in-creative", false);
        this.tokenGlint = bool(values, "tokens.glint", true);
    }

    /** The bundled defaults, with no file needed. */
    public static StallConfig defaults()
    {
        return DEFAULTS;
    }

    /** Loads {@code config.yml} from {@code directory}, creating or updating it if needed. */
    public static StallConfig load(Path directory, StoreLog log)
    {
        Map<String, Object> values = ConfigFile.load(directory.resolve(FILE_NAME), TEMPLATE, MIGRATIONS, log);
        return new StallConfig(values, log);
    }

    static String readBundled(String resource)
    {
        try (InputStream stream = StallConfig.class.getResourceAsStream("/" + resource))
        {
            if (stream == null)
            {
                throw new IllegalStateException("Missing bundled resource /" + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException failure)
        {
            throw new IllegalStateException("Could not read bundled resource /" + resource, failure);
        }
    }

    public int defaultShopLimit()
    {
        return defaultShopLimit;
    }

    public int maxLimitTier()
    {
        return maxLimitTier;
    }

    public int maxOffers()
    {
        return maxOffers;
    }

    public long offerRemoveCooldownTicks()
    {
        return offerRemoveCooldownTicks;
    }

    public boolean isContainerAllowed(String containerId)
    {
        return switch (containerId)
        {
            case ShopContainers.BARREL -> barrelsAllowed;
            case ShopContainers.CHEST -> chestsAllowed;
            case ShopContainers.TRAPPED_CHEST -> trappedChestsAllowed;
            default -> false;
        };
    }

    public boolean doubleChestsAllowed()
    {
        return doubleChestsAllowed;
    }

    public boolean largeBarrelsAllowed()
    {
        return largeBarrelsAllowed;
    }

    public boolean isWorldDisabled(String... worlds)
    {
        for (String world : worlds)
        {
            if (world != null && !world.isBlank())
            {
                for (String alias : worldAliases(world))
                {
                    if (disabledWorlds.contains(alias))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean protectExplosions()
    {
        return protectExplosions;
    }

    public boolean protectHopperExtract()
    {
        return protectHopperExtract;
    }

    public boolean allowHopperInsert()
    {
        return allowHopperInsert;
    }

    public long buyCooldownTicks()
    {
        return buyCooldownTicks;
    }

    public boolean notifyOwnerOnSale()
    {
        return notifyOwnerOnSale;
    }

    public int menuRefreshTicks()
    {
        return menuRefreshTicks;
    }

    public int maxTokensPerCommand()
    {
        return maxTokensPerCommand;
    }

    public boolean consumeTokenInCreative()
    {
        return consumeTokenInCreative;
    }

    public boolean tokenGlint()
    {
        return tokenGlint;
    }

    private static boolean bool(Map<String, Object> values, String path, boolean fallback)
    {
        return values.get(path) instanceof Boolean value ? value : fallback;
    }

    private static int clamped(Map<String, Object> values, String path, int fallback, int min, int max, StoreLog log)
    {
        if (!(values.get(path) instanceof Number number))
        {
            return fallback;
        }
        long raw = number.longValue();
        int clamped = (int) Math.clamp(raw, min, max);
        if (clamped != raw && log != null)
        {
            log.warn(FILE_NAME + ": " + path + " must be from " + min + " to " + max + "; using " + clamped + ".", null);
        }
        return clamped;
    }

    private static Set<String> worlds(Object raw)
    {
        if (!(raw instanceof List<?> list) || list.isEmpty())
        {
            return Set.of();
        }
        Set<String> worlds = new LinkedHashSet<>();
        for (Object item : list)
        {
            if (item != null && !item.toString().isBlank())
            {
                worlds.addAll(worldAliases(item.toString()));
            }
        }
        return Set.copyOf(worlds);
    }

    private static List<String> worldAliases(String world)
    {
        String lower = world.trim().toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        String path = colon >= 0 ? lower.substring(colon + 1) : lower;
        String namespaced = colon >= 0 ? lower : "minecraft:" + lower;
        return switch (path)
        {
            case "world", "overworld" -> List.of("world", "overworld", "minecraft:overworld", "minecraft:world");
            case "world_nether", "the_nether", "nether" ->
                    List.of("world_nether", "the_nether", "nether", "minecraft:the_nether", "minecraft:world_nether");
            case "world_the_end", "the_end", "end" ->
                    List.of("world_the_end", "the_end", "end", "minecraft:the_end", "minecraft:world_the_end");
            default -> List.of(lower, path, namespaced);
        };
    }
}
