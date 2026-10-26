package codes.castled.stall.fabric;

import codes.castled.stall.shop.Shop;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The marker on a shop's container: the shop's id, saved with the block as a Fabric data attachment. A
 * shop only trades while the container at its location carries its marker, so a container that replaced
 * the shop's own, or one the world rolled back, is never mistaken for the shop. A double chest carries it
 * on the half at the shop's location.
 */
final class ShopMarkers
{
    static final AttachmentType<String> SHOP = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(StallFabric.MOD_ID, "shop"),
            builder -> builder.persistent(Codec.STRING));

    /** A large barrel's extra stock, in {@link codes.castled.stall.store.StockCodec}'s text format. */
    static final AttachmentType<String> STOCK = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(StallFabric.MOD_ID, "stock"),
            builder -> builder.persistent(Codec.STRING));

    private ShopMarkers()
    {
    }

    /**
     * Registers the attachments. Must run from the mod initializer: chunks load before the server has
     * started, and a container read before an attachment's type exists would lose it.
     */
    static void register()
    {
        // Loading this class registers SHOP and STOCK.
    }

    static void stamp(BlockEntity barrel, Shop<?> shop)
    {
        barrel.setAttached(SHOP, shop.id().toString());
        barrel.setChanged();
    }

    static void clear(BlockEntity barrel)
    {
        barrel.removeAttached(SHOP);
        barrel.setChanged();
    }

    static boolean carries(BlockEntity barrel, Shop<?> shop)
    {
        return shop.id().toString().equals(barrel.getAttached(SHOP));
    }

    static String marker(BlockEntity barrel) { return barrel.getAttached(SHOP); }
}
