package codes.castled.stall.paper;

import codes.castled.stall.store.ItemCodec;
import codes.castled.stall.store.ItemDecodeException;
import codes.castled.stall.store.StoredItem;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Saves items with Paper's own {@link ItemStack#serializeAsBytes}: vanilla's item codec as NBT with a
 * {@code DataVersion}, gzip-compressed. Reading upgrades items saved by older Minecraft versions.
 */
final class PaperItemCodec implements ItemCodec<ItemStack>
{
    @Override
    public StoredItem encode(ItemStack item)
    {
        return new StoredItem(item.getType().getKey().toString(),
                Base64.getEncoder().encodeToString(item.asOne().serializeAsBytes()));
    }

    @Override
    public ItemStack decode(StoredItem stored) throws ItemDecodeException
    {
        try
        {
            ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(stored.nbt()));
            if (item.isEmpty())
            {
                throw new ItemDecodeException("saved as an empty item");
            }
            return item;
        }
        catch (RuntimeException unreadable)
        {
            throw new ItemDecodeException("not readable on this server: " + unreadable.getMessage(), unreadable);
        }
    }
}
