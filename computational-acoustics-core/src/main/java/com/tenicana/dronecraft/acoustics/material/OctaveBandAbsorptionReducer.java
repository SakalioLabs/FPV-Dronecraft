package com.tenicana.dronecraft.acoustics.material;

import com.tenicana.dronecraft.acoustics.AcousticBands;

/**
 * Reduces the six architectural-acoustics octave bands to the runtime's
 * deliberately broad bands. The caller must supply source-energy weights:
 * absorption is an energy ratio, so an unlabelled arithmetic average would
 * silently bake a source spectrum into a material.
 */
public final class OctaveBandAbsorptionReducer {
	public static final int OCTAVE_BAND_COUNT = 6;
	public static final double[] CENTERS_HZ = {
			125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0
	};

	private OctaveBandAbsorptionReducer() {
	}

	/**
	 * Uses half-open runtime bands [125,700), [700,4000), and
	 * [4000,20000]. Consequently the 4 kHz octave datum belongs to high.
	 */
	public static AcousticBands reduce(
			double[] absorption,
			double[] sourceEnergyWeights
	) {
		requireVector(absorption, "absorption");
		requireVector(sourceEnergyWeights, "sourceEnergyWeights");
		for (int index = 0; index < OCTAVE_BAND_COUNT; index++) {
			double coefficient = absorption[index];
			if (coefficient < 0.0 || coefficient > 1.0) {
				throw new IllegalArgumentException(
						"absorption[" + index + "] must be in [0, 1]"
				);
			}
			if (sourceEnergyWeights[index] < 0.0) {
				throw new IllegalArgumentException(
						"sourceEnergyWeights[" + index
								+ "] must be non-negative"
				);
			}
		}
		return new AcousticBands(
				weightedMean(absorption, sourceEnergyWeights, 0, 3, "low"),
				weightedMean(absorption, sourceEnergyWeights, 3, 5, "mid"),
				weightedMean(absorption, sourceEnergyWeights, 5, 6, "high")
		);
	}

	private static double weightedMean(
			double[] values,
			double[] weights,
			int start,
			int end,
			String band
	) {
		double numerator = 0.0;
		double denominator = 0.0;
		for (int index = start; index < end; index++) {
			numerator += values[index] * weights[index];
			denominator += weights[index];
		}
		if (denominator <= 0.0) {
			throw new IllegalArgumentException(
					band + " runtime band must have positive source energy"
			);
		}
		return numerator / denominator;
	}

	private static void requireVector(double[] values, String name) {
		if (values == null || values.length != OCTAVE_BAND_COUNT) {
			throw new IllegalArgumentException(
					name + " must contain exactly six octave bands"
			);
		}
		for (int index = 0; index < values.length; index++) {
			if (!Double.isFinite(values[index])) {
				throw new IllegalArgumentException(
						name + "[" + index + "] must be finite"
				);
			}
		}
	}
}
