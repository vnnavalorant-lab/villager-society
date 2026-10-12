package com.villagersociety.recommendation;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.reputation.ReputationManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationRulesTest {
	private final ModConfig cfg = new ModConfig();

	private PlayerStanding standing(int score, int trades, int deeds, long graveLock) {
		return new PlayerStanding(score, trades, deeds, 0, 0L, graveLock, List.of());
	}

	@Test
	void eligiblePlayerMayReceiveLetter() {
		assertTrue(RecommendationRules.checkIssue(standing(80, 10, 3, 0), cfg, 1000).isEmpty());
	}

	@Test
	void eachRequirementBlocksIssuance() {
		assertTrue(RecommendationRules.checkIssue(standing(79, 10, 3, 0), cfg, 1000).isPresent());
		assertTrue(RecommendationRules.checkIssue(standing(90, 9, 3, 0), cfg, 1000).isPresent());
		assertTrue(RecommendationRules.checkIssue(standing(90, 10, 2, 0), cfg, 1000).isPresent());
		assertTrue(RecommendationRules.checkIssue(standing(90, 10, 3, 5000), cfg, 1000).isPresent());
		assertTrue(RecommendationRules.checkIssue(standing(90, 10, 3, 5000), cfg, 5000).isEmpty());
	}

	@Test
	void unknownIssuerGivesSmallerBonus() {
		int known = RecommendationRules.trustBonus(cfg, 90, true);
		int unknown = RecommendationRules.trustBonus(cfg, 90, false);
		assertEquals(cfg.letterTrustBonus, known);
		assertTrue(unknown < known);
	}

	@Test
	void letterNeverGrantsFriendshipOrMore() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = standing(30, 0, 0, 0);
		int gained = m.grantTrust(st, 20, 1000);
		assertEquals(ReputationManager.letterScoreCap(), st.score);
		assertEquals(ReputationManager.letterScoreCap() - 30, gained);
		assertEquals(0, m.grantTrust(st, 20, 1000));
	}

	@Test
	void letterBonusIsAppliedFromZero() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		assertEquals(20, m.grantTrust(st, 20, 1000));
		assertEquals(20, st.score);
	}
}
