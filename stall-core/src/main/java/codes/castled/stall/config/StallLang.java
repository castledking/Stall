package codes.castled.stall.config;

import codes.castled.stall.offer.OfferStatus;
import codes.castled.stall.shop.ShopContainers;
import codes.castled.stall.shop.ShopLimits;
import codes.castled.stall.store.StoreLog;
import codes.castled.stall.trade.TradeFailure;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every message, menu item name, lore line and title Stall shows, read from {@code lang.yml}. A fresh
 * {@code StallLang} is built on startup and on {@code /stall admin reload}.
 *
 * <p>Strings already have their {@code &} color codes turned into {@code §} codes. {@link #chat(String,
 * String...)} puts {@code prefix} in front of a chat message, or returns {@code ""} when the message was
 * set to {@code ""} to silence it; {@link #text(String, String...)} leaves {@code prefix} off, for menu
 * titles, item names and lore.
 */
public final class StallLang
{
    public static final String FILE_NAME = "lang.yml";

    private static final String TEMPLATE = StallConfig.readBundled(FILE_NAME);
    private static final List<Migration> MIGRATIONS = List.of();
    private static final StallLang DEFAULTS = new StallLang(ConfigFile.defaults(TEMPLATE));

    private final Map<String, String> strings;
    private final Map<String, List<String>> lists;
    private final String prefix;

    private StallLang(Map<String, Object> values)
    {
        Map<String, String> strings = new LinkedHashMap<>();
        Map<String, List<String>> lists = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet())
        {
            String key = entry.getKey();
            Object raw = entry.getValue();
            if (raw instanceof List<?> list)
            {
                List<String> translated = new ArrayList<>(list.size());
                for (Object item : list)
                {
                    if (item != null)
                    {
                        translated.add(LegacyText.translate(item.toString()));
                    }
                }
                lists.put(key, Collections.unmodifiableList(translated));
                strings.put(key, String.join("\n", translated));
            }
            else
            {
                String text = raw == null ? "" : LegacyText.translate(raw.toString());
                strings.put(key, text);
                lists.put(key, text.isEmpty() ? List.of() : List.of(text.split("\n", -1)));
            }
        }
        this.strings = Collections.unmodifiableMap(strings);
        this.lists = Collections.unmodifiableMap(lists);
        this.prefix = strings.getOrDefault("prefix", "");
    }

    /** The bundled defaults, with no file needed. */
    public static StallLang defaults()
    {
        return DEFAULTS;
    }

    /** Loads {@code lang.yml} from {@code directory}, creating or updating it if needed. */
    public static StallLang load(Path directory, StoreLog log)
    {
        return new StallLang(ConfigFile.load(directory.resolve(FILE_NAME), TEMPLATE, MIGRATIONS, log));
    }

    /**
     * A raw message or menu text for {@code key}, with {@code {name}} placeholders replaced by the pairs in
     * {@code placeholders}, without {@code prefix}.
     */
    public String text(String key, String... placeholders)
    {
        return replace(strings.getOrDefault(key, key), placeholders);
    }

    /**
     * A chat message for {@code key}, with {@code prefix} in front. Empty when the message itself is empty,
     * so setting a message to {@code ""} in {@code lang.yml} silences it even when {@code prefix} is set.
     */
    public String chat(String key, String... placeholders)
    {
        String body = text(key, placeholders);
        return body.isEmpty() ? "" : prefix + body;
    }

    /** A list of lines for {@code key}, with {@code {name}} placeholders replaced in each line. */
    public List<String> lines(String key, String... placeholders)
    {
        List<String> raw = lists.getOrDefault(key, List.of());
        if (placeholders.length == 0 || raw.isEmpty())
        {
            return raw;
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw)
        {
            out.add(replace(line, placeholders));
        }
        return out;
    }

    /** What players call a container block: "barrel", "chest" or "trapped chest". */
    public String container(String id)
    {
        return switch (id)
        {
            case ShopContainers.CHEST -> text("containers.chest");
            case ShopContainers.TRAPPED_CHEST -> text("containers.trapped-chest");
            default -> text("containers.barrel");
        };
    }

    /** A container's name with {@code large } in front when {@code large} is true. */
    public String container(String id, boolean large)
    {
        return (large ? text("containers.large-prefix") : "") + container(id);
    }

    /** A player's shop limit as shown in chat. */
    public String limit(int limit)
    {
        return limit == ShopLimits.UNLIMITED ? text("limits.unlimited") : String.valueOf(limit);
    }

    /** The name of an offer's status in the shop menu. */
    public String status(OfferStatus status)
    {
        return switch (status)
        {
            case AVAILABLE -> text("menu.status.available");
            case OUT_OF_STOCK -> text("menu.status.out-of-stock");
            case CANNOT_AFFORD -> text("menu.status.cannot-afford");
            case INVENTORY_FULL -> text("menu.status.inventory-full");
            case SHOP_FULL -> text("menu.status.shop-full");
            case INCOMPLETE -> text("menu.status.incomplete");
            case UNAVAILABLE -> text("menu.status.unavailable");
        };
    }

    /** The chat message explaining why a trade could not happen. */
    public String tradeFailure(TradeFailure failure)
    {
        return switch (failure)
        {
            case OUT_OF_STOCK -> chat("trades.out-of-stock");
            case CANNOT_AFFORD -> chat("trades.cannot-afford");
            case INVENTORY_FULL -> chat("trades.inventory-full");
            case SHOP_FULL -> chat("trades.shop-full");
            case INCOMPLETE -> chat("trades.incomplete");
            case TOO_LARGE -> chat("trades.too-large");
        };
    }

    private static String replace(String template, String... placeholders)
    {
        if (placeholders.length == 0 || template.indexOf('{') < 0)
        {
            return template;
        }
        String result = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2)
        {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return result;
    }
}
