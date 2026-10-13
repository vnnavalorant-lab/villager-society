package com.villagersociety.reputation;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostilityTest {
	private final UUID village = UUID.randomUUID();
	private final UUID player = UUID.randomUUID();

	@Test
	void killingAWitnessedVillagerMakesYouWanted() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		assertTrue(m.triggersHostility(village, player, ReputationAction.KILL_VILLAGER, true, 1000));
		m.declareHostile(st, 1000);
		assertTrue(ReputationManager.isWanted(st));
		assertTrue(st.graveLockUntil > 1000);
	}

	@Test
	void unwitnessedCrimesDoNotInstantlyTurnVillagersHostile() {
		ReputationManager m = new ReputationManager();
		assertFalse(m.triggersHostility(village, player, ReputationAction.KILL_VILLAGER, false, 1000));
		assertFalse(m.triggersHostility(village, player, ReputationAction.BREAK_HAY_BALE, false, 1000));
	}

	@Test
	void singleHayBaleTriggersByDefault() {
		ReputationManager m = new ReputationManager();
		assertTrue(m.triggersHostility(village, player, ReputationAction.BREAK_HAY_BALE, true, 1000));
	}

	@Test
	void hayThresholdCanBeRaisedAndCountResetsEachDay() {
		ModConfig cfg = ModConfig.get();
		int old = cfg.hayBalesForHostility;
		try {
			cfg.hayBalesForHostility = 3;
			ReputationManager m = new ReputationManager();
			assertFalse(m.triggersHostility(village, player, ReputationAction.BREAK_HAY_BALE, true, 1000));
			assertFalse(m.triggersHostility(village, player, ReputationAction.BREAK_HAY_BALE, true, 1100));
			assertTrue(m.triggersHostility(village, player, ReputationAction.BREAK_HAY_BALE, true, 1200));
			assertFalse(m.triggersHostility(village, player, ReputationAction.BREAK_HAY_BALE, true, 1200 + 24_000));
		} finally {
			cfg.hayBalesForHostility = old;
		}
	}

	@Test
	void hostilityNeverRaisesAnAlreadyLowerScoreAndConfigStaysConsistent() {
		ReputationManager m = new ReputationManager();
		PlayerStanding st = new PlayerStanding();
		st.setScore(-95);
		m.declareHostile(st, 1000);
		assertEquals(-95, st.score);

		ModConfig c = new ModConfig();
		c.instantHostileScore = -10;   // would not be hostile enough
		c.validate();
		assertTrue(c.instantHostileScore <= c.rebellionThreshold);
	}
}
