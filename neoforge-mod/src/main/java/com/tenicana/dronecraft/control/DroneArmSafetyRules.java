package com.tenicana.dronecraft.control;

public final class DroneArmSafetyRules {
	public static final double MOMENTARY_ARM_THROTTLE_MAX = 0.10;
	public static final double MOMENTARY_ARM_AXIS_MAX = 0.25;
	public static final double STICK_GESTURE_THROTTLE_MAX = 0.10;
	public static final double STICK_GESTURE_AXIS_MIN = 0.72;

	private DroneArmSafetyRules() {
	}

	public static boolean canArmFromMomentaryControl(double throttle, double pitch, double roll, double yaw) {
		return finite(throttle, pitch, roll, yaw)
				&& throttle <= MOMENTARY_ARM_THROTTLE_MAX
				&& Math.abs(pitch) <= MOMENTARY_ARM_AXIS_MAX
				&& Math.abs(roll) <= MOMENTARY_ARM_AXIS_MAX
				&& Math.abs(yaw) <= MOMENTARY_ARM_AXIS_MAX;
	}

	public static boolean isStickArmGesture(double throttle, double pitch, double roll, double yaw) {
		return finite(throttle, pitch, roll, yaw)
				&& throttle <= STICK_GESTURE_THROTTLE_MAX
				&& yaw <= -STICK_GESTURE_AXIS_MIN
				&& pitch <= -STICK_GESTURE_AXIS_MIN
				&& roll >= STICK_GESTURE_AXIS_MIN;
	}

	public static boolean canTransitionToArmed(double throttle, double pitch, double roll, double yaw) {
		return canArmFromMomentaryControl(throttle, pitch, roll, yaw)
				|| isStickArmGesture(throttle, pitch, roll, yaw);
	}

	private static boolean finite(double throttle, double pitch, double roll, double yaw) {
		return Double.isFinite(throttle)
				&& Double.isFinite(pitch)
				&& Double.isFinite(roll)
				&& Double.isFinite(yaw);
	}
}
