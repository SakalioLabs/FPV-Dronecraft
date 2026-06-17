package com.tenicana.dronecraft.client.control;

import net.minecraft.util.Mth;

final class ControlInputSmoother {
	private float pitch;
	private float roll;
	private float yaw;

	Axes sample(float targetPitch, float targetRoll, float targetYaw, float risePerTick, float fallPerTick) {
		float safeRise = sanitizeStep(risePerTick, 0.075f);
		float safeFall = sanitizeStep(fallPerTick, 0.18f);
		pitch = approachAxis(pitch, targetPitch, safeRise, safeFall);
		roll = approachAxis(roll, targetRoll, safeRise, safeFall);
		yaw = approachAxis(yaw, targetYaw, safeRise, safeFall);
		return new Axes(pitch, roll, yaw);
	}

	void reset() {
		pitch = 0.0f;
		roll = 0.0f;
		yaw = 0.0f;
	}

	private static float approachAxis(float current, float target, float risePerTick, float fallPerTick) {
		float safeTarget = Mth.clamp(target, -1.0f, 1.0f);
		float step = shouldUseRiseStep(current, safeTarget) ? risePerTick : fallPerTick;
		if (current < safeTarget) {
			return Math.min(safeTarget, current + step);
		}
		if (current > safeTarget) {
			return Math.max(safeTarget, current - step);
		}
		return current;
	}

	private static boolean shouldUseRiseStep(float current, float target) {
		if (Math.abs(target) <= Math.abs(current)) {
			return false;
		}
		return current == 0.0f || target == 0.0f || Math.signum(current) == Math.signum(target);
	}

	private static float sanitizeStep(float value, float fallback) {
		if (!Float.isFinite(value) || value <= 0.0f) {
			return fallback;
		}
		return Mth.clamp(value, 0.01f, 1.0f);
	}

	record Axes(float pitch, float roll, float yaw) {
	}
}
