package codes.castled.stall.fabric;

import codes.castled.stall.shop.Shop;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class StallFabric implements ModInitializer
{
    public static final String MOD_ID = "stall";
    private static final Logger LOGGER = LoggerFactory.getLogger("Stall");

    /**
     * Runs before the default phase, where protection mods such as GriefPrevention3D listen, so buyers
     * can use shops inside claims they are not trusted in. A buyer menu grants no access to the container
     * itself; only the owner opens that, and their click passes through to protection as usual.
     */
    private static final Identifier SHOP_ACCESS_PHASE = Identifier.fromNamespaceAndPath(MOD_ID, "shop_access");

    /**
     * Runs after the default phase, so a protection mod that refuses the click stops it before a token
     * can turn a container into a shop: a shop can only go where its owner could open the container anyway.
     * A large barrel's 54-slot view opens here too, for the same reason.
     */
    private static final Identifier CREATE_PHASE = Identifier.fromNamespaceAndPath(MOD_ID, "create");

    /** How often open shop menus are redrawn to show stock that changed meanwhile. */
    private static final int MENU_REFRESH_TICKS = 20;

    private final FabricShops shops = new FabricShops(LOGGER);

    @Override
    public void onInitialize()
    {
        ShopMarkers.register();
        ShopProtection.install(shops);
        ServerLifecycleEvents.SERVER_STARTED.register(shops::startStorage);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> shops.stopStorage());
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> shops.chunkLoaded(level, chunk.getPos().x(), chunk.getPos().z()));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> shops.chunkUnloaded(level, chunk.getPos().x(), chunk.getPos().z()));

        UseBlockCallback.EVENT.addPhaseOrdering(SHOP_ACCESS_PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, CREATE_PHASE);
        UseBlockCallback.EVENT.register(SHOP_ACCESS_PHASE, (player, level, hand, hit) ->
        {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator())
            {
                return InteractionResult.PASS;
            }
            Optional<Shop<ItemStack>> shop = shops.shopAt(level, hit.getBlockPos());
            shop.ifPresent(found -> shops.reconcile(level, found));
            if (shop.isEmpty() || !shops.registry().isCurrent(shop.get())
                    || !FabricContainers.isSupported(level.getBlockState(hit.getBlockPos())))
            {
                return InteractionResult.PASS;
            }
            boolean previewing = shops.isPreviewing(player.getUUID());
            boolean owner = shop.get().isOwner(player.getUUID()) && !previewing;
            boolean manager = owner || (!previewing && StallPermissions.has(serverPlayer, StallPermissions.ADMIN));
            boolean emptyHanded = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
            boolean sneaking = player.isSecondaryUseActive();
            // The marker is on the half at the shop's location, which for a double chest may not be this one.
            if (!shops.isActive(level, shop.get()))
            {
                // A paused shop stays shut to buyers, which keeps its stock safe. Its owner's clicks go on to
                // protection and vanilla; a sneak-click reactivates it in the create phase below.
                if (manager)
                {
                    return InteractionResult.PASS;
                }
                serverPlayer.sendSystemMessage(FabricShops.text("This shop is paused until its owner opens it again.",
                        ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            if (manager && sneaking && emptyHanded)
            {
                shops.openOwnerMenu(serverPlayer, shop.get());
                return InteractionResult.SUCCESS;
            }
            // The owner's other clicks open the container to restock it, through protection as usual; a large
            // barrel's 54-slot view opens in the create phase, once protection has allowed the click.
            if (owner)
            {
                return InteractionResult.PASS;
            }
            // Sneaking with an item in hand places it against the container instead of opening it, so let
            // that through. Sneaking empty-handed still opens containers in vanilla, so it gets the menu.
            if (sneaking && !emptyHanded)
            {
                return InteractionResult.PASS;
            }
            // Either way the container stays shut: a buyer never reaches the stock itself.
            if (StallPermissions.has(serverPlayer, StallPermissions.USE))
            {
                shops.openBuyerMenu(serverPlayer, shop.get());
            }
            else
            {
                serverPlayer.sendSystemMessage(FabricShops.text(
                        "You don't have permission to use shops.", ChatFormatting.RED));
            }
            return InteractionResult.SUCCESS;
        });

        UseBlockCallback.EVENT.register(CREATE_PHASE, (player, level, hand, hit) ->
        {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()
                    || hand != InteractionHand.MAIN_HAND)
            {
                return InteractionResult.PASS;
            }
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (!FabricContainers.isSupported(state))
            {
                return InteractionResult.PASS;
            }
            Optional<Shop<ItemStack>> existing = shops.shopAt(level, hit.getBlockPos());
            if (existing.isPresent())
            {
                Shop<ItemStack> shop = existing.get();
                boolean previewing = shops.isPreviewing(player.getUUID());
                boolean owner = shop.isOwner(player.getUUID()) && !previewing;
                boolean manager = owner || (!previewing && StallPermissions.has(serverPlayer, StallPermissions.ADMIN));
                boolean emptyHanded = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
                boolean active = shops.isActive(level, shop);
                // Reactivating a paused shop: protection has allowed this click, so the owner can open the container.
                if (manager && player.isSecondaryUseActive() && emptyHanded && !active)
                {
                    shops.reactivate(serverPlayer, shop);
                    return InteractionResult.SUCCESS;
                }
                // The owner restocking: a large barrel opens as one 54-slot view instead of the barrel's own 27.
                if (owner && active && !player.isSecondaryUseActive() && shops.grantLargeStock(serverPlayer, shop, state)
                        && shops.openStock(serverPlayer, shop))
                {
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            if (!FabricTokens.isToken(held))
            {
                return InteractionResult.PASS;
            }
            shops.create(serverPlayer, (ServerLevel) level, hit.getBlockPos(), held);
            return InteractionResult.SUCCESS;
        });

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
        {
            Optional<Shop<ItemStack>> shop = shops.shopAt(level, pos);
            if (shop.isEmpty() || shop.get().isOwner(player.getUUID())
                    || !(player instanceof ServerPlayer serverPlayer)
                    || StallPermissions.has(serverPlayer, StallPermissions.ADMIN))
            {
                return true;
            }
            player.sendSystemMessage(FabricShops.text(
                    "This shop belongs to " + shop.get().ownerName() + ".", ChatFormatting.RED));
            return false;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
        {
            if (player instanceof ServerPlayer serverPlayer)
            {
                shops.shopOfBroken(level, pos, state).ifPresent(found ->
                {
                    shops.dropExtraStock(serverPlayer.level().getServer(), level, found, pos, state, blockEntity);
                    shops.remove(serverPlayer.level().getServer(), found, serverPlayer, pos);
                });
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server ->
        {
            shops.endOfTick();
            if (server.getTickCount() % 200 == 0) shops.sweep(server);
            if (server.getTickCount() % MENU_REFRESH_TICKS == 0)
            {
                shops.refreshOpenMenus();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
        {
            int owed = shops.tokensOwed(handler.player.getUUID());
            if (owed > 0)
            {
                handler.player.sendSystemMessage(FabricShops.text((owed == 1 ? "A Stall token is" : owed + " Stall tokens are")
                        + " waiting for you from removed shops. Use /stall reclaim to collect.", ChatFormatting.YELLOW));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> shops.forget(handler.player.getUUID()));

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                StallCommands.register(dispatcher, shops));

        LOGGER.info("Stall loaded.");
    }
}
