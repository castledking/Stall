package codes.castled.stall.paper;

import codes.castled.stall.shop.Shop;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

/**
 * The marker on a shop's container: the shop's id, saved with the block in its persistent data. A shop
 * only trades while the container at its location carries its marker, so a container that replaced the
 * shop's own, or one the world rolled back, is never mistaken for the shop. A double chest carries it on
 * the half at the shop's location.
 */
final class PaperMarkers
{
    private static final NamespacedKey SHOP = Objects.requireNonNull(NamespacedKey.fromString("stall:shop"));

    private PaperMarkers()
    {
    }

    /** Stamps the marker. Changes a snapshot of the tile and writes it back, the documented way. */
    static void stamp(Block block, Shop<?> shop)
    {
        if (block.getState() instanceof TileState tile)
        {
            tile.getPersistentDataContainer().set(SHOP, PersistentDataType.STRING, shop.id().toString());
            tile.update();
        }
    }

    static void clear(Block block)
    {
        if (block.getState() instanceof TileState tile)
        {
            tile.getPersistentDataContainer().remove(SHOP);
            tile.update();
        }
    }

    static boolean carries(Block block, Shop<?> shop)
    {
        String marked = marker(block);
        if (marked != null)
        {
            return shop.id().toString().equals(marked);
        }
        if (block.getState() instanceof TileState)
        {
            return false;
        }
        return shop.container() != null && (shop.container().startsWith("custom:") || !shop.container().startsWith("minecraft:"));
    }

    static String marker(Block block)
    {
        return block.getState() instanceof TileState tile
                ? tile.getPersistentDataContainer().get(SHOP, PersistentDataType.STRING) : null;
    }
}
