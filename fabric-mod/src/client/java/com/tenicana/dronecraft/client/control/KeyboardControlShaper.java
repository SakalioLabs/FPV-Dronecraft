package com.tenicana.dronecraft.client.control;

import com.tenicana.dronecraft.sim.ControlStickProfile;

final class KeyboardControlShaper {
	static final float AXIS_RISE_PER_TICK = 0.050f;
	static final float AXIS_FALL_PER_TICK = 0.24f;

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
}
