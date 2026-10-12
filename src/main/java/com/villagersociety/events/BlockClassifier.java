package com.villagersociety.events;

import com.villagersociety.reputation.ReputationAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Decides whether a broken block is an offense.
 *  - Mature crops are harvested, not destroyed: no offense. Immature crops = BREAK_CROP.
 *  - Any block that is a village POI (beds, job sites, bell) = BREAK_BED_OR_WORKSTATION.
 *
 * UNVERIFIED API POINTS (1.21.11): PoiTypes.forState(BlockState), CropBlock#isMaxAge(BlockState).
 */
public final class BlockClassifier {
	private BlockClassifier() {
	}

	public static Optional<ReputationAction> classifyBreak(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state) ? Optional.empty() : Optional.of(ReputationAction.BREAK_CROP);
		}
		if (isVillagePoi(state)) {
			return Optional.of(ReputationAction.BREAK_BED_OR_WORKSTATION);
		}
		return Optional.empty();
	}

	public static boolean isVillagePoi(BlockState state) {
		return PoiTypes.forState(state).filter(holder -> holder.is(PoiTypeTags.VILLAGE)).isPresent();
	}

	/** Community container = a village POI block that holds items (e.g. fisherman's barrel, furnaces). */
	public static boolean isCommunityContainer(ServerLevel level, BlockPos pos, BlockState state) {
		return isVillagePoi(state) && level.getBlockEntity(pos) instanceof Container;
	}
}
