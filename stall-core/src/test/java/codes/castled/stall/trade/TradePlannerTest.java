package codes.castled.stall.trade;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.offer.OfferStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradePlannerTest
{
    private static final int PLAYER_SLOTS = 36;
    private static final int BARREL_SLOTS = 27;
    private static final int CONTAINER_LIMIT = 99;

    private final TradePlanner<FakeItem> planner = new TradePlanner<>(FakeItem.EXACT);

    @Test
    void movesPaymentIntoTheShopAndRewardsToTheBuyer()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[10] = FakeItem.of("emerald", 1);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("cobblestone", 64);
        stock[1] = FakeItem.of("coal", 10);

        Offer<FakeItem> offer = offer(
                List.of(item("emerald", 1)),
                List.of(item("cobblestone", 16), item("coal", 4)));

        apply(success(planner.plan(offer, 1, player(buyer), barrel(stock))), buyer, stock);

        assertEquals(0, total(buyer, "emerald"));
        assertEquals(16, total(buyer, "cobblestone"));
        assertEquals(4, total(buyer, "coal"));
        assertEquals(1, total(stock, "emerald"));
        assertEquals(48, total(stock, "cobblestone"));
        assertEquals(6, total(stock, "coal"));
    }

    @Test
    void twoItemsOnEachSide()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[9] = FakeItem.of("diamond", 1);
        buyer[12] = FakeItem.of("gold_ingot", 5);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[3] = FakeItem.of("iron_ingot", 20);
        stock[4] = FakeItem.of("bread", 3);

        Offer<FakeItem> offer = offer(
                List.of(item("diamond", 1), item("gold_ingot", 2)),
                List.of(item("iron_ingot", 8), item("bread", 1)));

        apply(success(planner.plan(offer, 1, player(buyer), barrel(stock))), buyer, stock);

        assertEquals(0, total(buyer, "diamond"));
        assertEquals(3, total(buyer, "gold_ingot"));
        assertEquals(8, total(buyer, "iron_ingot"));
        assertEquals(1, total(buyer, "bread"));
        assertEquals(1, total(stock, "diamond"));
        assertEquals(2, total(stock, "gold_ingot"));
    }

    @Test
    void refusesWhenTheShopLacksAnyReward()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("emerald", 10);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("cobblestone", 64);
        stock[1] = FakeItem.of("coal", 3);

        Offer<FakeItem> offer = offer(
                List.of(item("emerald", 1)),
                List.of(item("cobblestone", 16), item("coal", 4)));

        assertFailure(TradeFailure.OUT_OF_STOCK, planner.plan(offer, 1, player(buyer), barrel(stock)));
    }

    @Test
    void refusesWhenTheBuyerLacksAnyCost()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("diamond", 1);
        buyer[1] = FakeItem.of("gold_ingot", 1);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("iron_ingot", 64);

        Offer<FakeItem> offer = offer(
                List.of(item("diamond", 1), item("gold_ingot", 2)),
                List.of(item("iron_ingot", 8)));

        assertFailure(TradeFailure.CANNOT_AFFORD, planner.plan(offer, 1, player(buyer), barrel(stock)));
    }

    @Test
    void outOfStockIsReportedBeforeCannotAfford()
    {
        Offer<FakeItem> offer = offer(List.of(item("emerald", 1)), List.of(item("cobblestone", 1)));

        assertFailure(TradeFailure.OUT_OF_STOCK,
                planner.plan(offer, 1, player(slots(PLAYER_SLOTS)), barrel(slots(BARREL_SLOTS))));
    }

    @Test
    void spaceFreedByPayingCountsForTheRewards()
    {
        FakeItem[] buyer = filled(PLAYER_SLOTS, FakeItem.of("dirt", 64));
        buyer[20] = FakeItem.of("emerald", 1);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("cobblestone", 64);
        stock[1] = FakeItem.of("coal", 64);

        Offer<FakeItem> oneStack = offer(List.of(item("emerald", 1)), List.of(item("cobblestone", 16)));
        apply(success(planner.plan(oneStack, 1, player(buyer), barrel(stock))), buyer, stock);
        assertEquals(16, buyer[20].count());
        assertEquals("cobblestone", buyer[20].id());
    }

    @Test
    void refusesWhenTheBuyerCannotHoldTheRewards()
    {
        FakeItem[] buyer = filled(PLAYER_SLOTS, FakeItem.of("dirt", 64));
        buyer[20] = FakeItem.of("emerald", 1);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("cobblestone", 64);
        stock[1] = FakeItem.of("coal", 64);

        Offer<FakeItem> twoStacks = offer(
                List.of(item("emerald", 1)),
                List.of(item("cobblestone", 16), item("coal", 4)));

        assertFailure(TradeFailure.INVENTORY_FULL, planner.plan(twoStacks, 1, player(buyer), barrel(stock)));
    }

    @Test
    void refusesWhenTheShopCannotHoldThePayment()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("diamond", 1);
        FakeItem[] stock = filled(BARREL_SLOTS, FakeItem.of("dirt", 64));
        stock[0] = FakeItem.of("cobblestone", 32);

        Offer<FakeItem> offer = offer(List.of(item("diamond", 1)), List.of(item("cobblestone", 16)));

        assertFailure(TradeFailure.SHOP_FULL, planner.plan(offer, 1, player(buyer), barrel(stock)));
    }

    @Test
    void spaceFreedBySellingCountsForThePayment()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("diamond", 1);
        FakeItem[] stock = filled(BARREL_SLOTS, FakeItem.of("dirt", 64));
        stock[5] = FakeItem.of("cobblestone", 16);

        Offer<FakeItem> offer = offer(List.of(item("diamond", 1)), List.of(item("cobblestone", 16)));

        apply(success(planner.plan(offer, 1, player(buyer), barrel(stock))), buyer, stock);
        assertEquals(FakeItem.of("diamond", 1), stock[5]);
    }

    @Test
    void theSameItemCanBeOnBothSides()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("diamond", 2);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("diamond", 1);
        stock[1] = FakeItem.of("gold_ingot", 1);

        Offer<FakeItem> offer = offer(
                List.of(item("diamond", 2)),
                List.of(item("diamond", 1), item("gold_ingot", 1)));

        apply(success(planner.plan(offer, 1, player(buyer), barrel(stock))), buyer, stock);
        assertEquals(1, total(buyer, "diamond"));
        assertEquals(1, total(buyer, "gold_ingot"));
        assertEquals(2, total(stock, "diamond"));
        assertEquals(0, total(stock, "gold_ingot"));
    }

    @Test
    void theSameItemInBothSlotsOfASideNeedsBothAmounts()
    {
        Offer<FakeItem> offer = offer(
                List.of(item("diamond", 2), item("diamond", 3)),
                List.of(item("stone", 1)));
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("stone", 64);

        FakeItem[] four = slots(PLAYER_SLOTS);
        four[0] = FakeItem.of("diamond", 4);
        assertFailure(TradeFailure.CANNOT_AFFORD, planner.plan(offer, 1, player(four), barrel(stock)));

        FakeItem[] five = slots(PLAYER_SLOTS);
        five[0] = FakeItem.of("diamond", 5);
        apply(success(planner.plan(offer, 1, player(five), barrel(stock))), five, stock);
        assertEquals(0, total(five, "diamond"));
        assertEquals(5, total(stock, "diamond"));
    }

    @Test
    void unstackableItemsTakeASlotEach()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("emerald", 3);
        FakeItem[] stock = slots(BARREL_SLOTS);
        for (int slot = 0; slot < 4; slot++)
        {
            stock[slot] = FakeItem.unstackable("iron_sword", 1);
        }

        Offer<FakeItem> offer = offer(
                List.of(item("emerald", 3)),
                List.of(new OfferItem<>(FakeItem.unstackable("iron_sword", 1), 3)));

        apply(success(planner.plan(offer, 1, player(buyer), barrel(stock))), buyer, stock);
        long swordSlots = Arrays.stream(buyer).filter(s -> s != null && s.id().equals("iron_sword")).count();
        assertEquals(3, swordSlots);
        assertTrue(Arrays.stream(buyer).allMatch(s -> s == null || s.count() <= s.maxStack()));
    }

    @Test
    void amountsAboveAStackSpreadOverSlots()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("emerald", 1);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("cobblestone", 64);
        stock[1] = FakeItem.of("cobblestone", 64);
        stock[2] = FakeItem.of("cobblestone", 10);

        Offer<FakeItem> offer = offer(List.of(item("emerald", 1)), List.of(item("cobblestone", 128)));

        apply(success(planner.plan(offer, 1, player(buyer), barrel(stock))), buyer, stock);
        assertEquals(128, total(buyer, "cobblestone"));
        assertEquals(10, total(stock, "cobblestone"));
        assertTrue(Arrays.stream(buyer).allMatch(s -> s == null || s.count() <= 64));
    }

    @Test
    void respectsTheContainerStackLimit()
    {
        FakeItem[] stock = filled(3, null);
        stock[0] = FakeItem.of("stone", 64);
        SlotInventory<FakeItem> limitOf16 = SlotInventory.of(Arrays.asList(stock), FakeItem.EXACT, 16);
        Offer<FakeItem> offer = offer(List.of(item("diamond", 48)), List.of(item("stone", 64)));

        FakeItem[] fits = slots(PLAYER_SLOTS);
        fits[0] = FakeItem.of("diamond", 48);
        apply(success(planner.plan(offer, 1, player(fits), limitOf16)), fits, stock);
        assertTrue(Arrays.stream(stock).allMatch(s -> s != null && s.count() == 16));

        FakeItem[] tooMany = slots(PLAYER_SLOTS);
        tooMany[0] = FakeItem.of("diamond", 49);
        FakeItem[] again = filled(3, null);
        again[0] = FakeItem.of("stone", 64);
        Offer<FakeItem> bigger = offer(List.of(item("diamond", 49)), List.of(item("stone", 64)));
        assertFailure(TradeFailure.SHOP_FULL, planner.plan(bigger, 1, player(tooMany),
                SlotInventory.of(Arrays.asList(again), FakeItem.EXACT, 16)));
    }

    @Test
    void matchesExactlyAndIgnoresLookAlikes()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("diamond", 5).withData("name=Fake Diamond");
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("stone", 64);

        Offer<FakeItem> offer = offer(List.of(item("diamond", 1)), List.of(item("stone", 1)));

        assertFailure(TradeFailure.CANNOT_AFFORD, planner.plan(offer, 1, player(buyer), barrel(stock)));
    }

    @Test
    void paymentComesOutOfTheMainInventoryBeforeTheHotbar()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("emerald", 5);
        buyer[30] = FakeItem.of("emerald", 2);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("stone", 64);

        Offer<FakeItem> offer = offer(List.of(item("emerald", 3)), List.of(item("stone", 1)));

        apply(success(planner.plan(offer, 1, player(buyer), barrel(stock))), buyer, stock);
        assertEquals(4, buyer[0].count());
        assertNull(buyer[30]);
    }

    @Test
    void rewardsTopUpMatchingStacksBeforeEmptySlots()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[5] = FakeItem.of("cobblestone", 60);
        buyer[6] = FakeItem.of("emerald", 1);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("cobblestone", 64);

        Offer<FakeItem> offer = offer(List.of(item("emerald", 1)), List.of(item("cobblestone", 16)));

        apply(success(planner.plan(offer, 1, player(buyer), barrel(stock))), buyer, stock);
        assertEquals(64, buyer[5].count());
        assertEquals(FakeItem.of("cobblestone", 12), buyer[0]);
    }

    @Test
    void movesTheActualStacksNotTheTemplates()
    {
        TradePlanner<FakeItem> lenient = new TradePlanner<>(FakeItem.IGNORE_WEAR);
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[9] = FakeItem.unstackable("iron_sword", 1).withWear("damage=200");
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("emerald", 64).withWear("pristine");

        Offer<FakeItem> offer = offer(
                List.of(new OfferItem<>(FakeItem.unstackable("iron_sword", 1), 1)),
                List.of(item("emerald", 1)));

        TradePlan.Success<FakeItem> plan = success(lenient.plan(offer, 1,
                SlotInventory.playerStorage(Arrays.asList(buyer), FakeItem.IGNORE_WEAR, CONTAINER_LIMIT),
                SlotInventory.of(Arrays.asList(stock), FakeItem.IGNORE_WEAR, CONTAINER_LIMIT)));
        apply(plan, buyer, stock);

        FakeItem received = Arrays.stream(stock)
                .filter(s -> s != null && s.id().equals("iron_sword")).findFirst().orElseThrow();
        assertEquals("damage=200", received.wear());
        FakeItem emerald = Arrays.stream(buyer)
                .filter(s -> s != null && s.id().equals("emerald")).findFirst().orElseThrow();
        assertEquals("pristine", emerald.wear());
    }

    @Test
    void repeatsATradeAsOne()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("emerald", 10);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("bread", 64);

        Offer<FakeItem> offer = offer(List.of(item("emerald", 2)), List.of(item("bread", 5)));

        apply(success(planner.plan(offer, 3, player(buyer), barrel(stock))), buyer, stock);
        assertEquals(4, total(buyer, "emerald"));
        assertEquals(15, total(buyer, "bread"));
    }

    @Test
    void refusesAmountsThatOverflow()
    {
        Offer<FakeItem> offer = offer(List.of(item("emerald", OfferItem.MAX_AMOUNT)), List.of(item("bread", 1)));

        assertFailure(TradeFailure.TOO_LARGE,
                planner.plan(offer, 1_000_000, player(slots(PLAYER_SLOTS)), barrel(slots(BARREL_SLOTS))));
    }

    @Test
    void refusesAnOfferThatIsNotSetUpYet()
    {
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("stone", 64);
        Offer<FakeItem> noCost = new Offer<>(UUID.randomUUID(), OfferSlots.empty(),
                OfferSlots.of(List.of(item("stone", 1))));

        assertFailure(TradeFailure.INCOMPLETE, planner.plan(noCost, 1, player(slots(PLAYER_SLOTS)), barrel(stock)));
        assertEquals(OfferStatus.INCOMPLETE, planner.status(noCost, player(slots(PLAYER_SLOTS)), barrel(stock)));
    }

    @Test
    void anItemInTheSecondSlotOnlyStillCounts()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("emerald", 1);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("bread", 3);
        Offer<FakeItem> offer = new Offer<>(UUID.randomUUID(),
                OfferSlots.<FakeItem>empty().with(1, item("emerald", 1)),
                OfferSlots.<FakeItem>empty().with(1, item("bread", 3)));

        apply(success(planner.plan(offer, 1, player(buyer), barrel(stock))), buyer, stock);
        assertEquals(3, total(buyer, "bread"));
        assertEquals(1, total(stock, "emerald"));
    }

    @Test
    void reportsStockAndStatus()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("emerald", 1);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("cobblestone", 64);
        stock[1] = FakeItem.of("cobblestone", 20);
        stock[2] = FakeItem.of("coal", 9);

        Offer<FakeItem> offer = offer(
                List.of(item("emerald", 1)),
                List.of(item("cobblestone", 16), item("coal", 4)));

        assertEquals(2, planner.timesInStock(offer, barrel(stock)));
        assertEquals(OfferStatus.AVAILABLE, planner.status(offer, player(buyer), barrel(stock)));
        assertEquals(OfferStatus.CANNOT_AFFORD, planner.status(offer, player(slots(PLAYER_SLOTS)), barrel(stock)));
        assertEquals(OfferStatus.OUT_OF_STOCK, planner.status(offer, player(buyer), barrel(slots(BARREL_SLOTS))));
    }

    @Test
    void namesWhatTheShopAndTheBuyerAreShortOf()
    {
        FakeItem[] buyer = slots(PLAYER_SLOTS);
        buyer[0] = FakeItem.of("diamond", 1);
        FakeItem[] stock = slots(BARREL_SLOTS);
        stock[0] = FakeItem.of("stick", 5);

        Offer<FakeItem> offer = offer(
                List.of(item("diamond", 2), item("diamond", 1)),
                List.of(item("stick", 1), item("grass_block", 2)));

        assertEquals(List.of(new ItemAmount<>(FakeItem.of("grass_block", 1), 2)), planner.missingStock(offer, barrel(stock)));
        assertEquals(List.of(new ItemAmount<>(FakeItem.of("diamond", 1), 2)), planner.missingPayment(offer, player(buyer)));

        stock[1] = FakeItem.of("grass_block", 2);
        buyer[1] = FakeItem.of("diamond", 2);
        assertEquals(List.of(), planner.missingStock(offer, barrel(stock)));
        assertEquals(List.of(), planner.missingPayment(offer, player(buyer)));
    }

    @Test
    void neverCreatesOrDestroysItems()
    {
        Random random = new Random(20261023L);
        String[] ids = {"emerald", "diamond", "cobblestone", "coal", "iron_sword", "ender_pearl"};
        int successes = 0;
        for (int run = 0; run < 5_000; run++)
        {
            FakeItem[] buyer = randomSlots(random, PLAYER_SLOTS, ids);
            FakeItem[] stock = randomSlots(random, BARREL_SLOTS, ids);
            Offer<FakeItem> offer = randomOffer(random, ids);
            int times = 1 + random.nextInt(3);

            Map<String, Long> before = totals(buyer, stock);
            Map<String, Long> buyerBefore = totals(buyer);
            TradePlan<FakeItem> plan = planner.plan(offer, times, player(buyer), barrel(stock));
            if (!(plan instanceof TradePlan.Success<FakeItem> success))
            {
                continue;
            }
            successes++;
            apply(success, buyer, stock);

            assertEquals(before, totals(buyer, stock), "items were created or destroyed");
            Map<String, Long> expected = new HashMap<>(buyerBefore);
            offer.costs().items().forEach(c -> expected.merge(c.item().id(), -(long) c.amount() * times, Long::sum));
            offer.rewards().items().forEach(r -> expected.merge(r.item().id(), (long) r.amount() * times, Long::sum));
            expected.values().removeIf(v -> v == 0);
            assertEquals(expected, totals(buyer), "the buyer did not get exactly the offer");
            for (FakeItem stack : concat(buyer, stock))
            {
                assertTrue(stack == null || (stack.count() > 0 && stack.count() <= stack.maxStack()),
                        "bad stack " + stack);
            }
        }
        assertTrue(successes > 500, "too few random trades succeeded to mean anything: " + successes);
    }

    private static Offer<FakeItem> offer(List<OfferItem<FakeItem>> costs, List<OfferItem<FakeItem>> rewards)
    {
        return new Offer<>(UUID.randomUUID(), OfferSlots.of(costs), OfferSlots.of(rewards));
    }

    private static OfferItem<FakeItem> item(String id, int amount)
    {
        FakeItem template = id.equals("iron_sword") ? FakeItem.unstackable(id, 1)
                : id.equals("ender_pearl") ? new FakeItem(id, "", "", 1, 16) : FakeItem.of(id, 1);
        return new OfferItem<>(template, amount);
    }

    private static FakeItem[] slots(int size)
    {
        return new FakeItem[size];
    }

    private static FakeItem[] filled(int size, FakeItem stack)
    {
        FakeItem[] slots = new FakeItem[size];
        Arrays.fill(slots, stack);
        return slots;
    }

    private static SlotInventory<FakeItem> player(FakeItem[] slots)
    {
        return SlotInventory.playerStorage(Arrays.asList(slots), FakeItem.EXACT, CONTAINER_LIMIT);
    }

    private static SlotInventory<FakeItem> barrel(FakeItem[] slots)
    {
        return SlotInventory.of(Arrays.asList(slots), FakeItem.EXACT, CONTAINER_LIMIT);
    }

    private static TradePlan.Success<FakeItem> success(TradePlan<FakeItem> plan)
    {
        return assertInstanceOf(TradePlan.Success.class, plan, () -> "expected success, got " + plan);
    }

    private static void assertFailure(TradeFailure expected, TradePlan<FakeItem> plan)
    {
        TradePlan.Failure<?> failure = assertInstanceOf(TradePlan.Failure.class, plan,
                () -> "expected " + expected + ", got " + plan);
        assertEquals(expected, failure.reason());
    }

    private static void apply(TradePlan.Success<FakeItem> plan, FakeItem[] buyer, FakeItem[] stock)
    {
        plan.buyerChanges().forEach(change -> buyer[change.slot()] = change.stack());
        plan.stockChanges().forEach(change -> stock[change.slot()] = change.stack());
    }

    private static long total(FakeItem[] slots, String id)
    {
        return Arrays.stream(slots).filter(s -> s != null && s.id().equals(id)).mapToLong(FakeItem::count).sum();
    }

    private static Map<String, Long> totals(FakeItem[]... inventories)
    {
        Map<String, Long> totals = new HashMap<>();
        for (FakeItem stack : concat(inventories))
        {
            if (stack != null)
            {
                totals.merge(stack.id(), (long) stack.count(), Long::sum);
            }
        }
        return totals;
    }

    private static List<FakeItem> concat(FakeItem[]... inventories)
    {
        List<FakeItem> all = new ArrayList<>();
        for (FakeItem[] inventory : inventories)
        {
            all.addAll(Arrays.asList(inventory));
        }
        return all;
    }

    private static FakeItem[] randomSlots(Random random, int size, String[] ids)
    {
        FakeItem[] slots = new FakeItem[size];
        double fill = random.nextDouble();
        for (int slot = 0; slot < size; slot++)
        {
            if (random.nextDouble() < fill)
            {
                OfferItem<FakeItem> template = item(ids[random.nextInt(ids.length)], 1);
                int max = template.item().maxStack();
                slots[slot] = template.item().withCount(1 + random.nextInt(max));
            }
        }
        return slots;
    }

    private static Offer<FakeItem> randomOffer(Random random, String[] ids)
    {
        return offer(randomSide(random, ids), randomSide(random, ids));
    }

    private static List<OfferItem<FakeItem>> randomSide(Random random, String[] ids)
    {
        List<OfferItem<FakeItem>> side = new ArrayList<>();
        int count = 1 + random.nextInt(OfferSlots.SIZE);
        for (int i = 0; i < count; i++)
        {
            side.add(item(ids[random.nextInt(ids.length)], 1 + random.nextInt(24)));
        }
        return side;
    }
}
