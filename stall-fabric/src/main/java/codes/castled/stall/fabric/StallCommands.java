package codes.castled.stall.fabric;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.Tokens;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** {@code /stall} and its subcommands. */
final class StallCommands
{
    private StallCommands()
    {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, FabricShops shops)
    {
        dispatcher.register(Commands.literal("stall")
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("token")
                        .requires(source -> StallPermissions.has(source, StallPermissions.TOKEN))
                        .executes(context -> token(context.getSource(), 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, Tokens.MAX_PER_COMMAND))
                                .executes(context -> token(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "amount")))))
                .then(Commands.literal("list")
                        .executes(context -> listOwn(context.getSource(), shops))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(source -> StallPermissions.has(source, StallPermissions.ADMIN))
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        shops.registry().all().stream().map(Shop::ownerName).distinct().toList(), builder))
                                .executes(context -> listFor(context.getSource(), shops,
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("info").executes(context -> info(context.getSource(), shops)))
                .then(Commands.literal("remove").executes(context -> remove(context.getSource(), shops)))
                .then(Commands.literal("reclaim").executes(context -> reclaim(context.getSource(), shops)))
                .then(Commands.literal("preview").executes(context -> preview(context.getSource(), shops)))
                .then(Commands.literal("admin")
                        .requires(source -> StallPermissions.has(source, StallPermissions.ADMIN))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.asList(context.getSource().getServer().getPlayerNames()), builder))
                                        .executes(context -> give(context.getSource(),
                                                StringArgumentType.getString(context, "player"), 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, Tokens.MAX_PER_COMMAND))
                                                .executes(context -> give(context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))));
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
        source.sendSuccess(() -> Component.literal("Stall: use a token on a barrel to make it your shop. Sneak and "
                + "right-click your shop with an empty hand to set up offers.").withStyle(ChatFormatting.GOLD), false);
        for (String line : lines)
        {
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return lines.size();
    }

    private static int token(CommandSourceStack source, int amount) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        FabricShops.giveTokens(player, amount);
        source.sendSuccess(() -> success((amount == 1 ? "Here's a Stall token." : "Here are " + amount + " Stall tokens.")
                + " Use one on a barrel to make it your shop."), false);
        return amount;
    }

    private static int listOwn(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.isOwner(player.getUUID()));
        String limit = FabricShops.describeLimit(StallPermissions.shopLimit(player));
        if (owned.isEmpty())
        {
            source.sendSuccess(() -> Component.literal("You don't own any shops (limit: " + limit + "). Use a Stall token "
                    + "on a barrel to make one."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Your shops (" + owned.size() + ", limit: " + limit + "):")
                .withStyle(ChatFormatting.GOLD), false);
        sendShopLines(source, owned);
        return owned.size();
    }

    private static int listFor(CommandSourceStack source, FabricShops shops, String name)
    {
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.ownerName().equalsIgnoreCase(name));
        if (owned.isEmpty())
        {
            source.sendFailure(Component.literal(name + " doesn't own any shops."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(owned.getFirst().ownerName() + "'s shops (" + owned.size() + "):")
                .withStyle(ChatFormatting.GOLD), false);
        sendShopLines(source, owned);
        return owned.size();
    }

    private static int info(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Shop<ItemStack>> target = targetShop(player, shops);
        if (target.isEmpty())
        {
            source.sendFailure(Component.literal("Look at a shop first."));
            return 0;
        }
        Shop<ItemStack> shop = target.get();
        source.sendSuccess(() -> Component.literal(shop.ownerName() + "'s shop at " + shop.location())
                .withStyle(ChatFormatting.GOLD), false);
        if (shop.offers().isEmpty())
        {
            source.sendSuccess(() -> Component.literal(" No offers yet.").withStyle(ChatFormatting.GRAY), false);
        }
        List<Offer<ItemStack>> offers = shop.offers();
        for (int i = 0; i < offers.size(); i++)
        {
            Offer<ItemStack> offer = offers.get(i);
            MutableComponent line = Component.literal(" " + (i + 1) + ". ").withStyle(ChatFormatting.GRAY)
                    .append(items(offer.costs().items()))
                    .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                    .append(items(offer.rewards().items()));
            if (!offer.isComplete())
            {
                line.append(Component.literal(" (not set up yet)").withStyle(ChatFormatting.DARK_GRAY));
            }
            source.sendSuccess(() -> line, false);
        }
        return offers.size();
    }

    private static int remove(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Shop<ItemStack>> target = targetShop(player, shops);
        if (target.isEmpty())
        {
            source.sendFailure(Component.literal("Look at a shop first."));
            return 0;
        }
        Shop<ItemStack> shop = target.get();
        boolean own = shop.isOwner(player.getUUID());
        if (!own && !StallPermissions.has(player, StallPermissions.ADMIN))
        {
            source.sendFailure(Component.literal("This shop belongs to " + shop.ownerName() + "."));
            return 0;
        }
        FabricShops.TokenReturn token = shops.remove(source.getServer(), shop, player, false);
        String message = own ? "Removed your shop." : "Removed " + shop.ownerName() + "'s shop.";
        message += switch (token)
        {
            case NONE, DROPPED -> "";
            case GIVEN -> own ? " Its token is back in your inventory." : " Its token went back to them.";
            case OWED -> " They'll get its token back with /stall reclaim.";
        };
        String result = message;
        source.sendSuccess(() -> success(result), false);
        return 1;
    }

    private static int reclaim(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        int owed = shops.reclaimTokens(player);
        if (owed == 0)
        {
            source.sendFailure(Component.literal("You have no tokens waiting."));
            return 0;
        }
        source.sendSuccess(() -> success(owed == 1 ? "Here's your Stall token back." : "Here are your " + owed
                + " Stall tokens back."), false);
        return owed;
    }

    private static int preview(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        boolean on = shops.toggleBuyerPreview(player.getUUID());
        source.sendSuccess(() -> success(on
                ? "Your own shops now open as they do for buyers. Use /stall preview again to stop."
                : "Your own shops open normally again."), false);
        return on ? 1 : 0;
    }

    private static int give(CommandSourceStack source, String name, int amount)
    {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
        if (target == null)
        {
            source.sendFailure(Component.literal(name + " isn't online."));
            return 0;
        }
        FabricShops.giveTokens(target, amount);
        String tokens = amount == 1 ? "a Stall token" : amount + " Stall tokens";
        target.sendSystemMessage(success("You received " + tokens + ". Use one on a barrel to make it your shop."));
        source.sendSuccess(() -> success("Gave " + target.getPlainTextName() + " " + tokens + "."), true);
        return amount;
    }

    private static List<Shop<ItemStack>> ownedBy(FabricShops shops, Predicate<Shop<ItemStack>> filter)
    {
        return shops.registry().all().stream()
                .filter(filter)
                .sorted(Comparator.comparing((Shop<ItemStack> shop) -> shop.location().world())
                        .thenComparingInt(shop -> shop.location().x())
                        .thenComparingInt(shop -> shop.location().z()))
                .toList();
    }

    private static void sendShopLines(CommandSourceStack source, List<Shop<ItemStack>> shops)
    {
        for (Shop<ItemStack> shop : shops)
        {
            int complete = shop.completeOffers().size();
            source.sendSuccess(() -> Component.literal(" " + shop.location() + " - "
                    + (complete == 1 ? "1 offer" : complete + " offers")).withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static MutableComponent items(List<OfferItem<ItemStack>> items)
    {
        MutableComponent text = Component.empty();
        if (items.isEmpty())
        {
            return text.append(Component.literal("nothing yet").withStyle(ChatFormatting.DARK_GRAY));
        }
        for (int i = 0; i < items.size(); i++)
        {
            if (i > 0)
            {
                text.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY));
            }
            text.append(Component.literal(items.get(i).amount() + "× ")).append(items.get(i).item().getHoverName());
        }
        return text;
    }

    private static Component success(String message)
    {
        return Component.literal(message).withStyle(ChatFormatting.GREEN);
    }

    private static Optional<Shop<ItemStack>> targetShop(ServerPlayer player, FabricShops shops)
    {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit))
        {
            return Optional.empty();
        }
        return shops.shopAt(player.level(), blockHit.getBlockPos());
    }
}
