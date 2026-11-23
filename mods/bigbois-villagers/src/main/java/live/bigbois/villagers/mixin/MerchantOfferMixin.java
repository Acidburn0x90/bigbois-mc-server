package live.bigbois.villagers.mixin;

import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps all merchant offers permanently in stock with zero demand inflation.
 */
@Mixin(MerchantOffer.class)
public class MerchantOfferMixin {
    @Shadow
    private int uses;

    @Shadow
    private int demand;

    @Inject(method = "increaseUses", at = @At("HEAD"), cancellable = true)
    private void bigbois$preventUseIncrement(CallbackInfo ci) {
        this.uses = 0;
        ci.cancel();
    }

    @Inject(method = "updateDemand", at = @At("HEAD"), cancellable = true)
    private void bigbois$preventDemandUpdate(CallbackInfo ci) {
        this.demand = 0;
        ci.cancel();
    }

    @Inject(method = "isOutOfStock", at = @At("HEAD"), cancellable = true)
    private void bigbois$neverOutOfStock(CallbackInfoReturnable<Boolean> cir) {
        this.uses = 0;
        cir.setReturnValue(false);
    }

    @Inject(method = "needsRestock", at = @At("HEAD"), cancellable = true)
    private void bigbois$neverNeedsRestock(CallbackInfoReturnable<Boolean> cir) {
        this.uses = 0;
        cir.setReturnValue(false);
    }

    @Inject(method = "getModifiedCostCount", at = @At("HEAD"))
    private void bigbois$clearDemandBeforeCost(ItemCost cost, CallbackInfoReturnable<Integer> cir) {
        this.demand = 0;
    }
}
