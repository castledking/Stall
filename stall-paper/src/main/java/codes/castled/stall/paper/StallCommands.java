package codes.castled.stall.paper;

import codes.castled.stall.config.StallLang;
import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.paper.permission.VaultHook;
import codes.castled.stall.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.UUID;
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
        if (executor(sender) instanceof Player player && !FoliaSupport.owns(player))
        {
            String[] arguments = args.clone();
            shops.scheduler().entity(player, () -> { if (player.isOnline()) execute(sender, label, arguments); });
            return true;
        }
        execute(sender, label, args);
        return true;
    }

    private int execute(CommandSender sender, String label, String[] args)
    {
        if (args.length == 0)
        {
            return help(sender, label);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ((subcommand.equals("token") && !sender.hasPermission("stall.token"))
                || (subcommand.equals("admin") && !sender.hasPermission("stall.admin"))
                || (subcommand.equals("list") && args.length > 1 && !sender.hasPermission("stall.admin")))
        {
            return fail(sender, shops.lang().chat("commands.no-permission"));
        }
        try
        {
            return switch (subcommand)
            {
                case "token" -> args.length <= 2 ? token(sender, amount(args, 1)) : usage(sender, label);
                case "list" -> switch (args.length)
                {
                    case 1 -> listOwn(sender, shops);
                    case 2 -> listFor(sender, shops, args[1]);
                    default -> usage(sender, label);
                };
                case "info" -> args.length == 1 ? info(sender, shops) : usage(sender, label);
                case "remove" -> args.length == 1 ? remove(sender, shops) : usage(sender, label);
                case "reclaim" -> args.length == 1 ? reclaim(sender, shops) : usage(sender, label);
                case "preview" -> args.length == 1 ? preview(sender, shops) : usage(sender, label);
                case "admin" -> admin(sender, label, args);
                default -> usage(sender, label);
            };
        }
        catch (NumberFormatException invalid)
        {
            return fail(sender, shops.lang().chat("commands.invalid-amount",
                    "max", String.valueOf(shops.config().maxTokensPerCommand())));
        }
    }

    private int admin(CommandSender sender, String label, String[] args)
    {
        if (args.length == 2 && args[1].equalsIgnoreCase("reload"))
        {
            shops.reloadConfig();
            return succeed(sender, shops.lang().chat("commands.reload"), 1);
        }
        if (args.length >= 3 && args.length <= 4 && args[1].equalsIgnoreCase("give"))
        {
            return give(sender, args[2], amount(args, 3));
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("max"))
        {
            return max(sender, label, args);
        }
        return usage(sender, label);
    }

    private int max(CommandSender sender, String label, String[] args)
    {
        String cmd = "/" + (label != null && !label.isBlank() ? label.toLowerCase(Locale.ROOT) : "stall");
        if (args.length < 5)
        {
            return fail(sender, shops.lang().chat("commands.max-usage", "command", cmd));
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        boolean isLimit = type.equals("limit");
        boolean isOffers = type.equals("offers");
        if (!isLimit && !isOffers)
        {
            return fail(sender, shops.lang().chat("commands.max-usage", "command", cmd));
        }
        String action = args[3].toLowerCase(Locale.ROOT);
        if (!action.equals("set") && !action.equals("add") && !action.equals("take") && !action.equals("reset"))
        {
            return fail(sender, shops.lang().chat("commands.max-usage", "command", cmd));
        }
        String playerName = args[4];
        Player online = Bukkit.getPlayer(playerName);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayer(playerName);
        if (target.getName() == null && !target.hasPlayedBefore())
        {
            return fail(sender, shops.lang().chat("commands.player-not-found", "player", playerName));
        }
        String name = target.getName() != null ? target.getName() : playerName;
        UUID uuid = target.getUniqueId();

        if (action.equals("reset"))
        {
            if (isLimit)
            {
                shops.playerOverrides().clearLimit(uuid);
            }
            else
            {
                shops.playerOverrides().clearOffers(uuid);
            }
            int current = isLimit ? shops.playerLimit(uuid, online) : shops.maxOffers(uuid, online);
            return succeed(sender, shops.lang().chat("commands.max-reset",
                    "player", name,
                    "type", isLimit ? "shop" : "offer",
                    "current", String.valueOf(current)), 1);
        }

        if (args.length < 6)
        {
            return fail(sender, shops.lang().chat("commands.max-usage", "command", cmd));
        }
        int amount;
        try
        {
            amount = Integer.parseInt(args[5]);
            if (amount < 0)
            {
                return fail(sender, shops.lang().chat("commands.invalid-amount",
                        "max", String.valueOf(shops.config().maxTokensPerCommand())));
            }
        }
        catch (NumberFormatException e)
        {
            return fail(sender, shops.lang().chat("commands.invalid-amount",
                    "max", String.valueOf(shops.config().maxTokensPerCommand())));
        }

        if (isLimit)
        {
            int current = shops.playerLimit(uuid, online);
            int newAmount = switch (action)
            {
                case "set" -> amount;
                case "add" -> current + amount;
                case "take" -> Math.max(0, current - amount);
                default -> amount;
            };
            shops.playerOverrides().setLimit(uuid, name, newAmount);
            if (online != null)
            {
                VaultHook.setLimitPermission(online, newAmount);
                if (online != sender)
                {
                    BukkitItems.send(online, shops.lang().chat("commands.max-updated-player",
                            "type", "shop", "current", String.valueOf(newAmount)));
                }
            }
            return succeed(sender, shops.lang().chat("commands.max-updated",
                    "player", name,
                    "type", "shop",
                    "action", action,
                    "amount", String.valueOf(amount),
                    "current", String.valueOf(newAmount)), 1);
        }
        else
        {
            int current = shops.maxOffers(uuid, online);
            int newAmount = switch (action)
            {
                case "set" -> Math.clamp(amount, 1, codes.castled.stall.shop.Shop.MAX_OFFERS);
                case "add" -> Math.clamp(current + amount, 1, codes.castled.stall.shop.Shop.MAX_OFFERS);
                case "take" -> Math.clamp(current - amount, 1, codes.castled.stall.shop.Shop.MAX_OFFERS);
                default -> Math.clamp(amount, 1, codes.castled.stall.shop.Shop.MAX_OFFERS);
            };
            shops.playerOverrides().setOffers(uuid, name, newAmount);
            if (online != null)
            {
                VaultHook.setOffersPermission(online, newAmount);
                if (online != sender)
                {
                    BukkitItems.send(online, shops.lang().chat("commands.max-updated-player",
                            "type", "offer", "current", String.valueOf(newAmount)));
                }
            }
            return succeed(sender, shops.lang().chat("commands.max-updated",
                    "player", name,
                    "type", "offer",
                    "action", action,
                    "amount", String.valueOf(amount),
                    "current", String.valueOf(newAmount)), 1);
        }
    }

    private int amount(String[] args, int index)
    {
        int amount = args.length > index ? Integer.parseInt(args[index]) : 1;
        if (amount < 1 || amount > shops.config().maxTokensPerCommand())
        {
            throw new NumberFormatException("amount outside the token limit");
        }
        return amount;
    }

    private int usage(CommandSender sender, String label)
    {
        String cmd = "/" + (label != null && !label.isBlank() ? label.toLowerCase(Locale.ROOT) : "stall");
        String msg = shops.lang().chat("commands.usage",
                "command", cmd,
                "max", String.valueOf(shops.config().maxTokensPerCommand()));
        if (!cmd.equals("/stall"))
        {
            msg = msg.replace("/stall", cmd);
        }
        return fail(sender, msg);
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
            suggestions.addAll(amountSuggestions());
        }
        else if (sender.hasPermission("stall.admin"))
        {
            if (args.length == 2 && args[0].equalsIgnoreCase("list"))
            {
                shops.registry().all().stream().map(Shop::ownerName).distinct().forEach(suggestions::add);
            }
            else if (args[0].equalsIgnoreCase("admin"))
            {
                if (args.length == 2) suggestions.addAll(List.of("give", "max", "reload"));
                else if (args.length == 3 && args[1].equalsIgnoreCase("give"))
                {
                    Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
                }
                else if (args.length == 4 && args[1].equalsIgnoreCase("give"))
                {
                    suggestions.addAll(amountSuggestions());
                }
                else if (args.length == 3 && args[1].equalsIgnoreCase("max"))
                {
                    suggestions.addAll(List.of("limit", "offers"));
                }
                else if (args.length == 4 && args[1].equalsIgnoreCase("max"))
                {
                    suggestions.addAll(List.of("add", "reset", "set", "take"));
                }
                else if (args.length == 5 && args[1].equalsIgnoreCase("max"))
                {
                    Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
                }
                else if (args.length == 6 && args[1].equalsIgnoreCase("max") && !args[3].equalsIgnoreCase("reset"))
                {
                    if (args[2].equalsIgnoreCase("offers"))
                    {
                        suggestions.addAll(List.of("1", "2", "3", "4", "5", "6"));
                    }
                    else
                    {
                        suggestions.addAll(List.of("1", "3", "5", "10", "20", "50", "100"));
                    }
                }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private List<String> amountSuggestions()
    {
        int max = shops.config().maxTokensPerCommand();
        return List.of(1, 16, 64).stream().filter(amount -> amount <= max).map(String::valueOf).toList();
    }

    private static CommandSender executor(CommandSender source)
    {
        return source instanceof ProxiedCommandSender proxy ? proxy.getCallee() : source;
    }

    private int help(CommandSender source, String label)
    {
        StallLang lang = shops.lang();
        BukkitItems.send(source, lang.chat("commands.help.header"));
        List<String> lines = lang.lines("commands.help.lines");
        String prefix = "/" + (label != null && !label.isBlank() ? label.toLowerCase(Locale.ROOT) : "stall") + " ";
        int count = 0;
        for (String line : lines)
        {
            if (line.contains("/stall token") && !source.hasPermission("stall.token"))
            {
                continue;
            }
            BukkitItems.send(source, formatHelpLine(line, prefix));
            count++;
        }
        if (source.hasPermission("stall.admin"))
        {
            for (String line : lang.lines("commands.help.admin-lines"))
            {
                BukkitItems.send(source, formatHelpLine(line, prefix));
                count++;
            }
        }
        return count;
    }

    private static String formatHelpLine(String line, String prefix)
    {
        return prefix.equals("/stall ") ? line : line.replace("/stall ", prefix);
    }

    private int token(CommandSender source, int amount)
    {
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, shops.lang().chat("commands.players-only"));
        }
        shops.giveTokens(player, amount);
        return succeed(source, shops.lang().chat(amount == 1 ? "commands.token.one" : "commands.token.many",
                "amount", String.valueOf(amount)), amount);
    }

    private static int listOwn(CommandSender source, PaperShops shops)
    {
        StallLang lang = shops.lang();
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, lang.chat("commands.players-only"));
        }
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.isOwner(player.getUniqueId()));
        String limit = shops.describeLimit(shops.shopLimit(player));
        if (owned.isEmpty())
        {
            BukkitItems.send(source, lang.chat("commands.list.none-own", "limit", limit));
            return 0;
        }
        BukkitItems.send(source, lang.chat("commands.list.own-header",
                "count", String.valueOf(owned.size()), "limit", limit));
        sendShopLines(source, lang, owned);
        return owned.size();
    }

    private static int listFor(CommandSender source, PaperShops shops, String name)
    {
        StallLang lang = shops.lang();
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.ownerName().equalsIgnoreCase(name));
        if (owned.isEmpty())
        {
            return fail(source, lang.chat("commands.list.none-other", "player", name));
        }
        BukkitItems.send(source, lang.chat("commands.list.other-header",
                "owner", owned.getFirst().ownerName(), "count", String.valueOf(owned.size())));
        sendShopLines(source, lang, owned);
        return owned.size();
    }

    private static int info(CommandSender source, PaperShops shops)
    {
        StallLang lang = shops.lang();
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, lang.chat("commands.players-only"));
        }
        Optional<Shop<ItemStack>> target = targetShop(player, shops);
        if (target.isEmpty())
        {
            return fail(source, lang.chat("commands.look-at-shop"));
        }
        Shop<ItemStack> shop = target.get();
        BukkitItems.send(source, lang.chat("commands.info.header",
                "owner", shop.ownerName(),
                "location", shop.location().toString(),
                "container", lang.container(shop.container(), shop.largeStock())));
        if (shop.offers().isEmpty())
        {
            BukkitItems.send(source, lang.text("commands.info.no-offers"));
        }
        List<Offer<ItemStack>> offers = shop.offers();
        for (int i = 0; i < offers.size(); i++)
        {
            Offer<ItemStack> offer = offers.get(i);
            StringBuilder line = new StringBuilder(lang.text("commands.info.offer-prefix",
                    "index", String.valueOf(i + 1)))
                    .append(items(lang, offer.costs().items()))
                    .append(lang.text("commands.info.offer-arrow"))
                    .append(items(lang, offer.rewards().items()));
            if (!offer.isComplete())
            {
                line.append(lang.text("commands.info.incomplete-suffix"));
            }
            BukkitItems.send(source, line.toString());
        }
        return offers.size();
    }

    private static int remove(CommandSender source, PaperShops shops)
    {
        StallLang lang = shops.lang();
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, lang.chat("commands.players-only"));
        }
        Optional<Shop<ItemStack>> target = targetShop(player, shops);
        if (target.isEmpty())
        {
            return fail(source, lang.chat("commands.look-at-shop"));
        }
        Shop<ItemStack> shop = target.get();
        boolean own = shop.isOwner(player.getUniqueId());
        if (!shops.canAccess(player, shop)) return fail(source, lang.chat("commands.move-closer"));
        if (!own && !player.hasPermission("stall.admin"))
        {
            return fail(source, lang.chat("shops.belongs-to", "owner", shop.ownerName()));
        }
        if (!shops.extraStockEmpty(shop))
        {
            return fail(source, lang.chat("commands.remove.extra-stock-not-empty"));
        }
        PaperShops.TokenReturn token = shops.remove(shop, player, null);
        String message = own
                ? lang.chat("commands.remove.own", "owner", shop.ownerName())
                : lang.chat("commands.remove.other", "owner", shop.ownerName());
        if (!message.isEmpty())
        {
            message += switch (token)
            {
                case NONE, DROPPED -> "";
                case GIVEN -> own
                        ? lang.text("commands.remove.token-own-inventory")
                        : lang.text("commands.remove.token-other-inventory");
                case OWED -> lang.text("commands.remove.token-owed");
            };
        }
        return succeed(source, message, 1);
    }

    private static int reclaim(CommandSender source, PaperShops shops)
    {
        StallLang lang = shops.lang();
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, lang.chat("commands.players-only"));
        }
        int owed = shops.reclaimTokens(player);
        if (owed == 0)
        {
            return fail(source, lang.chat("commands.reclaim.none"));
        }
        return succeed(source, lang.chat(owed == 1 ? "commands.reclaim.one" : "commands.reclaim.many",
                "count", String.valueOf(owed)), owed);
    }

    private static int preview(CommandSender source, PaperShops shops)
    {
        StallLang lang = shops.lang();
        if (!(executor(source) instanceof Player player))
        {
            return fail(source, lang.chat("commands.players-only"));
        }
        boolean on = shops.toggleBuyerPreview(player.getUniqueId());
        return succeed(source, lang.chat(on ? "commands.preview.enabled" : "commands.preview.disabled"),
                on ? 1 : 0);
    }

    private int give(CommandSender source, String name, int amount)
    {
        StallLang lang = shops.lang();
        Player target = Bukkit.getPlayerExact(name);
        if (target == null)
        {
            return fail(source, lang.chat("commands.player-offline", "player", name));
        }
        shops.scheduler().entity(target, () ->
        {
            if (!target.isOnline()) return;
            StallLang currentLang = shops.lang();
            shops.giveTokens(target, amount);
            String tokens = currentLang.text(amount == 1 ? "commands.give.token-one" : "commands.give.token-many",
                    "amount", String.valueOf(amount));
            BukkitItems.send(target, currentLang.chat("commands.give.received",
                    "tokens", tokens, "amount", String.valueOf(amount)));
            succeed(source, currentLang.chat("commands.give.sent",
                    "player", target.getName(), "tokens", tokens, "amount", String.valueOf(amount)), amount);
        });
        return amount;
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

    private static void sendShopLines(CommandSender sender, StallLang lang, List<Shop<ItemStack>> shops)
    {
        for (Shop<ItemStack> shop : shops)
        {
            int complete = shop.completeOffers().size();
            BukkitItems.send(sender, lang.text(
                    complete == 1 ? "commands.list.entry-one" : "commands.list.entry-many",
                    "location", shop.location().toString(),
                    "offers", String.valueOf(complete)));
        }
    }

    private static String items(StallLang lang, List<OfferItem<ItemStack>> items)
    {
        if (items.isEmpty())
        {
            return lang.text("commands.info.nothing-yet");
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < items.size(); i++)
        {
            if (i > 0)
            {
                text.append(lang.text("commands.info.item-separator"));
            }
            text.append(lang.text("commands.info.item-amount", "amount", String.valueOf(items.get(i).amount())))
                    .append(BukkitItems.name(items.get(i).item()));
        }
        return text.toString();
    }

    private static int succeed(CommandSender source, String message, int result)
    {
        BukkitItems.send(source, message);
        return result;
    }

    private static int fail(CommandSender source, String message)
    {
        BukkitItems.send(source, message);
        return 0;
    }

    private static Optional<Shop<ItemStack>> targetShop(Player player, PaperShops shops)
    {
        Block block = player.getTargetBlockExact(REACH);
        return block == null ? Optional.empty() : shops.shopAt(block);
    }
}
