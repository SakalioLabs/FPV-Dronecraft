package com.tenicana.dronecraft.mekanism;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record DroneLoadout(DronePowerTier powerTier, DroneRadioTier radioTier, boolean quantumLink) {
	public static final DroneLoadout BASIC = new DroneLoadout(DronePowerTier.BASIC, DroneRadioTier.BASIC, false);
	public static final Codec<DroneLoadout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			DronePowerTier.CODEC.fieldOf("power").forGetter(DroneLoadout::powerTier),
			DroneRadioTier.CODEC.fieldOf("radio").forGetter(DroneLoadout::radioTier),
			Codec.BOOL.optionalFieldOf("quantum", false).forGetter(DroneLoadout::quantumLink)
	).apply(instance, DroneLoadout::new));
	public static final StreamCodec<ByteBuf, DroneLoadout> STREAM_CODEC = StreamCodec.composite(
			DronePowerTier.STREAM_CODEC,
			DroneLoadout::powerTier,
			DroneRadioTier.STREAM_CODEC,
			DroneLoadout::radioTier,
			ByteBufCodecs.BOOL,
			DroneLoadout::quantumLink,
			DroneLoadout::new
	);

	public DroneLoadout {
		powerTier = powerTier == null ? DronePowerTier.BASIC : powerTier;
		radioTier = radioTier == null ? DroneRadioTier.BASIC : radioTier;
	}
}
