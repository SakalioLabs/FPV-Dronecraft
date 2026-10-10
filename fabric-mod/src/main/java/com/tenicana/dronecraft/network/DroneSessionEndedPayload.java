package com.tenicana.dronecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.tenicana.dronecraft.FpvDronecraftMod;

public record DroneSessionEndedPayload() implements CustomPacketPayload {
	public static final DroneSessionEndedPayload INSTANCE = new DroneSessionEndedPayload();
	public static final Type<DroneSessionEndedPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "remote_session_ended"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DroneSessionEndedPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
