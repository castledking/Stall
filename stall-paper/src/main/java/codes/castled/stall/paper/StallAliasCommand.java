package codes.castled.stall.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/** A dynamically registered alias for the {@code /stall} command. */
final class StallAliasCommand extends Command
{
    private final StallCommands executor;

    StallAliasCommand(String name, StallCommands executor)
    {
        super(name);
        this.executor = executor;
        setDescription("Create and manage player shops.");
        setUsage("/" + name);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args)
    {
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args)
    {
        return executor.onTabComplete(sender, this, alias, args);
    }
}
