package codes.castled.stall.fabric;

import codes.castled.stall.store.ShopDataException;
import codes.castled.stall.store.StockCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A large barrel's second 27 slots, saved on the barrel itself as a data attachment so they are written
 * with the chunk, in step with the barrel's own items: a world backup or rollback keeps both halves
 * together, and there is no separate file to fall out of step with the world. Every change is written to
 * the barrel straight away. One instance per barrel is shared by the owner's 54-slot view and by trades.
 */
final class ExtraStock extends SimpleContainer
{
    static final int SIZE = 27;

    private final BlockEntity barrel;
    private final Consumer<String> save;
    private final StockCodec<ItemStack> codec;
    private final BooleanSupplier shopActive;
    private List<String> preserved = List.of();
    /** Text this version can't read, such as a newer version's; never overwritten. */
    private String unreadable;
    private boolean loading;

    /**
     * @param saved what the barrel holds, as last saved, or {@code null} for nothing
     * @param save writes new text to the barrel
     */
    ExtraStock(BlockEntity barrel, String saved, Consumer<String> save, StockCodec<ItemStack> codec,
            BooleanSupplier shopActive, Logger logger)
    {
        super(SIZE);
        this.barrel = barrel;
        this.save = save;
        this.codec = codec;
        this.shopActive = shopActive;
        if (saved == null)
        {
            return;
        }
        try
        {
            StockCodec.Stock<ItemStack> stock = codec.decode(saved, SIZE);
            loading = true;
            for (int slot = 0; slot < SIZE; slot++)
            {
                ItemStack item = stock.slots().get(slot);
                setItem(slot, item == null ? ItemStack.EMPTY : item);
            }
            preserved = stock.preserved();
        }
        catch (ShopDataException failure)
        {
            unreadable = saved;
            logger.warn("The large barrel at {} holds extra stock this version can't read; it is left untouched.",
                    barrel.getBlockPos(), failure);
        }
        finally
        {
            loading = false;
        }
    }

    /** Whether this version can read, and so write, what the barrel holds. */
    boolean isReadable()
    {
        return unreadable == null;
    }

    /** Whether nothing at all is stored, unreadable stacks included. */
    boolean holdsNothing()
    {
        return unreadable == null && preserved.isEmpty() && isEmpty();
    }

    /** Stacks this version couldn't read, which can't be dropped. */
    boolean holdsUnreadable()
    {
        return unreadable != null || !preserved.isEmpty();
    }

    @Override
    public void setChanged()
    {
        super.setChanged();
        if (loading || unreadable != null)
        {
            return;
        }
        List<ItemStack> slots = new ArrayList<>(SIZE);
        for (int slot = 0; slot < SIZE; slot++)
        {
            ItemStack stack = getItem(slot);
            slots.add(stack.isEmpty() ? null : stack.copy());
        }
        save.accept(codec.encode(new StockCodec.Stock<>(slots, preserved)));
    }

    /** Open only while the barrel is in reach and still this shop's, so the view closes with the shop. */
    @Override
    public boolean stillValid(Player player)
    {
        return shopActive.getAsBoolean() && Container.stillValidBlockEntity(barrel, player);
    }
}
