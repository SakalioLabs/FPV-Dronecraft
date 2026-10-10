package com.tenicana.dronecraft.acoustics.diffraction;

import java.util.Objects;

/**
 * Finite-edge frequency-domain UDFA reference based on equations (10)-(17).
 */
public final class UdfaFiniteWedgeFilter {
	private static final double MIN_GAIN = 1.0e-12;

	private final UdfaInfiniteWedgeFilter infinite;

	public UdfaFiniteWedgeFilter(UdfaInfiniteWedgeFilter.Parameters parameters) {
		infinite = new UdfaInfiniteWedgeFilter(parameters);
	}

	public double pressureMagnitude(
			double frequencyHz,
			VoxelWedgeGeometryMapper.Result mappedGeometry
	) {
		return complexPressure(frequencyHz, mappedGeometry).magnitude();
	}

	public ComplexPressure complexPressure(
			double frequencyHz,
			VoxelWedgeGeometryMapper.Result mappedGeometry
	) {
		if (!(frequencyHz >= 0.0) || !Double.isFinite(frequencyHz)) {
			throw new IllegalArgumentException("frequencyHz must be finite and non-negative");
		}
		Objects.requireNonNull(mappedGeometry, "mappedGeometry");
		InfiniteWedgeGeometry geometry = mappedGeometry.geometry();
		double firstTime = mappedGeometry.firstEndpointExcessTimeSeconds();
		double secondTime = mappedGeometry.secondEndpointExcessTimeSeconds();
		if (mappedGeometry.apexWithinPhysicalBlockEdge()) {
			return insideResponse(frequencyHz, geometry, firstTime, secondTime);
		}
		return outsideResponse(frequencyHz, geometry, firstTime, secondTime);
	}

	private ComplexPressure insideResponse(
			double frequencyHz,
			InfiniteWedgeGeometry geometry,
			double firstTime,
			double secondTime
	) {
		double wedgeIndex = Math.PI / geometry.exteriorWedgeAngleRadians();
		double thetaPlus = geometry.receiverAzimuthRadians()
				+ geometry.sourceAzimuthRadians();
		double thetaMinus = geometry.receiverAzimuthRadians()
				- geometry.sourceAzimuthRadians();
		ComplexPressure plus = finiteTermInside(
				frequencyHz, thetaPlus, wedgeIndex, geometry, firstTime, secondTime
		).scale(sign(thetaPlus - Math.PI));
		ComplexPressure minus = finiteTermInside(
				frequencyHz, thetaMinus, wedgeIndex, geometry, firstTime, secondTime
		).scale(sign(thetaMinus - Math.PI));
		return plus.add(minus).scale(0.25);
	}

	private ComplexPressure finiteTermInside(
			double frequencyHz,
			double theta,
			double wedgeIndex,
			InfiniteWedgeGeometry geometry,
			double firstTime,
			double secondTime
	) {
		double cutoff = infinite.cutoffFrequencyHz(theta, geometry);
		double angularGain = UdfaInfiniteWedgeFilter.angularGain(theta, wedgeIndex);
		return finiteHalfWedge(frequencyHz, cutoff, firstTime)
				.add(finiteHalfWedge(frequencyHz, cutoff, secondTime))
				.scale(angularGain);
	}

	private ComplexPressure outsideResponse(
			double frequencyHz,
			InfiniteWedgeGeometry geometry,
			double firstTime,
			double secondTime
	) {
		double nearTime = Math.min(firstTime, secondTime);
		double farTime = Math.max(firstTime, secondTime);
		double wedgeIndex = Math.PI / geometry.exteriorWedgeAngleRadians();
		double thetaPlus = geometry.receiverAzimuthRadians()
				+ geometry.sourceAzimuthRadians();
		double thetaMinus = geometry.receiverAzimuthRadians()
				- geometry.sourceAzimuthRadians();
		double plusCutoff = infinite.cutoffFrequencyHz(thetaPlus, geometry);
		double minusCutoff = infinite.cutoffFrequencyHz(thetaMinus, geometry);
		double dominantCutoff = Math.max(plusCutoff, minusCutoff);
		double nearDominantGain = finiteGain(dominantCutoff, nearTime);
		double farDominantGain = finiteGain(dominantCutoff, farTime);
		double blend = farDominantGain <= MIN_GAIN
				? 0.0
				: Math.pow(
						Math.max(0.0, 1.0 - nearDominantGain / farDominantGain),
						4.0
				);
		double offCutoff = farTime <= nearTime
				? Double.POSITIVE_INFINITY
				: 1.0 / (Math.PI * (farTime - nearTime));

		ComplexPressure plus = finiteTermOutside(
				frequencyHz,
				thetaPlus,
				wedgeIndex,
				plusCutoff,
				nearTime,
				farTime,
				offCutoff,
				blend
		).scale(sign(thetaPlus - Math.PI));
		ComplexPressure minus = finiteTermOutside(
				frequencyHz,
				thetaMinus,
				wedgeIndex,
				minusCutoff,
				nearTime,
				farTime,
				offCutoff,
				blend
		).scale(sign(thetaMinus - Math.PI));
		return plus.add(minus).scale(0.25);
	}

	private ComplexPressure finiteTermOutside(
			double frequencyHz,
			double theta,
			double wedgeIndex,
			double cutoff,
			double nearTime,
			double farTime,
			double offCutoff,
			double blend
	) {
		double angularGain = UdfaInfiniteWedgeFilter.angularGain(theta, wedgeIndex);
		ComplexPressure remaining =
				finiteHalfWedge(frequencyHz, cutoff, farTime);
		double offGain = Math.max(
				0.0,
				finiteGain(cutoff, farTime) - finiteGain(cutoff, nearTime)
		);
		ComplexPressure off = firstOrderLowPass(
				frequencyHz, offCutoff
		).scale(offGain);
		return remaining.scale(blend)
				.add(off.scale(1.0 - blend))
				.scale(angularGain);
	}

	private ComplexPressure finiteHalfWedge(
			double frequencyHz,
			double infiniteCutoff,
			double endpointExcessTime
	) {
		double gain = finiteGain(infiniteCutoff, endpointExcessTime);
		if (gain <= MIN_GAIN) {
			return ComplexPressure.ZERO;
		}
		double adjustedCutoff = infiniteCutoff / (gain * gain);
		UdfaInfiniteWedgeFilter.Parameters base = infinite.parameters();
		double gainSquared = gain * gain;
		double adjustedRolloff = 1.0
				+ (base.rolloffShape() - 1.0) * gainSquared;
		double adjustedQuality = 0.5
				+ (base.quality() - 0.5) * gainSquared;
		return UdfaInfiniteWedgeFilter.modifiedFractionalLowPass(
				frequencyHz,
				adjustedCutoff,
				base.fractionalOrder(),
				adjustedRolloff,
				adjustedQuality,
				base.transitionShape()
		).scale(gain);
	}

	static double finiteGain(double cutoffHz, double excessTimeSeconds) {
		if (excessTimeSeconds <= 0.0) {
			return 0.0;
		}
		if (!Double.isFinite(cutoffHz)) {
			return 1.0;
		}
		double argument = Math.PI * Math.sqrt(
				2.0 * Math.max(0.0, cutoffHz) * excessTimeSeconds
		);
		return 2.0 / Math.PI * Math.atan(argument);
	}

	private static ComplexPressure firstOrderLowPass(
			double frequencyHz,
			double cutoffHz
	) {
		if (frequencyHz == 0.0 || !Double.isFinite(cutoffHz)) {
			return ComplexPressure.ONE;
		}
		double normalized = frequencyHz / Math.max(cutoffHz, Double.MIN_NORMAL);
		double scale = 1.0 / (1.0 + normalized * normalized);
		return new ComplexPressure(
				scale,
				-normalized * scale
		);
	}

	private static double sign(double value) {
		return value < 0.0 ? -1.0 : value > 0.0 ? 1.0 : 0.0;
	}
}
