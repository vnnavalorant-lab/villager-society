package com.villagersociety.recommendation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads/writes the letter's id on the item (custom_data). The item is only a ticket: the
 * authoritative record lives in SocietyState and is always checked server-side.
 *
 * UNVERIFIED API POINTS (1.21.11): CompoundTag#getStringOr, CustomData#copyTag, ItemLore(List).
 */
public final class LetterStack {
	private static final String KEY = "recommendation_id";

	private LetterStack() {
	}

	public static boolean isLetter(ItemStack stack) {
		return ModItems.RECOMMENDATION_LETTER != null && stack.is(ModItems.RECOMMENDATION_LETTER);
	}

	public static ItemStack create(UUID recommendationId, String villageShortId, String playerName) {
		ItemStack stack = new ItemStack(ModItems.RECOMMENDATION_LETTER);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, recommendationId.toString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.literal("Vila " + villageShortId),
			Component.literal("Para: " + playerName))));
		return stack;
	}

	public static Optional<UUID> read(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		String raw = data.copyTag().getStringOr(KEY, "");
		try {
			return raw.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(raw));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
