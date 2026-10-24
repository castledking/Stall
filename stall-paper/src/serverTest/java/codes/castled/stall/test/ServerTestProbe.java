package codes.castled.stall.test;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Only install on an isolated test server: runs the checks and stops the server. */
public final class ServerTestProbe extends JavaPlugin
{
    @Override
    public void onEnable()
    {
        getServer().getScheduler().runTask(this, () ->
        {
            try
            {
                Plugin stall = getServer().getPluginManager().getPlugin("Stall");
                if (stall == null || !stall.isEnabled()) throw new IllegalStateException("Stall did not enable");
                // Checks live in Stall's class loader, with access to its package-private adapters.
                Class<?> checks = Class.forName("codes.castled.stall.paper.ServerChecks", true, stall.getClass().getClassLoader());
                checks.getMethod("run", Plugin.class).invoke(null, stall);
                getLogger().info("STALL_SERVER_CHECKS_PASS");
            }
            catch (Throwable error)
            {
                getLogger().log(java.util.logging.Level.SEVERE, "STALL_SERVER_CHECKS_FAIL", error);
            }
            finally
            {
                getServer().shutdown();
            }
        });
    }
}
