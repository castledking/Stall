package codes.castled.stall.paper;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.Tokens;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/** {@code /stall} and its subcommands. */
final class StallCommands implements CommandExecutor, TabCompleter
{
    private static final int REACH = 5;

    private final PaperShops shops;

    StallCommands(PaperShops shops)
    {
        this.shops = shops;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        execute(sender, args);
        return true;
    }

    private int execute(CommandSender sender, String[] args)
    {
        if (args.length == 0)
        {
            return help(sender);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ((subcommand.equals("token") && !sender.hasPermission("stall.token"))
                || (subcommand.equals("admin") && !sender.hasPermission("stall.admin"))
                || (subcommand.equals("list") && args.length > 1 && !sender.hasPermission("stall.admin")))
        {
            return fail(sender, "You don't have permission to do that.");
        }
        try
        {
            return switch (subcommand)
            {
                case "token" -> args.length <= 2 ? token(sender, amount(args, 1)) : usage(sender);
                case "list" -> switch (args.length)
                {
                    case 1 -> listOwn(sender, shops);
                    case 2 -> listFor(sender, shops, args[1]);
                    default -> usage(sender);
                };
                case "info" -> args.length == 1 ? info(sender, shops) : usage(sender);
                case "remove" -> args.length == 1 ? remove(sender, shops) : usage(sender);
                case "reclaim" -> args.length == 1 ? reclaim(sender, shops) : usage(sender);
                case "preview" -> args.length == 1 ? preview(sender, shops) : usage(sender);
                case "admin" -> args.length >= 3 && args.length <= 4 && args[1].equalsIgnoreCase("give")
                        ? give(sender, args[2], amount(args, 3)) : usage(sender);
                default -> usage(sender);
            };
        }
        catch (NumberFormatException invalid)
        {
            return fail(sender, "Amount must be a whole number from 1 to " + Tokens.MAX_PER_COMMAND + ".");
        }
    }

    private static int amount(String[] args, int index)
    {
        int amount = args.length > index ? Integer.parseInt(args[index]) : 1;
        if (amount < 1 || amount > Tokens.MAX_PER_COMMAND)
        {
            throw new NumberFormatException("amount outside the token limit");
        }
        return amount;
    }

    private static int usage(CommandSender sender)
    {
        return fail(sender, "Use /stall for commands. Token amounts must be from 1 to " + Tokens.MAX_PER_COMMAND + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1)
        {
            suggestions.addAll(List.of("list", "info", "remove", "reclaim", "preview"));
            if (sender.hasPermission("stall.token")) suggestions.add("token");
            if (sender.hasPermission("stall.admin")) suggestions.add("admin");
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("token") && sender.hasPermission("stall.token"))
        {
            suggestions.addAll(List.of("1", "16", "64"));
        }
        else if (sender.hasPermission("stall.admin"))
        {
            if (args.length == 2 && args[0].equalsIgnoreCase("list"))
            {
                shops.registry().all().stream().map(Shop::ownerName).distinct().forEach(suggestions::add);
            }
            else if (args[0].equalsIgnoreCase("admin"))
            {
                if (args.length == 2) suggestions.add("give");
                else if (args.length == 3 && args[1].equalsIgnoreCase("give"))
                {
                    Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
                }
                else if (args.length == 4 && args[1].equalsIgnoreCase("give"))
                {
                    suggestions.addAll(List.of("1", "16", "64"));
                }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private static CommandSender executor(CommandSender source)
    {
        return source instanceof ProxiedCommandSender proxy ? proxy.getCallee() : source;
    }

    private static int help(CommandSender source)
    {
        List<String> lines = List.of(
                "/stall token [amount] - get Stall tokens",
                "/stall list - list your shops",
                "/stall info - show the shop you're looking at",
                "/stall remove - remove the shop you're looking at",
                "/stall reclaim - collect tokens from shops removed while you were away",
                "/stall preview - see your own shops as a buyer does");
        CommandSender sender = source;
        sender.sendMessage(BukkitItems.text("Stall: use a token on a barrel to make it your shop. Sneak and right-click "
                + "your shop with an empty hand to set up offers.", ChatColor.GOLD));
        for (String line : lines)
        {
            sender.sendMessage(BukkitItems.text(line, ChatColor.GRAY));
        }
        return lines.size();
    }

    private static int token(CommandSender source, int amount)
    {
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        PaperShops.giveTokens(player, amount);
        return succeed(source, (amount == 1 ? "Here's a Stall token." : "Here are " + amount + " Stall tokens.")
                + " Use one on a barrel to make it your shop.", amount);
    }

    private static int listOwn(CommandSender source, PaperShops shops)
    {
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.isOwner(player.getUniqueId()));
        String limit = PaperShops.describeLimit(PaperShops.shopLimit(player));
        CommandSender sender = source;
        if (owned.isEmpty())
        {
            sender.sendMessage(BukkitItems.text("You don't own any shops (limit: " + limit + "). Use a Stall token on a "
                    + "barrel to make one."));
            return 0;
        }
        sender.sendMessage(BukkitItems.text("Your shops (" + owned.size() + ", limit: " + limit + "):", ChatColor.GOLD));
        sendShopLines(sender, owned);
        return owned.size();
    }

    private static int listFor(CommandSender source, PaperShops shops, String name)
    {
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.ownerName().equalsIgnoreCase(name));
        if (owned.isEmpty())
        {
            return fail(source, name + " doesn't own any shops.");
        }
        source.sendMessage(BukkitItems.text(owned.getFirst().ownerName() + "'s shops (" + owned.size() + "):",
                ChatColor.GOLD));
        sendShopLines(source, owned);
        return owned.size();
    }

    private static int info(CommandSender source, PaperShops shops)
    {
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        Optional<Shop<ItemStack>> target = targetShop(player, shops);
        if (target.isEmpty())
        {
            return fail(source, "Look at a shop first.");
        }
        Shop<ItemStack> shop = target.get();
        CommandSender sender = source;
        sender.sendMessage(BukkitItems.text(shop.ownerName() + "'s shop at " + shop.location(), ChatColor.GOLD));
        if (shop.offers().isEmpty())
        {
            sender.sendMessage(BukkitItems.text(" No offers yet.", ChatColor.GRAY));
        }
        List<Offer<ItemStack>> offers = shop.offers();
        for (int i = 0; i < offers.size(); i++)
        {
            Offer<ItemStack> offer = offers.get(i);
            StringBuilder line = new StringBuilder(BukkitItems.text(" " + (i + 1) + ". ", ChatColor.GRAY))
                    .append(items(offer.costs().items()))
                    .append(BukkitItems.text(" → ", ChatColor.GRAY))
                    .append(items(offer.rewards().items()));
            if (!offer.isComplete())
            {
                line.append(BukkitItems.text(" (not set up yet)", ChatColor.DARK_GRAY));
            }
            sender.sendMessage(line.toString());
        }
        return offers.size();
    }

    private static int remove(CommandSender source, PaperShops shops)
    {
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        Optional<Shop<ItemStack>> target = targetShop(player, shops);
        if (target.isEmpty())
        {
            return fail(source, "Look at a shop first.");
        }
        Shop<ItemStack> shop = target.get();
        boolean own = shop.isOwner(player.getUniqueId());
        if (!own && !player.hasPermission("stall.admin"))
        {
            return fail(source, "This shop belongs to " + shop.ownerName() + ".");
        }
        PaperShops.TokenReturn token = shops.remove(shop, player, false);
        String message = own ? "Removed your shop." : "Removed " + shop.ownerName() + "'s shop.";
        message += switch (token)
        {
            case NONE, DROPPED -> "";
            case GIVEN -> own ? " Its token is back in your inventory." : " Its token went back to them.";
            case OWED -> " They'll get its token back with /stall reclaim.";
        };
        return succeed(source, message, 1);
    }

    private static int reclaim(CommandSender source, PaperShops shops)
    {
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        int owed = shops.reclaimTokens(player);
        if (owed == 0)
        {
            return fail(source, "You have no tokens waiting.");
        }
        return succeed(source, owed == 1 ? "Here's your Stall token back." : "Here are your " + owed
                + " Stall tokens back.", owed);
    }

    private static int preview(CommandSender source, PaperShops shops)
    {
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        boolean on = shops.toggleBuyerPreview(player.getUniqueId());
        return succeed(source, on
                ? "Your own shops now open as they do for buyers. Use /stall preview again to stop."
                : "Your own shops open normally again.", on ? 1 : 0);
    }

    private static int give(CommandSender source, String name, int amount)
    {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null)
        {
            return fail(source, name + " isn't online.");
        }
        PaperShops.giveTokens(target, amount);
        String tokens = amount == 1 ? "a Stall token" : amount + " Stall tokens";
        target.sendMessage(BukkitItems.text("You received " + tokens + ". Use one on a barrel to make it your shop.",
                ChatColor.GREEN));
        return succeed(source, "Gave " + target.getName() + " " + tokens + ".", amount);
    }

    private static List<Shop<ItemStack>> ownedBy(PaperShops shops, Predicate<Shop<ItemStack>> filter)
    {
        return shops.registry().all().stream()
                .filter(filter)
                .sorted(Comparator.comparing((Shop<ItemStack> shop) -> shop.location().world())
                        .thenComparingInt(shop -> shop.location().x())
                        .thenComparingInt(shop -> shop.location().z()))
                .toList();
    }

    private static void sendShopLines(CommandSender sender, List<Shop<ItemStack>> shops)
    {
        for (Shop<ItemStack> shop : shops)
        {
            int complete = shop.completeOffers().size();
            sender.sendMessage(BukkitItems.text(" " + shop.location() + " - "
                    + (complete == 1 ? "1 offer" : complete + " offers"), ChatColor.GRAY));
        }
    }

    private static String items(List<OfferItem<ItemStack>> items)
    {
        if (items.isEmpty())
        {
            return BukkitItems.text("nothing yet", ChatColor.DARK_GRAY);
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < items.size(); i++)
        {
            if (i > 0)
            {
                text.append(BukkitItems.text(" + ", ChatColor.GRAY));
            }
            text.append(items.get(i).amount()).append("× ").append(BukkitItems.name(items.get(i).item()));
        }
        return text.toString();
    }

    private static int succeed(CommandSender source, String message, int result)
    {
        source.sendMessage(BukkitItems.text(message, ChatColor.GREEN));
        return result;
    }

    private static int fail(CommandSender source, String message)
    {
        source.sendMessage(BukkitItems.text(message, ChatColor.RED));
        return 0;
    }

    private static Optional<Shop<ItemStack>> targetShop(Player player, PaperShops shops)
    {
        Block block = player.getTargetBlockExact(REACH);
        return block == null ? Optional.empty() : shops.shopAt(block);
    }
}
