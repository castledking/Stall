package codes.castled.stall.paper;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Entity tasks follow players across regions; region tasks only inspect their own shop chunks. */
final class SchedulerFacade
{
    private final Plugin plugin;
    private Runnable cancelClock = () -> { };
    private volatile boolean stopped;

    SchedulerFacade(Plugin plugin) { this.plugin = plugin; }

    void start(Runnable tick) { cancelClock = SchedulerAdapter.repeatGlobal(plugin, tick); }

    void entity(Entity entity, Runnable task)
    {
        if (stopped) return;
        if (FoliaSupport.owns(entity)) task.run();
        else laterEntity(entity, task);
    }

    void laterEntity(Entity entity, Runnable task)
    {
        if (!stopped) SchedulerAdapter.entity(plugin, entity, () -> { if (!stopped) task.run(); });
    }

    void region(World world, int chunkX, int chunkZ, Runnable task)
    {
        if (world.isChunkLoaded(chunkX, chunkZ)) afterChunkLoad(world, chunkX, chunkZ, task);
    }

    /** ChunkLoadEvent can precede the full-chunk cache update; defer its check without dropping it. */
    void afterChunkLoad(World world, int chunkX, int chunkZ, Runnable task)
    {
        if (!stopped) SchedulerAdapter.region(plugin, world, chunkX, chunkZ, () -> { if (!stopped) task.run(); });
    }

    void stop()
    {
        stopped = true;
        cancelClock.run();
    }
}
