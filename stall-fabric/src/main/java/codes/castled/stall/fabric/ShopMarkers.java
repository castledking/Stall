package codes.castled.stall.fabric;

import codes.castled.stall.shop.Shop;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The marker on a shop's barrel: the shop's id, saved with the barrel as a Fabric data attachment. A shop
 * only trades while the barrel at its location carries its marker, so a barrel that replaced the shop's
 * own, or one the world rolled back, is never mistaken for the shop.
 */
final class ShopMarkers
{
    static final AttachmentType<String> SHOP = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(StallFabric.MOD_ID, "shop"),
            builder -> builder.persistent(Codec.STRING));

    private ShopMarkers()
    {
    }

    /**
     * Registers the marker. Must run from the mod initializer: chunks load before the server has started,
     * and a barrel read before the marker's type exists would lose its marker.
     */
    static void register()
    {
        // Loading this class registers SHOP.
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
