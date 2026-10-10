package com.tenicana.dronecraft.mekanism;

import java.util.Locale;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum DroneRadioTier implements StringRepresentable {
	BASIC(128.0),
	ADVANCED(512.0),
	ELITE(2_048.0),
	ULTIMATE(8_192.0);

	public static final Codec<DroneRadioTier> CODEC = StringRepresentable.fromEnum(DroneRadioTier::values);
	public static final StreamCodec<ByteBuf, DroneRadioTier> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
			DroneRadioTier::byId,
			DroneRadioTier::ordinal
	);

	private final double rangeMeters;

	DroneRadioTier(double rangeMeters) {
		this.rangeMeters = rangeMeters;
	}

	public double rangeMeters() {
		return rangeMeters;
	}

	public static DroneRadioTier byId(int id) {
		DroneRadioTier[] values = values();
		return id >= 0 && id < values.length ? values[id] : BASIC;
	}

	public static DroneRadioTier byName(String name) {
		if (name == null) {
			return BASIC;
		}
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return BASIC;
		}
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
