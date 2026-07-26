package com.tenicana.dronecraft.acoustics.propagation;

/**
 * Closed axis-aligned support domain for an optional spatial calibration.
 * Construction may allocate; repeated support queries do not.
 */
public record SpatialSupportBounds(
		double minimumX,
		double minimumY,
		double minimumZ,
		double maximumX,
		double maximumY,
		double maximumZ
) {
	public SpatialSupportBounds {
		requireFinite(minimumX, "minimumX");
		requireFinite(minimumY, "minimumY");
		requireFinite(minimumZ, "minimumZ");
		requireFinite(maximumX, "maximumX");
		requireFinite(maximumY, "maximumY");
		requireFinite(maximumZ, "maximumZ");
		if (minimumX > maximumX || minimumY > maximumY
				|| minimumZ > maximumZ) {
			throw new IllegalArgumentException(
					"minimum support coordinates must not exceed maxima"
			);
		}
	}

	public boolean supportsPair(
			double sourceX,
			double sourceY,
			double sourceZ,
			double listenerX,
			double listenerY,
			double listenerZ
	) {
		return contains(sourceX, sourceY, sourceZ)
				&& contains(listenerX, listenerY, listenerZ);
	}

	public boolean contains(double x, double y, double z) {
		return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
				&& x >= minimumX && x <= maximumX
				&& y >= minimumY && y <= maximumY
				&& z >= minimumZ && z <= maximumZ;
	}

	private static void requireFinite(double value, String label) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(label + " must be finite");
		}
	}
}
