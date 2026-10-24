package codes.castled.stall.paper;

import codes.castled.stall.trade.StackRules;
import org.bukkit.inventory.ItemStack;

/** Bukkit's item stacks for the trade planner. An exact match is {@link ItemStack#isSimilar}. */
final class PaperStackRules implements StackRules<ItemStack>
{
    static final PaperStackRules INSTANCE = new PaperStackRules();

    private PaperStackRules()
    {
    }

    @Override
    public boolean isEmpty(ItemStack stack)
    {
        return stack.isEmpty();
    }

    @Override
    public boolean sameItem(ItemStack a, ItemStack b)
    {
        return a.isSimilar(b);
    }

    @Override
    public int count(ItemStack stack)
    {
        return stack.getAmount();
    }

    @Override
    public int maxStackSize(ItemStack stack)
    {
        return stack.getMaxStackSize();
    }

    @Override
    public ItemStack withCount(ItemStack stack, int count)
    {
        return stack.asQuantity(count);
    }
}
