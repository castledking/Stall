package codes.castled.stall.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Colored text in the legacy format both platforms understand: {@code §} followed by a code. Config files
 * use {@code &} instead, which {@link #translate} turns into {@code §}.
 *
 * <p>Codes: {@code 0}-{@code 9} and {@code a}-{@code f} for colors, {@code k} obfuscated, {@code l} bold,
 * {@code m} strikethrough, {@code n} underlined, {@code o} italic, {@code r} reset, and {@code &#RRGGBB}
 * for any color. As in vanilla, a color clears the formats before it.
 */
public final class LegacyText
{
    public static final char SECTION = '\u00a7';

    private static final String CODES = "0123456789abcdefklmnorx";
    private static final String[] COLOR_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    };

    private LegacyText()
    {
    }

    /**
     * A run of text in one style.
     *
     * @param color a vanilla color name such as {@code gold}, {@code #RRGGBB}, or null for none
     */
    public record Span(String text, String color, boolean bold, boolean italic, boolean underlined,
            boolean strikethrough, boolean obfuscated)
    {
        boolean sameStyle(Span other)
        {
            return java.util.Objects.equals(color, other.color) && bold == other.bold && italic == other.italic
                    && underlined == other.underlined && strikethrough == other.strikethrough
                    && obfuscated == other.obfuscated;
        }

        Span withText(String text)
        {
            return new Span(text, color, bold, italic, underlined, strikethrough, obfuscated);
        }
    }

    /** Turns a config file's {@code &} codes, including {@code &#RRGGBB}, into {@code §} codes. */
    public static String translate(String text)
    {
        if (text.indexOf('&') < 0)
        {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length())
            {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == '#' && i + 8 <= text.length() && isHex(text, i + 2, 6))
                {
                    out.append(SECTION).append('x');
                    for (int digit = i + 2; digit < i + 8; digit++)
                    {
                        out.append(SECTION).append(Character.toLowerCase(text.charAt(digit)));
                    }
                    i += 7;
                    continue;
                }
                if (CODES.indexOf(code) >= 0)
                {
                    out.append(SECTION).append(code);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isHex(String text, int from, int count)
    {
        for (int i = from; i < from + count; i++)
        {
            if (Character.digit(text.charAt(i), 16) < 0)
            {
                return false;
            }
        }
        return true;
    }

    /** Splits {@code §}-coded text into runs of one style each. */
    public static List<Span> parse(String text)
    {
        List<Span> spans = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        Span style = new Span("", null, false, false, false, false, false);
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c != SECTION || i + 1 >= text.length())
            {
                run.append(c);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            Span next = null;
            int skip = 1;
            if (code == 'x' && i + 14 <= text.length() && isHexSequence(text, i + 2))
            {
                StringBuilder hex = new StringBuilder("#");
                for (int digit = i + 3; digit < i + 14; digit += 2)
                {
                    hex.append(Character.toUpperCase(text.charAt(digit)));
                }
                next = new Span("", hex.toString(), false, false, false, false, false);
                skip = 13;
            }
            else if (Character.digit(code, 16) >= 0)
            {
                next = new Span("", COLOR_NAMES[Character.digit(code, 16)], false, false, false, false, false);
            }
            else
            {
                switch (code)
                {
                    case 'k' -> next = new Span("", style.color(), style.bold(), style.italic(), style.underlined(),
                            style.strikethrough(), true);
                    case 'l' -> next = new Span("", style.color(), true, style.italic(), style.underlined(),
                            style.strikethrough(), style.obfuscated());
                    case 'm' -> next = new Span("", style.color(), style.bold(), style.italic(), style.underlined(),
                            true, style.obfuscated());
                    case 'n' -> next = new Span("", style.color(), style.bold(), style.italic(), true,
                            style.strikethrough(), style.obfuscated());
                    case 'o' -> next = new Span("", style.color(), style.bold(), true, style.underlined(),
                            style.strikethrough(), style.obfuscated());
                    case 'r' -> next = new Span("", null, false, false, false, false, false);
                    default ->
                    {
                    }
                }
            }
            if (next == null)
            {
                run.append(c);
                continue;
            }
            add(spans, style.withText(run.toString()));
            run.setLength(0);
            style = next;
            i += skip;
        }
        add(spans, style.withText(run.toString()));
        return spans;
    }

    /** Whether {@code §r§r§g§g§b§b} starts at {@code from}. */
    private static boolean isHexSequence(String text, int from)
    {
        for (int i = from; i < from + 12; i += 2)
        {
            if (text.charAt(i) != SECTION || Character.digit(text.charAt(i + 1), 16) < 0)
            {
                return false;
            }
        }
        return true;
    }

    private static void add(List<Span> spans, Span span)
    {
        if (span.text().isEmpty())
        {
            return;
        }
        if (!spans.isEmpty() && spans.getLast().sameStyle(span))
        {
            spans.set(spans.size() - 1, span.withText(spans.getLast().text() + span.text()));
            return;
        }
        spans.add(span);
    }

    /** The text without any codes. */
    public static String strip(String text)
    {
        StringBuilder plain = new StringBuilder();
        for (Span span : parse(text))
        {
            plain.append(span.text());
        }
        return plain.toString();
    }

    /** The legacy code for a vanilla color name, or 0 if it isn't one. */
    public static char code(String colorName)
    {
        String name = colorName.toLowerCase(Locale.ROOT);
        for (int i = 0; i < COLOR_NAMES.length; i++)
        {
            if (COLOR_NAMES[i].equals(name))
            {
                return Character.forDigit(i, 16);
            }
        }
        return 0;
    }

    /** The codes that set {@code color}, a vanilla color name or {@code #RRGGBB}; empty if it is neither. */
    public static String colorCodes(String color)
    {
        if (color == null)
        {
            return "";
        }
        if (color.startsWith("#") && color.length() == 7 && isHex(color, 1, 6))
        {
            StringBuilder codes = new StringBuilder().append(SECTION).append('x');
            for (int i = 1; i < 7; i++)
            {
                codes.append(SECTION).append(Character.toLowerCase(color.charAt(i)));
            }
            return codes.toString();
        }
        char code = code(color);
        return code == 0 ? "" : String.valueOf(SECTION) + code;
    }

    /**
     * The text as an SNBT text component for an item's name or lore, which vanilla shows in italics unless
     * told otherwise. A single run is one component, exactly as a {@code Component.literal} with that
     * style, so items stay identical to those the Fabric mod makes.
     */
    public static String itemSnbt(String text)
    {
        List<Span> spans = parse(text);
        if (spans.size() == 1)
        {
            return snbt(spans.getFirst(), true);
        }
        StringBuilder root = new StringBuilder("{text:\"\",italic:false");
        if (!spans.isEmpty())
        {
            root.append(",extra:[");
            for (int i = 0; i < spans.size(); i++)
            {
                root.append(i == 0 ? "" : ",").append(snbt(spans.get(i), false));
            }
            root.append(']');
        }
        return root.append('}').toString();
    }

    private static String snbt(Span span, boolean root)
    {
        StringBuilder component = new StringBuilder("{text:").append(snbtString(span.text()));
        if (span.color() != null)
        {
            component.append(",color:").append(snbtString(span.color()));
        }
        if (span.bold())
        {
            component.append(",bold:true");
        }
        if (span.italic())
        {
            component.append(",italic:true");
        }
        else if (root)
        {
            component.append(",italic:false");
        }
        if (span.underlined())
        {
            component.append(",underlined:true");
        }
        if (span.strikethrough())
        {
            component.append(",strikethrough:true");
        }
        if (span.obfuscated())
        {
            component.append(",obfuscated:true");
        }
        return component.append('}').toString();
    }

    private static String snbtString(String text)
    {
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '"' || c == '\\')
            {
                quoted.append('\\').append(c);
            }
            else if (c >= 0x20)
            {
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }
}
