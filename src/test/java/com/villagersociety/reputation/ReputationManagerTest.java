package com.villagersociety.reputation;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.village.Personality;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReputationManagerTest {
	private final UUID village = UUID.randomUUID();
	private final UUID player = UUID.randomUUID();

	@Test
	void breakingCropsLowersReputation() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		var r = m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.BREAK_CROP, false, 1000);
		assertTrue(r.counted());
		assertTrue(r.delta() < 0);
		assertEquals(st.score, r.newScore());
	}

	@Test
	void sameActionInsideWindowIsCountedOnce() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.BREAK_CROP, false, 1000);
		int after = st.score;
		var second = m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.BREAK_CROP, false, 1010);
		assertFalse(second.counted());
		assertEquals(after, st.score);
	}

	@Test
	void accidentIsMilderThanDeliberate() {
		ReputationManager m = new ReputationManager();
		PlayerStanding a = new PlayerStanding();
		PlayerStanding b = new PlayerStanding();
		var deliberate = m.apply(village, player, a, Personality.COMMERCIAL, ReputationAction.BREAK_BED_OR_WORKSTATION, false, 1000);
		var accident = m.apply(village, UUID.randomUUID(), b, Personality.COMMERCIAL, ReputationAction.BREAK_BED_OR_WORKSTATION, true, 1000);
		assertTrue(accident.delta() > deliberate.delta());
		assertEquals(0, b.offenses);
	}

	@Test
	void agriculturalVillagePunishesCropsHarder() {
		ReputationManager m = new ReputationManager();
		PlayerStanding farm = new PlayerStanding();
		PlayerStanding other = new PlayerStanding();
		var f = m.apply(village, player, farm, Personality.AGRICULTURAL, ReputationAction.BREAK_CROP, false, 1000);
		var o = m.apply(village, UUID.randomUUID(), other, Personality.COMMERCIAL, ReputationAction.BREAK_CROP, false, 1000);
		assertTrue(f.delta() < o.delta());
	}

	@Test
	void scoreStaysWithinBounds() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding(-95, 0, 0, 0, 0, 0, List.of());
		m.apply(village, player, st, Personality.PROTECTIVE, ReputationAction.KILL_VILLAGER, false, 5000);
		assertEquals(-100, st.score);
	}

	@Test
	void graveOffenseLocksRecoveryAndSetsLock() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.KILL_VILLAGER, false, 1000);
		int score = st.score;
		assertTrue(st.graveLockUntil > 1000);
		m.recover(st, st.graveLockUntil - 1);
		assertEquals(score, st.score);
	}

	@Test
	void minorOffensesRecoverOverTimeButNeverAboveZero() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding(-3, 0, 0, 1, 1000L, 0L, List.of());
		m.recover(st, 1000L + 24000L * 2);
		assertEquals(-1, st.score);
		m.recover(st, 1000L + 24000L * 50);
		assertEquals(0, st.score);
	}

	@Test
	void tradesAndGoodDeedsAreCounted() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.TRADE, false, 100);
		m.apply(village, player, st, Personality.COMMERCIAL, ReputationAction.REPAIR_DAMAGE, false, 100);
		assertEquals(1, st.tradesCompleted);
		assertEquals(1, st.goodDeeds);
		assertTrue(st.score > 0);
	}

	@Test
	void levelsMatchScores() {
		assertEquals(ReputationLevel.ENEMY, ReputationLevel.fromScore(-100));
		assertEquals(ReputationLevel.HOSTILE, ReputationLevel.fromScore(-20));
		assertEquals(ReputationLevel.UNKNOWN, ReputationLevel.fromScore(0));
		assertEquals(ReputationLevel.FRIEND, ReputationLevel.fromScore(50));
		assertEquals(ReputationLevel.HERO, ReputationLevel.fromScore(100));
	}
}
