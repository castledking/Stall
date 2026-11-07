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
import java.util.Optional;
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
    private final boolean shulkerBoxesAllowed;
    private final boolean doubleChestsAllowed;
    private final boolean largeBarrelsAllowed;
    private final boolean largeShulkersAllowed;
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
    private final String tokenItem;
    private final int tokenCustomModelData;
    private final String tokenSkullTexture;
    private final Map<String, CustomBlockSpec> customBlocks;
    private final List<String> commandAliases;
    private final String iconEmptyCostSlot;
    private final String iconEmptyRewardSlot;
    private final String iconRemoveButton;
    private final String iconStatusAvailable;
    private final String iconStatusOutOfStock;
    private final String iconStatusCannotAfford;
    private final String iconStatusInventoryFull;
    private final String iconStatusShopFull;
    private final String iconStatusIncomplete;
    private final String iconStatusUnavailable;
    private final String iconStatusNewOffer;
    private final String iconBuyAvailable;
    private final String iconBuyUnavailable;

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
        this.shulkerBoxesAllowed = bool(values, "shops.containers.shulker-boxes", true);
        this.doubleChestsAllowed = bool(values, "shops.containers.double-chests", true);
        this.largeBarrelsAllowed = bool(values, "shops.containers.large-barrels", true);
        this.largeShulkersAllowed = bool(values, "shops.containers.large-shulkers", true);
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
        this.tokenItem = string(values, "tokens.item", "paper");
        this.tokenCustomModelData = clamped(values, "tokens.custom-model-data", 0, 0, Integer.MAX_VALUE, log);
        this.tokenSkullTexture = string(values, "tokens.skull-texture", "");
        this.customBlocks = customBlocks(values.get("shops.custom-blocks"));
        this.commandAliases = aliases(values.get("commands.aliases"), values.get("commands.alias"));
        this.iconEmptyCostSlot = string(values, "menu.icons.empty-cost-slot", "light_gray_stained_glass_pane");
        this.iconEmptyRewardSlot = string(values, "menu.icons.empty-reward-slot", "light_gray_stained_glass_pane");
        this.iconRemoveButton = string(values, "menu.icons.remove-button", "barrier");
        this.iconStatusAvailable = string(values, "menu.icons.status-available", "lime_stained_glass_pane");
        this.iconStatusOutOfStock = string(values, "menu.icons.status-out-of-stock", "red_stained_glass_pane");
        this.iconStatusCannotAfford = string(values, "menu.icons.status-cannot-afford", "orange_stained_glass_pane");
        this.iconStatusInventoryFull = string(values, "menu.icons.status-inventory-full", "orange_stained_glass_pane");
        this.iconStatusShopFull = string(values, "menu.icons.status-shop-full", "gray_stained_glass_pane");
        this.iconStatusIncomplete = string(values, "menu.icons.status-incomplete", "gray_stained_glass_pane");
        this.iconStatusUnavailable = string(values, "menu.icons.status-unavailable", "gray_stained_glass_pane");
        this.iconStatusNewOffer = string(values, "menu.icons.status-new-offer", "white_stained_glass_pane");
        this.iconBuyAvailable = string(values, "menu.icons.buy-available", "lime_dye");
        this.iconBuyUnavailable = string(values, "menu.icons.buy-unavailable", "gray_dye");
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
        if (ShopContainers.isShulkerBox(containerId))
        {
            return shulkerBoxesAllowed;
        }
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

    public boolean largeShulkersAllowed()
    {
        return largeShulkersAllowed;
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

    public String tokenItem()
    {
        return tokenItem;
    }

    public int tokenCustomModelData()
    {
        return tokenCustomModelData;
    }

    public String tokenSkullTexture()
    {
        return tokenSkullTexture;
    }

    public Map<String, CustomBlockSpec> customBlocks()
    {
        return customBlocks;
    }

    public Optional<CustomBlockSpec> customBlock(String id)
    {
        if (id == null || id.isBlank() || customBlocks.isEmpty())
        {
            return Optional.empty();
        }
        String lower = id.trim().toLowerCase(Locale.ROOT);
        CustomBlockSpec direct = customBlocks.get(lower);
        if (direct != null)
        {
            return Optional.of(direct);
        }
        int colon = lower.indexOf(':');
        if (colon >= 0)
        {
            String path = lower.substring(colon + 1);
            CustomBlockSpec withoutNamespace = customBlocks.get(path);
            if (withoutNamespace != null)
            {
                return Optional.of(withoutNamespace);
            }
        }
        else
        {
            for (Map.Entry<String, CustomBlockSpec> entry : customBlocks.entrySet())
            {
                String key = entry.getKey();
                int keyColon = key.indexOf(':');
                if (keyColon >= 0 && key.substring(keyColon + 1).equals(lower))
                {
                    return Optional.of(entry.getValue());
                }
            }
            CustomBlockSpec withMinecraft = customBlocks.get("minecraft:" + lower);
            if (withMinecraft != null)
            {
                return Optional.of(withMinecraft);
            }
        }
        return Optional.empty();
    }

    public boolean isCustomBlock(String id)
    {
        return customBlock(id).isPresent();
    }

    public String iconEmptyCostSlot()
    {
        return iconEmptyCostSlot;
    }

    public String iconEmptyRewardSlot()
    {
        return iconEmptyRewardSlot;
    }

    public String iconRemoveButton()
    {
        return iconRemoveButton;
    }

    public String iconStatusAvailable()
    {
        return iconStatusAvailable;
    }

    public String iconStatusOutOfStock()
    {
        return iconStatusOutOfStock;
    }

    public String iconStatusCannotAfford()
    {
        return iconStatusCannotAfford;
    }

    public String iconStatusInventoryFull()
    {
        return iconStatusInventoryFull;
    }

    public String iconStatusShopFull()
    {
        return iconStatusShopFull;
    }

    public String iconStatusIncomplete()
    {
        return iconStatusIncomplete;
    }

    public String iconStatusUnavailable()
    {
        return iconStatusUnavailable;
    }

    public String iconStatusNewOffer()
    {
        return iconStatusNewOffer;
    }

    public String iconBuyAvailable()
    {
        return iconBuyAvailable;
    }

    public String iconBuyUnavailable()
    {
        return iconBuyUnavailable;
    }

    public List<String> commandAliases()
    {
        return commandAliases;
    }

    public Optional<String> commandAlias()
    {
        return commandAliases.isEmpty() ? Optional.empty() : Optional.of(commandAliases.getFirst());
    }

    private static List<String> aliases(Object rawAliases, Object rawAlias)
    {
        Set<String> result = new LinkedHashSet<>();
        addAliases(result, rawAliases);
        addAliases(result, rawAlias);
        return List.copyOf(result);
    }

    private static void addAliases(Set<String> result, Object raw)
    {
        if (raw instanceof List<?> list)
        {
            for (Object item : list)
            {
                cleanAlias(item).ifPresent(result::add);
            }
        }
        else if (raw != null)
        {
            cleanAlias(raw).ifPresent(result::add);
        }
    }

    private static Optional<String> cleanAlias(Object item)
    {
        if (item == null)
        {
            return Optional.empty();
        }
        String clean = item.toString().trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("/"))
        {
            clean = clean.substring(1).trim();
        }
        if (clean.isEmpty() || clean.equals("stall"))
        {
            return Optional.empty();
        }
        return Optional.of(clean);
    }

    private static Map<String, CustomBlockSpec> customBlocks(Object raw)
    {
        if (raw instanceof Map<?, ?> map)
        {
            java.util.LinkedHashMap<String, CustomBlockSpec> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                if (entry.getKey() == null) continue;
                String id = entry.getKey().toString().trim().toLowerCase(Locale.ROOT);
                if (id.isEmpty()) continue;
                Object val = entry.getValue();
                if (val instanceof Number num)
                {
                    result.put(id, new CustomBlockSpec(id, num.intValue(), null));
                }
                else if (val instanceof Map<?, ?> inner)
                {
                    boolean isSpec = inner.containsKey("size") || inner.containsKey("title");
                    if (isSpec)
                    {
                        int size = inner.get("size") instanceof Number s ? s.intValue() : 27;
                        String title = inner.get("title") != null ? inner.get("title").toString() : null;
                        result.put(id, new CustomBlockSpec(id, size, title));
                    }
                    else
                    {
                        for (Map.Entry<?, ?> subEntry : inner.entrySet())
                        {
                            if (subEntry.getKey() == null) continue;
                            String subId = id + ":" + subEntry.getKey().toString().trim().toLowerCase(Locale.ROOT);
                            Object subVal = subEntry.getValue();
                            if (subVal instanceof Number num)
                            {
                                result.put(subId, new CustomBlockSpec(subId, num.intValue(), null));
                            }
                            else if (subVal instanceof Map<?, ?> subInner)
                            {
                                int size = subInner.get("size") instanceof Number s ? s.intValue() : 27;
                                String title = subInner.get("title") != null ? subInner.get("title").toString() : null;
                                result.put(subId, new CustomBlockSpec(subId, size, title));
                            }
                            else
                            {
                                result.put(subId, new CustomBlockSpec(subId, 27, null));
                            }
                        }
                    }
                }
                else
                {
                    result.put(id, new CustomBlockSpec(id, 27, null));
                }
            }
            return Map.copyOf(result);
        }
        else if (raw instanceof List<?> list)
        {
            java.util.LinkedHashMap<String, CustomBlockSpec> result = new java.util.LinkedHashMap<>();
            for (Object item : list)
            {
                if (item instanceof Map<?, ?> m)
                {
                    Object idObj = m.get("id") != null ? m.get("id") : m.get("block");
                    if (idObj == null) continue;
                    String id = idObj.toString().trim().toLowerCase(Locale.ROOT);
                    if (id.isEmpty()) continue;
                    int size = m.get("size") instanceof Number s ? s.intValue() : 27;
                    String title = m.get("title") != null ? m.get("title").toString() : null;
                    result.put(id, new CustomBlockSpec(id, size, title));
                }
            }
            return Map.copyOf(result);
        }
        return Map.of();
    }

    private static boolean bool(Map<String, Object> values, String path, boolean fallback)
    {
        return values.get(path) instanceof Boolean value ? value : fallback;
    }

    private static String string(Map<String, Object> values, String path, String fallback)
    {
        Object value = values.get(path);
        return (value != null && !value.toString().isBlank()) ? value.toString().trim() : fallback;
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
