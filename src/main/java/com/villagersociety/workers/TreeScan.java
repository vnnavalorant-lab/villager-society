package com.villagersociety.workers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds the logs of one tree and tells natural trees from player-built wooden structures. */
final class TreeScan {
	private TreeScan() {
	}

	/** Connected logs (including diagonals) starting at {@code start}, at most {@code max}. Loaded chunks only. */
	static List<BlockPos> collect(ServerLevel level, BlockPos start, int max) {
		List<BlockPos> result = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty() && result.size() < max) {
			BlockPos current = queue.poll();
			result.add(current);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = current.offset(dx, dy, dz);
						if (seen.add(next) && level.hasChunkAt(next) && level.getBlockState(next).is(BlockTags.LOGS)) {
							queue.add(next);
						}
					}
				}
			}
		}
		return result;
	}

	/** A natural tree has at least 3 non-persistent (naturally generated/grown) leaves touching its logs. */
	static boolean isNaturalTree(ServerLevel level, BlockPos start) {
		int leaves = 0;
		for (BlockPos log : collect(level, start, 64)) {
			for (Direction direction : Direction.values()) {
				BlockPos next = log.relative(direction);
				if (!level.hasChunkAt(next)) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT)
					&& !state.getValue(LeavesBlock.PERSISTENT) && ++leaves >= 3) {
					return true;
				}
			}
		}
		return false;
	}
}
