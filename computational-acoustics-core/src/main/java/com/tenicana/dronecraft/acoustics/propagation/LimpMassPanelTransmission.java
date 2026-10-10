package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;

/**
 * Normal-incidence transmission reference for an infinite limp mass sheet.
 *
 * <p>The pressure transmission magnitude is
 * {@code 1 / sqrt(1 + (pi*f*m/(rho*c))^2)}, where {@code m} is surface mass.
 * This is a boundary transmission loss, not the per-metre bulk attenuation
 * stored by {@code AcousticMaterial}. It is intended for offline calibration
 * and for deriving explicit surface parameters.</p>
 */
public record LimpMassPanelTransmission(
		double surfaceMassKilogramsPerSquareMeter,
		double airDensityKilogramsPerCubicMeter,
		double speedOfSoundMetersPerSecond
) {
	public LimpMassPanelTransmission {
		requirePositiveFinite(
				surfaceMassKilogramsPerSquareMeter,
				"surfaceMassKilogramsPerSquareMeter"
		);
		requirePositiveFinite(
				airDensityKilogramsPerCubicMeter,
				"airDensityKilogramsPerCubicMeter"
		);
		requirePositiveFinite(
				speedOfSoundMetersPerSecond,
				"speedOfSoundMetersPerSecond"
		);
	}

	public static LimpMassPanelTransmission fromDensityAndThickness(
			double densityKilogramsPerCubicMeter,
			double thicknessMeters,
			double airDensityKilogramsPerCubicMeter,
			double speedOfSoundMetersPerSecond
	) {
		requirePositiveFinite(
				densityKilogramsPerCubicMeter,
				"densityKilogramsPerCubicMeter"
		);
		requirePositiveFinite(thicknessMeters, "thicknessMeters");
		return new LimpMassPanelTransmission(
				densityKilogramsPerCubicMeter * thicknessMeters,
				airDensityKilogramsPerCubicMeter,
				speedOfSoundMetersPerSecond
		);
	}

	public double pressureMagnitude(double frequencyHz) {
		requireNonNegativeFinite(frequencyHz, "frequencyHz");
		double ratio = Math.PI * frequencyHz
				* surfaceMassKilogramsPerSquareMeter
				/ (airDensityKilogramsPerCubicMeter
						* speedOfSoundMetersPerSecond);
		return 1.0 / Math.sqrt(1.0 + ratio * ratio);
	}

	public double energyTransmission(double frequencyHz) {
		double pressure = pressureMagnitude(frequencyHz);
		return pressure * pressure;
	}

	public double transmissionLossDb(double frequencyHz) {
		return -20.0 * Math.log10(pressureMagnitude(frequencyHz));
	}

	public AcousticBands transmissionLossBands(
			double lowFrequencyHz,
			double midFrequencyHz,
			double highFrequencyHz
	) {
		if (!(lowFrequencyHz < midFrequencyHz
				&& midFrequencyHz < highFrequencyHz)) {
			throw new IllegalArgumentException(
					"band frequencies must be strictly increasing"
			);
		}
		return new AcousticBands(
				transmissionLossDb(lowFrequencyHz),
				transmissionLossDb(midFrequencyHz),
				transmissionLossDb(highFrequencyHz)
		);
	}

	private static void requirePositiveFinite(double value, String name) {
		if (!(value > 0.0) || !Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be positive and finite");
		}
	}

	private static void requireNonNegativeFinite(double value, String name) {
		if (!(value >= 0.0) || !Double.isFinite(value)) {
			throw new IllegalArgumentException(
					name + " must be non-negative and finite"
			);
		}
	}
}
