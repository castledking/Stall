package codes.castled.stall.shop;

import java.util.List;

/**
 * What a Stall token looks like, the same on both platforms.
 *
 * <p>A token is a vanilla paper item carrying a marker. Paper keeps a plugin's item data under
 * {@value #BUKKIT_VALUES} in the item's {@code minecraft:custom_data}, keyed {@value #MARKER}. Fabric
 * writes the marker in exactly the same place, so a token from either platform is recognized by the other.
 */
public final class Tokens
{
    public static final String NAME = "Stall Token";

    public static final List<String> LORE = List.of(
            "Use it on a barrel to turn the",
            "barrel into your shop.");

    /** The marker's key: Paper's {@code NamespacedKey} {@code stall:token}. */
    public static final String MARKER = "stall:token";

    /** Where Paper stores a plugin's item data inside {@code minecraft:custom_data}. */
    public static final String BUKKIT_VALUES = "PublicBukkitValues";

    /** Most tokens one command hands out at a time: one stack. */
    public static final int MAX_PER_COMMAND = 64;

    private Tokens()
    {
    }
}
