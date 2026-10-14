package com.villagersociety.workers;

import com.villagersociety.config.ModConfig;
import com.villagersociety.feedback.Say;
import com.villagersociety.leader.PlayerLeaders;
import com.villagersociety.persistence.SocietyData.WorkerAssignment;
import com.villagersociety.persistence.SocietyState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InventoryCarrier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the jobs villagers received from a village leader (mine stone, chop trees, farm). Assignments are
 * persisted; progress (current target, timers) is transient.
 *  - Runs every 10 ticks, only by day (at night villagers follow their normal routine) and only for assigned,
 *    loaded villagers. Nothing happens when there are no workers.
 *  - A worker walks to the nearest target in its area, breaks it with a virtual tool, keeps the drops (marked) in
 *    its own inventory and, when it has a few stacks, walks to the nearest chest/barrel and puts them in.
 *
 * UNVERIFIED API POINTS (1.21.11): InventoryCarrier#getInventory, Level#getDayTime, WalkTarget(Vec3,float,int).
 */
public final class WorkerManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("villager_society");
	private static final long STEP_INTERVAL = 10;
	private static final long VALIDATE_INTERVAL = 200;
	private static final double REACH = 3.2;
	private static final int DEPOSIT_AT_MARKED_STACKS = 4;

	private static final class WorkerState {
		BlockPos target;
		long targetSince;
		long nextAction;
		long nextSearch;
		boolean depositing;
		BlockPos chest;
		long chestWarnUntil;
		Vec3 lastMove;
		long lastMoveTick;
		final Map<BlockPos, Long> blacklist = new HashMap<>();
	}

	private final Map<UUID, WorkerState> states = new HashMap<>();

	public void register() {
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level
				&& SocietyState.get(level.getServer()).removeWorker(entity.getUUID())) {
				states.remove(entity.getUUID());
			}
		});
	}

	public void clear() {
		states.clear();
	}

	// ------------------------------------------------------------ assignment

	/** Cancels a job and puts any collected items on the ground (they would be stuck in the villager otherwise). */
	public void unassign(ServerLevel level, SocietyState society, UUID villager) {
		states.remove(villager);
		society.removeWorker(villager);
		Entity entity = level.getEntity(villager);
		if (entity instanceof Mob mob && mob instanceof InventoryCarrier carrier) {
			SimpleContainer inventory = carrier.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (!stack.isEmpty() && WorkTag.isMarked(stack)) {
					ItemStack out = stack.copy();
					WorkTag.unmark(out);
					Block.popResource(level, mob.blockPosition(), out);
					inventory.setItem(i, ItemStack.EMPTY);
				}
			}
		}
	}

	public void releaseVillage(ServerLevel level, SocietyState society, UUID village) {
		for (WorkerAssignment assignment : society.workersOfVillage(village)) {
			unassign(level, society, assignment.villager);
		}
	}

	// ------------------------------------------------------------------ tick

	private void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		SocietyState society = SocietyState.get(server);
		if (tick % VALIDATE_INTERVAL == 0) {
			PlayerLeaders.validateAll(server, society);
			states.keySet().removeIf(id -> society.worker(id) == null);
		}
		if (society.workers().isEmpty() || tick % STEP_INTERVAL != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level.getDayTime() % 24000L >= 12000L) {
			return; // night: villagers follow their normal routine
		}
		for (WorkerAssignment assignment : new ArrayList<>(society.workers())) {
			try {
				step(level, assignment, tick);
			} catch (RuntimeException e) {
				LOGGER.warn("Worker step failed", e);
			}
		}
	}

	private void step(ServerLevel level, WorkerAssignment assignment, long tick) {
		Entity entity = level.getEntity(assignment.villager);
		if (!(entity instanceof Mob mob) || !mob.isAlive() || !(mob instanceof InventoryCarrier carrier)) {
			return; // unloaded or gone; the assignment waits (death removes it)
		}
		SimpleContainer inventory = carrier.getInventory();
		WorkerState ws = states.computeIfAbsent(assignment.villager, k -> new WorkerState());

		int marked = 0;
		int free = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				free++;
			} else if (WorkTag.isMarked(stack)) {
				marked++;
			}
		}
		if (marked >= DEPOSIT_AT_MARKED_STACKS || (marked > 0 && free <= 2)) {
			ws.depositing = true;
		}
		if (marked == 0) {
			ws.depositing = false;
		}

		if (ws.depositing) {
			deposit(level, mob, inventory, ws, assignment, tick);
		} else {
			work(level, mob, inventory, ws, assignment, tick);
		}
	}

	private void work(ServerLevel level, Mob mob, SimpleContainer inventory, WorkerState ws,
					  WorkerAssignment assignment, long tick) {
		WorkerJob job = WorkerJob.fromId(assignment.job);
		if (ws.target != null) {
			boolean timedOut = tick - ws.targetSince > 400;
			if (timedOut) {
				ws.blacklist.put(ws.target, tick + 2400); // could not reach it: skip it for a while
			}
			if (timedOut || !WorkerTargets.isValid(level, job, ws.target)) {
				ws.target = null;
			}
		}
		if (ws.target == null) {
			if (tick < ws.nextSearch) {
				return;
			}
			ws.nextSearch = tick + 40;
			ws.blacklist.values().removeIf(until -> until < tick);
			ws.target = WorkerTargets.find(level, job, assignment.center, assignment.radius,
				mob.blockPosition(), ws.blacklist.keySet());
			ws.targetSince = tick;
			if (ws.target == null) {
				return;
			}
		}
		Vec3 center = Vec3.atCenterOf(ws.target);
		if (mob.position().distanceTo(center) > REACH) {
			moveTo(ws, mob, center, 0.6f, 2, tick);
			return;
		}
		if (tick < ws.nextAction) {
			return;
		}
		mob.getLookControl().setLookAt(center.x, center.y, center.z);
		mob.swing(InteractionHand.MAIN_HAND);
		WorkerTargets.perform(level, mob, inventory, job, ws.target);
		ws.target = null;
		ws.nextAction = tick + ModConfig.get().workerActionTicks;
	}

	private void deposit(ServerLevel level, Mob mob, SimpleContainer inventory, WorkerState ws,
						 WorkerAssignment assignment, long tick) {
		if (ws.chest == null || !isStorage(level, ws.chest)) {
			ws.chest = findChest(level, mob.blockPosition());
		}
		if (ws.chest == null) {
			if (tick >= ws.chestWarnUntil) {
				ws.chestWarnUntil = tick + 1200;
				ServerPlayer owner = level.getServer().getPlayerList().getPlayer(assignment.owner);
				if (owner != null) {
					Say.bar(owner, "Um morador não achou baú ou barril com espaço por perto.");
				}
			}
			return;
		}
		Vec3 center = Vec3.atCenterOf(ws.chest);
		if (mob.position().distanceTo(center) > 3.0) {
			moveTo(ws, mob, center, 0.7f, 2, tick);
			return;
		}
		if (level.getBlockEntity(ws.chest) instanceof Container container) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (stack.isEmpty() || !WorkTag.isMarked(stack)) {
					continue;
				}
				ItemStack rest = stack.copy();
				WorkTag.unmark(rest);
				rest = ContainerInsert.insert(container, rest);
				if (rest.isEmpty()) {
					inventory.setItem(i, ItemStack.EMPTY);
				} else {
					WorkTag.mark(rest);
					inventory.setItem(i, rest);
				}
			}
			container.setChanged();
			mob.swing(InteractionHand.MAIN_HAND);
		}
		ws.depositing = false; // re-evaluated on the next step
	}

	// --------------------------------------------------------------- helpers

	private static boolean isStorage(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) && level.getBlockEntity(pos) instanceof Container;
	}

	/** Nearest chest or barrel with an empty slot, within workerChestRadius (and 8 blocks up/down). */
	private static BlockPos findChest(ServerLevel level, BlockPos from) {
		int radius = ModConfig.get().workerChestRadius;
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = from.getX() - radius; x <= from.getX() + radius; x++) {
			for (int z = from.getZ() - radius; z <= from.getZ() + radius; z++) {
				p.set(x, from.getY(), z);
				if (!level.hasChunkAt(p)) {
					continue;
				}
				for (int y = from.getY() - 8; y <= from.getY() + 8; y++) {
					p.setY(y);
					BlockState state = level.getBlockState(p);
					if (!(state.is(Blocks.CHEST) || state.is(Blocks.BARREL))) {
						continue;
					}
					double distance = p.distSqr(from);
					if (distance < bestDistance && level.getBlockEntity(p) instanceof Container container
						&& ContainerInsert.hasRoom(container)) {
						bestDistance = distance;
						best = p.immutable();
					}
				}
			}
		}
		return best;
	}

	/** Sets the vanilla walk target (the brain's own movement behavior does the pathfinding), rate-limited. */
	private static void moveTo(WorkerState ws, Mob mob, Vec3 target, float speed, int closeEnough, long tick) {
		if (ws.lastMove != null && ws.lastMove.distanceToSqr(target) < 1.0 && tick - ws.lastMoveTick < 40) {
			return;
		}
		if (mob.isSleeping()) {
			mob.stopSleeping();
		}
		mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, speed, closeEnough));
		ws.lastMove = target;
		ws.lastMoveTick = tick;
	}
}
