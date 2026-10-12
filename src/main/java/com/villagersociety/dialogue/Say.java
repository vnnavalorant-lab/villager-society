package com.villagersociety.dialogue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Minimal feedback channel. Etapa 5 replaces the fixed texts with a contextual dialogue
 * system (trust, personality, cooldowns); callers will not need to change.
 */
public final class Say {
	private Say() {
	}

	public static void toPlayer(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal("[Vila] " + text).withStyle(ChatFormatting.YELLOW));
	}
}
