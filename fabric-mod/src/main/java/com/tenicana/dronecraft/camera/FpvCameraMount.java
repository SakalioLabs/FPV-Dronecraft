package com.tenicana.dronecraft.camera;

import net.minecraft.world.phys.Vec3;

public final class FpvCameraMount {
	public static final double COLLISION_CLEARANCE_METERS = 0.08;

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

	private static boolean finite(Vec3 vector) {
		return vector != null
				&& Double.isFinite(vector.x())
				&& Double.isFinite(vector.y())
				&& Double.isFinite(vector.z());
	}
}
