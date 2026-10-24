package codes.castled.stall.paper;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Runtime detection and ownership checks without linking Spigot to Paper-only classes. */
final class FoliaSupport
{
    static final boolean PRESENT = present();

    private FoliaSupport() { }

    private static boolean present()
    {
        try
        {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        }
        catch (ClassNotFoundException spigotOrPaper) { return false; }
    }

    static boolean owns(World world, int chunkX, int chunkZ)
    {
        return !PRESENT || (boolean) SchedulerAdapter.invoke(Bukkit.class, null, "isOwnedByCurrentRegion",
                new Class<?>[]{World.class, int.class, int.class}, world, chunkX, chunkZ);
    }

    static boolean owns(Entity entity)
    {
        return !PRESENT || (boolean) SchedulerAdapter.invoke(Bukkit.class, null, "isOwnedByCurrentRegion",
                new Class<?>[]{Entity.class}, entity);
    }
}
