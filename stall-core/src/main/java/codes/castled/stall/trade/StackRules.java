package codes.castled.stall.trade;

/**
 * What the trade planner needs to know about a platform's item stacks. Fabric implements it for
 * Minecraft's {@code ItemStack}, Paper for Bukkit's, and the tests for a plain record, so the planner
 * itself never touches a platform class.
 *
 * @param <I> the platform's item stack type
 */
public interface StackRules<I>
{
    boolean isEmpty(I stack);

    /**
     * Whether two stacks hold the same item with identical data, ignoring their counts. This is the
     * exact match an offer requires: enchantments, damage, names and every other data component count.
     */
    boolean sameItem(I a, I b);

    int count(I stack);

    /** The most of this item one slot can hold, before any container limit applies. */
    int maxStackSize(I stack);

    /** A new stack of the same item and data with {@code count} items. Never returns {@code stack} itself. */
    I withCount(I stack, int count);
}
