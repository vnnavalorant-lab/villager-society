package com.villagersociety.mixin;

import com.villagersociety.economy.TradeHooks;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The only mixin in the mod, and it only delegates. Villager entities are NOT replaced.
 *
 * UNVERIFIED (1.21.11): the Villager package (this may now be net.minecraft.world.entity.npc.villager.Villager)
 * and the method names updateSpecialPrices(Player) / notifyTrade(MerchantOffer) in Mojang mappings.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void villagersociety$applyDiscount(Player player, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		TradeHooks.applyDiscount(self, player, self.getOffers());
	}

	@Inject(method = "notifyTrade", at = @At("TAIL"))
	private void villagersociety$tradeCompleted(MerchantOffer offer, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		Player trader = self.getTradingPlayer();
		if (trader != null) {
			TradeHooks.onTradeCompleted(self, trader);
		}
	}
}
