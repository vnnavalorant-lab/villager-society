package com.villagersociety.recommendation;

import com.villagersociety.VillagerSociety;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Item registration. 1.21.2+ requires the registry key in Item.Properties#setId.
 * Neither item is in a creative tab: a letter copy without a server record is worthless, and the staff
 * is only handed out when a player becomes the leader of a village.
 */
public final class ModItems {
	public static final ResourceKey<Item> RECOMMENDATION_LETTER_KEY = ResourceKey.create(Registries.ITEM,
		Identifier.fromNamespaceAndPath(VillagerSociety.MOD_ID, "recommendation_letter"));
	public static final ResourceKey<Item> LEADER_STAFF_KEY = ResourceKey.create(Registries.ITEM,
		Identifier.fromNamespaceAndPath(VillagerSociety.MOD_ID, "leader_staff"));

	public static Item RECOMMENDATION_LETTER;
	public static Item LEADER_STAFF;

	private ModItems() {
	}

	public static void register() {
		RECOMMENDATION_LETTER = Registry.register(BuiltInRegistries.ITEM, RECOMMENDATION_LETTER_KEY,
			new Item(new Item.Properties().setId(RECOMMENDATION_LETTER_KEY).stacksTo(1).rarity(Rarity.UNCOMMON)));
		LEADER_STAFF = Registry.register(BuiltInRegistries.ITEM, LEADER_STAFF_KEY,
			new Item(new Item.Properties().setId(LEADER_STAFF_KEY).stacksTo(1).rarity(Rarity.RARE)));
	}

	public static boolean isStaff(ItemStack stack) {
		return LEADER_STAFF != null && stack.is(LEADER_STAFF);
	}
}
