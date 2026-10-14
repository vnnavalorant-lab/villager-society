package com.villagersociety.client;

import com.villagersociety.VillagerSociety;
import com.villagersociety.config.ModConfig;
import com.villagersociety.network.ReputationPayload;
import com.villagersociety.rebellion.RebellionPhase;
import com.villagersociety.reputation.ReputationLevel;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Small text on a translucent bar, centered above the hotbar, visible only while you are inside a village.
 * Green when the village likes you, red when it dislikes you, white when it is neutral.
 *
 * UNVERIFIED API POINTS (1.21.11): HudElementRegistry.attachElementAfter, VanillaHudElements.HOTBAR,
 * GuiGraphics#fill/drawString/guiWidth/guiHeight.
 */
final class ReputationHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(VillagerSociety.MOD_ID, "reputation_bar");
	private static final int BAR_COLOR = 0x80000000;   // black at 50% opacity
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;

	private ReputationHud() {
	}

	static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ID, (graphics, deltaTracker) -> render(graphics));
	}

	private static void render(GuiGraphics graphics) {
		if (!ModConfig.get().hudEnabled) {
			return;
		}
		ReputationPayload data = ClientReputationState.get();
		if (!data.inVillage()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		String text = text(data);
		int color = data.wanted() || data.score() <= -20 ? RED : data.score() >= 15 ? GREEN : WHITE;

		int textWidth = font.width(text);
		int x = (graphics.guiWidth() - textWidth) / 2;
		int y = graphics.guiHeight() - ModConfig.get().hudOffsetY;
		graphics.fill(x - 4, y - 2, x + textWidth + 4, y + font.lineHeight + 1, BAR_COLOR);
		graphics.drawString(font, text, x, y, color, true);
	}

	static String text(ReputationPayload data) {
		StringBuilder sb = new StringBuilder("Reputação: ");
		if (data.score() > 0) {
			sb.append('+');
		}
		sb.append(data.score()).append(" (").append(ReputationLevel.fromScore(data.score()).displayName()).append(')');
		if (data.wanted()) {
			sb.append(" | PROCURADO");
		}
		if (data.leader()) {
			sb.append(" | Líder");
		}
		RebellionPhase[] phases = RebellionPhase.values();
		if (data.phase() > 0 && data.phase() < phases.length) {
			sb.append(" | ").append(phases[data.phase()].displayName());
		}
		return sb.toString();
	}
}
