package com.villagersociety.economy;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.config.ModConfig;
import org.junit.jupiter.api.Test;

class EconomyTest {
	private final ModConfig cfg = new ModConfig();

	@Test
	void noDiscountBelowFriend() {
		assertEquals(0.0, DiscountCalculator.fraction(-50, cfg));
		assertEquals(0.0, DiscountCalculator.fraction(0, cfg));
		assertEquals(0.0, DiscountCalculator.fraction(30, cfg));
	}

	@Test
	void discountGrowsWithLevelAndRespectsMax() {
		double friend = DiscountCalculator.fraction(50, cfg);
		double benefactor = DiscountCalculator.fraction(70, cfg);
		double hero = DiscountCalculator.fraction(100, cfg);
		assertTrue(friend > 0 && friend < benefactor && benefactor < hero);
		assertTrue(hero <= cfg.maxDiscount);

		ModConfig strict = new ModConfig();
		strict.maxDiscount = 0.10;
		strict.validate();
		assertTrue(DiscountCalculator.fraction(100, strict) <= 0.10);
	}

	@Test
	void discountIsRoundedAndNeverMakesItemsFree() {
		assertEquals(0, DiscountCalculator.discountAmount(1, 0.5));
		assertEquals(0, DiscountCalculator.discountAmount(10, 0.0));
		assertEquals(2, DiscountCalculator.discountAmount(10, 0.2));
		assertEquals(1, DiscountCalculator.discountAmount(2, 0.9));
		assertEquals(0, DiscountCalculator.discountAmount(20, 0.02));
	}

	@Test
	void merchantsRefuseOnlyWhenRebellingOrEnemy() {
		assertFalse(TradeRules.shouldRefuse(false, 0, cfg));
		assertFalse(TradeRules.shouldRefuse(false, -30, cfg));
		assertTrue(TradeRules.shouldRefuse(true, 10, cfg));
		assertTrue(TradeRules.shouldRefuse(false, cfg.rebellionThreshold, cfg));
		ModConfig off = new ModConfig();
		off.refuseTradeDuringRebellion = false;
		assertFalse(TradeRules.shouldRefuse(true, -100, off));
	}
}
