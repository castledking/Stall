package codes.castled.stall.paper;

import codes.castled.stall.shop.Shop;
import codes.castled.stall.store.ItemCodec;
import codes.castled.stall.store.ShopDataException;
import codes.castled.stall.store.StockCodec;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Large barrels' second 27 slots. They're saved on the barrel itself, in its persistent data, so they are
 * written with the chunk in step with the barrel's own items: a world backup or rollback keeps both
 * halves together, and there is no separate file to fall out of step with the world.
 *
 * <p>While a shop's chunk is loaded its extra slots live in one inventory, shared by the owner's 54-slot
 * view and by trades, and every change is written back to the barrel within a tick. Each entry is only
 * touched on the region that owns its barrel.
 */
final class PaperExtraStock
{
    static final int SIZE = 27;

    private static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("stall:stock"));

    private final Plugin plugin;
    private final StockCodec<ItemStack> codec;
    private final Map<UUID, Entry> loaded = new ConcurrentHashMap<>();

    private static final class Entry
    {
        final Inventory inventory = Bukkit.createInventory(null, SIZE);
        List<String> preserved = List.of();
        /** Text this version can't read, such as a newer version's; never overwritten. */
        String unreadable;
        boolean dirty;
    }

    PaperExtraStock(Plugin plugin, ItemCodec<ItemStack> items)
    {
        this.plugin = plugin;
        this.codec = new StockCodec<>(items, PaperStackRules.INSTANCE);
    }

    /**
     * The live extra slots of the shop's barrel, read from the barrel the first time, or {@code null} if
     * what the barrel holds can't be read by this version.
     */
    Inventory inventory(Shop<ItemStack> shop, Block barrel)
    {
        Entry entry = loaded.computeIfAbsent(shop.id(), id -> read(shop, barrel));
        return entry.unreadable == null ? entry.inventory : null;
    }

    /** Whether the shop's barrel holds nothing in its extra slots, unreadable stacks included. */
    boolean isEmpty(Shop<ItemStack> shop, Block barrel)
    {
        Entry entry = loaded.computeIfAbsent(shop.id(), id -> read(shop, barrel));
        if (entry.unreadable != null || !entry.preserved.isEmpty())
        {
            return false;
        }
        for (ItemStack stack : entry.inventory.getContents())
        {
            if (!BukkitItems.isEmpty(stack))
            {
                return false;
            }
        }
        return true;
    }

    /** Notes a change made through the owner's view; it is written back on the next {@link #flush}. */
    boolean markDirty(Shop<ItemStack> shop)
    {
        Entry entry = loaded.get(shop.id());
        if (entry == null || entry.dirty)
        {
            return false;
        }
        entry.dirty = true;
        return true;
    }

    /** Writes the shop's extra slots back to its barrel, if they changed. */
    void flush(Shop<ItemStack> shop, Block barrel)
    {
        Entry entry = loaded.get(shop.id());
        if (entry == null || !entry.dirty || entry.unreadable != null)
        {
            return;
        }
        entry.dirty = false;
        if (!(barrel.getState() instanceof Container container) || !PaperMarkers.carries(barrel, shop))
        {
            // The barrel was replaced: its extra slots went with it, as its own items did.
            loaded.remove(shop.id());
            return;
        }
        List<ItemStack> slots = new ArrayList<>(SIZE);
        for (ItemStack stack : entry.inventory.getContents())
        {
            slots.add(BukkitItems.isEmpty(stack) ? null : stack.clone());
        }
        container.getPersistentDataContainer().set(KEY, PersistentDataType.STRING,
                codec.encode(new StockCodec.Stock<>(slots, entry.preserved)));
        container.update();
    }

    /** Writes and then forgets every loaded entry in {@code shops}, as their chunk unloads. */
    void unload(Collection<Shop<ItemStack>> shops, java.util.function.Function<Shop<ItemStack>, java.util.Optional<Block>> blocks)
    {
        for (Shop<ItemStack> shop : shops)
        {
            if (loaded.containsKey(shop.id()))
            {
                blocks.apply(shop).ifPresent(barrel -> flush(shop, barrel));
                loaded.remove(shop.id());
            }
        }
    }

    /**
     * Drops the extra slots' items where the barrel was broken. Stacks this version can't read can't be
     * dropped; they go with the barrel and are logged.
     */
    void dropContents(Shop<ItemStack> shop, Block barrel)
    {
        Entry entry = loaded.remove(shop.id());
        if (entry == null)
        {
            entry = read(shop, barrel);
        }
        for (ItemStack stack : entry.inventory.getContents())
        {
            if (!BukkitItems.isEmpty(stack))
            {
                barrel.getWorld().dropItemNaturally(barrel.getLocation().add(0.5, 0.5, 0.5), stack);
            }
        }
        if (entry.unreadable != null || !entry.preserved.isEmpty())
        {
            plugin.getLogger().warning("The large barrel of shop " + shop.id() + " at " + shop.location()
                    + " was broken while holding items this server can't read; they were lost with it.");
        }
    }

    /** Forgets a shop's entry without writing it, after its barrel is gone or replaced. */
    void forget(UUID shop)
    {
        loaded.remove(shop);
    }

    /** Shop ids whose extra slots are loaded, for writing everything back as the plugin stops. */
    Collection<UUID> loadedShops()
    {
        return List.copyOf(loaded.keySet());
    }

    private Entry read(Shop<ItemStack> shop, Block barrel)
    {
        Entry entry = new Entry();
        String text = barrel.getState() instanceof Container container
                ? container.getPersistentDataContainer().get(KEY, PersistentDataType.STRING) : null;
        if (text == null)
        {
            return entry;
        }
        try
        {
            StockCodec.Stock<ItemStack> stock = codec.decode(text, SIZE);
            for (int slot = 0; slot < SIZE; slot++)
            {
                entry.inventory.setItem(slot, stock.slots().get(slot));
            }
            entry.preserved = stock.preserved();
        }
        catch (ShopDataException unreadable)
        {
            entry.unreadable = text;
            plugin.getLogger().log(Level.WARNING, "The large barrel of shop " + shop.id() + " at " + shop.location()
                    + " holds extra stock this version can't read; it is left untouched.", unreadable);
        }
        return entry;
    }
}
