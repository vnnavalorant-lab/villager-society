package com.villagersociety.ai;

import com.villagersociety.config.ModConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * Visual-only weapons. The tool is put in the villager's main-hand slot (rendered by the vanilla villager
 * model) and never touches the profession inventory. A scoreboard tag marks armed villagers so the item
 * is always cleaned up: when the rebellion ends, when the villager dies (no free drops), and when the
 * entity loads after a restart in the middle of a rebellion.
 *
 * UNVERIFIED API POINTS (1.21.11): Mob#setItemSlot, Entity#addTag/removeTag/getTags,
 * ServerEntityEvents.ENTITY_LOAD and ServerLivingEntityEvents.ALLOW_DEATH signatures.
 */
public final class Armament {
	public static final String TAG = "villager_society_armed";
	private static final Item[] TOOLS = {Items.IRON_AXE, Items.STONE_AXE, Items.IRON_HOE, Items.STONE_HOE};

	private Armament() {
	}

	public static void arm(Mob mob) {
		if (!ModConfig.get().showWeapons || mob.getTags().contains(TAG)) {
			return;
		}
		UUID id = mob.getUUID();
		Item tool = TOOLS[Math.floorMod((int) (id.getLeastSignificantBits() ^ id.getMostSignificantBits()), TOOLS.length)];
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tool));
		mob.addTag(TAG);
	}

	public static void disarm(Mob mob) {
		if (!mob.getTags().contains(TAG)) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.removeTag(TAG);
	}

	/** Cleanup hooks; the callback decides whether an entity still belongs to an active rebellion. */
	public static void register(java.util.function.Predicate<UUID> inActiveRebellion) {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof Mob mob && mob.getTags().contains(TAG) && !inActiveRebellion.test(mob.getUUID())) {
				disarm(mob);
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Mob mob) {
				disarm(mob);
			}
			return true;
		});
	}
}
