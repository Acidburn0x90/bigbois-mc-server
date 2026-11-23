package live.bigbois.villagers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link Villager#updateTrades(ServerLevel)} for server-side trade rerolling.
 */
@Mixin(Villager.class)
public interface VillagerInvoker {
    @Invoker("updateTrades")
    void bigbois$invokeUpdateTrades(ServerLevel level);
}
