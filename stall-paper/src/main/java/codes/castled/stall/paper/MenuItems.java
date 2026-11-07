package codes.castled.stall.paper;

import codes.castled.stall.config.StallLang;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferStatus;
import codes.castled.stall.trade.ItemAmount;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The display items the shop menus are drawn with. None of them can ever leave a menu. */
final class MenuItems
{
    private MenuItems()
    {
    }

    /**
     * A copy of an offer's item with a line saying how many the trade moves, as the buyer sees it. The
     * stack shows that count when it fits one stack; the lore always gives the exact amount.
     */
    static ItemStack offerItem(StallLang lang, OfferItem<ItemStack> item, boolean cost)
    {
        String key = cost ? "menu.offer-item.buyer-pay" : "menu.offer-item.buyer-get";
        return withAmountLine(item, lang.text(key, "amount", String.valueOf(item.amount())), List.of());
    }

    /** An offer's item in the owner menu, with how to change it. */
    static ItemStack editableOfferItem(StallLang lang, OfferItem<ItemStack> item, boolean cost)
    {
        String key = cost ? "menu.offer-item.owner-pay" : "menu.offer-item.owner-get";
        return withAmountLine(item, lang.text(key, "amount", String.valueOf(item.amount())),
                BukkitItems.itemLines(lang.lines("menu.offer-item.edit-lore")));
    }

    private static ItemStack withAmountLine(OfferItem<ItemStack> item, String amountLine, List<String> extra)
    {
        ItemStack display = BukkitItems.withCount(item.item(), Math.clamp(item.amount(), 1, item.item().getMaxStackSize()));
        BukkitItems.editMeta(display, meta ->
        {
            List<String> existing = meta.getLore();
            List<String> lore = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            if (!amountLine.isEmpty())
            {
                lore.add(BukkitItems.itemLine(amountLine));
            }
            lore.addAll(extra);
            meta.setLore(lore);
        });
        return display;
    }

    /** An empty item slot in the owner menu. */
    static ItemStack emptyOfferSlot(StallLang lang, boolean cost)
    {
        return named(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                BukkitItems.itemLine(lang.text(cost ? "menu.empty-slot.cost-name" : "menu.empty-slot.reward-name")),
                BukkitItems.itemLines(lang.lines("menu.empty-slot.lore")));
    }

    static ItemStack removeButton(StallLang lang, boolean armed)
    {
        ItemStack stack = named(Material.BARRIER,
                BukkitItems.itemLine(lang.text(armed ? "menu.remove-button.armed-name" : "menu.remove-button.name")),
                BukkitItems.itemLines(lang.lines(armed ? "menu.remove-button.armed-lore" : "menu.remove-button.lore")));
        if (armed)
        {
            BukkitItems.editMeta(stack, meta -> meta.setEnchantmentGlintOverride(true));
        }
        return stack;
    }

    /** The status pane of an offer in the owner menu. */
    static ItemStack ownerStatus(StallLang lang, boolean complete, int timesInStock,
            List<ItemAmount<ItemStack>> missingStock)
    {
        if (!complete)
        {
            return named(Material.GRAY_STAINED_GLASS_PANE,
                    BukkitItems.itemLine(lang.text("menu.owner-incomplete.name")),
                    BukkitItems.itemLines(lang.lines("menu.owner-incomplete.lore")));
        }
        return status(lang, timesInStock == 0 ? OfferStatus.OUT_OF_STOCK : OfferStatus.AVAILABLE, timesInStock,
                missingStock);
    }

    /** The status pane of the empty row a new offer is put in. */
    static ItemStack newOffer(StallLang lang)
    {
        return named(Material.WHITE_STAINED_GLASS_PANE,
                BukkitItems.itemLine(lang.text("menu.new-offer.name")),
                BukkitItems.itemLines(lang.lines("menu.new-offer.lore")));
    }

    /**
     * The status pane. When the shop is out of stock or the buyer can't afford the trade, its lore lists
     * exactly which items are short and by how many.
     */
    static ItemStack status(StallLang lang, OfferStatus status, int timesInStock, List<ItemAmount<ItemStack>> missing)
    {
        Material pane = switch (status)
        {
            case AVAILABLE -> Material.LIME_STAINED_GLASS_PANE;
            case OUT_OF_STOCK -> Material.RED_STAINED_GLASS_PANE;
            case CANNOT_AFFORD, INVENTORY_FULL -> Material.ORANGE_STAINED_GLASS_PANE;
            case SHOP_FULL, INCOMPLETE, UNAVAILABLE -> Material.GRAY_STAINED_GLASS_PANE;
        };
        String stock = timesInStock == 1
                ? lang.text("menu.status.stock-one", "count", "1")
                : lang.text("menu.status.stock-many", "count", String.valueOf(timesInStock));
        List<String> lore = new ArrayList<>();
        if (!stock.isEmpty())
        {
            lore.add(BukkitItems.itemLine(stock));
        }
        if (!missing.isEmpty())
        {
            String header = lang.text(status == OfferStatus.OUT_OF_STOCK
                    ? "menu.status.missing-stock-header"
                    : "menu.status.missing-payment-header");
            if (!header.isEmpty())
            {
                lore.add(BukkitItems.itemLine(header));
            }
            for (ItemAmount<ItemStack> item : missing)
            {
                lore.add(BukkitItems.itemLine(lang.text("menu.status.missing-item",
                        "amount", String.valueOf(item.amount()),
                        "item", BukkitItems.name(item.item()))));
            }
        }
        return named(pane, BukkitItems.itemLine(lang.status(status)), lore);
    }

    static ItemStack buyButton(StallLang lang, OfferStatus status)
    {
        boolean available = status == OfferStatus.AVAILABLE;
        return named(available ? Material.LIME_DYE : Material.GRAY_DYE,
                BukkitItems.itemLine(available ? lang.text("menu.buy-button.name") : lang.status(status)),
                available ? BukkitItems.itemLines(lang.lines("menu.buy-button.lore")) : List.of());
    }

    private static ItemStack named(Material material, String name, List<String> lore)
    {
        ItemStack stack = new ItemStack(material);
        BukkitItems.editMeta(stack, meta ->
        {
            meta.setDisplayName(name);
            if (!lore.isEmpty())
            {
                meta.setLore(lore);
            }
        });
        return stack;
    }
}
