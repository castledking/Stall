package codes.castled.stall.paper;

import codes.castled.stall.shop.Shop;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
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
     * barrel itself shut; only the owner's restocking clicks pass through to protection and vanilla.
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
        Optional<Shop<ItemStack>> shop = shops.shopAt(block);
        if (shop.isEmpty() || !(block.getState() instanceof Barrel))
        {
            return;
        }
        boolean previewing = shops.isPreviewing(player.getUniqueId());
        boolean owner = shop.get().isOwner(player.getUniqueId()) && !previewing;
        boolean manager = owner || (!previewing && player.hasPermission("stall.admin"));
        boolean emptyHanded = BukkitItems.isEmpty(player.getInventory().getItemInMainHand())
                && BukkitItems.isEmpty(player.getInventory().getItemInOffHand());
        if (!PaperMarkers.carries(block, shop.get()))
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
        // The owner's other clicks open the barrel to restock it, through protection as usual.
        if (owner)
        {
            return;
        }
        // Sneaking with an item in hand places it against the barrel instead of opening it, so let
        // that through. Sneaking empty-handed still opens containers in vanilla, so it gets the menu.
        if (player.isSneaking() && !emptyHanded)
        {
            return;
        }
        // Either way the barrel stays shut: a buyer never reaches the stock itself.
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

    /**
     * Turns a barrel into a shop with a token, or reactivates a paused shop. Runs at {@code HIGH}, after
     * protection plugins such as GriefPrevention (at {@code LOW}), and only if none of them refused the
     * click: a shop can only go where its owner could open the barrel anyway.
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
        if (block == null || player.getGameMode() == GameMode.SPECTATOR || !(block.getState() instanceof Barrel barrel))
        {
            return;
        }
        Optional<Shop<ItemStack>> existing = shops.shopAt(block);
        if (existing.isPresent())
        {
            boolean manager = existing.get().isOwner(player.getUniqueId()) || player.hasPermission("stall.admin");
            boolean emptyHanded = BukkitItems.isEmpty(player.getInventory().getItemInMainHand())
                    && BukkitItems.isEmpty(player.getInventory().getItemInOffHand());
            if (manager && player.isSneaking() && emptyHanded && !PaperMarkers.carries(block, existing.get())
                    && !shops.isPreviewing(player.getUniqueId()))
            {
                event.setCancelled(true);
                shops.reactivate(player, block, existing.get());
            }
            return;
        }
        if (!PaperTokens.isToken(event.getItem()))
        {
            return;
        }
        event.setCancelled(true);
        shops.create(player, block, barrel);
    }

    /** Keeps hoppers and hopper minecarts from pulling a shop's stock out. Hoppers can still put items in. */
    @EventHandler(ignoreCancelled = true)
    void onHopperPull(InventoryMoveItemEvent event)
    {
        Inventory source = event.getSource();
        if (source.getType() != InventoryType.BARREL)
        {
            return;
        }
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

    @EventHandler
    void onClose(InventoryCloseEvent event)
    {
        if (event.getInventory().getHolder() instanceof StallMenu menu)
        {
            shops.closed(menu);
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
        shops.shopAt(event.getBlock()).ifPresent(shop -> shops.remove(shop, event.getPlayer(), true));
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
