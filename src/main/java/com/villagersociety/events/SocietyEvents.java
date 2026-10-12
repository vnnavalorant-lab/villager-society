package com.villagersociety.events;

import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.reputation.ReputationAction;
import com.villagersociety.village.VillageLocator;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Registers the Fabric events that feed the reputation system. Each handler does a cheap check
 * first and only then looks up the village (cached per chunk).
 *
 * Heuristics worth knowing:
 *  - Replanting = using a villager-plantable seed on farmland with air above, inside a village.
 *  - Community containers = village POI blocks that hold items (e.g. barrels).
 *  - Creative/spectator players are ignored unless debugCommands is true (so you can test).
 *
 * UNVERIFIED API POINTS (1.21.11): Fabric callback signatures (PlayerBlockBreakEvents.AFTER,
 * AttackEntityCallback, UseBlockCallback, ServerLivingEntityEvents.AFTER_DEATH) and InteractionResult.
 */
public final class SocietyEvents {
	private SocietyEvents() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp && !ignored(sp)) {
				onBlockBreak(level, sp, pos, state);
			}
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp
				&& entity.getType() == EntityType.VILLAGER && !ignored(sp)) {
				report(level, sp, entity.blockPosition(), ReputationAction.ATTACK_VILLAGER);
			}
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Entity killer = source.getEntity();
			if (!(killer instanceof ServerPlayer sp) || !(entity.level() instanceof ServerLevel level) || ignored(sp)) {
				return;
			}
			onDeath(level, sp, entity);
		});

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp
				&& hand == InteractionHand.MAIN_HAND && !ignored(sp)) {
				onUseBlock(level, sp, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			GossipScheduler.tick(server.getTickCount());
			ContainerWatcher.tick(server);
		});
	}

	public static void clearTransientState() {
		GossipScheduler.clear();
		ContainerWatcher.clear();
	}

	private static boolean ignored(ServerPlayer player) {
		return !ModConfig.get().debugCommands && (player.isCreative() || player.isSpectator());
	}

	private static void onBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		Optional<ReputationAction> action = BlockClassifier.classifyBreak(state);
		if (action.isPresent()) {
			report(level, player, pos, action.get());
		}
	}

	private static void onDeath(ServerLevel level, ServerPlayer player, LivingEntity victim) {
		if (victim.getType() == EntityType.VILLAGER) {
			report(level, player, victim.blockPosition(), ReputationAction.KILL_VILLAGER);
		} else if (victim instanceof Enemy) {
			boolean villagerNearby = !level.getEntitiesOfClass(LivingEntity.class,
				victim.getBoundingBox().inflate(12.0),
				e -> e.getType() == EntityType.VILLAGER && e.isAlive()).isEmpty();
			report(level, player, victim.blockPosition(),
				villagerNearby ? ReputationAction.DEFEND_VILLAGER : ReputationAction.KILL_MONSTER);
		}
	}

	private static void onUseBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
		BlockState clicked = level.getBlockState(pos);
		ItemStack held = player.getMainHandItem();

		if (held.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) && clicked.is(Blocks.FARMLAND)
			&& level.getBlockState(pos.above()).isAir()) {
			report(level, player, pos, ReputationAction.REPLANT_CROP);
			return;
		}
		if (BlockClassifier.isCommunityContainer(level, pos, clicked)) {
			SocietyState state = SocietyState.get(level.getServer());
			VillageData village = VillageLocator.locate(level, pos, state);
			if (village != null) {
				ContainerWatcher.begin(level, player, village.id, pos);
			}
		}
	}

	private static void report(ServerLevel level, ServerPlayer player, BlockPos pos, ReputationAction action) {
		SocietyState state = SocietyState.get(level.getServer());
		VillageData village = VillageLocator.locate(level, pos, state);
		if (village != null) {
			InfractionService.report(level, player, state, village, action, pos, false);
		}
	}
}
