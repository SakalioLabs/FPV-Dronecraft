package com.tenicana.dronecraft.camera;

public final class FpvCameraVibration {
	private static final int DEFAULT_BLADE_COUNT = 2;
	private static final int MIN_BLADE_COUNT = 1;
	private static final int MAX_BLADE_COUNT = 8;

	private FpvCameraVibration() {
	}

	public static int sanitizedBladeCount(int bladeCount) {
		if (bladeCount < MIN_BLADE_COUNT) {
			return DEFAULT_BLADE_COUNT;
		}
		return Math.min(bladeCount, MAX_BLADE_COUNT);
	}

	public static float bladePassPhaseRadians(float motorPhaseRadians, int bladeCount, float washPhaseRadians) {
		return motorPhaseRadians * sanitizedBladeCount(bladeCount) + 0.41f * (float) Math.sin(washPhaseRadians);
	}
}
