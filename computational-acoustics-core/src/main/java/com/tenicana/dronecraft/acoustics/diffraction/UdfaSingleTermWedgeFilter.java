package com.tenicana.dronecraft.acoustics.diffraction;

import java.util.Objects;

/**
 * Single-term UDFA approximation from Kirsch & Ewert, Acta Acustica 8
 * (2024), equations (4)-(5).
 *
 * <p>The single term has unity DC gain and approaches unity at an incident
 * shadow boundary. It is intended for higher-order contributions and cheap
 * zone transitions, not as a drop-in replacement for the more accurate
 * two-term first-order field.</p>
 */
public final class UdfaSingleTermWedgeFilter implements DiffractionFilterModel {
	private final UdfaInfiniteWedgeFilter twoTermReference;
	private final UdfaInfiniteWedgeFilter.Parameters parameters;

	public UdfaSingleTermWedgeFilter(UdfaInfiniteWedgeFilter.Parameters parameters) {
		this.parameters = Objects.requireNonNull(parameters, "parameters");
		this.twoTermReference = new UdfaInfiniteWedgeFilter(parameters);
	}

	@Override
	public double pressureMagnitude(double frequencyHz, InfiniteWedgeGeometry geometry) {
		return complexPressure(frequencyHz, geometry).magnitude();
	}

	public ComplexPressure complexPressure(
			double frequencyHz,
			InfiniteWedgeGeometry geometry
	) {
		if (!(frequencyHz >= 0.0) || !Double.isFinite(frequencyHz)) {
			throw new IllegalArgumentException("frequencyHz must be finite and non-negative");
		}
		Objects.requireNonNull(geometry, "geometry");
		double cutoff = cutoffFrequencyHz(geometry);
		return UdfaInfiniteWedgeFilter.modifiedFractionalLowPass(
				frequencyHz,
				cutoff,
				parameters.fractionalOrder(),
				parameters.rolloffShape(),
				parameters.quality(),
				parameters.transitionShape()
		);
	}

	/**
	 * Equation (5): combine the two component cutoffs and gains so that one
	 * unity-gain filter preserves the two-term high-frequency asymptote.
	 */
	public double cutoffFrequencyHz(InfiniteWedgeGeometry geometry) {
		Objects.requireNonNull(geometry, "geometry");
		double thetaMinus = geometry.sourceAzimuthRadians()
				- geometry.receiverAzimuthRadians();
		double thetaPlus = geometry.sourceAzimuthRadians()
				+ geometry.receiverAzimuthRadians();
		double wedgeIndex = Math.PI / geometry.exteriorWedgeAngleRadians();
		double gainMinus = UdfaInfiniteWedgeFilter.angularGain(thetaMinus, wedgeIndex);
		double gainPlus = UdfaInfiniteWedgeFilter.angularGain(thetaPlus, wedgeIndex);
		double cutoffMinus = twoTermReference.cutoffFrequencyHz(thetaMinus, geometry);
		double cutoffPlus = twoTermReference.cutoffFrequencyHz(thetaPlus, geometry);
		double weightedRootCutoff = (
				gainMinus * Math.sqrt(cutoffMinus)
						+ gainPlus * Math.sqrt(cutoffPlus)
		) / 2.0;
		return weightedRootCutoff * weightedRootCutoff;
	}
}
