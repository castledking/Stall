package codes.castled.stall.store;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.ShopContainers;
import codes.castled.stall.shop.ShopLocation;
import codes.castled.stall.store.TestItems.Item;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopCodecTest
{
    private final ShopCodec<Item> codec = new ShopCodec<>(TestItems.CODEC);

    @Test
    void aShopSurvivesARoundTrip() throws ShopDataException
    {
        Shop<Item> shop = shop();
        Offer<Item> offer = new Offer<>(UUID.randomUUID(),
                OfferSlots.<Item>empty(true).with(1, new OfferItem<>(new Item("minecraft:emerald", "{}"), 3)),
                OfferSlots.rewards(List.of(new OfferItem<>(new Item("minecraft:bread", "{name:Fresh}"), 128),
                        new OfferItem<>(new Item("minecraft:coal", ""), 4))));
        shop.addOffer(offer);

        Shop<Item> loaded = codec.decode(codec.encode(shop)).shop();

        assertEquals(shop.id(), loaded.id());
        assertEquals(shop.owner(), loaded.owner());
        assertEquals("Steve", loaded.ownerName());
        assertEquals(shop.location(), loaded.location());
        assertTrue(loaded.tokenConsumed());
        assertEquals(shop.created(), loaded.created());
        assertEquals(List.of(offer), loaded.offers());
    }

    @Test
    void emptySlotsKeepTheirPlaces()
    {
        Shop<Item> shop = shop();
        shop.addOffer(new Offer<>(UUID.randomUUID(),
                OfferSlots.<Item>empty(true).with(1, new OfferItem<>(new Item("minecraft:emerald", ""), 1)),
                OfferSlots.<Item>empty(false)));

        JsonObject offer = codec.encode(shop).getAsJsonArray("offers").get(0).getAsJsonObject();

        assertTrue(offer.getAsJsonArray("cost").get(0).isJsonNull());
        assertEquals(1, offer.getAsJsonArray("cost").get(1).getAsJsonObject().get("amount").getAsInt());
        assertEquals(2, offer.getAsJsonArray("reward").size());
    }

    @Test
    void aBarrelWithTwoRewardsIsWrittenSoStallOneZeroCanReadIt()
    {
        Shop<Item> shop = shop();
        shop.addOffer(offer("minecraft:emerald", "minecraft:bread"));

        JsonObject json = codec.encode(shop);

        assertEquals(1, json.get("schema").getAsInt());
        assertEquals(ShopContainers.BARREL, json.get("container").getAsString());
        assertFalse(json.has("largeStock"));
        JsonObject offer = json.getAsJsonArray("offers").get(0).getAsJsonObject();
        assertEquals(2, offer.getAsJsonArray("cost").size());
        assertEquals(2, offer.getAsJsonArray("reward").size(), "1.0.0 refuses a side of three entries");
    }

    @Test
    void aThirdRewardIsWrittenAsSchemaTwo() throws ShopDataException
    {
        Shop<Item> shop = shop();
        Offer<Item> offer = new Offer<>(UUID.randomUUID(),
                OfferSlots.costs(List.of(new OfferItem<>(new Item("minecraft:diamond", ""), 1))),
                OfferSlots.<Item>empty(false).with(2, new OfferItem<>(new Item("minecraft:coal", ""), 4)));
        shop.addOffer(offer);

        JsonObject json = codec.encode(shop);

        assertEquals(2, json.get("schema").getAsInt());
        JsonArray reward = json.getAsJsonArray("offers").get(0).getAsJsonObject().getAsJsonArray("reward");
        assertEquals(3, reward.size());
        assertTrue(reward.get(0).isJsonNull(), "the item keeps its place in the third slot");
        assertEquals(List.of(offer), codec.decode(json).shop().offers());
    }

    @Test
    void chestsAndLargeBarrelsAreWrittenAsSchemaTwo() throws ShopDataException
    {
        Shop<Item> chest = shop();
        chest.setContainer("minecraft:trapped_chest");
        Shop<Item> large = shop();
        large.enableLargeStock();

        JsonObject chestJson = codec.encode(chest);
        JsonObject largeJson = codec.encode(large);

        assertEquals(2, chestJson.get("schema").getAsInt());
        assertEquals("minecraft:trapped_chest", codec.decode(chestJson).shop().container());
        assertEquals(2, largeJson.get("schema").getAsInt());
        assertTrue(codec.decode(largeJson).shop().largeStock());
        assertFalse(codec.decode(chestJson).shop().largeStock());
    }

    @Test
    void aPreservedOfferWithThreeRewardsKeepsTheFileAtSchemaTwo() throws ShopDataException
    {
        Shop<Item> shop = shop();
        shop.addOffer(new Offer<>(UUID.randomUUID(),
                OfferSlots.costs(List.of(new OfferItem<>(new Item("gone:ruby", ""), 1))),
                OfferSlots.<Item>empty(false).with(2, new OfferItem<>(new Item("minecraft:coal", ""), 4))));
        Shop<Item> loaded = codec.decode(codec.encode(shop)).shop();

        assertTrue(loaded.offers().isEmpty());
        assertEquals(2, codec.encode(loaded).get("schema").getAsInt(),
                "1.0.0 retries preserved offers, and would refuse this one");
    }

    @Test
    void anOfferWithAnUnreadableItemIsPreservedNotDropped() throws ShopDataException
    {
        Shop<Item> shop = shop();
        Offer<Item> readable = offer("minecraft:emerald", "minecraft:bread");
        Offer<Item> fromRemovedMod = offer("gone:ruby", "minecraft:bread");
        shop.addOffer(readable);
        shop.addOffer(fromRemovedMod);

        ShopCodec.Decoded<Item> decoded = codec.decode(codec.encode(shop));
        assertEquals(1, decoded.unreadableOffers());
        assertEquals(List.of(readable), decoded.shop().offers());
        assertEquals(1, decoded.shop().preservedOffers().size());

        // Saved again without ever being readable, it is still there word for word.
        JsonObject resaved = codec.encode(decoded.shop());
        assertEquals(JsonParser.parseString(decoded.shop().preservedOffers().getFirst()),
                resaved.getAsJsonArray("preservedOffers").get(0));

        // Once the items can be read again, the offer comes back.
        Shop<Item> restored = new ShopCodec<>(TestItems.EVERYTHING).decode(resaved).shop();
        assertEquals(List.of(readable, fromRemovedMod), restored.offers());
        assertTrue(restored.preservedOffers().isEmpty());
    }

    @Test
    void filesFromANewerVersionAreRecognized()
    {
        JsonObject json = codec.encode(shop());
        json.addProperty("schema", ShopCodec.SCHEMA + 1);

        ShopDataException refused = assertThrows(ShopDataException.class, () -> codec.decode(json));
        assertTrue(refused.isFromNewerVersion());
    }

    @Test
    void malformedShopsAreRefused()
    {
        Shop<Item> shop = shop();
        shop.addOffer(offer("minecraft:emerald", "minecraft:cobblestone"));
        JsonObject missingOwner = codec.encode(shop);
        missingOwner.remove("owner");
        JsonObject badAmount = codec.encode(shop);
        badAmount.getAsJsonArray("offers").get(0).getAsJsonObject().getAsJsonArray("cost").get(0)
                .getAsJsonObject().addProperty("amount", 0);

        for (JsonObject json : List.of(missingOwner, badAmount))
        {
            ShopDataException refused = assertThrows(ShopDataException.class, () -> codec.decode(json));
            assertFalse(refused.isFromNewerVersion());
        }
    }

    private static Shop<Item> shop()
    {
        return new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "Steve",
                new ShopLocation("minecraft:overworld", 120, 64, -33), true, Instant.parse("2026-10-24T09:00:00Z"));
    }

    private static Offer<Item> offer(String cost, String reward)
    {
        return new Offer<>(UUID.randomUUID(),
                OfferSlots.costs(List.of(new OfferItem<>(new Item(cost, ""), 1))),
                OfferSlots.rewards(List.of(new OfferItem<>(new Item(reward, ""), 16))));
    }
}
