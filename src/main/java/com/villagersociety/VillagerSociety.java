package com.villagersociety;

import com.villagersociety.command.SocietyCommands;
import com.villagersociety.config.ModConfig;
import com.villagersociety.events.SocietyEvents;
import com.villagersociety.events.VillagerInteractions;
import com.villagersociety.events.WitnessMemory;
import com.villagersociety.leader.StaffActions;
import com.villagersociety.leader.StaffEvents;
import com.villagersociety.network.ControlSync;
import com.villagersociety.network.ReputationSync;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.rebellion.RebellionEvents;
import com.villagersociety.rebellion.RebellionManager;
import com.villagersociety.recommendation.ModItems;
import com.villagersociety.reputation.ReputationManager;
import com.villagersociety.village.VillageLocator;
import com.villagersociety.workers.WorkerManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint. Only wires modules together; each system lives in its own package.
 * Etapas: 1 config+persistência, 2 vilas/reputação, 3 eventos/testemunhas/fofoca, 4 descontos/carta,
 * 6 rebelião e combate, 7 painel (HUD), 8 líder da vila, imigração e trabalhadores, esmeraldas.
 */
public class VillagerSociety implements ModInitializer {
	public static final String MOD_ID = "villager_society";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Shared reputation rules (stateless apart from small anti-duplication caches). */
	public static final ReputationManager REPUTATION = new ReputationManager();
	/** What each villager knows about each player's misdeeds (transient). */
	public static final WitnessMemory MEMORY = new WitnessMemory();
	/** Active rebellions (transient; rebuilt from reputation after a restart). */
	public static final RebellionManager REBELLIONS = new RebellionManager();
	/** Jobs given to villagers by village leaders (assignments persist; progress is transient). */
	public static final WorkerManager WORKERS = new WorkerManager();

	@Override
	public void onInitialize() {
		ModConfig.load();
		ModItems.register();
		SocietyEvents.register();
		VillagerInteractions.register();
		RebellionEvents.register();
		ReputationSync.register();
		ControlSync.register();
		StaffEvents.register();
		WORKERS.register();
		SocietyCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SocietyState state = SocietyState.get(server);
			REBELLIONS.resetPersistedLabels(state);
			LOGGER.info("Villager Society loaded: {} village(s) registered.", state.villageCount());
		});
		// Drop transient state (it holds ServerLevel references) when the server stops.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VillageLocator.clearCache();
			SocietyEvents.clearTransientState();
			MEMORY.clear();
			REBELLIONS.clear();
			ReputationSync.clear();
			ControlSync.clear();
			StaffActions.clear();
			WORKERS.clear();
		});

		LOGGER.info("Villager Society initialized.");
	}
}
