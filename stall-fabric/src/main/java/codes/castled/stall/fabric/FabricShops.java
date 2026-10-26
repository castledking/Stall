package codes.castled.stall.fabric;

import codes.castled.stall.menu.OfferEditor;
import codes.castled.stall.offer.Offer;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.ShopAnchor;
import codes.castled.stall.shop.ShopLimits;
import codes.castled.stall.shop.ShopLocation;
import codes.castled.stall.shop.ShopRegistry;
import codes.castled.stall.shop.ShopViewers;
import codes.castled.stall.shop.TokenReturns;
import codes.castled.stall.shop.Tokens;
import codes.castled.stall.store.ShopPersistence;
import codes.castled.stall.store.StockCodec;
import codes.castled.stall.store.StoreLog;
import codes.castled.stall.trade.ClickThrottle;
import codes.castled.stall.trade.SlotChange;
import codes.castled.stall.trade.SlotInventory;
import codes.castled.stall.trade.TradePlan;
import codes.castled.stall.trade.TradePlanner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * The server's shops and everything that happens to them: making and removing them, opening their menus,
 * checking a menu is still valid, editing offers and carrying out trades. Server thread only.
 */
final class FabricShops
{
    /** Buy clicks closer together than this are taken as an accidental double click. */
    private static final long BUY_COOLDOWN_TICKS = 4;

    private final ShopRegistry<ItemStack> registry = new ShopRegistry<>();
    private final TradePlanner<ItemStack> planner = new TradePlanner<>(FabricStackRules.INSTANCE);
    private final OfferEditor<ItemStack> editor = new OfferEditor<>(FabricStackRules.INSTANCE);
    private final ClickThrottle throttle = new ClickThrottle(BUY_COOLDOWN_TICKS);
    private final ShopViewers<StallMenu> viewers = new ShopViewers<>();
    private final TokenReturns tokenReturns = new TokenReturns();
    private final Set<UUID> buyerPreview = new HashSet<>();
    private final Queue<Runnable> endOfTick = new ArrayDeque<>();
    private final Logger logger;
    /** Large barrels' extra slots, one per barrel, shared by the owner's view and by trades. */
    private final Map<BlockEntity, ExtraStock> extraStocks = new HashMap<>();
    private ShopPersistence<ItemStack> persistence;
    private StockCodec<ItemStack> stockCodec;

    FabricShops(Logger logger)
    {
        this.logger = logger;
    }

    ShopRegistry<ItemStack> registry()
    {
        return registry;
    }

    TradePlanner<ItemStack> planner()
    {
        return planner;
    }

    OfferEditor<ItemStack> editor()
    {
        return editor;
    }

    static ShopLocation locate(Level level, BlockPos pos)
    {
        return new ShopLocation(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** The shop at {@code pos}, which may be either half of a double chest shop. */
    Optional<Shop<ItemStack>> shopAt(Level level, BlockPos pos)
    {
        Optional<Shop<ItemStack>> shop = registry.at(locate(level, pos));
        if (shop.isPresent() || !level.isLoaded(pos))
        {
            return shop;
        }
        BlockState state = level.getBlockState(pos);
        return FabricContainers.connected(pos, state).flatMap(half -> registry.at(locate(level, half)));
    }

    /**
     * The shop a block belonged to, after it was broken: the block is gone and a double chest's remaining
     * half has already become a single chest, so the other half is found from the broken block's state.
     */
    Optional<Shop<ItemStack>> shopOfBroken(Level level, BlockPos pos, BlockState broken)
    {
        Optional<Shop<ItemStack>> shop = registry.at(locate(level, pos));
        if (shop.isPresent() || !(broken.is(Blocks.CHEST) || broken.is(Blocks.TRAPPED_CHEST))
                || broken.getValue(ChestBlock.TYPE) == ChestType.SINGLE)
        {
            return shop;
        }
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(broken));
        return registry.at(locate(level, other));
    }

    /** The shop's location in {@code level}, if it is in that dimension and loaded. */
    private static Optional<BlockPos> anchor(Level level, Shop<ItemStack> shop)
    {
        ShopLocation location = shop.location();
        BlockPos pos = new BlockPos(location.x(), location.y(), location.z());
        return level.dimension().identifier().toString().equals(location.world()) && level.isLoaded(pos)
                ? Optional.of(pos) : Optional.empty();
    }

    /** The container block at the shop's location, which carries its marker. */
    Optional<BlockEntity> anchorEntity(Level level, Shop<ItemStack> shop)
    {
        return anchor(level, shop)
                .filter(pos -> FabricContainers.isSupported(level.getBlockState(pos)))
                .map(level::getBlockEntity);
    }

    /**
     * Where a shop's stock is: its container, a double chest's covering both halves, and for a large barrel
     * its extra slots after the barrel's own. Empty if its chunk is unloaded or the container is gone.
     */
    Optional<Container> stock(Level level, Shop<ItemStack> shop)
    {
        Optional<BlockPos> pos = anchor(level, shop);
        Optional<Container> container = pos.flatMap(at -> FabricContainers.container(level, at));
        if (container.isEmpty())
        {
            return Optional.empty();
        }
        if (shop.largeStock() && FabricContainers.isBarrel(level.getBlockState(pos.get()))
                && level.getBlockEntity(pos.get()) instanceof BlockEntity barrel)
        {
            ExtraStock extra = extraStock(shop, barrel);
            if (extra.isReadable())
            {
                return Optional.of(new CompoundContainer(container.get(), extra));
            }
        }
        return container;
    }

    /** Whether {@code player} is using their own shops as a buyer would, for testing. */
    boolean isPreviewing(UUID player)
    {
        return buyerPreview.contains(player);
    }

    /** Lets an owner use their own shops as a buyer would. Returns whether preview is now on. */
    boolean toggleBuyerPreview(UUID player)
    {
        if (buyerPreview.remove(player))
        {
            return false;
        }
        buyerPreview.add(player);
        return true;
    }

    // --- Saving ------------------------------------------------------------------------------------

    /**
     * Loads the saved shops from {@code <world>/stall}, in the world's own save folder so backups and
     * rollbacks keep shops and their barrels together. Runs once the worlds are loaded, before anyone joins.
     */
    void startStorage(MinecraftServer server)
    {
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("stall").toAbsolutePath().normalize();
        FabricItemCodec items = new FabricItemCodec(server.registryAccess());
        stockCodec = new StockCodec<>(items, FabricStackRules.INSTANCE);
        persistence = new ShopPersistence<>(directory, items, new StoreLog()
        {
            @Override
            public void info(String message)
            {
                logger.info(message);
            }

            @Override
            public void warn(String message, Throwable error)
            {
                logger.warn(message, error);
            }
        });
        ShopPersistence.LoadReport report = persistence.load(registry, tokenReturns);
        logger.info("Loaded {} shop(s) from {}.", report.shops(), directory);
        if (report.quarantined() > 0 || report.fromNewerVersion() > 0)
        {
            logger.warn("{} shop file(s) could not be loaded and were moved to shops/_broken; {} were written by a "
                    + "newer version of Stall and left alone.", report.quarantined(), report.fromNewerVersion());
        }
        sweep(server);
    }

    void sweep(MinecraftServer server)
    {
        // ExtraStock refers to its barrel for validity checks. Explicitly release replaced/unloaded
        // barrels; a weak-key map would still retain them through that reference in the value.
        for (BlockEntity barrel : List.copyOf(extraStocks.keySet()))
        {
            Level level = barrel.getLevel();
            if (level == null || !level.isLoaded(barrel.getBlockPos())
                    || level.getBlockEntity(barrel.getBlockPos()) != barrel || ShopMarkers.marker(barrel) == null)
            {
                closeStockViews(server, barrel);
                extraStocks.remove(barrel);
            }
        }
        for (ServerLevel level : server.getAllLevels())
        {
            for (Shop<ItemStack> shop : registry.all())
            {
                if (shop.location().world().equals(level.dimension().identifier().toString())) reconcile(level, shop);
            }
        }
    }

    void chunkLoaded(ServerLevel level, int x, int z)
    {
        endOfTick.add(() -> registry.inChunk(level.dimension().identifier().toString(), x, z)
                .forEach(shop -> reconcile(level, shop)));
    }

    void chunkUnloaded(ServerLevel level, int x, int z)
    {
        for (BlockEntity barrel : List.copyOf(extraStocks.keySet()))
        {
            if (barrel.getLevel() == level && barrel.getBlockPos().getX() >> 4 == x
                    && barrel.getBlockPos().getZ() >> 4 == z)
            {
                closeStockViews(level.getServer(), barrel);
                extraStocks.remove(barrel);
            }
        }
    }

    void reconcile(Level level, Shop<ItemStack> shop)
    {
        if (!registry.isCurrent(shop)) return;
        var location = shop.location();
        BlockPos pos = new BlockPos(location.x(), location.y(), location.z());
        if (!level.dimension().identifier().toString().equals(location.world()) || !level.isLoaded(pos)) return;
        boolean container = FabricContainers.isSupported(level.getBlockState(pos));
        BlockEntity blockEntity = container ? level.getBlockEntity(pos) : null;
        ShopAnchor.Status status = ShopAnchor.inspect(true, blockEntity != null,
                blockEntity != null ? ShopMarkers.marker(blockEntity) : null, shop.id());
        if (status != ShopAnchor.Status.ORPHANED || !registry.remove(shop)) return;
        synchronized (tokenReturns)
        {
            tokenReturns.refund(shop);
            if (persistence != null) persistence.archiveShop(shop, tokenReturns, "container missing or owned by another shop");
        }
        for (StallMenu menu : viewers.of(shop.id())) menu.viewer().closeContainer();
        logger.info("Archived orphaned shop {} at {}; its owner can collect any returned token with /stall reclaim.", shop.id(), location);
    }

    /** Writes every change still waiting. Runs as the server stops. */
    void stopStorage()
    {
        for (StallMenu menu : viewers.all())
        {
            menu.viewer().closeContainer();
        }
        if (persistence != null)
        {
            persistence.close();
            persistence = null;
        }
    }

    private void save(Shop<ItemStack> shop)
    {
        if (persistence != null)
        {
            persistence.saveShop(shop);
        }
    }

    private void saveReturns()
    {
        if (persistence != null)
        {
            persistence.saveReturns(tokenReturns);
        }
    }

    // --- Markers ------------------------------------------------------------------------------------

    /**
     * Whether the barrel at the shop's location carries the shop's marker. A shop whose barrel doesn't is
     * paused: it doesn't trade until its owner reactivates it.
     */
    boolean isActive(Level level, Shop<ItemStack> shop)
    {
        return anchorEntity(level, shop).map(container -> ShopMarkers.carries(container, shop)).orElse(false);
    }

    /**
     * Stamps the shop's marker back on its barrel after the barrel lost it: after a crash rolled the world
     * back, a switch between Paper and Fabric, or the barrel being replaced. Protection plugins have
     * already allowed the owner's click by the time this runs.
     */
    void reactivate(ServerPlayer player, Shop<ItemStack> shop)
    {
        Optional<BlockEntity> anchor = anchorEntity(player.level(), shop);
        if (anchor.isEmpty())
        {
            return;
        }
        BlockState state = anchor.get().getBlockState();
        ShopMarkers.stamp(anchor.get(), shop);
        shop.setContainer(FabricContainers.id(state));
        save(shop);
        player.sendSystemMessage(text("This shop was paused because its " + FabricContainers.describe(state)
                + " lost its Stall marker. It's open again.", ChatFormatting.GREEN));
        openOwnerMenu(player, shop);
    }

    // --- Making and removing shops ----------------------------------------------------------------

    /**
     * Turns the container at {@code pos} into {@code player}'s shop with the token they hold, then opens
     * the owner menu. Protection mods have already allowed the click by the time this runs.
     */
    void create(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack token)
    {
        BlockState state = level.getBlockState(pos);
        BlockEntity container = level.getBlockEntity(pos);
        if (!FabricContainers.isSupported(state) || container == null)
        {
            return;
        }
        String name = FabricContainers.describe(state);
        if (FabricContainers.container(level, pos).isEmpty())
        {
            player.sendSystemMessage(text("Both halves of this chest must be available before it can become a shop.", ChatFormatting.RED));
            return;
        }
        if (!StallPermissions.has(player, StallPermissions.CREATE))
        {
            player.sendSystemMessage(text("You don't have permission to make shops.", ChatFormatting.RED));
            return;
        }
        int limit = StallPermissions.shopLimit(player);
        long owned = registry.countOwnedBy(player.getUUID());
        if (owned >= limit)
        {
            player.sendSystemMessage(text(limit == 0
                    ? "You can't own shops."
                    : "You already own " + owned + " of your " + limit + " shops. Remove one first.", ChatFormatting.RED));
            return;
        }
        if (FabricContainers.hasUnopenedLoot(level, pos))
        {
            player.sendSystemMessage(text("This " + name + " still holds unopened loot. Open it first.", ChatFormatting.RED));
            return;
        }

        boolean consumed = !player.hasInfiniteMaterials();
        Shop<ItemStack> shop = new Shop<>(UUID.randomUUID(), player.getUUID(), player.getPlainTextName(),
                locate(level, pos), consumed);
        shop.setContainer(FabricContainers.id(state));
        boolean large = grantLargeStock(player, shop, state);
        registry.add(shop);
        ShopMarkers.stamp(container, shop);
        save(shop);
        if (consumed)
        {
            token.shrink(1);
        }
        player.sendSystemMessage(text("This " + name + " is now your shop" + (large ? ", with 54 slots of stock" : "")
                + ". Set up its offers here; sneak and right-click it with an empty hand to come back.", ChatFormatting.GREEN));
        openOwnerMenu(player, shop);
    }

    /** Where a removed shop's token went. */
    enum TokenReturn
    {
        /** The shop was made without using up a token, so there was none to give back. */
        NONE,
        /** Dropped where the barrel was, because the owner broke it. */
        DROPPED,
        /** Put in the owner's inventory. */
        GIVEN,
        /** Kept for the owner to collect with {@code /stall reclaim}, because they're offline. */
        OWED
    }

    /**
     * Removes {@code shop} and hands its token back to the owner: dropped where the barrel was if the
     * owner broke it, otherwise into their inventory, or kept for {@code /stall reclaim} if they're offline.
     *
     * @param actor who removed the shop, or {@code null} if nobody did
     * @param broken where the actor broke the shop's block, or {@code null} if it was removed with a command
     */
    TokenReturn remove(MinecraftServer server, Shop<ItemStack> shop, ServerPlayer actor, BlockPos broken)
    {
        if (!registry.remove(shop)) return TokenReturn.NONE;
        if (persistence != null)
        {
            persistence.deleteShop(shop);
        }
        for (StallMenu menu : viewers.of(shop.id()))
        {
            menu.viewer().closeContainer();
        }
        // A container left standing becomes an ordinary one again: after a command, or when one half of a
        // double chest was broken and the other half carries the marker.
        if (actor != null)
        {
            anchorEntity(actor.level(), shop)
                    .filter(container -> !container.getBlockPos().equals(broken) && ShopMarkers.carries(container, shop))
                    .ifPresent(container ->
                    {
                        closeStockViews(server, container);
                        extraStocks.remove(container);
                        ShopMarkers.clear(container);
                    });
        }
        if (!shop.tokenConsumed())
        {
            return TokenReturn.NONE;
        }
        boolean byOwner = actor != null && shop.isOwner(actor.getUUID());
        if (broken != null && byOwner)
        {
            Block.popResource(actor.level(), broken, FabricTokens.create(1));
            return TokenReturn.DROPPED;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(shop.owner());
        if (owner == null)
        {
            tokenReturns.add(shop.owner(), 1);
            saveReturns();
            return TokenReturn.OWED;
        }
        give(owner, FabricTokens.create(1));
        if (!byOwner)
        {
            owner.sendSystemMessage(text("Your shop at " + shop.location() + " was removed. Its token is back in "
                    + "your inventory.", ChatFormatting.YELLOW));
        }
        return TokenReturn.GIVEN;
    }

    /** Gives {@code count} tokens, a stack at a time. */
    static void giveTokens(ServerPlayer player, int count)
    {
        for (int left = count; left > 0; left -= Tokens.MAX_PER_COMMAND)
        {
            give(player, FabricTokens.create(Math.min(left, Tokens.MAX_PER_COMMAND)));
        }
    }

    int tokensOwed(UUID player)
    {
        return tokenReturns.owed(player);
    }

    /** Hands {@code player} every token they're owed; returns how many. */
    int reclaimTokens(ServerPlayer player)
    {
        int owed = tokenReturns.claim(player.getUUID());
        if (owed > 0)
        {
            saveReturns();
            giveTokens(player, owed);
        }
        return owed;
    }

    /** Puts {@code stack} in the player's inventory, dropping what doesn't fit at their feet. */
    static void give(ServerPlayer player, ItemStack stack)
    {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    long ownedBy(UUID player)
    {
        return registry.countOwnedBy(player);
    }

    static String describeLimit(int limit)
    {
        return limit == ShopLimits.UNLIMITED ? "no limit" : String.valueOf(limit);
    }

    // --- Menus --------------------------------------------------------------------------------------

    void openBuyerMenu(ServerPlayer player, Shop<ItemStack> shop)
    {
        open(player, shop, ItemStack.EMPTY, false);
    }

    void openOwnerMenu(ServerPlayer player, Shop<ItemStack> shop)
    {
        open(player, shop, ItemStack.EMPTY, true);
    }

    private void open(ServerPlayer player, Shop<ItemStack> shop, ItemStack carried, boolean owner)
    {
        IntFunction<StallMenu> factory = owner
                ? containerId -> new OwnerMenu(containerId, player, shop, carried, this)
                : containerId -> new BuyerMenu(containerId, player, shop, carried, this);
        Component title = Component.literal(owner ? "Edit " + shop.ownerName() + "'s Stall" : shop.ownerName() + "'s Stall");
        player.openMenu(new SimpleMenuProvider((containerId, inventory, opener) -> factory.apply(containerId), title));
        if (player.containerMenu instanceof StallMenu menu && menu.shop() == shop)
        {
            viewers.add(shop.id(), menu);
        }
        else if (!carried.isEmpty())
        {
            give(player, carried);
        }
    }

    void closed(StallMenu menu)
    {
        viewers.remove(menu.shop().id(), menu);
    }

    /**
     * A shop menu stays open while its shop exists and is active, its barrel is still there, and the player
     * is in reach.
     */
    boolean stillValid(StallMenu menu, Player player)
    {
        Shop<ItemStack> shop = menu.shop();
        if (!registry.isCurrent(shop))
        {
            return false;
        }
        return anchorEntity(player.level(), shop)
                .map(container -> ShopMarkers.carries(container, shop) && Container.stillValidBlockEntity(container, player))
                .orElse(false);
    }

    /**
     * Redraws every open shop menu, so stock the owner added or another buyer took shows up without
     * waiting for a click. Clicks never rely on what a menu shows; every trade is checked again.
     */
    void refreshOpenMenus()
    {
        for (StallMenu menu : viewers.all())
        {
            update(menu);
        }
    }

    /** Redraws every menu open on {@code shop}, after its stock or offers changed. */
    void refresh(Shop<ItemStack> shop)
    {
        for (StallMenu menu : viewers.of(shop.id()))
        {
            update(menu);
        }
    }

    /** Called after an owner changed {@code shop}'s offers. */
    void edited(Shop<ItemStack> shop)
    {
        save(shop);
        refresh(shop);
    }

    /** Redraws a menu, or reopens it at the end of the tick if the offers now need a different number of rows. */
    private void update(StallMenu menu)
    {
        if (menu.wantedRows() == menu.rows())
        {
            menu.render();
        }
        else if (menu.claimReopen())
        {
            endOfTick.add(() -> reopen(menu));
        }
    }

    /**
     * Replaces a menu with a fresh one of the right size. Reopening happens at the end of the tick, never
     * inside the click that caused it, and whatever the player holds on the cursor moves to the new menu.
     */
    private void reopen(StallMenu menu)
    {
        ServerPlayer player = menu.viewer();
        if (player.containerMenu != menu || !registry.isCurrent(menu.shop()))
        {
            return;
        }
        ItemStack carried = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        // Closing without telling the client lets it swap screens in place instead of closing the old one
        // first, which would re-center the mouse.
        player.doCloseContainer();
        open(player, menu.shop(), carried, menu instanceof OwnerMenu);
    }

    /** Runs what was put off until the end of the tick. */
    void endOfTick()
    {
        for (int pending = endOfTick.size(); pending > 0; pending--)
        {
            endOfTick.remove().run();
        }
    }

    void forget(UUID player)
    {
        throttle.forget(player);
        buyerPreview.remove(player);
    }

    // --- Trades -------------------------------------------------------------------------------------

    /**
     * Carries out one trade of offer {@code offerId} for the menu's viewer. Everything is checked again
     * against the live shop, barrel and inventory, the trade is planned on copies, and only a plan that
     * fits completely is written, all within this call.
     */
    void buy(BuyerMenu menu, UUID offerId)
    {
        ServerPlayer player = menu.viewer();
        ServerLevel level = player.level();
        if (!throttle.tryAcquire(player.getUUID(), level.getServer().getTickCount()))
        {
            return;
        }
        Shop<ItemStack> shop = menu.shop();
        // An invalid menu is closed by vanilla on the next tick, so there is nothing more to do here.
        if (!stillValid(menu, player))
        {
            return;
        }
        if (menu.revision() != shop.revision())
        {
            menu.render();
            player.sendSystemMessage(text("This shop just changed. Check its offers again.", ChatFormatting.YELLOW));
            return;
        }
        Optional<Offer<ItemStack>> offer = shop.offer(offerId);
        Optional<Container> stock = stock(level, shop);
        if (offer.isEmpty() || stock.isEmpty())
        {
            return;
        }

        TradePlan<ItemStack> plan = planner.plan(offer.get(), 1, buyerSlots(player), stockSlots(stock.get()));
        switch (plan)
        {
            case TradePlan.Failure<ItemStack> failure ->
                    player.sendSystemMessage(text(failure.reason().message(), ChatFormatting.RED));
            case TradePlan.Success<ItemStack> success ->
            {
                commit(player.getInventory(), success.buyerChanges());
                commit(stock.get(), success.stockChanges());
                player.sendSystemMessage(text("Trade complete.", ChatFormatting.GREEN));
            }
        }
        refresh(shop);
    }

    static SlotInventory<ItemStack> buyerSlots(ServerPlayer player)
    {
        Inventory inventory = player.getInventory();
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : inventory.getNonEquipmentItems())
        {
            copies.add(stack.copy());
        }
        return SlotInventory.playerStorage(copies, FabricStackRules.INSTANCE, inventory.getMaxStackSize());
    }

    static SlotInventory<ItemStack> stockSlots(Container container)
    {
        List<ItemStack> copies = new ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            copies.add(container.getItem(slot).copy());
        }
        return SlotInventory.of(copies, FabricStackRules.INSTANCE, container.getMaxStackSize());
    }

    /** Writes a plan's changes. Slots 0 to 35 of a player's inventory are its storage slots, as planned. */
    private static void commit(Container container, List<SlotChange<ItemStack>> changes)
    {
        for (SlotChange<ItemStack> change : changes)
        {
            container.setItem(change.slot(), change.stack() == null ? ItemStack.EMPTY : change.stack());
        }
        container.setChanged();
    }

    static Component text(String message, ChatFormatting color)
    {
        return Component.literal(message).withStyle(color);
    }

    // --- Large barrels ------------------------------------------------------------------------------

    /** The extra slots of a large barrel, read from the barrel the first time they're needed. */
    private ExtraStock extraStock(Shop<ItemStack> shop, BlockEntity barrel)
    {
        return extraStocks.computeIfAbsent(barrel, entity -> new ExtraStock(entity, entity.getAttached(ShopMarkers.STOCK),
                text ->
                {
                    entity.setAttached(ShopMarkers.STOCK, text);
                    entity.setChanged();
                },
                stockCodec, () -> registry.isCurrent(shop) && ShopMarkers.carries(entity, shop), logger));
    }

    /**
     * Turns on large stock for a barrel shop whose owner has {@code stall.barrel.large}: at creation, and
     * whenever they open the shop later, so shops they made before getting the permission grow too. It is
     * never turned off. Returns whether the shop has large stock.
     */
    boolean grantLargeStock(ServerPlayer owner, Shop<ItemStack> shop, BlockState state)
    {
        if (!shop.largeStock() && FabricContainers.isBarrel(state) && StallPermissions.has(owner, StallPermissions.LARGE_BARREL))
        {
            shop.enableLargeStock();
            save(shop);
        }
        return shop.largeStock() && FabricContainers.isBarrel(state);
    }

    /**
     * Opens a large barrel's stock for its owner: the barrel's own 27 slots on top and the extra 27 below,
     * in one vanilla menu whose slots are the live containers. Protection mods have already allowed the
     * click. Returns whether a view opened.
     */
    boolean openStock(ServerPlayer player, Shop<ItemStack> shop)
    {
        Optional<BlockEntity> anchor = anchorEntity(player.level(), shop)
                .filter(container -> FabricContainers.isBarrel(container.getBlockState()));
        if (anchor.isEmpty() || !(anchor.get() instanceof Container barrel))
        {
            return false;
        }
        ExtraStock extra = extraStock(shop, anchor.get());
        if (!extra.isReadable())
        {
            player.sendSystemMessage(text("This shop's extra slots were saved by a newer version of Stall, so they "
                    + "can't be opened here.", ChatFormatting.RED));
            return false;
        }
        Container both = new CompoundContainer(barrel, extra);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, opener) -> ChestMenu.sixRows(containerId, inventory, both),
                Component.literal("Large Barrel")));
        return true;
    }

    /** Closes every large barrel view open on {@code barrel}. */
    private void closeStockViews(MinecraftServer server, BlockEntity barrel)
    {
        ExtraStock extra = extraStocks.get(barrel);
        if (extra == null)
        {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (player.containerMenu instanceof ChestMenu menu && menu.getContainer() instanceof CompoundContainer both
                    && both.contains(extra))
            {
                player.closeContainer();
            }
        }
    }

    /** Whether a large barrel's extra slots hold nothing; a shop that isn't one always does. */
    boolean extraStockEmpty(Level level, Shop<ItemStack> shop)
    {
        if (!shop.largeStock())
        {
            return true;
        }
        return anchorEntity(level, shop)
                .filter(container -> FabricContainers.isBarrel(container.getBlockState()))
                .map(barrel -> extraStock(shop, barrel).holdsNothing())
                .orElse(true);
    }

    /** Drops a large barrel's extra slots where it was broken. {@code barrel} is the broken block's entity. */
    void dropExtraStock(MinecraftServer server, Level level, Shop<ItemStack> shop, BlockPos pos, BlockState state,
            BlockEntity barrel)
    {
        if (!shop.largeStock() || barrel == null || !FabricContainers.isBarrel(state)
                || !anchor(level, shop).map(pos::equals).orElse(false))
        {
            return;
        }
        // Nobody may keep putting items into slots that are about to be dropped.
        closeStockViews(server, barrel);
        ExtraStock extra = extraStocks.containsKey(barrel) ? extraStocks.remove(barrel) : extraStock(shop, barrel);
        extraStocks.remove(barrel);
        Containers.dropContents(level, pos, extra);
        if (extra.holdsUnreadable())
        {
            logger.warn("The large barrel of shop {} at {} was broken while holding items this server can't read; "
                    + "they were lost with it.", shop.id(), shop.location());
        }
    }
}
