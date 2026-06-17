package com.tenicana.dronecraft.sim;

public final class ControlStickProfile {
	public static final double GAMEPAD_COMMAND_DEADBAND = 0.25;
	public static final double GAMEPAD_COMMAND_EXPO = 0.97;
	public static final double GAMEPAD_THROTTLE_HOVER_STICK = 0.50;
	public static final double GAMEPAD_THROTTLE_HOVER_COMMAND = 0.20;
	public static final double GAMEPAD_THROTTLE_LOW_EXPONENT = 1.60;
	public static final double GAMEPAD_THROTTLE_HIGH_EXPONENT = 1.45;
	public static final double KEYBOARD_COMMAND_EXPO = 0.90;

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
		return gamepadCommand(value, configuredDeadband, GAMEPAD_COMMAND_EXPO, 1.0);
	}

	public static double gamepadCommand(double value, double configuredDeadband, double configuredExpo, double rateScale) {
		double centered = applyDeadband(value, Math.max(GAMEPAD_COMMAND_DEADBAND, configuredDeadband));
		double shaped = expoCommand(centered, configuredExpo);
		return MathUtil.clamp(rateScale, 0.05, 1.0) * shaped;
	}

	public static double gamepadThrottle(double value) {
		double clamped = MathUtil.clamp(value, 0.0, 1.0);
		if (clamped <= GAMEPAD_THROTTLE_HOVER_STICK) {
			double low = clamped / GAMEPAD_THROTTLE_HOVER_STICK;
			return GAMEPAD_THROTTLE_HOVER_COMMAND * Math.pow(low, GAMEPAD_THROTTLE_LOW_EXPONENT);
		}
		double high = (clamped - GAMEPAD_THROTTLE_HOVER_STICK) / (1.0 - GAMEPAD_THROTTLE_HOVER_STICK);
		return GAMEPAD_THROTTLE_HOVER_COMMAND
				+ (1.0 - GAMEPAD_THROTTLE_HOVER_COMMAND) * Math.pow(high, GAMEPAD_THROTTLE_HIGH_EXPONENT);
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
