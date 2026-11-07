package codes.castled.stall.config;

import codes.castled.stall.store.StoreLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads a YAML config file against the defaults bundled with Stall, and keeps the file up to date as Stall
 * changes: the version manager.
 *
 * <p>Every bundled file has a {@value #VERSION_KEY}. When a server's file is older than the bundled one,
 * or is missing keys, it is rewritten from the bundled file, comments and all, with every value the server
 * had set carried over. Steps in {@link Migration}s run first, so renamed keys keep their values. The
 * previous file is kept beside it as {@code <name>.bak}. A file that is complete and current is never
 * touched, so a server's own comments and layout stay as they are.
 *
 * <p>A file that can't be read is left alone for its owner to fix, and the defaults are used meanwhile. A
 * file from a newer Stall is read as far as it goes and never rewritten.
 */
public final class ConfigFile
{
    public static final String VERSION_KEY = "config-version";

    private ConfigFile()
    {
    }

    /**
     * Loads {@code file}, creating or updating it as needed.
     *
     * @param template the bundled default file's text
     * @param migrations steps for files older than the template, in any order
     * @return every value, flattened to dotted paths; every path in the template is present
     */
    public static Map<String, Object> load(Path file, String template, List<Migration> migrations, StoreLog log)
    {
        Map<String, Object> defaults = defaults(template);
        String name = file.getFileName().toString();
        if (!Files.exists(file))
        {
            try
            {
                write(file, template);
                log.info("Created " + name + ".");
            }
            catch (IOException failure)
            {
                log.warn("Could not create " + file + "; using the default settings.", failure);
            }
            return defaults;
        }

        Map<String, Object> user;
        String rawText;
        try
        {
            rawText = Files.readString(file, StandardCharsets.UTF_8);
            user = flatten(Yaml.parse(rawText));
        }
        catch (YamlException invalid)
        {
            log.warn(name + " could not be read (" + invalid.getMessage() + "). Using the default settings until "
                    + "it is fixed.", null);
            return defaults;
        }
        catch (IOException failure)
        {
            log.warn("Could not read " + file + "; using the default settings.", failure);
            return defaults;
        }

        int latest = version(defaults);
        int current = user.containsKey(VERSION_KEY) ? version(user) : 0;
        if (current > latest)
        {
            log.warn(name + " was written by a newer version of Stall (config-version " + current + ", this one "
                    + "knows " + latest + "). Settings this version knows are used; the file is left alone.", null);
            return merge(name, defaults, user, log);
        }

        List<Migration> steps = new ArrayList<>(migrations);
        steps.sort((a, b) -> Integer.compare(a.version(), b.version()));
        for (Migration step : steps)
        {
            if (step.version() > current && step.version() <= latest)
            {
                step.change().accept(user);
            }
        }

        Map<String, Object> values = merge(name, defaults, user, log);
        List<String> missing = new ArrayList<>();
        for (String path : defaults.keySet())
        {
            if (!user.containsKey(path) && !path.equals(VERSION_KEY))
            {
                missing.add(path);
            }
        }
        List<String> unknown = new ArrayList<>();
        for (String path : user.keySet())
        {
            if (!defaults.containsKey(path))
            {
                unknown.add(path);
            }
        }
        boolean schemaChanged = current != latest || !missing.isEmpty();
        try
        {
            String updated = render(template, defaults, values);
            if (!schemaChanged && updated.equals(rawText.replace("\r", "")))
            {
                return values;
            }
            if (schemaChanged)
            {
                Path backup = file.resolveSibling(name + ".bak");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                write(file, updated);
                StringBuilder report = new StringBuilder("Updated ").append(name);
                if (current != latest)
                {
                    report.append(" from config-version ").append(current).append(" to ").append(latest);
                }
                report.append(". Your settings were kept");
                if (!missing.isEmpty())
                {
                    report.append("; new: ").append(String.join(", ", missing));
                }
                if (!unknown.isEmpty())
                {
                    report.append("; no longer used: ").append(String.join(", ", unknown));
                }
                report.append(". The previous file is ").append(backup.getFileName()).append('.');
                log.info(report.toString());
            }
            else
            {
                write(file, updated);
            }
        }
        catch (IOException | YamlException failure)
        {
            log.warn("Could not update " + file + ". Its settings are still used, with defaults for anything new.",
                    failure);
        }
        return values;
    }

    /** The bundled file's values. It ships with Stall, so failing to read it is a bug. */
    public static Map<String, Object> defaults(String template)
    {
        try
        {
            return flatten(Yaml.parse(template));
        }
        catch (YamlException invalid)
        {
            throw new IllegalStateException("Stall's bundled config is not valid: " + invalid.getMessage(), invalid);
        }
    }

    private static int version(Map<String, Object> values)
    {
        return values.get(VERSION_KEY) instanceof Number number ? number.intValue() : 0;
    }

    /**
     * The defaults with every usable value from the server's file laid over them. A value of the wrong
     * kind, such as text where a number belongs, is reported and the default used.
     */
    static Map<String, Object> merge(String name, Map<String, Object> defaults, Map<String, Object> user, StoreLog log)
    {
        Map<String, Object> values = new LinkedHashMap<>(defaults);
        for (Map.Entry<String, Object> entry : defaults.entrySet())
        {
            String path = entry.getKey();
            if (!user.containsKey(path) || path.equals(VERSION_KEY))
            {
                continue;
            }
            Object fallback = entry.getValue();
            Object value = coerce(fallback, user.get(path));
            if (value == null)
            {
                log.warn(name + ": " + path + " should be " + kind(fallback) + "; using the default, "
                        + Yaml.format(fallback) + ".", null);
                continue;
            }
            values.put(path, value);
        }
        return values;
    }

    /** {@code value} made to fit where {@code fallback} goes, or null if it can't. */
    private static Object coerce(Object fallback, Object value)
    {
        if (fallback instanceof Boolean)
        {
            return value instanceof Boolean ? value : null;
        }
        if (fallback instanceof Number)
        {
            return value instanceof Number ? value : null;
        }
        // Text and lists of lines can stand in for each other: any message may be one line or several.
        if (fallback instanceof String || fallback instanceof List<?> || fallback == null)
        {
            if (value == null)
            {
                return fallback instanceof List<?> ? List.of() : "";
            }
            if (value instanceof List<?> list)
            {
                List<String> lines = new ArrayList<>(list.size());
                for (Object item : list)
                {
                    lines.add(item == null ? "" : item.toString());
                }
                return Collections.unmodifiableList(lines);
            }
            return value instanceof Map<?, ?> ? null : value.toString();
        }
        return null;
    }

    private static String kind(Object fallback)
    {
        if (fallback instanceof Boolean)
        {
            return "true or false";
        }
        return fallback instanceof Number ? "a number" : "text";
    }

    /** Nested maps as one map of dotted paths to values. Lists and scalars are values. */
    static Map<String, Object> flatten(Map<String, Object> nested)
    {
        Map<String, Object> flat = new LinkedHashMap<>();
        flatten("", nested, flat);
        return flat;
    }

    private static void flatten(String prefix, Map<?, ?> nested, Map<String, Object> flat)
    {
        for (Map.Entry<?, ?> entry : nested.entrySet())
        {
            String path = prefix + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> child && !child.isEmpty())
            {
                flatten(path + ".", child, flat);
            }
            else
            {
                flat.put(path, entry.getValue());
            }
        }
    }

    /**
     * The template with its values replaced by {@code values}. Lines whose value is unchanged are copied
     * exactly, so the bundled formatting and comments survive; changed values are written out fresh.
     */
    static String render(String template, Map<String, Object> defaults, Map<String, Object> values) throws YamlException
    {
        String[] raw = template.replace("\r", "").split("\n", -1);
        List<String> out = new ArrayList<>(raw.length);
        Deque<Parent> parents = new ArrayDeque<>();
        int i = 0;
        while (i < raw.length)
        {
            String line = raw[i];
            int indent = indent(line);
            String content = line.substring(indent).stripTrailing();
            if (content.isEmpty() || content.startsWith("#"))
            {
                out.add(line);
                i++;
                continue;
            }
            while (!parents.isEmpty() && parents.peek().indent() >= indent)
            {
                parents.pop();
            }
            Yaml.Entry entry = Yaml.entry(content, i + 1);
            if (entry == null)
            {
                out.add(line);
                i++;
                continue;
            }
            String path = path(parents, entry.key());
            int end = i + 1;
            if (Yaml.isBlank(entry.rest()))
            {
                int next = nextContent(raw, i + 1);
                if (next >= 0)
                {
                    int nextIndent = indent(raw[next]);
                    String nextContent = raw[next].substring(nextIndent).stripTrailing();
                    if (Yaml.isListItem(nextContent) && nextIndent >= indent)
                    {
                        // A block list: its items belong to this key's value.
                        for (int item = next; item >= 0 && indent(raw[item]) == nextIndent
                                && Yaml.isListItem(raw[item].substring(nextIndent).stripTrailing());
                                item = nextContent(raw, item + 1))
                        {
                            end = item + 1;
                        }
                    }
                    else if (nextIndent > indent)
                    {
                        parents.push(new Parent(indent, entry.key()));
                        out.add(line);
                        i++;
                        continue;
                    }
                }
            }
            Object value = values.get(path);
            if (!values.containsKey(path) || Objects.equals(value, defaults.get(path)))
            {
                for (int copy = i; copy < end; copy++)
                {
                    out.add(raw[copy]);
                }
            }
            else
            {
                String key = " ".repeat(indent) + (content.startsWith("\"") || content.startsWith("'")
                        ? Yaml.quote(entry.key()) : entry.key()) + ":";
                if (value instanceof List<?> list && !list.isEmpty())
                {
                    out.add(key);
                    for (Object item : list)
                    {
                        out.add(" ".repeat(indent + 2) + "- " + Yaml.format(item));
                    }
                }
                else
                {
                    out.add(key + " " + Yaml.format(value));
                }
            }
            i = end;
        }
        return String.join("\n", out);
    }

    private record Parent(int indent, String key)
    {
    }

    private static String path(Deque<Parent> parents, String key)
    {
        StringBuilder path = new StringBuilder();
        for (var iterator = parents.descendingIterator(); iterator.hasNext(); )
        {
            path.append(iterator.next().key()).append('.');
        }
        return path.append(key).toString();
    }

    private static int indent(String line)
    {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ')
        {
            indent++;
        }
        return indent;
    }

    /** The next line at or after {@code from} that isn't blank or a comment, or -1. */
    private static int nextContent(String[] raw, int from)
    {
        for (int i = from; i < raw.length; i++)
        {
            String content = raw[i].strip();
            if (!content.isEmpty() && !content.startsWith("#"))
            {
                return i;
            }
        }
        return -1;
    }

    /** Writes through a temporary file, so a crash never leaves half a config behind. */
    private static void write(Path file, String text) throws IOException
    {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        try
        {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException unsupported)
        {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
