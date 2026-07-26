package com.tenicana.dronecraft.acoustics;

import java.util.Objects;

/**
 * Three-band material parameters. Transmission loss is expressed in dB per
 * metre of voxel path and operates on acoustic energy.
 */
public record AcousticMaterial(
		String id,
		AcousticBands transmissionLossDbPerMeter,
		AcousticBands surfaceAbsorption,
		double scattering
) {
	public static final AcousticMaterial AIR = new AcousticMaterial(
			"air",
			AcousticBands.SILENT,
			AcousticBands.SILENT,
			0.0
	);

	public AcousticMaterial {
		if (Objects.requireNonNull(id, "id").isBlank()) {
			throw new IllegalArgumentException("id must not be blank");
		}
		Objects.requireNonNull(transmissionLossDbPerMeter, "transmissionLossDbPerMeter");
		Objects.requireNonNull(surfaceAbsorption, "surfaceAbsorption");
		requireUnitInterval(surfaceAbsorption.low(), "low absorption");
		requireUnitInterval(surfaceAbsorption.mid(), "mid absorption");
		requireUnitInterval(surfaceAbsorption.high(), "high absorption");
		requireUnitInterval(scattering, "scattering");
	}

	public AcousticBands transmissionEnergyGain(double pathLengthMeters) {
		if (!Double.isFinite(pathLengthMeters) || pathLengthMeters < 0.0) {
			throw new IllegalArgumentException("pathLengthMeters must be finite and non-negative");
		}
		return transmissionLossDbPerMeter.map(
				lossDbPerMeter -> Math.pow(10.0, -lossDbPerMeter * pathLengthMeters / 10.0)
		);
	}

	public boolean isAir() {
		return transmissionLossDbPerMeter.totalEnergy() == 0.0
				&& surfaceAbsorption.totalEnergy() == 0.0
				&& scattering == 0.0;
	}

	private static void requireUnitInterval(double value, String name) {
		if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
			throw new IllegalArgumentException(name + " must be in [0, 1]");
		}
	}
}
