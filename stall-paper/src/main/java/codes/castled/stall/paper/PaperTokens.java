package codes.castled.stall.paper;

import codes.castled.stall.shop.Tokens;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

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
        ItemStack token = ItemStack.of(Material.PAPER, count);
        token.editMeta(meta ->
        {
            meta.displayName(Component.text(Tokens.NAME, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(Tokens.LORE.stream()
                    .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
        });
        return token;
    }

    static boolean isToken(ItemStack stack)
    {
        return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(MARKER);
    }
}
