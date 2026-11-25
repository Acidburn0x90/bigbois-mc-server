package live.bigbois.villagers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint for BigBois Villagers server-side cross-play QoL mod.
 */
public class BigBoisVillagersMod implements ModInitializer {
    public static final String MOD_ID = "bigbois-villagers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            TradeCycleManager.registerCommands(dispatcher)
        );
        UseEntityCallback.EVENT.register(TradeCycleManager::onUseEntity);
        UseBlockCallback.EVENT.register(TradeCycleManager::onUseBlock);
        LOGGER.info("Initialized BigBois Villagers 1.2.0 (cross-play server-side villager QoL & inventory Mending)");
    }
}
