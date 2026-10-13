package com.villagersociety.rebellion;

import static org.junit.jupiter.api.Assertions.*;

import com.villagersociety.rebellion.RoleAssigner.Candidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleAssignerTest {
	private static Candidate adult(double dist) {
		return new Candidate(UUID.randomUUID(), false, dist);
	}

	private static long count(Map<UUID, Role> roles, Role role) {
		return roles.values().stream().filter(r -> r == role).count();
	}

	@Test
	void babiesAndDistantWorkersFlee() {
		Candidate baby = new Candidate(UUID.randomUUID(), true, 3);
		Candidate far = adult(100);
		List<Candidate> list = new ArrayList<>(List.of(baby, far, adult(5), adult(6), adult(7)));
		Map<UUID, Role> roles = RoleAssigner.assign(list, 2, 8, 48);
		assertEquals(Role.FLEEING, roles.get(baby.id()));
		assertEquals(Role.FLEEING, roles.get(far.id()));
		assertEquals(3, RoleAssigner.participating(roles));
	}

	@Test
	void participantLimitIsRespectedAndExtrasFlee() {
		List<Candidate> list = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			list.add(adult(5 + i));
		}
		Map<UUID, Role> roles = RoleAssigner.assign(list, 2, 8, 48);
		assertEquals(8, RoleAssigner.participating(roles));
		assertEquals(4, count(roles, Role.FLEEING));
	}

	@Test
	void largeGroupHasFrontLineFlankersAlerterAndProtector() {
		List<Candidate> list = new ArrayList<>();
		list.add(new Candidate(UUID.randomUUID(), true, 4));
		for (int i = 0; i < 6; i++) {
			list.add(adult(5 + i));
		}
		Map<UUID, Role> roles = RoleAssigner.assign(list, 2, 8, 48);
		assertTrue(count(roles, Role.DEFENDER) >= 1);
		assertTrue(count(roles, Role.FLANKER) >= 1);
		assertEquals(1, count(roles, Role.ALERTER));
		assertEquals(1, count(roles, Role.PROTECTOR));
	}

	@Test
	void closestVillagerIsOnTheFrontLine() {
		Candidate nearest = adult(2);
		List<Candidate> list = new ArrayList<>(List.of(adult(10), nearest, adult(20), adult(30)));
		assertEquals(Role.DEFENDER, RoleAssigner.assign(list, 2, 8, 48).get(nearest.id()));
	}

	@Test
	void tooFewAdultsMeansNobodyFights() {
		Map<UUID, Role> roles = RoleAssigner.assign(List.of(adult(5)), 2, 8, 48);
		assertEquals(0, RoleAssigner.participating(roles));
	}
}
