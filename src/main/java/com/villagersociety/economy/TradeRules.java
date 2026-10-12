package com.villagersociety.economy;

import com.villagersociety.config.ModConfig;

/** When merchants refuse to trade. Refusal only blocks opening the trade screen; offers are untouched. */
public final class TradeRules {
	private TradeRules() {
	}

	public static boolean isRebellionActive(String rebellionState) {
		return "MOBILIZATION".equals(rebellionState) || "REBELLION".equals(rebellionState)
			|| "PURSUIT".equals(rebellionState);
	}

	public static boolean shouldRefuse(String villageRebellionState, int playerScore, ModConfig cfg) {
		return cfg.refuseTradeDuringRebellion
			&& (isRebellionActive(villageRebellionState) || playerScore <= cfg.rebellionThreshold);
	}
}
