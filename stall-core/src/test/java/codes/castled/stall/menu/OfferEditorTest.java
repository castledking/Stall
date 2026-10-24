package codes.castled.stall.menu;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.ShopLocation;
import codes.castled.stall.trade.StackRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferEditorTest
{
    private record Item(String id, int count, int maxStack)
    {
        static Item of(String id, int count)
        {
            return new Item(id, count, 64);
        }
    }

    private static final StackRules<Item> RULES = new StackRules<>()
    {
        @Override
        public boolean isEmpty(Item stack)
        {
            return stack.count() <= 0;
        }

        @Override
        public boolean sameItem(Item a, Item b)
        {
            return a.id().equals(b.id());
        }

        @Override
        public int count(Item stack)
        {
            return stack.count();
        }

        @Override
        public int maxStackSize(Item stack)
        {
            return stack.maxStack();
        }

        @Override
        public Item withCount(Item stack, int count)
        {
            return new Item(stack.id(), count, stack.maxStack());
        }
    };

    private static final boolean COST = true;
    private static final boolean REWARD = false;

    private final OfferEditor<Item> editor = new OfferEditor<>(RULES);
    private final Shop<Item> shop = new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "Steve",
            new ShopLocation("minecraft:overworld", 0, 64, 0), true);

    @Test
    void puttingAnItemInTheEmptyRowStartsAnOffer()
    {
        assertTrue(editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 3)));

        assertEquals(1, shop.offers().size());
        Offer<Item> offer = shop.offers().getFirst();
        assertEquals(new OfferItem<>(Item.of("emerald", 1), 3), offer.costs().get(0));
        assertTrue(offer.rewards().isEmpty());
        assertFalse(offer.isComplete());
    }

    @Test
    void theNewOfferRowNeedsAnItem()
    {
        assertFalse(editor.click(shop, 0, COST, 0, MenuClick.LEFT, null));
        assertFalse(editor.click(shop, 0, COST, 0, MenuClick.DROP, null));
        assertTrue(shop.offers().isEmpty());
    }

    @Test
    void leftClickPutsTheStackInAndRightClickPutsOneIn()
    {
        editor.click(shop, 0, REWARD, 1, MenuClick.RIGHT, Item.of("bread", 16));
        assertEquals(1, reward(0, 1).amount());

        editor.click(shop, 0, REWARD, 1, MenuClick.LEFT, Item.of("bread", 16));
        assertEquals(17, reward(0, 1).amount());
        assertNull(reward(0, 0));
    }

    @Test
    void aDifferentItemReplacesTheSlot()
    {
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 5));
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("diamond", 2));

        assertEquals(new OfferItem<>(Item.of("diamond", 1), 2), shop.offers().getFirst().costs().get(0));
    }

    @Test
    void clicksWithAnEmptyCursorChangeTheAmount()
    {
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 10));

        editor.click(shop, 0, COST, 0, MenuClick.LEFT, null);
        assertEquals(11, cost(0, 0).amount());
        editor.click(shop, 0, COST, 0, MenuClick.RIGHT, null);
        assertEquals(10, cost(0, 0).amount());
        editor.click(shop, 0, COST, 0, MenuClick.SHIFT_LEFT, null);
        assertEquals(64, cost(0, 0).amount(), "a slot fills up to one stack");
        assertNull(cost(0, 1), "filling a slot doesn't spill into the next one");
        editor.click(shop, 0, COST, 0, MenuClick.SHIFT_RIGHT, null);
        assertEquals(1, cost(0, 0).amount());
        assertFalse(editor.click(shop, 0, COST, 0, MenuClick.RIGHT, null), "an amount never drops below one");
    }

    @Test
    void aFullSlotCarriesOnInTheOtherSlot()
    {
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 64));

        assertTrue(editor.click(shop, 0, COST, 0, MenuClick.LEFT, null));
        assertEquals(64, cost(0, 0).amount());
        assertEquals(new OfferItem<>(Item.of("emerald", 1), 1), cost(0, 1));

        editor.click(shop, 0, COST, 0, MenuClick.SHIFT_LEFT, null);
        assertEquals(64, cost(0, 1).amount(), "more keeps filling the other slot, up to a stack");
    }

    @Test
    void aSideHoldsAtMostTwoStacks()
    {
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 64));
        for (int i = 0; i < 10; i++)
        {
            editor.click(shop, 0, COST, 0, MenuClick.SHIFT_LEFT, null);
            editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 64));
        }
        assertEquals(64, cost(0, 0).amount());
        assertEquals(64, cost(0, 1).amount());
        assertFalse(editor.click(shop, 0, COST, 1, MenuClick.LEFT, null), "both slots are full");
    }

    @Test
    void theSecondSlotCarriesOnInTheFirst()
    {
        editor.click(shop, 0, REWARD, 1, MenuClick.LEFT, Item.of("bread", 64));
        editor.click(shop, 0, REWARD, 1, MenuClick.SHIFT_LEFT, null);

        assertEquals(64, reward(0, 0).amount());
        assertEquals(64, reward(0, 1).amount());
    }

    @Test
    void aFullSlotNeverOverwritesADifferentItem()
    {
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 64));
        editor.click(shop, 0, COST, 1, MenuClick.LEFT, Item.of("diamond", 5));

        assertFalse(editor.click(shop, 0, COST, 0, MenuClick.SHIFT_LEFT, null));
        assertFalse(editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 64)));
        assertEquals(new OfferItem<>(Item.of("diamond", 1), 5), cost(0, 1));
    }

    @Test
    void slotsHoldOneStackOfTheirOwnItem()
    {
        Item sword = new Item("iron_sword", 1, 1);
        editor.click(shop, 0, REWARD, 0, MenuClick.LEFT, sword);
        editor.click(shop, 0, REWARD, 0, MenuClick.LEFT, null);

        assertEquals(1, reward(0, 0).amount());
        assertEquals(1, reward(0, 1).amount(), "a second sword goes in the other slot");
        assertFalse(editor.click(shop, 0, REWARD, 0, MenuClick.SHIFT_LEFT, null));

        Item pearl = new Item("ender_pearl", 16, 16);
        editor.click(shop, 1, COST, 0, MenuClick.LEFT, pearl);
        editor.click(shop, 1, COST, 0, MenuClick.LEFT, null);
        assertEquals(16, cost(1, 0).amount());
        assertEquals(1, cost(1, 1).amount());
    }

    @Test
    void theDropKeyTakesAnItemOut()
    {
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 1));
        editor.click(shop, 0, REWARD, 0, MenuClick.LEFT, Item.of("bread", 1));

        assertTrue(editor.click(shop, 0, COST, 0, MenuClick.DROP, null));
        assertNull(cost(0, 0));
        assertEquals(1, shop.offers().size());
    }

    @Test
    void takingOutTheLastItemRemovesTheOffer()
    {
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 1));
        editor.click(shop, 1, COST, 0, MenuClick.LEFT, Item.of("diamond", 1));

        editor.click(shop, 0, COST, 0, MenuClick.DROP, null);

        assertEquals(1, shop.offers().size());
        assertEquals("diamond", cost(0, 0).item().id());
    }

    @Test
    void otherClicksAndEmptySlotsChangeNothing()
    {
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 1));
        int revision = shop.revision();

        assertFalse(editor.click(shop, 0, COST, 0, MenuClick.OTHER, Item.of("stone", 1)));
        assertFalse(editor.click(shop, 0, COST, 1, MenuClick.LEFT, null));
        assertFalse(editor.click(shop, 0, REWARD, 0, MenuClick.DROP, null));
        assertFalse(editor.click(shop, 5, COST, 0, MenuClick.LEFT, Item.of("stone", 1)), "rows past the empty one");
        assertEquals(revision, shop.revision());
    }

    @Test
    void aFullShopHasNoRowForANewOffer()
    {
        for (int row = 0; row < Shop.MAX_OFFERS; row++)
        {
            assertTrue(editor.click(shop, row, COST, 0, MenuClick.LEFT, Item.of("emerald", 1)));
        }
        assertFalse(editor.click(shop, Shop.MAX_OFFERS, COST, 0, MenuClick.LEFT, Item.of("emerald", 1)));
        assertEquals(Shop.MAX_OFFERS, shop.offers().size());
    }

    @Test
    void theCursorItemIsCopiedNotTaken()
    {
        Item cursor = Item.of("emerald", 7);

        editor.click(shop, 0, COST, 0, MenuClick.LEFT, cursor);

        assertEquals(7, cursor.count());
        assertEquals(1, cost(0, 0).item().count());
        assertEquals(List.of(new OfferItem<>(Item.of("emerald", 1), 7)), shop.offers().getFirst().costs().items());
    }

    @Test
    void everyEditBumpsTheRevision()
    {
        int revision = shop.revision();
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, Item.of("emerald", 1));
        editor.click(shop, 0, COST, 0, MenuClick.LEFT, null);
        editor.click(shop, 0, COST, 0, MenuClick.DROP, null);
        assertEquals(revision + 3, shop.revision());
    }

    private OfferItem<Item> cost(int row, int index)
    {
        return slot(shop.offers().get(row).costs(), index);
    }

    private OfferItem<Item> reward(int row, int index)
    {
        return slot(shop.offers().get(row).rewards(), index);
    }

    private static OfferItem<Item> slot(OfferSlots<Item> side, int index)
    {
        return side.get(index);
    }
}
