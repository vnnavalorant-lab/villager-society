package com.villagersociety.rebellion;

import com.villagersociety.VillagerSociety;
import com.villagersociety.ai.Armament;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Wires the rebellion system into the server: one tick hook plus the weapon cleanup hooks. */
public final class RebellionEvents {
	private RebellionEvents() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> VillagerSociety.REBELLIONS.tick(server));
		Armament.register(id -> VillagerSociety.REBELLIONS.isParticipant(id));
	}
}
