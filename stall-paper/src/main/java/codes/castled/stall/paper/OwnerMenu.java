package codes.castled.stall.paper;

import codes.castled.stall.menu.MenuClick;
import codes.castled.stall.menu.RemoveConfirmation;
import codes.castled.stall.menu.ShopMenuLayout;
import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.trade.SlotInventory;
import codes.castled.stall.trade.TradePlanner;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The owner menu: every offer in its own row, plus an empty row to start a new one, so the menu grows
 * as the owner fills it up. The first slot of each offer's row is its remove button: the drop key arms
 * it and a second press removes the offer.
 *
 * <p>The item slots follow {@link codes.castled.stall.menu.OfferEditor}: clicking with an item puts a
 * copy in and the cursor keeps the item. The owner's own inventory below works as usual, so items can
 * be picked up to put them in an offer.
 */
final class OwnerMenu extends StallMenu
{
    private final RemoveConfirmation removal = new RemoveConfirmation();

    OwnerMenu(PaperShops shops, Player viewer, Shop<ItemStack> shop)
    {
        super(shops, viewer, shop, ShopMenuLayout.ownerRows(shop.offers().size()),
                Component.text("Edit " + shop.ownerName() + "'s Stall"));
        render();
    }

    @Override
    int wantedRows()
    {
        return ShopMenuLayout.ownerRows(shop.offers().size());
    }

    @Override
    void handleClick(InventoryClickEvent event)
    {
        if (isMenuSlot(event.getRawSlot()))
        {
            event.setCancelled(true);
            clickedMenu(event.getRawSlot(), menuClick(event.getClick()), event.getCursor());
            return;
        }
        // Clicks in the owner's own inventory work as usual, except the two that reach into the menu.
        switch (event.getAction())
        {
            case MOVE_TO_OTHER_INVENTORY, COLLECT_TO_CURSOR -> event.setCancelled(true);
            default ->
            {
            }
        }
    }

    private void clickedMenu(int slot, MenuClick click, ItemStack cursor)
    {
        // Someone else changed the offers since this menu was drawn: show the change before acting on it.
        if (revision() != shop.revision())
        {
            render();
            return;
        }
        ShopMenuLayout.SlotRole role = ShopMenuLayout.roleOf(slot, rows());
        switch (role.role())
        {
            case REMOVE -> clickedRemove(role.row(), click);
            case COST, REWARD ->
            {
                removal.reset();
                boolean cost = role.role() == ShopMenuLayout.Role.COST;
                if (shops.editor().click(shop, role.row(), cost, role.index(), click, cursor))
                {
                    shops.edited(shop);
                }
                else
                {
                    render();
                }
            }
            default ->
            {
                removal.reset();
                render();
            }
        }
    }

    private void clickedRemove(int row, MenuClick click)
    {
        if (click != MenuClick.DROP || row >= shop.offers().size())
        {
            removal.reset();
            render();
            return;
        }
        if (removal.press(shop.offers().get(row).id(), Bukkit.getCurrentTick()))
        {
            shop.removeOffer(row);
            shops.edited(shop);
        }
        else
        {
            render();
        }
    }

    @Override
    void render()
    {
        beginRender();
        TradePlanner<ItemStack> planner = shops.planner();
        SlotInventory<ItemStack> stock = shops.stock(shop).map(PaperShops::stockSlots).orElse(null);
        long tick = Bukkit.getCurrentTick();

        List<Offer<ItemStack>> offers = shop.offers();
        for (int row = 0; row < rows(); row++)
        {
            if (row < offers.size())
            {
                Offer<ItemStack> offer = offers.get(row);
                inventory.setItem(ShopMenuLayout.removeSlot(row), MenuItems.removeButton(removal.isArmed(offer.id(), tick)));
                drawItems(row, offer.costs(), true);
                drawItems(row, offer.rewards(), false);
                int inStock = stock == null || !offer.isComplete() ? 0 : planner.timesInStock(offer, stock);
                inventory.setItem(ShopMenuLayout.statusSlot(row), MenuItems.ownerStatus(offer.isComplete(), inStock,
                        stock == null ? List.of() : planner.missingStock(offer, stock)));
            }
            else if (row == offers.size())
            {
                drawItems(row, OfferSlots.empty(), true);
                drawItems(row, OfferSlots.empty(), false);
                inventory.setItem(ShopMenuLayout.statusSlot(row), MenuItems.newOffer());
            }
        }
    }

    private void drawItems(int row, OfferSlots<ItemStack> side, boolean cost)
    {
        for (int i = 0; i < OfferSlots.SIZE; i++)
        {
            OfferItem<ItemStack> item = side.get(i);
            inventory.setItem(ShopMenuLayout.itemSlot(row, cost, i),
                    item == null ? MenuItems.emptyOfferSlot(cost) : MenuItems.editableOfferItem(item, cost));
        }
    }
}
