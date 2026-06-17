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

		Attitude attitude = attitude(mode, profile, safePitch, safeRoll, safePrevious);
		float throttleAuthority = 0.75f + 0.25f * clamp(safeThrottle / Math.max(0.10f, safeHover), 0.0f, 1.35f);
		float targetVelocityX = clamp(attitude.rollRadians() / profile.maxRollRadians(), -1.0f, 1.0f)
				* profile.horizontalSpeedMetersPerSecond()
				* throttleAuthority;
		float targetVelocityZ = -clamp(attitude.pitchRadians() / profile.maxPitchRadians(), -1.0f, 1.0f)
				* profile.horizontalSpeedMetersPerSecond()
				* throttleAuthority;
		float targetVelocityY = verticalVelocity(safeThrottle, safeHover, profile);
		if (nearGroundLocked && targetVelocityY < 0.0f) {
			targetVelocityY = 0.0f;
		}
		if (safeThrottle > safeHover && targetVelocityY > 0.0f && targetVelocityY < THRUST_MIN_CLIMB) {
			targetVelocityY = THRUST_MIN_CLIMB;
		}

		float velocityX = smooth(safePrevious.velocityX(), targetVelocityX, velocitySmoothing(safePrevious.velocityX(), targetVelocityX, profile));
		float velocityY = smooth(safePrevious.velocityY(), targetVelocityY, velocitySmoothing(safePrevious.velocityY(), targetVelocityY, profile));
		float velocityZ = smooth(safePrevious.velocityZ(), targetVelocityZ, velocitySmoothing(safePrevious.velocityZ(), targetVelocityZ, profile));
		velocityX = clamp(velocityX, -profile.horizontalSpeedLimitMetersPerSecond(), profile.horizontalSpeedLimitMetersPerSecond());
		velocityY = clamp(velocityY, -VERTICAL_SPEED_LIMIT, VERTICAL_SPEED_LIMIT);
		velocityZ = clamp(velocityZ, -profile.horizontalSpeedLimitMetersPerSecond(), profile.horizontalSpeedLimitMetersPerSecond());
		if (nearGroundLocked && velocityY < 0.0f) {
			velocityY = 0.0f;
		}

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
				attitude.pitchRadians(),
				attitude.rollRadians(),
				yawDegreesPerTick,
				motorPower,
				averageRpm
		);
	}

	private static Attitude attitude(FlightMode mode, Profile profile, float pitch, float roll, State previous) {
		FlightMode safeMode = mode == null ? FlightMode.HORIZON : mode;
		return switch (safeMode) {
			case ANGLE -> angleAttitude(profile, pitch, roll, previous);
			case HORIZON -> horizonAttitude(profile, pitch, roll, previous);
			case ACRO -> new Attitude(
					heldRateAttitude(previous.pitchRadians(), pitch, profile.pitchRateRadiansPerTick(), profile.acroHoldDamping(), profile.maxAcroPitchRadians()),
					heldRateAttitude(previous.rollRadians(), roll, profile.rollRateRadiansPerTick(), profile.acroHoldDamping(), profile.maxAcroRollRadians())
			);
		};
	}

	private static Attitude angleAttitude(Profile profile, float pitch, float roll, State previous) {
		return new Attitude(
				smoothLimited(
						previous.pitchRadians(),
						pitch * profile.maxPitchRadians(),
						attitudeSmoothing(pitch, profile),
						attitudeStepLimitRadians(pitch, profile)
				),
				smoothLimited(
						previous.rollRadians(),
						roll * profile.maxRollRadians(),
						attitudeSmoothing(roll, profile),
						attitudeStepLimitRadians(roll, profile)
				)
		);
	}

	private static Attitude horizonAttitude(Profile profile, float pitch, float roll, State previous) {
		float pitchBlend = smoothStep((Math.abs(pitch) - 0.62f) / 0.33f);
		float rollBlend = smoothStep((Math.abs(roll) - 0.62f) / 0.33f);
		float anglePitch = smoothLimited(
				previous.pitchRadians(),
				pitch * profile.maxPitchRadians(),
				attitudeSmoothing(pitch, profile),
				attitudeStepLimitRadians(pitch, profile)
		);
		float angleRoll = smoothLimited(
				previous.rollRadians(),
				roll * profile.maxRollRadians(),
				attitudeSmoothing(roll, profile),
				attitudeStepLimitRadians(roll, profile)
		);
		float ratePitch = heldRateAttitude(previous.pitchRadians(), pitch, profile.pitchRateRadiansPerTick(), profile.acroHoldDamping(), profile.maxAcroPitchRadians());
		float rateRoll = heldRateAttitude(previous.rollRadians(), roll, profile.rollRateRadiansPerTick(), profile.acroHoldDamping(), profile.maxAcroRollRadians());
		return new Attitude(
				lerp(anglePitch, ratePitch, pitchBlend),
				lerp(angleRoll, rateRoll, rollBlend)
		);
	}

	private static float attitudeSmoothing(float command, Profile profile) {
		return Math.abs(command) < 0.035f ? profile.attitudeRecenterSmoothing() : profile.attitudeSmoothing();
	}

	private static float attitudeStepLimitRadians(float command, Profile profile) {
		return Math.abs(command) < 0.035f ? profile.attitudeRecenterStepLimitRadians() : profile.attitudeStepLimitRadians();
	}

	private static float heldRateAttitude(float previousRadians, float command, float rateRadiansPerTick, float centerDamping, float limitRadians) {
		float updated = previousRadians + command * rateRadiansPerTick;
		if (Math.abs(command) < 0.035f) {
			updated *= centerDamping;
		}
		return clamp(updated, -limitRadians, limitRadians);
	}

	private static float verticalVelocity(float throttle, float hoverThrottle, Profile profile) {
		if (throttle <= THRUST_DEADZONE) {
			return -profile.descentGain();
		}
		float centered = throttle - hoverThrottle;
		if (Math.abs(centered) < profile.hoverBand()) {
			return 0.0f;
		}
		if (centered > 0.0f) {
			return centered / Math.max(0.10f, 1.0f - hoverThrottle) * profile.thrustGain();
		}
		return centered / Math.max(0.10f, hoverThrottle) * profile.descentGain();
	}

	private static float smooth(float current, float target, float smoothing) {
		return current + (target - current) * clamp(smoothing, 0.0f, 1.0f);
	}

	private static float velocitySmoothing(float current, float target, Profile profile) {
		boolean reversing = Math.signum(current) != 0.0f
				&& Math.signum(target) != 0.0f
				&& Math.signum(current) != Math.signum(target);
		boolean braking = reversing || Math.abs(target) < Math.abs(current);
		return braking ? profile.velocityBrakeSmoothing() : profile.velocitySmoothing();
	}

	private static float smoothLimited(float current, float target, float smoothing, float maxStep) {
		float next = smooth(current, target, smoothing);
		float delta = clamp(next - current, -Math.max(0.0f, maxStep), Math.max(0.0f, maxStep));
		return current + delta;
	}

	private static float lerp(float a, float b, float t) {
		float clamped = clamp(t, 0.0f, 1.0f);
		return a + (b - a) * clamped;
	}

	private static float smoothStep(float value) {
		float clamped = clamp(value, 0.0f, 1.0f);
		return clamped * clamped * (3.0f - 2.0f * clamped);
	}

	private static float clamp(float value, float min, float max) {
		if (!Float.isFinite(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	record State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians) {
		static final State ZERO = new State(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);

		State(float velocityX, float velocityY, float velocityZ) {
			this(velocityX, velocityY, velocityZ, 0.0f, 0.0f);
		}
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

	private record Attitude(float pitchRadians, float rollRadians) {
	}

	private record Profile(
			float horizontalSpeedMetersPerSecond,
			float horizontalSpeedLimitMetersPerSecond,
			float maxPitchRadians,
			float maxRollRadians,
			float maxAcroPitchRadians,
			float maxAcroRollRadians,
			float pitchRateRadiansPerTick,
			float rollRateRadiansPerTick,
			float yawDegreesPerTick,
			float attitudeSmoothing,
			float attitudeStepLimitRadians,
			float attitudeRecenterSmoothing,
			float attitudeRecenterStepLimitRadians,
			float acroHoldDamping,
			float velocitySmoothing,
			float velocityBrakeSmoothing,
			float hoverBand,
			float descentGain,
			float thrustGain
	) {
		private static Profile forMode(FlightMode mode) {
			return switch (mode == null ? FlightMode.HORIZON : mode) {
				case ANGLE -> new Profile(0.60f, 0.88f, radians(12.0f), radians(12.0f), radians(28.0f), radians(30.0f), radians(1.1f), radians(1.2f), 0.48f, 0.12f, radians(0.65f), 0.46f, radians(3.0f), 0.78f, 0.12f, 0.30f, 0.055f, 0.62f, 1.45f);
				case HORIZON -> new Profile(1.25f, 1.65f, radians(26.0f), radians(28.0f), radians(48.0f), radians(52.0f), radians(2.6f), radians(2.9f), 1.55f, 0.16f, radians(1.85f), 0.28f, radians(3.0f), 0.88f, 0.20f, 0.26f, HOVER_BAND, DESCENT_GAIN, THRUST_GAIN);
				case ACRO -> new Profile(1.85f, 2.35f, radians(46.0f), radians(50.0f), radians(68.0f), radians(72.0f), radians(4.8f), radians(5.3f), 2.70f, 0.16f, radians(3.80f), 0.16f, radians(3.80f), 0.995f, 0.22f, 0.22f, 0.030f, 1.10f, 2.80f);
			};
		}

		private static float radians(float degrees) {
			return (float) Math.toRadians(degrees);
		}
	}
}
