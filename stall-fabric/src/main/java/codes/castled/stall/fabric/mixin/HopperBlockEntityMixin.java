package codes.castled.stall.fabric.mixin;

import codes.castled.stall.fabric.ShopProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps hoppers and hopper minecarts from pulling a shop's stock out of its barrel. Fabric API has no
 * hook for hopper transfers. Hoppers can still put items into a shop.
 */
@Mixin(HopperBlockEntity.class)
abstract class HopperBlockEntityMixin
{
    /** Hoppers and hopper minecarts both pull from the block above them, found the same way vanilla does. */
    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void stall$keepShopStock(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> callback)
    {
        BlockPos above = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
        if (!ShopProtection.mayExtract(level, above))
        {
            callback.setReturnValue(false);
        }
    }
}
