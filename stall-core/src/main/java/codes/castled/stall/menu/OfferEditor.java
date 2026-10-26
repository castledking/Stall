package codes.castled.stall.menu;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.trade.StackRules;

import java.util.Objects;
import java.util.UUID;

/**
 * What the owner's clicks on an offer's item slots do. The slots hold templates, never real items:
 * clicking with an item on the cursor puts a copy of it in the offer and the cursor keeps the item, and
 * taking an item out of the offer only clears the slot. Nothing goes into the menu and nothing comes
 * out of it, so editing an offer can neither duplicate nor lose an item.
 *
 * <table>
 *   <caption>Clicks on an item slot</caption>
 *   <tr><td>Left-click with an item</td><td>Put that stack in, or add it if the slot holds the same item</td></tr>
 *   <tr><td>Right-click with an item</td><td>Put one in, or add one if the slot holds the same item</td></tr>
 *   <tr><td>Left / right-click</td><td>One more / one fewer</td></tr>
 *   <tr><td>Shift-left / shift-right-click</td><td>A stack more / a stack fewer</td></tr>
 *   <tr><td>Drop key</td><td>Take the item out</td></tr>
 * </table>
 *
 * <p>Each slot holds at most one stack of its item: 64 of most items, 16 ender pearls, one sword. Adding to
 * a slot fills it up to a stack. Adding to a slot that is already full carries on in the first of the side's
 * other slots that is empty or holds the same item with room to spare; if there is none, nothing changes.
 * So a buyer pays at most two stacks and gets at most three.
 *
 * <p>Putting an item in the empty row below the last offer starts a new offer. An offer whose last item is
 * taken out is removed.
 *
 * @param <I> the platform's item stack type
 */
public final class OfferEditor<I>
{
    private final StackRules<I> rules;

    public OfferEditor(StackRules<I> rules)
    {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * Applies a click on item {@code index} of the cost or reward side of the offer in {@code row}.
     *
     * @param cursor what the owner holds on the cursor; {@code null} or empty for nothing
     * @return whether the shop's offers changed
     */
    public boolean click(Shop<I> shop, int row, boolean cost, int index, MenuClick click, I cursor)
    {
        I held = cursor == null || rules.isEmpty(cursor) ? null : cursor;
        int offerCount = shop.offers().size();
        if (row < 0 || row > offerCount || index < 0 || index >= OfferSlots.capacity(cost))
        {
            return false;
        }
        boolean newOffer = row == offerCount;
        if (newOffer && (shop.isFull() || held == null))
        {
            return false;
        }

        Offer<I> offer = newOffer
                ? new Offer<>(UUID.randomUUID(), OfferSlots.empty(true), OfferSlots.empty(false))
                : shop.offers().get(row);
        OfferSlots<I> side = offer.side(cost);
        OfferSlots<I> edited = edit(side, index, click, held);
        if (edited == side)
        {
            return false;
        }

        Offer<I> updated = offer.withSide(cost, edited);
        if (newOffer)
        {
            shop.addOffer(updated);
        }
        else if (updated.isEmpty())
        {
            shop.removeOffer(row);
        }
        else
        {
            shop.replaceOffer(row, updated);
        }
        return true;
    }

    /** Returns {@code side} itself when the click changes nothing. */
    private OfferSlots<I> edit(OfferSlots<I> side, int index, MenuClick click, I held)
    {
        OfferItem<I> current = side.get(index);
        if (held != null && (click == MenuClick.LEFT || click == MenuClick.RIGHT))
        {
            int amount = click == MenuClick.LEFT ? rules.count(held) : 1;
            if (current != null && rules.sameItem(current.item(), held))
            {
                return increase(side, index, amount);
            }
            I template = rules.withCount(held, 1);
            return side.with(index, new OfferItem<>(template, Math.min(amount, stackSize(template))));
        }
        if (current == null)
        {
            return side;
        }
        int stack = stackSize(current.item());
        return switch (click)
        {
            case LEFT -> increase(side, index, 1);
            case RIGHT -> decrease(side, index, 1);
            case SHIFT_LEFT -> increase(side, index, stack);
            case SHIFT_RIGHT -> decrease(side, index, stack);
            case DROP -> side.with(index, null);
            case OTHER -> side;
        };
    }

    /**
     * Adds {@code amount} to slot {@code index}, up to a stack. A slot that is already full passes the
     * increase to the first other slot of the side that is empty or holds the same item with room.
     */
    private OfferSlots<I> increase(OfferSlots<I> side, int index, int amount)
    {
        OfferItem<I> current = side.get(index);
        int stack = stackSize(current.item());
        if (current.amount() < stack)
        {
            return side.with(index, new OfferItem<>(current.item(), Math.min(current.amount() + amount, stack)));
        }
        for (int other = 0; other < side.size(); other++)
        {
            if (other == index)
            {
                continue;
            }
            OfferItem<I> slot = side.get(other);
            if (slot == null)
            {
                return side.with(other, new OfferItem<>(current.item(), Math.min(amount, stack)));
            }
            if (rules.sameItem(slot.item(), current.item()) && slot.amount() < stack)
            {
                return side.with(other, new OfferItem<>(slot.item(), Math.min(slot.amount() + amount, stack)));
            }
        }
        return side;
    }

    /** Takes {@code amount} off slot {@code index}, never below one and never above a stack. */
    private OfferSlots<I> decrease(OfferSlots<I> side, int index, int amount)
    {
        OfferItem<I> current = side.get(index);
        int target = Math.clamp((long) current.amount() - amount, 1, stackSize(current.item()));
        return target == current.amount() ? side : side.with(index, new OfferItem<>(current.item(), target));
    }

    /** The most of {@code item} one slot of an offer holds: one stack of it. */
    private int stackSize(I item)
    {
        return Math.clamp(rules.maxStackSize(item), 1, OfferItem.MAX_AMOUNT);
    }
}
