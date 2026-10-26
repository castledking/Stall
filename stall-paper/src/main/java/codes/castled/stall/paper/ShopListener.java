package codes.castled.stall.paper;

import codes.castled.stall.shop.Shop;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

final class ShopListener implements Listener
{
    private final PaperShops shops;

    ShopListener(PaperShops shops)
    {
        this.shops = shops;
    }

    /**
     * Opens the shop menus. Runs at {@code LOWEST}, before protection plugins such as GriefPrevention,
     * so buyers can use shops inside claims they are not trusted in. Cancelling the click keeps the
     * container itself shut; only the owner's restocking clicks pass through to protection and vanilla.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    void onInteract(PlayerInteractEvent event)
    {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND)
        {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || player.getGameMode() == GameMode.SPECTATOR)
        {
            return;
        }
        if (!FoliaSupport.owns(block.getWorld(), block.getX() >> 4, block.getZ() >> 4))
        {
            event.setCancelled(true);
            return;
        }
        Optional<Shop<ItemStack>> shop = shops.shopAt(block);
        shop.ifPresent(shops::reconcile);
        if (shop.isEmpty() || !shops.registry().isCurrent(shop.get()) || !PaperContainers.isSupported(block))
        {
            return;
        }
        boolean previewing = shops.isPreviewing(player.getUniqueId());
        boolean owner = shop.get().isOwner(player.getUniqueId()) && !previewing;
        boolean manager = owner || (!previewing && player.hasPermission("stall.admin"));
        boolean emptyHanded = BukkitItems.isEmpty(player.getInventory().getItemInMainHand())
                && BukkitItems.isEmpty(player.getInventory().getItemInOffHand());
        // The marker is on the half at the shop's location, which for a double chest may not be this one.
        if (!shops.isActive(shop.get()))
        {
            // A paused shop stays shut to buyers, which keeps its stock safe. Its owner's clicks go on to
            // protection and vanilla; a sneak-click reactivates it at HIGH below.
            if (!manager)
            {
                event.setCancelled(true);
                player.sendMessage(BukkitItems.text("This shop is paused until its owner opens it again.",
                        ChatColor.YELLOW));
            }
            return;
        }
        if (manager && player.isSneaking() && emptyHanded)
        {
            event.setCancelled(true);
            shops.openOwnerMenu(player, shop.get());
            return;
        }
        // The owner's other clicks open the container to restock it, through protection as usual; a large
        // barrel's 54-slot view opens at HIGH below, once protection has allowed the click.
        if (owner)
        {
            return;
        }
        // Sneaking with an item in hand places it against the container instead of opening it, so let
        // that through. Sneaking empty-handed still opens containers in vanilla, so it gets the menu.
        if (player.isSneaking() && !emptyHanded)
        {
            return;
        }
        // Either way the container stays shut: a buyer never reaches the stock itself.
        event.setCancelled(true);
        if (player.hasPermission("stall.use"))
        {
            shops.openBuyerMenu(player, shop.get());
        }
        else
        {
            player.sendMessage(BukkitItems.text("You don't have permission to use shops.", ChatColor.RED));
        }
    }

    /** Protect only shop blocks; the rest of the explosion behaves normally. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onEntityExplosion(EntityExplodeEvent event)
    {
        event.blockList().removeIf(block -> shops.shopAt(block).isPresent());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBlockExplosion(BlockExplodeEvent event)
    {
        event.blockList().removeIf(block -> shops.shopAt(block).isPresent());
    }

    @EventHandler
    void onChunkLoad(ChunkLoadEvent event)
    {
        shops.chunkLoaded(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    /** Writes large barrels' extra slots back before their chunk is saved and unloaded. */
    @EventHandler
    void onChunkUnload(ChunkUnloadEvent event)
    {
        shops.chunkUnloading(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    /**
     * Keeps anyone but a shop's owner, or an operator, from joining a chest onto a chest shop: the new half
     * would open the whole double chest, stock and all. Placing it while sneaking keeps it separate, as
     * in vanilla. The owner may join one on, and the shop then covers both halves.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event)
    {
        Block placed = event.getBlockPlaced();
        if (!PaperContainers.isSupported(placed) || PaperContainers.isBarrel(placed))
        {
            return;
        }
        Optional<Shop<ItemStack>> shop = PaperContainers.partner(placed).flatMap(shops::shopAt);
        Player player = event.getPlayer();
        if (shop.isEmpty() || shop.get().isOwner(player.getUniqueId()) || player.hasPermission("stall.admin"))
        {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(BukkitItems.text("That would join your chest onto " + shop.get().ownerName() + "'s shop. "
                + "Sneak while placing it to keep it separate.", ChatColor.RED));
    }

    /**
     * Turns a container into a shop with a token, reactivates a paused shop, or opens a large barrel's
     * 54-slot stock for its owner. Runs at {@code HIGH}, after protection plugins such as GriefPrevention
     * (at {@code LOW}), and only if none of them refused the click: a shop can only go where its owner
     * could open the container anyway.
     */
    @EventHandler(priority = EventPriority.HIGH)
    void onTokenUse(PlayerInteractEvent event)
    {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.useInteractedBlock() == Event.Result.DENY)
        {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || player.getGameMode() == GameMode.SPECTATOR
                || !FoliaSupport.owns(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)
                || !PaperContainers.isSupported(block))
        {
            return;
        }
        Optional<Shop<ItemStack>> existing = shops.shopAt(block);
        if (existing.isPresent())
        {
            Shop<ItemStack> shop = existing.get();
            boolean previewing = shops.isPreviewing(player.getUniqueId());
            boolean owner = shop.isOwner(player.getUniqueId()) && !previewing;
            boolean manager = owner || (!previewing && player.hasPermission("stall.admin"));
            boolean emptyHanded = BukkitItems.isEmpty(player.getInventory().getItemInMainHand())
                    && BukkitItems.isEmpty(player.getInventory().getItemInOffHand());
            boolean active = shops.isActive(shop);
            if (manager && player.isSneaking() && emptyHanded && !active)
            {
                event.setCancelled(true);
                shops.reactivate(player, shop);
                return;
            }
            // The owner restocking: a large barrel opens as one 54-slot view instead of the barrel's own 27.
            if (owner && active && !player.isSneaking() && shops.grantLargeStock(player, shop, block)
                    && shops.openStock(player, shop))
            {
                event.setCancelled(true);
            }
            return;
        }
        if (!PaperTokens.isToken(event.getItem()))
        {
            return;
        }
        event.setCancelled(true);
        shops.create(player, block);
    }

    /** Keeps hoppers and hopper minecarts from pulling a shop's stock out. Hoppers can still put items in. */
    @EventHandler(ignoreCancelled = true)
    void onHopperPull(InventoryMoveItemEvent event)
    {
        Inventory source = event.getSource();
        if (source.getType() != InventoryType.BARREL && source.getType() != InventoryType.CHEST)
        {
            return;
        }
        // A double chest's location is between its halves, which lands in one of them; shopAt covers both.
        Location location = source.getLocation();
        if (location != null && shops.shopAt(location.getBlock()).isPresent())
        {
            event.setCancelled(true);
        }
    }

    /** Hands every click while a shop menu is open, in either half of the screen, to that menu. */
    @EventHandler(priority = EventPriority.LOWEST)
    void onClick(InventoryClickEvent event)
    {
        if (event.getView().getTopInventory().getHolder() instanceof StallMenu menu)
        {
            menu.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onDrag(InventoryDragEvent event)
    {
        if (event.getView().getTopInventory().getHolder() instanceof StallMenu menu)
        {
            menu.handleDrag(event);
        }
    }

    /** Any click or drag in a large barrel's view may have changed its extra slots, which are then saved. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onStockClick(InventoryClickEvent event)
    {
        if (event.getWhoClicked() instanceof Player player && shops.isStockView(player, event.getView().getTopInventory()))
        {
            shops.stockViewChanged(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onStockDrag(InventoryDragEvent event)
    {
        if (event.getWhoClicked() instanceof Player player && shops.isStockView(player, event.getView().getTopInventory()))
        {
            shops.stockViewChanged(player);
        }
    }

    @EventHandler
    void onClose(InventoryCloseEvent event)
    {
        if (event.getInventory().getHolder() instanceof StallMenu menu)
        {
            shops.closed(menu);
        }
        else if (event.getPlayer() instanceof Player player)
        {
            shops.stockViewClosed(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event)
    {
        Optional<Shop<ItemStack>> shop = shops.shopAt(event.getBlock());
        Player player = event.getPlayer();
        if (shop.isEmpty() || shop.get().isOwner(player.getUniqueId()) || player.hasPermission("stall.admin"))
        {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(BukkitItems.text("This shop belongs to " + shop.get().ownerName() + ".", ChatColor.RED));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBroken(BlockBreakEvent event)
    {
        shops.shopAt(event.getBlock()).ifPresent(shop ->
        {
            shops.dropExtraStock(shop, event.getBlock());
            shops.remove(shop, event.getPlayer(), event.getBlock());
        });
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event)
    {
        int owed = shops.tokensOwed(event.getPlayer().getUniqueId());
        if (owed > 0)
        {
            event.getPlayer().sendMessage(BukkitItems.text((owed == 1 ? "A Stall token is" : owed + " Stall tokens are")
                    + " waiting for you from removed shops. Use /stall reclaim to collect.", ChatColor.YELLOW));
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event)
    {
        shops.forget(event.getPlayer().getUniqueId());
    }
}
