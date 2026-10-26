package codes.castled.stall.fabric;

import codes.castled.stall.shop.ShopLimits;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Stall's permissions through Fabric API's permission API. {@code stall:limit.5} is the node LuckPerms
 * reads as {@code stall.limit.5}, so permissions are written the same way as on Paper. Without a
 * permission mod, the defaults below apply: everyone may use and make shops, operators may administer.
 */
final class StallPermissions
{
    static final String USE = "use";
    static final String CREATE = "create";
    static final String TOKEN = "token";
    static final String ADMIN = "admin";
    /** Barrel shops hold 54 slots of stock. A perk to hand out, so nobody has it by default. */
    static final String LARGE_BARREL = "barrel.large";

    private StallPermissions()
    {
    }

    /**
     * @param path the permission without its {@code stall.} prefix
     */
    static boolean has(PermissionContextOwner subject, String path)
    {
        Identifier node = Identifier.fromNamespaceAndPath(StallFabric.MOD_ID, path);
        return switch (path)
        {
            case USE, CREATE, TOKEN -> subject.checkPermission(node, true);
            case ADMIN, ShopLimits.UNLIMITED_PERMISSION -> subject.checkPermission(node, PermissionLevel.GAMEMASTERS);
            default -> subject.checkPermission(node, false);
        };
    }

    static int shopLimit(PermissionContextOwner subject)
    {
        return ShopLimits.limitFor(path -> has(subject, path));
    }
}
