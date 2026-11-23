package live.bigbois.villagers.mixin;

import live.bigbois.villagers.TradeCycleManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dynamically adapts the Fletcher 4-log trade to the player's carried wood type during Spacebar/quick-trade refill.
 */
@Mixin(MerchantMenu.class)
public class MerchantMenuMixin {
    @Shadow
    @Final
    private Merchant trader;

    @Shadow
    @Final
    private MerchantContainer tradeContainer;

    @Inject(method = "tryMoveItems", at = @At("HEAD"))
    private void bigbois$adaptFletcherLogOnQuickMove(int idx, CallbackInfo ci) {
        if (this.trader instanceof Villager villager && villager.getTradingPlayer() instanceof ServerPlayer sp) {
            TradeCycleManager.adaptFletcherLogOfferForMenu(
                villager,
                sp,
                (MerchantMenu) (Object) this,
                this.tradeContainer,
                idx
            );
        }
    }
}
