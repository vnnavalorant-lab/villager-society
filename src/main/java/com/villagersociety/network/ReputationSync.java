package com.villagersociety.network;

import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.rebellion.RebellionPhase;
import com.villagersociety.village.VillageLocator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps each player's HUD panel up to date. Once per second, per online player: the village at their
 * position is looked up (cached) and a packet is sent only if the data changed, plus a refresh every 10 seconds.
 * Players whose client does not have the mod are skipped, so unmodded clients can still join.
 */
public final class ReputationSync {
	private static final long INTERVAL_TICKS = 20;
	private static final long REFRESH_TICKS = 200;

	private static final Map<UUID, ReputationPayload> LAST = new HashMap<>();
	private static final Map<UUID, Long> LAST_SENT = new HashMap<>();

	private ReputationSync() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(ReputationPayload.TYPE, ReputationPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ReputationSync::tick);
	}

	public static void clear() {
		LAST.clear();
		LAST_SENT.clear();
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now % INTERVAL_TICKS != 0) {
			return;
		}
		SocietyState state = SocietyState.get(server);
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			online.add(id);
			if (!ServerPlayNetworking.canSend(player, ReputationPayload.TYPE)) {
				continue;
			}
			ReputationPayload payload = compute(player, state);
			Long sentAt = LAST_SENT.get(id);
			if (!payload.equals(LAST.get(id)) || sentAt == null || now - sentAt >= REFRESH_TICKS) {
				ServerPlayNetworking.send(player, payload);
				LAST.put(id, payload);
				LAST_SENT.put(id, now);
			}
		}
		LAST.keySet().retainAll(online);
		LAST_SENT.keySet().retainAll(online);
	}

	private static ReputationPayload compute(ServerPlayer player, SocietyState state) {
		if (!(player.level() instanceof ServerLevel level)) {
			return ReputationPayload.NONE;
		}
		VillageData village = VillageLocator.locate(level, player.blockPosition(), state);
		if (village == null) {
			return ReputationPayload.NONE;
		}
		int score = 0;
		PlayerStanding st = village.standings.get(player.getUUID());
		if (st != null) {
			VillagerSociety.REPUTATION.recover(st, level.getGameTime());
			score = st.score;
		}
		RebellionPhase phase = VillagerSociety.REBELLIONS.phaseOf(village.id, player.getUUID());
		return new ReputationPayload(true, score, phase.ordinal(), score <= ModConfig.get().rebellionThreshold);
	}
}
