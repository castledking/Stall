package codes.castled.stall.fabric;

import codes.castled.stall.menu.MenuClick;
import codes.castled.stall.menu.RemoveConfirmation;
import codes.castled.stall.menu.ShopMenuLayout;
import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.trade.SlotInventory;
import codes.castled.stall.trade.TradePlanner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

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

    OwnerMenu(int containerId, ServerPlayer viewer, Shop<ItemStack> shop, ItemStack carried, FabricShops shops)
    {
        super(containerId, viewer, shop, ShopMenuLayout.ownerRows(shop.offers().size()), carried, shops);
        render();
    }

    @Override
    int wantedRows()
    {
        return ShopMenuLayout.ownerRows(shop.offers().size());
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player)
    {
        boolean onDisplay = slot >= 0 && slot < displaySize();
        if (onDisplay && input == ContainerInput.SWAP && button == Inventory.SLOT_OFFHAND)
        {
            resyncOffhand();
            return;
        }
        // Drags go through vanilla, which skips display slots because they refuse items.
        if (onDisplay && input != ContainerInput.QUICK_CRAFT)
        {
            clickedDisplay(slot, menuClick(input, button));
            return;
        }
        super.clicked(slot, button, input, player);
    }

    private void clickedDisplay(int slot, MenuClick click)
    {
        // Someone else changed the offers since this menu was drawn: show the change before acting on it.
        if (revision() != shop.revision())
        {
            render();
            return;
        }
        ShopMenuLayout.SlotRole role = ShopMenuLayout.roleOf(slot, rows);
        switch (role.role())
        {
            case REMOVE -> clickedRemove(role.row(), click);
            case COST, REWARD ->
            {
                removal.reset();
                boolean cost = role.role() == ShopMenuLayout.Role.COST;
                if (shops.editor().click(shop, role.row(), cost, role.index(), click, getCarried()))
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
        if (removal.press(shop.offers().get(row).id(), tick()))
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
        SlotInventory<ItemStack> stock = shops.stock(viewer.level(), shop).map(FabricShops::stockSlots).orElse(null);
        long tick = tick();

        List<Offer<ItemStack>> offers = shop.offers();
        for (int row = 0; row < rows; row++)
        {
            if (row < offers.size())
            {
                Offer<ItemStack> offer = offers.get(row);
                display.setItem(ShopMenuLayout.removeSlot(row), MenuItems.removeButton(removal.isArmed(offer.id(), tick)));
                drawItems(row, offer.costs(), true);
                drawItems(row, offer.rewards(), false);
                int inStock = stock == null || !offer.isComplete() ? 0 : planner.timesInStock(offer, stock);
                display.setItem(ShopMenuLayout.statusSlot(row), MenuItems.ownerStatus(offer.isComplete(), inStock,
                        stock == null ? List.of() : planner.missingStock(offer, stock)));
            }
            else if (row == offers.size())
            {
                drawItems(row, OfferSlots.empty(), true);
                drawItems(row, OfferSlots.empty(), false);
                display.setItem(ShopMenuLayout.statusSlot(row), MenuItems.newOffer());
            }
        }
    }

    private void drawItems(int row, OfferSlots<ItemStack> side, boolean cost)
    {
        for (int i = 0; i < OfferSlots.SIZE; i++)
        {
            OfferItem<ItemStack> item = side.get(i);
            display.setItem(ShopMenuLayout.itemSlot(row, cost, i),
                    item == null ? MenuItems.emptyOfferSlot(cost) : MenuItems.editableOfferItem(item, cost));
        }
    }
}
