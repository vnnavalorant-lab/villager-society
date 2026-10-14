package com.villagersociety.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players hold the Sprint key (Ctrl by default). The server cannot see a held key while the
 * player stands still, so the client reports changes. Falls back to the server-side sprinting flag.
 */
public final class ControlSync {
	private static final Set<UUID> DOWN = new HashSet<>();

	private ControlSync() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CtrlKeyPayload.TYPE, CtrlKeyPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CtrlKeyPayload.TYPE, (payload, context) -> {
			UUID id = context.player().getUUID();
			if (payload.down()) {
				DOWN.add(id);
			} else {
				DOWN.remove(id);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 200 == 0) {
				DOWN.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			}
		});
	}

	public static boolean isCtrlDown(ServerPlayer player) {
		return DOWN.contains(player.getUUID()) || player.isSprinting();
	}

	public static void clear() {
		DOWN.clear();
	}
}
