package com.villagersociety.workers;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Puts a stack into a chest/barrel: first merges into matching stacks, then uses empty slots. */
final class ContainerInsert {
	private ContainerInsert() {
	}

	static boolean hasRoom(Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Returns what did not fit (empty if everything was inserted). The caller calls setChanged(). */
	static ItemStack insert(Container container, ItemStack stack) {
		ItemStack remaining = stack.copy();
		for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
			ItemStack slot = container.getItem(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, remaining)
				&& slot.getCount() < slot.getMaxStackSize() && container.canPlaceItem(i, remaining)) {
				int move = Math.min(remaining.getCount(), slot.getMaxStackSize() - slot.getCount());
				slot.grow(move);
				remaining.shrink(move);
			}
		}
		for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
			if (container.getItem(i).isEmpty() && container.canPlaceItem(i, remaining)) {
				int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
				container.setItem(i, remaining.split(move));
			}
		}
		return remaining;
	}
}
