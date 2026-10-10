package com.tenicana.dronecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.tenicana.dronecraft.FpvDronecraftMod;

/** Sent before chunk deltas, so both client storage regions are ready before chunk data arrives. */
public record DroneChunkCentersPayload(boolean active, int bodyX, int bodyZ, int droneX, int droneZ)
		implements CustomPacketPayload {
	public static final Type<DroneChunkCentersPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, "remote_chunk_centers"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DroneChunkCentersPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, DroneChunkCentersPayload::active,
			ByteBufCodecs.VAR_INT, DroneChunkCentersPayload::bodyX,
			ByteBufCodecs.VAR_INT, DroneChunkCentersPayload::bodyZ,
			ByteBufCodecs.VAR_INT, DroneChunkCentersPayload::droneX,
			ByteBufCodecs.VAR_INT, DroneChunkCentersPayload::droneZ,
			DroneChunkCentersPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
