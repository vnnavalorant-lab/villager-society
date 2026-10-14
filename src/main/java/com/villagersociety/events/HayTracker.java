package com.villagersociety.events;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Counts the village's hay bales the first time one is broken (loaded chunks only, one pass) and then only
 * keeps a counter, which is persisted with the village. Breaking bales is a crime only once the broken
 * count reaches {@code hayFractionForCrime} (default one half) of the total.
 */
public final class HayTracker {
	private HayTracker() {
	}

	/** Records one broken bale; returns true when this break counts as a crime. */
	public static boolean recordBreak(ServerLevel level, VillageData village, SocietyState state) {
		if (village.hayTotal <= 0) {
			// The bale was already removed when the event fired, hence the +1.
			village.hayTotal = countHay(level, village) + 1;
			village.hayBroken = 0;
		}
		village.hayBroken++;
		state.setDirty();
		return HayRule.punishable(village.hayTotal, village.hayBroken, ModConfig.get().hayFractionForCrime);
	}

	static int countHay(ServerLevel level, VillageData village) {
		int radius = Math.min(village.radius, 48);
		int cx = village.center.getX();
		int cy = village.center.getY();
		int cz = village.center.getZ();
		int count = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				p.set(x, cy, z);
				if (!level.hasChunkAt(p)) {
					continue; // never force-load chunks just to count hay
				}
				for (int y = cy - 12; y <= cy + 12; y++) {
					p.setY(y);
					if (level.getBlockState(p).is(Blocks.HAY_BLOCK)) {
						count++;
					}
				}
			}
		}
		return count;
	}
}
