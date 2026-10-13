package com.villagersociety.rebellion;

import com.villagersociety.config.ModConfig;

/**
 * Pure phase transition rules (no Minecraft types), driven by reputation thresholds, timers and
 * where the player is. Includes hysteresis so a score hovering at a threshold does not flap, and
 * hard limits (pursuit time/distance, total duration) so a rebellion can never run forever.
 */
public final class RebellionStateMachine {
	public static final long SUSPICION_MIN = 40;
	public static final long WARNING_MIN = 100;
	public static final long MOBILIZATION_MIN = 100;
	public static final long MOBILIZATION_MAX = 600;
	public static final long RECOVERY_TICKS = 600;
	public static final long LEAVE_GRACE = 400;
	public static final int HYSTERESIS = 5;

	private RebellionStateMachine() {
	}

	public record Context(RebellionPhase current, long now, long phaseStart, long pursuitStart, long startedAt,
						  int score, boolean playerValid, boolean playerInArea, double distFromCenter,
						  int participating) {
	}

	public static RebellionPhase next(Context c) {
		ModConfig cfg = ModConfig.get();
		long elapsed = c.now() - c.phaseStart();
		RebellionPhase cur = c.current();

		if (cur == RebellionPhase.RECOVERY) {
			return elapsed >= RECOVERY_TICKS ? RebellionPhase.NONE : RebellionPhase.RECOVERY;
		}
		if (!c.playerValid()) {
			return cur == RebellionPhase.NONE ? RebellionPhase.NONE : RebellionPhase.RECOVERY;
		}
		long maxTotal = (long) cfg.pursuitMaxDurationTicks * 4;

		switch (cur) {
			case NONE:
				return (c.score() <= cfg.suspicionThreshold && c.playerInArea())
					? RebellionPhase.SUSPICION : RebellionPhase.NONE;

			case SUSPICION:
				if (c.score() > cfg.suspicionThreshold + HYSTERESIS) {
					return RebellionPhase.NONE;
				}
				if (!c.playerInArea() && elapsed >= LEAVE_GRACE) {
					return RebellionPhase.NONE;
				}
				if (c.score() <= cfg.warningThreshold && elapsed >= SUSPICION_MIN) {
					return RebellionPhase.WARNING;
				}
				return cur;

			case WARNING:
				if (c.score() > cfg.warningThreshold + HYSTERESIS) {
					return RebellionPhase.SUSPICION;
				}
				if (!c.playerInArea() && elapsed >= LEAVE_GRACE) {
					return RebellionPhase.NONE;
				}
				if (c.score() <= cfg.mobilizationThreshold && elapsed >= WARNING_MIN) {
					return RebellionPhase.MOBILIZATION;
				}
				return cur;

			case MOBILIZATION:
				if (c.score() > cfg.mobilizationThreshold + HYSTERESIS) {
					return RebellionPhase.WARNING;
				}
				if (elapsed >= MOBILIZATION_MIN) {
					if (c.participating() < cfg.rebellionMinParticipants) {
						return RebellionPhase.RECOVERY;
					}
					if (c.score() <= cfg.rebellionThreshold) {
						return RebellionPhase.REBELLION;
					}
				}
				if (elapsed >= MOBILIZATION_MAX || (!c.playerInArea() && elapsed >= LEAVE_GRACE)) {
					return RebellionPhase.RECOVERY;
				}
				return cur;

			case REBELLION:
				if (c.participating() < 1 || c.now() - c.startedAt() > maxTotal) {
					return RebellionPhase.RECOVERY;
				}
				return c.playerInArea() ? cur : RebellionPhase.PURSUIT;

			case PURSUIT:
				if (c.participating() < 1 || c.now() - c.startedAt() > maxTotal) {
					return RebellionPhase.RECOVERY;
				}
				if (c.playerInArea()) {
					return RebellionPhase.REBELLION;
				}
				if (c.distFromCenter() > cfg.pursuitMaxDistance
					|| c.now() - c.pursuitStart() > cfg.pursuitMaxDurationTicks) {
					return RebellionPhase.RECOVERY;
				}
				return cur;

			default:
				return cur;
		}
	}
}
