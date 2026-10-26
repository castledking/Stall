package codes.castled.stall.store;

import codes.castled.stall.trade.StackRules;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A large barrel's extra stock as text, saved on the barrel itself so it is written with the chunk, in
 * step with the barrel's own contents:
 *
 * <pre>{@code
 * { "schema": 1,
 *   "slots": [ { "count": 64, "id": "minecraft:emerald", "nbt": "H4sI…" }, null, … ],
 *   "preserved": [ … ] }
 * }</pre>
 *
 * Each stack is saved in the same item format as offers, which holds a single item, with its count beside
 * it, as an offer keeps its amount. A stack this server can't read, for example from a mod that was
 * removed, is moved to {@code preserved} and kept word for word; when it becomes readable again it goes
 * back into the first empty slot.
 */
public final class StockCodec<I>
{
    public static final int SCHEMA = 1;

    private final ItemCodec<I> items;
    private final StackRules<I> rules;

    public StockCodec(ItemCodec<I> items, StackRules<I> rules)
    {
        this.items = Objects.requireNonNull(items, "items");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * What a barrel's extra stock holds.
     *
     * @param slots one entry per slot, {@code null} for an empty one
     * @param preserved stacks that couldn't be read, as saved
     */
    public record Stock<I>(List<I> slots, List<String> preserved)
    {
        public Stock
        {
            slots = Collections.unmodifiableList(new ArrayList<>(slots));
            preserved = List.copyOf(preserved);
        }

        public static <I> Stock<I> empty(int size)
        {
            return new Stock<>(Collections.nCopies(size, null), List.of());
        }

        /** Whether nothing at all is stored, preserved stacks included. */
        public boolean isEmpty()
        {
            return preserved.isEmpty() && slots.stream().allMatch(Objects::isNull);
        }
    }

    public String encode(Stock<I> stock)
    {
        JsonArray slots = new JsonArray();
        for (I item : stock.slots())
        {
            if (item == null)
            {
                slots.add(JsonNull.INSTANCE);
                continue;
            }
            StoredItem stored = items.encode(item);
            JsonObject slot = new JsonObject();
            slot.addProperty("count", rules.count(item));
            slot.addProperty("id", stored.id());
            slot.addProperty("nbt", stored.nbt());
            slots.add(slot);
        }
        JsonArray preserved = new JsonArray();
        stock.preserved().forEach(raw -> preserved.add(JsonParser.parseString(raw)));

        JsonObject json = new JsonObject();
        json.addProperty("schema", SCHEMA);
        json.add("slots", slots);
        json.add("preserved", preserved);
        return json.toString();
    }

    /**
     * Reads {@code text} into {@code size} slots. Stacks beyond {@code size}, and stacks that can't be
     * read, end up preserved rather than lost.
     *
     * @throws ShopDataException if {@code text} isn't extra stock this version can read
     */
    public Stock<I> decode(String text, int size) throws ShopDataException
    {
        List<I> slots = new ArrayList<>(Collections.nCopies(size, null));
        List<String> preserved = new ArrayList<>();
        try
        {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            int schema = json.get("schema").getAsInt();
            if (schema > SCHEMA)
            {
                throw ShopDataException.fromNewerVersion(schema);
            }
            JsonArray saved = json.getAsJsonArray("slots");
            for (int index = 0; index < saved.size(); index++)
            {
                JsonElement element = saved.get(index);
                if (element == null || element.isJsonNull())
                {
                    continue;
                }
                I item = read(element.getAsJsonObject());
                if (item == null || index >= size)
                {
                    preserved.add(element.toString());
                }
                else
                {
                    slots.set(index, item);
                }
            }
            JsonArray kept = json.getAsJsonArray("preserved");
            if (kept != null)
            {
                for (JsonElement element : kept)
                {
                    // Try again: whatever made these unreadable may have been fixed since.
                    I item = read(element.getAsJsonObject());
                    int free = slots.indexOf(null);
                    if (item == null || free < 0)
                    {
                        preserved.add(element.toString());
                    }
                    else
                    {
                        slots.set(free, item);
                    }
                }
            }
        }
        catch (ShopDataException newer)
        {
            throw newer;
        }
        catch (RuntimeException malformed)
        {
            throw new ShopDataException("not valid extra stock: " + malformed.getMessage(), malformed);
        }
        return new Stock<>(slots, preserved);
    }

    /** The stack in {@code slot}, or {@code null} if this server can't read it. */
    private I read(JsonObject slot)
    {
        try
        {
            I item = items.decode(new StoredItem(slot.get("id").getAsString(), slot.get("nbt").getAsString()));
            return rules.withCount(item, slot.get("count").getAsInt());
        }
        catch (ItemDecodeException unreadable)
        {
            return null;
        }
    }
}
