package codes.castled.stall.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class StallPlugin extends JavaPlugin
{
    /** How often open shop menus are checked for a player who walked away or a barrel that is gone. */
    private static final long MENU_CHECK_TICKS = 5;

    /** How often open shop menus are redrawn to show stock that changed meanwhile. */
    private static final long MENU_REFRESH_TICKS = 20;

    private PaperShops shops;

    @Override
    public void onEnable()
    {
        shops = new PaperShops(this);
        shops.startStorage();
        getServer().getPluginManager().registerEvents(new ShopListener(shops), this);
        getServer().getScheduler().runTaskTimer(this, shops::closeInvalidMenus, MENU_CHECK_TICKS, MENU_CHECK_TICKS);
        getServer().getScheduler().runTaskTimer(this, shops::refreshOpenMenus, MENU_REFRESH_TICKS, MENU_REFRESH_TICKS);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(StallCommands.build(shops), "Stall player shops"));
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
