package com.villagersociety.events;

import com.villagersociety.persistence.SocietyData.VillageData;
import com.villagersociety.persistence.SocietyState;
import com.villagersociety.reputation.ReputationAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Detects theft from community containers: counts the player's items when a container is opened
 * and again when the screen closes; a net gain is theft. Only players with an open session are
 * inspected, so the per-tick cost is zero when nobody is using a community container.
 */
public final class ContainerWatcher {
	private record Session(ServerLevel level, UUID village, BlockPos pos, int startCount) {
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private ContainerWatcher() {
	}

	public static void begin(ServerLevel level, ServerPlayer player, UUID village, BlockPos pos) {
		SESSIONS.put(player.getUUID(), new Session(level, village, pos, countItems(player)));
	}

	public static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Session> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove(); // disconnected
				continue;
			}
			if (player.containerMenu != player.inventoryMenu) {
				continue; // still open
			}
			Session s = entry.getValue();
			it.remove();
			if (countItems(player) > s.startCount()) {
				SocietyState state = SocietyState.get(server);
				VillageData village = state.village(s.village());
				if (village != null) {
					InfractionService.report(s.level(), player, state, village,
						ReputationAction.STEAL_CONTAINER, s.pos(), false);
				}
			}
		}
	}

	public static void clear() {
		SESSIONS.clear();
	}

	private static int countItems(ServerPlayer player) {
		Inventory inv = player.getInventory();
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			total += inv.getItem(i).getCount();
		}
		return total;
	}
}
