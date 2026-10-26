package codes.castled.stall.fabric;

import codes.castled.stall.shop.ShopContainers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.Optional;

/**
 * The blocks a shop can be made from, and how to reach their stock: barrels, chests and trapped chests,
 * single or double. Copper chests are left out, because copper golems take items out of them; they are
 * chests to vanilla, so each block is named rather than matched by class.
 */
final class FabricContainers
{
    private FabricContainers()
    {
    }

    static boolean isSupported(BlockState state)
    {
        return state.is(Blocks.BARREL) || state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST);
    }

    static boolean isBarrel(BlockState state)
    {
        return state.is(Blocks.BARREL);
    }

    /** The block's id as saved with a shop, such as {@code minecraft:chest}. */
    static String id(BlockState state)
    {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    static String describe(BlockState state)
    {
        return ShopContainers.describe(id(state));
    }

    /** The other half of a double chest, or empty for anything else, found the way vanilla finds it. */
    static Optional<BlockPos> partner(Level level, BlockPos pos, BlockState state)
    {
        Optional<BlockPos> connected = connected(pos, state);
        if (connected.isEmpty()) return Optional.empty();
        BlockPos other = connected.get();
        if (!level.isLoaded(other)) return Optional.empty();
        BlockState half = level.getBlockState(other);
        boolean pointsBack = half.is(state.getBlock()) && half.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && other.relative(ChestBlock.getConnectedDirection(half)).equals(pos);
        return pointsBack ? Optional.of(other) : Optional.empty();
    }

    /** The expected other half, without reading or loading its chunk. Useful for protection lookup. */
    static Optional<BlockPos> connected(BlockPos pos, BlockState state)
    {
        if (!(state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE)
        {
            return Optional.empty();
        }
        return Optional.of(pos.relative(ChestBlock.getConnectedDirection(state)));
    }

    /**
     * The live container holding the stock at {@code pos}: a barrel, or a chest with a double chest's other
     * half joined on, whatever sits on top of it.
     */
    static Optional<Container> container(Level level, BlockPos pos)
    {
        if (!level.isLoaded(pos)) return Optional.empty();
        BlockState state = level.getBlockState(pos);
        if (!isSupported(state))
        {
            return Optional.empty();
        }
        if (state.getBlock() instanceof ChestBlock chest)
        {
            if (connected(pos, state).isPresent() && partner(level, pos, state).isEmpty()) return Optional.empty();
            return Optional.ofNullable(ChestBlock.getContainer(chest, state, level, pos, true));
        }
        return level.getBlockEntity(pos) instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    /** Whether the container, or a double chest's other half, still holds unopened loot. */
    static boolean hasUnopenedLoot(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return hasLootTable(level, pos) || partner(level, pos, state).map(other -> hasLootTable(level, other)).orElse(false);
    }

    private static boolean hasLootTable(Level level, BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container && container.getLootTable() != null;
    }
}
