package com.villagersociety.feedback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The only text the mod ever shows. Villagers never "talk". Two channels:
 *  - {@link #toPlayer}: chat, only for the recommendation-letter flow;
 *  - {@link #bar}: the action bar (short, disappears by itself), for the leader staff.
 * The same text is never repeated to the same player within 5 seconds.
 */
public final class Say {
	private record Last(String text, long millis) {
	}

	private static final long REPEAT_WINDOW_MILLIS = 5000;
	private static final Map<UUID, Last> LAST = new HashMap<>();

	private Say() {
	}

	public static void toPlayer(ServerPlayer player, String text) {
		if (allowed(player, text)) {
			player.sendSystemMessage(Component.literal("[Vila] " + text).withStyle(ChatFormatting.YELLOW));
		}
	}

	public static void bar(ServerPlayer player, String text) {
		if (allowed(player, text)) {
			player.displayClientMessage(Component.literal(text), true);
		}
	}

	private static boolean allowed(ServerPlayer player, String text) {
		long now = System.currentTimeMillis();
		Last last = LAST.get(player.getUUID());
		if (last != null && last.text().equals(text) && now - last.millis() < REPEAT_WINDOW_MILLIS) {
			return false;
		}
		if (LAST.size() > 512) {
			LAST.clear();
		}
		LAST.put(player.getUUID(), new Last(text, now));
		return true;
	}
}
