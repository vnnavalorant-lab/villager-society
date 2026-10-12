package com.villagersociety;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint. Only wires modules together; each system lives in its own package.
 * Etapa 1: config + persistência. Os demais módulos serão registrados aqui nas próximas etapas.
 */
public class VillagerSociety implements ModInitializer {
	public static final String MOD_ID = "villager_society";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModConfig.load();

		// Touch the saved data once at startup so a corrupt save is reported early.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SocietyState state = SocietyState.get(server);
			LOGGER.info("Villager Society loaded: {} village(s) registered.", state.villageCount());
		});

		LOGGER.info("Villager Society initialized.");
	}
}
