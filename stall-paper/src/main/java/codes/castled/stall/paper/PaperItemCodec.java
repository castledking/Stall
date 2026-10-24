package codes.castled.stall.paper;

import codes.castled.stall.store.ItemCodec;
import codes.castled.stall.store.ItemDecodeException;
import codes.castled.stall.store.StoredItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;

/**
 * The same versioned, gzip-compressed vanilla item NBT as Paper and Fabric use. Spigot has no public
 * binary item codec, so this small bridge binds Minecraft 26.3's unobfuscated codec at startup.
 * No CraftBukkit or Minecraft classes are bundled. All other plugin code compiles against Spigot API.
 */
final class PaperItemCodec implements ItemCodec<ItemStack>
{
    private final Object codec;
    private final Object ops;
    private final Object fixer;
    private final Object itemStackFix;
    private final Method asNative;
    private final Method asBukkit;
    private final Method encodeStart;
    private final Method parse;
    private final Method getOrThrow;
    private final Method writeCompressed;
    private final Method readCompressed;
    private final Method unlimitedHeap;
    private final Method addVersion;
    private final Method getVersion;
    private final Method update;
    private final Constructor<?> dynamic;
    private final Method dynamicValue;
    private final Object nbtOps;
    private final int currentVersion;

    PaperItemCodec()
    {
        try
        {
            Class<?> nativeItem = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> craftItem = Class.forName(Bukkit.getServer().getClass().getPackageName() + ".inventory.CraftItemStack");
            Class<?> dynamicOps = Class.forName("com.mojang.serialization.DynamicOps");
            Class<?> compound = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> nbtIo = Class.forName("net.minecraft.nbt.NbtIo");
            Class<?> nbtUtils = Class.forName("net.minecraft.nbt.NbtUtils");
            Class<?> accounter = Class.forName("net.minecraft.nbt.NbtAccounter");
            Class<?> dynamicType = Class.forName("com.mojang.serialization.Dynamic");

            asNative = craftItem.getMethod("asNMSCopy", ItemStack.class);
            asBukkit = mirrorMethod(craftItem, nativeItem);
            codec = nativeItem.getField("CODEC").get(null);
            encodeStart = Class.forName("com.mojang.serialization.Encoder").getMethod("encodeStart", dynamicOps, Object.class);
            parse = Class.forName("com.mojang.serialization.Decoder").getMethod("parse", dynamicOps, Object.class);
            getOrThrow = Class.forName("com.mojang.serialization.DataResult").getMethod("getOrThrow");

            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object registries = Class.forName("net.minecraft.server.MinecraftServer").getMethod("registryAccess").invoke(server);
            nbtOps = Class.forName("net.minecraft.nbt.NbtOps").getField("INSTANCE").get(null);
            ops = Class.forName("net.minecraft.core.HolderLookup$Provider")
                    .getMethod("createSerializationContext", dynamicOps).invoke(registries, nbtOps);

            writeCompressed = nbtIo.getMethod("writeCompressed", compound, OutputStream.class);
            readCompressed = nbtIo.getMethod("readCompressed", InputStream.class, accounter);
            unlimitedHeap = accounter.getMethod("unlimitedHeap");
            addVersion = nbtUtils.getMethod("addCurrentDataVersion", compound);
            getVersion = nbtUtils.getMethod("getDataVersion", compound, int.class);
            currentVersion = (int) getVersion.invoke(null, addVersion.invoke(null, compound.getConstructor().newInstance()), 0);
            fixer = Class.forName("net.minecraft.util.datafix.DataFixers").getMethod("getDataFixer").invoke(null);
            itemStackFix = Class.forName("net.minecraft.util.datafix.fixes.References").getField("ITEM_STACK").get(null);
            update = Class.forName("com.mojang.datafixers.DataFixer").getMethod("update",
                    Class.forName("com.mojang.datafixers.DSL$TypeReference"), dynamicType, int.class, int.class);
            dynamic = dynamicType.getConstructor(dynamicOps, Object.class);
            dynamicValue = dynamicType.getMethod("getValue");
        }
        catch (ReflectiveOperationException incompatible)
        {
            // Fail before loading shops, so an unsupported server never quarantines otherwise valid items.
            throw new IllegalStateException("Stall could not bind Minecraft 26.3's native item codec", cause(incompatible));
        }
    }

    @Override
    public StoredItem encode(ItemStack item)
    {
        try
        {
            Object nativeItem = asNative.invoke(null, BukkitItems.withCount(item, 1));
            Object tag = getOrThrow.invoke(encodeStart.invoke(codec, ops, nativeItem));
            addVersion.invoke(null, tag);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            writeCompressed.invoke(null, tag, bytes);
            return new StoredItem(item.getType().getKey().toString(), Base64.getEncoder().encodeToString(bytes.toByteArray()));
        }
        catch (ReflectiveOperationException failed)
        {
            throw new IllegalStateException("Could not save a shop item", cause(failed));
        }
    }

    @Override
    public ItemStack decode(StoredItem stored) throws ItemDecodeException
    {
        try
        {
            Object tag = readCompressed.invoke(null, new ByteArrayInputStream(Base64.getDecoder().decode(stored.nbt())),
                    unlimitedHeap.invoke(null));
            int savedVersion = (int) getVersion.invoke(null, tag, 0);
            if (savedVersion > currentVersion)
            {
                throw new ItemDecodeException("saved by a newer Minecraft version (data version " + savedVersion + ")");
            }
            Object upgraded = dynamicValue.invoke(update.invoke(fixer, itemStackFix,
                    dynamic.newInstance(nbtOps, tag), savedVersion, currentVersion));
            Object nativeItem = getOrThrow.invoke(parse.invoke(codec, ops, upgraded));
            ItemStack item = (ItemStack) asBukkit.invoke(null, nativeItem);
            if (BukkitItems.isEmpty(item))
            {
                throw new ItemDecodeException("saved as an empty item");
            }
            return item;
        }
        catch (ReflectiveOperationException | RuntimeException unreadable)
        {
            Throwable reason = cause(unreadable);
            throw new ItemDecodeException("not readable on this server: " + reason.getMessage(), reason);
        }
    }

    private static Throwable cause(Throwable error)
    {
        return error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
    }

    private static Method mirrorMethod(Class<?> craftItem, Class<?> nativeItem) throws NoSuchMethodException
    {
        try
        {
            return craftItem.getMethod("asBukkitMirror", nativeItem);
        }
        catch (NoSuchMethodException spigot)
        {
            return craftItem.getMethod("asCraftMirror", nativeItem);
        }
    }
}
