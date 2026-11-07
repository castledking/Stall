package codes.castled.stall.paper;

import codes.castled.stall.config.LegacyText;
import codes.castled.stall.config.SkinTextures;
import codes.castled.stall.config.StallConfig;
import codes.castled.stall.config.StallLang;
import codes.castled.stall.shop.Tokens;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stall tokens: item with a name, a glint and a {@code stall:token} marker in its persistent data, which
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
        String itemId = config.tokenItem();
        if (itemId == null || itemId.isBlank())
        {
            itemId = "minecraft:paper";
        }
        else
        {
            itemId = itemId.trim().toLowerCase(Locale.ROOT);
            if (!itemId.contains(":"))
            {
                itemId = "minecraft:" + itemId;
            }
        }

        String name = LegacyText.itemSnbt(lang.text("token.name"));
        String lore = lang.lines("token.lore").stream()
                .map(LegacyText::itemSnbt)
                .collect(Collectors.joining(","));

        StringBuilder spec = new StringBuilder(itemId)
                .append("[minecraft:custom_name=").append(name)
                .append(",minecraft:lore=[").append(lore).append(']');

        if (config.tokenGlint())
        {
            spec.append(",minecraft:enchantment_glint_override=true");
        }

        boolean isHead = itemId.equals("minecraft:player_head");
        String base64Skin = isHead ? SkinTextures.toBase64(config.tokenSkullTexture()) : "";
        if (!base64Skin.isEmpty())
        {
            spec.append(",minecraft:profile={properties:[{name:\"textures\",value:\"")
                    .append(base64Skin)
                    .append("\"}]}");
        }

        spec.append(']');

        ItemStack token;
        try
        {
            token = Bukkit.getItemFactory().createItemStack(spec.toString());
        }
        catch (Throwable fallback)
        {
            // Fallback for older Spigot item factory component parsing
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(itemId);
            if (mat == null)
            {
                mat = org.bukkit.Material.PAPER;
            }
            token = new ItemStack(mat);
        }

        token.setAmount(count);
        BukkitItems.editMeta(token, meta ->
        {
            meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
            if (config.tokenCustomModelData() > 0)
            {
                try
                {
                    meta.setCustomModelData(config.tokenCustomModelData());
                }
                catch (Throwable ignored)
                {
                }
            }
            if (meta instanceof SkullMeta skullMeta && !base64Skin.isEmpty())
            {
                applySkullProfile(skullMeta, base64Skin);
            }
        });
        return token;
    }

    static boolean isToken(ItemStack stack)
    {
        return !BukkitItems.isEmpty(stack) && stack.getItemMeta().getPersistentDataContainer().has(MARKER);
    }

    private static void applySkullProfile(SkullMeta meta, String base64Skin)
    {
        // 1. Try Paper createProfile API reflectively
        try
        {
            Method createProfile = Bukkit.class.getMethod("createProfile", UUID.class, String.class);
            Object profile = createProfile.invoke(null, UUID.randomUUID(), "");
            Class<?> propClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
            Constructor<?> propCtor = propClass.getConstructor(String.class, String.class);
            Object prop = propCtor.newInstance("textures", base64Skin);
            Method setProp = profile.getClass().getMethod("setProperty", propClass);
            setProp.invoke(profile, prop);
            Method setPlayerProfile = meta.getClass().getMethod("setPlayerProfile", profile.getClass().getInterfaces()[0]);
            setPlayerProfile.invoke(meta, profile);
            return;
        }
        catch (Throwable ignored)
        {
        }

        // 2. Try authlib GameProfile reflection (works on Paper and Spigot)
        try
        {
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Constructor<?> gpCtor = gameProfileClass.getConstructor(UUID.class, String.class);
            Object gameProfile = gpCtor.newInstance(UUID.randomUUID(), "");
            Method getProperties = gameProfileClass.getMethod("getProperties");
            Object properties = getProperties.invoke(gameProfile);
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Constructor<?> propCtor = propertyClass.getConstructor(String.class, String.class);
            Object property = propCtor.newInstance("textures", base64Skin);
            Method put = properties.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(properties, "textures", property);

            Field profileField = null;
            Class<?> current = meta.getClass();
            while (current != null && profileField == null)
            {
                try
                {
                    profileField = current.getDeclaredField("profile");
                }
                catch (NoSuchFieldException ignored)
                {
                    current = current.getSuperclass();
                }
            }
            if (profileField != null)
            {
                profileField.setAccessible(true);
                profileField.set(meta, gameProfile);
            }
        }
        catch (Throwable ignored)
        {
        }
    }
}
