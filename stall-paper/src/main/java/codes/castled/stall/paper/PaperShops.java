package codes.castled.stall.paper;

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
import codes.castled.stall.store.StoreLog;
import codes.castled.stall.trade.ClickThrottle;
import codes.castled.stall.trade.SlotChange;
import codes.castled.stall.trade.SlotInventory;
import codes.castled.stall.trade.TradePlan;
import codes.castled.stall.trade.TradePlanner;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The server's shops and everything that happens to them: making and removing them, opening their menus,
 * checking a menu is still valid, editing offers and carrying out trades. World and inventory operations run on their owning server region.
 */
final class PaperShops
{
    /** Buy clicks closer together than this are taken as an accidental double click. */
    private static final long BUY_COOLDOWN_TICKS = 4;

    /**
     * How far from the barrel a shop menu stays open, matching vanilla's container reach: the block
     * interaction range of 4.5 plus vanilla's allowance of 4, measured to the barrel's center.
     */
    private static final double MAX_DISTANCE_SQUARED = 9.0 * 9.0;

    private final Plugin plugin;
    private final ShopRegistry<ItemStack> registry = new ShopRegistry<>();
    private final TradePlanner<ItemStack> planner = new TradePlanner<>(PaperStackRules.INSTANCE);
    private final OfferEditor<ItemStack> editor = new OfferEditor<>(PaperStackRules.INSTANCE);
    private final ClickThrottle throttle = new ClickThrottle(BUY_COOLDOWN_TICKS);
    private final ShopViewers<StallMenu> viewers = new ShopViewers<>();
    private final TokenReturns tokenReturns = new TokenReturns();
    private final Set<UUID> buyerPreview = ConcurrentHashMap.newKeySet();
    /** Large barrels' 54-slot stock views, by viewer. */
    private final Map<UUID, StockView> stockViews = new ConcurrentHashMap<>();
    private ShopPersistence<ItemStack> persistence;
    private PaperExtraStock extraStock;
    private final AtomicLong currentTick = new AtomicLong();
    private final SchedulerFacade scheduler;

    PaperShops(Plugin plugin)
    {
        this.plugin = plugin;
        this.scheduler = new SchedulerFacade(plugin);
    }

    SchedulerFacade scheduler() { return scheduler; }

    /** A trade may touch both inventories only while this thread owns the player and the shop chunk. */
    boolean canAccess(Player player, Shop<ItemStack> shop)
    {
        if (!registry.isCurrent(shop) || !FoliaSupport.owns(player) || !player.isOnline()) return false;
        ShopLocation location = shop.location();
        World world = player.getWorld();
        return world.getKey().toString().equals(location.world())
                && FoliaSupport.owns(world, location.x() >> 4, location.z() >> 4);
    }

    private void close(StallMenu menu)
    {
        scheduler.entity(menu.viewer(), () ->
        {
            if (menu.viewer().getOpenInventory().getTopInventory() == menu.getInventory()) menu.viewer().closeInventory();
        });
    }

    /** Only the metadata scan is global. Reading a block is always scheduled on its own region. */
    void sweep()
    {
        for (Shop<ItemStack> shop : registry.all())
        {
            World world = Bukkit.getWorlds().stream().filter(loaded -> loaded.getKey().toString().equals(shop.location().world()))
                    .findFirst().orElse(null);
            if (world != null) scheduler.region(world, shop.location().x() >> 4, shop.location().z() >> 4, () -> reconcile(shop));
        }
    }

    void chunkLoaded(World world, int x, int z)
    {
        scheduler.afterChunkLoad(world, x, z, () -> registry.inChunk(world.getKey().toString(), x, z).forEach(this::reconcile));
    }

    void reconcile(Shop<ItemStack> shop)
    {
        if (!registry.isCurrent(shop)) return;
        Optional<Block> anchor = block(shop);
        if (anchor.isEmpty()) return; // Unloaded or foreign region: leave the record alone.
        Block block = anchor.get();
        ShopAnchor.Status status = ShopAnchor.inspect(true, PaperContainers.isSupported(block),
                PaperMarkers.marker(block), shop.id());
        if (status == ShopAnchor.Status.PAUSED && extraStock != null)
        {
            // A replacement barrel doesn't hold the old one's extra slots, just as it doesn't hold its items.
            extraStock.forget(shop.id());
        }
        if (status != ShopAnchor.Status.ORPHANED || !registry.remove(shop)) return;
        if (extraStock != null) extraStock.forget(shop.id());
        closeStockViews(shop);
        synchronized (tokenReturns)
        {
            tokenReturns.refund(shop);
            if (persistence != null) persistence.archiveShop(shop, tokenReturns, "container missing or owned by another shop");
        }
        viewers.of(shop.id()).forEach(this::close);
        plugin.getLogger().info("Archived orphaned shop " + shop.id() + " at " + shop.location()
                + "; its owner can collect any returned token with /stall reclaim.");
    }

    /** A scheduler-driven clock, unaffected by time commands and available on both Spigot and Paper. */
    void tick()
    {
        long tick = currentTick.incrementAndGet();
        if (tick % 5 == 0) closeInvalidMenus();
        if (tick % 20 == 0) refreshOpenMenus();
        if (tick % 200 == 0) sweep();
    }

    long currentTick()
    {
        return currentTick.get();
    }

    // --- Saving ------------------------------------------------------------------------------------

    /**
     * Loads the saved shops from {@code <world>/stall}, in the main world's own save folder so backups and
     * rollbacks keep shops and their barrels together. Runs on enable, once the worlds are loaded.
     */
    void startStorage()
    {
        Path directory = levelDirectory().resolve("stall");
        PaperItemCodec items = new PaperItemCodec();
        extraStock = new PaperExtraStock(plugin, items);
        if (!CombinedInventories.available())
        {
            plugin.getLogger().log(Level.WARNING, "Large barrels are unavailable on this server: their owners can't "
                    + "open the extra slots. Trades still use them.", CombinedInventories.failure());
        }
        persistence = new ShopPersistence<>(directory, items, new StoreLog()
        {
            @Override
            public void info(String message)
            {
                plugin.getLogger().info(message);
            }

            @Override
            public void warn(String message, Throwable error)
            {
                plugin.getLogger().log(Level.WARNING, message, error);
            }
        });
        ShopPersistence.LoadReport report = persistence.load(registry, tokenReturns);
        plugin.getLogger().info("Loaded " + report.shops() + " shop(s) from " + directory + ".");
        if (report.quarantined() > 0 || report.fromNewerVersion() > 0)
        {
            plugin.getLogger().warning(report.quarantined() + " shop file(s) could not be loaded and were moved to "
                    + "shops/_broken; " + report.fromNewerVersion() + " were written by a newer version of Stall and "
                    + "left alone.");
        }
    }

    /** Writes every change still waiting. Runs on disable. */
    void stopStorage()
    {
        flushAllExtraStock();
        if (persistence != null)
        {
            persistence.close();
            persistence = null;
        }
    }

    /**
     * The main world's save folder. Since Minecraft 26.1 every dimension, the overworld included, is saved
     * under {@code <world>/dimensions/<namespace>/<path>}, and Paper reports a world's folder as that
     * dimension folder, so the save folder is three levels up from the overworld's.
     */
    private static Path levelDirectory()
    {
        Path overworld = Bukkit.getWorlds().getFirst().getWorldFolder().toPath().toAbsolutePath().normalize();
        Path dimension = Path.of("dimensions", "minecraft", "overworld");
        return overworld.endsWith(dimension) ? overworld.getParent().getParent().getParent() : overworld;
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

    /** The block at the shop's location, if its world and chunk are loaded. */
    Optional<Block> block(Shop<ItemStack> shop)
    {
        ShopLocation location = shop.location();
        NamespacedKey key = NamespacedKey.fromString(location.world());
        World world = key == null ? null : Bukkit.getWorlds().stream()
                .filter(loaded -> loaded.getKey().equals(key)).findFirst().orElse(null);
        if (world == null || !FoliaSupport.owns(world, location.x() >> 4, location.z() >> 4)
                || !world.isChunkLoaded(location.x() >> 4, location.z() >> 4))
        {
            return Optional.empty();
        }
        return Optional.of(world.getBlockAt(location.x(), location.y(), location.z()));
    }

    /**
     * Whether the barrel at the shop's location carries the shop's marker. A shop whose barrel doesn't is
     * paused: it doesn't trade until its owner reactivates it.
     */
    boolean isActive(Shop<ItemStack> shop)
    {
        return block(shop).map(block -> PaperMarkers.carries(block, shop)).orElse(false);
    }

    /**
     * Stamps the shop's marker back on its barrel after the barrel lost it: after a crash rolled the world
     * back, a switch between Paper and Fabric, or the barrel being replaced. Protection plugins have
     * already allowed the owner's click by the time this runs.
     */
    void reactivate(Player player, Shop<ItemStack> shop)
    {
        if (!canAccess(player, shop)) return;
        Optional<Block> anchor = block(shop).filter(PaperContainers::isSupported);
        if (anchor.isEmpty()) return;
        Block block = anchor.get();
        if (extraStock != null) extraStock.forget(shop.id());
        PaperMarkers.stamp(block, shop);
        shop.setContainer(PaperContainers.id(block));
        save(shop);
        player.sendMessage(BukkitItems.text("This shop was paused because its " + PaperContainers.describe(block)
                + " lost its Stall marker. It's open again.", ChatColor.GREEN));
        scheduler.laterEntity(player, () ->
        {
            if (player.isOnline() && registry.isCurrent(shop))
            {
                openOwnerMenu(player, shop);
            }
        });
    }

    ShopRegistry<ItemStack> registry()
    {
        return registry;
    }

    PaperExtraStock extraStock()
    {
        return extraStock;
    }

    TradePlanner<ItemStack> planner()
    {
        return planner;
    }

    OfferEditor<ItemStack> editor()
    {
        return editor;
    }

    int tokensOwed(UUID player)
    {
        return tokenReturns.owed(player);
    }

    /** Hands {@code player} every token they're owed; returns how many. */
    int reclaimTokens(Player player)
    {
        int owed;
        synchronized (tokenReturns)
        {
            owed = tokenReturns.claim(player.getUniqueId());
            if (owed > 0) saveReturns();
        }
        if (owed > 0) giveTokens(player, owed);
        return owed;
    }

    static ShopLocation locate(Block block)
    {
        return new ShopLocation(block.getWorld().getKey().toString(), block.getX(), block.getY(), block.getZ());
    }

    /** The shop at {@code block}, which may be either half of a double chest shop. */
    Optional<Shop<ItemStack>> shopAt(Block block)
    {
        Optional<Shop<ItemStack>> shop = registry.at(locate(block));
        if (shop.isPresent() || !PaperContainers.isSupported(block))
        {
            return shop;
        }
        return PaperContainers.connected(block).flatMap(half -> registry.at(locate(half)));
    }

    /**
     * Where a shop's stock is: its container's live inventory, a double chest's covering both halves, and
     * for a large barrel its extra slots after the barrel's own. Empty if its chunk is unloaded or the
     * container is gone.
     */
    Optional<PaperStock> stock(Shop<ItemStack> shop)
    {
        Optional<Block> anchor = block(shop);
        Optional<Inventory> inventory = anchor.flatMap(PaperContainers::inventory);
        if (inventory.isEmpty())
        {
            return Optional.empty();
        }
        Block block = anchor.get();
        if (shop.largeStock() && extraStock != null && PaperContainers.isBarrel(block))
        {
            Inventory extra = extraStock.inventory(shop, block);
            if (extra != null)
            {
                return Optional.of(new PaperStock(List.of(inventory.get(), extra), true));
            }
        }
        return Optional.of(PaperStock.of(inventory.get()));
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

    static int shopLimit(Permissible player)
    {
        return ShopLimits.limitFor(path -> player.hasPermission("stall." + path));
    }

    static String describeLimit(int limit)
    {
        return limit == ShopLimits.UNLIMITED ? "no limit" : String.valueOf(limit);
    }

    // --- Making and removing shops ----------------------------------------------------------------

    /**
     * Turns the container at {@code block} into {@code player}'s shop with the token in their main hand,
     * then opens the owner menu. Protection plugins have already allowed the click by the time this runs.
     */
    void create(Player player, Block block)
    {
        if (!FoliaSupport.owns(player) || !FoliaSupport.owns(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) return;
        if (!player.hasPermission("stall.create"))
        {
            player.sendMessage(BukkitItems.text("You don't have permission to make shops.", ChatColor.RED));
            return;
        }
        int limit = shopLimit(player);
        long owned = registry.countOwnedBy(player.getUniqueId());
        if (owned >= limit)
        {
            player.sendMessage(BukkitItems.text(limit == 0
                    ? "You can't own shops."
                    : "You already own " + owned + " of your " + limit + " shops. Remove one first.", ChatColor.RED));
            return;
        }
        String name = PaperContainers.describe(block);
        if (PaperContainers.inventory(block).isEmpty())
        {
            player.sendMessage(BukkitItems.text("Both halves of this chest must be available before it can become a shop.", ChatColor.RED));
            return;
        }
        if (PaperContainers.hasUnopenedLoot(block))
        {
            player.sendMessage(BukkitItems.text("This " + name + " still holds unopened loot. Open it first.", ChatColor.RED));
            return;
        }

        boolean consumed = player.getGameMode() != GameMode.CREATIVE;
        Shop<ItemStack> shop = new Shop<>(UUID.randomUUID(), player.getUniqueId(), player.getName(), locate(block), consumed);
        shop.setContainer(PaperContainers.id(block));
        boolean large = grantLargeStock(player, shop, block);
        registry.add(shop);
        PaperMarkers.stamp(block, shop);
        save(shop);
        if (consumed)
        {
            ItemStack token = player.getInventory().getItemInMainHand();
            token.setAmount(token.getAmount() - 1);
            player.getInventory().setItemInMainHand(token.getAmount() > 0 ? token : null);
        }
        player.sendMessage(BukkitItems.text("This " + name + " is now your shop" + (large ? ", with 54 slots of stock" : "")
                + ". Set up its offers here; sneak and right-click it with an empty hand to come back.", ChatColor.GREEN));
        // Opening an inventory from the interact event itself is fine, but wait a tick so the click is done.
        scheduler.laterEntity(player, () ->
        {
            if (player.isOnline() && registry.isCurrent(shop))
            {
                openOwnerMenu(player, shop);
            }
        });
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
     * @param broken the block the actor broke, or {@code null} if the shop was removed with a command
     */
    TokenReturn remove(Shop<ItemStack> shop, Player actor, Block broken)
    {
        if (actor != null && !canAccess(actor, shop)) return TokenReturn.NONE;
        if (!registry.remove(shop)) return TokenReturn.NONE;
        if (persistence != null)
        {
            persistence.deleteShop(shop);
        }
        for (StallMenu menu : viewers.of(shop.id()))
        {
            close(menu);
        }
        closeStockViews(shop);
        if (extraStock != null) extraStock.forget(shop.id());
        // A container left standing becomes an ordinary one again: after a command, or when one half of a
        // double chest was broken and the other half carries the marker.
        block(shop).filter(block -> !block.equals(broken) && PaperMarkers.carries(block, shop))
                .ifPresent(PaperMarkers::clear);
        if (!shop.tokenConsumed())
        {
            return TokenReturn.NONE;
        }
        boolean byOwner = actor != null && shop.isOwner(actor.getUniqueId());
        if (broken != null && byOwner)
        {
            broken.getWorld().dropItemNaturally(broken.getLocation().add(0.5, 0.5, 0.5), PaperTokens.create(1));
            return TokenReturn.DROPPED;
        }
        Player owner = Bukkit.getPlayer(shop.owner());
        if (owner == null)
        {
            synchronized (tokenReturns)
            {
                tokenReturns.add(shop.owner(), 1);
                saveReturns();
            }
            return TokenReturn.OWED;
        }
        if (!FoliaSupport.owns(owner))
        {
            synchronized (tokenReturns)
            {
                tokenReturns.add(shop.owner(), 1);
                saveReturns();
            }
            scheduler.entity(owner, () ->
            {
                if (!owner.isOnline()) return;
                synchronized (tokenReturns)
                {
                    if (!tokenReturns.claimOne(shop.owner())) return;
                    saveReturns();
                }
                give(owner, PaperTokens.create(1));
                owner.sendMessage(BukkitItems.text("Your shop was removed. Its token is back in your inventory.", ChatColor.YELLOW));
            });
            return TokenReturn.OWED;
        }
        give(owner, PaperTokens.create(1));
        if (!byOwner)
        {
            owner.sendMessage(BukkitItems.text("Your shop at " + shop.location() + " was removed. Its token is back in "
                    + "your inventory.", ChatColor.YELLOW));
        }
        return TokenReturn.GIVEN;
    }

    /** Gives {@code count} tokens, a stack at a time. */
    static void giveTokens(Player player, int count)
    {
        for (int left = count; left > 0; left -= Tokens.MAX_PER_COMMAND)
        {
            give(player, PaperTokens.create(Math.min(left, Tokens.MAX_PER_COMMAND)));
        }
    }

    /** Puts {@code stack} in the player's inventory, dropping what doesn't fit at their feet. */
    static void give(Player player, ItemStack stack)
    {
        for (ItemStack leftover : player.getInventory().addItem(stack).values())
        {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    // --- Menus --------------------------------------------------------------------------------------

    void openBuyerMenu(Player player, Shop<ItemStack> shop)
    {
        if (!canAccess(player, shop)) return;
        open(player, new BuyerMenu(this, player, shop));
    }

    void openOwnerMenu(Player player, Shop<ItemStack> shop)
    {
        if (!canAccess(player, shop)) return;
        open(player, new OwnerMenu(this, player, shop));
    }

    private void open(Player player, StallMenu menu)
    {
        player.openInventory(menu.getInventory());
        // Another plugin may have cancelled the opening; only a menu that is really open is tracked.
        if (player.getOpenInventory().getTopInventory() == menu.getInventory())
        {
            viewers.add(menu.shop().id(), menu);
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
    boolean stillValid(StallMenu menu)
    {
        Shop<ItemStack> shop = menu.shop();
        Player player = menu.viewer();
        if (!canAccess(player, shop) || stock(shop).isEmpty() || !isActive(shop))
        {
            return false;
        }
        ShopLocation location = shop.location();
        if (!player.getWorld().getKey().toString().equals(location.world()))
        {
            return false;
        }
        Block block = player.getWorld().getBlockAt(location.x(), location.y(), location.z());
        return player.getEyeLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) <= MAX_DISTANCE_SQUARED;
    }

    /**
     * Closes menus that are no longer valid. Vanilla does this for its own containers every tick; a
     * plugin inventory has to be checked by the plugin.
     */
    void closeInvalidMenus()
    {
        for (StallMenu menu : viewers.all())
        {
            scheduler.entity(menu.viewer(), () ->
            {
                if (!stillValid(menu)) close(menu);
            });
        }
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

    /**
     * Closes every shop menu, so none outlives the plugin that cancels its clicks, and every large barrel
     * view, so none outlives the plugin that saves its extra slots.
     */
    void closeAll()
    {
        for (StallMenu menu : viewers.all())
        {
            close(menu);
        }
        stockViews.values().stream().map(StockView::shop).distinct().forEach(this::closeStockViews);
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

    /** Redraws a menu, or reopens it next tick if the offers now need a different number of rows. */
    private void update(StallMenu menu)
    {
        scheduler.entity(menu.viewer(), () -> updateOwned(menu));
    }

    private void updateOwned(StallMenu menu)
    {
        if (!stillValid(menu))
        {
            close(menu);
            return;
        }
        if (menu.wantedRows() == menu.rows())
        {
            menu.render();
        }
        else if (menu.claimReopen())
        {
            // Opening an inventory inside the click that caused this is unsafe, so it waits a tick.
            scheduler.laterEntity(menu.viewer(), () -> reopen(menu));
        }
    }

    /** Replaces a menu with a fresh one of the right size, moving whatever the player holds on the cursor. */
    private void reopen(StallMenu menu)
    {
        Player player = menu.viewer();
        if (player.getOpenInventory().getTopInventory() != menu.getInventory() || !stillValid(menu))
        {
            return;
        }
        ItemStack cursor = player.getItemOnCursor();
        player.setItemOnCursor(null);
        open(player, menu instanceof OwnerMenu
                ? new OwnerMenu(this, player, menu.shop())
                : new BuyerMenu(this, player, menu.shop()));
        if (!BukkitItems.isEmpty(cursor))
        {
            player.setItemOnCursor(cursor);
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
        Player player = menu.viewer();
        if (!throttle.tryAcquire(player.getUniqueId(), currentTick()))
        {
            return;
        }
        Shop<ItemStack> shop = menu.shop();
        // Closing an inventory inside its own click event is unsafe; closeInvalidMenus closes it shortly.
        if (!stillValid(menu))
        {
            return;
        }
        if (menu.revision() != shop.revision())
        {
            menu.render();
            player.sendMessage(BukkitItems.text("This shop just changed. Check its offers again.", ChatColor.YELLOW));
            return;
        }
        Optional<Offer<ItemStack>> offer = shop.offer(offerId);
        Optional<PaperStock> stock = stock(shop);
        if (offer.isEmpty() || stock.isEmpty())
        {
            return;
        }

        TradePlan<ItemStack> plan = planner.plan(offer.get(), 1, buyerSlots(player), stock.get().slots());
        switch (plan)
        {
            case TradePlan.Failure<ItemStack> failure ->
                    player.sendMessage(BukkitItems.text(failure.reason().message(), ChatColor.RED));
            case TradePlan.Success<ItemStack> success ->
            {
                commit(player.getInventory(), success.buyerChanges());
                stock.get().commit(success.stockChanges());
                if (stock.get().large())
                {
                    extraStock.markDirty(shop);
                    block(shop).ifPresent(barrel -> extraStock.flush(shop, barrel));
                }
                player.sendMessage(BukkitItems.text("Trade complete.", ChatColor.GREEN));
            }
        }
        refresh(shop);
    }

    static SlotInventory<ItemStack> buyerSlots(Player player)
    {
        PlayerInventory inventory = player.getInventory();
        return SlotInventory.playerStorage(copies(inventory.getStorageContents()), PaperStackRules.INSTANCE,
                inventory.getMaxStackSize());
    }

    /** Inventories hand out live mirrors of their stacks, so the planner gets clones. */
    private static List<ItemStack> copies(ItemStack[] stacks)
    {
        List<ItemStack> copies = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks)
        {
            copies.add(stack == null ? null : stack.clone());
        }
        return copies;
    }

    /** Writes a plan's changes. Slots 0 to 35 of a player's inventory are its storage slots, as planned. */
    private static void commit(Inventory inventory, List<SlotChange<ItemStack>> changes)
    {
        for (SlotChange<ItemStack> change : changes)
        {
            inventory.setItem(change.slot(), change.stack());
        }
    }

    // --- Large barrels ------------------------------------------------------------------------------

    /** A large barrel's 54-slot view open for a player. */
    private record StockView(Shop<ItemStack> shop, Inventory inventory)
    {
    }

    /**
     * Turns on large stock for a barrel shop whose owner has {@code stall.barrel.large}: at creation, and
     * whenever they open the shop later, so shops they made before getting the permission grow too. It is
     * never turned off. Returns whether the shop has large stock.
     */
    boolean grantLargeStock(Player owner, Shop<ItemStack> shop, Block block)
    {
        if (!shop.largeStock() && PaperContainers.isBarrel(block) && CombinedInventories.available()
                && owner.hasPermission("stall.barrel.large"))
        {
            shop.enableLargeStock();
            save(shop);
        }
        return shop.largeStock() && PaperContainers.isBarrel(block);
    }

    /**
     * Opens a large barrel's stock for its owner: the barrel's own 27 slots on top and the extra 27 below,
     * in one vanilla menu whose slots are the live inventories. Protection plugins have already allowed
     * the click. Returns whether a view opened.
     */
    boolean openStock(Player player, Shop<ItemStack> shop)
    {
        Optional<Block> anchor = block(shop).filter(PaperContainers::isBarrel);
        if (!canAccess(player, shop) || anchor.isEmpty() || extraStock == null || !CombinedInventories.available())
        {
            return false;
        }
        Block barrel = anchor.get();
        Inventory extra = extraStock.inventory(shop, barrel);
        Optional<Inventory> own = PaperContainers.inventory(barrel);
        if (extra == null || own.isEmpty())
        {
            player.sendMessage(BukkitItems.text("This shop's extra slots were saved by a newer version of Stall, so "
                    + "they can't be opened here.", ChatColor.RED));
            return false;
        }
        Inventory combined = CombinedInventories.of(own.get(), extra);
        InventoryView view = player.openInventory(combined);
        // Another plugin may have cancelled the opening; only a view that is really open is tracked.
        if (view == null || view.getTopInventory() != combined)
        {
            return false;
        }
        view.setTitle("Large Barrel");
        stockViews.put(player.getUniqueId(), new StockView(shop, combined));
        return true;
    }

    /** Whether {@code inventory} is the large barrel view open for {@code player}. */
    boolean isStockView(Player player, Inventory inventory)
    {
        StockView view = stockViews.get(player.getUniqueId());
        return view != null && view.inventory() == inventory;
    }

    /**
     * Called for every click and drag in a large barrel view: the extra slots are written back to the
     * barrel on the next tick, once the click has been applied.
     */
    void stockViewChanged(Player player)
    {
        StockView view = stockViews.get(player.getUniqueId());
        if (view == null || !extraStock.markDirty(view.shop()))
        {
            return;
        }
        ShopLocation location = view.shop().location();
        World world = player.getWorld();
        scheduler.region(world, location.x() >> 4, location.z() >> 4,
                () -> block(view.shop()).ifPresent(barrel -> extraStock.flush(view.shop(), barrel)));
    }

    void stockViewClosed(Player player, Inventory inventory)
    {
        StockView view = stockViews.get(player.getUniqueId());
        if (view != null && view.inventory() == inventory && stockViews.remove(player.getUniqueId(), view))
        {
            extraStock.markDirty(view.shop());
            block(view.shop()).ifPresent(barrel -> extraStock.flush(view.shop(), barrel));
        }
    }

    /**
     * Closes every large barrel view open on {@code shop}. Closing saves the view's extra slots, through
     * {@link #stockViewClosed}, before anything else happens to them.
     */
    private void closeStockViews(Shop<ItemStack> shop)
    {
        for (Map.Entry<UUID, StockView> open : stockViews.entrySet())
        {
            StockView view = open.getValue();
            if (view.shop() != shop)
            {
                continue;
            }
            Player viewer = Bukkit.getPlayer(open.getKey());
            if (viewer == null)
            {
                stockViews.remove(open.getKey(), view);
                continue;
            }
            scheduler.entity(viewer, () ->
            {
                if (viewer.getOpenInventory().getTopInventory() == view.inventory()) viewer.closeInventory();
                stockViews.remove(open.getKey(), view);
            });
        }
    }

    /** Whether a large barrel's extra slots hold nothing; a shop that isn't one always does. */
    boolean extraStockEmpty(Shop<ItemStack> shop)
    {
        if (!shop.largeStock() || extraStock == null)
        {
            return true;
        }
        return block(shop).filter(PaperContainers::isBarrel).map(barrel -> extraStock.isEmpty(shop, barrel)).orElse(true);
    }

    /** Drops a large barrel's extra slots where it was broken, before the barrel goes. */
    void dropExtraStock(Shop<ItemStack> shop, Block broken)
    {
        if (shop.largeStock() && extraStock != null && PaperContainers.isBarrel(broken)
                && block(shop).filter(broken::equals).isPresent())
        {
            // Nobody may keep putting items into slots that are about to be dropped.
            closeStockViews(shop);
            extraStock.dropContents(shop, broken);
        }
    }

    /** Writes back the extra slots of every large barrel in a chunk that is unloading. */
    void chunkUnloading(World world, int x, int z)
    {
        if (extraStock != null)
        {
            extraStock.unload(registry.inChunk(world.getKey().toString(), x, z), this::block);
        }
    }

    private void flushAllExtraStock()
    {
        if (extraStock == null)
        {
            return;
        }
        for (UUID id : extraStock.loadedShops())
        {
            registry.all().stream().filter(shop -> shop.id().equals(id)).findFirst()
                    .ifPresent(shop -> block(shop).ifPresent(barrel ->
                    {
                        extraStock.markDirty(shop);
                        extraStock.flush(shop, barrel);
                    }));
        }
    }
}
