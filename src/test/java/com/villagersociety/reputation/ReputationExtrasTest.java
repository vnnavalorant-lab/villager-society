package com.villagersociety.reputation;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.village.Personality;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReputationExtrasTest {
	private final UUID village = UUID.randomUUID();
	private final UUID player = UUID.randomUUID();

	@Test
	void witnessedOffenseHurtsMoreThanUnwitnessed() {
		ReputationManager m = new ReputationManager();
		PlayerStanding a = new PlayerStanding();
		PlayerStanding b = new PlayerStanding();
		var unseen = m.apply(village, player, a, Personality.COMMERCIAL, ReputationAction.STEAL_CONTAINER, false, 1000, 0.3);
		var seen = m.apply(village, UUID.randomUUID(), b, Personality.COMMERCIAL, ReputationAction.STEAL_CONTAINER, false, 1000, 1.3);
		assertTrue(seen.delta() < unseen.delta());
	}

	@Test
	void positiveGainsAreCappedPerDay() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		int cap = ModConfig.get().positiveGainCapPerDay;
		for (int i = 0; i < 200; i++) {
			m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.DEFEND_VILLAGER, false, 1000L + i * 1000L / 200);
			m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.TRADE, false, 1000L);
		}
		assertTrue(st.score <= cap, "score " + st.score + " exceeded cap " + cap);
		// A new in-game day opens a new allowance.
		var next = m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.TRADE, false, 1000L + 24_000L);
		assertTrue(next.counted());
	}

	@Test
	void donationsAreRateLimited() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		assertTrue(m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.DONATE_ITEM, false, 1000).counted());
		assertFalse(m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.DONATE_ITEM, false, 1100).counted());
		assertTrue(m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.DONATE_ITEM, false, 1700).counted());
	}
}
