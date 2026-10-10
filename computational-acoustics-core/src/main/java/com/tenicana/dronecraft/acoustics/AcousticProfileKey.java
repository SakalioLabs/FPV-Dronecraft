package com.tenicana.dronecraft.acoustics;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Physical configuration used to select a measured source profile.
 *
 * <p>Radius is stored in integer millimetres so profile selection is stable
 * across network float serialization.</p>
 */
public record AcousticProfileKey(
		String airframePreset,
		int rotorCount,
		int bladeCount,
		int rotorRadiusMillimetres,
		int motorPolePairs
) {
	private static final Pattern AIRFRAME_ID =
			Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

	public AcousticProfileKey {
		Objects.requireNonNull(airframePreset, "airframePreset");
		airframePreset = airframePreset.trim().toLowerCase(Locale.ROOT);
		if (!AIRFRAME_ID.matcher(airframePreset).matches()) {
			throw new IllegalArgumentException(
					"airframePreset must be a lowercase path-like identifier"
			);
		}
		if (rotorCount < 1 || rotorCount > 32) {
			throw new IllegalArgumentException("rotorCount must be in [1, 32]");
		}
		if (bladeCount < 1 || bladeCount > 16) {
			throw new IllegalArgumentException("bladeCount must be in [1, 16]");
		}
		if (rotorRadiusMillimetres < 5 || rotorRadiusMillimetres > 2_000) {
			throw new IllegalArgumentException(
					"rotorRadiusMillimetres must be in [5, 2000]"
			);
		}
		if (motorPolePairs < 1 || motorPolePairs > 64) {
			throw new IllegalArgumentException("motorPolePairs must be in [1, 64]");
		}
	}

	public static AcousticProfileKey fromMetres(
			String airframePreset,
			int rotorCount,
			int bladeCount,
			double rotorRadiusMetres,
			int motorPolePairs
	) {
		if (!Double.isFinite(rotorRadiusMetres)) {
			throw new IllegalArgumentException("rotorRadiusMetres must be finite");
		}
		return new AcousticProfileKey(
				airframePreset,
				rotorCount,
				bladeCount,
				(int) Math.round(rotorRadiusMetres * 1_000.0),
				motorPolePairs
		);
	}
}
