package codes.castled.stall.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A snapshot of an inventory's slots for the trade planner. The platform hands over copies of its
 * stacks; the planner works on further copies and never writes back, so a snapshot can be planned
 * against any number of times.
 *
 * <p>Empty slots are {@code null}. Besides the slots, a snapshot knows the inventory's own stack
 * limit and the order slots are drained and filled in.
 *
 * @param <I> the platform's item stack type
 */
public final class SlotInventory<I>
{
    private static final int HOTBAR_SIZE = 9;

    private final List<I> slots;
    private final int maxStackSize;
    private final int[] removalOrder;
    private final int[] insertionOrder;

    private SlotInventory(List<I> slots, int maxStackSize, int[] removalOrder, int[] insertionOrder)
    {
        if (maxStackSize < 1)
        {
            throw new IllegalArgumentException("maxStackSize must be at least 1, was " + maxStackSize);
        }
        this.slots = slots;
        this.maxStackSize = maxStackSize;
        this.removalOrder = removalOrder;
        this.insertionOrder = insertionOrder;
    }

    /**
     * A container such as a barrel or chest, drained and filled from its first slot to its last.
     *
     * @param stacks copies of the container's stacks; empty stacks may be {@code null} or empty
     * @param maxStackSize the container's own per-slot limit
     */
    public static <I> SlotInventory<I> of(List<I> stacks, StackRules<I> rules, int maxStackSize)
    {
        List<I> normalized = normalize(stacks, rules);
        int[] ascending = ascending(normalized.size());
        return new SlotInventory<>(normalized, maxStackSize, ascending, ascending.clone());
    }

    /**
     * A player's storage slots, the hotbar first, as both platforms list them: slots 0 to 8 are the
     * hotbar and 9 to 35 the main inventory. Payments come out of the main inventory before the hotbar,
     * so what the player is holding goes last. Purchases fill slots the way picking items up does.
     */
    public static <I> SlotInventory<I> playerStorage(List<I> stacks, StackRules<I> rules, int maxStackSize)
    {
        List<I> normalized = normalize(stacks, rules);
        int size = normalized.size();
        int hotbar = Math.min(HOTBAR_SIZE, size);
        int[] removal = new int[size];
        int next = 0;
        for (int slot = hotbar; slot < size; slot++)
        {
            removal[next++] = slot;
        }
        for (int slot = 0; slot < hotbar; slot++)
        {
            removal[next++] = slot;
        }
        return new SlotInventory<>(normalized, maxStackSize, removal, ascending(size));
    }

    public int size()
    {
        return slots.size();
    }

    /** The stack in {@code slot}, or {@code null} if it is empty. */
    public I get(int slot)
    {
        return slots.get(slot);
    }

    public int maxStackSize()
    {
        return maxStackSize;
    }

    /** How many items in this inventory exactly match {@code item}. */
    public long countMatching(I item, StackRules<I> rules)
    {
        long total = 0;
        for (I stack : slots)
        {
            if (stack != null && rules.sameItem(stack, item))
            {
                total += rules.count(stack);
            }
        }
        return total;
    }

    int[] removalOrder()
    {
        return removalOrder;
    }

    int[] insertionOrder()
    {
        return insertionOrder;
    }

    private static <I> List<I> normalize(List<I> stacks, StackRules<I> rules)
    {
        Objects.requireNonNull(stacks, "stacks");
        List<I> normalized = new ArrayList<>(stacks.size());
        for (I stack : stacks)
        {
            normalized.add(stack == null || rules.isEmpty(stack) ? null : stack);
        }
        return Collections.unmodifiableList(normalized);
    }

    private static int[] ascending(int size)
    {
        int[] order = new int[size];
        for (int slot = 0; slot < size; slot++)
        {
            order[slot] = slot;
        }
        return order;
    }
}
