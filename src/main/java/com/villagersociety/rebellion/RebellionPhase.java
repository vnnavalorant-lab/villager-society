package com.villagersociety.rebellion;

/** The six phases of the spec (plus NONE). */
public enum RebellionPhase {
	NONE(""), SUSPICION("Suspeita"), WARNING("Advertência"), MOBILIZATION("Mobilização"),
	REBELLION("Rebelião"), PURSUIT("Perseguição"), RECOVERY("Recuperação");

	private final String displayName;

	RebellionPhase(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	/** Villagers are organizing or fighting against the player (merchants refuse to trade). */
	public boolean isActiveAgainstPlayer() {
		return this == MOBILIZATION || this == REBELLION || this == PURSUIT;
	}
}
