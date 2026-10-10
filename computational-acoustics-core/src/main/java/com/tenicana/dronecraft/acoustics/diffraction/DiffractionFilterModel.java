package com.tenicana.dronecraft.acoustics.diffraction;

import com.tenicana.dronecraft.acoustics.AcousticBands;

/**
 * Frequency-domain reference contract for an edge diffraction model.
 * Runtime IIR rendering can be validated against this contract independently.
 */
public interface DiffractionFilterModel {
	double pressureMagnitude(double frequencyHz, InfiniteWedgeGeometry geometry);

	default AcousticBands energyGain(
			BandCenters centers,
			InfiniteWedgeGeometry geometry
	) {
		return new AcousticBands(
				square(pressureMagnitude(centers.lowHz(), geometry)),
				square(pressureMagnitude(centers.midHz(), geometry)),
				square(pressureMagnitude(centers.highHz(), geometry))
		);
	}

	private static double square(double value) {
		return value * value;
	}

	record BandCenters(double lowHz, double midHz, double highHz) {
		public BandCenters {
			if (!(lowHz > 0.0 && lowHz < midHz && midHz < highHz)
					|| !Double.isFinite(highHz)) {
				throw new IllegalArgumentException(
						"band centers must be finite, positive and strictly increasing"
				);
			}
		}

		public static BandCenters researchDefaults() {
			return new BandCenters(250.0, 1_000.0, 4_000.0);
		}
	}
}
