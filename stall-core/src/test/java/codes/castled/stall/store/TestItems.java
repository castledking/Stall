package codes.castled.stall.store;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** A stand-in item and codec. Items whose id starts with {@code gone:} can't be read, like a removed mod's. */
final class TestItems
{
    record Item(String id, String data)
    {
    }

    static final ItemCodec<Item> CODEC = new ItemCodec<>()
    {
        @Override
        public StoredItem encode(Item item)
        {
            String payload = item.id() + "|" + item.data();
            return new StoredItem(item.id(), Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public Item decode(StoredItem stored) throws ItemDecodeException
        {
            String payload = new String(Base64.getDecoder().decode(stored.nbt()), StandardCharsets.UTF_8);
            int split = payload.indexOf('|');
            String id = payload.substring(0, split);
            if (id.startsWith("gone:"))
            {
                throw new ItemDecodeException("no such item: " + id);
            }
            return new Item(id, payload.substring(split + 1));
        }
    };

    /** Reads everything, including {@code gone:} items, as if the missing mod were installed again. */
    static final ItemCodec<Item> EVERYTHING = new ItemCodec<>()
    {
        @Override
        public StoredItem encode(Item item)
        {
            return CODEC.encode(item);
        }

        @Override
        public Item decode(StoredItem stored)
        {
            String payload = new String(Base64.getDecoder().decode(stored.nbt()), StandardCharsets.UTF_8);
            int split = payload.indexOf('|');
            return new Item(payload.substring(0, split), payload.substring(split + 1));
        }
    };

    private TestItems()
    {
    }
}
