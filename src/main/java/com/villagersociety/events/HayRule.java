package com.villagersociety.events;

/** Pure rule: how many hay bales must be broken before it counts as a crime against the village. */
public final class HayRule {
	private HayRule() {
	}

	/** Bales that must be broken (at least 1): e.g. 10 bales and fraction 0.5 -> 5. */
	public static int threshold(int total, double fraction) {
		return Math.max(1, (int) Math.ceil(total * fraction));
	}

	public static boolean punishable(int total, int broken, double fraction) {
		return broken >= threshold(total, fraction);
	}
}
