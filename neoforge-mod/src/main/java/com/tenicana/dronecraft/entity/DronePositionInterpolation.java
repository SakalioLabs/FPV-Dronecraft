package com.tenicana.dronecraft.entity;

/** One tick of packet interpolation, matching the aircraft's one-tick tracking interval. */
final class DronePositionInterpolation {
	private final DroneEntity entity;
	private boolean pending;
	private double x, y, z;
	private float yaw, pitch;
	DronePositionInterpolation(DroneEntity entity, int ignoredInterval) { this.entity = entity; }
	void target(double x, double y, double z, float yaw, float pitch) {
		this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
		pending = true;
	}
	void interpolate() {
		if (!pending) return;
		entity.setPos(x, y, z);
		entity.setYRot(yaw);
		entity.setXRot(pitch);
		pending = false;
	}
	void cancel() { pending = false; }
}
