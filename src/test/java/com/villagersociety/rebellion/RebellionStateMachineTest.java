package com.villagersociety.rebellion;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.config.ModConfig;
import com.villagersociety.rebellion.RebellionPhase;
import com.villagersociety.rebellion.RebellionStateMachine.Context;
import org.junit.jupiter.api.Test;

class RebellionStateMachineTest {
	private final ModConfig cfg = ModConfig.get();

	private Context ctx(RebellionPhase phase, long now, long phaseStart, int score, boolean valid,
						boolean inArea, double dist, int participating) {
		return new Context(phase, now, phaseStart, phaseStart, 0, score, valid, inArea, dist, participating);
	}

	private RebellionPhase step(Context c) {
		return RebellionStateMachine.next(c);
	}

	@Test
	void escalatesThroughEveryPhaseAsReputationFalls() {
		assertEquals(RebellionPhase.NONE, step(ctx(RebellionPhase.NONE, 0, 0, -10, true, true, 5, 0)));
		assertEquals(RebellionPhase.SUSPICION, step(ctx(RebellionPhase.NONE, 0, 0, cfg.suspicionThreshold, true, true, 5, 0)));
		assertEquals(RebellionPhase.SUSPICION,
			step(ctx(RebellionPhase.SUSPICION, RebellionStateMachine.SUSPICION_MIN - 1, 0, cfg.warningThreshold, true, true, 5, 0)));
		assertEquals(RebellionPhase.WARNING,
			step(ctx(RebellionPhase.SUSPICION, RebellionStateMachine.SUSPICION_MIN, 0, cfg.warningThreshold, true, true, 5, 0)));
		assertEquals(RebellionPhase.MOBILIZATION,
			step(ctx(RebellionPhase.WARNING, RebellionStateMachine.WARNING_MIN, 0, cfg.mobilizationThreshold, true, true, 5, 0)));
		assertEquals(RebellionPhase.REBELLION,
			step(ctx(RebellionPhase.MOBILIZATION, RebellionStateMachine.MOBILIZATION_MIN, 0, cfg.rebellionThreshold, true, true, 5, 4)));
	}

	@Test
	void aKnownEnemyIsMobilizedAgainstAsSoonAsItIsInTheVillage() {
		assertEquals(RebellionPhase.MOBILIZATION,
			step(ctx(RebellionPhase.NONE, 0, 0, cfg.instantHostileScore, true, true, 5, 0)));
		assertEquals(RebellionPhase.NONE,
			step(ctx(RebellionPhase.NONE, 0, 0, cfg.instantHostileScore, true, false, 90, 0)));
		// a merely unpopular player still goes through suspicion first
		assertEquals(RebellionPhase.SUSPICION,
			step(ctx(RebellionPhase.NONE, 0, 0, cfg.suspicionThreshold, true, true, 5, 0)));
	}

	@Test
	void tooFewParticipantsAbortsMobilization() {
		assertEquals(RebellionPhase.RECOVERY,
			step(ctx(RebellionPhase.MOBILIZATION, RebellionStateMachine.MOBILIZATION_MIN, 0, cfg.rebellionThreshold, true, true, 5,
				cfg.rebellionMinParticipants - 1)));
	}

	@Test
	void mobilizationDoesNotLastForeverWhenScoreStaysAboveRebellionThreshold() {
		assertEquals(RebellionPhase.RECOVERY,
			step(ctx(RebellionPhase.MOBILIZATION, RebellionStateMachine.MOBILIZATION_MAX, 0, cfg.mobilizationThreshold, true, true, 5, 5)));
	}

	@Test
	void fleeingTheAreaStartsPursuitAndPursuitHasHardLimits() {
		assertEquals(RebellionPhase.PURSUIT, step(ctx(RebellionPhase.REBELLION, 100, 0, -90, true, false, 80, 4)));
		assertEquals(RebellionPhase.REBELLION, step(ctx(RebellionPhase.PURSUIT, 100, 0, -90, true, true, 10, 4)));
		assertEquals(RebellionPhase.PURSUIT, step(ctx(RebellionPhase.PURSUIT, 100, 0, -90, true, false, 40, 4)));
		assertEquals(RebellionPhase.RECOVERY,
			step(ctx(RebellionPhase.PURSUIT, 100, 0, -90, true, false, cfg.pursuitMaxDistance + 1, 4)));
		assertEquals(RebellionPhase.RECOVERY,
			step(ctx(RebellionPhase.PURSUIT, cfg.pursuitMaxDurationTicks + 1, 0, -90, true, false, 40, 4)));
	}

	@Test
	void everyActivePhaseEndsWhenPlayerIsGone() {
		for (RebellionPhase p : new RebellionPhase[] {RebellionPhase.SUSPICION, RebellionPhase.WARNING,
			RebellionPhase.MOBILIZATION, RebellionPhase.REBELLION, RebellionPhase.PURSUIT}) {
			assertEquals(RebellionPhase.RECOVERY, step(ctx(p, 10, 0, -90, false, false, Double.MAX_VALUE, 4)), p.name());
		}
		assertEquals(RebellionPhase.NONE, step(ctx(RebellionPhase.NONE, 10, 0, -90, false, false, Double.MAX_VALUE, 0)));
	}

	@Test
	void rebellionNeverRunsForeverAndRecoveryEnds() {
		long limit = (long) cfg.pursuitMaxDurationTicks * 4;
		assertEquals(RebellionPhase.RECOVERY, step(ctx(RebellionPhase.REBELLION, limit + 1, 0, -90, true, true, 5, 4)));
		assertEquals(RebellionPhase.RECOVERY, step(ctx(RebellionPhase.RECOVERY, 10, 0, -90, true, true, 5, 4)));
		assertEquals(RebellionPhase.NONE,
			step(ctx(RebellionPhase.RECOVERY, RebellionStateMachine.RECOVERY_TICKS, 0, -90, true, true, 5, 4)));
	}

	@Test
	void payingTheVillageOffEndsTheRebellion() {
		int paid = cfg.mobilizationThreshold + 1;
		assertEquals(RebellionPhase.RECOVERY, step(ctx(RebellionPhase.REBELLION, 100, 0, paid, true, true, 5, 4)));
		assertEquals(RebellionPhase.RECOVERY, step(ctx(RebellionPhase.PURSUIT, 100, 0, paid, true, false, 40, 4)));
	}

	@Test
	void recoveringReputationDeEscalates() {
		assertEquals(RebellionPhase.NONE,
			step(ctx(RebellionPhase.SUSPICION, 100, 0, cfg.suspicionThreshold + RebellionStateMachine.HYSTERESIS + 1, true, true, 5, 0)));
		assertEquals(RebellionPhase.SUSPICION,
			step(ctx(RebellionPhase.WARNING, 100, 0, cfg.warningThreshold + RebellionStateMachine.HYSTERESIS + 1, true, true, 5, 0)));
	}
}
