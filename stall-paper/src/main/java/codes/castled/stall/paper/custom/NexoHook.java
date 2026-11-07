package codes.castled.stall.paper.custom;

import org.bukkit.block.Block;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Safe reflection-based hook for Nexo custom blocks and furniture.
 */
public final class NexoHook
{
    private static final Method IS_BLOCK;
    private static final Method BLOCK_MECHANIC;
    private static final Method IS_FURNITURE;
    private static final Method FURNITURE_MECHANIC;

    static
    {
        Method isBlock = null;
        Method blockMech = null;
        Method isFurn = null;
        Method furnMech = null;
        try
        {
            Class<?> nexoBlocks = Class.forName("com.nexomc.nexo.api.NexoBlocks");
            isBlock = nexoBlocks.getMethod("isCustomBlock", Block.class);
            blockMech = nexoBlocks.getMethod("customBlockMechanic", Block.class);
        }
        catch (Throwable ignored)
        {
        }
        try
        {
            Class<?> nexoFurniture = Class.forName("com.nexomc.nexo.api.NexoFurniture");
            isFurn = nexoFurniture.getMethod("isFurniture", Block.class);
            furnMech = nexoFurniture.getMethod("furnitureMechanic", Block.class);
        }
        catch (Throwable ignored)
        {
        }
        IS_BLOCK = isBlock;
        BLOCK_MECHANIC = blockMech;
        IS_FURNITURE = isFurn;
        FURNITURE_MECHANIC = furnMech;
    }

    private NexoHook()
    {
    }

    public static boolean isAvailable()
    {
        return IS_BLOCK != null || IS_FURNITURE != null;
    }

    public static Optional<String> customBlockId(Block block)
    {
        if (block == null)
        {
            return Optional.empty();
        }
        if (IS_BLOCK != null && BLOCK_MECHANIC != null)
        {
            try
            {
                Object isCustom = IS_BLOCK.invoke(null, block);
                if (Boolean.TRUE.equals(isCustom))
                {
                    Object mechanic = BLOCK_MECHANIC.invoke(null, block);
                    if (mechanic != null)
                    {
                        Method getId = mechanic.getClass().getMethod("getItemID");
                        Object id = getId.invoke(mechanic);
                        if (id != null)
                        {
                            return Optional.of(id.toString());
                        }
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        if (IS_FURNITURE != null && FURNITURE_MECHANIC != null)
        {
            try
            {
                Object isFurn = IS_FURNITURE.invoke(null, block);
                if (Boolean.TRUE.equals(isFurn))
                {
                    Object mechanic = FURNITURE_MECHANIC.invoke(null, block);
                    if (mechanic != null)
                    {
                        Method getId = mechanic.getClass().getMethod("getItemID");
                        Object id = getId.invoke(mechanic);
                        if (id != null)
                        {
                            return Optional.of(id.toString());
                        }
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        return Optional.empty();
    }
}
