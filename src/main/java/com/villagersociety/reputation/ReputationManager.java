package com.villagersociety.reputation;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.HistoryEntry;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.village.Personality;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * All reputation rules in one place. Pure logic (no Minecraft types): the caller supplies the
 * standing, personality and current game time, so everything here is unit-testable.
 * Passive recovery is computed lazily from timestamps (no per-tick work).
 */
public final class ReputationManager {
	/** An offense of the same kind within this window counts as a repeat. */
	private static final long REPEAT_WINDOW_TICKS = 72_000L;
	private static final long DAY_TICKS = 24_000L;
	private static final double ACCIDENT_FACTOR = 0.25;
	private static final double GRAVE_LOCK_POSITIVE_FACTOR = 0.5;

	private record DedupKey(UUID village, UUID player, ReputationAction action) {
	}

	private record PairKey(UUID village, UUID player) {
	}

	private record DailyGain(long start, int gained) {
	}

	private record HayCount(long start, int count) {
	}

	public record Result(boolean counted, int delta, int newScore, ReputationLevel level) {
	}

	/** Transient anti-duplication / anti-farming state; safe to lose on restart. */
	private final Map<DedupKey, Long> lastCounted = new HashMap<>();
	private final Map<PairKey, DailyGain> daily = new HashMap<>();
	private final Map<PairKey, HayCount> hay = new HashMap<>();

	public Result apply(UUID villageId, UUID player, PlayerStanding st, Personality personality,
						ReputationAction action, boolean accidental, long now) {
		return apply(villageId, player, st, personality, action, accidental, now, 1.0);
	}

	/**
	 * @param witnessWeight multiplier for offenses: &lt;1 when nobody saw it, &gt;1 when many villagers did.
	 *                      Ignored for positive actions.
	 */
	public Result apply(UUID villageId, UUID player, PlayerStanding st, Personality personality,
						ReputationAction action, boolean accidental, long now, double witnessWeight) {
		ModConfig cfg = ModConfig.get();
		recover(st, now);

		DedupKey key = new DedupKey(villageId, player, action);
		Long last = lastCounted.get(key);
		if (action.dedupeTicks() > 0 && last != null && now >= last && now - last < action.dedupeTicks()) {
			return ignored(st);
		}
		if (lastCounted.size() > 4096) {
			lastCounted.clear();
		}
		if (daily.size() > 4096) {
			daily.clear();
		}
		if (hay.size() > 4096) {
			hay.clear();
		}
		lastCounted.put(key, now);

		int base = action.base(cfg);
		boolean negative = base < 0;
		double delta;
		if (negative) {
			delta = base * cfg.punishmentIntensity * personality.lossMultiplier() * witnessWeight;
			if (action == ReputationAction.BREAK_CROP) {
				delta *= personality.cropLossMultiplier();
			}
			if (accidental) {
				delta *= ACCIDENT_FACTOR;
			} else if (isRepeat(st, action, now)) {
				delta *= cfg.repeatOffenseMultiplier;
			}
		} else {
			delta = base * personality.gainMultiplier();
			if (action == ReputationAction.TRADE) {
				delta *= personality.tradeGainMultiplier();
			} else if (action == ReputationAction.DEFEND_VILLAGER) {
				delta *= personality.defendGainMultiplier();
			}
			if (now < st.graveLockUntil) {
				delta *= GRAVE_LOCK_POSITIVE_FACTOR;
			}
		}

		int applied = (int) Math.round(delta);
		if (applied == 0 && base != 0 && !accidental) {
			applied = negative ? -1 : 1;
		}

		if (!negative && applied > 0) {
			PairKey pair = new PairKey(villageId, player);
			DailyGain g = daily.get(pair);
			if (g == null || now < g.start() || now - g.start() >= DAY_TICKS) {
				g = new DailyGain(now, 0);
			}
			int room = cfg.positiveGainCapPerDay - g.gained();
			if (room <= 0) {
				return ignored(st);
			}
			applied = Math.min(applied, room);
			daily.put(pair, new DailyGain(g.start(), g.gained() + applied));
		}

		boolean grave = !accidental && action.isGrave();

		int before = st.score;
		st.setScore(before + applied);
		int actual = st.score - before;

		if (negative) {
			if (!accidental) {
				st.offenses++;
			}
			st.lastRecoveryTime = now;
			if (grave) {
				st.graveLockUntil = Math.max(st.graveLockUntil, now + cfg.graveOffenseLockTicks);
			}
		} else if (action == ReputationAction.TRADE) {
			st.tradesCompleted++;
		} else if (action.isRelevantGoodDeed()) {
			st.goodDeeds++;
		}

		if (action.isRecorded() && actual != 0) {
			st.addHistory(new HistoryEntry(action.id(), actual, now, grave), cfg.maxHistoryEntriesPerPlayer);
		}
		return new Result(true, actual, st.score, ReputationLevel.fromScore(st.score));
	}

	/**
	 * Whether this (already counted) offense makes the village consider the player an enemy at once.
	 * Needs a witness: villagers cannot react to a crime nobody saw. Call once per counted offense.
	 */
	public boolean triggersHostility(UUID villageId, UUID player, ReputationAction action, boolean witnessed, long now) {
		ModConfig cfg = ModConfig.get();
		if (action == ReputationAction.KILL_VILLAGER) {
			return witnessed && cfg.killVillagerMakesHostile;
		}
		if (action == ReputationAction.BREAK_HAY_BALE) {
			PairKey pair = new PairKey(villageId, player);
			HayCount h = hay.get(pair);
			if (h == null || now < h.start() || now - h.start() >= DAY_TICKS) {
				h = new HayCount(now, 0);
			}
			h = new HayCount(h.start(), h.count() + 1);
			hay.put(pair, h);
			return witnessed && cfg.hayBalesForHostility > 0 && h.count() >= cfg.hayBalesForHostility;
		}
		return false;
	}

	/** Drops the player to the hostile score (never raises it) and locks passive recovery like a grave offense. */
	public void declareHostile(PlayerStanding st, long now) {
		ModConfig cfg = ModConfig.get();
		int before = st.score;
		st.setScore(Math.min(before, cfg.instantHostileScore));
		st.lastRecoveryTime = now;
		st.graveLockUntil = Math.max(st.graveLockUntil, now + cfg.graveOffenseLockTicks);
		st.addHistory(new HistoryEntry("declared_hostile", st.score - before, now, true), cfg.maxHistoryEntriesPerPlayer);
	}

	/** True when the village treats the player as someone it may attack (score at or below the rebellion threshold). */
	public static boolean isWanted(PlayerStanding st) {
		return st.score <= ModConfig.get().rebellionThreshold;
	}

	/**
	 * Initial trust from a recommendation letter. Never lifts a player to FRIEND or above:
	 * the bonus is capped just below the FRIEND threshold. Returns the points actually granted.
	 */
	public int grantTrust(PlayerStanding st, int amount, long now) {
		int cap = ReputationLevel.FRIEND.minScore() - 1;
		if (amount <= 0 || st.score >= cap) {
			return 0;
		}
		int before = st.score;
		st.setScore(Math.min(cap, before + amount));
		int actual = st.score - before;
		st.addHistory(new HistoryEntry("recommendation", actual, now, false), ModConfig.get().maxHistoryEntriesPerPlayer);
		return actual;
	}

	/** Highest score a letter can lift a player to (just below FRIEND). */
	public static int letterScoreCap() {
		return ReputationLevel.FRIEND.minScore() - 1;
	}

	/**
	 * Passive recovery of negative scores toward 0 (never above). Time spent under a grave-offense
	 * lock does not count. Safe to call at any time and as often as needed.
	 */
	public void recover(PlayerStanding st, long now) {
		ModConfig cfg = ModConfig.get();
		if (st.score >= 0) {
			st.lastRecoveryTime = now;
			return;
		}
		long start = Math.max(st.lastRecoveryTime, st.graveLockUntil);
		if (now <= start || cfg.recoveryPerInterval <= 0) {
			return;
		}
		long steps = (now - start) / cfg.recoveryIntervalTicks;
		if (steps <= 0) {
			return;
		}
		long gained = steps * cfg.recoveryPerInterval;
		st.setScore((int) Math.min(0L, st.score + gained));
		st.lastRecoveryTime = start + steps * cfg.recoveryIntervalTicks;
		if (st.score >= 0) {
			st.lastRecoveryTime = now;
		}
	}

	private static Result ignored(PlayerStanding st) {
		return new Result(false, 0, st.score, ReputationLevel.fromScore(st.score));
	}

	private static boolean isRepeat(PlayerStanding st, ReputationAction action, long now) {
		for (HistoryEntry e : st.history) {
			if (e.delta() < 0 && e.kind().equals(action.id()) && now - e.gameTime() < REPEAT_WINDOW_TICKS) {
				return true;
			}
		}
		return false;
	}
}
