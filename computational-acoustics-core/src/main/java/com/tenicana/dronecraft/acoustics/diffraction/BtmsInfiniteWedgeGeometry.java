package com.tenicana.dronecraft.acoustics.diffraction;

/**
 * Cylindrical geometry for the exact BTMS infinite-wedge contour integral.
 *
 * <p>Radial distances are measured perpendicular to the edge. Only the axial
 * source-receiver separation is required because a common translation along
 * an infinite edge does not change the field.</p>
 */
public record BtmsInfiniteWedgeGeometry(
		double sourceRadialDistanceMeters,
		double receiverRadialDistanceMeters,
		double axialSeparationMeters,
		double sourceAzimuthRadians,
		double receiverAzimuthRadians,
		double exteriorWedgeAngleRadians,
		double speedOfSoundMetersPerSecond
) {
	public BtmsInfiniteWedgeGeometry {
		requirePositiveFinite(sourceRadialDistanceMeters,
				"sourceRadialDistanceMeters");
		requirePositiveFinite(receiverRadialDistanceMeters,
				"receiverRadialDistanceMeters");
		if (!(axialSeparationMeters >= 0.0) || !Double.isFinite(axialSeparationMeters)) {
			throw new IllegalArgumentException(
					"axialSeparationMeters must be finite and non-negative"
			);
		}
		requireFinite(sourceAzimuthRadians, "sourceAzimuthRadians");
		requireFinite(receiverAzimuthRadians, "receiverAzimuthRadians");
		if (!(exteriorWedgeAngleRadians > Math.PI
				&& exteriorWedgeAngleRadians <= 2.0 * Math.PI)
				|| !Double.isFinite(exteriorWedgeAngleRadians)) {
			throw new IllegalArgumentException(
					"exteriorWedgeAngleRadians must be in (pi, 2pi]"
			);
		}
		requirePositiveFinite(speedOfSoundMetersPerSecond,
				"speedOfSoundMetersPerSecond");
	}

	/**
	 * Least-distance source-edge-receiver path, equal to R_ref at eta=0.
	 */
	public double shortestDiffractedPathMeters() {
		return Math.hypot(
				sourceRadialDistanceMeters + receiverRadialDistanceMeters,
				axialSeparationMeters
		);
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
