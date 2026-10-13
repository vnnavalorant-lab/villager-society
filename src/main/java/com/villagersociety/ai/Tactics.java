package com.villagersociety.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure geometry/selection used by the combat controller, so positions are spread around the
 * target instead of piling up on one point, and only a few villagers attack at the same time.
 */
public final class Tactics {
	private Tactics() {
	}

	public record Pt(double x, double z) {
	}

	/** A point on a circle around the target; slot {@code index} of {@code count}. */
	public static Pt slotPoint(double tx, double tz, int index, int count, double radius, double rotationRad) {
		double angle = rotationRad + 2 * Math.PI * index / Math.max(1, count);
		return new Pt(tx + Math.cos(angle) * radius, tz + Math.sin(angle) * radius);
	}

	/** A point {@code distance} blocks further away from the player, along the player-to-villager line. */
	public static Pt fleePoint(double px, double pz, double vx, double vz, double distance) {
		double dx = vx - px;
		double dz = vz - pz;
		double len = Math.hypot(dx, dz);
		if (len < 1e-6) {
			dx = 1;
			dz = 0;
			len = 1;
		}
		return new Pt(vx + dx / len * distance, vz + dz / len * distance);
	}

	/** A point next to the protected villager, on the side facing the player. */
	public static Pt protectorPoint(double bx, double bz, double px, double pz, double offset) {
		double dx = px - bx;
		double dz = pz - bz;
		double len = Math.hypot(dx, dz);
		if (len < 1e-6) {
			return new Pt(bx, bz);
		}
		return new Pt(bx + dx / len * offset, bz + dz / len * offset);
	}

	/** The {@code max} closest villagers are allowed to strike this round. */
	public static Set<UUID> selectAttackers(Map<UUID, Double> distances, int max) {
		List<Map.Entry<UUID, Double>> entries = new ArrayList<>(distances.entrySet());
		entries.sort(Map.Entry.<UUID, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())));
		Set<UUID> allowed = new HashSet<>();
		for (int i = 0; i < Math.min(max, entries.size()); i++) {
			allowed.add(entries.get(i).getKey());
		}
		return allowed;
	}
}
