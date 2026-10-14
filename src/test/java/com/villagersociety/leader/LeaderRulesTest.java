package com.villagersociety.leader;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.config.ModConfig;
import com.villagersociety.workers.WorkerJob;
import org.junit.jupiter.api.Test;

class LeaderRulesTest {
	private final ModConfig cfg = new ModConfig();

	@Test
	void onlyHighReputationCanLeadAndLowReputationStepsDown() {
		assertFalse(LeaderRules.canBecomeLeader(cfg.leaderMinReputation - 1, cfg));
		assertTrue(LeaderRules.canBecomeLeader(cfg.leaderMinReputation, cfg));
		assertFalse(LeaderRules.mustStepDown(cfg.leaderKeepReputation, cfg));
		assertTrue(LeaderRules.mustStepDown(cfg.leaderKeepReputation - 1, cfg));
	}

	@Test
	void keepThresholdNeverExceedsTheMinimumToBecomeLeader() {
		ModConfig c = new ModConfig();
		c.leaderMinReputation = 50;
		c.leaderKeepReputation = 90;
		c.validate();
		assertTrue(c.leaderKeepReputation <= c.leaderMinReputation);
	}

	@Test
	void staffModesCycleThroughEveryJobAndImmigration() {
		StaffMode mode = StaffMode.MINE;
		assertEquals(WorkerJob.MINE, mode.job());
		mode = mode.next();
		assertEquals(WorkerJob.CHOP, mode.job());
		mode = mode.next();
		assertEquals(WorkerJob.FARM, mode.job());
		mode = mode.next();
		assertEquals(StaffMode.MIGRATE, mode);
		assertNull(mode.job());
		assertEquals(StaffMode.MINE, mode.next());
	}

	@Test
	void unknownJobIdsFallBackToMining() {
		assertEquals(WorkerJob.CHOP, WorkerJob.fromId("chop"));
		assertEquals(WorkerJob.MINE, WorkerJob.fromId("something_old"));
	}
}
