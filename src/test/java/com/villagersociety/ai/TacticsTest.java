package com.villagersociety.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.ai.Tactics.Pt;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticsTest {
	@Test
	void slotsAreSpreadAroundTheTargetOnTheSameRadius() {
		Set<String> distinct = new HashSet<>();
		for (int i = 0; i < 4; i++) {
			Pt p = Tactics.slotPoint(10, 20, i, 4, 2.5, 0.3);
			assertEquals(2.5, Math.hypot(p.x() - 10, p.z() - 20), 1e-9);
			distinct.add(String.format("%.3f/%.3f", p.x(), p.z()));
		}
		assertEquals(4, distinct.size());
	}

	@Test
	void fleePointIsFurtherFromThePlayer() {
		Pt p = Tactics.fleePoint(0, 0, 3, 4, 10);
		assertTrue(Math.hypot(p.x(), p.z()) > Math.hypot(3, 4));
		Pt same = Tactics.fleePoint(5, 5, 5, 5, 10);   // standing on the player: still moves somewhere
		assertTrue(Math.hypot(same.x() - 5, same.z() - 5) > 1);
	}

	@Test
	void protectorStandsBetweenBabyAndPlayer() {
		Pt p = Tactics.protectorPoint(0, 0, 10, 0, 2);
		assertEquals(2.0, p.x(), 1e-9);
		assertEquals(0.0, p.z(), 1e-9);
	}

	@Test
	void onlyTheClosestVillagersAreAllowedToStrike() {
		Map<UUID, Double> dist = new HashMap<>();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		UUID d = UUID.randomUUID();
		dist.put(a, 5.0);
		dist.put(b, 1.0);
		dist.put(c, 3.0);
		dist.put(d, 9.0);
		assertEquals(Set.of(b, c), Tactics.selectAttackers(dist, 2));
		assertEquals(4, Tactics.selectAttackers(dist, 10).size());
	}
}
