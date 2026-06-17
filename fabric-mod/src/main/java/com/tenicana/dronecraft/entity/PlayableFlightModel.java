package com.tenicana.dronecraft.entity;

import com.tenicana.dronecraft.sim.FlightMode;

final class PlayableFlightModel {
	private static final float DESCENT_GAIN = 1.0f;
	private static final float THRUST_GAIN = 2.5f;
	private static final float THRUST_DEADZONE = 0.005f;
	private static final float THRUST_MIN_CLIMB = 0.025f;
	private static final float VERTICAL_SPEED_LIMIT = 2.8f;
	private static final float HOVER_BAND = 0.035f;
	private static final float PLAYABLE_AXIS_NOISE_EPSILON = 0.006f;
	private static final float VELOCITY_SETTLE_EPSILON_MPS = 0.018f;
	private static final float ATTITUDE_SETTLE_EPSILON_RADIANS = (float) Math.toRadians(0.20f);
	private static final float YAW_SETTLE_EPSILON_DEGREES_PER_TICK = 0.020f;
	private static final float IDLE_RPM = 2200.0f;
	private static final float MAX_RPM_DELTA = 13500.0f;
	private static final float MODE_SWITCH_ANGLE_ATTITUDE_KEEP = 0.18f;
	private static final float MODE_SWITCH_HORIZON_ATTITUDE_KEEP = 0.24f;
	private static final float MODE_SWITCH_ACRO_ATTITUDE_KEEP = 1.0f;
	private static final float MODE_SWITCH_ANGLE_YAW_KEEP = 0.10f;
	private static final float MODE_SWITCH_HORIZON_YAW_KEEP = 0.18f;
	private static final float MODE_SWITCH_ACRO_YAW_KEEP = 0.45f;
	private static final float MODE_SWITCH_ANGLE_HORIZONTAL_KEEP = 0.45f;
	private static final float MODE_SWITCH_HORIZON_HORIZONTAL_KEEP = 0.62f;
	private static final float MODE_SWITCH_ACRO_HORIZONTAL_KEEP = 0.82f;
	private static final float LOW_THROTTLE_ANGLE_HORIZONTAL_AUTHORITY = 0.34f;
	private static final float LOW_THROTTLE_HORIZON_HORIZONTAL_AUTHORITY = 0.42f;
	private static final float LOW_THROTTLE_ACRO_HORIZONTAL_AUTHORITY = 0.60f;
	private static final float MAX_HIGH_THROTTLE_HORIZONTAL_BOOST = 0.10f;
	private static final float GROUND_ANGLE_HORIZONTAL_AUTHORITY_SCALE = 0.55f;
	private static final float GROUND_HORIZON_HORIZONTAL_AUTHORITY_SCALE = 0.62f;
	private static final float GROUND_ACRO_HORIZONTAL_AUTHORITY_SCALE = 0.74f;

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
		return step(mode, throttle, pitch, roll, yaw, hoverThrottle, nearGroundLocked, 1.0f, previous);
	}

	static Step step(
			FlightMode mode,
			float throttle,
			float pitch,
			float roll,
			float yaw,
			float hoverThrottle,
			boolean nearGroundLocked,
			float lowAltitudeHorizontalAuthorityScale,
			State previous
	) {
		FlightMode safeMode = mode == null ? FlightMode.HORIZON : mode;
		Profile profile = Profile.forMode(safeMode);
		float safeThrottle = clamp(throttle, 0.0f, 1.0f);
		float safePitch = clamp(pitch, -1.0f, 1.0f);
		float safeRoll = clamp(roll, -1.0f, 1.0f);
		float safeYaw = clamp(yaw, -1.0f, 1.0f);
		float safeHover = clamp(hoverThrottle, 0.12f, 0.55f);
		float safeLowAltitudeHorizontalScale = clamp(lowAltitudeHorizontalAuthorityScale, 0.0f, 1.0f);
		State safePrevious = previousStateForMode(safeMode, profile, previous);

		float attitudeCommandAuthority = lowAltitudeAttitudeCommandAuthority(safeMode, nearGroundLocked, safeLowAltitudeHorizontalScale);
		float yawCommandAuthority = lowAltitudeYawCommandAuthority(safeMode, nearGroundLocked, safeLowAltitudeHorizontalScale);
		float attitudePitch = safePitch * attitudeCommandAuthority;
		float attitudeRoll = safeRoll * attitudeCommandAuthority;
		float yawCommand = safeYaw * yawCommandAuthority;

		Attitude attitude = attitude(safeMode, profile, attitudePitch, attitudeRoll, safePrevious);
		float pitchRadians = settledAttitude(safeMode, attitudePitch, attitude.pitchRadians());
		float rollRadians = settledAttitude(safeMode, attitudeRoll, attitude.rollRadians());
		float throttleAuthority = horizontalThrottleAuthority(safeMode, safeThrottle, safeHover, nearGroundLocked, safeLowAltitudeHorizontalScale, profile);
		float targetVelocityX = clamp(rollRadians / profile.maxRollRadians(), -1.0f, 1.0f)
				* profile.horizontalSpeedMetersPerSecond()
				* throttleAuthority;
		float targetVelocityZ = -clamp(pitchRadians / profile.maxPitchRadians(), -1.0f, 1.0f)
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
		velocityX = settledVelocity(velocityX, targetVelocityX);
		velocityY = settledVelocity(velocityY, targetVelocityY);
		velocityZ = settledVelocity(velocityZ, targetVelocityZ);
		if (nearGroundLocked && velocityY < 0.0f) {
			velocityY = 0.0f;
		}
		if (nearGroundLocked && shouldGroundCatchVertical(safeThrottle, safeHover, targetVelocityY, profile)) {
			velocityY = 0.0f;
		}
		if (nearGroundLocked && shouldGroundDamp(safeThrottle, safeHover, targetVelocityX, targetVelocityZ, profile)) {
			velocityX = smooth(velocityX, 0.0f, profile.groundFrictionSmoothing());
			velocityZ = smooth(velocityZ, 0.0f, profile.groundFrictionSmoothing());
		}
		if (shouldAirBrake(safeMode, safeThrottle, safeHover, safePitch, safeRoll, profile)) {
			velocityX = smooth(velocityX, 0.0f, profile.airBrakeSmoothing());
			velocityZ = smooth(velocityZ, 0.0f, profile.airBrakeSmoothing());
		}
		velocityX = settledVelocity(velocityX, targetVelocityX);
		velocityY = settledVelocity(velocityY, targetVelocityY);
		velocityZ = settledVelocity(velocityZ, targetVelocityZ);

		float targetYawDegreesPerTick = yawCommand * profile.yawDegreesPerTick();
		float yawDegreesPerTick = smooth(
				safePrevious.yawDegreesPerTick(),
				targetYawDegreesPerTick,
				yawSmoothing(safePrevious.yawDegreesPerTick(), targetYawDegreesPerTick, profile)
		);
		yawDegreesPerTick = settledYawRate(yawDegreesPerTick, targetYawDegreesPerTick);
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
				averageRpm,
				safeMode
		);
	}

	static float playableAxisCommand(float value) {
		if (!Float.isFinite(value)) {
			return 0.0f;
		}
		float clamped = clamp(value, -1.0f, 1.0f);
		return Math.abs(clamped) <= PLAYABLE_AXIS_NOISE_EPSILON ? 0.0f : clamped;
	}

	private static State previousStateForMode(FlightMode mode, Profile profile, State previous) {
		if (previous == null) {
			return State.zero(mode);
		}
		if (previous.mode() == mode) {
			return previous;
		}
		return new State(
				previous.velocityX() * modeSwitchHorizontalKeep(mode),
				previous.velocityY(),
				previous.velocityZ() * modeSwitchHorizontalKeep(mode),
				clamp(previous.pitchRadians() * modeSwitchAttitudeKeep(mode), -profile.maxPitchRadians(), profile.maxPitchRadians()),
				clamp(previous.rollRadians() * modeSwitchAttitudeKeep(mode), -profile.maxRollRadians(), profile.maxRollRadians()),
				previous.yawDegreesPerTick() * modeSwitchYawKeep(mode),
				mode
		);
	}

	private static float modeSwitchAttitudeKeep(FlightMode mode) {
		return switch (mode) {
			case ANGLE -> MODE_SWITCH_ANGLE_ATTITUDE_KEEP;
			case HORIZON -> MODE_SWITCH_HORIZON_ATTITUDE_KEEP;
			case ACRO -> MODE_SWITCH_ACRO_ATTITUDE_KEEP;
		};
	}

	private static float modeSwitchYawKeep(FlightMode mode) {
		return switch (mode) {
			case ANGLE -> MODE_SWITCH_ANGLE_YAW_KEEP;
			case HORIZON -> MODE_SWITCH_HORIZON_YAW_KEEP;
			case ACRO -> MODE_SWITCH_ACRO_YAW_KEEP;
		};
	}

	private static float modeSwitchHorizontalKeep(FlightMode mode) {
		return switch (mode) {
			case ANGLE -> MODE_SWITCH_ANGLE_HORIZONTAL_KEEP;
			case HORIZON -> MODE_SWITCH_HORIZON_HORIZONTAL_KEEP;
			case ACRO -> MODE_SWITCH_ACRO_HORIZONTAL_KEEP;
		};
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

	private static float horizontalThrottleAuthority(
			FlightMode mode,
			float throttle,
			float hoverThrottle,
			boolean nearGroundLocked,
			float lowAltitudeHorizontalAuthorityScale,
			Profile profile
	) {
		float liftWindowTop = hoverThrottle + profile.hoverBand() * 2.0f;
		float liftProgress = smoothStep((throttle - THRUST_DEADZONE) / Math.max(0.10f, liftWindowTop - THRUST_DEADZONE));
		float lowThrottleAuthority = lowThrottleHorizontalAuthority(mode);
		float authority = lerp(lowThrottleAuthority, 1.0f, liftProgress);
		float highThrottleProgress = smoothStep((throttle - liftWindowTop) / Math.max(0.16f, 0.50f - liftWindowTop));
		authority += highThrottleProgress * MAX_HIGH_THROTTLE_HORIZONTAL_BOOST;
		authority *= lowAltitudeHorizontalAuthorityScale;
		if (nearGroundLocked && throttle <= liftWindowTop) {
			authority *= groundHorizontalAuthorityScale(mode);
		}
		return clamp(authority, 0.0f, 1.10f);
	}

	private static float lowAltitudeAttitudeCommandAuthority(FlightMode mode, boolean nearGroundLocked, float lowAltitudeHorizontalAuthorityScale) {
		float minimum = switch (mode == null ? FlightMode.HORIZON : mode) {
			case ANGLE -> 0.68f;
			case HORIZON -> 0.76f;
			case ACRO -> 0.90f;
		};
		return lowAltitudeCommandAuthority(minimum, nearGroundLocked, lowAltitudeHorizontalAuthorityScale);
	}

	private static float lowAltitudeYawCommandAuthority(FlightMode mode, boolean nearGroundLocked, float lowAltitudeHorizontalAuthorityScale) {
		float minimum = switch (mode == null ? FlightMode.HORIZON : mode) {
			case ANGLE -> 0.66f;
			case HORIZON -> 0.72f;
			case ACRO -> 0.86f;
		};
		return lowAltitudeCommandAuthority(minimum, nearGroundLocked, lowAltitudeHorizontalAuthorityScale);
	}

	private static float lowAltitudeCommandAuthority(float minimum, boolean nearGroundLocked, float lowAltitudeHorizontalAuthorityScale) {
		float safeMinimum = clamp(minimum, 0.0f, 1.0f);
		if (nearGroundLocked) {
			return safeMinimum;
		}
		return lerp(safeMinimum, 1.0f, lowAltitudeHorizontalAuthorityScale);
	}

	private static float lowThrottleHorizontalAuthority(FlightMode mode) {
		return switch (mode == null ? FlightMode.HORIZON : mode) {
			case ANGLE -> LOW_THROTTLE_ANGLE_HORIZONTAL_AUTHORITY;
			case HORIZON -> LOW_THROTTLE_HORIZON_HORIZONTAL_AUTHORITY;
			case ACRO -> LOW_THROTTLE_ACRO_HORIZONTAL_AUTHORITY;
		};
	}

	private static float groundHorizontalAuthorityScale(FlightMode mode) {
		return switch (mode == null ? FlightMode.HORIZON : mode) {
			case ANGLE -> GROUND_ANGLE_HORIZONTAL_AUTHORITY_SCALE;
			case HORIZON -> GROUND_HORIZON_HORIZONTAL_AUTHORITY_SCALE;
			case ACRO -> GROUND_ACRO_HORIZONTAL_AUTHORITY_SCALE;
		};
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

	private static boolean shouldGroundDamp(float throttle, float hoverThrottle, float targetVelocityX, float targetVelocityZ, Profile profile) {
		return throttle <= hoverThrottle + profile.hoverBand()
				&& Math.abs(targetVelocityX) <= profile.groundFrictionTargetVelocityThreshold()
				&& Math.abs(targetVelocityZ) <= profile.groundFrictionTargetVelocityThreshold();
	}

	private static boolean shouldGroundCatchVertical(float throttle, float hoverThrottle, float targetVelocityY, Profile profile) {
		return throttle <= hoverThrottle + profile.hoverBand()
				&& targetVelocityY <= 0.0f;
	}

	private static boolean shouldAirBrake(FlightMode mode, float throttle, float hoverThrottle, float pitch, float roll, Profile profile) {
		return mode != FlightMode.ACRO
				&& profile.airBrakeSmoothing() > 0.0f
				&& Math.abs(throttle - hoverThrottle) <= profile.airBrakeThrottleBand()
				&& Math.abs(pitch) <= profile.airBrakeCommandThreshold()
				&& Math.abs(roll) <= profile.airBrakeCommandThreshold();
	}

	private static float settledAttitude(FlightMode mode, float command, float radians) {
		if (mode != FlightMode.ACRO && Math.abs(command) <= PLAYABLE_AXIS_NOISE_EPSILON && Math.abs(radians) <= ATTITUDE_SETTLE_EPSILON_RADIANS) {
			return 0.0f;
		}
		return radians;
	}

	private static float settledVelocity(float velocity, float targetVelocity) {
		if (Math.abs(targetVelocity) <= VELOCITY_SETTLE_EPSILON_MPS && Math.abs(velocity) <= VELOCITY_SETTLE_EPSILON_MPS) {
			return 0.0f;
		}
		return velocity;
	}

	private static float settledYawRate(float yawDegreesPerTick, float targetYawDegreesPerTick) {
		if (Math.abs(targetYawDegreesPerTick) <= YAW_SETTLE_EPSILON_DEGREES_PER_TICK
				&& Math.abs(yawDegreesPerTick) <= YAW_SETTLE_EPSILON_DEGREES_PER_TICK) {
			return 0.0f;
		}
		return yawDegreesPerTick;
	}

	private static float yawSmoothing(float current, float target, Profile profile) {
		boolean braking = Math.abs(target) < Math.abs(current)
				|| (Math.signum(current) != 0.0f && Math.signum(target) != 0.0f && Math.signum(current) != Math.signum(target));
		return braking ? profile.yawBrakeSmoothing() : profile.yawSmoothing();
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

	record State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians, float yawDegreesPerTick, FlightMode mode) {
		static final State ZERO = zero(FlightMode.HORIZON);

		State(float velocityX, float velocityY, float velocityZ) {
			this(velocityX, velocityY, velocityZ, 0.0f, 0.0f, 0.0f, FlightMode.HORIZON);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, 0.0f, FlightMode.HORIZON);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians, float yawDegreesPerTick) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, FlightMode.HORIZON);
		}

		static State zero(FlightMode mode) {
			return new State(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, mode == null ? FlightMode.HORIZON : mode);
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
			float averageRpm,
			FlightMode mode
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
			float yawSmoothing,
			float yawBrakeSmoothing,
			float attitudeSmoothing,
			float attitudeStepLimitRadians,
			float attitudeRecenterSmoothing,
			float attitudeRecenterStepLimitRadians,
			float acroHoldDamping,
			float velocitySmoothing,
			float velocityBrakeSmoothing,
			float groundFrictionSmoothing,
			float groundFrictionTargetVelocityThreshold,
			float airBrakeSmoothing,
			float airBrakeCommandThreshold,
			float airBrakeThrottleBand,
			float hoverBand,
			float descentGain,
			float thrustGain
	) {
		private static Profile forMode(FlightMode mode) {
			return switch (mode == null ? FlightMode.HORIZON : mode) {
				case ANGLE -> new Profile(0.60f, 0.88f, radians(12.0f), radians(12.0f), radians(28.0f), radians(30.0f), radians(1.1f), radians(1.2f), 0.48f, 0.42f, 0.62f, 0.12f, radians(0.65f), 0.46f, radians(3.0f), 0.78f, 0.12f, 0.30f, 0.68f, 0.08f, 0.32f, 0.06f, 0.085f, 0.055f, 0.62f, 1.45f);
				case HORIZON -> new Profile(1.25f, 1.65f, radians(26.0f), radians(28.0f), radians(48.0f), radians(52.0f), radians(2.6f), radians(2.9f), 1.55f, 0.85f, 0.74f, 0.16f, radians(1.85f), 0.28f, radians(3.0f), 0.88f, 0.20f, 0.26f, 0.58f, 0.12f, 0.24f, 0.055f, 0.075f, HOVER_BAND, DESCENT_GAIN, THRUST_GAIN);
				case ACRO -> new Profile(1.85f, 2.35f, radians(46.0f), radians(50.0f), radians(68.0f), radians(72.0f), radians(4.8f), radians(5.3f), 2.70f, 0.95f, 0.40f, 0.16f, radians(3.80f), 0.16f, radians(3.80f), 0.995f, 0.22f, 0.22f, 0.42f, 0.14f, 0.0f, 0.0f, 0.0f, 0.030f, 1.10f, 2.80f);
			};
		}

		private static float radians(float degrees) {
			return (float) Math.toRadians(degrees);
		}
	}
}
