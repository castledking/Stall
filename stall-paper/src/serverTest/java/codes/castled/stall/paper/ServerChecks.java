package codes.castled.stall.paper;

import codes.castled.stall.offer.Offer;
import codes.castled.stall.offer.OfferItem;
import codes.castled.stall.offer.OfferSlots;
import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.ShopLocation;
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
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.ArmorStand;

/** Real server checks for item parity, menus, commands, stock protection and the Bukkit transaction adapter. */
public final class ServerChecks
{
    private ServerChecks()
    {
    }

    public static void start(Plugin plugin, Consumer<Throwable> done)
    {
        Runnable checks = () ->
        {
            try
            {
                run(plugin);
                if (FoliaSupport.PRESENT)
                {
                    ArmorStand stand = Bukkit.getWorlds().getFirst().spawn(new org.bukkit.Location(Bukkit.getWorlds().getFirst(), 0.5, 85, 0.5), ArmorStand.class);
                    var field = StallPlugin.class.getDeclaredField("shops");
                    field.setAccessible(true);
                    PaperShops shops = (PaperShops) field.get(plugin);
                    World world = stand.getWorld();
                    Shop<ItemStack> distant = new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "DistantOwner",
                            new codes.castled.stall.shop.ShopLocation(world.getKey().toString(), 8192, 80, 8192), true);
                    check(!FoliaSupport.owns(world, 512, 512), "test chunks did not form separate regions");
                    // Setup owns the async chunk request; unlike production sweeps it may keep the test region alive.
                    SchedulerAdapter.region(plugin, world, 512, 512, () ->
                    {
                        try
                        {
                            check(FoliaSupport.owns(world, 512, 512), "region task ran on the wrong region");
                            Block anchor = world.getBlockAt(8192, 80, 8192);
                            anchor.setType(Material.BARREL);
                            PaperMarkers.stamp(anchor, distant);
                            shops.registry().add(distant);
                            check(shops.stock(distant).isPresent(), "owned-region stock was refused");
                            shops.scheduler().entity(stand, () ->
                            {
                                try
                                {
                                    check(FoliaSupport.owns(stand), "entity task ran on the wrong region");
                                    check(shops.block(distant).isEmpty() && shops.stock(distant).isEmpty(), "foreign-region stock was accessed");
                                    shops.reconcile(distant);
                                    check(shops.registry().isCurrent(distant), "foreign-region reconciliation removed a shop");
                                    stand.remove();
                                    SchedulerAdapter.region(plugin, world, 512, 512, () ->
                                    {
                                        try
                                        {
                                            world.getBlockAt(8192, 80, 8192).setType(Material.AIR);
                                            shops.reconcile(distant);
                                            check(shops.tokensOwed(distant.owner()) == 1, "distant region did not refund its orphan");
                                            done.accept(null);
                                        }
                                        catch (Throwable error) { done.accept(error); }
                                    });
                                }
                                catch (Throwable error) { done.accept(error); }
                            });
                        }
                        catch (Throwable error) { done.accept(error); }
                    });
                }
                else done.accept(null);
            }
            catch (Throwable error) { done.accept(error); }
        };
        if (!FoliaSupport.PRESENT) Bukkit.getScheduler().runTask(plugin, checks);
        else
        {
            try
            {
                World world = Bukkit.getWorlds().getFirst();
                // Test setup may load its disposable chunk. Production reconciliation never requests one.
                var loadChunk = World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
                CompletableFuture<?> loaded = CompletableFuture.allOf(
                        (CompletableFuture<?>) loadChunk.invoke(world, 0, 0, true),
                        (CompletableFuture<?>) loadChunk.invoke(world, 512, 512, true));
                loaded.whenComplete((chunk, error) ->
                {
                    if (error != null) done.accept(error);
                    else SchedulerAdapter.region(plugin, world, 0, 0, checks);
                });
            }
            catch (Throwable error) { done.accept(error); }
        }
    }

    public static void run(Plugin plugin) throws Exception
    {
        check(FoliaSupport.PRESENT ? FoliaSupport.owns(Bukkit.getWorlds().getFirst(), 0, 0) : Bukkit.isPrimaryThread(),
                "checks must run on the owning server region");
        var field = StallPlugin.class.getDeclaredField("shops");
        field.setAccessible(true);
        PaperShops shops = (PaperShops) field.get(plugin);
        checkCodec(plugin);
        checkCommands(shops);
        if (!FoliaSupport.PRESENT) checkShop(shops);
        if (!FoliaSupport.PRESENT)
        {
            checkContainers(shops);
            checkUnloadedChest(shops);
        }
        checkLargeBarrel(shops, !FoliaSupport.PRESENT);
        checkLifecycle(shops);
        plugin.getLogger().info(FoliaSupport.PRESENT
                ? "Checked Folia region ownership, item data, commands, large barrel storage, explosions and reconciliation; player GUI/trade checks require real players."
                : "Checked native items, commands, menus, trades, chests, large barrels, stock protection, explosions and reconciliation.");
    }

    private static void checkLifecycle(PaperShops shops)
    {
        World world = Bukkit.getWorlds().getFirst();
        Block block = world.getBlockAt(0, 80, 0);
        block.setType(Material.BARREL);
        Shop<ItemStack> shop = new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "LifecycleOwner", PaperShops.locate(block), true);
        shops.registry().add(shop);
        shops.reconcile(shop);
        check(shops.registry().isCurrent(shop), "unmarked replacement barrel must pause, not refund");
        PaperMarkers.stamp(block, shop);
        ((Barrel) block.getState()).getInventory().addItem(new ItemStack(Material.DIAMOND, 7));
        Block neighbour = world.getBlockAt(1, 80, 0);
        neighbour.setType(Material.STONE);
        world.createExplosion(block.getLocation().add(0.5, 0.5, 0.5), 5.0F, false, true);
        check(block.getType() == Material.BARREL && count(((Barrel) block.getState()).getInventory(), Material.DIAMOND) == 7,
                "explosion destroyed a shop or its stock");
        check(neighbour.getType() != Material.STONE, "shop protection cancelled the entire explosion");
        block.setType(Material.AIR);
        shops.reconcile(shop);
        shops.reconcile(shop);
        check(!shops.registry().isCurrent(shop) && shops.tokensOwed(shop.owner()) == 1, "missing barrel was not refunded exactly once");

        block.setType(Material.BARREL);
        Shop<ItemStack> conflict = new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "ConflictOwner", PaperShops.locate(block), true);
        shops.registry().add(conflict);
        PaperMarkers.stamp(block, shop);
        shops.reconcile(conflict);
        check(!shops.registry().isCurrent(conflict) && shops.tokensOwed(conflict.owner()) == 1, "conflicting marker did not orphan the old record");
        check(PaperMarkers.carries(block, shop), "cleanup erased another shop's marker");
        block.setType(Material.AIR);
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
        Offer<ItemStack> offer = new Offer<>(UUID.randomUUID(), OfferSlots.costs(List.of(
                new OfferItem<>(new ItemStack(Material.EMERALD), 1),
                new OfferItem<>(new ItemStack(Material.GOLD_INGOT), 2))), OfferSlots.rewards(List.of(
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
            shops.remove(shop, null, null);
            block.setType(Material.AIR);
        }
    }

    /** A stand-in player who can buy: an inventory of 36 storage slots and nothing else. */
    private static Player buyer(UUID id, World world, Block near, Inventory storage, boolean permitted)
    {
        PlayerInventory inventory = proxy(PlayerInventory.class, (method, args) -> switch (method)
        {
            case "getStorageContents" -> storage.getContents();
            case "getMaxStackSize" -> 64;
            case "getItemInMainHand", "getItemInOffHand" -> new ItemStack(Material.AIR);
            case "setItem" -> { storage.setItem((int) args[0], (ItemStack) args[1]); yield null; }
            default -> throw new UnsupportedOperationException("inventory: " + method);
        });
        return proxy(Player.class, (method, args) -> switch (method)
        {
            case "getUniqueId" -> id;
            case "getName" -> "StallTestPlayer";
            case "isOnline" -> true;
            case "getWorld" -> world;
            case "getEyeLocation", "getLocation" -> near.getLocation().add(0.5, 0.5, 0.5);
            case "getInventory" -> inventory;
            case "hasPermission" -> permitted;
            case "sendMessage" -> null;
            default -> throw new UnsupportedOperationException("player: " + method);
        });
    }

    /** A chest stays protected even when the other half's chunk is unavailable. Nothing loads it. */
    private static void checkUnloadedChest(PaperShops shops)
    {
        World world = Bukkit.getWorlds().getFirst();
        world.getChunkAt(1000, 1000);
        world.unloadChunk(1001, 1000, false);
        check(!world.isChunkLoaded(1001, 1000), "test neighbour is still loaded");
        Block half = world.getBlockAt(16015, 80, 16000);
        chest(half, org.bukkit.block.data.type.Chest.Type.LEFT);
        Shop<ItemStack> shop = new Shop<>(UUID.randomUUID(), UUID.randomUUID(), "UnloadedOwner",
                new ShopLocation(world.getKey().toString(), 16016, 80, 16000), true);
        shop.setContainer("minecraft:chest");
        shops.registry().add(shop);
        try
        {
            check(shops.shopAt(half).filter(shop::equals).isPresent(), "unloaded anchor exposed the other half");
            check(PaperContainers.inventory(half).isEmpty(), "unloaded chest inventory was opened");
            check(!world.isChunkLoaded(1001, 1000), "chest lookup loaded its neighbour");
        }
        finally
        {
            shops.registry().remove(shop);
            half.setType(Material.AIR, false);
        }
    }

    private static void chest(Block block, org.bukkit.block.data.type.Chest.Type type)
    {
        var data = (org.bukkit.block.data.type.Chest) Material.CHEST.createBlockData();
        data.setFacing(org.bukkit.block.BlockFace.NORTH);
        data.setType(type);
        block.setBlockData(data, false);
    }

    /**
     * A double chest shop: either half finds the shop, its stock covers both halves, a third reward is
     * paid out, and both halves are protected from hoppers, breaking, explosions and joined chests.
     */
    private static void checkContainers(PaperShops shops)
    {
        World world = Bukkit.getWorlds().getFirst();
        // Facing north, a left half's other half is to the east.
        Block left = world.getBlockAt(10, 80, 0);
        Block right = world.getBlockAt(11, 80, 0);
        chest(left, org.bukkit.block.data.type.Chest.Type.LEFT);
        chest(right, org.bukkit.block.data.type.Chest.Type.RIGHT);
        check(PaperContainers.partner(left).filter(right::equals).isPresent()
                && PaperContainers.partner(right).filter(left::equals).isPresent(), "double chest halves did not find each other");
        UUID ownerId = UUID.randomUUID();
        Shop<ItemStack> shop = new Shop<>(UUID.randomUUID(), ownerId, "ChestOwner", PaperShops.locate(left), true);
        shop.setContainer(PaperContainers.id(left));
        Offer<ItemStack> offer = new Offer<>(UUID.randomUUID(),
                OfferSlots.costs(List.of(new OfferItem<>(new ItemStack(Material.EMERALD), 1))),
                OfferSlots.rewards(List.of(new OfferItem<>(new ItemStack(Material.BREAD), 16),
                        new OfferItem<>(new ItemStack(Material.COAL), 4), new OfferItem<>(new ItemStack(Material.APPLE), 2))));
        shop.addOffer(offer);
        shops.registry().add(shop);
        PaperMarkers.stamp(left, shop);
        Block single = world.getBlockAt(13, 80, 0);
        Block joining = world.getBlockAt(14, 80, 0);
        Shop<ItemStack> singleShop = null;
        try
        {
            check(shops.shopAt(right).filter(shop::equals).isPresent(), "the other half of a double chest did not find its shop");
            check(shops.stock(shop).map(stock -> stock.slots().size()).orElse(0) == 54, "a double chest shop's stock is not 54 slots");
            ((org.bukkit.block.Chest) left.getState()).getBlockInventory().addItem(new ItemStack(Material.BREAD, 32), new ItemStack(Material.COAL, 8));
            Inventory farHalf = ((org.bukkit.block.Chest) right.getState()).getBlockInventory();
            farHalf.addItem(new ItemStack(Material.APPLE, 4));

            Inventory storage = Bukkit.createInventory(null, 36);
            storage.addItem(new ItemStack(Material.EMERALD, 1));
            Player buyer = buyer(UUID.randomUUID(), world, left, storage, false);
            shops.buy(new BuyerMenu(shops, buyer, shop), offer.id());
            check(count(storage, Material.BREAD) == 16 && count(storage, Material.COAL) == 4
                    && count(storage, Material.APPLE) == 2, "the buyer did not receive all three rewards");
            check(count(farHalf, Material.APPLE) == 2, "the reward was not taken from the double chest's other half");

            ShopListener listener = new ShopListener(shops);
            Inventory hopper = Bukkit.createInventory(null, InventoryType.HOPPER);
            for (Inventory source : List.of(farHalf, shops.stock(shop).orElseThrow().parts().getFirst()))
            {
                var pull = new InventoryMoveItemEvent(source, new ItemStack(Material.APPLE), hopper, true);
                listener.onHopperPull(pull);
                check(pull.isCancelled(), "a hopper drained a double chest shop through " + source.getSize() + " slots");
            }
            var breaking = new BlockBreakEvent(right, buyer);
            listener.onBreak(breaking);
            check(breaking.isCancelled(), "another player could break a double chest shop's other half");
            world.createExplosion(right.getLocation().add(0.5, 0.5, 0.5), 4.0F, false, true);
            check(left.getType() == Material.CHEST && right.getType() == Material.CHEST, "an explosion destroyed half a chest shop");

            // Someone else's chest must not join a single chest shop; its owner's may.
            chest(single, org.bukkit.block.data.type.Chest.Type.SINGLE);
            singleShop = new Shop<>(UUID.randomUUID(), ownerId, "ChestOwner", PaperShops.locate(single), true);
            shops.registry().add(singleShop);
            PaperMarkers.stamp(single, singleShop);
            chest(joining, org.bukkit.block.data.type.Chest.Type.RIGHT);
            check(PaperContainers.partner(joining).filter(single::equals).isEmpty(), "a one-sided chest counted as joined");
            chest(single, org.bukkit.block.data.type.Chest.Type.LEFT);
            var stranger = new org.bukkit.event.block.BlockPlaceEvent(joining, joining.getState(), single, new ItemStack(Material.CHEST), buyer, true,
                    org.bukkit.inventory.EquipmentSlot.HAND);
            listener.onPlace(stranger);
            check(stranger.isCancelled(), "a stranger joined a chest onto someone's shop");
            var owner = new org.bukkit.event.block.BlockPlaceEvent(joining, joining.getState(), single, new ItemStack(Material.CHEST),
                    buyer(ownerId, world, single, Bukkit.createInventory(null, 36), false), true, org.bukkit.inventory.EquipmentSlot.HAND);
            listener.onPlace(owner);
            check(!owner.isCancelled(), "a shop's owner could not join a chest onto it");

            // The owner breaking the half without the marker removes the shop and clears the other half.
            Player ownerPlayer = buyer(ownerId, world, right, Bukkit.createInventory(null, 36), false);
            listener.onBroken(new BlockBreakEvent(right, ownerPlayer));
            check(!shops.registry().isCurrent(shop) && PaperMarkers.marker(left) == null,
                    "breaking a double chest shop's other half left the shop or its marker behind");
        }
        finally
        {
            shops.remove(shop, null, null);
            if (singleShop != null) shops.remove(singleShop, null, null);
            for (Block block : List.of(left, right, single, joining)) block.setType(Material.AIR);
        }
    }

    /**
     * A large barrel: one live 54-slot view over the barrel and its extra slots, trades that reach the extra
     * slots, extra slots that survive being read back from the barrel, and broken barrels that drop them.
     */
    private static void checkLargeBarrel(PaperShops shops, boolean trades)
    {
        check(CombinedInventories.available(), "combined inventories could not bind: " + CombinedInventories.failure());
        World world = Bukkit.getWorlds().getFirst();
        Block block = world.getBlockAt(20, 80, 0);
        block.setType(Material.BARREL);
        UUID ownerId = UUID.randomUUID();
        Shop<ItemStack> shop = new Shop<>(UUID.randomUUID(), ownerId, "LargeOwner", PaperShops.locate(block), true);
        shops.registry().add(shop);
        PaperMarkers.stamp(block, shop);
        try
        {
            if (trades)
            {
                check(!shops.grantLargeStock(buyer(ownerId, world, block, Bukkit.createInventory(null, 36), false), shop, block),
                        "large stock was granted without the permission");
                check(shops.grantLargeStock(buyer(ownerId, world, block, Bukkit.createInventory(null, 36), true), shop, block)
                        && shop.largeStock(), "large stock was not granted with the permission");
            }
            else
            {
                shop.enableLargeStock();
            }
            PaperStock stock = shops.stock(shop).orElseThrow();
            check(stock.large() && stock.slots().size() == 54, "a large barrel's stock is not 54 slots");
            Inventory own = stock.parts().get(0);
            Inventory extra = stock.parts().get(1);

            Inventory combined = CombinedInventories.of(own, extra);
            check(combined.getSize() == 54, "the combined view is not 54 slots");
            combined.setItem(30, new ItemStack(Material.DIAMOND, 3));
            check(extra.getItem(3) != null && extra.getItem(3).getAmount() == 3, "the view's lower half is not the extra slots");
            own.setItem(0, new ItemStack(Material.IRON_INGOT, 5));
            check(combined.getItem(0) != null && combined.getItem(0).getType() == Material.IRON_INGOT,
                    "the view's upper half is not the barrel");
            extra.clear();
            own.clear();

            if (trades)
            {
                Offer<ItemStack> offer = new Offer<>(UUID.randomUUID(),
                        OfferSlots.costs(List.of(new OfferItem<>(new ItemStack(Material.EMERALD), 1))),
                        OfferSlots.rewards(List.of(new OfferItem<>(new ItemStack(Material.GOLD_INGOT), 10))));
                shop.addOffer(offer);
                extra.addItem(new ItemStack(Material.GOLD_INGOT, 64));
                Inventory storage = Bukkit.createInventory(null, 36);
                storage.addItem(new ItemStack(Material.EMERALD, 1));
                shops.buy(new BuyerMenu(shops, buyer(UUID.randomUUID(), world, block, storage, false), shop), offer.id());
                check(count(storage, Material.GOLD_INGOT) == 10 && count(extra, Material.GOLD_INGOT) == 54,
                        "a trade did not take its reward from the extra slots");
                check(count(own, Material.EMERALD) == 1, "the payment did not land in the barrel");
            }
            else
            {
                extra.addItem(new ItemStack(Material.GOLD_INGOT, 54));
            }

            // Written to the barrel and read back, the extra slots are unchanged.
            shops.extraStock().markDirty(shop);
            shops.extraStock().flush(shop, block);
            shops.extraStock().forget(shop.id());
            Inventory reread = shops.stock(shop).orElseThrow().parts().get(1);
            check(reread != extra && count(reread, Material.GOLD_INGOT) == 54, "the extra slots did not survive a reread");
            check(!shops.extraStockEmpty(shop), "a large barrel holding extra stock counted as empty");

            // Breaking the barrel drops the extra slots with it.
            shops.dropExtraStock(shop, block);
            long dropped = world.getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 2, 2, 2).stream()
                    .filter(entity -> entity instanceof org.bukkit.entity.Item item && item.getItemStack().getType() == Material.GOLD_INGOT)
                    .mapToLong(entity -> ((org.bukkit.entity.Item) entity).getItemStack().getAmount()).sum();
            check(dropped == 54, "breaking a large barrel dropped " + dropped + " of its 54 extra items");
            world.getNearbyEntities(block.getLocation(), 3, 3, 3).stream()
                    .filter(entity -> entity instanceof org.bukkit.entity.Item).forEach(org.bukkit.entity.Entity::remove);
        }
        finally
        {
            shops.remove(shop, null, null);
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
