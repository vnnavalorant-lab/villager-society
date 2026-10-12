package com.villagersociety.economy;

import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.events.InfractionService;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.reputation.ReputationAction;
import com.villagersociety.village.VillageLocator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Called from VillagerMixin. Keeps all trade logic out of the mixin.
 *
 * Discount: vanilla's Villager#updateSpecialPrices resets every offer's special price diff and then
 * applies vanilla reputation/Hero of the Village. We run right after it and ADD our (negative) diff,
 * computed from the base price, so vanilla demand/gossip modifiers are preserved and nothing stacks.
 *
 * UNVERIFIED API POINTS (1.21.11): MerchantOffer#getBaseCostA(), #addToSpecialPriceDiff(int),
 * package net.minecraft.world.item.trading.
 */
public final class TradeHooks {
	private TradeHooks() {
	}

	public static void applyDiscount(Entity villager, Player player, MerchantOffers offers) {
		if (!(villager.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) {
			return;
		}
		SocietyState state = SocietyState.get(level.getServer());
		VillageData village = VillageLocator.locate(level, villager.blockPosition(), state);
		if (village == null) {
			return;
		}
		PlayerStanding st = village.standing(sp.getUUID());
		VillagerSociety.REPUTATION.recover(st, level.getGameTime());

		double fraction = DiscountCalculator.fraction(st.score, ModConfig.get());
		if (fraction <= 0) {
			return;
		}
		for (MerchantOffer offer : offers) {
			int amount = DiscountCalculator.discountAmount(offer.getBaseCostA().getCount(), fraction);
			if (amount > 0) {
				offer.addToSpecialPriceDiff(-amount);
			}
		}
	}

	public static void onTradeCompleted(Entity villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) {
			return;
		}
		SocietyState state = SocietyState.get(level.getServer());
		VillageData village = VillageLocator.locate(level, villager.blockPosition(), state);
		if (village != null) {
			InfractionService.report(level, sp, state, village, ReputationAction.TRADE, villager.blockPosition(), false);
		}
	}
}
