package codes.castled.stall.paper.custom;

import codes.castled.stall.config.CustomBlockSpec;
import codes.castled.stall.config.StallConfig;
import org.bukkit.block.Block;

import java.util.Optional;

/**
 * Registry and detector for custom blocks and models from CraftEngine, Nexo, and Oraxen.
 */
public final class CustomBlocks
{
    private CustomBlocks()
    {
    }

    /**
     * Resolves the custom block identifier from any supported provider (CraftEngine, Nexo, Oraxen).
     */
    public static Optional<String> findCustomBlockId(Block block)
    {
        if (block == null)
        {
            return Optional.empty();
        }
        Optional<String> nexo = NexoHook.customBlockId(block);
        if (nexo.isPresent())
        {
            return nexo;
        }
        Optional<String> oraxen = OraxenHook.customBlockId(block);
        if (oraxen.isPresent())
        {
            return oraxen;
        }
        return CraftEngineHook.customBlockId(block);
    }

    /**
     * Resolves a {@link CustomBlockSpec} if the given block is a custom model matching {@code config.yml}'s
     * {@code shops.custom-blocks}.
     */
    public static Optional<CustomBlockSpec> findSpec(Block block, StallConfig config)
    {
        if (block == null || config == null || config.customBlocks().isEmpty())
        {
            return Optional.empty();
        }
        return findCustomBlockId(block).flatMap(config::customBlock);
    }

    /**
     * Checks whether the given block is a supported and configured custom shop block.
     */
    public static boolean isSupportedCustomBlock(Block block, StallConfig config)
    {
        return findSpec(block, config).isPresent();
    }
}
