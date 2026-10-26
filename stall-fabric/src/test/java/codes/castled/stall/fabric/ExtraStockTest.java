package codes.castled.stall.fabric;

import codes.castled.stall.store.StockCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers.PendingComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A large barrel's extra slots against vanilla's own barrel and containers. */
class ExtraStockTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger("ExtraStockTest");
    private static StockCodec<ItemStack> codec;

    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider registries = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(PendingComponents::apply);
        codec = new StockCodec<>(new FabricItemCodec(registries), FabricStackRules.INSTANCE);
    }

    private static BarrelBlockEntity barrel()
    {
        return new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
    }

    /** Stands in for the barrel's data attachment, which Fabric API adds by mixin at run time. */
    private static final class Saved
    {
        String text;

        ExtraStock open(BarrelBlockEntity barrel)
        {
            return new ExtraStock(barrel, text, written -> text = written, codec, () -> true, LOGGER);
        }
    }

    @Test
    void extraSlotsAreSavedOnTheBarrelAndReadBack()
    {
        BarrelBlockEntity barrel = barrel();
        Saved saved = new Saved();
        ExtraStock extra = saved.open(barrel);
        assertTrue(extra.holdsNothing());

        extra.setItem(3, new ItemStack(Items.GOLD_INGOT, 54));

        ExtraStock reread = saved.open(barrel);
        assertTrue(reread.getItem(3).is(Items.GOLD_INGOT));
        assertEquals(54, reread.getItem(3).getCount(), "the count is saved beside the item");
        assertFalse(reread.holdsNothing());
    }

    @Test
    void aLargeBarrelReadsAsOneContainerOfBothHalves()
    {
        BarrelBlockEntity barrel = barrel();
        Saved saved = new Saved();
        ExtraStock extra = saved.open(barrel);
        CompoundContainer both = new CompoundContainer(barrel, extra);

        assertEquals(54, both.getContainerSize());
        both.setItem(30, new ItemStack(Items.DIAMOND, 3));
        both.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        both.setChanged();

        assertEquals(3, extra.getItem(3).getCount(), "the lower half is the extra slots");
        assertTrue(barrel.getItem(0).is(Items.IRON_INGOT), "the upper half is the barrel itself");
        assertEquals(3, saved.open(barrel).getItem(3).getCount(),
                "changes through the joined container are saved on the barrel");
    }

    @Test
    void onlyBarrelsChestsAndTrappedChestsBecomeShops()
    {
        assertTrue(FabricContainers.isSupported(Blocks.BARREL.defaultBlockState()));
        assertTrue(FabricContainers.isSupported(Blocks.CHEST.defaultBlockState()));
        assertTrue(FabricContainers.isSupported(Blocks.TRAPPED_CHEST.defaultBlockState()));
        for (String copper : new String[]{"copper_chest", "waxed_oxidized_copper_chest"})
        {
            assertFalse(FabricContainers.isSupported(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(copper))
                    .defaultBlockState()), "copper golems empty copper chests");
        }
        assertFalse(FabricContainers.isSupported(Blocks.ENDER_CHEST.defaultBlockState()));
        assertEquals("minecraft:trapped_chest", FabricContainers.id(Blocks.TRAPPED_CHEST.defaultBlockState()));
    }
}
