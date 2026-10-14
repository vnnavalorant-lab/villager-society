package com.villagersociety.leader;

import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.feedback.Say;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyData.WorkerAssignment;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.workers.WorkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the Leader's Staff does:
 *  - Shift + right-click (air or block): cycles the mode (Mineração, Lenhador, Plantação, Imigração)
 *  - right-click a block: sets the work mark (or, in Imigração with a villager selected, picks the destination)
 *  - right-click a villager of your village: gives it the current job (again to cancel it), or selects it for immigration
 * Feedback goes to the action bar, one short line.
 */
public final class StaffActions {
	private static final Map<UUID, StaffState> STATES = new HashMap<>();

	private StaffActions() {
	}

	private static StaffState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), k -> new StaffState());
	}

	public static void clear() {
		STATES.clear();
	}

	public static void cycleMode(ServerPlayer player) {
		StaffState s = state(player);
		s.mode = s.mode.next();
		s.selectedVillager = null;
		s.selectedVillage = null;
		Say.bar(player, "Cajado: " + s.mode.displayName());
	}

	public static void onBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
		StaffState s = state(player);
		if (s.mode == StaffMode.MIGRATE && s.selectedVillager != null) {
			SocietyState society = SocietyState.get(level.getServer());
			Immigration.migrate(level, player, s.selectedVillager, s.selectedVillage, pos, society);
			s.selectedVillager = null;
			s.selectedVillage = null;
			return;
		}
		s.mark = pos.immutable();
		Say.bar(player, "Marca definida: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
	}

	public static void useOnVillager(ServerLevel level, ServerPlayer player, Entity villager,
									 VillageData village, SocietyState society) {
		if (!PlayerLeaders.isLeader(village, player.getUUID())) {
			level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
			return;
		}
		StaffState s = state(player);
		if (s.mode == StaffMode.MIGRATE) {
			s.selectedVillager = villager.getUUID();
			s.selectedVillage = village.id;
			Say.bar(player, "Morador selecionado: clique no destino, em outra vila.");
			return;
		}

		UUID id = villager.getUUID();
		if (society.worker(id) != null) {
			VillagerSociety.WORKERS.unassign(level, society, id);
			Say.bar(player, "Trabalho cancelado.");
			return;
		}
		ModConfig cfg = ModConfig.get();
		if (society.workerCountOf(player.getUUID()) >= cfg.maxWorkersPerLeader) {
			Say.bar(player, "Limite de trabalhadores atingido (" + cfg.maxWorkersPerLeader + ").");
			return;
		}
		WorkerJob job = s.mode.job();
		society.putWorker(new WorkerAssignment(id, player.getUUID(), village.id, job.id(),
			workCenter(s, village, villager), cfg.workAreaRadius));
		Say.bar(player, job.displayName() + ": morador designado.");
	}

	/** The staff mark if it is near the village, otherwise the villager's own position. */
	private static BlockPos workCenter(StaffState s, VillageData village, Entity villager) {
		if (s.mark != null) {
			double limit = village.radius + ModConfig.get().markMaxDistance;
			if (s.mark.distSqr(village.center) <= limit * limit) {
				return s.mark;
			}
		}
		return villager.blockPosition();
	}
}
