package com.villagersociety.recommendation;

import com.villagersociety.VillagerSociety;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Item registration. 1.21.2+ requires the registry key in Item.Properties#setId.
 * The letter is deliberately NOT in a creative tab: a copy without a server record is worthless.
 */
public final class ModItems {
	public static final ResourceKey<Item> RECOMMENDATION_LETTER_KEY = ResourceKey.create(Registries.ITEM,
		Identifier.fromNamespaceAndPath(VillagerSociety.MOD_ID, "recommendation_letter"));

	public static Item RECOMMENDATION_LETTER;

	private ModItems() {
	}

	public static void register() {
		RECOMMENDATION_LETTER = Registry.register(BuiltInRegistries.ITEM, RECOMMENDATION_LETTER_KEY,
			new Item(new Item.Properties().setId(RECOMMENDATION_LETTER_KEY).stacksTo(1).rarity(Rarity.UNCOMMON)));
	}
}
