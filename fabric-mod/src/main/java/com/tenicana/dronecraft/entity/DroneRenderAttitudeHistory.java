package com.tenicana.dronecraft.entity;

/** Keeps received telemetry separate from the attitude interval currently being rendered. */
final class DroneRenderAttitudeHistory {
	private float targetPitch;
	private float targetYaw;
	private float targetRoll;
	private float previousPitch;
	private float previousYaw;
	private float previousRoll;
	private float currentPitch;
	private float currentYaw;
	private float currentRoll;
	private boolean initialized;

	void receive(float pitch, float yaw, float roll) {
		targetPitch = pitch;
		targetYaw = yaw;
		targetRoll = roll;
	}

	void tick() {
		if (!initialized) {
			reset(targetPitch, targetYaw, targetRoll);
			return;
		}
		previousPitch = currentPitch;
		previousYaw = currentYaw;
		previousRoll = currentRoll;
		currentPitch = targetPitch;
		currentYaw = targetYaw;
		currentRoll = targetRoll;
	}

	void reset(float pitch, float yaw, float roll) {
		receive(pitch, yaw, roll);
		previousPitch = currentPitch = pitch;
		previousYaw = currentYaw = yaw;
		previousRoll = currentRoll = roll;
		initialized = true;
	}

	float pitchRadians(float partialTick) {
		return initialized ? interpolateRadians(previousPitch, currentPitch, partialTick) : targetPitch;
	}

	float yawRadians(float partialTick) {
		return initialized ? interpolateRadians(previousYaw, currentYaw, partialTick) : targetYaw;
	}

	float rollRadians(float partialTick) {
		return initialized ? interpolateRadians(previousRoll, currentRoll, partialTick) : targetRoll;
	}

	private static float interpolateRadians(float start, float end, float partialTick) {
		float t = Float.isFinite(partialTick) ? Math.max(0.0f, Math.min(1.0f, partialTick)) : 0.0f;
		float delta = (float) Math.atan2(Math.sin(end - start), Math.cos(end - start));
		return start + delta * t;
	}
}
