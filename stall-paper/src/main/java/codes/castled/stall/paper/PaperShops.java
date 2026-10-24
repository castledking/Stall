package codes.castled.stall.paper;

import codes.castled.stall.menu.OfferEditor;
import codes.castled.stall.offer.Offer;
import codes.castled.stall.shop.Shop;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The server's shops and everything that happens to them: making and removing them, opening their menus,
 * checking a menu is still valid, editing offers and carrying out trades. Main thread only.
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
    private final Set<UUID> buyerPreview = new HashSet<>();
    private ShopPersistence<ItemStack> persistence;

    PaperShops(Plugin plugin)
    {
        this.plugin = plugin;
    }

    // --- Saving ------------------------------------------------------------------------------------

    /**
     * Loads the saved shops from {@code <world>/stall}, in the main world's own save folder so backups and
     * rollbacks keep shops and their barrels together. Runs on enable, once the worlds are loaded.
     */
    void startStorage()
    {
        Path directory = levelDirectory().resolve("stall");
        persistence = new ShopPersistence<>(directory, new PaperItemCodec(), new StoreLog()
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
        Path overworld = Bukkit.getWorlds().getFirst().getWorldPath().toAbsolutePath().normalize();
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
        World world = key == null ? null : Bukkit.getWorld(key);
        if (world == null || !world.isChunkLoaded(location.x() >> 4, location.z() >> 4))
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
    void reactivate(Player player, Block block, Shop<ItemStack> shop)
    {
        PaperMarkers.stamp(block, shop);
        player.sendMessage(Component.text("This shop was paused because its barrel lost its Stall marker. It's open "
                + "again.", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTask(plugin, () ->
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
        int owed = tokenReturns.claim(player.getUniqueId());
        if (owed > 0)
        {
            saveReturns();
            giveTokens(player, owed);
        }
        return owed;
    }

    static ShopLocation locate(Block block)
    {
        return new ShopLocation(block.getWorld().getKey().toString(), block.getX(), block.getY(), block.getZ());
    }

    Optional<Shop<ItemStack>> shopAt(Block block)
    {
        return registry.at(locate(block));
    }

    /** The barrel holding a shop's stock, or empty if its chunk is unloaded or the barrel is gone. */
    Optional<Inventory> stock(Shop<ItemStack> shop)
    {
        ShopLocation location = shop.location();
        NamespacedKey key = NamespacedKey.fromString(location.world());
        World world = key == null ? null : Bukkit.getWorld(key);
        if (world == null || !world.isChunkLoaded(location.x() >> 4, location.z() >> 4))
        {
            return Optional.empty();
        }
        Block block = world.getBlockAt(location.x(), location.y(), location.z());
        return block.getState(false) instanceof Barrel barrel ? Optional.of(barrel.getInventory()) : Optional.empty();
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
     * Turns {@code block}'s barrel into {@code player}'s shop with the token in their main hand, then opens
     * the owner menu. Protection plugins have already allowed the click by the time this runs.
     */
    void create(Player player, Block block, Barrel barrel)
    {
        if (!player.hasPermission("stall.create"))
        {
            player.sendMessage(Component.text("You don't have permission to make shops.", NamedTextColor.RED));
            return;
        }
        int limit = shopLimit(player);
        long owned = registry.countOwnedBy(player.getUniqueId());
        if (owned >= limit)
        {
            player.sendMessage(Component.text(limit == 0
                    ? "You can't own shops."
                    : "You already own " + owned + " of your " + limit + " shops. Remove one first.", NamedTextColor.RED));
            return;
        }
        if (barrel.hasLootTable())
        {
            player.sendMessage(Component.text("This barrel still holds unopened loot. Open it first.", NamedTextColor.RED));
            return;
        }

        boolean consumed = player.getGameMode() != GameMode.CREATIVE;
        Shop<ItemStack> shop = new Shop<>(UUID.randomUUID(), player.getUniqueId(), player.getName(), locate(block), consumed);
        registry.add(shop);
        PaperMarkers.stamp(block, shop);
        save(shop);
        if (consumed)
        {
            ItemStack token = player.getInventory().getItemInMainHand();
            token.setAmount(token.getAmount() - 1);
            player.getInventory().setItemInMainHand(token.getAmount() > 0 ? token : null);
        }
        player.sendMessage(Component.text("This barrel is now your shop. Set up its offers here; sneak and right-click "
                + "it with an empty hand to come back.", NamedTextColor.GREEN));
        // Opening an inventory from the interact event itself is fine, but wait a tick so the click is done.
        Bukkit.getScheduler().runTask(plugin, () ->
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
     * @param broken whether the actor broke the barrel, rather than removing the shop with a command
     */
    TokenReturn remove(Shop<ItemStack> shop, Player actor, boolean broken)
    {
        registry.remove(shop);
        if (persistence != null)
        {
            persistence.deleteShop(shop);
        }
        for (StallMenu menu : viewers.of(shop.id()))
        {
            menu.viewer().closeInventory();
        }
        // A barrel left standing becomes an ordinary barrel again.
        if (!broken)
        {
            block(shop).filter(block -> PaperMarkers.carries(block, shop)).ifPresent(PaperMarkers::clear);
        }
        if (!shop.tokenConsumed())
        {
            return TokenReturn.NONE;
        }
        boolean byOwner = actor != null && shop.isOwner(actor.getUniqueId());
        if (broken && byOwner)
        {
            ShopLocation location = shop.location();
            Block block = actor.getWorld().getBlockAt(location.x(), location.y(), location.z());
            actor.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(), PaperTokens.create(1));
            return TokenReturn.DROPPED;
        }
        Player owner = Bukkit.getPlayer(shop.owner());
        if (owner == null)
        {
            tokenReturns.add(shop.owner(), 1);
            saveReturns();
            return TokenReturn.OWED;
        }
        give(owner, PaperTokens.create(1));
        if (!byOwner)
        {
            owner.sendMessage(Component.text("Your shop at " + shop.location() + " was removed. Its token is back in "
                    + "your inventory.", NamedTextColor.YELLOW));
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
        open(player, new BuyerMenu(this, player, shop));
    }

    void openOwnerMenu(Player player, Shop<ItemStack> shop)
    {
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
        if (!registry.isCurrent(shop) || !player.isOnline() || stock(shop).isEmpty() || !isActive(shop))
        {
            return false;
        }
        ShopLocation location = shop.location();
        if (!player.getWorld().getKey().toString().equals(location.world()))
        {
            return false;
        }
        Block block = player.getWorld().getBlockAt(location.x(), location.y(), location.z());
        return player.getEyeLocation().distanceSquared(block.getLocation().toCenterLocation()) <= MAX_DISTANCE_SQUARED;
    }

    /**
     * Closes menus that are no longer valid. Vanilla does this for its own containers every tick; a
     * plugin inventory has to be checked by the plugin.
     */
    void closeInvalidMenus()
    {
        for (StallMenu menu : viewers.all())
        {
            if (!stillValid(menu))
            {
                menu.viewer().closeInventory();
            }
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

    /** Closes every shop menu, so none outlives the plugin that cancels its clicks. */
    void closeAll()
    {
        for (StallMenu menu : viewers.all())
        {
            menu.viewer().closeInventory();
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

    /** Redraws a menu, or reopens it next tick if the offers now need a different number of rows. */
    private void update(StallMenu menu)
    {
        if (menu.wantedRows() == menu.rows())
        {
            menu.render();
        }
        else if (menu.claimReopen())
        {
            // Opening an inventory inside the click that caused this is unsafe, so it waits a tick.
            Bukkit.getScheduler().runTask(plugin, () -> reopen(menu));
        }
    }

    /** Replaces a menu with a fresh one of the right size, moving whatever the player holds on the cursor. */
    private void reopen(StallMenu menu)
    {
        Player player = menu.viewer();
        if (player.getOpenInventory().getTopInventory() != menu.getInventory() || !registry.isCurrent(menu.shop()))
        {
            return;
        }
        ItemStack cursor = player.getItemOnCursor();
        player.setItemOnCursor(null);
        open(player, menu instanceof OwnerMenu
                ? new OwnerMenu(this, player, menu.shop())
                : new BuyerMenu(this, player, menu.shop()));
        if (!cursor.isEmpty())
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
        if (!throttle.tryAcquire(player.getUniqueId(), Bukkit.getCurrentTick()))
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
            player.sendMessage(Component.text("This shop just changed. Check its offers again.", NamedTextColor.YELLOW));
            return;
        }
        Optional<Offer<ItemStack>> offer = shop.offer(offerId);
        Optional<Inventory> barrel = stock(shop);
        if (offer.isEmpty() || barrel.isEmpty())
        {
            return;
        }

        TradePlan<ItemStack> plan = planner.plan(offer.get(), 1, buyerSlots(player), stockSlots(barrel.get()));
        switch (plan)
        {
            case TradePlan.Failure<ItemStack> failure ->
                    player.sendMessage(Component.text(failure.reason().message(), NamedTextColor.RED));
            case TradePlan.Success<ItemStack> success ->
            {
                commit(player.getInventory(), success.buyerChanges());
                commit(barrel.get(), success.stockChanges());
                player.sendMessage(Component.text("Trade complete.", NamedTextColor.GREEN));
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

    static SlotInventory<ItemStack> stockSlots(Inventory inventory)
    {
        return SlotInventory.of(copies(inventory.getContents()), PaperStackRules.INSTANCE, inventory.getMaxStackSize());
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
}
