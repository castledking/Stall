package codes.castled.stall.fabric;

import codes.castled.stall.menu.ShopMenuLayout;
import codes.castled.stall.menu.ShopMenuLayout.Role;
import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferStatus;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.trade.ItemAmount;
import codes.castled.stall.trade.SlotInventory;
import codes.castled.stall.trade.TradePlanner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The buyer menu: one row per complete offer. Every click, in either half of the screen, is ignored
 * except a left or right click on a buy button, so the buyer's cursor stays empty and nothing moves.
 */
final class BuyerMenu extends StallMenu
{
    /** The offer shown in each row, as of the last redraw. */
    private final List<UUID> rowOffers = new ArrayList<>();

    BuyerMenu(int containerId, ServerPlayer viewer, Shop<ItemStack> shop, ItemStack carried, FabricShops shops)
    {
        super(containerId, viewer, shop, ShopMenuLayout.buyerRows(shop.completeOffers().size()), carried, shops);
        render();
    }

    @Override
    int wantedRows()
    {
        return ShopMenuLayout.buyerRows(shop.completeOffers().size());
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player)
    {
        if (input == ContainerInput.SWAP && button == Inventory.SLOT_OFFHAND)
        {
            resyncOffhand();
            return;
        }
        if (input != ContainerInput.PICKUP || slot < 0 || slot >= displaySize())
        {
            return;
        }
        ShopMenuLayout.SlotRole role = ShopMenuLayout.roleOf(slot, rows);
        if (role.role() == Role.BUY && role.row() < rowOffers.size())
        {
            shops.buy(this, rowOffers.get(role.row()));
        }
    }

    /** Draws every complete offer against the shop's stock and the viewer's current contents. */
    @Override
    void render()
    {
        beginRender();
        rowOffers.clear();

        TradePlanner<ItemStack> planner = shops.planner();
        Optional<Container> stockContainer = shops.stock(viewer.level(), shop);
        SlotInventory<ItemStack> buyer = FabricShops.buyerSlots(viewer);
        SlotInventory<ItemStack> stock = stockContainer.map(FabricShops::stockSlots).orElse(null);

        List<Offer<ItemStack>> offers = shop.completeOffers();
        for (int row = 0; row < Math.min(offers.size(), rows); row++)
        {
            Offer<ItemStack> offer = offers.get(row);
            rowOffers.add(offer.id());
            for (boolean cost : new boolean[]{true, false})
            {
                List<OfferItem<ItemStack>> items = offer.side(cost).items();
                for (int i = 0; i < items.size(); i++)
                {
                    display.setItem(ShopMenuLayout.itemSlot(row, cost, i), MenuItems.offerItem(items.get(i), cost));
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
            display.setItem(ShopMenuLayout.statusSlot(row), MenuItems.status(status, inStock, missing));
            display.setItem(ShopMenuLayout.buySlot(row), MenuItems.buyButton(status));
        }
    }
}
