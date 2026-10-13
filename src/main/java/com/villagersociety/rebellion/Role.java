package com.villagersociety.rebellion;

/** Temporary roles for rebellion participants. */
public enum Role {
	DEFENDER,   // front line: goes straight at the player
	FLANKER,    // approaches from a different angle
	PROTECTOR,  // stays between the player and vulnerable villagers (babies)
	ALERTER,    // runs to the bell / meeting point and raises the alarm
	FLEEING;    // babies, distant workers and extra villagers: get away from the player

	public boolean isParticipating() {
		return this != FLEEING;
	}
}
