package com.tenicana.dronecraft.entity;

final class PlayableDebugAxisFilter {
	static final float DEFAULT_RISE_SMOOTHING = 0.14f;
	static final float DEFAULT_FALL_SMOOTHING = 0.68f;
	static final float DEFAULT_THROTTLE_RISE_SMOOTHING = 0.24f;
	static final float DEFAULT_THROTTLE_FALL_SMOOTHING = 0.42f;
	private static final float RELEASE_SNAP = 0.015f;
	private static final float REVERSAL_SNAP = 0.008f;

	private PlayableDebugAxisFilter() {
	}

	static float throttle(float current, float target) {
		return filter(current, target, DEFAULT_THROTTLE_RISE_SMOOTHING, DEFAULT_THROTTLE_FALL_SMOOTHING, false);
	}

	static float filter(float current, float target, float riseSmoothing, float fallSmoothing, boolean keepSign) {
		float safeCurrent = Float.isFinite(current) ? current : 0.0f;
		float safeTarget = Float.isFinite(target) ? target : 0.0f;
		boolean fromCenter = Math.abs(safeCurrent) < 1.0e-5f;
		boolean targetCentered = Math.abs(safeTarget) < 1.0e-5f;
		boolean sameDirection = fromCenter || targetCentered || Math.signum(safeCurrent) == Math.signum(safeTarget);
		boolean rising = sameDirection && Math.abs(safeTarget) > Math.abs(safeCurrent);
		float smoothing = rising ? riseSmoothing : fallSmoothing;
		float filtered = safeCurrent + (safeTarget - safeCurrent) * Math.max(0.0f, Math.min(1.0f, smoothing));
		if (!keepSign) {
			return Math.max(0.0f, filtered);
		}
		if (targetCentered && Math.abs(filtered) <= RELEASE_SNAP) {
			return 0.0f;
		}
		if (!sameDirection && Math.abs(filtered) <= REVERSAL_SNAP) {
			return 0.0f;
		}
		return filtered;
	}
}
