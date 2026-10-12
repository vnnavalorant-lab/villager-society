package com.villagersociety.recommendation;

import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.dialogue.Say;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.RecommendationRecord;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.reputation.ReputationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Issuing and redeeming letters. Everything is decided on the server from the persistent record;
 * the item only carries an id. Redeeming order: validate -> consume record -> consume item -> grant trust.
 */
public final class RecommendationService {
	private RecommendationService() {
	}

	public static void issue(ServerLevel level, ServerPlayer player, VillageData village, UUID leader, SocietyState state) {
		long now = level.getGameTime();
		UUID playerId = player.getUUID();
		PlayerStanding st = village.standing(playerId);
		VillagerSociety.REPUTATION.recover(st, now);

		Optional<String> problem = RecommendationRules.checkIssue(st, ModConfig.get(), now);
		if (problem.isPresent()) {
			Say.toPlayer(player, "Ainda não posso recomendar você: " + problem.get() + ".");
			return;
		}

		// Only one valid letter per player+village: older copies are revoked.
		state.revokeActiveFor(playerId, village.id);
		UUID id = UUID.randomUUID();
		state.putRecommendation(new RecommendationRecord(id, village.id, leader, playerId, st.score, now, false, Optional.empty()));

		ItemStack letter = LetterStack.create(id, shortId(village.id), player.getName().getString());
		if (!player.getInventory().add(letter)) {
			player.drop(letter, false);
		}
		Say.toPlayer(player, "Você conquistou a confiança desta vila. Leve esta carta a outro representante.");
	}

	public static void present(ServerLevel level, ServerPlayer player, ItemStack letter, VillageData destination,
							   SocietyState state) {
		long now = level.getGameTime();
		ModConfig cfg = ModConfig.get();

		Optional<UUID> idOpt = LetterStack.read(letter);
		if (idOpt.isEmpty()) {
			Say.toPlayer(player, "Não consigo ler esta carta.");
			return;
		}
		UUID id = idOpt.get();
		RecommendationRecord record = state.recommendation(id);
		if (record == null) {
			Say.toPlayer(player, "Esta carta não consta em nenhum registro. Parece falsa.");
			return;
		}
		if (!record.player.equals(player.getUUID())) {
			Say.toPlayer(player, "Esta carta foi escrita para outra pessoa.");
			return;
		}
		if (record.used) {
			Say.toPlayer(player, "Esta carta já foi usada ou não é mais válida.");
			return;
		}
		if (record.issuerVillage.equals(destination.id)) {
			Say.toPlayer(player, "Esta carta foi escrita por nós mesmos.");
			return;
		}

		PlayerStanding st = destination.standing(player.getUUID());
		VillagerSociety.REPUTATION.recover(st, now);
		if (now < st.graveLockUntil) {
			Say.toPlayer(player, "Seus crimes aqui são recentes demais. Uma carta não muda isso.");
			return;
		}
		if (st.score >= ReputationManager.letterScoreCap()) {
			Say.toPlayer(player, "Já confiamos em você. Guarde a carta para outra vila.");
			return;
		}

		VillageData issuer = state.village(record.issuerVillage);
		int bonus = RecommendationRules.trustBonus(cfg, record.issuerReputation, issuer != null);

		if (!state.consumeRecommendation(id, destination.id)) {
			Say.toPlayer(player, "Esta carta já foi usada ou não é mais válida.");
			return;
		}
		letter.shrink(1);
		int gained = VillagerSociety.REPUTATION.grantTrust(st, bonus, now);
		state.setDirty();
		Say.toPlayer(player, "O chefe de outra vila falou bem de você. (+" + gained + " de confiança)");
	}

	private static String shortId(UUID id) {
		return id.toString().substring(0, 8);
	}
}
