package com.villagersociety.village;

import java.util.Random;
import java.util.UUID;

/**
 * Village personalities. One enum + multipliers, so every type shares the same code path.
 * The personality is stored by id in the village record and never re-rolled.
 */
public enum Personality {
	//                  id              gain  loss  crop  trade defend gossip
	CAUTIOUS("cautious",         0.7, 1.0, 1.0, 1.0, 1.0, 1.0),
	AGRICULTURAL("agricultural", 1.0, 1.0, 2.0, 1.0, 1.0, 1.0),
	COMMERCIAL("commercial",     1.0, 1.0, 1.0, 1.5, 1.0, 1.0),
	PROTECTIVE("protective",     1.0, 1.2, 1.0, 1.0, 1.5, 1.2),
	ISOLATED("isolated",         0.6, 1.0, 1.0, 1.0, 1.0, 0.5);

	private final String id;
	private final double gain;
	private final double loss;
	private final double crop;
	private final double trade;
	private final double defend;
	private final double gossip;

	Personality(String id, double gain, double loss, double crop, double trade, double defend, double gossip) {
		this.id = id;
		this.gain = gain;
		this.loss = loss;
		this.crop = crop;
		this.trade = trade;
		this.defend = defend;
		this.gossip = gossip;
	}

	public String id() {
		return id;
	}

	/** Multiplier for any positive reputation gain (low = slow to trust). */
	public double gainMultiplier() {
		return gain;
	}

	/** Multiplier for any reputation loss. */
	public double lossMultiplier() {
		return loss;
	}

	/** Extra multiplier for crop damage. */
	public double cropLossMultiplier() {
		return crop;
	}

	public double tradeGainMultiplier() {
		return trade;
	}

	public double defendGainMultiplier() {
		return defend;
	}

	/** How readily the village shares reports (used by the gossip system). */
	public double gossipMultiplier() {
		return gossip;
	}

	/** Unknown ids fall back to CAUTIOUS (the safest behavior) instead of failing. */
	public static Personality fromId(String id) {
		for (Personality p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return CAUTIOUS;
	}

	/** Deterministic pick from the village UUID, so the same id always yields the same result. */
	public static Personality randomFor(UUID villageId) {
		long seed = villageId.getMostSignificantBits() ^ villageId.getLeastSignificantBits();
		return values()[new Random(seed).nextInt(values().length)];
	}
}
