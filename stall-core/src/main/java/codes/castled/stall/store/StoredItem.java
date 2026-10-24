package codes.castled.stall.store;

import java.util.Objects;

/**
 * An item as Stall saves it.
 *
 * @param id the item type, such as {@code minecraft:diamond}, for people reading the file; loading never
 *           relies on it
 * @param nbt Base64 of the item exactly as Paper's {@code ItemStack#serializeAsBytes} writes it: vanilla's
 *            item codec as NBT with a {@code DataVersion}, gzip-compressed. Fabric writes and reads the same
 *            bytes with vanilla's own classes.
 */
public record StoredItem(String id, String nbt)
{
    public StoredItem
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nbt, "nbt");
    }
}
