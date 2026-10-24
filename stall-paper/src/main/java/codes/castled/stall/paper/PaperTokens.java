package codes.castled.stall.paper;

import codes.castled.stall.shop.Tokens;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Stall tokens: paper with a name, a glint and a {@code stall:token} marker in its persistent data, which
 * Paper keeps where the Fabric mod looks for it too (see {@link Tokens}).
 */
final class PaperTokens
{
    private static final NamespacedKey MARKER = Objects.requireNonNull(NamespacedKey.fromString(Tokens.MARKER));

    private PaperTokens()
    {
    }

    static ItemStack create(int count)
    {
        // The legacy text setters encode style defaults differently on Spigot and Paper. Parsing the
        // same vanilla components keeps token identity identical, including the Fabric token's text.
        String lore = Tokens.LORE.stream().map(line -> text(line, "gray")).collect(Collectors.joining(","));
        ItemStack token = Bukkit.getItemFactory().createItemStack("minecraft:paper[minecraft:custom_name="
                + text(Tokens.NAME, "gold") + ",minecraft:lore=[" + lore + "],minecraft:enchantment_glint_override=true]");
        token.setAmount(count);
        BukkitItems.editMeta(token, meta ->
        {
            meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
        });
        return token;
    }

    static boolean isToken(ItemStack stack)
    {
        return !BukkitItems.isEmpty(stack) && stack.getItemMeta().getPersistentDataContainer().has(MARKER);
    }

    private static String text(String text, String color)
    {
        String quoted = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{text:\"" + quoted + "\",color:'" + color + "',italic:false}";
    }
}
