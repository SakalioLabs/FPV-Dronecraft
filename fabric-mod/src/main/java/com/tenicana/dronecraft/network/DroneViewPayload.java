package com.tenicana.dronecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.tenicana.dronecraft.FpvDronecraftMod;

public record DroneViewPayload(boolean fpvView) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "drone_view");
	public static final CustomPacketPayload.Type<DroneViewPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, DroneViewPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL,
			DroneViewPayload::fpvView,
			DroneViewPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
