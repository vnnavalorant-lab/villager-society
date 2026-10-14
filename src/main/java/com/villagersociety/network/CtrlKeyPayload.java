package com.villagersociety.network;

import com.villagersociety.VillagerSociety;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: whether the Sprint key (Ctrl by default) is held. Used for "Ctrl + right-click". */
public record CtrlKeyPayload(boolean down) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CtrlKeyPayload> TYPE = new CustomPacketPayload.Type<>(
		Identifier.fromNamespaceAndPath(VillagerSociety.MOD_ID, "ctrl_key"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CtrlKeyPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, CtrlKeyPayload::down,
		CtrlKeyPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
