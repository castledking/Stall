package codes.castled.stall.fabric;

import codes.castled.stall.menu.MenuClick;
import codes.castled.stall.menu.ShopMenuLayout;
import codes.castled.stall.shop.Shop;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A shop menu: a vanilla chest screen of one to six rows, so clients need nothing installed. Only the
 * server knows it is a shop.
 *
 * <p>The top rows hold display copies in slots that refuse anything going in or out, whatever code path
 * asks, so vanilla's own click handling can never move a display item or put a real one in their place.
 * The player's inventory below them is the real one.
 */
abstract class StallMenu extends AbstractContainerMenu
{
    protected final ServerPlayer viewer;
    protected final Shop<ItemStack> shop;
    protected final FabricShops shops;
    protected final SimpleContainer display;
    protected final int rows;
    private int revision;
    private boolean reopenPending;

    /**
     * @param carried what the player held on the cursor in the menu this one replaces, if any
     */
    StallMenu(int containerId, ServerPlayer viewer, Shop<ItemStack> shop, int rows, ItemStack carried, FabricShops shops)
    {
        super(menuType(rows), containerId);
        this.viewer = viewer;
        this.shop = shop;
        this.shops = shops;
        this.rows = rows;
        this.display = new SimpleContainer(rows * ShopMenuLayout.COLUMNS);

        // The same slots as a vanilla chest menu of this size, which is what the client builds.
        for (int row = 0; row < rows; row++)
        {
            for (int column = 0; column < ShopMenuLayout.COLUMNS; column++)
            {
                addSlot(new DisplaySlot(display, column + row * ShopMenuLayout.COLUMNS, 8 + column * 18, 18 + row * 18));
            }
        }
        int offset = (rows - 4) * 18;
        Inventory inventory = viewer.getInventory();
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + offset));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            addSlot(new Slot(inventory, column, 8 + column * 18, 161 + offset));
        }
        // Set before the menu opens, so the client gets it with the menu's first contents.
        setCarried(carried);
    }

    ServerPlayer viewer()
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

    int rows()
    {
        return rows;
    }

    /**
     * How many rows the shop's current offers need. A menu's size is fixed once it is open, so when this
     * changes the menu is reopened at the new size.
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

    /**
     * Draws the menu from the shop's current state. The changed slots reach the client with the next
     * broadcast, after a click or at the end of the tick.
     */
    abstract void render();

    /** Starts a redraw: remembers the revision being drawn and empties the display. */
    protected void beginRender()
    {
        revision = shop.revision();
        for (int slot = 0; slot < display.getContainerSize(); slot++)
        {
            display.setItem(slot, ItemStack.EMPTY);
        }
    }

    protected int displaySize()
    {
        return display.getContainerSize();
    }

    protected long tick()
    {
        return viewer.level().getServer().getTickCount();
    }

    protected static MenuClick menuClick(ContainerInput input, int button)
    {
        return switch (input)
        {
            case PICKUP -> button == 0 ? MenuClick.LEFT : MenuClick.RIGHT;
            case QUICK_MOVE -> button == 0 ? MenuClick.SHIFT_LEFT : MenuClick.SHIFT_RIGHT;
            case THROW -> MenuClick.DROP;
            default -> MenuClick.OTHER;
        };
    }

    /**
     * Sends the client its real offhand after the swap-with-offhand key over a display slot. The client
     * has already moved the display item into its offhand, and vanilla only corrects slots that belong to
     * the open menu, so without this the item would linger there as a ghost. Paper does the same.
     */
    protected void resyncOffhand()
    {
        InventoryMenu inventoryMenu = viewer.inventoryMenu;
        ItemStack offhand = inventoryMenu.getSlot(InventoryMenu.SHIELD_SLOT).getItem().copy();
        viewer.connection.send(new ClientboundContainerSetSlotPacket(
                inventoryMenu.containerId, inventoryMenu.incrementStateId(), InventoryMenu.SHIELD_SLOT, offhand));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return shops.stillValid(this, player);
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        shops.closed(this);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot slot)
    {
        return !(slot instanceof DisplaySlot);
    }

    @Override
    public boolean canDragTo(Slot slot)
    {
        return !(slot instanceof DisplaySlot);
    }

    private static MenuType<?> menuType(int rows)
    {
        return switch (rows)
        {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("A chest menu has 1 to 6 rows, not " + rows);
        };
    }

    /** A slot showing a display copy. Nothing can be taken out of it or put into it. */
    private static final class DisplaySlot extends Slot
    {
        DisplaySlot(Container container, int index, int x, int y)
        {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            return false;
        }

        @Override
        public boolean mayPickup(Player player)
        {
            return false;
        }
    }
}
