package codes.castled.stall.paper;

import codes.castled.stall.shop.ShopContainers;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.loot.Lootable;

import java.util.Optional;

/**
 * The blocks a shop can be made from, and how to reach their stock: barrels, chests and trapped chests,
 * single or double. Copper chests are left out, because copper golems take items out of them.
 */
final class PaperContainers
{
    private PaperContainers()
    {
    }

    static boolean isSupported(Block block)
    {
        Material type = block.getType();
        return type == Material.BARREL || type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }

    static boolean isBarrel(Block block)
    {
        return block.getType() == Material.BARREL;
    }

    /** The block's id as saved with a shop, such as {@code minecraft:chest}. */
    static String id(Block block)
    {
        return block.getType().getKey().toString();
    }

    /** What players call the container, for messages. */
    static String describe(Block block)
    {
        return ShopContainers.describe(id(block));
    }

    /**
     * The other half of a double chest, or empty for anything else. Found the way vanilla finds it, from
     * the chest's facing and which half it is, and only if the other half points back.
     */
    static Optional<Block> partner(Block block)
    {
        return connected(block).filter(other -> other.getWorld().isChunkLoaded(other.getX() >> 4, other.getZ() >> 4)
                && FoliaSupport.owns(other.getWorld(), other.getX() >> 4, other.getZ() >> 4))
                .filter(other -> other.getType() == block.getType()
                        && other.getBlockData() instanceof Chest half
                        && block.getBlockData() instanceof Chest chest
                        && half.getFacing() == chest.getFacing()
                        && half.getType() != Chest.Type.SINGLE && half.getType() != chest.getType());
    }

    /** The expected other half, without reading or loading its chunk. Useful for protection lookup. */
    static Optional<Block> connected(Block block)
    {
        if (!(block.getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE)
        {
            return Optional.empty();
        }
        BlockFace facing = chest.getFacing();
        Block other = block.getRelative(chest.getType() == Chest.Type.LEFT ? clockwise(facing) : counterClockwise(facing));
        return Optional.of(other);
    }

    /** The live inventory of the container at {@code block}; a double chest's covers both halves. */
    static Optional<Inventory> inventory(Block block)
    {
        if (connected(block).isPresent() && partner(block).isEmpty())
        {
            return Optional.empty();
        }
        return isSupported(block) && block.getState() instanceof Container container
                ? Optional.of(container.getInventory()) : Optional.empty();
    }

    /** Whether the container, or a double chest's other half, still holds unopened loot. */
    static boolean hasUnopenedLoot(Block block)
    {
        return hasLootTable(block) || partner(block).map(PaperContainers::hasLootTable).orElse(false);
    }

    private static boolean hasLootTable(Block block)
    {
        return block.getState() instanceof Lootable lootable && lootable.getLootTable() != null;
    }

    private static BlockFace clockwise(BlockFace face)
    {
        return switch (face)
        {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private static BlockFace counterClockwise(BlockFace face)
    {
        return switch (face)
        {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }
}
