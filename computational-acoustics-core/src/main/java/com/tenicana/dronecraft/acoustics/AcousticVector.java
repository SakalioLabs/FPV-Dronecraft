package com.tenicana.dronecraft.acoustics;

/**
 * Engine-independent vector used at the acoustics module boundary.
 */
public record AcousticVector(double x, double y, double z) {
	public static final AcousticVector ZERO = new AcousticVector(0.0, 0.0, 0.0);

	public AcousticVector {
		requireFinite(x, "x");
		requireFinite(y, "y");
		requireFinite(z, "z");
	}

	public AcousticVector subtract(AcousticVector other) {
		return new AcousticVector(x - other.x, y - other.y, z - other.z);
	}

	public AcousticVector add(AcousticVector other) {
		return new AcousticVector(x + other.x, y + other.y, z + other.z);
	}

	public AcousticVector multiply(double scalar) {
		requireFinite(scalar, "scalar");
		return new AcousticVector(x * scalar, y * scalar, z * scalar);
	}

	public double dot(AcousticVector other) {
		return x * other.x + y * other.y + z * other.z;
	}

	public AcousticVector cross(AcousticVector other) {
		return new AcousticVector(
				y * other.z - z * other.y,
				z * other.x - x * other.z,
				x * other.y - y * other.x
		);
	}

	public double length() {
		return Math.sqrt(dot(this));
	}

	public AcousticVector normalized() {
		double length = length();
		if (length <= 1.0e-12) {
			return ZERO;
		}
		return multiply(1.0 / length);
	}

	private static void requireFinite(double value, String name) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(name + " must be finite");
		}
	}
}
