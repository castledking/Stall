package codes.castled.stall.paper;

import codes.castled.stall.menu.ShopMenuLayout;
import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferStatus;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.trade.ItemAmount;
import codes.castled.stall.trade.SlotInventory;
import codes.castled.stall.trade.TradePlanner;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The buyer menu: one row per complete offer. Every click and drag, in either half of the screen, is
 * cancelled; a left or right click on a buy button makes the trade.
 */
final class BuyerMenu extends StallMenu
{
    /** The offer shown in each row, as of the last redraw. */
    private final List<UUID> rowOffers = new ArrayList<>();

    BuyerMenu(PaperShops shops, Player viewer, Shop<ItemStack> shop)
    {
        super(shops, viewer, shop, ShopMenuLayout.buyerRows(shop.completeOffers().size()),
                Component.text(shop.ownerName() + "'s Stall"));
        render();
    }

    @Override
    int wantedRows()
    {
        return ShopMenuLayout.buyerRows(shop.completeOffers().size());
    }

    @Override
    void handleClick(InventoryClickEvent event)
    {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!isMenuSlot(slot) || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT))
        {
            return;
        }
        ShopMenuLayout.SlotRole role = ShopMenuLayout.roleOf(slot, rows());
        if (role.role() == ShopMenuLayout.Role.BUY && role.row() < rowOffers.size())
        {
            shops.buy(this, rowOffers.get(role.row()));
        }
    }

    @Override
    void handleDrag(InventoryDragEvent event)
    {
        event.setCancelled(true);
    }

    /** Draws every complete offer against the barrel's and the viewer's current contents. */
    @Override
    void render()
    {
        beginRender();
        rowOffers.clear();

        TradePlanner<ItemStack> planner = shops.planner();
        Optional<Inventory> barrel = shops.stock(shop);
        SlotInventory<ItemStack> buyer = PaperShops.buyerSlots(viewer);
        SlotInventory<ItemStack> stock = barrel.map(PaperShops::stockSlots).orElse(null);

        List<Offer<ItemStack>> offers = shop.completeOffers();
        for (int row = 0; row < Math.min(offers.size(), rows()); row++)
        {
            Offer<ItemStack> offer = offers.get(row);
            rowOffers.add(offer.id());
            for (boolean cost : new boolean[]{true, false})
            {
                List<OfferItem<ItemStack>> items = offer.side(cost).items();
                for (int i = 0; i < items.size(); i++)
                {
                    inventory.setItem(ShopMenuLayout.itemSlot(row, cost, i), MenuItems.offerItem(items.get(i), cost));
                }
            }
            OfferStatus status = stock == null ? OfferStatus.UNAVAILABLE : planner.status(offer, buyer, stock);
            int inStock = stock == null ? 0 : planner.timesInStock(offer, stock);
            List<ItemAmount<ItemStack>> missing = switch (status)
            {
                case OUT_OF_STOCK -> planner.missingStock(offer, stock);
                case CANNOT_AFFORD -> planner.missingPayment(offer, buyer);
                default -> List.of();
            };
            inventory.setItem(ShopMenuLayout.statusSlot(row), MenuItems.status(status, inStock, missing));
            inventory.setItem(ShopMenuLayout.buySlot(row), MenuItems.buyButton(status));
        }
    }
}
