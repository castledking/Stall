package codes.castled.stall.fabric;

import codes.castled.stall.shop.Shop;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.List;
import java.util.Optional;

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

    /** Whether a hopper may pull items out of the block at {@code pos}: not out of a shop's container. */
    public static boolean mayExtract(Level level, BlockPos pos)
    {
        FabricShops shops = active;
        if (shops == null || level.isClientSide())
        {
            return true;
        }
        // Most hoppers sit under something other than a shop container; skip the shop lookup for them.
        return !FabricContainers.isSupported(level.getBlockState(pos)) || shops.shopAt(level, pos).isEmpty();
    }

    /**
     * Keeps a chest placed against a chest shop from joining it, unless the shop's owner, or an operator,
     * places it: the new half would open the whole double chest, stock and all. The chest is placed on its
     * own instead, as when sneaking in vanilla. The owner's joined chest becomes part of the shop.
     */
    public static BlockState keepShopChestsApart(BlockPlaceContext context, BlockState placed)
    {
        FabricShops shops = active;
        Level level = context.getLevel();
        if (shops == null || level.isClientSide() || placed == null || !placed.hasProperty(ChestBlock.TYPE)
                || placed.getValue(ChestBlock.TYPE) == ChestType.SINGLE || !FabricContainers.isSupported(placed))
        {
            return placed;
        }
        BlockPos partner = context.getClickedPos().relative(ChestBlock.getConnectedDirection(placed));
        Optional<Shop<ItemStack>> shop = shops.shopAt(level, partner);
        boolean allowed = shop.isEmpty() || (context.getPlayer() instanceof ServerPlayer player
                && (shop.get().isOwner(player.getUUID()) || StallPermissions.has(player, StallPermissions.ADMIN)));
        return allowed ? placed : placed.setValue(ChestBlock.TYPE, ChestType.SINGLE);
    }

    public static List<BlockPos> filterExplosion(Level level, List<BlockPos> blocks)
    {
        FabricShops shops = active;
        // Vanilla shuffles/mutates this list after the hook; Stream.toList() would crash an explosion.
        java.util.ArrayList<BlockPos> affected = new java.util.ArrayList<>(blocks);
        if (shops != null) affected.removeIf(pos -> shops.shopAt(level, pos).isPresent());
        return affected;
    }
}
