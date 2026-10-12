package com.villagersociety.village;

import com.villagersociety.village.PoiClustering.P;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Decides whether a detected cluster is an already-registered village. Pure logic. */
public final class VillageMatcher {
	private VillageMatcher() {
	}

	/** Nearest registered center within {@code maxDistance} of the cluster centroid, if any. */
	public static Optional<UUID> match(Map<UUID, P> centers, P centroid, double maxDistance) {
		UUID best = null;
		double bestD = Double.MAX_VALUE;
		for (Map.Entry<UUID, P> e : centers.entrySet()) {
			double d = e.getValue().distanceTo(centroid);
			if (d <= maxDistance && d < bestD) {
				bestD = d;
				best = e.getKey();
			}
		}
		return Optional.ofNullable(best);
	}
}
