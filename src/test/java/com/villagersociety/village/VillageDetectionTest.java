package com.villagersociety.village;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.village.PoiClustering.P;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VillageDetectionTest {
	private static List<P> row(int startX, int z, int count, int step) {
		List<P> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new P(startX + i * step, 64, z));
		}
		return list;
	}

	@Test
	void twoDistantVillagesAreNotMerged() {
		List<P> all = new ArrayList<>();
		all.addAll(row(0, 0, 5, 10));      // village A: x 0..40
		all.addAll(row(100, 0, 5, 10));    // village B: x 100..140 (gap 60 > link 24)
		List<P> a = PoiClustering.clusterFrom(all, new P(5, 64, 0), 24);
		List<P> b = PoiClustering.clusterFrom(all, new P(105, 64, 0), 24);
		assertEquals(5, a.size());
		assertEquals(5, b.size());
		assertTrue(PoiClustering.centroid(a).x() < 50);
		assertTrue(PoiClustering.centroid(b).x() > 100);
	}

	@Test
	void chainedPoisFormOneVillage() {
		List<P> chain = row(0, 0, 8, 20);   // each 20 apart (< 24) -> single cluster
		assertEquals(8, PoiClustering.clusterFrom(chain, new P(0, 64, 0), 24).size());
	}

	@Test
	void matcherPicksNearestWithinLimitOnly() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		Map<UUID, P> centers = Map.of(a, new P(0, 64, 0), b, new P(200, 64, 0));
		assertEquals(Optional.of(a), VillageMatcher.match(centers, new P(10, 64, 5), 24));
		assertEquals(Optional.empty(), VillageMatcher.match(centers, new P(100, 64, 0), 24));
	}

	@Test
	void personalityIsDeterministicPerVillage() {
		UUID id = UUID.randomUUID();
		assertEquals(Personality.randomFor(id), Personality.randomFor(id));
		assertEquals(Personality.CAUTIOUS, Personality.fromId("does_not_exist"));
		assertEquals(Personality.COMMERCIAL, Personality.fromId("commercial"));
	}
}
