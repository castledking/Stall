package codes.castled.stall.offer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One side of an offer: its slots in order, each holding an item or nothing. Slots keep their places,
 * so an item put in the second slot stays there while the first is empty.
 */
public final class OfferSlots<I>
{
    /** How many items one side of an offer holds. */
    public static final int SIZE = 2;

    private final List<OfferItem<I>> slots;

    private OfferSlots(List<OfferItem<I>> slots)
    {
        this.slots = Collections.unmodifiableList(slots);
    }

    public static <I> OfferSlots<I> empty()
    {
        List<OfferItem<I>> slots = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++)
        {
            slots.add(null);
        }
        return new OfferSlots<>(slots);
    }

    /** A side holding {@code items} in its first slots. */
    public static <I> OfferSlots<I> of(List<OfferItem<I>> items)
    {
        if (items.size() > SIZE)
        {
            throw new IllegalArgumentException("A side holds at most " + SIZE + " items, not " + items.size());
        }
        OfferSlots<I> side = empty();
        for (int i = 0; i < items.size(); i++)
        {
            side = side.with(i, Objects.requireNonNull(items.get(i), "item"));
        }
        return side;
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
