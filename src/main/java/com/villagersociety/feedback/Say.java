package com.villagersociety.feedback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Plain system feedback for the recommendation-letter flow only (why a letter was refused, that it was accepted).
 * Villagers never "talk": they like or dislike you through reputation, trades, prices and behavior.
 */
public final class Say {
	private Say() {
	}

	public static void toPlayer(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal("[Vila] " + text).withStyle(ChatFormatting.YELLOW));
	}
}
