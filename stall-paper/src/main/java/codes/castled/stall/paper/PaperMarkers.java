package codes.castled.stall.paper;

import codes.castled.stall.shop.Shop;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

/**
 * The marker on a shop's barrel: the shop's id, saved with the barrel in its persistent data. A shop only
 * trades while the barrel at its location carries its marker, so a barrel that replaced the shop's own,
 * or one the world rolled back, is never mistaken for the shop.
 */
final class PaperMarkers
{
    private static final NamespacedKey SHOP = Objects.requireNonNull(NamespacedKey.fromString("stall:shop"));

    private PaperMarkers()
    {
    }

    /** Stamps the marker. Changes a snapshot of the barrel and writes it back, the documented way. */
    static void stamp(Block block, Shop<?> shop)
    {
        if (block.getState() instanceof Barrel barrel)
        {
            barrel.getPersistentDataContainer().set(SHOP, PersistentDataType.STRING, shop.id().toString());
            barrel.update();
        }
    }

    static void clear(Block block)
    {
        if (block.getState() instanceof Barrel barrel)
        {
            barrel.getPersistentDataContainer().remove(SHOP);
            barrel.update();
        }
    }

    static boolean carries(Block block, Shop<?> shop)
    {
        return block.getState(false) instanceof Barrel barrel
                && shop.id().toString().equals(barrel.getPersistentDataContainer().get(SHOP, PersistentDataType.STRING));
    }
}
