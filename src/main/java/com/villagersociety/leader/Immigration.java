package com.villagersociety.leader;

import com.villagersociety.VillagerSociety;
import com.villagersociety.feedback.Say;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.reputation.ReputationManager;
import com.villagersociety.village.VillageLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

import java.util.UUID;

/**
 * Moves a villager of the leader's village to another registered village (the block you click is the spot).
 * Rules: the destination must be a different village that does not consider you wanted, must have a free bed,
 * and the spot needs two blocks of air. The villager forgets its old home, workstation and meeting point, so the
 * vanilla behavior claims a new bed and job in the new village. It is teleported (a walk of hundreds of blocks
 * through unloaded chunks is not possible).
 *
 * UNVERIFIED API POINTS (1.21.11): Entity#teleportTo(double,double,double), PoiTypes.HOME,
 * PoiManager.Occupancy.HAS_SPACE, MemoryModuleType.{HOME, JOB_SITE, POTENTIAL_JOB_SITE, MEETING_POINT}.
 */
final class Immigration {
	private Immigration() {
	}

	static void migrate(ServerLevel level, ServerPlayer player, UUID villagerId, UUID sourceVillage,
						BlockPos clicked, SocietyState society) {
		Entity entity = level.getEntity(villagerId);
		if (!(entity instanceof Mob mob) || !mob.isAlive()) {
			Say.bar(player, "Esse morador não está disponível.");
			return;
		}
		VillageData destination = VillageLocator.locate(level, clicked, society);
		if (destination == null) {
			Say.bar(player, "O destino precisa estar dentro de uma vila.");
			return;
		}
		if (destination.id.equals(sourceVillage)) {
			Say.bar(player, "Escolha o destino em OUTRA vila.");
			return;
		}
		PlayerStanding standing = destination.standings.get(player.getUUID());
		if (standing != null && ReputationManager.isWanted(standing)) {
			Say.bar(player, "Essa vila não aceita você.");
			return;
		}
		BlockPos spot = clicked.above();
		if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) {
			Say.bar(player, "Sem espaço livre nesse ponto.");
			return;
		}
		long freeBeds = level.getPoiManager()
			.getInRange(holder -> holder.is(PoiTypes.HOME), destination.center, destination.radius,
				PoiManager.Occupancy.HAS_SPACE)
			.limit(1)
			.count();
		if (freeBeds == 0) {
			Say.bar(player, "Essa vila não tem camas livres.");
			return;
		}

		VillagerSociety.WORKERS.unassign(level, society, villagerId);
		mob.getBrain().eraseMemory(MemoryModuleType.HOME);
		mob.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		mob.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		mob.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT);
		mob.getNavigation().stop();
		mob.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		Say.bar(player, "Morador enviado para a nova vila.");
	}
}
