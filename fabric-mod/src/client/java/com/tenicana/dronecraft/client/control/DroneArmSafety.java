package com.tenicana.dronecraft.client.control;

import com.tenicana.dronecraft.control.DroneArmSafetyRules;

final class DroneArmSafety {
	static final float MOMENTARY_ARM_THROTTLE_MAX = (float) DroneArmSafetyRules.MOMENTARY_ARM_THROTTLE_MAX;
	static final float MOMENTARY_ARM_AXIS_MAX = (float) DroneArmSafetyRules.MOMENTARY_ARM_AXIS_MAX;
	static final float STICK_GESTURE_THROTTLE_MAX = (float) DroneArmSafetyRules.STICK_GESTURE_THROTTLE_MAX;
	static final float STICK_GESTURE_AXIS_MIN = (float) DroneArmSafetyRules.STICK_GESTURE_AXIS_MIN;

	private DroneArmSafety() {
	}

	static boolean canArmFromMomentaryControl(float throttle, float pitch, float roll, float yaw) {
		return DroneArmSafetyRules.canArmFromMomentaryControl(throttle, pitch, roll, yaw);
	}

	static boolean isStickArmGesture(float throttle, float pitch, float roll, float yaw) {
		return DroneArmSafetyRules.isStickArmGesture(throttle, pitch, roll, yaw);
	}
}
