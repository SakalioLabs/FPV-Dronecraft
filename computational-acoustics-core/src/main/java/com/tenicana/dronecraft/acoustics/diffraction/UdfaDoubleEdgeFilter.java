package com.tenicana.dronecraft.acoustics.diffraction;

import java.util.Objects;

/**
 * Paper-derived higher-order approximation for a three-sided barrier.
 *
 * <p>Implements Kirsch & Ewert, Acta Acustica 8 (2024), equations (8)-(12)
 * for the double shadow zone. It combines two reciprocal contributions,
 * each containing a two-term first-edge filter and a single-term second-edge
 * filter. No code from the separately distributed UDFA toolbox is used.</p>
 */
public final class UdfaDoubleEdgeFilter {
	private static final double ZERO_WIDTH_TOLERANCE_METERS = 1.0e-12;

	private final UdfaInfiniteWedgeFilter twoTerm;
	private final UdfaSingleTermWedgeFilter singleTerm;

	public UdfaDoubleEdgeFilter(UdfaInfiniteWedgeFilter.Parameters parameters) {
		Objects.requireNonNull(parameters, "parameters");
		this.twoTerm = new UdfaInfiniteWedgeFilter(parameters);
		this.singleTerm = new UdfaSingleTermWedgeFilter(parameters);
	}

	public double pressureMagnitude(
			double frequencyHz,
			DoubleEdgeGeometry geometry
	) {
		return complexPressure(frequencyHz, geometry).magnitude();
	}

	public ComplexPressure complexPressure(
			double frequencyHz,
			DoubleEdgeGeometry geometry
	) {
		if (!(frequencyHz >= 0.0) || !Double.isFinite(frequencyHz)) {
			throw new IllegalArgumentException("frequencyHz must be finite and non-negative");
		}
		Objects.requireNonNull(geometry, "geometry");
		if (geometry.edgeSeparationMeters() <= ZERO_WIDTH_TOLERANCE_METERS) {
			return zeroWidthKnifeEdgePressure(frequencyHz, geometry);
		}

		double effectiveWidth = geometry.effectiveEdgeSeparationMeters();
		double firstModifiedAngle = modifiedExteriorWedgeAngle(
				geometry.firstExteriorWedgeAngleRadians(),
				geometry.secondExteriorWedgeAngleRadians(),
				geometry.sourceDistanceToFirstEdgeMeters()
						+ geometry.receiverDistanceToFirstEdgeMeters(),
				geometry.edgeSeparationMeters(),
				geometry.exteriorAngleBlend()
		);
		double secondModifiedAngle = modifiedExteriorWedgeAngle(
				geometry.secondExteriorWedgeAngleRadians(),
				geometry.firstExteriorWedgeAngleRadians(),
				geometry.sourceDistanceToSecondEdgeMeters()
						+ geometry.receiverDistanceToSecondEdgeMeters(),
				geometry.edgeSeparationMeters(),
				geometry.exteriorAngleBlend()
		);

		InfiniteWedgeGeometry firstGeometry = new InfiniteWedgeGeometry(
				geometry.sourceDistanceToFirstEdgeMeters(),
				effectiveWidth + geometry.receiverDistanceToSecondEdgeMeters(),
				geometry.sourceAzimuthAtFirstEdgeRadians(),
				geometry.receiverAzimuthAtFirstEdgeRadians(),
				firstModifiedAngle,
				geometry.incidenceAngleRadians(),
				geometry.speedOfSoundMetersPerSecond()
		);
		InfiniteWedgeGeometry secondGeometry = new InfiniteWedgeGeometry(
				geometry.receiverDistanceToSecondEdgeMeters(),
				effectiveWidth + geometry.sourceDistanceToFirstEdgeMeters(),
				geometry.receiverAzimuthAtSecondEdgeRadians(),
				geometry.sourceAzimuthAtSecondEdgeRadians(),
				secondModifiedAngle,
				geometry.incidenceAngleRadians(),
				geometry.speedOfSoundMetersPerSecond()
		);
		InfiniteWedgeGeometry firstToSecondGeometry = new InfiniteWedgeGeometry(
				effectiveWidth,
				geometry.receiverDistanceToSecondEdgeMeters(),
				0.0,
				geometry.receiverAzimuthAtSecondEdgeRadians(),
				geometry.secondExteriorWedgeAngleRadians(),
				geometry.incidenceAngleRadians(),
				geometry.speedOfSoundMetersPerSecond()
		);
		InfiniteWedgeGeometry secondToFirstGeometry = new InfiniteWedgeGeometry(
				effectiveWidth,
				geometry.sourceDistanceToFirstEdgeMeters(),
				0.0,
				geometry.sourceAzimuthAtFirstEdgeRadians(),
				geometry.firstExteriorWedgeAngleRadians(),
				geometry.incidenceAngleRadians(),
				geometry.speedOfSoundMetersPerSecond()
		);

		ComplexPressure firstContribution = twoTerm.complexPressure(
				frequencyHz,
				firstGeometry
		).multiply(singleTerm.complexPressure(
				frequencyHz,
				firstToSecondGeometry
		));
		ComplexPressure secondContribution = twoTerm.complexPressure(
				frequencyHz,
				secondGeometry
		).multiply(singleTerm.complexPressure(
				frequencyHz,
				secondToFirstGeometry
		));
		double firstToSecondCutoff = singleTerm.cutoffFrequencyHz(
				firstToSecondGeometry
		);
		double secondToFirstCutoff = singleTerm.cutoffFrequencyHz(
				secondToFirstGeometry
		);
		double alpha = mixingWeight(firstToSecondCutoff, secondToFirstCutoff);
		return firstContribution.scale(1.0 - alpha)
				.add(secondContribution.scale(alpha));
	}

	/**
	 * Equation (8). The dimensional form is retained exactly as published.
	 */
	public static double modifiedExteriorWedgeAngle(
			double ownExteriorAngleRadians,
			double otherExteriorAngleRadians,
			double diffractedPathDistanceMeters,
			double edgeSeparationMeters,
			double blend
	) {
		if (!(diffractedPathDistanceMeters > 0.0)
				|| !Double.isFinite(diffractedPathDistanceMeters)) {
			throw new IllegalArgumentException(
					"diffractedPathDistanceMeters must be positive and finite"
			);
		}
		if (!(edgeSeparationMeters >= 0.0)
				|| !Double.isFinite(edgeSeparationMeters)) {
			throw new IllegalArgumentException(
					"edgeSeparationMeters must be finite and non-negative"
			);
		}
		if (!(blend >= 0.0 && blend <= 1.0) || !Double.isFinite(blend)) {
			throw new IllegalArgumentException("blend must be in [0, 1]");
		}
		double distanceSquared = diffractedPathDistanceMeters
				* diffractedPathDistanceMeters;
		double widthFactor = blend * distanceSquared
				/ (edgeSeparationMeters + distanceSquared);
		return ownExteriorAngleRadians
				+ widthFactor * (otherExteriorAngleRadians - Math.PI);
	}

	static double mixingWeight(
			double firstToSecondCutoffHz,
			double secondToFirstCutoffHz
	) {
		if (Double.isInfinite(firstToSecondCutoffHz)
				&& Double.isInfinite(secondToFirstCutoffHz)) {
			return 0.5;
		}
		if (Double.isInfinite(firstToSecondCutoffHz)) {
			return 0.0;
		}
		if (Double.isInfinite(secondToFirstCutoffHz)) {
			return 1.0;
		}
		return secondToFirstCutoffHz
				/ (firstToSecondCutoffHz + secondToFirstCutoffHz);
	}

	private ComplexPressure zeroWidthKnifeEdgePressure(
			double frequencyHz,
			DoubleEdgeGeometry geometry
	) {
		double combinedExteriorAngle = geometry.firstExteriorWedgeAngleRadians()
				+ geometry.secondExteriorWedgeAngleRadians() - Math.PI;
		InfiniteWedgeGeometry knifeEdge = new InfiniteWedgeGeometry(
				geometry.sourceDistanceToFirstEdgeMeters(),
				geometry.receiverDistanceToSecondEdgeMeters(),
				geometry.sourceAzimuthAtFirstEdgeRadians(),
				geometry.receiverAzimuthAtFirstEdgeRadians(),
				combinedExteriorAngle,
				geometry.incidenceAngleRadians(),
				geometry.speedOfSoundMetersPerSecond()
		);
		return twoTerm.complexPressure(frequencyHz, knifeEdge);
	}
}
