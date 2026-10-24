package codes.castled.stall.shop;

import java.util.Objects;

/**
 * Where a shop's container is.
 *
 * @param world the dimension key, such as {@code minecraft:overworld}, which Paper and Fabric name alike
 */
public record ShopLocation(String world, int x, int y, int z)
{
    public ShopLocation
    {
        Objects.requireNonNull(world, "world");
    }

    @Override
    public String toString()
    {
        return world + " " + x + " " + y + " " + z;
    }
}
