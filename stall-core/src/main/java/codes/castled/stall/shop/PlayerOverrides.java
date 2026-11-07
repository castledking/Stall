package codes.castled.stall.shop;

import codes.castled.stall.store.StoreLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persisted player overrides for shop limits and max offers, matching GPExpansion's override model.
 */
public final class PlayerOverrides
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Entry
    {
        private Integer limit;
        private Integer offers;
        private String name;

        public Entry(Integer limit, Integer offers, String name)
        {
            this.limit = limit;
            this.offers = offers;
            this.name = name;
        }

        public Integer limit()
        {
            return limit;
        }

        public Integer offers()
        {
            return offers;
        }

        public String name()
        {
            return name;
        }

        public boolean isEmpty()
        {
            return limit == null && offers == null;
        }
    }

    private final Path file;
    private final StoreLog log;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public PlayerOverrides(Path file, StoreLog log)
    {
        this.file = file;
        this.log = log;
        load();
    }

    public static PlayerOverrides inMemory()
    {
        return new PlayerOverrides(null, null);
    }

    public OptionalInt limitOverride(UUID playerId)
    {
        if (playerId == null)
        {
            return OptionalInt.empty();
        }
        Entry entry = entries.get(playerId);
        return (entry != null && entry.limit != null) ? OptionalInt.of(entry.limit) : OptionalInt.empty();
    }

    public OptionalInt offersOverride(UUID playerId)
    {
        if (playerId == null)
        {
            return OptionalInt.empty();
        }
        Entry entry = entries.get(playerId);
        return (entry != null && entry.offers != null) ? OptionalInt.of(entry.offers) : OptionalInt.empty();
    }

    public void setLimit(UUID playerId, String name, int limit)
    {
        entries.compute(playerId, (id, existing) ->
        {
            String playerName = name != null && !name.isBlank() ? name : (existing != null ? existing.name : null);
            Integer offers = existing != null ? existing.offers : null;
            return new Entry(limit, offers, playerName);
        });
        save();
    }

    public void setOffers(UUID playerId, String name, int offers)
    {
        entries.compute(playerId, (id, existing) ->
        {
            String playerName = name != null && !name.isBlank() ? name : (existing != null ? existing.name : null);
            Integer limit = existing != null ? existing.limit : null;
            return new Entry(limit, offers, playerName);
        });
        save();
    }

    public boolean clearLimit(UUID playerId)
    {
        boolean[] changed = new boolean[]{false};
        entries.computeIfPresent(playerId, (id, existing) ->
        {
            if (existing.limit != null)
            {
                changed[0] = true;
                existing.limit = null;
            }
            return existing.isEmpty() ? null : existing;
        });
        if (changed[0])
        {
            save();
        }
        return changed[0];
    }

    public boolean clearOffers(UUID playerId)
    {
        boolean[] changed = new boolean[]{false};
        entries.computeIfPresent(playerId, (id, existing) ->
        {
            if (existing.offers != null)
            {
                changed[0] = true;
                existing.offers = null;
            }
            return existing.isEmpty() ? null : existing;
        });
        if (changed[0])
        {
            save();
        }
        return changed[0];
    }

    public boolean reset(UUID playerId)
    {
        Entry removed = entries.remove(playerId);
        if (removed != null)
        {
            save();
            return true;
        }
        return false;
    }

    public boolean hasOverride(UUID playerId)
    {
        Entry entry = entries.get(playerId);
        return entry != null && !entry.isEmpty();
    }

    private void load()
    {
        if (file == null || !Files.exists(file))
        {
            return;
        }
        try
        {
            String content = Files.readString(file);
            JsonObject json = GSON.fromJson(content, JsonObject.class);
            if (json == null)
            {
                return;
            }
            for (Map.Entry<String, JsonElement> member : json.entrySet())
            {
                try
                {
                    UUID id = UUID.fromString(member.getKey());
                    if (member.getValue().isJsonObject())
                    {
                        JsonObject obj = member.getValue().getAsJsonObject();
                        Integer limit = obj.has("limit") && !obj.get("limit").isJsonNull() ? obj.get("limit").getAsInt() : null;
                        Integer offers = obj.has("offers") && !obj.get("offers").isJsonNull() ? obj.get("offers").getAsInt() : null;
                        String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : null;
                        if (limit != null || offers != null)
                        {
                            entries.put(id, new Entry(limit, offers, name));
                        }
                    }
                }
                catch (IllegalArgumentException ignored)
                {
                }
            }
        }
        catch (Exception ex)
        {
            if (log != null)
            {
                log.warn("Could not read overrides file " + file + ": " + ex.getMessage(), ex);
            }
        }
    }

    private synchronized void save()
    {
        if (file == null)
        {
            return;
        }
        try
        {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, Entry> entry : entries.entrySet())
            {
                if (entry.getValue().isEmpty())
                {
                    continue;
                }
                JsonObject obj = new JsonObject();
                if (entry.getValue().limit() != null)
                {
                    obj.addProperty("limit", entry.getValue().limit());
                }
                if (entry.getValue().offers() != null)
                {
                    obj.addProperty("offers", entry.getValue().offers());
                }
                if (entry.getValue().name() != null)
                {
                    obj.addProperty("name", entry.getValue().name());
                }
                root.add(entry.getKey().toString(), obj);
            }
            Path temp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(temp, GSON.toJson(root));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException ex)
        {
            if (log != null)
            {
                log.warn("Could not save overrides file " + file + ": " + ex.getMessage(), ex);
            }
        }
    }
}
