package com.villagersociety.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagersociety.VillagerSociety;
import com.villagersociety.persistence.SocietyData.RecommendationRecord;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyData.WorkerAssignment;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single SavedData stored in the Overworld's data storage (data/villager_society_society.dat).
 * All reputation, personalities, leaders, workers and recommendations live here and are
 * only marked dirty when something actually changes (never per tick).
 * The API points used here were confirmed by the compiler (id is a String in 1.21.11).
 */
public class SocietyState extends SavedData {
	private final Map<UUID, VillageData> villages = new HashMap<>();
	private final Map<UUID, RecommendationRecord> recommendations = new HashMap<>();
	private final Map<UUID, WorkerAssignment> workers = new HashMap<>();

	public static final Codec<SocietyState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, VillageData.CODEC)
			.optionalFieldOf("villages", Map.of()).forGetter(s -> s.villages),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, RecommendationRecord.CODEC)
			.optionalFieldOf("recommendations", Map.of()).forGetter(s -> s.recommendations),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, WorkerAssignment.CODEC)
			.optionalFieldOf("workers", Map.of()).forGetter(s -> s.workers)
	).apply(i, SocietyState::new));

	public static final SavedDataType<SocietyState> TYPE = new SavedDataType<>(
		VillagerSociety.MOD_ID + "_society", // 1.21.11: the id is a String (confirmed by compiler)
		SocietyState::new,
		CODEC,
		null // no DataFixer: this is a modded save with its own schema
	);

	public SocietyState() {
	}

	private SocietyState(Map<UUID, VillageData> villages, Map<UUID, RecommendationRecord> recommendations,
						 Map<UUID, WorkerAssignment> workers) {
		this.villages.putAll(villages);
		this.recommendations.putAll(recommendations);
		this.workers.putAll(workers);
	}

	/** Always returns the Overworld instance, the single source of truth for all dimensions. */
	public static SocietyState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ----------------------------------------------------------- villages

	public int villageCount() {
		return villages.size();
	}

	public Collection<VillageData> villages() {
		return villages.values();
	}

	public VillageData village(UUID id) {
		return villages.get(id);
	}

	public void putVillage(VillageData village) {
		villages.put(village.id, village);
		setDirty();
	}

	public void removeVillage(UUID id) {
		if (villages.remove(id) != null) {
			setDirty();
		}
	}

	// ---------------------------------------------------- recommendations

	public RecommendationRecord recommendation(UUID id) {
		return recommendations.get(id);
	}

	public void putRecommendation(RecommendationRecord record) {
		recommendations.put(record.id, record);
		setDirty();
	}

	/**
	 * Revokes every still-valid letter issued to {@code player} by {@code village}. Called when a new
	 * letter is issued, so only one letter per player and village is ever valid (lost or duplicated
	 * older copies stop working).
	 */
	public void revokeActiveFor(UUID player, UUID village) {
		boolean changed = false;
		for (RecommendationRecord r : recommendations.values()) {
			if (!r.used && r.player.equals(player) && r.issuerVillage.equals(village)) {
				r.used = true;
				changed = true;
			}
		}
		if (changed) {
			setDirty();
		}
	}

	/**
	 * Atomically marks a recommendation as used. Returns false if it does not exist or was
	 * already used, so a letter can never be redeemed twice.
	 */
	public boolean consumeRecommendation(UUID id, UUID destinationVillage) {
		RecommendationRecord record = recommendations.get(id);
		if (record == null || record.used) {
			return false;
		}
		record.used = true;
		record.usedInVillage = java.util.Optional.of(destinationVillage);
		setDirty();
		return true;
	}

	// ------------------------------------------------------------- workers

	public Collection<WorkerAssignment> workers() {
		return workers.values();
	}

	public WorkerAssignment worker(UUID villager) {
		return workers.get(villager);
	}

	public void putWorker(WorkerAssignment assignment) {
		workers.put(assignment.villager, assignment);
		setDirty();
	}

	public boolean removeWorker(UUID villager) {
		if (workers.remove(villager) != null) {
			setDirty();
			return true;
		}
		return false;
	}

	public List<WorkerAssignment> workersOfVillage(UUID village) {
		List<WorkerAssignment> list = new ArrayList<>();
		for (WorkerAssignment w : workers.values()) {
			if (w.village.equals(village)) {
				list.add(w);
			}
		}
		return list;
	}

	public int workerCountOf(UUID owner) {
		int n = 0;
		for (WorkerAssignment w : workers.values()) {
			if (w.owner.equals(owner)) {
				n++;
			}
		}
		return n;
	}
}
