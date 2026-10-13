package com.villagersociety.rebellion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Distributes villagers into roles. Babies, distant workers and villagers beyond the participant
 * limit flee. If fewer than {@code minFighters} adults are eligible, nobody fights at all.
 * Pure logic: closest eligible villagers get the front-line roles.
 */
public final class RoleAssigner {
	private RoleAssigner() {
	}

	public record Candidate(UUID id, boolean baby, double distToPlayer) {
	}

	public static Map<UUID, Role> assign(List<Candidate> candidates, int minFighters, int maxFighters,
										 double maxDistance) {
		Map<UUID, Role> out = new HashMap<>();
		List<Candidate> eligible = new ArrayList<>();
		boolean babies = false;
		for (Candidate c : candidates) {
			if (c.baby()) {
				out.put(c.id(), Role.FLEEING);
				babies = true;
			} else if (c.distToPlayer() > maxDistance) {
				out.put(c.id(), Role.FLEEING);
			} else {
				eligible.add(c);
			}
		}
		eligible.sort(Comparator.comparingDouble(Candidate::distToPlayer).thenComparing(Candidate::id));

		int fighters = Math.min(eligible.size(), maxFighters);
		for (int i = fighters; i < eligible.size(); i++) {
			out.put(eligible.get(i).id(), Role.FLEEING);
		}
		if (fighters < minFighters) {
			for (int i = 0; i < fighters; i++) {
				out.put(eligible.get(i).id(), Role.FLEEING);
			}
			return out;
		}

		int alerter = fighters >= 3 ? fighters - 1 : -1;
		int protector = (babies && fighters >= 4) ? fighters - 2 : -1;
		int toggle = 0;
		for (int i = 0; i < fighters; i++) {
			Role role;
			if (i == alerter) {
				role = Role.ALERTER;
			} else if (i == protector) {
				role = Role.PROTECTOR;
			} else {
				role = (toggle++ % 2 == 0) ? Role.DEFENDER : Role.FLANKER;
			}
			out.put(eligible.get(i).id(), role);
		}
		return out;
	}

	public static int participating(Map<UUID, Role> roles) {
		int n = 0;
		for (Role r : roles.values()) {
			if (r.isParticipating()) {
				n++;
			}
		}
		return n;
	}
}
