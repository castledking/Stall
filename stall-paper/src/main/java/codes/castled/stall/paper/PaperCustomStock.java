package codes.castled.stall.paper;

import codes.castled.stall.config.CustomBlockSpec;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.store.ItemCodec;
import codes.castled.stall.store.StockCodec;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Stock management and persistence for custom block models (CraftEngine, Nexo, Oraxen)
 * supporting arbitrary sizes (9, 18, 27, 36, 45, 54 slots).
 */
public final class PaperCustomStock
{
    private static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("stall:custom_stock"));

    private final Plugin plugin;
    private final Path directory;
    private final StockCodec<ItemStack> codec;
    private final Map<UUID, Entry> loaded = new ConcurrentHashMap<>();

    private static final class Entry
    {
        final Inventory inventory;
        List<String> preserved = List.of();
        boolean dirty;

        Entry(Inventory inventory)
        {
            this.inventory = inventory;
        }
    }

    public PaperCustomStock(Plugin plugin, Path directory, ItemCodec<ItemStack> items)
    {
        this.plugin = plugin;
        this.directory = directory.resolve("custom_stock");
        this.codec = new StockCodec<>(items, PaperStackRules.INSTANCE);
        try
        {
            Files.createDirectories(this.directory);
        }
        catch (IOException ignored)
        {
        }
    }

    public Inventory inventory(Shop<ItemStack> shop, Block block, CustomBlockSpec spec)
    {
        Entry entry = loaded.computeIfAbsent(shop.id(), id -> read(shop, block, spec));
        return entry.inventory;
    }

    public boolean markDirty(Shop<ItemStack> shop)
    {
        Entry entry = loaded.get(shop.id());
        if (entry == null || entry.dirty)
        {
            return false;
        }
        entry.dirty = true;
        return true;
    }

    public void flush(Shop<ItemStack> shop, Block block)
    {
        Entry entry = loaded.get(shop.id());
        if (entry == null || !entry.dirty)
        {
            return;
        }
        entry.dirty = false;
        int size = entry.inventory.getSize();
        List<ItemStack> slots = new ArrayList<>(size);
        for (ItemStack stack : entry.inventory.getContents())
        {
            slots.add(BukkitItems.isEmpty(stack) ? null : stack.clone());
        }
        String encoded = codec.encode(new StockCodec.Stock<>(slots, entry.preserved));

        // Save to file
        try
        {
            Path file = directory.resolve(shop.id() + ".json");
            Path temp = directory.resolve(shop.id() + ".tmp");
            Files.writeString(temp, encoded);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException ex)
        {
            plugin.getLogger().log(Level.WARNING, "Could not save custom stock for shop " + shop.id(), ex);
        }

        // Also save to TileState PDC if the custom block is a tile entity
        if (block != null && block.getState() instanceof TileState tile)
        {
            tile.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, encoded);
            tile.update();
        }
    }

    public void dropContents(Shop<ItemStack> shop, Block block, CustomBlockSpec spec)
    {
        Entry entry = loaded.remove(shop.id());
        if (entry == null)
        {
            entry = read(shop, block, spec);
        }
        for (ItemStack stack : entry.inventory.getContents())
        {
            if (!BukkitItems.isEmpty(stack))
            {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stack);
            }
        }
        try
        {
            Files.deleteIfExists(directory.resolve(shop.id() + ".json"));
        }
        catch (IOException ignored)
        {
        }
    }

    public void forget(UUID shopId)
    {
        loaded.remove(shopId);
    }

    public void flushAll()
    {
        for (Map.Entry<UUID, Entry> mapEntry : loaded.entrySet())
        {
            if (mapEntry.getValue().dirty)
            {
                mapEntry.getValue().dirty = false;
                int size = mapEntry.getValue().inventory.getSize();
                List<ItemStack> slots = new ArrayList<>(size);
                for (ItemStack stack : mapEntry.getValue().inventory.getContents())
                {
                    slots.add(BukkitItems.isEmpty(stack) ? null : stack.clone());
                }
                String encoded = codec.encode(new StockCodec.Stock<>(slots, mapEntry.getValue().preserved));
                try
                {
                    Path file = directory.resolve(mapEntry.getKey() + ".json");
                    Path temp = directory.resolve(mapEntry.getKey() + ".tmp");
                    Files.writeString(temp, encoded);
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (IOException ex)
                {
                    plugin.getLogger().log(Level.WARNING, "Could not save custom stock for shop " + mapEntry.getKey(), ex);
                }
            }
        }
    }

    private Entry read(Shop<ItemStack> shop, Block block, CustomBlockSpec spec)
    {
        int size = spec.size();
        String title = spec.title();
        Inventory inv = Bukkit.createInventory(null, size, title);
        Entry entry = new Entry(inv);

        String text = null;
        Path file = directory.resolve(shop.id() + ".json");
        if (Files.exists(file))
        {
            try
            {
                text = Files.readString(file);
            }
            catch (IOException ignored)
            {
            }
        }
        if (text == null && block != null && block.getState() instanceof TileState tile)
        {
            text = tile.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        }

        if (text != null && !text.isBlank())
        {
            try
            {
                StockCodec.Stock<ItemStack> stock = codec.decode(text, size);
                for (int slot = 0; slot < Math.min(size, stock.slots().size()); slot++)
                {
                    inv.setItem(slot, stock.slots().get(slot));
                }
                entry.preserved = stock.preserved();
            }
            catch (Exception ex)
            {
                plugin.getLogger().log(Level.WARNING, "Could not decode stock for custom shop " + shop.id(), ex);
            }
        }
        return entry;
    }
}
