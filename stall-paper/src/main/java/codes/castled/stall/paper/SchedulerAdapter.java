package codes.castled.stall.paper;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

/** The scheduling subset Stall needs, following GPExpansion's adapter pattern. */
final class SchedulerAdapter
{
    private SchedulerAdapter() { }

    static Object invoke(Class<?> api, Object receiver, String name, Class<?>[] types, Object... arguments)
    {
        try { return api.getMethod(name, types).invoke(receiver, arguments); }
        catch (ReflectiveOperationException error)
        {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Could not call " + api.getName() + "." + name, cause);
        }
    }

    private static Class<?> api(String name)
    {
        try { return Class.forName("io.papermc.paper.threadedregions.scheduler." + name); }
        catch (ClassNotFoundException incompatible) { throw new IllegalStateException(incompatible); }
    }

    static Object scheduler(String getter)
    {
        return invoke(Bukkit.class, null, getter, new Class<?>[0]);
    }

    static Runnable repeatGlobal(Plugin plugin, Runnable task)
    {
        if (!FoliaSupport.PRESENT)
        {
            var scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, 1, 1);
            return scheduled::cancel;
        }
        Object scheduled = invoke(api("GlobalRegionScheduler"), scheduler("getGlobalRegionScheduler"), "runAtFixedRate",
                new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class}, plugin, (Consumer<Object>) ignored -> task.run(), 1L, 1L);
        return () -> invoke(api("ScheduledTask"), scheduled, "cancel", new Class<?>[0]);
    }

    static void entity(Plugin plugin, Entity entity, Runnable task)
    {
        if (!FoliaSupport.PRESENT)
        {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        Object scheduler = invoke(Entity.class, entity, "getScheduler", new Class<?>[0]);
        invoke(api("EntityScheduler"), scheduler, "run", new Class<?>[]{Plugin.class, Consumer.class, Runnable.class},
                plugin, (Consumer<Object>) ignored -> task.run(), (Runnable) () -> { });
    }

    static void region(Plugin plugin, World world, int chunkX, int chunkZ, Runnable task)
    {
        if (!FoliaSupport.PRESENT)
        {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        invoke(api("RegionScheduler"), scheduler("getRegionScheduler"), "run",
                new Class<?>[]{Plugin.class, World.class, int.class, int.class, Consumer.class},
                plugin, world, chunkX, chunkZ, (Consumer<Object>) ignored -> task.run());
    }
}
