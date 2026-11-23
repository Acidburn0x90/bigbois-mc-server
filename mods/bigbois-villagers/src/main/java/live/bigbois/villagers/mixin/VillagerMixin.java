package live.bigbois.villagers.mixin;

import java.util.Optional;
import live.bigbois.villagers.TradeCycleManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes hit price penalties, blocks job claiming while asleep, and grants novice Fletchers 3 trades with 4-log to emerald.
 */
@Mixin(Villager.class)
public class VillagerMixin {
    @Shadow
    @Final
    private GossipContainer gossips;

    @Inject(method = "onReputationEventFrom", at = @At("HEAD"), cancellable = true)
    private void bigbois$ignoreHurtReputation(ReputationEventType type, Entity source, CallbackInfo ci) {
        if (type == ReputationEventType.VILLAGER_HURT) {
            ci.cancel();
        }
    }

    @Inject(method = "updateSpecialPrices", at = @At("HEAD"))
    private void bigbois$clearHurtPenalty(Player player, CallbackInfo ci) {
        this.gossips.remove(GossipType.MINOR_NEGATIVE);
    }

    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void bigbois$blockJobClaimWhileAsleep(ServerLevel level, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (self.isSleeping() && self.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE)) {
            self.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
            self.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        }
    }

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void bigbois$adjustFletcherStarterTrades(ServerLevel level, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (!self.getVillagerData().profession().is(VillagerProfession.FLETCHER) || self.getVillagerData().level() != 1) {
            return;
        }
        MerchantOffers offers = self.getOffers();
        offers.removeIf(offer -> offer.getBaseCostA().is(Items.STICK) && offer.getResult().is(Items.EMERALD));

        boolean hasLogTrade = offers.stream().anyMatch(
            offer -> offer.getBaseCostA().is(ItemTags.LOGS)
                && offer.getBaseCostA().getCount() == 4
                && offer.getResult().is(Items.EMERALD)
        );
        if (!hasLogTrade) {
            offers.add(0, new MerchantOffer(new ItemCost(Items.OAK_LOG, 4), new ItemStack(Items.EMERALD, 1), 16, 2, 0.05F));
        }

        boolean hasArrowTrade = offers.stream().anyMatch(
            offer -> offer.getBaseCostA().is(Items.EMERALD) && offer.getResult().is(Items.ARROW)
        );
        if (!hasArrowTrade) {
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.ARROW, 16), 12, 1, 0.05F));
        }

        boolean hasFlintTrade = offers.stream().anyMatch(
            offer -> offer.getBaseCostA().is(Items.GRAVEL) && offer.getResult().is(Items.FLINT)
        );
        if (!hasFlintTrade) {
            offers.add(new MerchantOffer(
                new ItemCost(Items.GRAVEL, 10),
                Optional.of(new ItemCost(Items.EMERALD, 1)),
                new ItemStack(Items.FLINT, 10),
                12,
                1,
                0.05F
            ));
        }
    }

    @Inject(method = "startTrading", at = @At("HEAD"))
    private void bigbois$adaptFletcherLogOnOpen(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp) {
            TradeCycleManager.adaptFletcherLogOfferOnOpen((Villager) (Object) this, sp);
        }
    }

    @Inject(method = "rewardTradeXp", at = @At("TAIL"))
    private void bigbois$adaptFletcherLogAfterTrade(MerchantOffer offer, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (self.getTradingPlayer() instanceof ServerPlayer sp && sp.containerMenu instanceof MerchantMenu menu) {
            TradeCycleManager.adaptFletcherLogOfferAfterTrade(self, sp, menu, offer);
        }
    }
}
