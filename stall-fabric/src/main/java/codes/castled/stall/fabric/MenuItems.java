package codes.castled.stall.fabric;

import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferStatus;
import codes.castled.stall.trade.ItemAmount;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

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
                line("Click with an item: put it in", ChatFormatting.GRAY),
                line("Left/right-click: one more/fewer", ChatFormatting.GRAY),
                line("Shift-click: a stack more/fewer", ChatFormatting.GRAY),
                line("Q: take it out", ChatFormatting.GRAY)));
    }

    private static ItemStack withAmountLine(OfferItem<ItemStack> item, String prefix, boolean cost, List<Component> extra)
    {
        ItemStack display = item.item().copyWithCount(Math.clamp(item.amount(), 1, item.item().getMaxStackSize()));
        List<Component> lore = new ArrayList<>(display.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lore.add(line(prefix + item.amount() + "×", cost ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        lore.addAll(extra);
        display.set(DataComponents.LORE, new ItemLore(lore));
        return display;
    }

    /** An empty item slot in the owner menu. */
    static ItemStack emptyOfferSlot(boolean cost)
    {
        return named("light_gray_stained_glass_pane",
                line(cost ? "Add what buyers pay" : "Add what buyers get", cost ? ChatFormatting.GOLD : ChatFormatting.GREEN),
                List.of(line("Click with an item to put it here", ChatFormatting.GRAY)));
    }

    static ItemStack removeButton(boolean armed)
    {
        ItemStack stack = named("barrier",
                line(armed ? "Press Q again to remove" : "Remove offer", ChatFormatting.RED),
                List.of(line(armed ? "Any other click cancels" : "Press Q, then Q again", ChatFormatting.GRAY)));
        if (armed)
        {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    /** The status pane of an offer in the owner menu. */
    static ItemStack ownerStatus(boolean complete, int timesInStock, List<ItemAmount<ItemStack>> missingStock)
    {
        if (!complete)
        {
            return named("gray_stained_glass_pane", line("Not set up yet", ChatFormatting.GRAY),
                    List.of(line("Buyers see this offer once it has", ChatFormatting.GRAY),
                            line("an item on each side.", ChatFormatting.GRAY)));
        }
        return status(timesInStock == 0 ? OfferStatus.OUT_OF_STOCK : OfferStatus.AVAILABLE, timesInStock, missingStock);
    }

    /** The status pane of the empty row a new offer is put in. */
    static ItemStack newOffer()
    {
        return named("white_stained_glass_pane", line("New offer", ChatFormatting.WHITE),
                List.of(line("Put what buyers pay on the left", ChatFormatting.GRAY),
                        line("and what they get on the right.", ChatFormatting.GRAY)));
    }

    /**
     * The status pane. When the shop is out of stock or the buyer can't afford the trade, its lore lists
     * exactly which items are short and by how many.
     */
    static ItemStack status(OfferStatus status, int timesInStock, List<ItemAmount<ItemStack>> missing)
    {
        String pane = switch (status)
        {
            case AVAILABLE -> "lime_stained_glass_pane";
            case OUT_OF_STOCK -> "red_stained_glass_pane";
            case CANNOT_AFFORD, INVENTORY_FULL -> "orange_stained_glass_pane";
            case SHOP_FULL, INCOMPLETE, UNAVAILABLE -> "gray_stained_glass_pane";
        };
        String stock = timesInStock == 1 ? "1 trade in stock" : timesInStock + " trades in stock";
        List<Component> lore = new ArrayList<>();
        lore.add(line(stock, ChatFormatting.GRAY));
        if (!missing.isEmpty())
        {
            lore.add(line(status == OfferStatus.OUT_OF_STOCK ? "The shop is out of:" : "You still need:",
                    ChatFormatting.GRAY));
            for (ItemAmount<ItemStack> item : missing)
            {
                lore.add(Component.empty().withStyle(style -> style.withItalic(false).withColor(ChatFormatting.WHITE))
                        .append(" " + item.amount() + "× ")
                        .append(item.item().getHoverName()));
            }
        }
        return named(pane, line(status.label(), color(status)), lore);
    }

    static ItemStack buyButton(OfferStatus status)
    {
        boolean available = status == OfferStatus.AVAILABLE;
        return named(available ? "lime_dye" : "gray_dye",
                available ? line("Buy", ChatFormatting.GREEN) : line(status.label(), color(status)),
                available ? List.of(line("Click to trade", ChatFormatting.GRAY)) : List.of());
    }

    private static ItemStack named(String itemId, Component name, List<Component> lore)
    {
        ItemStack stack = new ItemStack(item(itemId));
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (!lore.isEmpty())
        {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    private static ChatFormatting color(OfferStatus status)
    {
        return switch (status)
        {
            case AVAILABLE -> ChatFormatting.GREEN;
            case OUT_OF_STOCK -> ChatFormatting.RED;
            case CANNOT_AFFORD, INVENTORY_FULL -> ChatFormatting.GOLD;
            case SHOP_FULL, INCOMPLETE, UNAVAILABLE -> ChatFormatting.GRAY;
        };
    }

    private static Component line(String text, ChatFormatting color)
    {
        return Component.literal(text).withStyle(style -> style.withItalic(false).withColor(color));
    }

    /** Colored items are looked up by id: Minecraft 26.2 replaced their {@code Items} fields with color collections. */
    private static Item item(String id)
    {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
    }
}
