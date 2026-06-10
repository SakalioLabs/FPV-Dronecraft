package com.tenicana.dronecraft.sim;

public final class MathUtil {
	private MathUtil() {
	}

	public static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public static double squareSigned(double value) {
		return Math.copySign(value * value, value);
	}

	public static double lerp(double from, double to, double amount) {
		return from + (to - from) * amount;
	}

	public static double expSmoothing(double dt, double timeConstant) {
		if (timeConstant <= 1.0e-9) {
			return 1.0;
		}
		return 1.0 - Math.exp(-dt / timeConstant);
	}
}
