package com.villagersociety.recommendation;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.PlayerStanding;

import java.util.Locale;
import java.util.Optional;

/** Pure eligibility and bonus rules for recommendation letters. */
public final class RecommendationRules {
	private RecommendationRules() {
	}

	/** Empty when the player may receive a letter; otherwise the reason (pt-BR). */
	public static Optional<String> checkIssue(PlayerStanding st, ModConfig cfg, long now) {
		if (st.score < cfg.letterMinReputation) {
			return Optional.of(String.format(Locale.ROOT, "sua reputação é insuficiente (%d/%d)",
				st.score, cfg.letterMinReputation));
		}
		if (st.tradesCompleted < cfg.letterMinTrades) {
			return Optional.of(String.format(Locale.ROOT, "poucas trocas concluídas (%d/%d)",
				st.tradesCompleted, cfg.letterMinTrades));
		}
		if (st.goodDeeds < cfg.letterMinGoodDeeds) {
			return Optional.of(String.format(Locale.ROOT, "poucas boas ações relevantes (%d/%d)",
				st.goodDeeds, cfg.letterMinGoodDeeds));
		}
		if (now < st.graveLockUntil) {
			return Optional.of("você cometeu infrações graves recentemente");
		}
		return Optional.empty();
	}

	/** Trust granted by a letter: reduced when the issuing village no longer exists / is unknown. */
	public static int trustBonus(ModConfig cfg, int issuerReputationAtIssue, boolean issuerVillageKnown) {
		if (!issuerVillageKnown) {
			return cfg.letterTrustBonusUnknownVillage;
		}
		double f = Math.max(0.0, Math.min(1.0, issuerReputationAtIssue / (double) cfg.letterMinReputation));
		return (int) Math.round(cfg.letterTrustBonus * f);
	}
}
