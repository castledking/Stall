package codes.castled.stall.paper;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class StallPlugin extends JavaPlugin
{
    private PaperShops shops;

    @Override
    public void onEnable()
    {
        shops = new PaperShops(this);
        shops.startStorage();
        getServer().getPluginManager().registerEvents(new ShopListener(shops), this);
        getServer().getScheduler().runTaskTimer(this, shops::tick, 1, 1);
        StallCommands commands = new StallCommands(shops);
        var command = Objects.requireNonNull(getCommand("stall"), "stall command missing from plugin.yml");
        command.setExecutor(commands);
        command.setTabCompleter(commands);
    }

    @Override
    public void onDisable()
    {
        if (shops != null)
        {
            shops.closeAll();
            shops.stopStorage();
        }
    }
}
