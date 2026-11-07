package codes.castled.stall.paper;

import codes.castled.stall.config.LegacyText;
import codes.castled.stall.config.StallConfig;
import codes.castled.stall.config.StallLang;
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
        return create(count, StallConfig.defaults(), StallLang.defaults());
    }

    static ItemStack create(int count, StallConfig config, StallLang lang)
    {
        // The legacy text setters encode style defaults differently on Spigot and Paper. Parsing the
        // same vanilla components keeps token identity identical, including the Fabric token's text.
        String name = LegacyText.itemSnbt(lang.text("token.name"));
        String lore = lang.lines("token.lore").stream()
                .map(LegacyText::itemSnbt)
                .collect(Collectors.joining(","));
        StringBuilder spec = new StringBuilder("minecraft:paper[minecraft:custom_name=")
                .append(name)
                .append(",minecraft:lore=[").append(lore).append(']');
        if (config.tokenGlint())
        {
            spec.append(",minecraft:enchantment_glint_override=true");
        }
        spec.append(']');
        ItemStack token = Bukkit.getItemFactory().createItemStack(spec.toString());
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
}
