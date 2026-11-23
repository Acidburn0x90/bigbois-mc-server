package live.bigbois.villagers.mixin;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows any log in {@link ItemTags#LOGS} to satisfy the 4-log Fletcher starter trade.
 */
@Mixin(ItemCost.class)
public class ItemCostMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void bigbois$acceptAnyLogForFletcher(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ItemCost self = (ItemCost) (Object) this;
        if (self.item().is(ItemTags.LOGS) && self.count() == 4 && stack.is(ItemTags.LOGS)) {
            cir.setReturnValue(true);
        }
    }
}
