package com.tenicana.dronecraft.entity;

import com.tenicana.dronecraft.sim.FlightMode;

final class PlayableFlightModel {
	private static final float DESCENT_GAIN = 1.0f;
	private static final float THRUST_GAIN = 2.5f;
	private static final float THRUST_DEADZONE = 0.005f;
	private static final float THRUST_MIN_CLIMB = 0.025f;
	private static final float VERTICAL_SPEED_LIMIT = 2.8f;
	private static final float HOVER_BAND = 0.035f;
	private static final float IDLE_RPM = 2200.0f;
	private static final float MAX_RPM_DELTA = 13500.0f;

	private PlayableFlightModel() {
	}

	static Step step(
			FlightMode mode,
			float throttle,
			float pitch,
			float roll,
			float yaw,
			float hoverThrottle,
			boolean nearGroundLocked,
			State previous
	) {
		Profile profile = Profile.forMode(mode);
		float safeThrottle = clamp(throttle, 0.0f, 1.0f);
		float safePitch = clamp(pitch, -1.0f, 1.0f);
		float safeRoll = clamp(roll, -1.0f, 1.0f);
		float safeYaw = clamp(yaw, -1.0f, 1.0f);
		float safeHover = clamp(hoverThrottle, 0.12f, 0.55f);
		State safePrevious = previous == null ? State.ZERO : previous;

		float throttleAuthority = 0.75f + 0.25f * clamp(safeThrottle / Math.max(0.10f, safeHover), 0.0f, 1.35f);
		float targetVelocityX = safeRoll * profile.horizontalSpeedMetersPerSecond() * throttleAuthority;
		float targetVelocityZ = -safePitch * profile.horizontalSpeedMetersPerSecond() * throttleAuthority;
		float targetVelocityY = verticalVelocity(safeThrottle, safeHover);
		if (nearGroundLocked && targetVelocityY < 0.0f) {
			targetVelocityY = 0.0f;
		}
		if (safeThrottle > safeHover && targetVelocityY > 0.0f && targetVelocityY < THRUST_MIN_CLIMB) {
			targetVelocityY = THRUST_MIN_CLIMB;
		}

		float velocityX = smooth(safePrevious.velocityX(), targetVelocityX, profile.velocitySmoothing());
		float velocityY = smooth(safePrevious.velocityY(), targetVelocityY, profile.velocitySmoothing());
		float velocityZ = smooth(safePrevious.velocityZ(), targetVelocityZ, profile.velocitySmoothing());
		velocityX = clamp(velocityX, -profile.horizontalSpeedLimitMetersPerSecond(), profile.horizontalSpeedLimitMetersPerSecond());
		velocityY = clamp(velocityY, -VERTICAL_SPEED_LIMIT, VERTICAL_SPEED_LIMIT);
		velocityZ = clamp(velocityZ, -profile.horizontalSpeedLimitMetersPerSecond(), profile.horizontalSpeedLimitMetersPerSecond());
		if (nearGroundLocked && velocityY < 0.0f) {
			velocityY = 0.0f;
		}

		float pitchRadians = safePitch * profile.maxPitchRadians();
		float rollRadians = safeRoll * profile.maxRollRadians();
		float yawDegreesPerTick = safeYaw * profile.yawDegreesPerTick();
		float motorPower = safeThrottle <= THRUST_DEADZONE ? 0.14f : clamp(0.14f + safeThrottle * 0.86f, 0.0f, 1.0f);
		float averageRpm = IDLE_RPM + (float) Math.sqrt(safeThrottle) * MAX_RPM_DELTA;
		return new Step(
				targetVelocityX,
				targetVelocityY,
				targetVelocityZ,
				velocityX,
				velocityY,
				velocityZ,
				pitchRadians,
				rollRadians,
				yawDegreesPerTick,
				motorPower,
				averageRpm
		);
	}

	private static float verticalVelocity(float throttle, float hoverThrottle) {
		if (throttle <= THRUST_DEADZONE) {
			return -DESCENT_GAIN;
		}
		float centered = throttle - hoverThrottle;
		if (Math.abs(centered) < HOVER_BAND) {
			return 0.0f;
		}
		if (centered > 0.0f) {
			return centered / Math.max(0.10f, 1.0f - hoverThrottle) * THRUST_GAIN;
		}
		return centered / Math.max(0.10f, hoverThrottle) * DESCENT_GAIN;
	}

	private static float smooth(float current, float target, float smoothing) {
		return current + (target - current) * clamp(smoothing, 0.0f, 1.0f);
	}

	private static float clamp(float value, float min, float max) {
		if (!Float.isFinite(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	record State(float velocityX, float velocityY, float velocityZ) {
		static final State ZERO = new State(0.0f, 0.0f, 0.0f);
	}

	record Step(
			float targetVelocityX,
			float targetVelocityY,
			float targetVelocityZ,
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float yawDegreesPerTick,
			float motorPower,
			float averageRpm
	) {
	}

	private record Profile(
			float horizontalSpeedMetersPerSecond,
			float horizontalSpeedLimitMetersPerSecond,
			float maxPitchRadians,
			float maxRollRadians,
			float yawDegreesPerTick,
			float velocitySmoothing
	) {
		private static Profile forMode(FlightMode mode) {
			return switch (mode == null ? FlightMode.HORIZON : mode) {
				case ANGLE -> new Profile(1.25f, 1.65f, radians(24.0f), radians(24.0f), 1.75f, 0.30f);
				case HORIZON -> new Profile(1.60f, 2.05f, radians(36.0f), radians(38.0f), 2.35f, 0.26f);
				case ACRO -> new Profile(1.95f, 2.45f, radians(50.0f), radians(55.0f), 3.10f, 0.23f);
			};
		}

		private static float radians(float degrees) {
			return (float) Math.toRadians(degrees);
		}
	}
}
