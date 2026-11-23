package live.bigbois.villagers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows unemployed villagers to walk to and claim job sites whenever awake.
 */
@Mixin(GoToPotentialJobSite.class)
public class GoToPotentialJobSiteMixin {
    @Inject(
        method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bigbois$allowWhenAwake(ServerLevel level, Villager villager, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(!villager.isSleeping());
    }
}
