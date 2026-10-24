package codes.castled.stall.fabric.mixin;

import codes.castled.stall.fabric.ShopProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Filter the affected block list before vanilla destroys blocks or creates their drops. */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin
{
    @Shadow @Final private ServerLevel level;

    @ModifyVariable(method = "explode", at = @At("STORE"), ordinal = 0)
    private List<BlockPos> stall$keepShops(List<BlockPos> blocks)
    {
        return ShopProtection.filterExplosion(level, blocks);
    }
}
