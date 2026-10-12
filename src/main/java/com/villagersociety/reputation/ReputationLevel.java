package com.villagersociety.reputation;

/** Descriptive standing levels, derived from a score in [-100, 100]. Declared from highest to lowest. */
public enum ReputationLevel {
	HERO(85, "Herói da comunidade"),
	BENEFACTOR(65, "Benfeitor"),
	FRIEND(40, "Amigo da vila"),
	ACQUAINTANCE(15, "Conhecido"),
	UNKNOWN(-19, "Desconhecido"),
	HOSTILE(-59, "Hostil"),
	ENEMY(-100, "Inimigo da comunidade");

	private final int minScore;
	private final String displayName;

	ReputationLevel(int minScore, String displayName) {
		this.minScore = minScore;
		this.displayName = displayName;
	}

	public int minScore() {
		return minScore;
	}

	public String displayName() {
		return displayName;
	}

	public static ReputationLevel fromScore(int score) {
		for (ReputationLevel level : values()) {
			if (score >= level.minScore) {
				return level;
			}
		}
		return ENEMY;
	}
}
