package codes.castled.stall.store;

import codes.castled.stall.shop.Shop;
import codes.castled.stall.shop.ShopRegistry;
import codes.castled.stall.shop.TokenReturns;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Saves shops and owed tokens under one data folder, the same layout on Paper and Fabric:
 *
 * <pre>
 * stall/
 *   shops/&lt;shop id&gt;.json   one file per shop, see {@link ShopCodec}
 *   shops/_broken/         files that could not be loaded, moved aside rather than deleted
 *   returns.json           tokens owed to players, for /stall reclaim
 * </pre>
 *
 * <p>The server's shops live in memory; this keeps the files in step. Every change is turned into JSON on
 * the server thread, where reading items is safe, and written by {@link FileStore} in the background.
 */
public final class ShopPersistence<I> implements AutoCloseable
{
    private static final String SHOPS = "shops";
    private static final String BROKEN = "_broken";
    private static final String RETURNS = "returns.json";
    private static final int RETURNS_SCHEMA = 1;

    private final Path directory;
    private final ShopCodec<I> codec;
    private final FileStore files;
    private final StoreLog log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public ShopPersistence(Path directory, ItemCodec<I> items, StoreLog log)
    {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.codec = new ShopCodec<>(items);
        this.log = Objects.requireNonNull(log, "log");
        this.files = new FileStore(log);
    }

    public Path directory()
    {
        return directory;
    }

    /**
     * What loading found.
     *
     * @param quarantined files that weren't valid shops and were moved to {@code shops/_broken}
     * @param fromNewerVersion files a newer version of Stall wrote, left untouched
     * @param unreadableOffers offers kept unread because this server can't read their items
     */
    public record LoadReport(int shops, int quarantined, int fromNewerVersion, int unreadableOffers)
    {
    }

    /** Loads every saved shop into {@code registry} and every owed token into {@code returns}. */
    public LoadReport load(ShopRegistry<I> registry, TokenReturns returns)
    {
        int loaded = 0;
        int quarantined = 0;
        int newer = 0;
        int unreadable = 0;
        for (Path file : shopFiles())
        {
            try
            {
                ShopCodec.Decoded<I> decoded = codec.decode(readJson(file));
                Shop<I> shop = decoded.shop();
                if (!file.getFileName().toString().equals(shop.id() + ".json"))
                {
                    throw new ShopDataException("its file name doesn't match its shop id " + shop.id(), null);
                }
                if (registry.at(shop.location()).isPresent())
                {
                    throw new ShopDataException("another shop is already at " + shop.location(), null);
                }
                registry.add(shop);
                loaded++;
                unreadable += decoded.unreadableOffers();
                if (decoded.unreadableOffers() > 0)
                {
                    log.warn("Shop " + shop.id() + " at " + shop.location() + " has " + decoded.unreadableOffers()
                            + " offer(s) with items this server can't read. They are kept, but hidden.", null);
                }
            }
            catch (ShopDataException invalid)
            {
                if (invalid.isFromNewerVersion())
                {
                    newer++;
                    log.warn("Left " + file + " alone: " + invalid.getMessage() + ".", null);
                }
                else
                {
                    quarantine(file, invalid);
                    quarantined++;
                }
            }
            catch (IOException | RuntimeException unreadableFile)
            {
                quarantine(file, unreadableFile);
                quarantined++;
            }
        }
        loadReturns(returns);
        return new LoadReport(loaded, quarantined, newer, unreadable);
    }

    public void saveShop(Shop<I> shop)
    {
        files.write(shopFile(shop.id()), gson.toJson(codec.encode(shop)));
    }

    public void deleteShop(Shop<I> shop)
    {
        files.delete(shopFile(shop.id()));
    }

    public void saveReturns(TokenReturns returns)
    {
        JsonObject owed = new JsonObject();
        returns.snapshot().forEach((player, count) -> owed.addProperty(player.toString(), count));
        JsonObject json = new JsonObject();
        json.addProperty("schema", RETURNS_SCHEMA);
        json.add("owed", owed);
        files.write(directory.resolve(RETURNS), gson.toJson(json));
    }

    /** Blocks until every change so far is on disk. */
    public void flush()
    {
        files.flush();
    }

    /** Writes every pending change and stops the storage thread. */
    @Override
    public void close()
    {
        files.close();
    }

    private Path shopFile(UUID id)
    {
        return directory.resolve(SHOPS).resolve(id + ".json");
    }

    private List<Path> shopFiles()
    {
        Path shops = directory.resolve(SHOPS);
        if (!Files.isDirectory(shops))
        {
            return List.of();
        }
        // Left-over ".json.tmp" files are writes a crash interrupted; the real file is still intact.
        try (Stream<Path> listing = Files.list(shops))
        {
            return listing.filter(file -> file.getFileName().toString().endsWith(".json") && Files.isRegularFile(file))
                    .sorted()
                    .toList();
        }
        catch (IOException failure)
        {
            log.warn("Could not list " + shops + "; no shops were loaded.", failure);
            return List.of();
        }
    }

    private void loadReturns(TokenReturns returns)
    {
        Path file = directory.resolve(RETURNS);
        if (!Files.isRegularFile(file))
        {
            return;
        }
        try
        {
            JsonObject owed = readJson(file).getAsJsonObject("owed");
            Map<UUID, Integer> saved = new HashMap<>();
            owed.entrySet().forEach(entry -> saved.put(UUID.fromString(entry.getKey()), entry.getValue().getAsInt()));
            returns.restore(saved);
        }
        catch (IOException | RuntimeException failure)
        {
            quarantine(file, failure);
        }
    }

    private static JsonObject readJson(Path file) throws IOException
    {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    /** Moves a file that couldn't be loaded into {@code shops/_broken}, so it is kept but never loaded again. */
    private void quarantine(Path file, Exception reason)
    {
        Path broken = directory.resolve(SHOPS).resolve(BROKEN);
        try
        {
            Files.createDirectories(broken);
            Path target = broken.resolve(file.getFileName());
            if (Files.exists(target))
            {
                target = broken.resolve(file.getFileName() + "." + System.currentTimeMillis());
            }
            Files.move(file, target);
            log.warn("Could not load " + file + " (" + describe(reason) + "). Moved it to " + target + ".", null);
        }
        catch (IOException moveFailure)
        {
            log.warn("Could not load " + file + " (" + describe(reason) + "), and could not move it aside.", moveFailure);
        }
    }

    /** The first line of what went wrong; a bad file is expected now and then and needs no stack trace. */
    private static String describe(Exception reason)
    {
        String message = reason.getMessage();
        return message == null ? reason.getClass().getSimpleName() : message.lines().findFirst().orElse(message);
    }
}
