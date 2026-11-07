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
    public static final String SHULKER_BOX = "minecraft:shulker_box";

    private ShopContainers()
    {
    }

    /** Whether the container id is any shulker box (undyed or dyed). */
    public static boolean isShulkerBox(String id)
    {
        return id != null && (id.equals(SHULKER_BOX) || (id.startsWith("minecraft:") && id.endsWith("_shulker_box")));
    }

    /** What players call the container, for messages: "barrel", "chest", "trapped chest" or "shulker box". */
    public static String describe(String id)
    {
        if (id == null || id.isBlank())
        {
            return "shop";
        }
        if (isShulkerBox(id))
        {
            return "shulker box";
        }
        return switch (id)
        {
            case CHEST -> "chest";
            case TRAPPED_CHEST -> "trapped chest";
            case BARREL -> "barrel";
            default -> {
                String name = id.startsWith("custom:") ? id.substring("custom:".length()) : id;
                if (name.contains(":"))
                {
                    name = name.substring(name.indexOf(':') + 1);
                }
                yield name.replace('_', ' ').replace('-', ' ');
            }
        };
    }
}
