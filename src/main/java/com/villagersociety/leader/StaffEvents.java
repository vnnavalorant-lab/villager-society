package com.villagersociety.leader;

import com.villagersociety.recommendation.ModItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Staff in the air or on a block. (On a villager it is handled by VillagerInteractions.)
 *
 * UNVERIFIED API POINT (1.21.11): UseItemCallback lambda signature and return type.
 */
public final class StaffEvents {
	private StaffEvents() {
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world instanceof ServerLevel && player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND
				&& ModItems.isStaff(sp.getMainHandItem()) && sp.isShiftKeyDown()) {
				StaffActions.cycleMode(sp);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND
				&& ModItems.isStaff(sp.getMainHandItem())) {
				if (sp.isShiftKeyDown()) {
					StaffActions.cycleMode(sp);
				} else {
					StaffActions.onBlock(level, sp, hit.getBlockPos());
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}
}
