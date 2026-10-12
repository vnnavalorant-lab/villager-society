package com.villagersociety.village;

import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Chooses a persistent village representative among its existing villagers (no new entity).
 * The choice is stored by UUID; it is only changed when the current leader is gone (dead) and
 * the village area is loaded. Candidate selection is deterministic (lowest UUID, adults only).
 */
public final class LeaderService {
	private LeaderService() {
	}

	public static Optional<UUID> ensureLeader(ServerLevel level, VillageData village, SocietyState state) {
		if (village.leader.isPresent()) {
			Entity current = level.getEntity(village.leader.get());
			if (current != null && current.isAlive()) {
				return village.leader;
			}
			if (!level.hasChunkAt(village.center)) {
				return village.leader; // cannot tell if it is dead or just unloaded
			}
		}
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
			new AABB(village.center).inflate(village.radius),
			e -> e.getType() == EntityType.VILLAGER && e.isAlive() && !e.isBaby());
		if (candidates.isEmpty()) {
			return village.leader;
		}
		UUID best = candidates.stream().map(Entity::getUUID).min(Comparator.naturalOrder()).orElseThrow();
		if (!village.leader.equals(Optional.of(best))) {
			village.leader = Optional.of(best);
			state.setDirty();
		}
		return village.leader;
	}
}
