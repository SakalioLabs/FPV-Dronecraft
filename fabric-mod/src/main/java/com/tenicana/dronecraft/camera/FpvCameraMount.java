package com.tenicana.dronecraft.camera;

import net.minecraft.world.phys.Vec3;

public final class FpvCameraMount {
	public static final double COLLISION_CLEARANCE_METERS = 0.08;
	public static final double MIN_CLEAR_FORWARD_OFFSET_METERS = 1.10;
	public static final double MIN_CLEAR_UP_OFFSET_METERS = 0.66;

	private FpvCameraMount() {
	}

	public static Vec3 retreatFromHit(Vec3 origin, Vec3 desired, Vec3 hit, double clearanceMeters) {
		if (!finite(origin) || !finite(desired) || !finite(hit)) {
			return desired;
		}
		Vec3 originToDesired = desired.subtract(origin);
		double length = originToDesired.length();
		if (length <= 1.0e-6) {
			return desired;
		}
		double clearance = Double.isFinite(clearanceMeters) ? Math.max(0.0, clearanceMeters) : COLLISION_CLEARANCE_METERS;
		double hitDistance = hit.subtract(origin).length();
		double safeDistance = Math.max(0.0, Math.min(length, hitDistance - clearance));
		if (safeDistance >= length) {
			return desired;
		}
		return origin.add(originToDesired.scale(safeDistance / length));
	}

	public static double clearForwardOffset(double configuredMeters) {
		if (!Double.isFinite(configuredMeters)) {
			return MIN_CLEAR_FORWARD_OFFSET_METERS;
		}
		return Math.max(MIN_CLEAR_FORWARD_OFFSET_METERS, configuredMeters);
	}

	public static double clearForwardOffset(double configuredMeters, double forwardShakeMeters) {
		double shake = Double.isFinite(forwardShakeMeters) ? forwardShakeMeters : 0.0;
		return clearForwardOffset(clearForwardOffset(configuredMeters) - shake);
	}

	public static double clearUpOffset(double configuredMeters) {
		if (!Double.isFinite(configuredMeters)) {
			return MIN_CLEAR_UP_OFFSET_METERS;
		}
		return Math.max(MIN_CLEAR_UP_OFFSET_METERS, configuredMeters);
	}

	public static double clearUpOffset(double configuredMeters, double verticalShakeMeters) {
		double shake = Double.isFinite(verticalShakeMeters) ? verticalShakeMeters : 0.0;
		return clearUpOffset(clearUpOffset(configuredMeters) + shake);
	}

	private static boolean finite(Vec3 vector) {
		return vector != null
				&& Double.isFinite(vector.x())
				&& Double.isFinite(vector.y())
				&& Double.isFinite(vector.z());
	}
}
