package com.villagersociety.events;

import com.villagersociety.reputation.ReputationAction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What each villager personally knows about a player's misdeeds. Transient on purpose: the
 * persistent consequence lives in the village reputation; this only drives who reacts and how.
 * Direct knowledge (saw it) is never downgraded to hearsay (was told).
 */
public final class WitnessMemory {
	public enum Source {
		DIRECT, HEARSAY
	}

	public record Knowledge(ReputationAction action, Source source, long learnedAt) {
	}

	private static final long TTL_TICKS = 24_000L * 3;
	private static final int MAX_VILLAGERS = 2048;

	private final Map<UUID, Map<UUID, Knowledge>> memory = new HashMap<>();

	public void learn(UUID villager, UUID player, ReputationAction action, Source source, long now) {
		if (memory.size() >= MAX_VILLAGERS && !memory.containsKey(villager)) {
			prune(now);
			if (memory.size() >= MAX_VILLAGERS) {
				memory.clear();
			}
		}
		Map<UUID, Knowledge> perPlayer = memory.computeIfAbsent(villager, k -> new HashMap<>());
		Knowledge existing = perPlayer.get(player);
		if (existing != null && existing.source() == Source.DIRECT && source == Source.HEARSAY
			&& now - existing.learnedAt() < TTL_TICKS) {
			return;
		}
		perPlayer.put(player, new Knowledge(action, source, now));
	}

	public Optional<Knowledge> get(UUID villager, UUID player, long now) {
		Map<UUID, Knowledge> perPlayer = memory.get(villager);
		if (perPlayer == null) {
			return Optional.empty();
		}
		Knowledge k = perPlayer.get(player);
		if (k == null) {
			return Optional.empty();
		}
		if (now - k.learnedAt() >= TTL_TICKS) {
			perPlayer.remove(player);
			return Optional.empty();
		}
		return Optional.of(k);
	}

	public boolean knows(UUID villager, UUID player, long now) {
		return get(villager, player, now).isPresent();
	}

	private void prune(long now) {
		memory.values().forEach(m -> m.values().removeIf(k -> now - k.learnedAt() >= TTL_TICKS));
		memory.values().removeIf(Map::isEmpty);
	}

	public void clear() {
		memory.clear();
	}
}
