package com.villagersociety.economy;

import com.villagersociety.config.ModConfig;
import com.villagersociety.reputation.ReputationLevel;

/**
 * Discount rules. The discount is always computed from the vanilla BASE price and added to
 * the offer's special price diff, which vanilla resets on every trade screen opening, so
 * discounts can never stack on prices the mod already reduced.
 */
public final class DiscountCalculator {
	private DiscountCalculator() {
	}

	/** Fraction (0..maxDiscount) for a reputation score. */
	public static double fraction(int score, ModConfig cfg) {
		double f = switch (ReputationLevel.fromScore(score)) {
			case FRIEND -> cfg.friendDiscount;
			case BENEFACTOR -> cfg.benefactorDiscount;
			case HERO -> cfg.heroDiscount;
			default -> 0.0;
		};
		return Math.min(f, cfg.maxDiscount);
	}

	/** Items to subtract from a base price: rounded, and never makes an offer free. */
	public static int discountAmount(int baseCount, double fraction) {
		if (baseCount <= 1 || fraction <= 0) {
			return 0;
		}
		int d = (int) Math.floor(baseCount * fraction + 0.5);
		return Math.max(0, Math.min(d, baseCount - 1));
	}
}
