package live.bigbois.villagers.mixin;

import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents JourneyMap Shared Maps from sending custom tile payloads to Vanilla or Bedrock clients.
 */
@Pseudo
@Mixin(targets = "com.jmsharedmaps.network.TileBatchSender", remap = false)
public class JmSharedMapsRelayGuardMixin {
    @Unique
    private static final Identifier BIGBOIS_JM_BATCH_CHANNEL =
        Identifier.fromNamespaceAndPath("jmsharedmaps", "chunk_tile_batch");

    @Inject(
        method = "send(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/List;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void bigbois$skipClientsWithoutMod(ServerPlayer player, List<?> tiles, CallbackInfo ci) {
        if (!ServerPlayNetworking.canSend(player, BIGBOIS_JM_BATCH_CHANNEL)) {
            ci.cancel();
        }
    }
}
