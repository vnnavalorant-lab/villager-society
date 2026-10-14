package com.villagersociety.workers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What each job looks for and does. Blocks are broken with a VIRTUAL tool (never a real one, nothing is
 * consumed) and their drops go into the villager's inventory, marked so only they are taken to the chest.
 * Only loaded chunks are ever read, so a worker can never force chunks to load.
 *
 * UNVERIFIED API POINTS (1.21.11): Block.getDrops(state, level, pos, be, entity, tool),
 * ServerLevel#destroyBlock(pos, drop, entity), BlockTags.BASE_STONE_OVERWORLD/LOGS, CropBlock#isMaxAge.
 */
final class WorkerTargets {
	private WorkerTargets() {
	}

	static boolean isValid(ServerLevel level, WorkerJob job, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return switch (job) {
			case MINE -> state.is(BlockTags.BASE_STONE_OVERWORLD) && exposed(level, pos);
			case CHOP -> state.is(BlockTags.LOGS);
			case FARM -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
		};
	}

	/** Only stone with at least one open side, so workers peel the surface instead of digging blindly. */
	private static boolean exposed(ServerLevel level, BlockPos pos) {
		for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
			BlockPos next = pos.relative(direction);
			if (level.hasChunkAt(next) && level.getBlockState(next).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** Nearest valid target inside the work area, skipping blacklisted positions. */
	static BlockPos find(ServerLevel level, WorkerJob job, BlockPos center, int radius, BlockPos from,
						 Set<BlockPos> ignored) {
		int up = job == WorkerJob.CHOP ? 10 : 4;
		int down = job == WorkerJob.CHOP ? 2 : 4;
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				p.set(x, center.getY(), z);
				if (!level.hasChunkAt(p)) {
					continue;
				}
				for (int y = center.getY() - down; y <= center.getY() + up; y++) {
					p.setY(y);
					double distance = p.distSqr(from);
					if (distance >= bestDistance || !isValid(level, job, p)) {
						continue;
					}
					BlockPos candidate = p.immutable();
					if (ignored.contains(candidate)) {
						continue;
					}
					if (job == WorkerJob.CHOP && !TreeScan.isNaturalTree(level, candidate)) {
						continue;
					}
					best = candidate;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	static void perform(ServerLevel level, Mob mob, SimpleContainer inventory, WorkerJob job, BlockPos pos) {
		switch (job) {
			case MINE -> breakAndStore(level, mob, inventory, pos, new ItemStack(Items.IRON_PICKAXE));
			case CHOP -> {
				for (BlockPos log : TreeScan.collect(level, pos, 64)) {
					breakAndStore(level, mob, inventory, log, new ItemStack(Items.IRON_AXE));
				}
			}
			case FARM -> harvest(level, mob, inventory, pos);
		}
	}

	private static void breakAndStore(ServerLevel level, Mob mob, SimpleContainer inventory, BlockPos pos, ItemStack tool) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), mob, tool);
		level.destroyBlock(pos, false, mob);
		store(level, inventory, pos, drops);
	}

	/** Harvests a mature crop and replants it using one of its own drops (a seed or the crop item itself). */
	private static void harvest(ServerLevel level, Mob mob, SimpleContainer inventory, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
			return;
		}
		List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, mob, ItemStack.EMPTY));
		Item seed = crop.asItem();
		boolean replant = false;
		for (ItemStack drop : drops) {
			if (drop.is(seed) && drop.getCount() > 0) {
				drop.shrink(1);
				replant = true;
				break;
			}
		}
		level.destroyBlock(pos, false, mob);
		if (replant && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
			level.setBlockAndUpdate(pos, crop.defaultBlockState());
		}
		store(level, inventory, pos, drops);
	}

	private static void store(ServerLevel level, SimpleContainer inventory, BlockPos pos, List<ItemStack> drops) {
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			ItemStack stack = drop.copy();
			WorkTag.mark(stack);
			ItemStack rest = inventory.addItem(stack);
			if (!rest.isEmpty()) {
				WorkTag.unmark(rest);
				Block.popResource(level, pos, rest); // inventory full: do not lose the items
			}
		}
	}
}
