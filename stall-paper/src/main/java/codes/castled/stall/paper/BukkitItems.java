package codes.castled.stall.paper;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Item helpers using the API shared by Spigot and Paper. */
final class BukkitItems
{
    private BukkitItems()
    {
    }

    static boolean isEmpty(ItemStack stack)
    {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    static ItemStack withCount(ItemStack stack, int count)
    {
        ItemStack copy = stack.clone();
        copy.setAmount(count);
        return copy;
    }

    static void editMeta(ItemStack stack, Consumer<ItemMeta> edit)
    {
        ItemMeta meta = stack.getItemMeta();
        edit.accept(meta);
        stack.setItemMeta(meta);
    }

    /** A readable name for chat and missing-item hints; the actual offer always shows the original item. */
    static String name(ItemStack stack)
    {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName())
        {
            return meta.getDisplayName();
        }
        if (meta != null && meta.hasItemName())
        {
            return meta.getItemName();
        }
        return Arrays.stream(stack.getType().name().toLowerCase(Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    static String text(String text)
    {
        return text;
    }

    static String text(String text, ChatColor color)
    {
        return ChatColor.RESET.toString() + color + text;
    }
}
