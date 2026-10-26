package codes.castled.stall.fabric.mixin;

import codes.castled.stall.fabric.ShopProtection;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a chest placed against someone else's chest shop from joining it, which would open the shop's
 * stock through the new half. Fabric API has no block placement event, so the chest's own choice of
 * whether to join a neighbour is adjusted.
 */
@Mixin(ChestBlock.class)
abstract class ChestBlockMixin
{
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void stall$keepShopChestsApart(BlockPlaceContext context, CallbackInfoReturnable<BlockState> callback)
    {
        callback.setReturnValue(ShopProtection.keepShopChestsApart(context, callback.getReturnValue()));
    }
}
