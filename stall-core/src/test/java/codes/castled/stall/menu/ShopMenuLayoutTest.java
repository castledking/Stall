package codes.castled.stall.menu;

import codes.castled.stall.menu.ShopMenuLayout.Role;
import codes.castled.stall.menu.ShopMenuLayout.SlotRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopMenuLayoutTest
{
    @Test
    void buyerMenuHasOneRowPerOfferUpToSix()
    {
        assertEquals(1, ShopMenuLayout.buyerRows(0));
        assertEquals(1, ShopMenuLayout.buyerRows(1));
        assertEquals(3, ShopMenuLayout.buyerRows(3));
        assertEquals(6, ShopMenuLayout.buyerRows(6));
        assertEquals(6, ShopMenuLayout.buyerRows(9));
    }

    @Test
    void ownerMenuGrowsWithTheOffersAndKeepsAnEmptyRow()
    {
        assertEquals(1, ShopMenuLayout.ownerRows(0));
        assertEquals(2, ShopMenuLayout.ownerRows(1));
        assertEquals(6, ShopMenuLayout.ownerRows(5));
        assertEquals(6, ShopMenuLayout.ownerRows(6), "a full shop has no empty row");
    }

    @Test
    void slotsRoundTripThroughRoleOf()
    {
        for (int row = 0; row < 6; row++)
        {
            for (int index = 0; index < 2; index++)
            {
                assertEquals(new SlotRole(Role.COST, row, index),
                        ShopMenuLayout.roleOf(ShopMenuLayout.costSlot(row, index), 6));
                assertEquals(new SlotRole(Role.REWARD, row, index),
                        ShopMenuLayout.roleOf(ShopMenuLayout.rewardSlot(row, index), 6));
                assertEquals(ShopMenuLayout.costSlot(row, index), ShopMenuLayout.itemSlot(row, true, index));
                assertEquals(ShopMenuLayout.rewardSlot(row, index), ShopMenuLayout.itemSlot(row, false, index));
            }
            assertEquals(new SlotRole(Role.REMOVE, row, -1), ShopMenuLayout.roleOf(ShopMenuLayout.removeSlot(row), 6));
            assertEquals(new SlotRole(Role.STATUS, row, -1), ShopMenuLayout.roleOf(ShopMenuLayout.statusSlot(row), 6));
            assertEquals(new SlotRole(Role.BUY, row, -1), ShopMenuLayout.roleOf(ShopMenuLayout.buySlot(row), 6));
        }
    }

    @Test
    void readsLikeAVillagerTrade()
    {
        assertEquals(0, ShopMenuLayout.removeSlot(0));
        assertEquals(1, ShopMenuLayout.costSlot(0, 0));
        assertEquals(2, ShopMenuLayout.costSlot(0, 1));
        assertEquals(3, ShopMenuLayout.statusSlot(0));
        assertEquals(4, ShopMenuLayout.rewardSlot(0, 0));
        assertEquals(5, ShopMenuLayout.rewardSlot(0, 1));
        assertEquals(7, ShopMenuLayout.buySlot(0));
        assertEquals(16, ShopMenuLayout.buySlot(1));
    }

    @Test
    void everythingElseIsBlank()
    {
        for (int column : new int[]{6, 8})
        {
            assertEquals(Role.NONE, ShopMenuLayout.roleOf(column, 1).role());
        }
        assertEquals(Role.NONE, ShopMenuLayout.roleOf(ShopMenuLayout.buySlot(2), 2).role());
        assertEquals(Role.NONE, ShopMenuLayout.roleOf(-999, 3).role());
    }
}
