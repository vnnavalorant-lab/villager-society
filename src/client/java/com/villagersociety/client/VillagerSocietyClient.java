package com.villagersociety.client;

import com.villagersociety.network.ReputationPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client entrypoint: receives reputation data and draws the HUD panel. Holds no authority over anything. */
public class VillagerSocietyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(ReputationPayload.TYPE,
			(payload, context) -> ClientReputationState.set(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientReputationState.clear());
		ReputationHud.register();
	}
}
