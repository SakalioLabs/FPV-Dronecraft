package com.tenicana.dronecraft.entity;

final class PlayableMovementYaw {
	static final float APPLY_EPSILON_DEGREES = 0.02f;

	private PlayableMovementYaw() {
	}

	static float midpointForTick(float currentYawDegrees, float yawDegreesPerTick) {
		if (!Float.isFinite(currentYawDegrees)) {
			currentYawDegrees = 0.0f;
		}
		if (!Float.isFinite(yawDegreesPerTick) || Math.abs(yawDegreesPerTick) <= APPLY_EPSILON_DEGREES) {
			return currentYawDegrees;
		}
		return currentYawDegrees + yawDegreesPerTick * 0.5f;
	}
}
