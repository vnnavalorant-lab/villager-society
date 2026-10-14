package com.villagersociety.events;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HayRuleTest {
	@Test
	void halfOfTheVillagesHayMustBeBroken() {
		assertEquals(5, HayRule.threshold(10, 0.5));
		assertEquals(6, HayRule.threshold(11, 0.5)); // rounds up
		assertFalse(HayRule.punishable(10, 1, 0.5));
		assertFalse(HayRule.punishable(10, 4, 0.5));
		assertTrue(HayRule.punishable(10, 5, 0.5));
		assertTrue(HayRule.punishable(10, 9, 0.5));
	}

	@Test
	void aVillageWithOneBaleNeedsThatOneBale() {
		assertEquals(1, HayRule.threshold(1, 0.5));
		assertTrue(HayRule.punishable(1, 1, 0.5));
	}

	@Test
	void thresholdIsNeverZeroAndFractionCanBeRaised() {
		assertEquals(1, HayRule.threshold(0, 0.5));
		assertEquals(10, HayRule.threshold(10, 1.0));
		assertFalse(HayRule.punishable(10, 9, 1.0));
	}
}
