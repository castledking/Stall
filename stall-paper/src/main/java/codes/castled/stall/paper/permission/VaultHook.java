package codes.castled.stall.paper.permission;

import codes.castled.stall.shop.ShopLimits;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional Vault permission provider integration, matching GPExpansion's permission cleanup model.
 */
public final class VaultHook
{
    private static Object vaultPermission;
    private static Method playerAdd;
    private static Method playerRemove;

    static
    {
        try
        {
            if (Bukkit.getPluginManager().getPlugin("Vault") != null)
            {
                Class<?> permClass = Class.forName("net.milkbowl.vault.permission.Permission");
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(permClass);
                if (rsp != null)
                {
                    vaultPermission = rsp.getProvider();
                    playerAdd = permClass.getMethod("playerAdd", String.class, OfflinePlayer.class, String.class);
                    playerRemove = permClass.getMethod("playerRemove", String.class, OfflinePlayer.class, String.class);
                }
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    private VaultHook()
    {
    }

    public static boolean isAvailable()
    {
        return vaultPermission != null && playerAdd != null && playerRemove != null;
    }

    public static void setLimitPermission(Player player, int limit)
    {
        if (!isAvailable() || player == null)
        {
            return;
        }
        try
        {
            List<String> toRemove = new ArrayList<>();
            for (PermissionAttachmentInfo info : player.getEffectivePermissions())
            {
                String node = info.getPermission();
                if (node.startsWith("stall.limit.") && info.getValue())
                {
                    toRemove.add(node);
                }
            }
            for (String node : toRemove)
            {
                playerRemove.invoke(vaultPermission, null, player, node);
            }
            String grant = limit == ShopLimits.UNLIMITED ? "stall.limit.unlimited" : ("stall.limit." + limit);
            playerAdd.invoke(vaultPermission, null, player, grant);
        }
        catch (Throwable ignored)
        {
        }
    }

    public static void setOffersPermission(Player player, int offers)
    {
        if (!isAvailable() || player == null)
        {
            return;
        }
        try
        {
            List<String> toRemove = new ArrayList<>();
            for (PermissionAttachmentInfo info : player.getEffectivePermissions())
            {
                String node = info.getPermission();
                if (node.startsWith("stall.offers.") && info.getValue())
                {
                    toRemove.add(node);
                }
            }
            for (String node : toRemove)
            {
                playerRemove.invoke(vaultPermission, null, player, node);
            }
            playerAdd.invoke(vaultPermission, null, player, "stall.offers." + offers);
        }
        catch (Throwable ignored)
        {
        }
    }
}
