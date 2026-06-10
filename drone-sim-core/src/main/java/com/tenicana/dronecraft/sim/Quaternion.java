package com.tenicana.dronecraft.sim;

public record Quaternion(double w, double x, double y, double z) {
	public static final Quaternion IDENTITY = new Quaternion(1.0, 0.0, 0.0, 0.0);

	public Quaternion multiply(Quaternion other) {
		return new Quaternion(
				w * other.w - x * other.x - y * other.y - z * other.z,
				w * other.x + x * other.w + y * other.z - z * other.y,
				w * other.y - x * other.z + y * other.w + z * other.x,
				w * other.z + x * other.y - y * other.x + z * other.w
		);
	}

	public Quaternion multiply(double scalar) {
		return new Quaternion(w * scalar, x * scalar, y * scalar, z * scalar);
	}

	public Quaternion add(Quaternion other) {
		return new Quaternion(w + other.w, x + other.x, y + other.y, z + other.z);
	}

	public Quaternion conjugate() {
		return new Quaternion(w, -x, -y, -z);
	}

	public double length() {
		return Math.sqrt(w * w + x * x + y * y + z * z);
	}

	public Quaternion normalized() {
		double length = length();
		if (length < 1.0e-9) {
			return IDENTITY;
		}
		return multiply(1.0 / length);
	}

	public Vec3 rotate(Vec3 vector) {
		Quaternion result = multiply(new Quaternion(0.0, vector.x(), vector.y(), vector.z())).multiply(conjugate());
		return new Vec3(result.x, result.y, result.z);
	}

	public Quaternion integrateBodyAngularVelocity(Vec3 angularVelocityBody, double dt) {
		Quaternion omega = new Quaternion(0.0, angularVelocityBody.x(), angularVelocityBody.y(), angularVelocityBody.z());
		return add(multiply(omega).multiply(0.5 * dt)).normalized();
	}

	public Vec3 toEulerXYZRadians() {
		double sinXCosY = 2.0 * (w * x - y * z);
		double cosXCosY = 1.0 - 2.0 * (x * x + y * y);
		double xAngle = Math.atan2(sinXCosY, cosXCosY);

		double sinY = 2.0 * (w * y + z * x);
		double yAngle = Math.abs(sinY) >= 1.0 ? Math.copySign(Math.PI / 2.0, sinY) : Math.asin(sinY);

		double sinZCosY = 2.0 * (w * z - x * y);
		double cosZCosY = 1.0 - 2.0 * (y * y + z * z);
		double zAngle = Math.atan2(sinZCosY, cosZCosY);

		return new Vec3(xAngle, yAngle, zAngle);
	}
}
