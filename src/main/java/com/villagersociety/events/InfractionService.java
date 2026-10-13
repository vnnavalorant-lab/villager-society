package com.villagersociety.events;

import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.reputation.ReputationAction;
import com.villagersociety.reputation.ReputationManager;
import com.villagersociety.village.Personality;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * Single entry point for "a player did something in a village".
 *  - Offenses are weighted by witnesses: nobody saw it = weak evidence; many saw it = stronger reaction.
 *  - Direct witnesses remember the player and gossip to nearby villagers with a delay, hop by hop.
 *  - All work happens in response to an event; nothing scans the world per tick.
 */
public final class InfractionService {
	private static final double UNWITNESSED_WEIGHT = 0.3;
	private static final int MAX_WITNESSES = 8;
	private static final int MAX_GOSSIPERS = 3;
	private static final int MAX_LISTENERS = 6;

	private InfractionService() {
	}

	public static ReputationManager.Result report(ServerLevel level, ServerPlayer player, SocietyState state,
												  VillageData village, ReputationAction action,
												  BlockPos where, boolean accidental) {
		ModConfig cfg = ModConfig.get();
		long now = level.getGameTime();
		Personality personality = Personality.fromId(village.personality);
		UUID playerId = player.getUUID();

		List<LivingEntity> witnesses = List.of();
		double weight = 1.0;
		if (!action.isPositive()) {
			witnesses = findWitnesses(level, player, where, cfg.witnessDistance);
			weight = witnesses.isEmpty() ? UNWITNESSED_WEIGHT : Math.min(1.5, 1.0 + 0.15 * (witnesses.size() - 1));
		}

		ReputationManager.Result result = VillagerSociety.REPUTATION.apply(village.id, playerId,
			village.standing(playerId), personality, action, accidental, now, weight);
		if (!result.counted()) {
			return result;
		}
		state.setDirty();

		if (!action.isPositive() && VillagerSociety.REPUTATION.triggersHostility(
			village.id, playerId, action, !witnesses.isEmpty(), now)) {
			VillagerSociety.REPUTATION.declareHostile(village.standing(playerId), now);
			state.setDirty();
		}

		if (!action.isPositive() && !witnesses.isEmpty()) {
			int gossipers = 0;
			for (LivingEntity w : witnesses) {
				VillagerSociety.MEMORY.learn(w.getUUID(), playerId, action, WitnessMemory.Source.DIRECT, now);
				if (gossipers++ < MAX_GOSSIPERS) {
					scheduleGossip(level, w.getUUID(), playerId, action, personality, 0);
				}
			}
		}
		return result;
	}

	/** Villagers awake, within range and with line of sight to the player. */
	static List<LivingEntity> findWitnesses(ServerLevel level, ServerPlayer player, BlockPos where, double distance) {
		double max2 = distance * distance;
		double cx = where.getX() + 0.5;
		double cy = where.getY() + 0.5;
		double cz = where.getZ() + 0.5;
		List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class,
			new AABB(where).inflate(distance),
			e -> e.getType() == EntityType.VILLAGER && e.isAlive() && !e.isSleeping()
				&& e.distanceToSqr(cx, cy, cz) <= max2 && e.hasLineOfSight(player));
		return list.size() > MAX_WITNESSES ? list.subList(0, MAX_WITNESSES) : list;
	}

	private static void scheduleGossip(ServerLevel level, UUID from, UUID player, ReputationAction action,
									   Personality personality, int hop) {
		ModConfig cfg = ModConfig.get();
		if (hop >= cfg.gossipMaxHops) {
			return;
		}
		int radius = (int) Math.round(cfg.gossipRadius * personality.gossipMultiplier());
		if (radius < 4) {
			return;
		}
		long at = level.getServer().getTickCount() + (long) cfg.gossipDelayTicks * (hop + 1);
		GossipScheduler.schedule(at, () -> {
			Entity source = level.getEntity(from);
			if (!(source instanceof LivingEntity speaker) || !speaker.isAlive()) {
				return; // died or unloaded meanwhile: the rumor dies with it
			}
			long now = level.getGameTime();
			List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
				speaker.getBoundingBox().inflate(radius),
				e -> e != speaker && e.getType() == EntityType.VILLAGER && e.isAlive());
			int told = 0;
			for (LivingEntity listener : near) {
				if (told >= MAX_LISTENERS) {
					break;
				}
				if (VillagerSociety.MEMORY.knows(listener.getUUID(), player, now)) {
					continue;
				}
				VillagerSociety.MEMORY.learn(listener.getUUID(), player, action, WitnessMemory.Source.HEARSAY, now);
				told++;
				scheduleGossip(level, listener.getUUID(), player, action, personality, hop + 1);
			}
		});
	}
}
