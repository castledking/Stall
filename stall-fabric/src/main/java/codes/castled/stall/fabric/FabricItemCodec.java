package codes.castled.stall.fabric;

import codes.castled.stall.store.ItemCodec;
import codes.castled.stall.store.ItemDecodeException;
import codes.castled.stall.store.StoredItem;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * Saves items in exactly the format Paper's {@code ItemStack#serializeAsBytes} uses, built from the same
 * vanilla pieces Paper builds it from: the item codec written as NBT, the current {@code DataVersion}
 * added, then gzip. Reading runs the data fixer from the saved version, as Paper does.
 */
final class FabricItemCodec implements ItemCodec<ItemStack>
{
    private final RegistryOps<Tag> ops;

    FabricItemCodec(HolderLookup.Provider registries)
    {
        this.ops = registries.createSerializationContext(NbtOps.INSTANCE);
    }

    @Override
    public StoredItem encode(ItemStack item)
    {
        Tag encoded = ItemStack.CODEC.encodeStart(ops, item.copyWithCount(1)).getOrThrow();
        if (!(encoded instanceof CompoundTag tag))
        {
            throw new IllegalStateException("An item saved as " + encoded.getType() + " instead of a compound");
        }
        NbtUtils.addCurrentDataVersion(tag);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try
        {
            NbtIo.writeCompressed(tag, bytes);
        }
        catch (IOException impossible)
        {
            throw new UncheckedIOException(impossible);
        }
        return new StoredItem(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }

    @Override
    public ItemStack decode(StoredItem stored) throws ItemDecodeException
    {
        CompoundTag tag;
        try
        {
            tag = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(stored.nbt())),
                    NbtAccounter.unlimitedHeap());
        }
        catch (IOException | IllegalArgumentException unreadable)
        {
            throw new ItemDecodeException("not a readable item", unreadable);
        }
        int saved = NbtUtils.getDataVersion(tag, 0);
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        if (saved > current)
        {
            throw new ItemDecodeException("saved by a newer Minecraft version (data version " + saved + ")");
        }
        Tag upgraded = DataFixers.getDataFixer()
                .update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, (Tag) tag), saved, current)
                .getValue();
        return ItemStack.CODEC.parse(ops, upgraded).getOrThrow(ItemDecodeException::new);
    }
}
