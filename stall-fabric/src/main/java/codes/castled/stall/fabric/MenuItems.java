package codes.castled.stall.fabric;

import codes.castled.stall.config.StallConfig;
import codes.castled.stall.config.StallLang;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferStatus;
import codes.castled.stall.trade.ItemAmount;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                FabricText.itemLines(lang.lines("menu.offer-item.edit-lore")));
    }

    private static ItemStack withAmountLine(OfferItem<ItemStack> item, String amountLine, List<Component> extra)
    {
        ItemStack display = item.item().copyWithCount(Math.clamp(item.amount(), 1, item.item().getMaxStackSize()));
        List<Component> lore = new ArrayList<>(display.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        if (!amountLine.isEmpty())
        {
            lore.add(FabricText.itemLine(amountLine));
        }
        lore.addAll(extra);
        display.set(DataComponents.LORE, new ItemLore(lore));
        return display;
    }

    /** An empty item slot in the owner menu. */
    static ItemStack emptyOfferSlot(StallConfig config, StallLang lang, boolean cost)
    {
        Item item = item(cost ? config.iconEmptyCostSlot() : config.iconEmptyRewardSlot(), "light_gray_stained_glass_pane");
        return named(item,
                FabricText.itemLine(lang.text(cost ? "menu.empty-slot.cost-name" : "menu.empty-slot.reward-name")),
                FabricText.itemLines(lang.lines("menu.empty-slot.lore")));
    }

    static ItemStack removeButton(StallConfig config, StallLang lang, boolean armed)
    {
        Item item = item(config.iconRemoveButton(), "barrier");
        ItemStack stack = named(item,
                FabricText.itemLine(lang.text(armed ? "menu.remove-button.armed-name" : "menu.remove-button.name")),
                FabricText.itemLines(lang.lines(armed ? "menu.remove-button.armed-lore" : "menu.remove-button.lore")));
        if (armed)
        {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    /** The status pane of an offer in the owner menu. */
    static ItemStack ownerStatus(StallConfig config, StallLang lang, boolean complete, int timesInStock,
            List<ItemAmount<ItemStack>> missingStock)
    {
        if (!complete)
        {
            Item item = item(config.iconStatusIncomplete(), "gray_stained_glass_pane");
            return named(item,
                    FabricText.itemLine(lang.text("menu.owner-incomplete.name")),
                    FabricText.itemLines(lang.lines("menu.owner-incomplete.lore")));
        }
        return status(config, lang, timesInStock == 0 ? OfferStatus.OUT_OF_STOCK : OfferStatus.AVAILABLE, timesInStock,
                missingStock);
    }

    /** The status pane of the empty row a new offer is put in. */
    static ItemStack newOffer(StallConfig config, StallLang lang)
    {
        Item item = item(config.iconStatusNewOffer(), "white_stained_glass_pane");
        return named(item,
                FabricText.itemLine(lang.text("menu.new-offer.name")),
                FabricText.itemLines(lang.lines("menu.new-offer.lore")));
    }

    /**
     * The status pane. When the shop is out of stock or the buyer can't afford the trade, its lore lists
     * exactly which items are short and by how many.
     */
    static ItemStack status(StallConfig config, StallLang lang, OfferStatus status, int timesInStock, List<ItemAmount<ItemStack>> missing)
    {
        String fallback = switch (status)
        {
            case AVAILABLE -> "lime_stained_glass_pane";
            case OUT_OF_STOCK -> "red_stained_glass_pane";
            case CANNOT_AFFORD, INVENTORY_FULL -> "orange_stained_glass_pane";
            case SHOP_FULL, INCOMPLETE, UNAVAILABLE -> "gray_stained_glass_pane";
        };
        String iconKey = switch (status)
        {
            case AVAILABLE -> config.iconStatusAvailable();
            case OUT_OF_STOCK -> config.iconStatusOutOfStock();
            case CANNOT_AFFORD -> config.iconStatusCannotAfford();
            case INVENTORY_FULL -> config.iconStatusInventoryFull();
            case SHOP_FULL -> config.iconStatusShopFull();
            case INCOMPLETE -> config.iconStatusIncomplete();
            case UNAVAILABLE -> config.iconStatusUnavailable();
        };
        Item item = item(iconKey, fallback);
        String stock = timesInStock == 1
                ? lang.text("menu.status.stock-one", "count", "1")
                : lang.text("menu.status.stock-many", "count", String.valueOf(timesInStock));
        List<Component> lore = new ArrayList<>();
        if (!stock.isEmpty())
        {
            lore.add(FabricText.itemLine(stock));
        }
        if (!missing.isEmpty())
        {
            String header = lang.text(status == OfferStatus.OUT_OF_STOCK
                    ? "menu.status.missing-stock-header"
                    : "menu.status.missing-payment-header");
            if (!header.isEmpty())
            {
                lore.add(FabricText.itemLine(header));
            }
            for (ItemAmount<ItemStack> itemAmount : missing)
            {
                lore.add(missingLine(lang, itemAmount));
            }
        }
        return named(item, FabricText.itemLine(lang.status(status)), lore);
    }

    private static Component missingLine(StallLang lang, ItemAmount<ItemStack> item)
    {
        String token = "\0item\0";
        String template = lang.text("menu.status.missing-item",
                "amount", String.valueOf(item.amount()),
                "item", token);
        int at = template.indexOf(token);
        if (at < 0)
        {
            return FabricText.itemLine(template);
        }
        String before = template.substring(0, at);
        String after = template.substring(at + token.length());
        MutableComponent line = Component.empty().withStyle(style -> style.withItalic(false))
                .append(FabricText.chat(before))
                .append(item.item().getHoverName());
        if (!after.isEmpty())
        {
            line.append(FabricText.chat(after));
        }
        return line;
    }

    static ItemStack buyButton(StallConfig config, StallLang lang, OfferStatus status)
    {
        boolean available = status == OfferStatus.AVAILABLE;
        String fallback = available ? "lime_dye" : "gray_dye";
        Item item = item(available ? config.iconBuyAvailable() : config.iconBuyUnavailable(), fallback);
        return named(item,
                FabricText.itemLine(available ? lang.text("menu.buy-button.name") : lang.status(status)),
                available ? FabricText.itemLines(lang.lines("menu.buy-button.lore")) : List.of());
    }

    private static ItemStack named(Item item, Component name, List<Component> lore)
    {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (!lore.isEmpty())
        {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return stack;
    }

    /** Colored items are looked up by id: Minecraft 26.2 replaced their {@code Items} fields with color collections. */
    private static Item item(String id, String fallbackId)
    {
        if (id != null && !id.isBlank())
        {
            String clean = id.trim().toLowerCase(Locale.ROOT);
            Identifier parsed = clean.contains(":") ? Identifier.tryParse(clean) : Identifier.withDefaultNamespace(clean);
            if (parsed != null && BuiltInRegistries.ITEM.containsKey(parsed))
            {
                Item found = BuiltInRegistries.ITEM.getValue(parsed);
                if (found != null && found != Items.AIR)
                {
                    return found;
                }
            }
        }
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(fallbackId));
    }
}
