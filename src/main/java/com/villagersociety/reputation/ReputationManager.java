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
	private static final double ACCIDENT_FACTOR = 0.25;
	private static final double GRAVE_LOCK_POSITIVE_FACTOR = 0.5;

	private record DedupKey(UUID village, UUID player, ReputationAction action) {
	}

	public record Result(boolean counted, int delta, int newScore, ReputationLevel level) {
	}

	/** Transient anti-duplication timestamps; safe to lose on restart. */
	private final Map<DedupKey, Long> lastCounted = new HashMap<>();

	public Result apply(UUID villageId, UUID player, PlayerStanding st, Personality personality,
						ReputationAction action, boolean accidental, long now) {
		ModConfig cfg = ModConfig.get();
		recover(st, now);

		DedupKey key = new DedupKey(villageId, player, action);
		Long last = lastCounted.get(key);
		if (action.dedupeTicks() > 0 && last != null && now >= last && now - last < action.dedupeTicks()) {
			return new Result(false, 0, st.score, ReputationLevel.fromScore(st.score));
		}
		if (lastCounted.size() > 4096) {
			lastCounted.clear();
		}
		lastCounted.put(key, now);

		int base = action.base(cfg);
		boolean negative = base < 0;
		double delta;
		if (negative) {
			delta = base * cfg.punishmentIntensity * personality.lossMultiplier();
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

	private static boolean isRepeat(PlayerStanding st, ReputationAction action, long now) {
		for (HistoryEntry e : st.history) {
			if (e.delta() < 0 && e.kind().equals(action.id()) && now - e.gameTime() < REPEAT_WINDOW_TICKS) {
				return true;
			}
		}
		return false;
	}
}
