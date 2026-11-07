package codes.castled.stall.fabric;

import codes.castled.stall.config.SkinTextures;
import codes.castled.stall.config.StallConfig;
import codes.castled.stall.config.StallLang;
import codes.castled.stall.shop.Tokens;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Stall tokens: configured item with a name, a glint and a marker in its custom data, at the same path Paper's
 * persistent data uses (see {@link Tokens}).
 */
final class FabricTokens
{
    private FabricTokens()
    {
    }

    static ItemStack create(int count)
    {
        return create(count, StallConfig.defaults(), StallLang.defaults());
    }

    static ItemStack create(int count, StallConfig config, StallLang lang)
    {
        Item item = resolveItem(config.tokenItem());
        ItemStack token = new ItemStack(item, count);
        token.set(DataComponents.CUSTOM_NAME, FabricText.itemLine(lang.text("token.name")));
        token.set(DataComponents.LORE, new ItemLore(FabricText.itemLines(lang.lines("token.lore"))));
        if (config.tokenGlint())
        {
            token.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }

        if (config.tokenCustomModelData() > 0)
        {
            token.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    List.of((float) config.tokenCustomModelData()),
                    List.of(),
                    List.of(),
                    List.of()
            ));
        }

        if (item == Items.PLAYER_HEAD && !config.tokenSkullTexture().isBlank())
        {
            String base64 = SkinTextures.toBase64(config.tokenSkullTexture());
            if (!base64.isEmpty())
            {
                applyProfile(token, base64);
            }
        }

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

    private static void applyProfile(ItemStack token, String base64)
    {
        try
        {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "");
            try
            {
                Method propsMethod = profile.getClass().getMethod("properties");
                Object props = propsMethod.invoke(profile);
                Method put = props.getClass().getMethod("put", Object.class, Object.class);
                put.invoke(props, "textures", new Property("textures", base64));
            }
            catch (NoSuchMethodException e)
            {
                Method propsMethod = profile.getClass().getMethod("getProperties");
                Object props = propsMethod.invoke(profile);
                Method put = props.getClass().getMethod("put", Object.class, Object.class);
                put.invoke(props, "textures", new Property("textures", base64));
            }
            try
            {
                Method createResolved = ResolvableProfile.class.getMethod("createResolved", GameProfile.class);
                Object resolvable = createResolved.invoke(null, profile);
                token.set(DataComponents.PROFILE, (ResolvableProfile) resolvable);
            }
            catch (NoSuchMethodException e)
            {
                Constructor<ResolvableProfile> ctor = ResolvableProfile.class.getConstructor(GameProfile.class);
                token.set(DataComponents.PROFILE, ctor.newInstance(profile));
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    private static Item resolveItem(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return Items.PAPER;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (!id.contains(":"))
        {
            id = "minecraft:" + id;
        }
        try
        {
            Identifier loc = Identifier.tryParse(id);
            if (loc != null && BuiltInRegistries.ITEM.containsKey(loc))
            {
                Item item = BuiltInRegistries.ITEM.getValue(loc);
                return (item != null && item != Items.AIR) ? item : Items.PAPER;
            }
        }
        catch (Exception ignored)
        {
        }
        return Items.PAPER;
    }
}
