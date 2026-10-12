package com.villagersociety.reputation;

import com.villagersociety.config.ModConfig;

/**
 * Every action that can change a player's standing.
 * dedupeTicks: the same action by the same player in the same village counts at most once
 * per window (prevents counting one event many times, e.g. breaking 50 crops in a row,
 * and limits reputation farming).
 */
public enum ReputationAction {
	TRADE("trade", true, false, 0, false, false),
	REPLANT_CROP("replant_crop", true, false, 200, false, false),
	DONATE_ITEM("donate_item", true, false, 600, true, true),
	DEFEND_VILLAGER("defend_villager", true, false, 100, true, true),
	KILL_MONSTER("kill_monster", true, false, 100, false, false),
	REPAIR_DAMAGE("repair_damage", true, false, 40, true, true),
	BREAK_CROP("break_crop", false, false, 100, false, true),
	BREAK_BED_OR_WORKSTATION("break_bed_or_workstation", false, false, 20, false, true),
	STEAL_CONTAINER("steal_container", false, false, 60, false, true),
	ATTACK_VILLAGER("attack_villager", false, true, 20, false, true),
	KILL_VILLAGER("kill_villager", false, true, 0, false, true);

	private final String id;
	private final boolean positive;
	private final boolean grave;
	private final int dedupeTicks;
	private final boolean relevantGoodDeed;
	private final boolean recorded;

	ReputationAction(String id, boolean positive, boolean grave, int dedupeTicks,
					 boolean relevantGoodDeed, boolean recorded) {
		this.id = id;
		this.positive = positive;
		this.grave = grave;
		this.dedupeTicks = dedupeTicks;
		this.relevantGoodDeed = relevantGoodDeed;
		this.recorded = recorded;
	}

	public String id() {
		return id;
	}

	public boolean isPositive() {
		return positive;
	}

	/** Grave offenses lock passive recovery for a long time. */
	public boolean isGrave() {
		return grave;
	}

	public int dedupeTicks() {
		return dedupeTicks;
	}

	/** Counts toward "relevant good deeds" needed for the recommendation letter. */
	public boolean isRelevantGoodDeed() {
		return relevantGoodDeed;
	}

	/** Whether the action is stored in the player's history (trades are not, to avoid flooding it). */
	public boolean isRecorded() {
		return recorded;
	}

	public int base(ModConfig c) {
		return switch (this) {
			case TRADE -> c.repTradeCompleted;
			case REPLANT_CROP -> c.repReplantCrop;
			case DONATE_ITEM -> c.repDonateItem;
			case DEFEND_VILLAGER -> c.repDefendVillager;
			case KILL_MONSTER -> c.repKillMonsterNearVillage;
			case REPAIR_DAMAGE -> c.repRepairDamage;
			case BREAK_CROP -> c.repBreakCrop;
			case BREAK_BED_OR_WORKSTATION -> c.repBreakBedOrWorkstation;
			case STEAL_CONTAINER -> c.repStealContainer;
			case ATTACK_VILLAGER -> c.repAttackVillager;
			case KILL_VILLAGER -> c.repKillVillager;
		};
	}
}
