package codes.castled.stall.paper.custom;

import org.bukkit.block.Block;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Safe reflection-based hook for CraftEngine custom blocks.
 */
public final class CraftEngineHook
{
    private static final Method IS_CUSTOM_BLOCK;
    private static final Method GET_CUSTOM_BLOCK_STATE;

    static
    {
        Method isBlock = null;
        Method getState = null;
        try
        {
            Class<?> ceBlocks = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
            isBlock = ceBlocks.getMethod("isCustomBlock", Block.class);
            getState = ceBlocks.getMethod("getCustomBlockState", Block.class);
        }
        catch (Throwable ignored)
        {
        }
        IS_CUSTOM_BLOCK = isBlock;
        GET_CUSTOM_BLOCK_STATE = getState;
    }

    private CraftEngineHook()
    {
    }

    public static boolean isAvailable()
    {
        return IS_CUSTOM_BLOCK != null && GET_CUSTOM_BLOCK_STATE != null;
    }

    public static Optional<String> customBlockId(Block block)
    {
        if (block == null || IS_CUSTOM_BLOCK == null || GET_CUSTOM_BLOCK_STATE == null)
        {
            return Optional.empty();
        }
        try
        {
            Object isCustom = IS_CUSTOM_BLOCK.invoke(null, block);
            if (Boolean.TRUE.equals(isCustom))
            {
                Object state = GET_CUSTOM_BLOCK_STATE.invoke(null, block);
                if (state != null)
                {
                    Method ownerMethod = state.getClass().getMethod("owner");
                    Object owner = ownerMethod.invoke(state);
                    if (owner != null)
                    {
                        // Check owner.key().location() or owner.value().id()
                        try
                        {
                            Method keyMethod = owner.getClass().getMethod("key");
                            Object key = keyMethod.invoke(owner);
                            if (key != null)
                            {
                                Method locMethod = key.getClass().getMethod("location");
                                Object loc = locMethod.invoke(key);
                                if (loc != null)
                                {
                                    return Optional.of(loc.toString());
                                }
                            }
                        }
                        catch (Throwable ignored)
                        {
                        }
                        try
                        {
                            Method valueMethod = owner.getClass().getMethod("value");
                            Object value = valueMethod.invoke(owner);
                            if (value != null)
                            {
                                Method idMethod = value.getClass().getMethod("id");
                                Object id = idMethod.invoke(value);
                                if (id != null)
                                {
                                    return Optional.of(id.toString());
                                }
                            }
                        }
                        catch (Throwable ignored)
                        {
                        }
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
        }
        return Optional.empty();
    }
}
