package com.villagersociety.leader;

import com.villagersociety.config.ModConfig;

/** Pure rules for the player-leader of a village. */
public final class LeaderRules {
	private LeaderRules() {
	}

	public static boolean canBecomeLeader(int score, ModConfig cfg) {
		return score >= cfg.leaderMinReputation;
	}

	public static boolean mustStepDown(int score, ModConfig cfg) {
		return score < cfg.leaderKeepReputation;
	}
}
