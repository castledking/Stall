package codes.castled.stall.store;

import codes.castled.stall.store.StockCodec.Stock;
import codes.castled.stall.store.TestItems.Item;
import codes.castled.stall.trade.StackRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockCodecTest
{
    /** A stack of a test item. Like the real codecs, the item codec saves one item and leaves the count out. */
    record Stack(Item item, int count)
    {
        static Stack of(String id, int count)
        {
            return new Stack(new Item(id, ""), count);
        }
    }

    private static final StackRules<Stack> RULES = new StackRules<>()
    {
        @Override public boolean isEmpty(Stack stack) { return stack == null || stack.count() <= 0; }
        @Override public boolean sameItem(Stack a, Stack b) { return a.item().equals(b.item()); }
        @Override public int count(Stack stack) { return stack.count(); }
        @Override public int maxStackSize(Stack stack) { return 64; }
        @Override public Stack withCount(Stack stack, int count) { return new Stack(stack.item(), count); }
    };

    private static ItemCodec<Stack> stacks(ItemCodec<Item> items)
    {
        return new ItemCodec<>()
        {
            @Override public StoredItem encode(Stack stack) { return items.encode(stack.item()); }
            @Override public Stack decode(StoredItem stored) throws ItemDecodeException { return new Stack(items.decode(stored), 1); }
        };
    }

    private final StockCodec<Stack> codec = new StockCodec<>(stacks(TestItems.CODEC), RULES);

    @Test
    void stockSurvivesARoundTripWithEverySlotAndCountInPlace() throws ShopDataException
    {
        List<Stack> slots = new ArrayList<>(Collections.nCopies(27, null));
        slots.set(0, Stack.of("minecraft:emerald", 64));
        slots.set(26, Stack.of("minecraft:bread", 3));

        Stock<Stack> decoded = codec.decode(codec.encode(new Stock<>(slots, List.of())), 27);

        assertEquals(slots, decoded.slots());
        assertTrue(decoded.preserved().isEmpty());
        assertFalse(decoded.isEmpty());
        assertTrue(Stock.<Stack>empty(27).isEmpty());
    }

    @Test
    void anUnreadableStackIsKeptAndComesBackWhenReadable() throws ShopDataException
    {
        List<Stack> slots = Arrays.asList(Stack.of("gone:ruby", 5), null, Stack.of("minecraft:bread", 1));
        Stock<Stack> loaded = codec.decode(codec.encode(new Stock<>(slots, List.of())), 3);

        assertNull(loaded.slots().get(0), "an unreadable stack never fills a slot");
        assertEquals(1, loaded.preserved().size());
        assertFalse(loaded.isEmpty(), "a barrel holding only unreadable stacks still isn't empty");

        // Saved again, the stack is kept word for word; once readable it goes into the first empty slot.
        Stock<Stack> restored = new StockCodec<>(stacks(TestItems.EVERYTHING), RULES).decode(codec.encode(loaded), 3);
        assertEquals(Stack.of("gone:ruby", 5), restored.slots().get(0), "the count comes back too");
        assertTrue(restored.preserved().isEmpty());
    }

    @Test
    void stacksBeyondTheSlotsArePreserved() throws ShopDataException
    {
        List<Stack> slots = Arrays.asList(Stack.of("minecraft:stone", 1), Stack.of("minecraft:dirt", 1));

        Stock<Stack> smaller = codec.decode(codec.encode(new Stock<>(slots, List.of())), 1);

        assertEquals(List.of(Stack.of("minecraft:stone", 1)), smaller.slots());
        assertEquals(1, smaller.preserved().size());
    }

    @Test
    void stockFromANewerVersionIsRecognized()
    {
        JsonObject json = JsonParser.parseString(codec.encode(Stock.empty(27))).getAsJsonObject();
        json.addProperty("schema", StockCodec.SCHEMA + 1);

        ShopDataException refused = assertThrows(ShopDataException.class, () -> codec.decode(json.toString(), 27));
        assertTrue(refused.isFromNewerVersion());
        assertThrows(ShopDataException.class, () -> codec.decode("not json", 27));
    }
}
