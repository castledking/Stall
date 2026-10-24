package codes.castled.stall.paper;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.store.ItemDecodeException;
import codes.castled.stall.store.StoredItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Real server checks for item parity, menus, commands, stock protection and the Bukkit transaction adapter. */
public final class ServerChecks
{
    private ServerChecks()
    {
    }

    public static void run(Plugin plugin) throws Exception
    {
        check(Bukkit.isPrimaryThread(), "checks must run on the server thread");
        var field = StallPlugin.class.getDeclaredField("shops");
        field.setAccessible(true);
        PaperShops shops = (PaperShops) field.get(plugin);
        checkCodec(plugin);
        checkCommands(shops);
        checkShop(shops);
        plugin.getLogger().info("Checked native item round trips and upgrades, commands, menus, trades and hopper protection.");
    }

    private static void checkCodec(Plugin plugin) throws Exception
    {
        PaperItemCodec codec = new PaperItemCodec();
        List<ItemStack> items = List.of(
                new ItemStack(Material.EMERALD, 32),
                PaperTokens.create(16),
                Bukkit.getItemFactory().createItemStack("minecraft:diamond_sword[minecraft:damage=42,minecraft:custom_name={text:'Proof',color:'gold',italic:false},minecraft:enchantments={'minecraft:unbreaking':3},minecraft:custom_data={stall_test:{nested:[I;1,2,3]}}]"),
                Bukkit.getItemFactory().createItemStack("minecraft:potion[minecraft:potion_contents={potion:'minecraft:strong_healing'}]"),
                Bukkit.getItemFactory().createItemStack("minecraft:stone[minecraft:max_stack_size=16,minecraft:lore=[{text:'Keep this lore',color:'aqua'}]]"));
        Properties exchange = new Properties();
        for (int i = 0; i < items.size(); i++)
        {
            ItemStack original = items.get(i);
            ItemStack before = original.clone();
            StoredItem stored = codec.encode(original);
            ItemStack loaded = codec.decode(stored);
            check(original.equals(before), "encoding changed the original item");
            check(loaded.getAmount() == 1 && loaded.isSimilar(original), "item metadata changed in a round trip: " + i);
            exchange.setProperty(i + ".id", stored.id());
            exchange.setProperty(i + ".nbt", stored.nbt());
            try
            {
                // Paper's independent public codec must read our bytes, and we must read its bytes.
                var decode = ItemStack.class.getMethod("deserializeBytes", byte[].class);
                ItemStack fromPaper = (ItemStack) decode.invoke(null, java.util.Base64.getDecoder().decode(stored.nbt()));
                check(fromPaper.isSimilar(original), "Paper could not read the shared format");
                byte[] paperBytes = (byte[]) ItemStack.class.getMethod("serializeAsBytes").invoke(BukkitItems.withCount(original, 1));
                check(codec.decode(new StoredItem(stored.id(), java.util.Base64.getEncoder().encodeToString(paperBytes)))
                        .isSimilar(original), "the shared codec could not read Paper bytes");
            }
            catch (NoSuchMethodException spigot)
            {
                // Spigot's lack of these methods is why the native bridge exists.
            }
        }
        Properties fixtures = new Properties();
        try (var stream = ServerChecks.class.getResourceAsStream("/item-fixtures.properties"))
        {
            fixtures.load(stream);
        }
        for (String key : List.of("CURRENT", "LEGACY"))
        {
            check(codec.decode(new StoredItem("minecraft:emerald", fixtures.getProperty(key))).getType() == Material.EMERALD,
                    "could not read " + key + " cross-platform fixture");
        }
        for (String key : List.of("NEWER", "UNKNOWN"))
        {
            try
            {
                codec.decode(new StoredItem("minecraft:emerald", fixtures.getProperty(key)));
                throw new AssertionError("accepted an unreadable " + key + " fixture");
            }
            catch (ItemDecodeException expected)
            {
            }
        }
        String directory = System.getProperty("stall.test.exchangeDir");
        if (directory != null)
        {
            Path path = Path.of(directory);
            Files.createDirectories(path);
            StringWriter text = new StringWriter();
            exchange.store(text, "Real Bukkit-server item codec fixtures");
            Files.writeString(path.resolve(Bukkit.getName() + ".properties"), text.toString());
            try (var files = Files.list(path))
            {
                for (Path fixture : files.filter(file -> file.toString().endsWith(".properties")).toList())
                {
                    Properties other = new Properties();
                    try (var reader = Files.newBufferedReader(fixture)) { other.load(reader); }
                    for (int i = 0; i < items.size(); i++)
                    {
                        check(codec.decode(new StoredItem(other.getProperty(i + ".id"), other.getProperty(i + ".nbt")))
                                .isSimilar(items.get(i)), "cross-server item mismatch in " + fixture);
                    }
                }
            }
        }
    }

    private static void checkCommands(PaperShops shops)
    {
        StallCommands commands = new StallCommands(shops);
        List<String> messages = new ArrayList<>();
        CommandSender ordinary = sender(messages, false);
        commands.onCommand(ordinary, null, "stall", new String[]{"admin", "give", "Nobody", "1"});
        check(messages.getLast().contains("permission"), "admin command bypassed its permission");
        commands.onCommand(ordinary, null, "stall", new String[]{"list", "Nobody"});
        check(messages.getLast().contains("permission"), "listing someone else's shops bypassed its permission");
        check(!commands.onTabComplete(ordinary, null, "stall", new String[]{""}).contains("admin"), "admin tab leaked");
        CommandSender admin = sender(messages, true);
        for (String amount : List.of("0", "-1", "65", "999999999999", "nope"))
        {
            commands.onCommand(admin, null, "stall", new String[]{"token", amount});
            check(messages.getLast().contains("whole number from 1 to 64"), "invalid token amount accepted: " + amount);
        }
        commands.onCommand(admin, null, "stall", new String[]{"token", "1"});
        check(messages.getLast().contains("Only players"), "console received tokens");
        check(commands.onTabComplete(admin, null, "stall", new String[]{"ad"}).equals(List.of("admin")), "tab prefix failed");
    }

    private static void checkShop(PaperShops shops)
    {
        World world = Bukkit.getWorlds().getFirst();
        Block block = world.getBlockAt(0, 80, 0);
        block.setType(Material.BARREL);
        Inventory stock = ((Barrel) block.getState()).getInventory();
        Inventory storage = Bukkit.createInventory(null, 36);
        UUID buyerId = UUID.randomUUID();
        PlayerInventory inventory = proxy(PlayerInventory.class, (method, args) -> switch (method)
        {
            case "getStorageContents" -> storage.getContents();
            case "getMaxStackSize" -> 64;
            case "getItemInMainHand", "getItemInOffHand" -> new ItemStack(Material.AIR);
            case "setItem" -> { storage.setItem((int) args[0], (ItemStack) args[1]); yield null; }
            default -> throw new UnsupportedOperationException("inventory: " + method);
        });
        Player buyer = proxy(Player.class, (method, args) -> switch (method)
        {
            case "getUniqueId" -> buyerId;
            case "getName" -> "StallTestBuyer";
            case "isOnline" -> true;
            case "getWorld" -> world;
            case "getEyeLocation" -> block.getLocation().add(0.5, 0.5, 0.5);
            case "getInventory" -> inventory;
            case "hasPermission" -> false;
            case "sendMessage" -> null;
            default -> throw new UnsupportedOperationException("player: " + method);
        });
        Shop<ItemStack> shop = new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "TestOwner", PaperShops.locate(block), false);
        Offer<ItemStack> offer = new Offer<>(UUID.randomUUID(), OfferSlots.of(List.of(
                new OfferItem<>(new ItemStack(Material.EMERALD), 1),
                new OfferItem<>(new ItemStack(Material.GOLD_INGOT), 2))), OfferSlots.of(List.of(
                new OfferItem<>(new ItemStack(Material.BREAD), 16),
                new OfferItem<>(new ItemStack(Material.COAL), 4))));
        shop.addOffer(offer);
        shops.registry().add(shop);
        PaperMarkers.stamp(block, shop);
        try
        {
            check(shops.isActive(shop) && shops.stock(shop).isPresent(), "could not link the stamped barrel");
            storage.addItem(new ItemStack(Material.EMERALD, 2), new ItemStack(Material.GOLD_INGOT, 4));
            stock.addItem(new ItemStack(Material.BREAD, 32), new ItemStack(Material.COAL, 8));
            BuyerMenu menu = new BuyerMenu(shops, buyer, shop);
            check(menu.getInventory().getSize() == 9, "buyer menu has the wrong size");
            shops.buy(menu, offer.id());
            check(count(storage, Material.BREAD) == 16 && count(storage, Material.COAL) == 4, "buyer did not receive both rewards");
            check(count(stock, Material.EMERALD) == 1 && count(stock, Material.GOLD_INGOT) == 2, "shop did not receive both payments");
            shops.buy(menu, offer.id());
            check(count(storage, Material.BREAD) == 16, "same-tick repeat bought twice");

            // A failed trade must leave both inventories exactly unchanged.
            storage.clear();
            ItemStack[] stockBefore = stock.getContents();
            for (int tick = 0; tick < 4; tick++) shops.tick();
            shops.buy(menu, offer.id());
            check(Arrays.equals(stockBefore, stock.getContents()) && storage.isEmpty(), "failed trade changed inventory contents");

            OwnerMenu owner = new OwnerMenu(shops, buyer, shop);
            check(owner.getInventory().getSize() == 18, "owner menu must show one offer and one new row");
            for (ClickType click : ClickType.values())
            {
                InventoryClickEvent event = click(menu, buyer, inventory, 0, click);
                menu.handleClick(event);
                check(event.isCancelled(), "buyer display click escaped: " + click);
                // A placeholder in the new row: item movement must always be cancelled.
                event = click(owner, buyer, inventory, 10, click);
                owner.handleClick(event);
                check(event.isCancelled(), "owner display click escaped: " + click);
            }
            ShopListener listener = new ShopListener(shops);
            Inventory hopper = Bukkit.createInventory(null, InventoryType.HOPPER);
            var pull = new InventoryMoveItemEvent(stock, new ItemStack(Material.BREAD), hopper, true);
            listener.onHopperPull(pull);
            check(pull.isCancelled(), "hopper drained shop stock");
            var push = new InventoryMoveItemEvent(hopper, new ItemStack(Material.BREAD), stock, true);
            listener.onHopperPull(push);
            check(!push.isCancelled(), "hopper input was blocked");
            var breaking = new BlockBreakEvent(block, buyer);
            listener.onBreak(breaking);
            check(breaking.isCancelled(), "another player could break a shop");
            check(PaperTokens.isToken(PaperTokens.create(1)) && !PaperTokens.isToken(new ItemStack(Material.PAPER)), "token marker failed");
        }
        finally
        {
            shops.remove(shop, null, false);
            block.setType(Material.AIR);
        }
    }

    private static InventoryClickEvent click(StallMenu menu, Player player, PlayerInventory bottom, int slot, ClickType click)
    {
        InventoryView view = proxy(InventoryView.class, (method, args) -> switch (method)
        {
            case "getPlayer" -> player;
            case "getTopInventory" -> menu.getInventory();
            case "getBottomInventory" -> bottom;
            case "convertSlot" -> args[0];
            case "getCursor" -> new ItemStack(Material.AIR);
            case "getInventory" -> menu.getInventory();
            default -> throw new UnsupportedOperationException("view: " + method);
        });
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
    }

    private static long count(Inventory inventory, Material type)
    {
        return Arrays.stream(inventory.getContents()).filter(item -> item != null && item.getType() == type)
                .mapToLong(ItemStack::getAmount).sum();
    }

    private static CommandSender sender(List<String> messages, boolean permitted)
    {
        return proxy(CommandSender.class, (method, args) -> switch (method)
        {
            case "hasPermission" -> permitted;
            case "sendMessage" -> { messages.add((String) args[0]); yield null; }
            default -> throw new UnsupportedOperationException("sender: " + method);
        });
    }

    @FunctionalInterface
    private interface Call { Object call(String method, Object[] args); }

    private static <T> T proxy(Class<T> type, Call call)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) ->
        {
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("toString")) return "Stall test " + type.getSimpleName();
            return call.call(method.getName(), args);
        }));
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
