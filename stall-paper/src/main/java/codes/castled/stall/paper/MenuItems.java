package codes.castled.stall.paper;

import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferStatus;
import codes.castled.stall.trade.ItemAmount;
import org.bukkit.ChatColor;
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
    static ItemStack offerItem(OfferItem<ItemStack> item, boolean cost)
    {
        return withAmountLine(item, cost ? "You pay " : "You get ", cost, List.of());
    }

    /** An offer's item in the owner menu, with how to change it. */
    static ItemStack editableOfferItem(OfferItem<ItemStack> item, boolean cost)
    {
        return withAmountLine(item, cost ? "Buyers pay " : "Buyers get ", cost, List.of(
                line("Click with an item: put it in", ChatColor.GRAY),
                line("Left/right-click: one more/fewer", ChatColor.GRAY),
                line("Shift-click: a stack more/fewer", ChatColor.GRAY),
                line("Q: take it out", ChatColor.GRAY)));
    }

    private static ItemStack withAmountLine(OfferItem<ItemStack> item, String prefix, boolean cost, List<String> extra)
    {
        ItemStack display = BukkitItems.withCount(item.item(), Math.clamp(item.amount(), 1, item.item().getMaxStackSize()));
        BukkitItems.editMeta(display, meta ->
        {
            List<String> existing = meta.getLore();
            List<String> lore = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            lore.add(line(prefix + item.amount() + "×", cost ? ChatColor.GOLD : ChatColor.GREEN));
            lore.addAll(extra);
            meta.setLore(lore);
        });
        return display;
    }

    /** An empty item slot in the owner menu. */
    static ItemStack emptyOfferSlot(boolean cost)
    {
        return named(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                line(cost ? "Add what buyers pay" : "Add what buyers get", cost ? ChatColor.GOLD : ChatColor.GREEN),
                List.of(line("Click with an item to put it here", ChatColor.GRAY)));
    }

    static ItemStack removeButton(boolean armed)
    {
        ItemStack stack = named(Material.BARRIER,
                line(armed ? "Press Q again to remove" : "Remove offer", ChatColor.RED),
                List.of(line(armed ? "Any other click cancels" : "Press Q, then Q again", ChatColor.GRAY)));
        if (armed)
        {
            BukkitItems.editMeta(stack, meta -> meta.setEnchantmentGlintOverride(true));
        }
        return stack;
    }

    /** The status pane of an offer in the owner menu. */
    static ItemStack ownerStatus(boolean complete, int timesInStock, List<ItemAmount<ItemStack>> missingStock)
    {
        if (!complete)
        {
            return named(Material.GRAY_STAINED_GLASS_PANE, line("Not set up yet", ChatColor.GRAY),
                    List.of(line("Buyers see this offer once it has", ChatColor.GRAY),
                            line("an item on each side.", ChatColor.GRAY)));
        }
        return status(timesInStock == 0 ? OfferStatus.OUT_OF_STOCK : OfferStatus.AVAILABLE, timesInStock, missingStock);
    }

    /** The status pane of the empty row a new offer is put in. */
    static ItemStack newOffer()
    {
        return named(Material.WHITE_STAINED_GLASS_PANE, line("New offer", ChatColor.WHITE),
                List.of(line("Put what buyers pay on the left", ChatColor.GRAY),
                        line("and what they get on the right.", ChatColor.GRAY)));
    }

    /**
     * The status pane. When the shop is out of stock or the buyer can't afford the trade, its lore lists
     * exactly which items are short and by how many.
     */
    static ItemStack status(OfferStatus status, int timesInStock, List<ItemAmount<ItemStack>> missing)
    {
        Material pane = switch (status)
        {
            case AVAILABLE -> Material.LIME_STAINED_GLASS_PANE;
            case OUT_OF_STOCK -> Material.RED_STAINED_GLASS_PANE;
            case CANNOT_AFFORD, INVENTORY_FULL -> Material.ORANGE_STAINED_GLASS_PANE;
            case SHOP_FULL, INCOMPLETE, UNAVAILABLE -> Material.GRAY_STAINED_GLASS_PANE;
        };
        String stock = timesInStock == 1 ? "1 trade in stock" : timesInStock + " trades in stock";
        List<String> lore = new ArrayList<>();
        lore.add(line(stock, ChatColor.GRAY));
        if (!missing.isEmpty())
        {
            lore.add(line(status == OfferStatus.OUT_OF_STOCK ? "The shop is out of:" : "You still need:",
                    ChatColor.GRAY));
            for (ItemAmount<ItemStack> item : missing)
            {
                lore.add(line(" " + item.amount() + "× ", ChatColor.WHITE) + BukkitItems.name(item.item()));
            }
        }
        return named(pane, line(status.label(), color(status)), lore);
    }

    static ItemStack buyButton(OfferStatus status)
    {
        boolean available = status == OfferStatus.AVAILABLE;
        return named(available ? Material.LIME_DYE : Material.GRAY_DYE,
                available ? line("Buy", ChatColor.GREEN) : line(status.label(), color(status)),
                available ? List.of(line("Click to trade", ChatColor.GRAY)) : List.of());
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

    private static ChatColor color(OfferStatus status)
    {
        return switch (status)
        {
            case AVAILABLE -> ChatColor.GREEN;
            case OUT_OF_STOCK -> ChatColor.RED;
            case CANNOT_AFFORD, INVENTORY_FULL -> ChatColor.GOLD;
            case SHOP_FULL, INCOMPLETE, UNAVAILABLE -> ChatColor.GRAY;
        };
    }

    private static String line(String text, ChatColor color)
    {
        return BukkitItems.text(text, color);
    }
}
