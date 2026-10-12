package com.villagersociety.events;

import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.dialogue.Say;
import com.villagersociety.economy.TradeRules;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.recommendation.LetterStack;
import com.villagersociety.recommendation.RecommendationService;
import com.villagersociety.reputation.ReputationAction;
import com.villagersociety.reputation.ReputationManager;
import com.villagersociety.village.LeaderService;
import com.villagersociety.village.VillageLocator;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.UUID;

/**
 * Right-click on a villager (main hand only):
 *  - sneak + bread/carrot/potato/beetroot/wheat -> donate food (+reputation)
 *  - sneak + (no food)                          -> ask the village leader for a recommendation letter
 *  - holding a letter, no sneak, on a leader    -> present the letter
 *  - otherwise, if the village refuses to trade with you -> trade screen blocked; else vanilla behavior
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

	private static InteractionResult handle(ServerLevel level, ServerPlayer player, Entity villager) {
		SocietyState state = SocietyState.get(level.getServer());
		VillageData village = VillageLocator.locate(level, villager.blockPosition(), state);
		if (village == null) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getMainHandItem();
		boolean sneaking = player.isShiftKeyDown();

		if (sneaking && isDonatable(held)) {
			return donate(level, player, held, village, state, villager);
		}

		Optional<UUID> leader = LeaderService.ensureLeader(level, village, state);
		boolean isLeader = leader.isPresent() && leader.get().equals(villager.getUUID());

		if (sneaking) {
			if (isLeader) {
				RecommendationService.issue(level, player, village, villager.getUUID(), state);
			} else {
				Say.toPlayer(player, "Eu não sou o representante desta vila. Procure o chefe (veja /villagersociety info).");
			}
			return InteractionResult.SUCCESS;
		}

		if (LetterStack.isLetter(held)) {
			if (isLeader) {
				RecommendationService.present(level, player, held, village, state);
				return InteractionResult.SUCCESS;
			}
			Say.toPlayer(player, "Entregue essa carta ao representante da vila.");
			return InteractionResult.SUCCESS;
		}

		PlayerStanding st = village.standing(player.getUUID());
		VillagerSociety.REPUTATION.recover(st, level.getGameTime());
		if (TradeRules.shouldRefuse(village.rebellionState, st.score, ModConfig.get())) {
			Say.toPlayer(player, "Não vamos negociar com você. Reconquiste nossa confiança.");
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	private static boolean isDonatable(ItemStack stack) {
		return stack.is(Items.BREAD) || stack.is(Items.CARROT) || stack.is(Items.POTATO)
			|| stack.is(Items.BEETROOT) || stack.is(Items.WHEAT);
	}

	private static InteractionResult donate(ServerLevel level, ServerPlayer player, ItemStack held,
											VillageData village, SocietyState state, Entity villager) {
		ReputationManager.Result r = InfractionService.report(level, player, state, village,
			ReputationAction.DONATE_ITEM, villager.blockPosition(), false);
		if (r.counted()) {
			held.shrink(1);
			Say.toPlayer(player, "Obrigado pela ajuda!");
		} else {
			Say.toPlayer(player, "Você já nos ajudou há pouco. Guarde isso para depois.");
		}
		return InteractionResult.SUCCESS;
	}
}
