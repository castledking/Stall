package codes.castled.stall.paper;

import codes.castled.stall.menu.MenuClick;
import codes.castled.stall.menu.ShopMenuLayout;
import codes.castled.stall.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A shop menu: a chest inventory of one to six rows, owned by this holder so Stall's listener can
 * recognize it. Its slots hold display copies; every click on them is cancelled, so nothing in them ever
 * moves.
 */
abstract class StallMenu implements InventoryHolder
{
    protected final PaperShops shops;
    protected final Player viewer;
    protected final Shop<ItemStack> shop;
    protected final Inventory inventory;
    private int revision;
    private boolean reopenPending;

    StallMenu(PaperShops shops, Player viewer, Shop<ItemStack> shop, int rows, String title)
    {
        this.shops = shops;
        this.viewer = viewer;
        this.shop = shop;
        this.inventory = Bukkit.createInventory(this, rows * ShopMenuLayout.COLUMNS, title);
    }

    @Override
    public Inventory getInventory()
    {
        return inventory;
    }

    Player viewer()
    {
        return viewer;
    }

    Shop<ItemStack> shop()
    {
        return shop;
    }

    /** The shop revision this menu was last drawn from. */
    int revision()
    {
        return revision;
    }

    /**
     * How many rows the shop's current offers need. An inventory's size is fixed once it exists, so when
     * this changes the menu is reopened at the new size.
     */
    abstract int wantedRows();

    /** Marks the menu as about to be reopened; returns false if that was already arranged. */
    boolean claimReopen()
    {
        if (reopenPending)
        {
            return false;
        }
        reopenPending = true;
        return true;
    }

    /** Draws the menu from the shop's current state. */
    abstract void render();

    /** Handles any click while this menu is open, in either half of the screen. */
    abstract void handleClick(InventoryClickEvent event);

    /** Cancels drags that touch the menu's own slots. */
    void handleDrag(InventoryDragEvent event)
    {
        if (event.getRawSlots().stream().anyMatch(slot -> slot < inventory.getSize()))
        {
            event.setCancelled(true);
        }
    }

    /** Starts a redraw: remembers the revision being drawn and empties the menu. */
    protected void beginRender()
    {
        revision = shop.revision();
        inventory.clear();
    }

    int rows()
    {
        return inventory.getSize() / ShopMenuLayout.COLUMNS;
    }

    protected boolean isMenuSlot(int rawSlot)
    {
        return rawSlot >= 0 && rawSlot < inventory.getSize();
    }

    protected static MenuClick menuClick(ClickType click)
    {
        return switch (click)
        {
            case LEFT -> MenuClick.LEFT;
            case RIGHT -> MenuClick.RIGHT;
            case SHIFT_LEFT -> MenuClick.SHIFT_LEFT;
            case SHIFT_RIGHT -> MenuClick.SHIFT_RIGHT;
            case DROP, CONTROL_DROP -> MenuClick.DROP;
            default -> MenuClick.OTHER;
        };
    }
}
