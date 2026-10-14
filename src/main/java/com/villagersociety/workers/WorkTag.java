package com.villagersociety.workers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Marks items a worker collected (custom_data) so only those are taken to the chest. The villager's own food
 * and seeds are never touched, so breeding and farming keep working. Marked and unmarked stacks never merge.
 */
final class WorkTag {
	private static final String KEY = "vs_work";

	private WorkTag() {
	}

	static ItemStack mark(ItemStack stack) {
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, "1");
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	static boolean isMarked(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && "1".equals(data.copyTag().getStringOr(KEY, ""));
	}

	static void unmark(ItemStack stack) {
		stack.remove(DataComponents.CUSTOM_DATA);
	}
}
