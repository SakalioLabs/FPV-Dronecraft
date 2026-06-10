package com.tenicana.dronecraft.sim;

public record Vec3(double x, double y, double z) {
	public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

	public Vec3 add(Vec3 other) {
		return new Vec3(x + other.x, y + other.y, z + other.z);
	}

	public Vec3 subtract(Vec3 other) {
		return new Vec3(x - other.x, y - other.y, z - other.z);
	}

	public Vec3 multiply(double scalar) {
		return new Vec3(x * scalar, y * scalar, z * scalar);
	}

	public Vec3 multiply(Vec3 other) {
		return new Vec3(x * other.x, y * other.y, z * other.z);
	}

	public Vec3 divide(Vec3 other) {
		return new Vec3(x / other.x, y / other.y, z / other.z);
	}

	public double dot(Vec3 other) {
		return x * other.x + y * other.y + z * other.z;
	}

	public Vec3 cross(Vec3 other) {
		return new Vec3(
				y * other.z - z * other.y,
				z * other.x - x * other.z,
				x * other.y - y * other.x
		);
	}

	public double lengthSquared() {
		return dot(this);
	}

	public double length() {
		return Math.sqrt(lengthSquared());
	}

	public boolean isFinite() {
		return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
	}

	public Vec3 normalized() {
		double length = length();
		if (length < 1.0e-9) {
			return ZERO;
		}
		return multiply(1.0 / length);
	}

	public Vec3 clamp(double min, double max) {
		return new Vec3(
				MathUtil.clamp(x, min, max),
				MathUtil.clamp(y, min, max),
				MathUtil.clamp(z, min, max)
		);
	}
}
