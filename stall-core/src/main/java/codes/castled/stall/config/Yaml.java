package codes.castled.stall.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The part of YAML Stall's config files use, read and written without a library: neither Minecraft nor
 * Fabric ships a YAML parser, and the core has to work on both platforms.
 *
 * <p>Supported: nested keys by indentation, comments, plain, {@code 'single'} and {@code "double"} quoted
 * scalars, booleans, numbers, {@code null}, block lists of scalars ({@code - item}) and flow lists of
 * scalars ({@code [a, b]}), and the empty map {@code {}}. Anchors, tags, multi-document files, block
 * scalars ({@code |}, {@code >}) and nested collections inside lists are not; a file using them is
 * reported with the line it went wrong on rather than read wrongly.
 */
public final class Yaml
{
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+\\.\\d*|\\.\\d+|\\d+)([eE][-+]?\\d+)?");

    private Yaml()
    {
    }

    /** One meaningful line: its number, indentation and text without the indentation. */
    record Line(int number, int indent, String content)
    {
    }

    /** A {@code key: rest} line split at its colon. {@code rest} still holds any trailing comment. */
    record Entry(String key, String rest)
    {
    }

    /** Reads a YAML document whose top level is a map. Maps keep their keys in file order. */
    public static Map<String, Object> parse(String text) throws YamlException
    {
        List<Line> lines = lines(text);
        if (lines.isEmpty())
        {
            return new LinkedHashMap<>();
        }
        Parser parser = new Parser(lines);
        Line first = lines.getFirst();
        if (isListItem(first.content()))
        {
            throw new YamlException(first.number(), "the file must be made of keys, not a list");
        }
        Map<String, Object> map = parser.map(first.indent());
        if (parser.position < lines.size())
        {
            Line stray = lines.get(parser.position);
            throw new YamlException(stray.number(), "this line is indented less than the first key");
        }
        return map;
    }

    /** The lines that carry content: blank lines and whole-line comments are dropped. */
    static List<Line> lines(String text) throws YamlException
    {
        List<Line> lines = new ArrayList<>();
        String[] raw = text.split("\n", -1);
        for (int i = 0; i < raw.length; i++)
        {
            String line = raw[i];
            if (line.endsWith("\r"))
            {
                line = line.substring(0, line.length() - 1);
            }
            if (i == 0 && line.startsWith("\uFEFF"))
            {
                line = line.substring(1);
            }
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ')
            {
                indent++;
            }
            String content = line.substring(indent).stripTrailing();
            if (content.isEmpty() || content.startsWith("#"))
            {
                continue;
            }
            if (content.charAt(0) == '\t')
            {
                throw new YamlException(i + 1, "indent with spaces, not tabs");
            }
            if (content.equals("---") || content.equals("..."))
            {
                continue;
            }
            lines.add(new Line(i + 1, indent, content));
        }
        return lines;
    }

    static boolean isListItem(String content)
    {
        return content.equals("-") || content.startsWith("- ");
    }

    /** Splits {@code key: value}; null if the line isn't a key. */
    static Entry entry(String content, int number) throws YamlException
    {
        if (content.startsWith("\"") || content.startsWith("'"))
        {
            Cursor cursor = new Cursor(content, number);
            String key = cursor.quoted();
            String after = content.substring(cursor.index);
            if (!after.startsWith(":") || (after.length() > 1 && after.charAt(1) != ' '))
            {
                return null;
            }
            return new Entry(key, after.substring(1).trim());
        }
        for (int i = 0; i < content.length(); i++)
        {
            char c = content.charAt(i);
            if (c == '#' && i > 0 && content.charAt(i - 1) == ' ')
            {
                return null;
            }
            if (c == ':' && (i + 1 == content.length() || content.charAt(i + 1) == ' '))
            {
                String key = content.substring(0, i).trim();
                return key.isEmpty() ? null : new Entry(key, content.substring(i + 1).trim());
            }
        }
        return null;
    }

    /** Whether a value part is empty, or only a comment, meaning the value is a nested block or null. */
    static boolean isBlank(String rest)
    {
        return rest.isEmpty() || rest.startsWith("#");
    }

    /** Reads an inline value: a quoted or plain scalar, a flow list, or {@code {}}. */
    static Object scalar(String rest, int number) throws YamlException
    {
        Cursor cursor = new Cursor(rest, number);
        Object value;
        char first = rest.charAt(0);
        if (first == '"' || first == '\'')
        {
            value = cursor.quoted();
        }
        else if (first == '[')
        {
            value = cursor.flowList();
        }
        else if (first == '{')
        {
            cursor.index++;
            cursor.skipSpaces();
            if (!cursor.at('}'))
            {
                throw new YamlException(number, "inline maps aren't supported; put each key on its own line");
            }
            cursor.index++;
            value = new LinkedHashMap<String, Object>();
        }
        else if (first == '|' || first == '>')
        {
            throw new YamlException(number, "block text (| or >) isn't supported; use a quoted string, or a list "
                    + "of lines");
        }
        else
        {
            return plain(cutComment(rest));
        }
        cursor.expectEnd();
        return value;
    }

    /** A plain scalar's text up to any comment. */
    private static String cutComment(String text)
    {
        int comment = text.indexOf(" #");
        return (comment < 0 ? text : text.substring(0, comment)).trim();
    }

    /** What an unquoted scalar means: a boolean, null, a number, or else the text itself. */
    static Object plain(String text)
    {
        String lower = text.toLowerCase(Locale.ROOT);
        switch (lower)
        {
            case "true":
                return true;
            case "false":
                return false;
            case "null":
            case "~":
                return null;
            default:
                break;
        }
        if (INTEGER.matcher(text).matches())
        {
            try
            {
                long number = Long.parseLong(text.startsWith("+") ? text.substring(1) : text);
                return number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE ? (Object) (int) number : (Object) number;
            }
            catch (NumberFormatException tooLarge)
            {
                return text;
            }
        }
        if (DECIMAL.matcher(text).matches())
        {
            return Double.parseDouble(text);
        }
        return text;
    }

    // --- Writing -------------------------------------------------------------------------------------

    /** A value as it is written after {@code key: }. Strings are always double-quoted. */
    public static String format(Object value)
    {
        if (value == null)
        {
            return "\"\"";
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long)
        {
            return value.toString();
        }
        if (value instanceof Number number)
        {
            double decimal = number.doubleValue();
            return decimal == Math.rint(decimal) && Math.abs(decimal) < 1e15
                    ? String.valueOf((long) decimal) + ".0" : String.valueOf(decimal);
        }
        if (value instanceof List<?> list)
        {
            StringBuilder flow = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++)
            {
                flow.append(i == 0 ? "" : ", ").append(format(list.get(i)));
            }
            return flow.append(']').toString();
        }
        return quote(value.toString());
    }

    /** A double-quoted YAML string. */
    public static String quote(String text)
    {
        StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            switch (c)
            {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\t' -> quoted.append("\\t");
                case '\r' -> quoted.append("\\r");
                default ->
                {
                    if (c < 0x20)
                    {
                        quoted.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    // --- Reading blocks ------------------------------------------------------------------------------

    private static final class Parser
    {
        private final List<Line> lines;
        private int position;

        Parser(List<Line> lines)
        {
            this.lines = lines;
        }

        Map<String, Object> map(int indent) throws YamlException
        {
            Map<String, Object> map = new LinkedHashMap<>();
            while (position < lines.size())
            {
                Line line = lines.get(position);
                if (line.indent() < indent)
                {
                    break;
                }
                if (line.indent() > indent)
                {
                    throw new YamlException(line.number(), "unexpected indentation");
                }
                if (isListItem(line.content()))
                {
                    throw new YamlException(line.number(), "a list item here needs a key above it");
                }
                Entry entry = entry(line.content(), line.number());
                if (entry == null)
                {
                    throw new YamlException(line.number(), "expected \"key: value\"");
                }
                if (map.containsKey(entry.key()))
                {
                    throw new YamlException(line.number(), "\"" + entry.key() + "\" is set twice");
                }
                position++;
                map.put(entry.key(), value(entry.rest(), indent, line.number()));
            }
            return map;
        }

        private Object value(String rest, int indent, int number) throws YamlException
        {
            if (!isBlank(rest))
            {
                return scalar(rest, number);
            }
            if (position >= lines.size())
            {
                return null;
            }
            Line next = lines.get(position);
            if (next.indent() > indent)
            {
                return isListItem(next.content()) ? list(next.indent()) : map(next.indent());
            }
            // YAML also allows a list at the same indentation as its key.
            if (next.indent() == indent && isListItem(next.content()))
            {
                return list(indent);
            }
            return null;
        }

        private List<Object> list(int indent) throws YamlException
        {
            List<Object> list = new ArrayList<>();
            while (position < lines.size())
            {
                Line line = lines.get(position);
                if (line.indent() != indent || !isListItem(line.content()))
                {
                    break;
                }
                position++;
                String item = line.content().substring(1).trim();
                if (position < lines.size() && lines.get(position).indent() > indent)
                {
                    throw new YamlException(lines.get(position).number(), "lists can only hold plain values, not "
                            + "nested lists or keys");
                }
                list.add(isBlank(item) ? null : scalar(item, line.number()));
            }
            if (position < lines.size() && lines.get(position).indent() > indent)
            {
                throw new YamlException(lines.get(position).number(), "unexpected indentation");
            }
            return list;
        }
    }

    /** Reads quoted strings and flow lists out of one line. */
    private static final class Cursor
    {
        private final String text;
        private final int number;
        private int index;

        Cursor(String text, int number)
        {
            this.text = text;
            this.number = number;
        }

        boolean at(char c)
        {
            return index < text.length() && text.charAt(index) == c;
        }

        void skipSpaces()
        {
            while (index < text.length() && text.charAt(index) == ' ')
            {
                index++;
            }
        }

        /** After a value, only spaces and a comment may follow. */
        void expectEnd() throws YamlException
        {
            skipSpaces();
            if (index < text.length() && text.charAt(index) != '#')
            {
                throw new YamlException(number, "unexpected text after the value: " + text.substring(index));
            }
        }

        String quoted() throws YamlException
        {
            char quote = text.charAt(index++);
            StringBuilder value = new StringBuilder();
            while (true)
            {
                if (index >= text.length())
                {
                    throw new YamlException(number, "this string is missing its closing " + quote);
                }
                char c = text.charAt(index++);
                if (quote == '\'')
                {
                    if (c == '\'')
                    {
                        if (at('\''))
                        {
                            value.append('\'');
                            index++;
                            continue;
                        }
                        return value.toString();
                    }
                    value.append(c);
                    continue;
                }
                if (c == '"')
                {
                    return value.toString();
                }
                if (c != '\\')
                {
                    value.append(c);
                    continue;
                }
                if (index >= text.length())
                {
                    throw new YamlException(number, "this string is missing its closing \"");
                }
                char escaped = text.charAt(index++);
                switch (escaped)
                {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case 'r' -> value.append('\r');
                    case '0' -> value.append('\0');
                    case '"', '\\', '/', ' ' -> value.append(escaped);
                    case 'x' -> value.appendCodePoint(hex(2));
                    case 'u' -> value.appendCodePoint(hex(4));
                    case 'U' -> value.appendCodePoint(hex(8));
                    // Not a YAML escape: keep it as written rather than refuse the file.
                    default -> value.append('\\').append(escaped);
                }
            }
        }

        private int hex(int digits) throws YamlException
        {
            if (index + digits > text.length())
            {
                throw new YamlException(number, "incomplete escape in a string");
            }
            try
            {
                int code = Integer.parseInt(text.substring(index, index + digits), 16);
                index += digits;
                return code;
            }
            catch (NumberFormatException invalid)
            {
                throw new YamlException(number, "invalid escape in a string");
            }
        }

        List<Object> flowList() throws YamlException
        {
            index++; // [
            List<Object> list = new ArrayList<>();
            skipSpaces();
            if (at(']'))
            {
                index++;
                return list;
            }
            while (true)
            {
                skipSpaces();
                if (index >= text.length())
                {
                    throw new YamlException(number, "this list is missing its closing ]");
                }
                char c = text.charAt(index);
                if (c == '"' || c == '\'')
                {
                    list.add(quoted());
                }
                else if (c == '[' || c == '{')
                {
                    throw new YamlException(number, "lists can only hold plain values, not nested lists or keys");
                }
                else
                {
                    int start = index;
                    while (index < text.length() && text.charAt(index) != ',' && text.charAt(index) != ']')
                    {
                        index++;
                    }
                    list.add(plain(text.substring(start, index).trim()));
                }
                skipSpaces();
                if (at(','))
                {
                    index++;
                    continue;
                }
                if (at(']'))
                {
                    index++;
                    return list;
                }
                throw new YamlException(number, "expected , or ] in this list");
            }
        }
    }
}
