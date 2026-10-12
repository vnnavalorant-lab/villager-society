package com.villagersociety.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ModConfigTest {
	@Test
	void defaultsAreValidAndUnchangedByValidation() {
		ModConfig c = new ModConfig();
		int rebellion = c.rebellionThreshold;
		double hero = c.heroDiscount;
		c.validate();
		assertEquals(rebellion, c.rebellionThreshold);
		assertEquals(hero, c.heroDiscount);
	}

	@Test
	void absurdValuesAreClamped() {
		ModConfig c = new ModConfig();
		c.maxDiscount = 5.0;
		c.heroDiscount = 9.0;
		c.rebellionMaxParticipants = 0;
		c.punishmentIntensity = Double.NaN;
		c.validate();
		assertTrue(c.maxDiscount <= 0.5);
		assertTrue(c.heroDiscount <= c.maxDiscount);
		assertTrue(c.rebellionMaxParticipants >= c.rebellionMinParticipants);
		assertTrue(c.punishmentIntensity >= 0.1);
	}

	@Test
	void phaseThresholdsStayOrdered() {
		ModConfig c = new ModConfig();
		c.suspicionThreshold = -90;
		c.warningThreshold = -10;
		c.mobilizationThreshold = -5;
		c.rebellionThreshold = -1;
		c.validate();
		assertTrue(c.suspicionThreshold > c.warningThreshold);
		assertTrue(c.warningThreshold > c.mobilizationThreshold);
		assertTrue(c.mobilizationThreshold > c.rebellionThreshold);
	}
}
