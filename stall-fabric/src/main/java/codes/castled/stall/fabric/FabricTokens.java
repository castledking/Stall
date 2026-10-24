package codes.castled.stall.fabric;

import codes.castled.stall.shop.Tokens;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Stall tokens: paper with a name, a glint and a marker in its custom data, at the same path Paper's
 * persistent data uses (see {@link Tokens}).
 */
final class FabricTokens
{
    private FabricTokens()
    {
    }

    static ItemStack create(int count)
    {
        ItemStack token = new ItemStack(Items.PAPER, count);
        token.set(DataComponents.CUSTOM_NAME, Component.literal(Tokens.NAME)
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
        token.set(DataComponents.LORE, new ItemLore(Tokens.LORE.stream()
                .<Component>map(line -> Component.literal(line)
                        .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))
                .toList()));
        token.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        CompoundTag values = new CompoundTag();
        values.putByte(Tokens.MARKER, (byte) 1);
        CompoundTag data = new CompoundTag();
        data.put(Tokens.BUKKIT_VALUES, values);
        token.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return token;
    }

    static boolean isToken(ItemStack stack)
    {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getCompoundOrEmpty(Tokens.BUKKIT_VALUES).contains(Tokens.MARKER);
    }
}
