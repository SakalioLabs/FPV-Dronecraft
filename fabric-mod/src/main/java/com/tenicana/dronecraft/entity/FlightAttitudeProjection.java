package com.tenicana.dronecraft.entity;

import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

final class FlightAttitudeProjection {
	private static final Vec3 BODY_FORWARD = new Vec3(0.0, 0.0, 1.0);
	private static final double HORIZONTAL_FORWARD_EPSILON = 1.0e-9;

	private FlightAttitudeProjection() {
	}

	static double headingYawDegrees(Quaternion attitude, double fallbackYawDegrees) {
		if (attitude == null || !isFinite(attitude)) {
			return finiteOrZero(fallbackYawDegrees);
		}
		Vec3 forward = attitude.normalized().rotate(BODY_FORWARD);
		if (!isFinite(forward)) {
			return finiteOrZero(fallbackYawDegrees);
		}
		double horizontalForward = Math.hypot(forward.x(), forward.z());
		if (!Double.isFinite(horizontalForward) || horizontalForward <= HORIZONTAL_FORWARD_EPSILON) {
			return finiteOrZero(fallbackYawDegrees);
		}
		double yawDegrees = Math.toDegrees(Math.atan2(forward.x(), forward.z()));
		return Double.isFinite(yawDegrees) ? yawDegrees : finiteOrZero(fallbackYawDegrees);
	}

	private static boolean isFinite(Quaternion quaternion) {
		return Double.isFinite(quaternion.w())
				&& Double.isFinite(quaternion.x())
				&& Double.isFinite(quaternion.y())
				&& Double.isFinite(quaternion.z());
	}

	private static boolean isFinite(Vec3 vector) {
		return Double.isFinite(vector.x())
				&& Double.isFinite(vector.y())
				&& Double.isFinite(vector.z());
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}
}
