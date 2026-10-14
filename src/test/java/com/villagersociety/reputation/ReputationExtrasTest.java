package com.villagersociety.reputation;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.village.Personality;
import java.util.List;
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
	void emeraldGiftsAreRateLimitedToOnePerHalfSecond() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		assertTrue(m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.DONATE_ITEM, false, 1000).counted());
		assertFalse(m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.DONATE_ITEM, false, 1005).counted());
		assertTrue(m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.DONATE_ITEM, false, 1010).counted());
	}

	@Test
	void emeraldsRestoreReputationEvenUnderGraveLockAndAfterTheDailyCap() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding(-80, 0, 0, 1, 1000L, 1000L + 999_999L, List.of());
		int per = ModConfig.get().repDonateItem;
		var first = m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.DONATE_ITEM, false, 1000);
		assertEquals(per, first.delta()); // not halved by the grave-offense lock
		// 40 gifts in a row (spaced past the rate limit) are never stopped by the daily cap
		for (int i = 1; i <= 40; i++) {
			m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.DONATE_ITEM, false, 1000L + i * 10L);
		}
		assertEquals(Math.min(100, -80 + per * 41), st.score);
	}
}
