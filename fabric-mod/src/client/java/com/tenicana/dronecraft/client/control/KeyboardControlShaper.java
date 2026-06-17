package com.tenicana.dronecraft.client.control;

import com.tenicana.dronecraft.sim.ControlStickProfile;

final class KeyboardControlShaper {
	static final float AXIS_RISE_PER_TICK = 0.050f;
	static final float AXIS_FALL_PER_TICK = 0.24f;
	static final float THROTTLE_STEP_PER_TICK = 0.014f;
	static final float THROTTLE_FINE_STEP_PER_TICK = 0.007f;
	static final float THROTTLE_FINE_WINDOW = 0.10f;
	static final float THROTTLE_HOVER_SNAP_MARGIN = 0.004f;

	private KeyboardControlShaper() {
	}

	static float approachAxis(float current, float target) {
		float step = Math.abs(target) > Math.abs(current) ? AXIS_RISE_PER_TICK : AXIS_FALL_PER_TICK;
		if (current < target) {
			return Math.min(target, current + step);
		}
		if (current > target) {
			return Math.max(target, current - step);
		}
		return current;
	}

	static float commandAxis(float value) {
		return (float) ControlStickProfile.keyboardCommand(value);
	}

	static float adjustThrottle(float current, int direction, float hoverThrottle) {
		float safeCurrent = clamp01(current);
		int sign = Integer.compare(direction, 0);
		if (sign == 0) {
			return safeCurrent;
		}

		float safeHover = clamp01(hoverThrottle);
		float next = clamp01(safeCurrent + sign * throttleStep(safeCurrent, safeHover));
		if (sign > 0 && safeCurrent < safeHover && next >= safeHover - THROTTLE_HOVER_SNAP_MARGIN) {
			return safeHover;
		}
		if (sign < 0 && safeCurrent > safeHover && next <= safeHover + THROTTLE_HOVER_SNAP_MARGIN) {
			return safeHover;
		}
		return next;
	}

	private static float throttleStep(float current, float hoverThrottle) {
		return Math.abs(current - hoverThrottle) <= THROTTLE_FINE_WINDOW
				? THROTTLE_FINE_STEP_PER_TICK
				: THROTTLE_STEP_PER_TICK;
	}

	private static float clamp01(float value) {
		if (!Float.isFinite(value)) {
			return 0.0f;
		}
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}
