package com.villagersociety.leader;

import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.feedback.Say;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.recommendation.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The PLAYER leader of a village (different from the villager "representative" used for letters).
 * Becoming the leader: Shift + right-click on a villager of the village while holding a bell, with enough
 * reputation and no other leader. The bell is consumed and a Leader's Staff is handed over (again, if lost).
 * Leadership is lost when reputation falls under {@code leaderKeepReputation}.
 */
public final class PlayerLeaders {
	private PlayerLeaders() {
	}

	public static boolean isLeader(VillageData village, UUID player) {
		return village.playerLeader.isPresent() && village.playerLeader.get().equals(player);
	}

	public static void useBell(ServerLevel level, ServerPlayer player, ItemStack bell, VillageData village,
							   SocietyState state) {
		UUID id = player.getUUID();
		boolean alreadyLeader = isLeader(village, id);
		if (village.playerLeader.isPresent() && !alreadyLeader) {
			Say.bar(player, "Esta vila já tem um líder.");
			return;
		}
		if (!alreadyLeader) {
			PlayerStanding st = village.standing(id);
			VillagerSociety.REPUTATION.recover(st, level.getGameTime());
			ModConfig cfg = ModConfig.get();
			if (!LeaderRules.canBecomeLeader(st.score, cfg)) {
				Say.bar(player, "Reputação insuficiente para liderar: " + st.score + "/" + cfg.leaderMinReputation);
				return;
			}
			village.playerLeader = Optional.of(id);
			state.setDirty();
		}
		if (!player.isCreative()) {
			bell.shrink(1);
		}
		ItemStack staff = new ItemStack(ModItems.LEADER_STAFF);
		if (!player.getInventory().add(staff)) {
			player.drop(staff, false);
		}
		Say.bar(player, alreadyLeader ? "Novo cajado entregue." : "Você agora lidera esta vila.");
	}

	/** Removes leadership from players whose reputation fell too low, releasing their workers. */
	public static void validateAll(MinecraftServer server, SocietyState state) {
		ModConfig cfg = ModConfig.get();
		List<VillageData> toRevoke = new ArrayList<>();
		for (VillageData village : state.villages()) {
			if (village.playerLeader.isEmpty()) {
				continue;
			}
			PlayerStanding st = village.standings.get(village.playerLeader.get());
			if (st == null || LeaderRules.mustStepDown(st.score, cfg)) {
				toRevoke.add(village);
			}
		}
		for (VillageData village : toRevoke) {
			UUID formerLeader = village.playerLeader.orElse(null);
			village.playerLeader = Optional.empty();
			state.setDirty();
			VillagerSociety.WORKERS.releaseVillage(server.overworld(), state, village.id);
			ServerPlayer player = formerLeader == null ? null : server.getPlayerList().getPlayer(formerLeader);
			if (player != null) {
				Say.bar(player, "Você perdeu a liderança da vila.");
			}
		}
	}
}
