package codes.castled.stall.fabric;

import codes.castled.stall.store.ItemDecodeException;
import codes.castled.stall.store.StoredItem;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers.PendingComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Fabric item codec against vanilla's own registries, and against items written in Paper's format by
 * an independent NBT writer (gzip-compressed, named root compound, as {@code NbtIo.writeCompressed} does).
 */
class FabricItemCodecTest
{
    @Test
    void explosionFilteringKeepsVanillasMutableListContract()
    {
        var affected = ShopProtection.filterExplosion(null, List.of(net.minecraft.core.BlockPos.ZERO));
        affected.clear();
        assertTrue(affected.isEmpty());
    }
    /** {id: "minecraft:emerald", count: 1, DataVersion: 5023}, Minecraft 26.3's data version. */
    private static final String PAPER_EMERALD = "H4sIAAAAAAAC/+NiYOBgYMpMYRDMzcxLTS5KTCuxSs1NLUrMSWFmYE3OL80rYWBgYGRm4HZJLEkMSy0qzszPY2AQns8AAGNYAo06AAAA";

    /** {id: "minecraft:emerald", Count: 1b, DataVersion: 3700}: the layout before 1.20.5 moved items to components. */
    private static final String LEGACY_EMERALD = "H4sIAAAAAAAC/+NiYOBgYMpMYRDMzcxLTS5KTCuxSs1NLUrMSWFkYHXOL80rYWRm4HZJLEkMSy0qzszPY2DgK2EAAGMbRA03AAAA";

    /** An emerald from a data version far beyond this server's. */
    private static final String FUTURE_EMERALD = "H4sIAAAAAAAC/+NiYOBgYMpMYRDMzcxLTS5KTCuxSs1NLUrMSWFmYE3OL80rYWBgYGRm4HZJLEkMSy0qzszPY2Bsm88AAB1FZs46AAAA";

    /** An item from a mod that isn't installed. */
    private static final String MODDED_ITEM = "H4sIAAAAAAAC/+NiYOBgYMpMYeBJz89Lzc1PsSoqTapkZmBNzi/NK2FgYGBkZuB2SSxJDEstKs7Mz2NgEJ7PAAA55En5NQAAAA==";

    private static FabricItemCodec codec;

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider registries = VanillaRegistries.createWorldLookup();
        // A running server binds items' default components once its registries load; do the same here.
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(PendingComponents::apply);
        codec = new FabricItemCodec(registries);
    }

    @Test
    void itemsKeepEveryComponent() throws ItemDecodeException
    {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Blade"));
        sword.setDamageValue(42);

        StoredItem stored = codec.encode(sword);
        ItemStack read = codec.decode(stored);

        assertEquals("minecraft:diamond_sword", stored.id());
        assertTrue(ItemStack.isSameItemSameComponents(sword, read));
        assertEquals(1, read.getCount());
    }

    @Test
    void writesWhatPaperWrites() throws IOException
    {
        StoredItem stored = codec.encode(new ItemStack(Items.EMERALD, 5));
        CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(stored.nbt())),
                NbtAccounter.unlimitedHeap());

        assertEquals("minecraft:emerald", tag.getStringOr("id", ""));
        assertEquals(1, tag.getIntOr("count", 0), "only one of the item is kept");
        assertEquals(SharedConstants.getCurrentVersion().dataVersion().version(), tag.getIntOr("DataVersion", 0));
    }

    @Test
    void readsPaperFormat() throws ItemDecodeException
    {
        ItemStack read = codec.decode(new StoredItem("minecraft:emerald", PAPER_EMERALD));
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.EMERALD), read));
    }

    @Test
    void readsRealSpigotItemsAndMatchesItsTokens() throws IOException, ItemDecodeException
    {
        Properties fixtures = new Properties();
        try (var stream = getClass().getResourceAsStream("/spigot-items.properties"))
        {
            assertNotNull(stream);
            fixtures.load(stream);
        }
        for (int i = 0; i < 5; i++)
        {
            ItemStack item = codec.decode(new StoredItem(fixtures.getProperty(i + ".id"), fixtures.getProperty(i + ".nbt")));
            assertEquals(1, item.getCount());
            assertTrue(ItemStack.isSameItemSameComponents(item, codec.decode(codec.encode(item))));
        }
        ItemStack token = codec.decode(new StoredItem("minecraft:paper", fixtures.getProperty("1.nbt")));
        assertTrue(FabricTokens.isToken(token));
        assertTrue(ItemStack.isSameItemSameComponents(FabricTokens.create(1), token), "tokens must have identical components on every platform");
        ItemStack sword = codec.decode(new StoredItem("minecraft:diamond_sword", fixtures.getProperty("2.nbt")));
        assertEquals(42, sword.getDamageValue());
        assertEquals("Proof", sword.getHoverName().getString());
        assertEquals(3, sword.getEnchantments().entrySet().iterator().next().getIntValue());
        assertNotNull(sword.get(DataComponents.CUSTOM_DATA));
        ItemStack potion = codec.decode(new StoredItem("minecraft:potion", fixtures.getProperty("3.nbt")));
        assertNotNull(potion.get(DataComponents.POTION_CONTENTS));
        ItemStack stone = codec.decode(new StoredItem("minecraft:stone", fixtures.getProperty("4.nbt")));
        assertEquals(16, stone.getMaxStackSize());
        assertEquals("Keep this lore", stone.get(DataComponents.LORE).lines().getFirst().getString());
    }

    @Test
    void upgradesItemsFromOlderVersions() throws ItemDecodeException
    {
        ItemStack read = codec.decode(new StoredItem("minecraft:emerald", LEGACY_EMERALD));
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.EMERALD), read));
    }

    @Test
    void refusesItemsFromNewerVersions()
    {
        assertThrows(ItemDecodeException.class, () -> codec.decode(new StoredItem("minecraft:emerald", FUTURE_EMERALD)));
    }

    @Test
    void refusesItemsThatDontExistHere()
    {
        assertThrows(ItemDecodeException.class, () -> codec.decode(new StoredItem("gonemod:ruby", MODDED_ITEM)));
        assertThrows(ItemDecodeException.class, () -> codec.decode(new StoredItem("minecraft:emerald", "not base64!")));
    }

    /** Leaves Fabric-encoded items where the Paper smoke test picks them up, to check Paper reads them. */
    @Test
    void writesFixturesForPaper() throws IOException
    {
        String directory = System.getProperty("stall.fixtureDir");
        if (directory == null)
        {
            return;
        }
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Fabric Blade"));
        named.setDamageValue(7);
        Path file = Path.of(directory, "fabric-items.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, List.of(
                "EMERALD=" + codec.encode(new ItemStack(Items.EMERALD)).nbt(),
                "BLADE=" + codec.encode(named).nbt()));
    }
}
