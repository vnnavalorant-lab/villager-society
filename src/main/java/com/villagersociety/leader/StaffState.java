package com.villagersociety.leader;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/** Transient per-player state of the Leader's Staff (mode, work mark, villager selected for immigration). */
final class StaffState {
	StaffMode mode = StaffMode.MINE;
	BlockPos mark;
	UUID selectedVillager;
	UUID selectedVillage;
}
