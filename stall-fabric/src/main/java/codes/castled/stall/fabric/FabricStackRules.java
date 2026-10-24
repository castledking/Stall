package codes.castled.stall.fabric;

import codes.castled.stall.trade.StackRules;
import net.minecraft.world.item.ItemStack;

/** Minecraft's item stacks for the trade planner. An exact match is the same item with the same components. */
final class FabricStackRules implements StackRules<ItemStack>
{
    static final FabricStackRules INSTANCE = new FabricStackRules();

    private FabricStackRules()
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
        return ItemStack.isSameItemSameComponents(a, b);
    }

    @Override
    public int count(ItemStack stack)
    {
        return stack.getCount();
    }

    @Override
    public int maxStackSize(ItemStack stack)
    {
        return stack.getMaxStackSize();
    }

    @Override
    public ItemStack withCount(ItemStack stack, int count)
    {
        return stack.copyWithCount(count);
    }
}
