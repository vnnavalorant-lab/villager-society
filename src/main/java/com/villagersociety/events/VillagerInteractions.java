package com.villagersociety.events;

import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.economy.TradeRules;
import com.villagersociety.leader.PlayerLeaders;
import com.villagersociety.leader.StaffActions;
import com.villagersociety.network.ControlSync;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.recommendation.LetterStack;
import com.villagersociety.recommendation.ModItems;
import com.villagersociety.recommendation.RecommendationService;
import com.villagersociety.reputation.ReputationAction;
import com.villagersociety.reputation.ReputationManager;
import com.villagersociety.village.LeaderService;
import com.villagersociety.village.VillageLocator;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Right-click on a villager (main hand only). Villagers never speak; they react with the vanilla "yes"/"no"
 * sounds, by refusing to trade, and by their prices.
 *  - Leader's Staff in hand                     -> leader tools (assign job / select for immigration)
 *  - Emerald + Ctrl                             -> give the emerald (restores reputation)
 *  - Bell + Shift                               -> become the village leader (needs high reputation)
 *  - Shift (anything else) on the representative -> ask for a recommendation letter
 *  - holding a letter, no Shift, on the representative -> present the letter
 *  - otherwise: trade is blocked ("no" sound) if the village refuses to trade with you; else vanilla behavior
 */
public final class VillagerInteractions {
	private VillagerInteractions() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || entity.getType() != EntityType.VILLAGER
				|| !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			return handle(level, sp, entity);
		});
	}

	/**
	 * One right-click on an entity reaches the server as TWO interaction packets (interact-at, then interact),
	 * and the Fabric callback runs for both. Any action that already produced a result for this player and this
	 * villager within the last 2 ticks is answered with that same result without running again.
	 */
	private record Recent(UUID villager, long tick, InteractionResult result) {
	}

	private static final Map<UUID, Recent> RECENT = new HashMap<>();

	private static InteractionResult handle(ServerLevel level, ServerPlayer player, Entity villager) {
		long now = level.getGameTime();
		Recent previous = RECENT.get(player.getUUID());
		if (previous != null && previous.villager().equals(villager.getUUID()) && now - previous.tick() <= 2) {
			return previous.result();
		}
		InteractionResult result = process(level, player, villager);
		if (result != InteractionResult.PASS) {
			if (RECENT.size() > 256) {
				RECENT.clear();
			}
			RECENT.put(player.getUUID(), new Recent(villager.getUUID(), now, result));
		}
		return result;
	}

	private static InteractionResult process(ServerLevel level, ServerPlayer player, Entity villager) {
		SocietyState state = SocietyState.get(level.getServer());
		VillageData village = VillageLocator.locate(level, villager.blockPosition(), state);
		if (village == null) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getMainHandItem();
		boolean sneaking = player.isShiftKeyDown();

		if (ModItems.isStaff(held)) {
			StaffActions.useOnVillager(level, player, villager, village, state);
			return InteractionResult.SUCCESS;
		}

		if (held.is(Items.EMERALD) && ControlSync.isCtrlDown(player)) {
			return giveEmerald(level, player, held, village, state, villager);
		}

		if (sneaking && held.is(Items.BELL)) {
			PlayerLeaders.useBell(level, player, held, village, state);
			react(level, villager, SoundEvents.VILLAGER_YES);
			return InteractionResult.SUCCESS;
		}

		Optional<UUID> leader = LeaderService.ensureLeader(level, village, state);
		boolean isRepresentative = leader.isPresent() && leader.get().equals(villager.getUUID());

		if (sneaking) {
			if (isRepresentative) {
				RecommendationService.issue(level, player, village, villager.getUUID(), state);
			} else {
				react(level, villager, SoundEvents.VILLAGER_NO);
			}
			return InteractionResult.SUCCESS;
		}

		if (LetterStack.isLetter(held)) {
			if (isRepresentative) {
				RecommendationService.present(level, player, held, village, state);
			} else {
				react(level, villager, SoundEvents.VILLAGER_NO);
			}
			return InteractionResult.SUCCESS;
		}

		PlayerStanding st = village.standing(player.getUUID());
		VillagerSociety.REPUTATION.recover(st, level.getGameTime());
		boolean rebelling = VillagerSociety.REBELLIONS.isTargeting(village.id, player.getUUID());
		if (TradeRules.shouldRefuse(rebelling, st.score, ModConfig.get())) {
			react(level, villager, SoundEvents.VILLAGER_NO);
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	/** One emerald per click: restores reputation even for hostile players. Silent apart from the "yes" sound. */
	private static InteractionResult giveEmerald(ServerLevel level, ServerPlayer player, ItemStack held,
												 VillageData village, SocietyState state, Entity villager) {
		PlayerStanding st = village.standing(player.getUUID());
		if (st.score >= PlayerStanding.MAX) {
			react(level, villager, SoundEvents.VILLAGER_NO); // nothing left to restore: keep the emerald
			return InteractionResult.SUCCESS;
		}
		ReputationManager.Result r = InfractionService.report(level, player, state, village,
			ReputationAction.DONATE_ITEM, villager.blockPosition(), false);
		if (r.counted()) {
			if (!player.isCreative()) {
				held.shrink(1);
			}
			react(level, villager, SoundEvents.VILLAGER_YES);
		}
		return InteractionResult.SUCCESS;
	}

	private static void react(ServerLevel level, Entity villager, SoundEvent sound) {
		level.playSound(null, villager.blockPosition(), sound, SoundSource.NEUTRAL, 1.0f, 1.0f);
	}
}
