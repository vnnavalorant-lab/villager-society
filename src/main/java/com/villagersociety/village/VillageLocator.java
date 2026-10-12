package com.villagersociety.village;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.village.PoiClustering.P;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Finds (and registers on first sight) the village that owns a block position.
 *
 * How villages are told apart:
 *  1. Collect village POIs (beds, job sites, bell) near the position.
 *  2. Keep only the chain-connected cluster nearest to the position (see {@link PoiClustering}).
 *  3. Re-query around the cluster centroid so the centroid does not depend on where you stood.
 *  4. Match the centroid against registered villages; otherwise register a new one.
 *
 * Results are cached per chunk for a few seconds, so block-break events do not repeat POI queries.
 * Only the Overworld is supported for now.
 *
 * UNVERIFIED API POINTS (1.21.11): PoiManager#getInRange(Predicate<Holder<PoiType>>, BlockPos, int, Occupancy),
 * PoiTypeTags.VILLAGE, PoiRecord#getPos(), PoiManager.Occupancy.{ANY, IS_OCCUPIED}.
 */
public final class VillageLocator {
	private static final int CACHE_TTL_TICKS = 200;
	private static final int MIN_POIS = 3;
	private static final double LINK_DISTANCE = 24.0;
	private static final int MAX_POIS_PER_QUERY = 256;

	private record CacheKey(ServerLevel level, long chunk) {
	}

	private record CacheEntry(UUID village, long expires) {
	}

	private static final Map<CacheKey, CacheEntry> CACHE = new HashMap<>();

	private VillageLocator() {
	}

	public static void clearCache() {
		CACHE.clear();
	}

	/** Returns the village at {@code pos}, registering it if new, or null when pos is not in a village. */
	public static VillageData locate(ServerLevel level, BlockPos pos, SocietyState state) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		long now = level.getGameTime();
		long chunk = (((long) (pos.getX() >> 4)) << 32) | ((pos.getZ() >> 4) & 0xFFFFFFFFL);
		CacheKey key = new CacheKey(level, chunk);

		CacheEntry cached = CACHE.get(key);
		if (cached != null && cached.expires() > now) {
			if (cached.village() == null) {
				return null;
			}
			VillageData known = state.village(cached.village());
			if (known != null) {
				return known;
			}
		}

		VillageData result = resolve(level, pos, state);
		if (CACHE.size() > 1024) {
			CACHE.clear();
		}
		CACHE.put(key, new CacheEntry(result == null ? null : result.id, now + CACHE_TTL_TICKS));
		return result;
	}

	private static VillageData resolve(ServerLevel level, BlockPos pos, SocietyState state) {
		ModConfig cfg = ModConfig.get();
		P seed = new P(pos.getX(), pos.getY(), pos.getZ());

		List<P> near = poisInRange(level, pos, cfg.villageInfluenceRadius, PoiManager.Occupancy.ANY);
		if (near.isEmpty()) {
			return null;
		}
		List<P> cluster = PoiClustering.clusterFrom(near, seed, LINK_DISTANCE);
		P centroid = PoiClustering.centroid(cluster);

		// Second pass around the centroid: captures the whole village even if we stood at its edge.
		List<P> wide = poisInRange(level, new BlockPos(centroid.x(), centroid.y(), centroid.z()),
			cfg.villageInfluenceRadius, PoiManager.Occupancy.ANY);
		if (!wide.isEmpty()) {
			cluster = PoiClustering.clusterFrom(wide, centroid, LINK_DISTANCE);
			centroid = PoiClustering.centroid(cluster);
		}

		if (cluster.size() < MIN_POIS) {
			return null;
		}
		if (centroid.distanceTo(seed) > cfg.villageAssociationMaxDistance) {
			return null;
		}

		int radius = (int) Math.max(24, Math.min(cfg.villageInfluenceRadius,
			PoiClustering.maxDistance(cluster, centroid) + 16));
		BlockPos centerPos = new BlockPos(centroid.x(), centroid.y(), centroid.z());

		Map<UUID, P> centers = new HashMap<>();
		for (VillageData v : state.villages()) {
			centers.put(v.id, new P(v.center.getX(), v.center.getY(), v.center.getZ()));
		}
		Optional<UUID> match = VillageMatcher.match(centers, centroid, Math.max(16, cfg.villageInfluenceRadius / 2.0));
		if (match.isPresent()) {
			VillageData existing = state.village(match.get());
			if (existing != null) {
				P old = centers.get(existing.id);
				if (old.distanceTo(centroid) >= 6 || Math.abs(existing.radius - radius) >= 8) {
					existing.center = centerPos;
					existing.radius = radius;
					state.setDirty();
				}
				return existing;
			}
		}

		// New villages need at least one POI claimed by a villager, so a player's own beds are ignored.
		List<P> occupied = poisInRange(level, centerPos, radius, PoiManager.Occupancy.IS_OCCUPIED);
		if (occupied.isEmpty()) {
			return null;
		}

		UUID id = UUID.randomUUID();
		VillageData created = new VillageData(id, centerPos, radius, Personality.randomFor(id).id(),
			Optional.empty(), "NONE", Map.of());
		state.putVillage(created);
		return created;
	}

	private static List<P> poisInRange(ServerLevel level, BlockPos center, int radius, PoiManager.Occupancy occupancy) {
		return level.getPoiManager()
			.getInRange(holder -> holder.is(PoiTypeTags.VILLAGE), center, radius, occupancy)
			.map(record -> {
				BlockPos p = record.getPos();
				return new P(p.getX(), p.getY(), p.getZ());
			})
			.limit(MAX_POIS_PER_QUERY)
			.toList();
	}
}
