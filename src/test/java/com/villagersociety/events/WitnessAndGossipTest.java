package com.villagersociety.events;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.reputation.ReputationAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WitnessAndGossipTest {
	@AfterEach
	void cleanup() {
		GossipScheduler.clear();
	}

	@Test
	void directKnowledgeIsNotDowngradedToHearsay() {
		WitnessMemory m = new WitnessMemory();
		UUID v = UUID.randomUUID();
		UUID p = UUID.randomUUID();
		m.learn(v, p, ReputationAction.BREAK_CROP, WitnessMemory.Source.DIRECT, 100);
		m.learn(v, p, ReputationAction.BREAK_CROP, WitnessMemory.Source.HEARSAY, 200);
		assertEquals(WitnessMemory.Source.DIRECT, m.get(v, p, 300).orElseThrow().source());
	}

	@Test
	void hearsayCanBeUpgradedToDirect() {
		WitnessMemory m = new WitnessMemory();
		UUID v = UUID.randomUUID();
		UUID p = UUID.randomUUID();
		m.learn(v, p, ReputationAction.BREAK_CROP, WitnessMemory.Source.HEARSAY, 100);
		m.learn(v, p, ReputationAction.ATTACK_VILLAGER, WitnessMemory.Source.DIRECT, 200);
		assertEquals(WitnessMemory.Source.DIRECT, m.get(v, p, 300).orElseThrow().source());
	}

	@Test
	void unknownVillagersKnowNothingAndMemoryExpires() {
		WitnessMemory m = new WitnessMemory();
		UUID v = UUID.randomUUID();
		UUID p = UUID.randomUUID();
		assertFalse(m.knows(v, p, 0));
		m.learn(v, p, ReputationAction.BREAK_CROP, WitnessMemory.Source.DIRECT, 0);
		assertTrue(m.knows(v, p, 1000));
		assertFalse(m.knows(v, p, 24_000L * 4));
	}

	@Test
	void schedulerRunsTasksInOrderAndOnlyWhenDue() {
		List<Integer> order = new ArrayList<>();
		GossipScheduler.schedule(50, () -> order.add(2));
		GossipScheduler.schedule(10, () -> order.add(1));
		GossipScheduler.schedule(10, () -> order.add(11));
		GossipScheduler.tick(5);
		assertTrue(order.isEmpty());
		GossipScheduler.tick(10);
		assertEquals(List.of(1, 11), order);
		GossipScheduler.tick(100);
		assertEquals(List.of(1, 11, 2), order);
		assertEquals(0, GossipScheduler.pending());
	}

	@Test
	void schedulerSurvivesFailingTasksAndIsBounded() {
		List<Integer> ran = new ArrayList<>();
		GossipScheduler.schedule(1, () -> {
			throw new IllegalStateException("boom");
		});
		GossipScheduler.schedule(1, () -> ran.add(1));
		GossipScheduler.tick(1);
		assertEquals(List.of(1), ran);

		int accepted = 0;
		for (int i = 0; i < 2000; i++) {
			if (GossipScheduler.schedule(1000, () -> { })) {
				accepted++;
			}
		}
		assertEquals(512, accepted);
	}
}
