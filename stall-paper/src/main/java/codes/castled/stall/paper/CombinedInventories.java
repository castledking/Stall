package codes.castled.stall.paper;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Joins two live inventories into one 54-slot inventory, the way the server joins a double chest's halves:
 * a vanilla {@code CompoundContainer} wrapped in CraftBukkit's double-chest inventory. Opened, it is an
 * ordinary vanilla menu whose slots are the two inventories themselves, so every click works as vanilla's
 * and nothing is copied back and forth.
 *
 * <p>The Bukkit API can't join inventories, so this binds the server's classes by name, like
 * {@link PaperItemCodec}. Minecraft 26.x isn't obfuscated, so the names are the same on Spigot, Paper and
 * Folia. They must be checked again when adding a Minecraft version.
 */
final class CombinedInventories
{
    private static final Binding BINDING = bind();

    private CombinedInventories()
    {
    }

    private record Binding(Method handle, Constructor<?> compound, Constructor<?> combined, Throwable failure)
    {
    }

    private static Binding bind()
    {
        try
        {
            String craft = Bukkit.getServer().getClass().getPackageName() + ".inventory.";
            Class<?> container = Class.forName("net.minecraft.world.Container");
            Class<?> compound = Class.forName("net.minecraft.world.CompoundContainer");
            return new Binding(Class.forName(craft + "CraftInventory").getMethod("getInventory"),
                    compound.getConstructor(container, container),
                    Class.forName(craft + "CraftInventoryDoubleChest").getConstructor(compound), null);
        }
        catch (ReflectiveOperationException | LinkageError failure)
        {
            return new Binding(null, null, null, failure);
        }
    }

    /** Whether this server's classes could be bound. Without them there are no large barrels. */
    static boolean available()
    {
        return BINDING.failure() == null;
    }

    /** Why binding failed, for the log; {@code null} if it didn't. */
    static Throwable failure()
    {
        return BINDING.failure();
    }

    /** {@code top} as the first 27 slots and {@code bottom} as the last 27, both live. */
    static Inventory of(Inventory top, Inventory bottom)
    {
        if (!available())
        {
            throw new IllegalStateException("Combined inventories are not available on this server", BINDING.failure());
        }
        try
        {
            Object joined = BINDING.compound().newInstance(BINDING.handle().invoke(top), BINDING.handle().invoke(bottom));
            return (Inventory) BINDING.combined().newInstance(joined);
        }
        catch (ReflectiveOperationException failure)
        {
            throw new IllegalStateException("Could not join two inventories", failure);
        }
    }
}
