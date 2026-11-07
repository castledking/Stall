package codes.castled.stall.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class StallPlugin extends JavaPlugin
{
    private PaperShops shops;
    private StallCommands commands;
    private final Set<String> registeredAliases = new HashSet<>();

    @Override
    public void onEnable()
    {
        shops = new PaperShops(this);
        shops.startStorage();
        getServer().getPluginManager().registerEvents(new ShopListener(shops), this);
        shops.scheduler().start(shops::tick);
        shops.sweep();
        commands = new StallCommands(shops);
        var command = Objects.requireNonNull(getCommand("stall"), "stall command missing from plugin.yml");
        command.setExecutor(commands);
        command.setTabCompleter(commands);
        registerAliases();
    }

    void registerAliases()
    {
        if (commands == null)
        {
            return;
        }
        CommandMap map = commandMap();
        if (map == null)
        {
            return;
        }
        boolean added = false;
        for (String alias : shops.config().commandAliases())
        {
            if (!alias.equalsIgnoreCase("stall") && registeredAliases.add(alias.toLowerCase(Locale.ROOT)))
            {
                map.register(alias, "stall", new StallAliasCommand(alias, commands));
                added = true;
            }
        }
        if (added)
        {
            for (var player : getServer().getOnlinePlayers())
            {
                player.updateCommands();
            }
        }
    }

    private CommandMap commandMap()
    {
        try
        {
            Method method = getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) method.invoke(getServer());
        }
        catch (ReflectiveOperationException ignored)
        {
            try
            {
                Field field = getServer().getPluginManager().getClass().getDeclaredField("commandMap");
                field.setAccessible(true);
                return (CommandMap) field.get(getServer().getPluginManager());
            }
            catch (ReflectiveOperationException e)
            {
                getLogger().warning("Could not access CommandMap to register command aliases.");
                return null;
            }
        }
    }

    @Override
    public void onDisable()
    {
        if (shops != null)
        {
            shops.closeAll();
            shops.scheduler().stop();
            shops.stopStorage();
        }
    }
}
