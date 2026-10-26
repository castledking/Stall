package codes.castled.stall.offer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One side of an offer: its slots in order, each holding an item or nothing. Slots keep their places,
 * so an item put in the second slot stays there while the first is empty.
 *
 * <p>The two sides hold different numbers of items: a buyer pays with up to {@link #COST_SLOTS} items and
 * gets up to {@link #REWARD_SLOTS} back.
 */
public final class OfferSlots<I>
{
    /** How many items the buyer pays with. */
    public static final int COST_SLOTS = 2;

    /** How many items the buyer gets. */
    public static final int REWARD_SLOTS = 3;

    private final List<OfferItem<I>> slots;

    private OfferSlots(List<OfferItem<I>> slots)
    {
        this.slots = Collections.unmodifiableList(slots);
    }

    /** How many items the cost side, or the reward side, holds. */
    public static int capacity(boolean cost)
    {
        return cost ? COST_SLOTS : REWARD_SLOTS;
    }

    /** An empty cost side, or an empty reward side. */
    public static <I> OfferSlots<I> empty(boolean cost)
    {
        return empty(capacity(cost));
    }

    /** A cost side holding {@code items} in its first slots. */
    public static <I> OfferSlots<I> costs(List<OfferItem<I>> items)
    {
        return of(COST_SLOTS, items);
    }

    /** A reward side holding {@code items} in its first slots. */
    public static <I> OfferSlots<I> rewards(List<OfferItem<I>> items)
    {
        return of(REWARD_SLOTS, items);
    }

    private static <I> OfferSlots<I> empty(int size)
    {
        List<OfferItem<I>> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            slots.add(null);
        }
        return new OfferSlots<>(slots);
    }

    private static <I> OfferSlots<I> of(int size, List<OfferItem<I>> items)
    {
        if (items.size() > size)
        {
            throw new IllegalArgumentException("This side holds at most " + size + " items, not " + items.size());
        }
        OfferSlots<I> side = empty(size);
        for (int i = 0; i < items.size(); i++)
        {
            side = side.with(i, Objects.requireNonNull(items.get(i), "item"));
        }
        return side;
    }

    /** How many slots this side has, filled or not. */
    public int size()
    {
        return slots.size();
    }

    /** The item in slot {@code index}, or {@code null} if the slot is empty. */
    public OfferItem<I> get(int index)
    {
        return slots.get(index);
    }

    /** A copy with slot {@code index} set to {@code item}, or emptied if {@code item} is {@code null}. */
    public OfferSlots<I> with(int index, OfferItem<I> item)
    {
        List<OfferItem<I>> updated = new ArrayList<>(slots);
        updated.set(index, item);
        return new OfferSlots<>(updated);
    }

    /** The items in the filled slots, in order. */
    public List<OfferItem<I>> items()
    {
        return slots.stream().filter(Objects::nonNull).toList();
    }

    public boolean isEmpty()
    {
        return slots.stream().allMatch(Objects::isNull);
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof OfferSlots<?> that && slots.equals(that.slots);
    }

    @Override
    public int hashCode()
    {
        return slots.hashCode();
    }

    @Override
    public String toString()
    {
        return slots.toString();
    }
}
