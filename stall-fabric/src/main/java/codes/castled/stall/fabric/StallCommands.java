package codes.castled.stall.fabric;

import codes.castled.stall.config.StallLang;
import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.Tokens;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
                .executes(context -> help(context.getSource(), shops))
                .then(Commands.literal("token")
                        .requires(source -> StallPermissions.has(source, StallPermissions.TOKEN))
                        .executes(context -> token(context.getSource(), shops, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, Tokens.MAX_PER_COMMAND))
                                .executes(context -> token(context.getSource(), shops,
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
                        .then(Commands.literal("reload")
                                .executes(context -> reload(context.getSource(), shops)))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.asList(context.getSource().getServer().getPlayerNames()), builder))
                                        .executes(context -> give(context.getSource(), shops,
                                                StringArgumentType.getString(context, "player"), 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, Tokens.MAX_PER_COMMAND))
                                                .executes(context -> give(context.getSource(), shops,
                                                        StringArgumentType.getString(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))));
    }

    private static int help(CommandSourceStack source, FabricShops shops)
    {
        StallLang lang = shops.lang();
        FabricText.sendSuccess(source, lang.chat("commands.help.header"), false);
        List<String> lines = lang.lines("commands.help.lines");
        for (String line : lines)
        {
            FabricText.sendSuccess(source, line, false);
        }
        return lines.size();
    }

    private static int token(CommandSourceStack source, FabricShops shops, int amount) throws CommandSyntaxException
    {
        int max = shops.config().maxTokensPerCommand();
        if (amount < 1 || amount > max)
        {
            FabricText.sendFailure(source, shops.lang().chat("commands.invalid-amount",
                    "max", String.valueOf(max)));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        shops.giveTokens(player, amount);
        FabricText.sendSuccess(source, shops.lang().chat(
                amount == 1 ? "commands.token.one" : "commands.token.many",
                "amount", String.valueOf(amount)), false);
        return amount;
    }

    private static int listOwn(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        StallLang lang = shops.lang();
        ServerPlayer player = source.getPlayerOrException();
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.isOwner(player.getUUID()));
        String limit = shops.describeLimit(StallPermissions.shopLimit(player, shops.config()));
        if (owned.isEmpty())
        {
            FabricText.sendSuccess(source, lang.chat("commands.list.none-own", "limit", limit), false);
            return 0;
        }
        FabricText.sendSuccess(source, lang.chat("commands.list.own-header",
                "count", String.valueOf(owned.size()), "limit", limit), false);
        sendShopLines(source, lang, owned);
        return owned.size();
    }

    private static int listFor(CommandSourceStack source, FabricShops shops, String name)
    {
        StallLang lang = shops.lang();
        List<Shop<ItemStack>> owned = ownedBy(shops, shop -> shop.ownerName().equalsIgnoreCase(name));
        if (owned.isEmpty())
        {
            FabricText.sendFailure(source, lang.chat("commands.list.none-other", "player", name));
            return 0;
        }
        FabricText.sendSuccess(source, lang.chat("commands.list.other-header",
                "owner", owned.getFirst().ownerName(), "count", String.valueOf(owned.size())), false);
        sendShopLines(source, lang, owned);
        return owned.size();
    }

    private static int info(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        StallLang lang = shops.lang();
        ServerPlayer player = source.getPlayerOrException();
        Optional<Shop<ItemStack>> target = targetShop(player, shops);
        if (target.isEmpty())
        {
            FabricText.sendFailure(source, lang.chat("commands.look-at-shop"));
            return 0;
        }
        Shop<ItemStack> shop = target.get();
        FabricText.sendSuccess(source, lang.chat("commands.info.header",
                "owner", shop.ownerName(),
                "location", shop.location().toString(),
                "container", lang.container(shop.container(), shop.largeStock())), false);
        if (shop.offers().isEmpty())
        {
            FabricText.sendSuccess(source, lang.text("commands.info.no-offers"), false);
        }
        List<Offer<ItemStack>> offers = shop.offers();
        for (int i = 0; i < offers.size(); i++)
        {
            Offer<ItemStack> offer = offers.get(i);
            MutableComponent line = FabricText.chat(lang.text("commands.info.offer-prefix",
                    "index", String.valueOf(i + 1)))
                    .append(items(lang, offer.costs().items()))
                    .append(FabricText.chat(lang.text("commands.info.offer-arrow")))
                    .append(items(lang, offer.rewards().items()));
            if (!offer.isComplete())
            {
                line.append(FabricText.chat(lang.text("commands.info.incomplete-suffix")));
            }
            source.sendSuccess(() -> line, false);
        }
        return offers.size();
    }

    private static int remove(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        StallLang lang = shops.lang();
        ServerPlayer player = source.getPlayerOrException();
        Optional<Shop<ItemStack>> target = targetShop(player, shops);
        if (target.isEmpty())
        {
            FabricText.sendFailure(source, lang.chat("commands.look-at-shop"));
            return 0;
        }
        Shop<ItemStack> shop = target.get();
        boolean own = shop.isOwner(player.getUUID());
        if (!own && !StallPermissions.has(player, StallPermissions.ADMIN))
        {
            FabricText.sendFailure(source, lang.chat("shops.belongs-to", "owner", shop.ownerName()));
            return 0;
        }
        if (!shops.extraStockEmpty(player.level(), shop))
        {
            FabricText.sendFailure(source, lang.chat("commands.remove.extra-stock-not-empty"));
            return 0;
        }
        FabricShops.TokenReturn token = shops.remove(source.getServer(), shop, player, null);
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
        FabricText.sendSuccess(source, message, false);
        return 1;
    }

    private static int reclaim(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        StallLang lang = shops.lang();
        ServerPlayer player = source.getPlayerOrException();
        int owed = shops.reclaimTokens(player);
        if (owed == 0)
        {
            FabricText.sendFailure(source, lang.chat("commands.reclaim.none"));
            return 0;
        }
        FabricText.sendSuccess(source, lang.chat(owed == 1 ? "commands.reclaim.one" : "commands.reclaim.many",
                "count", String.valueOf(owed)), false);
        return owed;
    }

    private static int preview(CommandSourceStack source, FabricShops shops) throws CommandSyntaxException
    {
        StallLang lang = shops.lang();
        ServerPlayer player = source.getPlayerOrException();
        boolean on = shops.toggleBuyerPreview(player.getUUID());
        FabricText.sendSuccess(source, lang.chat(on ? "commands.preview.enabled" : "commands.preview.disabled"),
                false);
        return on ? 1 : 0;
    }

    private static int reload(CommandSourceStack source, FabricShops shops)
    {
        shops.reloadConfig();
        FabricText.sendSuccess(source, shops.lang().chat("commands.reload"), true);
        return 1;
    }

    private static int give(CommandSourceStack source, FabricShops shops, String name, int amount)
    {
        StallLang lang = shops.lang();
        int max = shops.config().maxTokensPerCommand();
        if (amount < 1 || amount > max)
        {
            FabricText.sendFailure(source, lang.chat("commands.invalid-amount", "max", String.valueOf(max)));
            return 0;
        }
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(name);
        if (target == null)
        {
            FabricText.sendFailure(source, lang.chat("commands.player-offline", "player", name));
            return 0;
        }
        shops.giveTokens(target, amount);
        String tokens = lang.text(amount == 1 ? "commands.give.token-one" : "commands.give.token-many",
                "amount", String.valueOf(amount));
        FabricText.send(target, lang.chat("commands.give.received",
                "tokens", tokens, "amount", String.valueOf(amount)));
        FabricText.sendSuccess(source, lang.chat("commands.give.sent",
                "player", target.getPlainTextName(), "tokens", tokens, "amount", String.valueOf(amount)), true);
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

    private static void sendShopLines(CommandSourceStack source, StallLang lang, List<Shop<ItemStack>> shops)
    {
        for (Shop<ItemStack> shop : shops)
        {
            int complete = shop.completeOffers().size();
            FabricText.sendSuccess(source, lang.text(
                    complete == 1 ? "commands.list.entry-one" : "commands.list.entry-many",
                    "location", shop.location().toString(),
                    "offers", String.valueOf(complete)), false);
        }
    }

    private static MutableComponent items(StallLang lang, List<OfferItem<ItemStack>> items)
    {
        if (items.isEmpty())
        {
            return FabricText.chat(lang.text("commands.info.nothing-yet"));
        }
        MutableComponent text = Component.empty();
        for (int i = 0; i < items.size(); i++)
        {
            if (i > 0)
            {
                text.append(FabricText.chat(lang.text("commands.info.item-separator")));
            }
            text.append(FabricText.chat(lang.text("commands.info.item-amount",
                    "amount", String.valueOf(items.get(i).amount()))))
                    .append(items.get(i).item().getHoverName());
        }
        return text;
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
