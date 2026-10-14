package com.villagersociety.client;

import com.villagersociety.network.CtrlKeyPayload;
import com.villagersociety.network.ReputationPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entrypoint: receives reputation data, draws the HUD panel and reports the Sprint key (Ctrl by default)
 * so the server knows about "Ctrl + right-click". Holds no authority over anything.
 */
public class VillagerSocietyClient implements ClientModInitializer {
	private static boolean lastCtrl = false;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(ReputationPayload.TYPE,
			(payload, context) -> ClientReputationState.set(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientReputationState.clear();
			lastCtrl = false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			boolean down = client.options.keySprint.isDown();
			if (down != lastCtrl && ClientPlayNetworking.canSend(CtrlKeyPayload.TYPE)) {
				ClientPlayNetworking.send(new CtrlKeyPayload(down));
				lastCtrl = down;
			}
		});
		ReputationHud.register();
	}
}
