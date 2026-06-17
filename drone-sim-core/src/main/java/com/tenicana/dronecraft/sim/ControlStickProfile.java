package com.tenicana.dronecraft.sim;

public final class ControlStickProfile {
	public static final double GAMEPAD_COMMAND_DEADBAND = 0.14;
	public static final double GAMEPAD_COMMAND_EXPO = 0.80;
	public static final double GAMEPAD_THROTTLE_EXPONENT = 2.0;
	public static final double KEYBOARD_COMMAND_EXPO = 0.55;

	private ControlStickProfile() {
	}

	public static double applyDeadband(double value, double deadband) {
		double safeDeadband = MathUtil.clamp(deadband, 0.0, 0.95);
		double clamped = MathUtil.clamp(value, -1.0, 1.0);
		double magnitude = Math.abs(clamped);
		if (magnitude <= safeDeadband) {
			return 0.0;
		}
		return Math.copySign((magnitude - safeDeadband) / (1.0 - safeDeadband), clamped);
	}

	public static double gamepadCommand(double value, double configuredDeadband) {
		double centered = applyDeadband(value, Math.max(GAMEPAD_COMMAND_DEADBAND, configuredDeadband));
		return expoCommand(centered, GAMEPAD_COMMAND_EXPO);
	}

	public static double gamepadThrottle(double value) {
		double clamped = MathUtil.clamp(value, 0.0, 1.0);
		return Math.pow(clamped, GAMEPAD_THROTTLE_EXPONENT);
	}

	public static double keyboardCommand(double value) {
		return expoCommand(value, KEYBOARD_COMMAND_EXPO);
	}

	public static double expoCommand(double value, double expo) {
		double clamped = MathUtil.clamp(value, -1.0, 1.0);
		double magnitude = Math.abs(clamped);
		if (magnitude <= 0.0) {
			return 0.0;
		}
		double safeExpo = MathUtil.clamp(expo, 0.0, 1.0);
		double cubic = magnitude * magnitude * magnitude;
		double curved = (1.0 - safeExpo) * magnitude + safeExpo * cubic;
		return Math.copySign(curved, clamped);
	}
}
