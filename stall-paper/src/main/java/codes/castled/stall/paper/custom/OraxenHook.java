package codes.castled.stall.paper.custom;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Safe reflection-based hook for Oraxen custom blocks and furniture.
 */
public final class OraxenHook
{
    private static final Method IS_BLOCK;
    private static final Method GET_BLOCK_LOC;
    private static final Method IS_FURNITURE;
    private static final Method GET_FURNITURE_MECH;

    static
    {
        Method isBlock = null;
        Method getBlockLoc = null;
        Method isFurn = null;
        Method getFurnMech = null;
        try
        {
            Class<?> oraxenBlocks = Class.forName("io.th0rgal.oraxen.api.OraxenBlocks");
            try
            {
                isBlock = oraxenBlocks.getMethod("isOraxenBlock", Block.class);
            }
            catch (NoSuchMethodException e)
            {
                isBlock = oraxenBlocks.getMethod("isOraxenBlock", Location.class);
            }
            getBlockLoc = oraxenBlocks.getMethod("getOraxenBlock", Location.class);
        }
        catch (Throwable ignored)
        {
        }
        try
        {
            Class<?> oraxenFurniture = Class.forName("io.th0rgal.oraxen.api.OraxenFurniture");
            isFurn = oraxenFurniture.getMethod("isFurniture", Block.class);
            getFurnMech = oraxenFurniture.getMethod("getFurnitureMechanic", Block.class);
        }
        catch (Throwable ignored)
        {
        }
        IS_BLOCK = isBlock;
        GET_BLOCK_LOC = getBlockLoc;
        IS_FURNITURE = isFurn;
        GET_FURNITURE_MECH = getFurnMech;
    }

    private OraxenHook()
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
        if (IS_BLOCK != null && GET_BLOCK_LOC != null)
        {
            try
            {
                Object isCustom;
                if (IS_BLOCK.getParameterTypes()[0].equals(Block.class))
                {
                    isCustom = IS_BLOCK.invoke(null, block);
                }
                else
                {
                    isCustom = IS_BLOCK.invoke(null, block.getLocation());
                }
                if (Boolean.TRUE.equals(isCustom))
                {
                    Object mechanic = GET_BLOCK_LOC.invoke(null, block.getLocation());
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
        if (IS_FURNITURE != null && GET_FURNITURE_MECH != null)
        {
            try
            {
                Object isFurn = IS_FURNITURE.invoke(null, block);
                if (Boolean.TRUE.equals(isFurn))
                {
                    Object mechanic = GET_FURNITURE_MECH.invoke(null, block);
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
