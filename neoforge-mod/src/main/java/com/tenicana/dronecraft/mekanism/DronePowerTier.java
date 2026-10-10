package com.tenicana.dronecraft.mekanism;

import java.util.Locale;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum DronePowerTier implements StringRepresentable {
	BASIC(3_600_000L),
	ADVANCED(10_800_000L),
	ELITE(28_800_000L),
	ULTIMATE(72_000_000L);

	public static final Codec<DronePowerTier> CODEC = StringRepresentable.fromEnum(DronePowerTier::values);
	public static final StreamCodec<ByteBuf, DronePowerTier> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
			DronePowerTier::byId,
			DronePowerTier::ordinal
	);

	private final long capacityFe;

	DronePowerTier(long capacityFe) {
		this.capacityFe = capacityFe;
	}

	public long capacityFe() {
		return capacityFe;
	}

	public static DronePowerTier byId(int id) {
		DronePowerTier[] values = values();
		return id >= 0 && id < values.length ? values[id] : BASIC;
	}

	public static DronePowerTier byName(String name) {
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
