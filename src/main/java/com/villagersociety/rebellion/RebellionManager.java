package com.villagersociety.rebellion;

import com.villagersociety.VillagerSociety;
import com.villagersociety.ai.Armament;
import com.villagersociety.ai.Tactics;
import com.villagersociety.config.ModConfig;
import com.villagersociety.events.SocietyEvents;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.rebellion.RoleAssigner.Candidate;
import com.villagersociety.village.VillageLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the six-phase rebellion. Cost control:
 *  - hostile players are looked up every 40 ticks (online players only, village lookup is cached);
 *  - active rebellions are updated every {@code aiUpdateIntervalTicks};
 *  - villagers are queried only on phase changes and every 100 ticks; they are addressed by UUID and
 *    checked for being alive/loaded before every action;
 *  - movement orders are rate-limited per villager.
 * Only the player is ever attacked, never another villager.
 *
 * UNVERIFIED API POINTS (1.21.11): WalkTarget(Vec3,float,int), MemoryModuleType.WALK_TARGET, Brain#setMemory,
 * LookControl#setLookAt(Entity), Entity#hurtServer(ServerLevel, DamageSource, float),
 * Level#damageSources().mobAttack, PoiTypes.MEETING, SoundEvents.BELL_BLOCK.
 */
public final class RebellionManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("villager_society");
	private static final long SCAN_INTERVAL = 40;
	private static final long REFRESH_INTERVAL = 100;

	private record Key(UUID village, UUID player) {
	}

	private final Map<Key, Rebellion> active = new HashMap<>();
	private final Map<Key, Long> cooldownUntil = new HashMap<>();

	// ------------------------------------------------------------------ queries

	public RebellionPhase phaseOf(UUID village, UUID player) {
		Rebellion r = active.get(new Key(village, player));
		return r == null ? RebellionPhase.NONE : r.phase;
	}

	/** True while this village is organizing or fighting against this player. */
	public boolean isTargeting(UUID village, UUID player) {
		return phaseOf(village, player).isActiveAgainstPlayer();
	}

	public boolean isParticipant(UUID entity) {
		for (Rebellion r : active.values()) {
			if (r.participants.containsKey(entity)) {
				return true;
			}
		}
		return false;
	}

	public void clear() {
		active.clear();
		cooldownUntil.clear();
	}

	/** Temporary combat state is not restored after a restart: clear the stale label on every village. */
	public void resetPersistedLabels(SocietyState state) {
		boolean changed = false;
		for (VillageData v : state.villages()) {
			if (!"NONE".equals(v.rebellionState)) {
				v.rebellionState = "NONE";
				changed = true;
			}
		}
		if (changed) {
			state.setDirty();
		}
	}

	// --------------------------------------------------------------------- tick

	public void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		ModConfig cfg = ModConfig.get();
		if (tick % SCAN_INTERVAL == 0) {
			scanPlayers(server, tick);
		}
		if (active.isEmpty() || tick % cfg.aiUpdateIntervalTicks != 0) {
			return;
		}
		for (Rebellion r : new ArrayList<>(active.values())) {
			try {
				update(server, r, tick);
			} catch (RuntimeException e) {
				LOGGER.warn("Rebellion update failed; ending it safely", e);
				finish(SocietyState.get(server), r, tick, true);
			}
		}
	}

	private void scanPlayers(MinecraftServer server, long tick) {
		SocietyState state = SocietyState.get(server);
		ModConfig cfg = ModConfig.get();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.isAlive() || SocietyEvents.isIgnored(p) || !(p.level() instanceof ServerLevel level)) {
				continue;
			}
			VillageData v = VillageLocator.locate(level, p.blockPosition(), state);
			if (v == null) {
				continue;
			}
			Key key = new Key(v.id, p.getUUID());
			Long cd = cooldownUntil.get(key);
			if (active.containsKey(key) || (cd != null && tick < cd)) {
				continue;
			}
			PlayerStanding st = v.standings.get(p.getUUID());
			if (st == null) {
				continue;
			}
			VillagerSociety.REPUTATION.recover(st, level.getGameTime());
			if (st.score <= cfg.suspicionThreshold) {
				active.put(key, new Rebellion(level, v.id, p.getUUID(), tick));
			}
		}
	}

	// ------------------------------------------------------------------- update

	private void update(MinecraftServer server, Rebellion r, long tick) {
		SocietyState state = SocietyState.get(server);
		VillageData v = state.village(r.village);
		if (v == null) {
			finish(state, r, tick, false);
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(r.player);
		boolean valid = player != null && player.isAlive() && player.level() == r.level
			&& !SocietyEvents.isIgnored(player);

		int score = 0;
		PlayerStanding st = v.standings.get(r.player);
		if (st != null) {
			VillagerSociety.REPUTATION.recover(st, r.level.getGameTime());
			score = st.score;
		}
		double cx = v.center.getX() + 0.5;
		double cz = v.center.getZ() + 0.5;
		double dist = valid ? Math.hypot(player.getX() - cx, player.getZ() - cz) : Double.MAX_VALUE;
		boolean inArea = valid && dist <= v.radius;

		if (r.phase.ordinal() >= RebellionPhase.MOBILIZATION.ordinal() && r.phase != RebellionPhase.RECOVERY) {
			prune(r);
			if (valid && r.phase != RebellionPhase.PURSUIT && tick - r.lastRefresh >= REFRESH_INTERVAL) {
				refresh(r, v, player, tick);
			}
		}

		RebellionStateMachine.Context ctx = new RebellionStateMachine.Context(r.phase, tick, r.phaseStart,
			r.pursuitStart, r.startedAt, score, valid, inArea, dist, r.participating());
		RebellionPhase next = RebellionStateMachine.next(ctx);
		if (next != r.phase) {
			RebellionPhase from = r.phase;
			transition(state, r, v, player, from, next, tick);
			if (next == RebellionPhase.NONE) {
				finish(state, r, tick, from == RebellionPhase.RECOVERY || from.ordinal() >= RebellionPhase.WARNING.ordinal());
				return;
			}
		}
		if (valid) {
			behave(r, v, player, tick);
		}
	}

	private void transition(SocietyState state, Rebellion r, VillageData v, ServerPlayer player,
							RebellionPhase from, RebellionPhase to, long tick) {
		r.phase = to;
		r.phaseStart = tick;
		if (to == RebellionPhase.PURSUIT) {
			r.pursuitStart = tick;
		}
		String label = to.name();
		if (!label.equals(v.rebellionState)) {
			v.rebellionState = label;
			state.setDirty();
		}
		switch (to) {
			case SUSPICION, WARNING -> grumble(r, player);
			case MOBILIZATION -> {
				if (player != null) {
					refresh(r, v, player, tick);
				}
				r.meeting = meetingPoint(r.level, v);
				ringBell(r);
			}
			case REBELLION -> {
				armCombatants(r);
				grumble(r, player);
			}
			case PURSUIT -> grumble(r, player);
			case RECOVERY -> {
				disarmAll(r);
				r.participants.clear();
			}
			default -> {
			}
		}
	}

	/** Wordless reaction: the vanilla villager "no" grunt next to the player. */
	private void grumble(Rebellion r, ServerPlayer player) {
		if (player != null) {
			r.level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
		}
	}

	// ---------------------------------------------------------------- behaviors

	private void behave(Rebellion r, VillageData v, ServerPlayer player, long tick) {
		switch (r.phase) {
			case SUSPICION -> watchPlayer(r, player);
			case WARNING -> approach(r, player, tick);
			case MOBILIZATION -> gather(r, tick);
			case REBELLION, PURSUIT -> fight(r, v, player, tick);
			default -> {
			}
		}
	}

	/** Suspicion: the nearest villagers stop what they are doing and look at the player. */
	private void watchPlayer(Rebellion r, ServerPlayer player) {
		for (Mob mob : nearestVillagers(r.level, player, ModConfig.get().witnessDistance * 1.5, 3)) {
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(player);
		}
	}

	/** Warning: one villager confronts the player, a few others come closer to watch. */
	private void approach(Rebellion r, ServerPlayer player, long tick) {
		List<Mob> near = nearestVillagers(r.level, player, 24, 4);
		for (int i = 0; i < near.size(); i++) {
			Mob mob = near.get(i);
			mob.getLookControl().setLookAt(player);
			moveTo(r, mob, player.position(), 0.7f, i == 0 ? 3 : 6, tick);
		}
	}

	/** Mobilization: participating villagers walk to the bell (or the village center). */
	private void gather(Rebellion r, long tick) {
		if (r.meeting == null) {
			return;
		}
		for (Map.Entry<UUID, Role> e : r.participants.entrySet()) {
			Mob mob = resolve(r, e.getKey());
			if (mob != null && e.getValue().isParticipating()) {
				moveTo(r, mob, r.meeting, 0.8f, 3, tick);
			}
		}
	}

	private void fight(Rebellion r, VillageData v, ServerPlayer player, long tick) {
		ModConfig cfg = ModConfig.get();
		Vec3 target = player.position();
		float speed = r.phase == RebellionPhase.PURSUIT ? 1.0f : 0.9f;

		List<UUID> ids = new ArrayList<>(r.participants.keySet());
		Collections.sort(ids);

		Mob baby = null;
		for (UUID id : ids) {
			Mob m = resolve(r, id);
			if (m != null && m.isBaby()) {
				baby = m;
				break;
			}
		}

		int slotCount = 0;
		for (UUID id : ids) {
			Role role = r.participants.get(id);
			if (role == Role.DEFENDER || role == Role.FLANKER) {
				slotCount++;
			}
		}
		double rotation = Math.toRadians(player.getYRot()) + Math.PI / 2;
		int slot = 0;
		Map<UUID, Double> strikers = new HashMap<>();

		for (UUID id : ids) {
			Mob mob = resolve(r, id);
			if (mob == null) {
				continue;
			}
			Role role = r.participants.get(id);
			double distToPlayer = mob.distanceTo(player);
			if (role == Role.PROTECTOR && baby == null) {
				role = Role.DEFENDER;
			}
			switch (role) {
				case FLEEING -> {
					if (distToPlayer < 24) {
						Tactics.Pt p = Tactics.fleePoint(target.x, target.z, mob.getX(), mob.getZ(), 14);
						moveTo(r, mob, new Vec3(p.x(), mob.getY(), p.z()), 1.0f, 1, tick);
					}
				}
				case ALERTER -> {
					if (r.meeting != null) {
						moveTo(r, mob, r.meeting, 1.0f, 2, tick);
						if (!r.belled && mob.position().distanceTo(r.meeting) < 5) {
							ringBell(r);
						}
					}
				}
				case PROTECTOR -> {
					Tactics.Pt p = Tactics.protectorPoint(baby.getX(), baby.getZ(), target.x, target.z, 2.0);
					moveTo(r, mob, new Vec3(p.x(), mob.getY(), p.z()), 1.0f, 1, tick);
					mob.getLookControl().setLookAt(player);
					strikers.put(id, distToPlayer);
				}
				case FLANKER -> {
					if (distToPlayer > 4) {
						Tactics.Pt p = Tactics.slotPoint(target.x, target.z, slot, slotCount, 2.5, rotation);
						moveTo(r, mob, new Vec3(p.x(), mob.getY(), p.z()), speed, 1, tick);
					} else {
						moveTo(r, mob, target, speed, 1, tick);
					}
					slot++;
					mob.getLookControl().setLookAt(player);
					strikers.put(id, distToPlayer);
				}
				default -> {
					moveTo(r, mob, target, speed, 1, tick);
					slot++;
					mob.getLookControl().setLookAt(player);
					strikers.put(id, distToPlayer);
				}
			}
		}

		Set<UUID> allowed = Tactics.selectAttackers(strikers, cfg.maxSimultaneousAttackers);
		double range = cfg.meleeRange + 0.4;
		for (UUID id : allowed) {
			Mob mob = resolve(r, id);
			if (mob == null || tick < r.nextAttack.getOrDefault(id, 0L)) {
				continue;
			}
			if (mob.distanceToSqr(player) <= range * range && mob.hasLineOfSight(player)) {
				mob.swing(InteractionHand.MAIN_HAND);
				player.hurtServer(r.level, r.level.damageSources().mobAttack(mob), (float) cfg.meleeDamage);
				r.nextAttack.put(id, tick + cfg.meleeCooldownTicks);
			}
		}
	}

	// ------------------------------------------------------------- participants

	private void refresh(Rebellion r, VillageData v, ServerPlayer player, long tick) {
		ModConfig cfg = ModConfig.get();
		r.lastRefresh = tick;
		List<LivingEntity> villagers = r.level.getEntitiesOfClass(LivingEntity.class,
			new AABB(v.center).inflate(v.radius),
			e -> e.getType() == EntityType.VILLAGER && e.isAlive());
		List<Candidate> candidates = new ArrayList<>();
		for (LivingEntity e : villagers) {
			candidates.add(new Candidate(e.getUUID(), e.isBaby(), e.distanceTo(player)));
		}
		Map<UUID, Role> roles = RoleAssigner.assign(candidates, cfg.rebellionMinParticipants,
			cfg.rebellionMaxParticipants, cfg.villageInfluenceRadius);

		for (Map.Entry<UUID, Role> old : r.participants.entrySet()) {
			Role now = roles.get(old.getKey());
			if (now == null || !now.isParticipating()) {
				Mob mob = resolve(r, old.getKey());
				if (mob != null) {
					Armament.disarm(mob);
				}
			}
		}
		r.participants.clear();
		r.participants.putAll(roles);
		if (r.phase == RebellionPhase.REBELLION || r.phase == RebellionPhase.PURSUIT) {
			armCombatants(r);
		}
	}

	private void prune(Rebellion r) {
		r.participants.keySet().removeIf(id -> {
			boolean gone = resolve(r, id) == null;
			if (gone) {
				r.nextAttack.remove(id);
				r.lastTarget.remove(id);
				r.lastTargetTick.remove(id);
			}
			return gone;
		});
	}

	private void armCombatants(Rebellion r) {
		for (Map.Entry<UUID, Role> e : r.participants.entrySet()) {
			Role role = e.getValue();
			if (role == Role.DEFENDER || role == Role.FLANKER || role == Role.PROTECTOR) {
				Mob mob = resolve(r, e.getKey());
				if (mob != null) {
					Armament.arm(mob);
				}
			}
		}
	}

	private void disarmAll(Rebellion r) {
		for (UUID id : r.participants.keySet()) {
			Mob mob = resolve(r, id);
			if (mob != null) {
				Armament.disarm(mob);
			}
		}
	}

	private void finish(SocietyState state, Rebellion r, long tick, boolean cooldown) {
		disarmAll(r);
		r.participants.clear();
		Key key = new Key(r.village, r.player);
		active.remove(key);
		VillageData v = state.village(r.village);
		if (v != null && !"NONE".equals(v.rebellionState)) {
			v.rebellionState = "NONE";
			state.setDirty();
		}
		if (cooldown) {
			cooldownUntil.put(key, tick + ModConfig.get().rebellionCooldownTicks);
		}
		if (cooldownUntil.size() > 1024) {
			cooldownUntil.clear();
		}
	}

	// ------------------------------------------------------------------ helpers

	private static Mob resolve(Rebellion r, UUID id) {
		Entity e = r.level.getEntity(id);
		return (e instanceof Mob m && m.isAlive()) ? m : null;
	}

	private static List<Mob> nearestVillagers(ServerLevel level, ServerPlayer player, double radius, int limit) {
		List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class,
			player.getBoundingBox().inflate(radius),
			e -> e.getType() == EntityType.VILLAGER && e.isAlive() && !e.isBaby() && !e.isSleeping());
		found.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		List<Mob> out = new ArrayList<>();
		for (LivingEntity e : found) {
			if (e instanceof Mob m && out.size() < limit) {
				out.add(m);
			}
		}
		return out;
	}

	/** Sets the vanilla walk target (the brain's own movement behavior does the pathfinding), rate-limited. */
	private static void moveTo(Rebellion r, Mob mob, Vec3 target, float speed, int closeEnough, long tick) {
		UUID id = mob.getUUID();
		Vec3 last = r.lastTarget.get(id);
		Long lastTick = r.lastTargetTick.get(id);
		if (last != null && lastTick != null && last.distanceToSqr(target) < 1.0 && tick - lastTick < 40) {
			return;
		}
		if (mob.isSleeping()) {
			mob.stopSleeping();
		}
		mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, speed, closeEnough));
		r.lastTarget.put(id, target);
		r.lastTargetTick.put(id, tick);
	}

	private static Vec3 meetingPoint(ServerLevel level, VillageData v) {
		Optional<Vec3> bell = level.getPoiManager()
			.getInRange(holder -> holder.is(PoiTypes.MEETING), v.center, v.radius, PoiManager.Occupancy.ANY)
			.findFirst()
			.map(record -> Vec3.atBottomCenterOf(record.getPos()));
		return bell.orElse(Vec3.atBottomCenterOf(v.center));
	}

	private static void ringBell(Rebellion r) {
		if (r.meeting == null) {
			return;
		}
		r.belled = true;
		r.level.playSound(null, BlockPos.containing(r.meeting), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 1.0f);
	}
}
