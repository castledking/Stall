package codes.castled.stall.shop;

/**
 * The blocks a shop can be made from, by their ids as saved with a shop. Copper chests are left out,
 * because copper golems take items out of them.
 */
public final class ShopContainers
{
    public static final String BARREL = "minecraft:barrel";
    public static final String CHEST = "minecraft:chest";
    public static final String TRAPPED_CHEST = "minecraft:trapped_chest";

    private ShopContainers()
    {
    }

    /** What players call the container, for messages: "barrel", "chest" or "trapped chest". */
    public static String describe(String id)
    {
        return switch (id)
        {
            case CHEST -> "chest";
            case TRAPPED_CHEST -> "trapped chest";
            default -> "barrel";
        };
    }
}
