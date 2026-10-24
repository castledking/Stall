package codes.castled.stall.store;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.ShopLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A shop as JSON, the same on Paper and Fabric:
 *
 * <pre>{@code
 * {
 *   "schema": 1,
 *   "id": "…", "owner": "…", "ownerName": "Steve",
 *   "location": { "world": "minecraft:overworld", "x": 120, "y": 64, "z": -33 },
 *   "container": "minecraft:barrel",
 *   "tokenConsumed": true,
 *   "created": "2026-10-24T09:00:00Z",
 *   "offers": [ { "id": "…",
 *                 "cost":   [ { "amount": 1, "item": { "id": "minecraft:emerald", "nbt": "H4sI…" } }, null ],
 *                 "reward": [ … ] } ],
 *   "preservedOffers": [ … ]
 * }
 * }</pre>
 *
 * Each side of an offer lists its slots in order, with {@code null} for an empty one, so items keep their
 * places. An offer with an item this server can't read is kept word for word under {@code preservedOffers}.
 */
public final class ShopCodec<I>
{
    /** The version of the format written. Files from a newer version are left alone, never overwritten. */
    public static final int SCHEMA = 1;

    private static final String CONTAINER = "minecraft:barrel";

    private final ItemCodec<I> items;

    public ShopCodec(ItemCodec<I> items)
    {
        this.items = Objects.requireNonNull(items, "items");
    }

    public JsonObject encode(Shop<I> shop)
    {
        JsonObject json = new JsonObject();
        json.addProperty("schema", SCHEMA);
        json.addProperty("id", shop.id().toString());
        json.addProperty("owner", shop.owner().toString());
        json.addProperty("ownerName", shop.ownerName());

        ShopLocation location = shop.location();
        JsonObject place = new JsonObject();
        place.addProperty("world", location.world());
        place.addProperty("x", location.x());
        place.addProperty("y", location.y());
        place.addProperty("z", location.z());
        json.add("location", place);

        json.addProperty("container", CONTAINER);
        json.addProperty("tokenConsumed", shop.tokenConsumed());
        json.addProperty("created", shop.created().toString());

        JsonArray offers = new JsonArray();
        for (Offer<I> offer : shop.offers())
        {
            offers.add(encodeOffer(offer));
        }
        json.add("offers", offers);

        JsonArray preserved = new JsonArray();
        for (String raw : shop.preservedOffers())
        {
            preserved.add(JsonParser.parseString(raw));
        }
        json.add("preservedOffers", preserved);
        return json;
    }

    /** A loaded shop, and how many of its offers had to be preserved unread. */
    public record Decoded<I>(Shop<I> shop, int unreadableOffers)
    {
    }

    /**
     * @throws ShopDataException if the JSON isn't a shop this version can load
     */
    public Decoded<I> decode(JsonObject json) throws ShopDataException
    {
        try
        {
            int schema = json.get("schema").getAsInt();
            if (schema > SCHEMA)
            {
                throw ShopDataException.fromNewerVersion(schema);
            }
            JsonObject place = json.getAsJsonObject("location");
            ShopLocation location = new ShopLocation(place.get("world").getAsString(),
                    place.get("x").getAsInt(), place.get("y").getAsInt(), place.get("z").getAsInt());
            Shop<I> shop = new Shop<>(
                    UUID.fromString(json.get("id").getAsString()),
                    UUID.fromString(json.get("owner").getAsString()),
                    json.get("ownerName").getAsString(),
                    location,
                    json.get("tokenConsumed").getAsBoolean(),
                    Instant.parse(json.get("created").getAsString()));

            List<Offer<I>> offers = new ArrayList<>();
            List<String> preserved = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("offers"))
            {
                JsonObject offerJson = element.getAsJsonObject();
                try
                {
                    offers.add(decodeOffer(offerJson));
                }
                catch (ItemDecodeException unreadable)
                {
                    preserved.add(offerJson.toString());
                }
            }
            int unreadable = preserved.size();
            JsonArray kept = json.getAsJsonArray("preservedOffers");
            if (kept != null)
            {
                for (JsonElement element : kept)
                {
                    // Try again: whatever made these unreadable may have been fixed since.
                    try
                    {
                        offers.add(decodeOffer(element.getAsJsonObject()));
                    }
                    catch (ItemDecodeException stillUnreadable)
                    {
                        preserved.add(element.toString());
                        unreadable++;
                    }
                }
            }
            shop.restore(offers, preserved);
            return new Decoded<>(shop, unreadable);
        }
        catch (RuntimeException malformed)
        {
            throw new ShopDataException("not a valid shop: " + malformed.getMessage(), malformed);
        }
    }

    private JsonObject encodeOffer(Offer<I> offer)
    {
        JsonObject json = new JsonObject();
        json.addProperty("id", offer.id().toString());
        json.add("cost", encodeSide(offer.costs()));
        json.add("reward", encodeSide(offer.rewards()));
        return json;
    }

    private JsonArray encodeSide(OfferSlots<I> side)
    {
        JsonArray slots = new JsonArray();
        for (int index = 0; index < OfferSlots.SIZE; index++)
        {
            OfferItem<I> item = side.get(index);
            if (item == null)
            {
                slots.add(JsonNull.INSTANCE);
                continue;
            }
            StoredItem stored = items.encode(item.item());
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("id", stored.id());
            itemJson.addProperty("nbt", stored.nbt());
            JsonObject slot = new JsonObject();
            slot.addProperty("amount", item.amount());
            slot.add("item", itemJson);
            slots.add(slot);
        }
        return slots;
    }

    private Offer<I> decodeOffer(JsonObject json) throws ItemDecodeException
    {
        return new Offer<>(UUID.fromString(json.get("id").getAsString()),
                decodeSide(json.getAsJsonArray("cost")), decodeSide(json.getAsJsonArray("reward")));
    }

    private OfferSlots<I> decodeSide(JsonArray json) throws ItemDecodeException
    {
        if (json.size() > OfferSlots.SIZE)
        {
            throw new IllegalArgumentException("a side holds at most " + OfferSlots.SIZE + " items");
        }
        OfferSlots<I> side = OfferSlots.empty();
        for (int index = 0; index < json.size(); index++)
        {
            JsonElement element = json.get(index);
            if (element == null || element.isJsonNull())
            {
                continue;
            }
            JsonObject slot = element.getAsJsonObject();
            JsonObject itemJson = slot.getAsJsonObject("item");
            I item = items.decode(new StoredItem(itemJson.get("id").getAsString(), itemJson.get("nbt").getAsString()));
            side = side.with(index, new OfferItem<>(item, slot.get("amount").getAsInt()));
        }
        return side;
    }
}
