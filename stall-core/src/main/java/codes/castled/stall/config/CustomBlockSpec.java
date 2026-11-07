package codes.castled.stall.config;

import java.util.Locale;

/**
 * Configuration for a custom block/model from CraftEngine, Nexo, or Oraxen supported as a shop block.
 *
 * @param id the custom block/model ID (e.g. {@code "market_stall"} or {@code "nexo:stall"})
 * @param size the inventory stock size in slots (9, 18, 27, 36, 45, or 54)
 * @param title the display title for the shop container / stock view
 */
public record CustomBlockSpec(String id, int size, String title)
{
    public CustomBlockSpec
    {
        if (id == null || id.isBlank())
        {
            throw new IllegalArgumentException("Custom block id cannot be blank");
        }
        id = id.trim().toLowerCase(Locale.ROOT);
        size = Math.clamp(size, 9, 54);
        if (size % 9 != 0)
        {
            size = ((size / 9) + 1) * 9;
        }
        if (title == null || title.isBlank())
        {
            title = defaultTitle(id);
        }
    }

    private static String defaultTitle(String id)
    {
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        name = name.replace('_', ' ').replace('-', ' ');
        if (name.isEmpty())
        {
            return "Stall";
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
