package com.villagersociety.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.events.InfractionService;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.reputation.ReputationAction;
import com.villagersociety.reputation.ReputationLevel;
import com.villagersociety.reputation.ReputationManager;
import com.villagersociety.rebellion.RebellionPhase;
import com.villagersociety.village.LeaderService;
import com.villagersociety.village.Personality;
import com.villagersociety.village.VillageLocator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Commands exist only with debugCommands=true. The reputation HUD panel replaces the old /villagersociety info.
 * /villagersociety debug info           -> village, leader, standing, phase at your position
 * /villagersociety debug <action>       -> applies a reputation action to yourself (debugCommands=true only)
 * /villagersociety debug set <score>    -> sets your score in the current village (debugCommands=true only)
 * /villagersociety debug enemy          -> score -100 with a grave-offense lock (no passive recovery)
 * /villagersociety debug maxout         -> score 90 + enough trades/good deeds for a letter (debugCommands=true only)
 *
 * UNVERIFIED API POINTS (1.21.11): CommandSourceStack#getPlayer/getLevel/getServer/sendSuccess/sendFailure,
 * CommandRegistrationCallback lambda signature.
 */
public final class SocietyCommands {
	private SocietyCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			if (!ModConfig.get().debugCommands) {
				return; // normal play uses the HUD panel; no commands are registered
			}
			LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug")
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())));
			for (ReputationAction action : ReputationAction.values()) {
				debug.then(Commands.literal(action.id()).executes(ctx -> debugAction(ctx.getSource(), action)));
			}
			debug.then(Commands.literal("maxout").executes(ctx -> maxOut(ctx.getSource())));
			debug.then(Commands.literal("enemy").executes(ctx -> makeEnemy(ctx.getSource())));
			debug.then(Commands.literal("set").then(Commands.argument("score", IntegerArgumentType.integer(-100, 100))
				.executes(ctx -> setScore(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "score")))));
			dispatcher.register(Commands.literal("villagersociety").then(debug));
		});
	}

	private static int info(CommandSourceStack src) {
		ServerPlayer player = requirePlayer(src);
		if (player == null) {
			return 0;
		}
		ServerLevel level = src.getLevel();
		SocietyState state = SocietyState.get(src.getServer());
		VillageData village = VillageLocator.locate(level, player.blockPosition(), state);
		if (village == null) {
			reply(src, "Você não está em uma vila registrada.");
			return 1;
		}
		PlayerStanding st = village.standing(player.getUUID());
		VillagerSociety.REPUTATION.recover(st, level.getGameTime());
		state.setDirty();

		Personality personality = Personality.fromId(village.personality);
		ReputationLevel lvl = ReputationLevel.fromScore(st.score);
		reply(src, String.format(Locale.ROOT, "Vila %s | personalidade: %s | centro: %d %d %d | raio: %d",
			village.id.toString().substring(0, 8), personality.id(),
			village.center.getX(), village.center.getY(), village.center.getZ(), village.radius));
		reply(src, String.format(Locale.ROOT, "Sua reputação: %d (%s) | trocas: %d | boas ações: %d | infrações: %d",
			st.score, lvl.displayName(), st.tradesCompleted, st.goodDeeds, st.offenses));

		if (ReputationManager.isWanted(st)) {
			reply(src, "Status: PROCURADO. Os moradores desta vila consideram você um inimigo.");
		}
		RebellionPhase phase = VillagerSociety.REBELLIONS.phaseOf(village.id, player.getUUID());
		if (phase != RebellionPhase.NONE) {
			reply(src, "Fase da vila contra você: " + phase.name());
		}

		Optional<UUID> leader = LeaderService.ensureLeader(level, village, state);
		if (leader.isPresent()) {
			Entity e = level.getEntity(leader.get());
			reply(src, e == null
				? "Representante: fora de alcance (não carregado)."
				: String.format(Locale.ROOT, "Representante: a %d blocos de você.", (int) Math.sqrt(player.distanceToSqr(e))));
		} else {
			reply(src, "Representante: ainda não definido (nenhum morador adulto carregado).");
		}
		return 1;
	}

	private static int debugAction(CommandSourceStack src, ReputationAction action) {
		Context c = context(src);
		if (c == null) {
			return 0;
		}
		ReputationManager.Result result = InfractionService.report(c.level, c.player, c.state, c.village,
			action, c.player.blockPosition(), false);
		if (!result.counted()) {
			reply(src, "Ação ignorada (limite de tempo ou teto diário).");
		} else {
			reply(src, String.format(Locale.ROOT, "%s: %+d -> reputação %d (%s)",
				action.id(), result.delta(), result.newScore(), result.level().displayName()));
		}
		return 1;
	}

	private static int setScore(CommandSourceStack src, int score) {
		Context c = context(src);
		if (c == null) {
			return 0;
		}
		PlayerStanding st = c.village.standing(c.player.getUUID());
		st.setScore(score);
		st.lastRecoveryTime = c.level.getGameTime();
		c.state.setDirty();
		reply(src, "Reputação definida para " + st.score + ".");
		return 1;
	}

	private static int makeEnemy(CommandSourceStack src) {
		Context c = context(src);
		if (c == null) {
			return 0;
		}
		long now = c.level.getGameTime();
		PlayerStanding st = c.village.standing(c.player.getUUID());
		st.setScore(-100);
		st.offenses = Math.max(st.offenses, 1);
		st.lastRecoveryTime = now;
		// Same lock a real grave offense sets, so the score does not recover on its own while you test.
		st.graveLockUntil = now + ModConfig.get().graveOffenseLockTicks;
		c.state.setDirty();
		reply(src, "Reputação -100 (Inimigo da comunidade) nesta vila. Use debug set <n> ou debug maxout para reverter.");
		return 1;
	}

	private static int maxOut(CommandSourceStack src) {
		Context c = context(src);
		if (c == null) {
			return 0;
		}
		ModConfig cfg = ModConfig.get();
		PlayerStanding st = c.village.standing(c.player.getUUID());
		st.setScore(90);
		st.tradesCompleted = Math.max(st.tradesCompleted, cfg.letterMinTrades);
		st.goodDeeds = Math.max(st.goodDeeds, cfg.letterMinGoodDeeds);
		st.graveLockUntil = 0;
		c.state.setDirty();
		reply(src, "Reputação 90 com trocas e boas ações suficientes. Agora use shift + botão direito no representante.");
		return 1;
	}

	private record Context(ServerPlayer player, ServerLevel level, SocietyState state, VillageData village) {
	}

	private static Context context(CommandSourceStack src) {
		ServerPlayer player = requirePlayer(src);
		if (player == null) {
			return null;
		}
		ServerLevel level = src.getLevel();
		SocietyState state = SocietyState.get(src.getServer());
		VillageData village = VillageLocator.locate(level, player.blockPosition(), state);
		if (village == null) {
			src.sendFailure(Component.literal("Você não está em uma vila registrada."));
			return null;
		}
		return new Context(player, level, state, village);
	}

	private static ServerPlayer requirePlayer(CommandSourceStack src) {
		ServerPlayer player = src.getPlayer();
		if (player == null) {
			src.sendFailure(Component.literal("Este comando só pode ser usado por um jogador."));
		}
		return player;
	}

	private static void reply(CommandSourceStack src, String text) {
		src.sendSuccess(() -> Component.literal(text), false);
	}
}
