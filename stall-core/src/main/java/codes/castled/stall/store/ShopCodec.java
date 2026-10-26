package codes.castled.stall.store;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.ShopContainers;
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
 *   "schema": 2,
 *   "id": "…", "owner": "…", "ownerName": "Steve",
 *   "location": { "world": "minecraft:overworld", "x": 120, "y": 64, "z": -33 },
 *   "container": "minecraft:barrel",
 *   "largeStock": true,
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
 *
 * <p>A shop is written with the oldest schema that can hold it. Schema 1 is a barrel with two items on each
 * side of every offer, which Stall 1.0.0 reads. A chest, a third reward or a large barrel needs schema 2,
 * which older versions leave alone instead of misreading: they would move a three-reward shop aside as
 * broken, and treat a chest as a barrel that had gone missing.
 */
public final class ShopCodec<I>
{
    /** The newest version of the format, read and written. Files from a newer version are left alone. */
    public static final int SCHEMA = 2;

    /** The version Stall 1.0.0 reads: a barrel, and at most two items on each side of an offer. */
    static final int FIRST_SCHEMA = 1;

    private final ItemCodec<I> items;

    public ShopCodec(ItemCodec<I> items)
    {
        this.items = Objects.requireNonNull(items, "items");
    }

    public JsonObject encode(Shop<I> shop)
    {
        JsonObject json = new JsonObject();
        json.addProperty("schema", schemaFor(shop));
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

        json.addProperty("container", shop.container());
        if (shop.largeStock())
        {
            json.addProperty("largeStock", true);
        }
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

    /** The oldest schema that holds {@code shop}, so older versions of Stall can still read what they can. */
    static int schemaFor(Shop<?> shop)
    {
        boolean needsNewer = !ShopContainers.BARREL.equals(shop.container()) || shop.largeStock()
                || shop.offers().stream().anyMatch(offer -> offer.rewards().get(OfferSlots.REWARD_SLOTS - 1) != null)
                || shop.preservedOffers().stream().anyMatch(ShopCodec::hasThirdReward);
        return needsNewer ? SCHEMA : FIRST_SCHEMA;
    }

    /** Whether a preserved offer, kept as saved, lists more rewards than Stall 1.0.0 reads. */
    private static boolean hasThirdReward(String raw)
    {
        try
        {
            JsonArray reward = JsonParser.parseString(raw).getAsJsonObject().getAsJsonArray("reward");
            return reward != null && reward.size() > OfferSlots.COST_SLOTS;
        }
        catch (RuntimeException unreadable)
        {
            // Can't tell, so take the safe side: a schema older versions won't touch.
            return true;
        }
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
            JsonElement container = json.get("container");
            if (container != null && !container.isJsonNull())
            {
                shop.setContainer(container.getAsString());
            }
            JsonElement largeStock = json.get("largeStock");
            if (largeStock != null && !largeStock.isJsonNull() && largeStock.getAsBoolean())
            {
                shop.enableLargeStock();
            }

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

    /**
     * Lists a side's slots, but never a trailing empty slot past the second: Stall 1.0.0 refuses a side of
     * more than two entries, so an empty third reward slot is left out and those files stay readable.
     */
    private JsonArray encodeSide(OfferSlots<I> side)
    {
        int length = OfferSlots.COST_SLOTS;
        for (int index = length; index < side.size(); index++)
        {
            if (side.get(index) != null)
            {
                length = index + 1;
            }
        }
        JsonArray slots = new JsonArray();
        for (int index = 0; index < length; index++)
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
                decodeSide(json.getAsJsonArray("cost"), true), decodeSide(json.getAsJsonArray("reward"), false));
    }

    private OfferSlots<I> decodeSide(JsonArray json, boolean cost) throws ItemDecodeException
    {
        OfferSlots<I> side = OfferSlots.empty(cost);
        if (json.size() > side.size())
        {
            throw new IllegalArgumentException("this side holds at most " + side.size() + " items");
        }
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
