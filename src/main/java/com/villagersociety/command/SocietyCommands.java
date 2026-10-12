package com.villagersociety.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.persistence.SocietyData.PlayerStanding;
import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.reputation.ReputationAction;
import com.villagersociety.reputation.ReputationLevel;
import com.villagersociety.reputation.ReputationManager;
import com.villagersociety.village.Personality;
import com.villagersociety.village.VillageLocator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * /villagersociety info            -> village and your standing at your position
 * /villagersociety debug <action>  -> applies a reputation action to yourself (only if debugCommands=true)
 *
 * UNVERIFIED API POINTS (1.21.11): CommandSourceStack#getPlayer/getLevel/getServer/sendSuccess/sendFailure,
 * CommandRegistrationCallback lambda signature.
 */
public final class SocietyCommands {
	private SocietyCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("villagersociety")
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())));

			if (ModConfig.get().debugCommands) {
				LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug");
				for (ReputationAction action : ReputationAction.values()) {
					debug.then(Commands.literal(action.id()).executes(ctx -> debug(ctx.getSource(), action)));
				}
				root.then(debug);
			}
			dispatcher.register(root);
		});
	}

	private static int info(CommandSourceStack src) {
		ServerPlayer player = src.getPlayer();
		if (player == null) {
			src.sendFailure(Component.literal("Este comando só pode ser usado por um jogador."));
			return 0;
		}
		ServerLevel level = src.getLevel();
		SocietyState state = SocietyState.get(src.getServer());
		VillageData village = VillageLocator.locate(level, player.blockPosition(), state);
		if (village == null) {
			reply(src, "Você não está em uma vila registrada.");
			return 1;
		}
		PlayerStanding st = state.village(village.id).standing(player.getUUID());
		VillagerSociety.REPUTATION.recover(st, level.getGameTime());
		state.setDirty();

		Personality personality = Personality.fromId(village.personality);
		ReputationLevel lvl = ReputationLevel.fromScore(st.score);
		reply(src, String.format(Locale.ROOT, "Vila %s | personalidade: %s | centro: %d %d %d | raio: %d",
			village.id.toString().substring(0, 8), personality.id(),
			village.center.getX(), village.center.getY(), village.center.getZ(), village.radius));
		reply(src, String.format(Locale.ROOT, "Sua reputação: %d (%s) | trocas: %d | boas ações: %d | infrações: %d",
			st.score, lvl.displayName(), st.tradesCompleted, st.goodDeeds, st.offenses));
		return 1;
	}

	private static int debug(CommandSourceStack src, ReputationAction action) {
		ServerPlayer player = src.getPlayer();
		if (player == null) {
			src.sendFailure(Component.literal("Este comando só pode ser usado por um jogador."));
			return 0;
		}
		ServerLevel level = src.getLevel();
		SocietyState state = SocietyState.get(src.getServer());
		VillageData village = VillageLocator.locate(level, player.blockPosition(), state);
		if (village == null) {
			src.sendFailure(Component.literal("Você não está em uma vila registrada."));
			return 0;
		}
		PlayerStanding st = village.standing(player.getUUID());
		ReputationManager.Result result = VillagerSociety.REPUTATION.apply(village.id, player.getUUID(), st,
			Personality.fromId(village.personality), action, false, level.getGameTime());
		state.setDirty();

		if (!result.counted()) {
			reply(src, "Ação ignorada (já contabilizada há pouco tempo).");
		} else {
			reply(src, String.format(Locale.ROOT, "%s: %+d -> reputação %d (%s)",
				action.id(), result.delta(), result.newScore(), result.level().displayName()));
		}
		return 1;
	}

	private static void reply(CommandSourceStack src, String text) {
		src.sendSuccess(() -> Component.literal(text), false);
	}
}
