package codes.castled.stall.paper;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.Tokens;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** {@code /stall} and its subcommands. */
final class StallCommands
{
    private static final int REACH = 5;

    private StallCommands()
    {
    }

    static LiteralCommandNode<CommandSourceStack> build(PaperShops shops)
    {
        return Commands.literal("stall")
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("token")
                        .requires(source -> source.getSender().hasPermission("stall.token"))
                        .executes(context -> token(context.getSource(), 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, Tokens.MAX_PER_COMMAND))
                                .executes(context -> token(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "amount")))))
                .then(Commands.literal("list")
                        .executes(context -> listOwn(context.getSource(), shops))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(source -> source.getSender().hasPermission("stall.admin"))
                                .suggests((context, builder) ->
                                {
                                    shops.registry().all().stream().map(Shop::ownerName).distinct().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> listFor(context.getSource(), shops,
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("info").executes(context -> info(context.getSource(), shops)))
                .then(Commands.literal("remove").executes(context -> remove(context.getSource(), shops)))
                .then(Commands.literal("reclaim").executes(context -> reclaim(context.getSource(), shops)))
                .then(Commands.literal("preview").executes(context -> preview(context.getSource(), shops)))
                .then(Commands.literal("admin")
                        .requires(source -> source.getSender().hasPermission("stall.admin"))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) ->
                                        {
                                            Bukkit.getOnlinePlayers().forEach(player -> builder.suggest(player.getName()));
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> give(context.getSource(),
                                                StringArgumentType.getString(context, "player"), 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, Tokens.MAX_PER_COMMAND))
                                                .executes(context -> give(context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount")))))))
                .build();
    }

    private static int help(CommandSourceStack source)
    {
        List<String> lines = List.of(
                "/stall token [amount] - get Stall tokens",
                "/stall list - list your shops",
                "/stall info - show the shop you're looking at",
                "/stall remove - remove the shop you're looking at",
                "/stall reclaim - collect tokens from shops removed while you were away",
                "/stall preview - see your own shops as a buyer does");
        CommandSender sender = source.getSender();
        sender.sendMessage(Component.text("Stall: use a token on a barrel to make it your shop. Sneak and right-click "
                + "your shop with an empty hand to set up offers.", NamedTextColor.GOLD));
        for (String line : lines)
        {
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
        return lines.size();
    }

    private static int token(CommandSourceStack source, int amount)
    {
        if (!(source.getExecutor() instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        PaperShops.giveTokens(player, amount);
        return succeed(source, (amount == 1 ? "Here's a Stall token." : "Here are " + amount + " Stall tokens.")
                + " Use one on a barrel to make it your shop.", amount);
    }

    private static int listOwn(CommandSourceStack source, PaperShops shops)
    {
        if (!(source.getExecutor() instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.isOwner(player.getUniqueId()));
        String limit = PaperShops.describeLimit(PaperShops.shopLimit(player));
        CommandSender sender = source.getSender();
        if (owned.isEmpty())
        {
            sender.sendMessage(Component.text("You don't own any shops (limit: " + limit + "). Use a Stall token on a "
                    + "barrel to make one."));
            return 0;
        }
        sender.sendMessage(Component.text("Your shops (" + owned.size() + ", limit: " + limit + "):", NamedTextColor.GOLD));
        sendShopLines(sender, owned);
        return owned.size();
    }

    private static int listFor(CommandSourceStack source, PaperShops shops, String name)
    {
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.ownerName().equalsIgnoreCase(name));
        if (owned.isEmpty())
        {
            return fail(source, name + " doesn't own any shops.");
        }
        source.getSender().sendMessage(Component.text(owned.getFirst().ownerName() + "'s shops (" + owned.size() + "):",
                NamedTextColor.GOLD));
        sendShopLines(source.getSender(), owned);
        return owned.size();
    }

    private static int info(CommandSourceStack source, PaperShops shops)
    {
        if (!(source.getExecutor() instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        Optional<Shop<ItemStack>> target = targetShop(player, shops);
        if (target.isEmpty())
        {
            return fail(source, "Look at a shop first.");
        }
        Shop<ItemStack> shop = target.get();
        CommandSender sender = source.getSender();
        sender.sendMessage(Component.text(shop.ownerName() + "'s shop at " + shop.location(), NamedTextColor.GOLD));
        if (shop.offers().isEmpty())
        {
            sender.sendMessage(Component.text(" No offers yet.", NamedTextColor.GRAY));
        }
        List<Offer<ItemStack>> offers = shop.offers();
        for (int i = 0; i < offers.size(); i++)
        {
            Offer<ItemStack> offer = offers.get(i);
            TextComponent.Builder line = Component.text()
                    .append(Component.text(" " + (i + 1) + ". ", NamedTextColor.GRAY))
                    .append(items(offer.costs().items()))
                    .append(Component.text(" → ", NamedTextColor.GRAY))
                    .append(items(offer.rewards().items()));
            if (!offer.isComplete())
            {
                line.append(Component.text(" (not set up yet)", NamedTextColor.DARK_GRAY));
            }
            sender.sendMessage(line.build());
        }
        return offers.size();
    }

    private static int remove(CommandSourceStack source, PaperShops shops)
    {
        if (!(source.getExecutor() instanceof Player player))
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
        return succeed(source, message, Command.SINGLE_SUCCESS);
    }

    private static int reclaim(CommandSourceStack source, PaperShops shops)
    {
        if (!(source.getExecutor() instanceof Player player))
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

    private static int preview(CommandSourceStack source, PaperShops shops)
    {
        if (!(source.getExecutor() instanceof Player player))
        {
            return fail(source, "Only players can do that.");
        }
        boolean on = shops.toggleBuyerPreview(player.getUniqueId());
        return succeed(source, on
                ? "Your own shops now open as they do for buyers. Use /stall preview again to stop."
                : "Your own shops open normally again.", on ? 1 : 0);
    }

    private static int give(CommandSourceStack source, String name, int amount)
    {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null)
        {
            return fail(source, name + " isn't online.");
        }
        PaperShops.giveTokens(target, amount);
        String tokens = amount == 1 ? "a Stall token" : amount + " Stall tokens";
        target.sendMessage(Component.text("You received " + tokens + ". Use one on a barrel to make it your shop.",
                NamedTextColor.GREEN));
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
            sender.sendMessage(Component.text(" " + shop.location() + " - "
                    + (complete == 1 ? "1 offer" : complete + " offers"), NamedTextColor.GRAY));
        }
    }

    private static Component items(List<OfferItem<ItemStack>> items)
    {
        if (items.isEmpty())
        {
            return Component.text("nothing yet", NamedTextColor.DARK_GRAY);
        }
        TextComponent.Builder text = Component.text();
        for (int i = 0; i < items.size(); i++)
        {
            if (i > 0)
            {
                text.append(Component.text(" + ", NamedTextColor.GRAY));
            }
            text.append(Component.text(items.get(i).amount() + "× ")).append(items.get(i).item().effectiveName());
        }
        return text.build();
    }

    private static int succeed(CommandSourceStack source, String message, int result)
    {
        source.getSender().sendMessage(Component.text(message, NamedTextColor.GREEN));
        return result;
    }

    private static int fail(CommandSourceStack source, String message)
    {
        source.getSender().sendMessage(Component.text(message, NamedTextColor.RED));
        return 0;
    }

    private static Optional<Shop<ItemStack>> targetShop(Player player, PaperShops shops)
    {
        Block block = player.getTargetBlockExact(REACH);
        return block == null ? Optional.empty() : shops.shopAt(block);
    }
}
