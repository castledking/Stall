package codes.castled.stall.menu;

import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;

/**
 * Where everything goes in a shop's menus. Each offer takes one row, read like a villager trade, from
 * what you pay to what you get:
 *
 * <pre>
 * column    0      1     2      3      4     5     6     7     8
 *        [remove] [pay] [pay] [status] [get] [get]       [buy]
 * </pre>
 *
 * The buyer menu uses the buy column and leaves the first one empty; the owner menu puts a remove
 * button in the first column and has no buy column.
 *
 * <p>The buyer menu has one row per complete offer, so up to three offers fit a barrel-sized menu and up
 * to six still fit on one page. The owner menu has a row per offer plus the empty row a new offer is
 * put in, until the shop is full.
 */
public final class ShopMenuLayout
{
    public static final int COLUMNS = 9;

    private static final int REMOVE_COLUMN = 0;
    private static final int FIRST_COST_COLUMN = 1;
    private static final int STATUS_COLUMN = 3;
    private static final int FIRST_REWARD_COLUMN = 4;
    private static final int BUY_COLUMN = 7;

    public enum Role
    {
        REMOVE,
        COST,
        STATUS,
        REWARD,
        BUY,
        NONE
    }

    /**
     * What a menu slot shows.
     *
     * @param row the row, or -1 for {@link Role#NONE}
     * @param index the item's slot within its side, for {@link Role#COST} and {@link Role#REWARD}
     */
    public record SlotRole(Role role, int row, int index)
    {
        static final SlotRole NONE = new SlotRole(Role.NONE, -1, -1);
    }

    private ShopMenuLayout()
    {
    }

    /** Rows the buyer menu needs for {@code offerCount} offers; at least one, so an empty shop still opens. */
    public static int buyerRows(int offerCount)
    {
        return Math.clamp(offerCount, 1, Shop.MAX_OFFERS);
    }

    /** Rows the owner menu needs for {@code offerCount} offers: one each, plus an empty row unless the shop is full. */
    public static int ownerRows(int offerCount)
    {
        return Math.min(offerCount + 1, Shop.MAX_OFFERS);
    }

    public static int removeSlot(int row)
    {
        return row * COLUMNS + REMOVE_COLUMN;
    }

    public static int costSlot(int row, int index)
    {
        return row * COLUMNS + FIRST_COST_COLUMN + index;
    }

    public static int statusSlot(int row)
    {
        return row * COLUMNS + STATUS_COLUMN;
    }

    public static int rewardSlot(int row, int index)
    {
        return row * COLUMNS + FIRST_REWARD_COLUMN + index;
    }

    public static int buySlot(int row)
    {
        return row * COLUMNS + BUY_COLUMN;
    }

    /** The slot for item {@code index} on the cost or the reward side of {@code row}. */
    public static int itemSlot(int row, boolean cost, int index)
    {
        return cost ? costSlot(row, index) : rewardSlot(row, index);
    }

    /** What slot {@code slot} shows in a menu of {@code rows} rows. */
    public static SlotRole roleOf(int slot, int rows)
    {
        if (slot < 0 || slot >= rows * COLUMNS)
        {
            return SlotRole.NONE;
        }
        int row = slot / COLUMNS;
        int column = slot % COLUMNS;
        if (column == REMOVE_COLUMN)
        {
            return new SlotRole(Role.REMOVE, row, -1);
        }
        if (column == STATUS_COLUMN)
        {
            return new SlotRole(Role.STATUS, row, -1);
        }
        if (column == BUY_COLUMN)
        {
            return new SlotRole(Role.BUY, row, -1);
        }
        int costIndex = column - FIRST_COST_COLUMN;
        if (costIndex >= 0 && costIndex < OfferSlots.SIZE && column < STATUS_COLUMN)
        {
            return new SlotRole(Role.COST, row, costIndex);
        }
        int rewardIndex = column - FIRST_REWARD_COLUMN;
        if (rewardIndex >= 0 && rewardIndex < OfferSlots.SIZE && column < BUY_COLUMN)
        {
            return new SlotRole(Role.REWARD, row, rewardIndex);
        }
        return SlotRole.NONE;
    }
}
