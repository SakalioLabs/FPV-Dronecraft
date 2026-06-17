package com.tenicana.dronecraft.client.control;

final class DroneArmSafety {
	static final float MOMENTARY_ARM_THROTTLE_MAX = 0.10f;
	static final float MOMENTARY_ARM_AXIS_MAX = 0.25f;
	static final float STICK_GESTURE_THROTTLE_MAX = 0.10f;
	static final float STICK_GESTURE_AXIS_MIN = 0.72f;

	private DroneArmSafety() {
	}

	static boolean canArmFromMomentaryControl(float throttle, float pitch, float roll, float yaw) {
		return finite(throttle, pitch, roll, yaw)
				&& throttle <= MOMENTARY_ARM_THROTTLE_MAX
				&& Math.abs(pitch) <= MOMENTARY_ARM_AXIS_MAX
				&& Math.abs(roll) <= MOMENTARY_ARM_AXIS_MAX
				&& Math.abs(yaw) <= MOMENTARY_ARM_AXIS_MAX;
	}

	static boolean isStickArmGesture(float throttle, float pitch, float roll, float yaw) {
		return finite(throttle, pitch, roll, yaw)
				&& throttle <= STICK_GESTURE_THROTTLE_MAX
				&& yaw <= -STICK_GESTURE_AXIS_MIN
				&& pitch <= -STICK_GESTURE_AXIS_MIN
				&& roll >= STICK_GESTURE_AXIS_MIN;
	}

	private static boolean finite(float throttle, float pitch, float roll, float yaw) {
		return Float.isFinite(throttle)
				&& Float.isFinite(pitch)
				&& Float.isFinite(roll)
				&& Float.isFinite(yaw);
	}
}
