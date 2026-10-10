package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticBands;

/**
 * Closed diffuse-field shoebox reference used to separate geometry and
 * effective room absorption from voxel-probe sampling error.
 */
public record ShoeboxRoomDecay(
		double lengthMeters,
		double widthMeters,
		double heightMeters
) {
	private static final double SIXTY_DB_ENERGY_NEPERS =
			6.0 * Math.log(10.0);

	public ShoeboxRoomDecay {
		requirePositive(lengthMeters, "lengthMeters");
		requirePositive(widthMeters, "widthMeters");
		requirePositive(heightMeters, "heightMeters");
	}

	public double volumeCubicMeters() {
		return lengthMeters * widthMeters * heightMeters;
	}

	public double surfaceAreaSquareMeters() {
		return 2.0 * (
				lengthMeters * widthMeters
						+ lengthMeters * heightMeters
						+ widthMeters * heightMeters
		);
	}

	/**
	 * Cauchy's mean-chord result for a convex enclosure.
	 */
	public double diffuseMeanFreePathMeters() {
		return 4.0 * volumeCubicMeters() / surfaceAreaSquareMeters();
	}

	public double eyringRt60Seconds(
			double absorption,
			double soundSpeedMetersPerSecond
	) {
		requireAbsorption(absorption);
		requirePositive(
				soundSpeedMetersPerSecond,
				"soundSpeedMetersPerSecond"
		);
		if (absorption == 1.0) {
			return 0.0;
		}
		if (absorption == 0.0) {
			return Double.POSITIVE_INFINITY;
		}
		return -SIXTY_DB_ENERGY_NEPERS * diffuseMeanFreePathMeters()
				/ (
						soundSpeedMetersPerSecond
								* Math.log1p(-absorption)
				);
	}

	public AcousticBands eyringRt60Seconds(
			AcousticBands absorption,
			double soundSpeedMetersPerSecond
	) {
		return absorption.map(
				value -> eyringRt60Seconds(
						value,
						soundSpeedMetersPerSecond
				)
		);
	}

	public double effectiveEyringAbsorption(
			double rt60Seconds,
			double soundSpeedMetersPerSecond
	) {
		requirePositive(rt60Seconds, "rt60Seconds");
		requirePositive(
				soundSpeedMetersPerSecond,
				"soundSpeedMetersPerSecond"
		);
		double exponent = -SIXTY_DB_ENERGY_NEPERS
				* diffuseMeanFreePathMeters()
				/ (soundSpeedMetersPerSecond * rt60Seconds);
		return -Math.expm1(exponent);
	}

	public AcousticBands effectiveEyringAbsorption(
			AcousticBands rt60Seconds,
			double soundSpeedMetersPerSecond
	) {
		return rt60Seconds.map(
				value -> effectiveEyringAbsorption(
						value,
						soundSpeedMetersPerSecond
				)
		);
	}

	public double effectiveSabineAbsorption(
			double rt60Seconds,
			double soundSpeedMetersPerSecond
	) {
		requirePositive(rt60Seconds, "rt60Seconds");
		requirePositive(
				soundSpeedMetersPerSecond,
				"soundSpeedMetersPerSecond"
		);
		return SIXTY_DB_ENERGY_NEPERS * diffuseMeanFreePathMeters()
				/ (soundSpeedMetersPerSecond * rt60Seconds);
	}

	private static void requireAbsorption(double value) {
		if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
			throw new IllegalArgumentException(
					"absorption must be finite and in [0, 1]"
			);
		}
	}

	private static void requirePositive(double value, String name) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(
					name + " must be finite and positive"
			);
		}
	}
}
