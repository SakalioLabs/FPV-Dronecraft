package com.tenicana.dronecraft.acoustics.diffraction;

import java.util.Objects;

/**
 * Zone boundaries from Section II of Kirsch & Ewert (2023).
 *
 * <p>The direct field ends at theta_s + pi. Reflected GA fields can exist
 * below pi - theta_s and above 2 theta_w - pi - theta_s.</p>
 */
public final class UdfaZoneClassifier {
	private static final double BOUNDARY_TOLERANCE = 1.0e-9;

	private UdfaZoneClassifier() {
	}

	public static Classification classify(InfiniteWedgeGeometry geometry) {
		Objects.requireNonNull(geometry, "geometry");
		double source = geometry.sourceAzimuthRadians();
		double receiver = geometry.receiverAzimuthRadians();
		double wedge = geometry.exteriorWedgeAngleRadians();
		double shadowBoundary = source + Math.PI;
		double firstReflectionBoundary = Math.PI - source;
		double secondReflectionBoundary = 2.0 * wedge - Math.PI - source;

		boolean directVisible = receiver <= shadowBoundary + BOUNDARY_TOLERANCE;
		boolean firstReflection = receiver
				< firstReflectionBoundary - BOUNDARY_TOLERANCE;
		boolean secondReflection = secondReflectionBoundary < wedge
				&& receiver > secondReflectionBoundary + BOUNDARY_TOLERANCE;
		return new Classification(
				directVisible,
				firstReflection,
				secondReflection,
				receiver - shadowBoundary,
				receiver - firstReflectionBoundary,
				receiver - secondReflectionBoundary
		);
	}

	public record Classification(
			boolean directVisible,
			boolean firstPlaneReflectionVisible,
			boolean secondPlaneReflectionVisible,
			double signedShadowBoundaryOffsetRadians,
			double signedFirstReflectionBoundaryOffsetRadians,
			double signedSecondReflectionBoundaryOffsetRadians
	) {
		public boolean shadowZone() {
			return !directVisible;
		}

		public int visibleReflectionCount() {
			return (firstPlaneReflectionVisible ? 1 : 0)
					+ (secondPlaneReflectionVisible ? 1 : 0);
		}
	}
}
