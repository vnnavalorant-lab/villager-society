package com.villagersociety.village;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups points of interest (beds, job sites, bells) into one village by single-linkage:
 * two POIs belong together when a chain of POIs, each within {@code link} blocks of the next,
 * connects them. Two villages separated by more than {@code link} blocks never merge.
 * Pure geometry, no Minecraft types.
 */
public final class PoiClustering {
	private PoiClustering() {
	}

	public record P(int x, int y, int z) {
		public double distanceTo(P o) {
			return Math.sqrt(distSq(o));
		}

		double distSq(P o) {
			double dx = x - o.x;
			double dy = y - o.y;
			double dz = z - o.z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	public static P nearest(List<P> points, P target) {
		P best = null;
		double bestD = Double.MAX_VALUE;
		for (P p : points) {
			double d = p.distSq(target);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** Returns the cluster containing the point nearest to {@code seed}. */
	public static List<P> clusterFrom(List<P> points, P seed, double link) {
		List<P> result = new ArrayList<>();
		if (points.isEmpty()) {
			return result;
		}
		double linkSq = link * link;
		boolean[] used = new boolean[points.size()];
		P start = nearest(points, seed);
		ArrayDeque<P> queue = new ArrayDeque<>();
		used[points.indexOf(start)] = true;
		queue.add(start);
		while (!queue.isEmpty()) {
			P cur = queue.poll();
			result.add(cur);
			for (int i = 0; i < points.size(); i++) {
				if (!used[i] && points.get(i).distSq(cur) <= linkSq) {
					used[i] = true;
					queue.add(points.get(i));
				}
			}
		}
		return result;
	}

	public static P centroid(List<P> points) {
		long x = 0, y = 0, z = 0;
		for (P p : points) {
			x += p.x;
			y += p.y;
			z += p.z;
		}
		int n = Math.max(1, points.size());
		return new P((int) Math.round((double) x / n), (int) Math.round((double) y / n), (int) Math.round((double) z / n));
	}

	public static double maxDistance(List<P> points, P center) {
		double max = 0;
		for (P p : points) {
			max = Math.max(max, p.distanceTo(center));
		}
		return max;
	}
}
