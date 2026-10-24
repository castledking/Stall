package codes.castled.stall.test;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import java.util.function.Consumer;

/** Only install on an isolated test server: runs the checks and stops the server. */
public final class ServerTestProbe extends JavaPlugin
{
    @Override
    public void onEnable()
    {
        try
        {
            Plugin stall = getServer().getPluginManager().getPlugin("Stall");
            if (stall == null || !stall.isEnabled()) throw new IllegalStateException("Stall did not enable");
            Class<?> checks = Class.forName("codes.castled.stall.paper.ServerChecks", true, stall.getClass().getClassLoader());
            checks.getMethod("start", Plugin.class, Consumer.class).invoke(null, stall, (Consumer<Throwable>) this::finish);
        }
        catch (Throwable error) { finish(error); }
    }

    private void finish(Throwable error)
    {
        if (error == null) getLogger().info("STALL_SERVER_CHECKS_PASS");
        else getLogger().log(java.util.logging.Level.SEVERE, "STALL_SERVER_CHECKS_FAIL", error);
        try
        {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler")
                    .getMethod("execute", Plugin.class, Runnable.class).invoke(scheduler, this, (Runnable) Bukkit::shutdown);
        }
        catch (NoSuchMethodException spigot) { Bukkit.getScheduler().runTask(this, Bukkit::shutdown); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
