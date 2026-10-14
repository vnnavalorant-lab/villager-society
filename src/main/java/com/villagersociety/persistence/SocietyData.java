package com.villagersociety.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure data model persisted in the world. No logic beyond clamping/trimming,
 * so rules live in the reputation/ village/ recommendation/ leader/ workers/ modules.
 * Each class exposes a Codec used by {@link SocietyState}.
 */
public final class SocietyData {
	private SocietyData() {
	}

	/** One entry in a player's history inside a village (good deed or offense). */
	public record HistoryEntry(String kind, int delta, long gameTime, boolean grave) {
		public static final Codec<HistoryEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(HistoryEntry::kind),
			Codec.INT.fieldOf("delta").forGetter(HistoryEntry::delta),
			Codec.LONG.fieldOf("time").forGetter(HistoryEntry::gameTime),
			Codec.BOOL.fieldOf("grave").forGetter(HistoryEntry::grave)
		).apply(i, HistoryEntry::new));
	}

	/** Independent standing of one player inside one village. */
	public static final class PlayerStanding {
		public static final int MIN = -100;
		public static final int MAX = 100;

		public int score;
		public int tradesCompleted;
		public int goodDeeds;
		public int offenses;
		public long lastRecoveryTime;
		public long graveLockUntil;
		public final List<HistoryEntry> history;

		public PlayerStanding() {
			this(0, 0, 0, 0, 0L, 0L, new ArrayList<>());
		}

		public PlayerStanding(int score, int trades, int goodDeeds, int offenses,
							  long lastRecoveryTime, long graveLockUntil, List<HistoryEntry> history) {
			this.score = Math.max(MIN, Math.min(MAX, score));
			this.tradesCompleted = Math.max(0, trades);
			this.goodDeeds = Math.max(0, goodDeeds);
			this.offenses = Math.max(0, offenses);
			this.lastRecoveryTime = lastRecoveryTime;
			this.graveLockUntil = graveLockUntil;
			this.history = new ArrayList<>(history);
		}

		public void setScore(int value) {
			this.score = Math.max(MIN, Math.min(MAX, value));
		}

		/** Keeps only the newest {@code max} entries. */
		public void addHistory(HistoryEntry entry, int max) {
			history.add(entry);
			while (history.size() > max) {
				history.remove(0);
			}
		}

		public static final Codec<PlayerStanding> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("score").forGetter(s -> s.score),
			Codec.INT.optionalFieldOf("trades", 0).forGetter(s -> s.tradesCompleted),
			Codec.INT.optionalFieldOf("good_deeds", 0).forGetter(s -> s.goodDeeds),
			Codec.INT.optionalFieldOf("offenses", 0).forGetter(s -> s.offenses),
			Codec.LONG.optionalFieldOf("last_recovery", 0L).forGetter(s -> s.lastRecoveryTime),
			Codec.LONG.optionalFieldOf("grave_lock_until", 0L).forGetter(s -> s.graveLockUntil),
			HistoryEntry.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(s -> s.history)
		).apply(i, PlayerStanding::new));
	}

	/** Persistent social record of one village. */
	public static final class VillageData {
		public final UUID id;
		public BlockPos center;
		public int radius;
		/** Personality id (resolved by village/ module). Stored as string to survive enum changes. */
		public String personality;
		/** UUID of the villager acting as representative (issues/receives recommendation letters), if any. */
		public Optional<UUID> leader;
		public String rebellionState;
		/** Player UUID -> standing. */
		public final Map<UUID, PlayerStanding> standings;
		/** UUID of the PLAYER who leads this village, if any. */
		public Optional<UUID> playerLeader;
		/** Hay bales counted in the village when the first one was broken, and how many were broken since. */
		public int hayTotal;
		public int hayBroken;

		public VillageData(UUID id, BlockPos center, int radius, String personality,
						   Optional<UUID> leader, String rebellionState, Map<UUID, PlayerStanding> standings,
						   Optional<UUID> playerLeader, int hayTotal, int hayBroken) {
			this.id = id;
			this.center = center;
			this.radius = radius;
			this.personality = personality;
			this.leader = leader;
			this.rebellionState = rebellionState;
			this.standings = new HashMap<>(standings);
			this.playerLeader = playerLeader;
			this.hayTotal = Math.max(0, hayTotal);
			this.hayBroken = Math.max(0, hayBroken);
		}

		public VillageData(UUID id, BlockPos center, int radius, String personality,
						   Optional<UUID> leader, String rebellionState, Map<UUID, PlayerStanding> standings) {
			this(id, center, radius, personality, leader, rebellionState, standings, Optional.empty(), 0, 0);
		}

		public PlayerStanding standing(UUID player) {
			return standings.computeIfAbsent(player, k -> new PlayerStanding());
		}

		public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(v -> v.id),
			BlockPos.CODEC.fieldOf("center").forGetter(v -> v.center),
			Codec.INT.fieldOf("radius").forGetter(v -> v.radius),
			Codec.STRING.fieldOf("personality").forGetter(v -> v.personality),
			UUIDUtil.STRING_CODEC.optionalFieldOf("leader").forGetter(v -> v.leader),
			Codec.STRING.optionalFieldOf("rebellion_state", "NONE").forGetter(v -> v.rebellionState),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerStanding.CODEC)
				.optionalFieldOf("standings", Map.of()).forGetter(v -> v.standings),
			UUIDUtil.STRING_CODEC.optionalFieldOf("player_leader").forGetter(v -> v.playerLeader),
			Codec.INT.optionalFieldOf("hay_total", 0).forGetter(v -> v.hayTotal),
			Codec.INT.optionalFieldOf("hay_broken", 0).forGetter(v -> v.hayBroken)
		).apply(i, VillageData::new));
	}

	/** Server-authority record of an issued recommendation letter. */
	public static final class RecommendationRecord {
		public final UUID id;
		public final UUID issuerVillage;
		public final UUID issuerVillager;
		public final UUID player;
		public final int issuerReputation;
		public final long issuedAt;
		public boolean used;
		public Optional<UUID> usedInVillage;

		public RecommendationRecord(UUID id, UUID issuerVillage, UUID issuerVillager, UUID player,
									int issuerReputation, long issuedAt, boolean used, Optional<UUID> usedInVillage) {
			this.id = id;
			this.issuerVillage = issuerVillage;
			this.issuerVillager = issuerVillager;
			this.player = player;
			this.issuerReputation = issuerReputation;
			this.issuedAt = issuedAt;
			this.used = used;
			this.usedInVillage = usedInVillage;
		}

		public static final Codec<RecommendationRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(r -> r.id),
			UUIDUtil.STRING_CODEC.fieldOf("issuer_village").forGetter(r -> r.issuerVillage),
			UUIDUtil.STRING_CODEC.fieldOf("issuer_villager").forGetter(r -> r.issuerVillager),
			UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(r -> r.player),
			Codec.INT.fieldOf("issuer_reputation").forGetter(r -> r.issuerReputation),
			Codec.LONG.fieldOf("issued_at").forGetter(r -> r.issuedAt),
			Codec.BOOL.optionalFieldOf("used", false).forGetter(r -> r.used),
			UUIDUtil.STRING_CODEC.optionalFieldOf("used_in_village").forGetter(r -> r.usedInVillage)
		).apply(i, RecommendationRecord::new));
	}

	/** A villager assigned by a village leader to a job (mine stone, chop trees, farm) around a point. */
	public static final class WorkerAssignment {
		public final UUID villager;
		public final UUID owner;
		public final UUID village;
		/** Job id (resolved by workers/ module). */
		public final String job;
		public final BlockPos center;
		public final int radius;

		public WorkerAssignment(UUID villager, UUID owner, UUID village, String job, BlockPos center, int radius) {
			this.villager = villager;
			this.owner = owner;
			this.village = village;
			this.job = job;
			this.center = center;
			this.radius = radius;
		}

		public static final Codec<WorkerAssignment> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("villager").forGetter(w -> w.villager),
			UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(w -> w.owner),
			UUIDUtil.STRING_CODEC.fieldOf("village").forGetter(w -> w.village),
			Codec.STRING.fieldOf("job").forGetter(w -> w.job),
			BlockPos.CODEC.fieldOf("center").forGetter(w -> w.center),
			Codec.INT.fieldOf("radius").forGetter(w -> w.radius)
		).apply(i, WorkerAssignment::new));
	}
}
