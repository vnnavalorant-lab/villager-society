package com.villagersociety.mixin;

import com.villagersociety.economy.TradeHooks;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The only mixin in the mod, and it only delegates. Villager entities are NOT replaced.
 *
 * Package confirmed by the compiler: net.minecraft.world.entity.npc.villager.
 * UNVERIFIED: updateSpecialPrices(Player) and rewardTradeXp(MerchantOffer) exist in Villager (Mojang mappings).
 * A wrong name makes the game crash at startup with an InvalidInjectionException naming the method.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void villagersociety$applyDiscount(Player player, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		TradeHooks.applyDiscount(self, player, self.getOffers());
	}

	/**
	 * notifyTrade() lives in AbstractVillager (confirmed by a runtime crash), so we hook rewardTradeXp(),
	 * the abstract method Villager implements and notifyTrade() calls exactly once per completed trade.
	 * At HEAD the trading player is still set.
	 */
	@Inject(method = "rewardTradeXp", at = @At("HEAD"))
	private void villagersociety$tradeCompleted(MerchantOffer offer, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		Player trader = self.getTradingPlayer();
		if (trader != null) {
			TradeHooks.onTradeCompleted(self, trader);
		}
	}
}
