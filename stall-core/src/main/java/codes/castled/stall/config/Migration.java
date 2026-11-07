package codes.castled.stall.config;

import java.util.Map;
import java.util.function.Consumer;

/**
 * One step in a config file's history, applied when a file older than {@code version} is loaded, before
 * new keys are filled in. Use it when a key is renamed, moved or changes meaning, so a server's own
 * setting carries over instead of being replaced by the new default.
 *
 * <p>Migrations work on the file's values flattened to dotted paths, such as {@code tokens.item}.
 *
 * @param version the {@code config-version} this step brings a file up to
 */
public record Migration(int version, Consumer<Map<String, Object>> change)
{
    /** Moves a value to a new path, if the file has one at the old path and none at the new. */
    public static Migration rename(int version, String from, String to)
    {
        return new Migration(version, values ->
        {
            if (values.containsKey(from) && !values.containsKey(to))
            {
                values.put(to, values.remove(from));
            }
        });
    }

    /** Drops a value that no longer means anything. */
    public static Migration remove(int version, String path)
    {
        return new Migration(version, values -> values.remove(path));
    }
}
