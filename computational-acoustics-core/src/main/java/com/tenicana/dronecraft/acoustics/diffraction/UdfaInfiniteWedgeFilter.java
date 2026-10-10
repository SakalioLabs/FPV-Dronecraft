package com.tenicana.dronecraft.acoustics.diffraction;

import java.util.Objects;

/**
 * Independent implementation of the infinite-wedge, two-term frequency-domain
 * equations (2)-(6) from Kirsch & Ewert, IEEE/ACM TASLP 31 (2023), 1636-1651.
 *
 * <p>This class is derived from the published equations only. It contains no
 * code from the separately distributed UDFA MATLAB toolbox.</p>
 */
public final class UdfaInfiniteWedgeFilter implements DiffractionFilterModel {
	private static final double MIN_DENOMINATOR = 1.0e-12;

	private final Parameters parameters;

	public UdfaInfiniteWedgeFilter(Parameters parameters) {
		this.parameters = Objects.requireNonNull(parameters, "parameters");
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
		double wedgeIndex = Math.PI / geometry.exteriorWedgeAngleRadians();
		double thetaPlus = geometry.receiverAzimuthRadians()
				+ geometry.sourceAzimuthRadians();
		double thetaMinus = geometry.receiverAzimuthRadians()
				- geometry.sourceAzimuthRadians();

		ComplexPressure plus = term(frequencyHz, thetaPlus, wedgeIndex, geometry);
		ComplexPressure minus = term(frequencyHz, thetaMinus, wedgeIndex, geometry);
		ComplexPressure combined = plus.scale(sign(thetaPlus - Math.PI))
				.add(minus.scale(sign(thetaMinus - Math.PI)))
				.scale(0.5);
		return combined;
	}

	public double cutoffFrequencyHz(
			double thetaRadians,
			InfiniteWedgeGeometry geometry
	) {
		double wedgeIndex = Math.PI / geometry.exteriorWedgeAngleRadians();
		double n = angularCutoffFactor(thetaRadians, wedgeIndex);
		double incidence = Math.sin(geometry.incidenceAngleRadians());
		return 2.0 * geometry.speedOfSoundMetersPerSecond()
				* n * n
				/ (Math.PI * Math.PI
				* geometry.characteristicDistanceMeters()
				* incidence * incidence);
	}

	private ComplexPressure term(
			double frequencyHz,
			double thetaRadians,
			double wedgeIndex,
			InfiniteWedgeGeometry geometry
	) {
		double gain = angularGain(thetaRadians, wedgeIndex);
		double cutoff = cutoffFrequencyHz(thetaRadians, geometry);
		return modifiedFractionalLowPass(
				frequencyHz,
				cutoff,
				parameters.fractionalOrder(),
				parameters.rolloffShape(),
				parameters.quality(),
				parameters.transitionShape()
		).scale(gain);
	}

	static ComplexPressure modifiedFractionalLowPass(
			double frequencyHz,
			double cutoffHz,
			double fractionalOrder,
			double rolloffShape,
			double quality,
			double transitionShape
	) {
		if (frequencyHz == 0.0 || !Double.isFinite(cutoffHz)) {
			return ComplexPressure.ONE;
		}
		double normalized = frequencyHz / Math.max(cutoffHz, Double.MIN_NORMAL);
		ComplexPressure first = ComplexPressure.imaginary(normalized)
				.pow(2.0 / rolloffShape);
		ComplexPressure second = ComplexPressure.imaginary(normalized / quality)
				.pow(1.0 / (rolloffShape * transitionShape));
		return ComplexPressure.ONE.add(first).add(second)
				.pow(-fractionalOrder * rolloffShape / 2.0);
	}

	static double angularGain(double theta, double wedgeIndex) {
		double numerator = Math.sin(wedgeIndex * Math.PI);
		double radicand = 1.0
				- Math.cos(wedgeIndex * Math.PI) * Math.cos(wedgeIndex * theta);
		return numerator / Math.sqrt(Math.max(radicand, MIN_DENOMINATOR));
	}

	private static double angularCutoffFactor(double theta, double wedgeIndex) {
		double numerator = wedgeIndex * Math.sqrt(Math.max(
				0.0,
				1.0 - Math.cos(wedgeIndex * Math.PI) * Math.cos(wedgeIndex * theta)
		));
		double denominator = Math.cos(wedgeIndex * Math.PI)
				- Math.cos(wedgeIndex * theta);
		if (Math.abs(denominator) < MIN_DENOMINATOR) {
			return Math.copySign(Double.POSITIVE_INFINITY, denominator);
		}
		return numerator / denominator;
	}

	private static double sign(double value) {
		return value < 0.0 ? -1.0 : value > 0.0 ? 1.0 : 0.0;
	}

	Parameters parameters() {
		return parameters;
	}

	public record Parameters(
			double fractionalOrder,
			double rolloffShape,
			double quality,
			double transitionShape
	) {
		public Parameters {
			if (!(fractionalOrder > 0.0)
					|| !(rolloffShape > 0.0)
					|| !(quality > 0.0)
					|| !(transitionShape > 0.0)
					|| !Double.isFinite(fractionalOrder + rolloffShape
					+ quality + transitionShape)) {
				throw new IllegalArgumentException("UDFA parameters must be positive and finite");
			}
		}

		/**
		 * Published parameters [P]: alpha=0.5, b=1.44, Q=0.2, r=1.6.
		 */
		public static Parameters published2023() {
			return new Parameters(0.5, 1.44, 0.2, 1.6);
		}
	}

}
