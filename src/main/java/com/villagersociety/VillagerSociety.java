package com.villagersociety;

import com.villagersociety.command.SocietyCommands;
import com.villagersociety.config.ModConfig;
import com.villagersociety.events.SocietyEvents;
import com.villagersociety.events.VillagerInteractions;
import com.villagersociety.events.WitnessMemory;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.recommendation.ModItems;
import com.villagersociety.reputation.ReputationManager;
import com.villagersociety.village.VillageLocator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint. Only wires modules together; each system lives in its own package.
 * Etapas feitas: 1 (config + persistência), 2 (vilas, personalidades, reputação),
 * 3 (eventos, testemunhas, fofoca), 4 (descontos, carta de recomendação).
 */
public class VillagerSociety implements ModInitializer {
	public static final String MOD_ID = "villager_society";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Shared reputation rules (stateless apart from small anti-duplication caches). */
	public static final ReputationManager REPUTATION = new ReputationManager();
	/** What each villager knows about each player's misdeeds (transient). */
	public static final WitnessMemory MEMORY = new WitnessMemory();

	@Override
	public void onInitialize() {
		ModConfig.load();
		ModItems.register();
		SocietyEvents.register();
		VillagerInteractions.register();
		SocietyCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SocietyState state = SocietyState.get(server);
			LOGGER.info("Villager Society loaded: {} village(s) registered.", state.villageCount());
		});
		// Drop transient state (it holds ServerLevel references) when the server stops.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VillageLocator.clearCache();
			SocietyEvents.clearTransientState();
			MEMORY.clear();
		});

		LOGGER.info("Villager Society initialized.");
	}
}
