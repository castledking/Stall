package codes.castled.stall.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

/**
 * What Stall's mixins ask about the world. Public only so the mixins can reach it; not an API. Until the
 * mod initializer installs the server's shops, every answer is vanilla's: there are no shops yet.
 */
public final class ShopProtection
{
    private static volatile FabricShops active;

    private ShopProtection()
    {
    }

    static void install(FabricShops shops)
    {
        active = shops;
    }

    /** Whether a hopper may pull items out of the block at {@code pos}: not out of a shop's barrel. */
    public static boolean mayExtract(Level level, BlockPos pos)
    {
        FabricShops shops = active;
        if (shops == null || level.isClientSide())
        {
            return true;
        }
        // Most hoppers sit under something other than a barrel; skip the shop lookup for them.
        return !(level.getBlockEntity(pos) instanceof BarrelBlockEntity) || shops.shopAt(level, pos).isEmpty();
    }
}
