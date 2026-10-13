package com.villagersociety.rebellion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Transient state of one rebellion of one village against one player. Never persisted. */
final class Rebellion {
	final ServerLevel level;
	final UUID village;
	final UUID player;
	final long startedAt;

	RebellionPhase phase = RebellionPhase.NONE;
	long phaseStart;
	long pursuitStart;
	long lastRefresh = Long.MIN_VALUE / 2;
	boolean belled;
	Vec3 meeting;

	final Map<UUID, Role> participants = new HashMap<>();
	final Map<UUID, Long> nextAttack = new HashMap<>();
	final Map<UUID, Vec3> lastTarget = new HashMap<>();
	final Map<UUID, Long> lastTargetTick = new HashMap<>();

	Rebellion(ServerLevel level, UUID village, UUID player, long now) {
		this.level = level;
		this.village = village;
		this.player = player;
		this.startedAt = now;
		this.phaseStart = now;
		this.pursuitStart = now;
	}

	int participating() {
		return RoleAssigner.participating(participants);
	}
}
