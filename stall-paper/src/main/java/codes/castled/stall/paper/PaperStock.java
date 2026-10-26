package codes.castled.stall.paper;

import codes.castled.stall.trade.SlotChange;
import codes.castled.stall.trade.SlotInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a shop's stock is, read as one row of slots: a barrel's or chest's inventory, or a large barrel's
 * own inventory followed by its extra slots. A trade is planned on a copy of every slot and written back
 * part by part.
 *
 * @param parts live inventories, in slot order
 * @param large whether the last part is a large barrel's extra slots, which must be saved after a trade
 */
record PaperStock(List<Inventory> parts, boolean large)
{
    PaperStock
    {
        parts = List.copyOf(parts);
    }

    static PaperStock of(Inventory inventory)
    {
        return new PaperStock(List.of(inventory), false);
    }

    /** A copy of every slot, for planning. Inventories hand out live mirrors of their stacks, so these are clones. */
    SlotInventory<ItemStack> slots()
    {
        List<ItemStack> copies = new ArrayList<>();
        int maxStackSize = Integer.MAX_VALUE;
        for (Inventory part : parts)
        {
            for (ItemStack stack : part.getContents())
            {
                copies.add(stack == null ? null : stack.clone());
            }
            maxStackSize = Math.min(maxStackSize, part.getMaxStackSize());
        }
        return SlotInventory.of(copies, PaperStackRules.INSTANCE, maxStackSize);
    }

    /** Writes a plan's changes, each to the part its slot falls in. */
    void commit(List<SlotChange<ItemStack>> changes)
    {
        for (SlotChange<ItemStack> change : changes)
        {
            int slot = change.slot();
            for (Inventory part : parts)
            {
                if (slot < part.getSize())
                {
                    part.setItem(slot, change.stack());
                    break;
                }
                slot -= part.getSize();
            }
        }
    }
}
