package com.villagersociety.economy;

import com.villagersociety.config.ModConfig;

/** When merchants refuse to trade. Refusal only blocks opening the trade screen; offers are untouched. */
public final class TradeRules {
	private TradeRules() {
	}

	/**
	 * @param rebellionActiveAgainstThisPlayer the village is mobilized/fighting against THIS player
	 *                                         (other players are never affected by it)
	 */
	public static boolean shouldRefuse(boolean rebellionActiveAgainstThisPlayer, int playerScore, ModConfig cfg) {
		return cfg.refuseTradeDuringRebellion
			&& (rebellionActiveAgainstThisPlayer || playerScore <= cfg.rebellionThreshold);
	}
}
