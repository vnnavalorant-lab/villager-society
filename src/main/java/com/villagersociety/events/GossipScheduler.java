package com.villagersociety.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Delayed tasks for gossip. The queue is checked once per server tick (O(1) when empty) and
 * bounded, so a flood of infractions cannot grow it without limit.
 */
public final class GossipScheduler {
	private static final Logger LOGGER = LoggerFactory.getLogger("villager_society");
	private static final int MAX_TASKS = 512;

	private record Task(long at, long seq, Runnable run) {
	}

	private static final PriorityQueue<Task> QUEUE =
		new PriorityQueue<>(Comparator.comparingLong(Task::at).thenComparingLong(Task::seq));
	private static long sequence = 0;

	private GossipScheduler() {
	}

	public static boolean schedule(long atTick, Runnable run) {
		if (QUEUE.size() >= MAX_TASKS) {
			return false;
		}
		QUEUE.add(new Task(atTick, sequence++, run));
		return true;
	}

	public static void tick(long nowTick) {
		while (!QUEUE.isEmpty() && QUEUE.peek().at() <= nowTick) {
			Task t = QUEUE.poll();
			try {
				t.run().run();
			} catch (RuntimeException e) {
				LOGGER.warn("Gossip task failed", e);
			}
		}
	}

	public static int pending() {
		return QUEUE.size();
	}

	public static void clear() {
		QUEUE.clear();
	}
}
