package com.villagersociety.network;

import com.villagersociety.VillagerSociety;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * What the HUD panel needs, sent server -> client. The server stays the authority: the client only
 * displays it. {@code phase} is the ordinal of RebellionPhase; {@code wanted} is computed by the server so the
 * client never needs the server's thresholds.
 *
 * UNVERIFIED API POINTS (1.21.11): CustomPacketPayload.Type constructor, StreamCodec.composite, ByteBufCodecs.
 */
public record ReputationPayload(boolean inVillage, int score, int phase, boolean wanted, boolean leader) implements CustomPacketPayload {
	public static final ReputationPayload NONE = new ReputationPayload(false, 0, 0, false, false);

	public static final CustomPacketPayload.Type<ReputationPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(VillagerSociety.MOD_ID, "reputation"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ReputationPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, ReputationPayload::inVillage,
		ByteBufCodecs.INT, ReputationPayload::score,
		ByteBufCodecs.INT, ReputationPayload::phase,
		ByteBufCodecs.BOOL, ReputationPayload::wanted,
		ByteBufCodecs.BOOL, ReputationPayload::leader,
		ReputationPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
