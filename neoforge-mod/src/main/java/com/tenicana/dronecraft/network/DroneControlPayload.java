package com.tenicana.dronecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.tenicana.dronecraft.FpvDronecraftMod;

public record DroneControlPayload(float throttle, float pitch, float roll, float yaw, boolean armed, int flightMode) implements CustomPacketPayload {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "drone_control");
	public static final CustomPacketPayload.Type<DroneControlPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, DroneControlPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT,
			DroneControlPayload::throttle,
			ByteBufCodecs.FLOAT,
			DroneControlPayload::pitch,
			ByteBufCodecs.FLOAT,
			DroneControlPayload::roll,
			ByteBufCodecs.FLOAT,
			DroneControlPayload::yaw,
			ByteBufCodecs.BOOL,
			DroneControlPayload::armed,
			ByteBufCodecs.VAR_INT,
			DroneControlPayload::flightMode,
			DroneControlPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
