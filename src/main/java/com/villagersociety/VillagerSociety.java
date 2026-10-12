package com.villagersociety;

import com.villagersociety.command.SocietyCommands;
import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.reputation.ReputationManager;
import com.villagersociety.village.VillageLocator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint. Only wires modules together; each system lives in its own package.
 * Etapas feitas: 1 (config + persistência), 2 (vilas, personalidades, reputação, comando /villagersociety).
 */
public class VillagerSociety implements ModInitializer {
	public static final String MOD_ID = "villager_society";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Shared reputation rules (stateless apart from a small anti-duplication cache). */
	public static final ReputationManager REPUTATION = new ReputationManager();

	@Override
	public void onInitialize() {
		ModConfig.load();
		SocietyCommands.register();

		// Touch the saved data once at startup so a corrupt save is reported early.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SocietyState state = SocietyState.get(server);
			LOGGER.info("Villager Society loaded: {} village(s) registered.", state.villageCount());
		});
		// The locator cache holds ServerLevel references: drop them when the server stops.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> VillageLocator.clearCache());

		LOGGER.info("Villager Society initialized.");
	}
}
