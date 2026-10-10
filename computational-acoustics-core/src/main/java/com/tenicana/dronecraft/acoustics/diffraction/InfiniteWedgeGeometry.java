package com.tenicana.dronecraft.acoustics.diffraction;

/**
 * Cylindrical geometry used by the infinite-wedge UDFA equations.
 * Angles are radians and distances are metres.
 */
public record InfiniteWedgeGeometry(
		double sourceDistanceMeters,
		double receiverDistanceMeters,
		double sourceAzimuthRadians,
		double receiverAzimuthRadians,
		double exteriorWedgeAngleRadians,
		double incidenceAngleRadians,
		double speedOfSoundMetersPerSecond
) {
	public InfiniteWedgeGeometry {
		requirePositiveFinite(sourceDistanceMeters, "sourceDistanceMeters");
		requirePositiveFinite(receiverDistanceMeters, "receiverDistanceMeters");
		requireFinite(sourceAzimuthRadians, "sourceAzimuthRadians");
		requireFinite(receiverAzimuthRadians, "receiverAzimuthRadians");
		if (!(exteriorWedgeAngleRadians > 0.0
				&& exteriorWedgeAngleRadians <= 2.0 * Math.PI)) {
			throw new IllegalArgumentException(
					"exteriorWedgeAngleRadians must be in (0, 2pi]"
			);
		}
		if (!(incidenceAngleRadians > 0.0 && incidenceAngleRadians <= Math.PI / 2.0)) {
			throw new IllegalArgumentException(
					"incidenceAngleRadians must be in (0, pi/2]"
			);
		}
		requirePositiveFinite(speedOfSoundMetersPerSecond, "speedOfSoundMetersPerSecond");
	}

	public double pathLengthMeters() {
		return sourceDistanceMeters + receiverDistanceMeters;
	}

	public double characteristicDistanceMeters() {
		return 2.0 * sourceDistanceMeters * receiverDistanceMeters / pathLengthMeters();
	}

	private static void requirePositiveFinite(double value, String name) {
		if (!(value > 0.0) || !Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be positive and finite");
		}
	}

	private static void requireFinite(double value, String name) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite");
		}
	}
}
