package com.tenicana.dronecraft.entity;

import com.tenicana.dronecraft.sim.FlightMode;

final class PlayableFlightModel {
	private static final FlightMode DEFAULT_PLAYABLE_MODE = FlightMode.DEFAULT_FIRST_FLIGHT;
	private static final float DESCENT_GAIN = 3.20f;
	private static final float THRUST_GAIN = 4.20f;
	private static final float THRUST_DEADZONE = 0.005f;
	private static final float THRUST_MIN_CLIMB = 0.025f;
	private static final float VERTICAL_ASCENT_SPEED_LIMIT = 9.0f;
	private static final float VERTICAL_DESCENT_SPEED_LIMIT = 18.0f;
	private static final float ZERO_THROTTLE_TERMINAL_DESCENT_METERS_PER_SECOND = 18.0f;
	private static final float VERTICAL_HOVER_EDGE_SOFTENING = 0.0075f;
	private static final float VERTICAL_HOVER_BRAKE_SMOOTHING = 0.72f;
	private static final float HOVER_BAND = 0.035f;
	private static final float PLAYABLE_AXIS_NOISE_EPSILON = 0.006f;
	private static final float VELOCITY_SETTLE_EPSILON_MPS = 0.018f;
	private static final float ATTITUDE_SETTLE_EPSILON_RADIANS = (float) Math.toRadians(0.20f);
	private static final float YAW_SETTLE_EPSILON_DEGREES_PER_TICK = 0.020f;
	private static final float IDLE_RPM = 2200.0f;
	private static final float HOVER_RPM = 6600.0f;
	private static final float MAX_RPM = 12300.0f;
	private static final float MODE_SWITCH_ANGLE_ATTITUDE_KEEP = 0.18f;
	private static final float MODE_SWITCH_HORIZON_ATTITUDE_KEEP = 0.24f;
	private static final float MODE_SWITCH_ACRO_ATTITUDE_KEEP = 1.0f;
	private static final float MODE_SWITCH_ANGLE_YAW_KEEP = 0.10f;
	private static final float MODE_SWITCH_HORIZON_YAW_KEEP = 0.18f;
	private static final float MODE_SWITCH_ACRO_YAW_KEEP = 0.45f;
	private static final float MODE_SWITCH_ANGLE_HORIZONTAL_KEEP = 0.45f;
	private static final float MODE_SWITCH_HORIZON_HORIZONTAL_KEEP = 0.62f;
	private static final float MODE_SWITCH_ACRO_HORIZONTAL_KEEP = 0.82f;
	private static final int MODE_SWITCH_SOFT_CAPTURE_TICKS = 6;
	private static final float MODE_SWITCH_ANGLE_HORIZONTAL_BRAKE = 0.34f;
	private static final float MODE_SWITCH_HORIZON_HORIZONTAL_BRAKE = 0.28f;
	private static final float MODE_SWITCH_ACRO_HORIZONTAL_BRAKE = 0.18f;
	private static final float MODE_SWITCH_ANGLE_YAW_BRAKE = 0.62f;
	private static final float MODE_SWITCH_HORIZON_YAW_BRAKE = 0.52f;
	private static final float MODE_SWITCH_ACRO_YAW_BRAKE = 0.42f;
	private static final float MAX_HIGH_THROTTLE_HORIZONTAL_BOOST = 0.20f;
	private static final float GROUND_ANGLE_HORIZONTAL_AUTHORITY_SCALE = 0.12f;
	private static final float GROUND_HORIZON_HORIZONTAL_AUTHORITY_SCALE = 0.30f;
	private static final float GROUND_ACRO_HORIZONTAL_AUTHORITY_SCALE = 0.45f;
	private static final float ANGLE_TILT_SINK_METERS_PER_SECOND = 1.15f;
	private static final float HORIZON_TILT_SINK_METERS_PER_SECOND = 1.85f;
	private static final float ACRO_TILT_SINK_METERS_PER_SECOND = 2.75f;
	private static final float INVERTED_THRUST_VERTICAL_PROJECTION_MIN = -0.45f;
	private static final float PLAYABLE_TICK_SECONDS = 0.05f;
	private static final float FULL_ROTATION_RADIANS = (float) (Math.PI * 2.0);
	private static final float ACRO_COMPLETED_ROTATION_MIN_RADIANS = (float) Math.toRadians(300.0f);
	private static final float ACRO_COMPLETED_ROTATION_SNAP_RADIANS = (float) Math.toRadians(40.0f);
	private static final float ACRO_COMPLETED_ROTATION_SNAP_MARGIN_RADIANS = (float) Math.toRadians(4.0f);
	private static final float ACRO_COMPLETED_ROTATION_RELEASE_COMMAND = 0.180f;
	private static final float ACRO_COMPLETED_ROTATION_RELEASE_SNAP_RADIANS = (float) Math.toRadians(145.0f);
	private static final float ACRO_COMPLETED_ROTATION_DRIFT_TRIM_SPEED_METERS_PER_SECOND = 2.75f;
	private static final float ACRO_GRAVITY_METERS_PER_SECOND_SQUARED = 9.80665f;
	private static final float ACRO_REFERENCE_MASS_KILOGRAMS = 1.10f;
	private static final float ACRO_AIR_DENSITY_KILOGRAMS_PER_CUBIC_METER = 1.225f;
	private static final float ACRO_FULL_THROTTLE_THRUST_TO_WEIGHT = 3.35f;
	private static final float ACRO_ROTOR_COUNT = 4.0f;
	private static final float ACRO_FORWARD_DRAG_AREA_SQUARE_METERS = 0.0216f;
	private static final float ACRO_LATERAL_DRAG_AREA_SQUARE_METERS = 0.0269f;
	private static final float ACRO_VERTICAL_DRAG_AREA_SQUARE_METERS = 0.0180f;
	private static final float ACRO_FORWARD_LINEAR_DRAG_PER_SECOND = 0.18f;
	private static final float ACRO_LATERAL_LINEAR_DRAG_PER_SECOND = 0.24f;
	private static final float ACRO_VERTICAL_LINEAR_DRAG_PER_SECOND = 0.14f;
	private static final float ACRO_FORWARD_QUADRATIC_DRAG_PER_METER = bodyQuadraticDragPerMeter(ACRO_FORWARD_DRAG_AREA_SQUARE_METERS);
	private static final float ACRO_LATERAL_QUADRATIC_DRAG_PER_METER = bodyQuadraticDragPerMeter(ACRO_LATERAL_DRAG_AREA_SQUARE_METERS);
	private static final float ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER = bodyQuadraticDragPerMeter(ACRO_VERTICAL_DRAG_AREA_SQUARE_METERS);
	private static final float ACRO_SEPARATION_AOA_START_RADIANS = (float) Math.toRadians(30.0f);
	private static final float ACRO_SEPARATION_AOA_FULL_RADIANS = (float) Math.toRadians(66.0f);
	private static final float ACRO_SEPARATION_SIDESLIP_START_RADIANS = (float) Math.toRadians(32.0f);
	private static final float ACRO_SEPARATION_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(68.0f);
	private static final float ACRO_PITCH_LIFT_AOA_STALL_START_RADIANS = (float) Math.toRadians(34.0f);
	private static final float ACRO_PITCH_LIFT_AOA_STALL_FULL_RADIANS = (float) Math.toRadians(72.0f);
	private static final float ACRO_PITCH_LIFT_GAIN = 0.085f;
	private static final float ACRO_SIDEFORCE_SIDESLIP_STALL_START_RADIANS = (float) Math.toRadians(35.0f);
	private static final float ACRO_SIDEFORCE_SIDESLIP_STALL_FULL_RADIANS = (float) Math.toRadians(75.0f);
	private static final float ACRO_SIDEFORCE_GAIN = 0.065f;
	private static final float ACRO_WEATHERCOCK_SIDESLIP_START_RADIANS = (float) Math.toRadians(7.0f);
	private static final float ACRO_WEATHERCOCK_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(48.0f);
	private static final float ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND = 2.5f;
	private static final float ACRO_WEATHERCOCK_FORWARD_FULL_METERS_PER_SECOND = 16.0f;
	private static final float ACRO_WEATHERCOCK_LATERAL_START_METERS_PER_SECOND = 1.5f;
	private static final float ACRO_WEATHERCOCK_LATERAL_FULL_METERS_PER_SECOND = 12.0f;
	private static final float ACRO_WEATHERCOCK_YAW_GAIN_DEGREES_PER_TICK = 0.030f;
	private static final float ACRO_WEATHERCOCK_YAW_MAX_DEGREES_PER_TICK = 0.22f;
	private static final float ACRO_SIDESLIP_YAW_DAMPING_GAIN = 0.018f;
	private static final float ACRO_SIDESLIP_YAW_DAMPING_MAX = 0.13f;
	private static final float ACRO_WEATHERCOCK_YAW_COMMAND_SUPPRESS = 0.45f;
	private static final float ACRO_THRUST_RISE_SMOOTHING = 0.55f;
	private static final float ACRO_THRUST_FALL_SMOOTHING = 0.68f;
	private static final float ACRO_THRUST_SETTLE_EPSILON = 0.004f;
	private static final float ACRO_PROP_DIAMETER_METERS = 0.127f;
	private static final float ACRO_ADVANCE_REFERENCE_MIN_RPM = 13000.0f;
	private static final float ACRO_ADVANCE_LOSS_START_J = 0.25f;
	private static final float ACRO_ADVANCE_LOSS_FULL_J = 1.00f;
	private static final float ACRO_ADVANCE_MAX_THRUST_LOSS = 0.20f;
	private static final float ACRO_ADVANCE_MIN_THRUST_SCALE = 0.78f;
	private static final float ACRO_ADVANCE_AXIAL_FLOW_WEIGHT = 0.18f;
	private static final float ACRO_ROTOR_RADIUS_METERS = ACRO_PROP_DIAMETER_METERS * 0.5f;
	private static final float ACRO_ROTOR_REFERENCE_MAX_RPM = 29137.0f;
	private static final float ACRO_ROTOR_DISK_DRAG_COEFFICIENT = 0.0028f;
	private static final float ACRO_ROTOR_FLAPPING_COEFFICIENT = 0.055f;
	private static final float ACRO_ROTOR_FLAPPING_FULL_MU = 0.095f;
	private static final float ACRO_ROTOR_FLAPPING_MAX_TILT_RADIANS = (float) Math.toRadians(18.0f);
	private static final float ACRO_ROTOR_FLAPPING_STRAIGHT_FLOW_WEIGHT = 0.38f;
	private static final float ACRO_ROTOR_FLAPPING_SIDESLIP_START_RADIANS = (float) Math.toRadians(10.0f);
	private static final float ACRO_ROTOR_FLAPPING_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(48.0f);
	private static final float ACRO_ROTOR_IN_PLANE_MAX_ACCELERATION = 5.0f;
	private static final float ACRO_ROTOR_IN_PLANE_STRAIGHT_FLOW_WEIGHT = 0.10f;
	private static final float ACRO_ROTOR_IN_PLANE_SIDESLIP_START_RADIANS = (float) Math.toRadians(12.0f);
	private static final float ACRO_ROTOR_IN_PLANE_SIDESLIP_FULL_RADIANS = (float) Math.toRadians(52.0f);
	private static final float ACRO_RATE_RISE_SMOOTHING = 0.62f;
	private static final float ACRO_RATE_FALL_SMOOTHING = 0.74f;
	private static final float ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK = (float) Math.toRadians(0.025f);

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
		FlightMode safeMode = safeMode(mode);
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

		AcroRateResponse acroRate = acroRateResponse(safeMode, safePrevious, attitudePitch, attitudeRoll, profile);
		Attitude attitude = attitude(safeMode, profile, attitudePitch, attitudeRoll, safePrevious, acroRate);
		float pitchRadians = completedAcroRotationAttitude(safeMode, attitudePitch, attitude.pitchRadians(), profile.maxPitchRadians());
		float rollRadians = completedAcroRotationAttitude(safeMode, attitudeRoll, attitude.rollRadians(), profile.maxRollRadians());
		boolean acroPitchCaptured = completedAcroAttitudeWasCaptured(safeMode, attitude.pitchRadians(), pitchRadians);
		boolean acroRollCaptured = completedAcroAttitudeWasCaptured(safeMode, attitude.rollRadians(), rollRadians);
		float acroPitchRateRadiansPerTick = acroPitchCaptured
				? 0.0f
				: acroRate.pitchRateRadiansPerTick();
		float acroRollRateRadiansPerTick = acroRollCaptured
				? 0.0f
				: acroRate.rollRateRadiansPerTick();
		pitchRadians = settledAttitude(safeMode, attitudePitch, pitchRadians);
		rollRadians = settledAttitude(safeMode, attitudeRoll, rollRadians);
		float throttleAuthority = horizontalThrottleAuthority(safeMode, safeThrottle, safeHover, nearGroundLocked, safeLowAltitudeHorizontalScale, profile);
		Velocity horizontalTarget = horizontalTargetVelocity(safeMode, pitchRadians, rollRadians, throttleAuthority, profile);
		float targetVelocityX = horizontalTarget.x();
		float targetVelocityZ = horizontalTarget.z();
		Velocity limitedHorizontalTarget = limitHorizontalVector(
				targetVelocityX,
				0.0f,
				targetVelocityZ,
				horizontalTargetSpeedLimit(profile, throttleAuthority)
		);
		targetVelocityX = limitedHorizontalTarget.x();
		targetVelocityZ = limitedHorizontalTarget.z();
		float targetVelocityY = attitudeAdjustedVerticalVelocity(
				safeMode,
				safeThrottle,
				safeHover,
				pitchRadians,
				rollRadians,
				verticalVelocity(safeThrottle, safeHover, profile),
				profile
		);
		if (nearGroundLocked && targetVelocityY < 0.0f) {
			targetVelocityY = 0.0f;
		}
		if (safeThrottle > safeHover + profile.hoverBand() + VERTICAL_HOVER_EDGE_SOFTENING
				&& targetVelocityY > 0.0f
				&& targetVelocityY < THRUST_MIN_CLIMB) {
			targetVelocityY = THRUST_MIN_CLIMB;
		}
		float targetCollectiveThrustToWeight = acroCollectiveThrustToWeight(safeThrottle, safeHover);
		float collectiveThrustToWeight = acroResponsiveCollectiveThrustToWeight(
				safeMode,
				safePrevious.acroCollectiveThrustToWeight(),
				targetCollectiveThrustToWeight
		);

		Velocity velocity = velocityStep(
				safeMode,
				safePrevious.velocityX(),
				safePrevious.velocityY(),
				safePrevious.velocityZ(),
				targetVelocityX,
				targetVelocityY,
				targetVelocityZ,
				safeThrottle,
				safeHover,
				collectiveThrustToWeight,
				pitchRadians,
				rollRadians,
				profile
		);
		float velocityX = velocity.x();
		float velocityY = velocity.y();
		float velocityZ = velocity.z();
		if (safeMode == FlightMode.ACRO && (acroPitchCaptured || acroRollCaptured)) {
			Velocity trimmed = completedAcroRotationVelocityTrim(velocityX, velocityY, velocityZ, acroPitchCaptured, acroRollCaptured);
			velocityX = trimmed.x();
			velocityY = trimmed.y();
			velocityZ = trimmed.z();
		}
		Velocity limitedHorizontalVelocity = limitHorizontalVector(
				velocityX,
				0.0f,
				velocityZ,
				profile.horizontalSpeedLimitMetersPerSecond()
		);
		velocityX = limitedHorizontalVelocity.x();
		velocityY = clamp(velocityY, -VERTICAL_DESCENT_SPEED_LIMIT, VERTICAL_ASCENT_SPEED_LIMIT);
		velocityZ = limitedHorizontalVelocity.z();
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
		if (shouldModeSwitchBrakeHorizontal(safePrevious, safePitch, safeRoll)) {
			float brake = modeSwitchHorizontalBrake(safeMode);
			velocityX = smooth(velocityX, 0.0f, brake);
			velocityZ = smooth(velocityZ, 0.0f, brake);
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
		if (shouldModeSwitchBrakeYaw(safePrevious, safeYaw)) {
			yawDegreesPerTick = smooth(yawDegreesPerTick, 0.0f, modeSwitchYawBrake(safeMode));
		}
		yawDegreesPerTick = settledYawRate(yawDegreesPerTick, targetYawDegreesPerTick);
		yawDegreesPerTick = acroAerodynamicYawRate(
				safeMode,
				yawDegreesPerTick,
				yawCommand,
				velocityX,
				velocityY,
				velocityZ,
				pitchRadians,
				rollRadians
		);
		float motorPower = safeThrottle <= THRUST_DEADZONE ? 0.14f : clamp(0.14f + safeThrottle * 0.86f, 0.0f, 1.0f);
		float averageRpm = averageRpm(safeThrottle, safeHover);
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
				collectiveThrustToWeight,
				acroPitchRateRadiansPerTick,
				acroRollRateRadiansPerTick,
				safeMode,
				nextModeSwitchTicksRemaining(safePrevious)
		);
	}

	static float playableAxisCommand(float value) {
		if (!Float.isFinite(value)) {
			return 0.0f;
		}
		float clamped = clamp(value, -1.0f, 1.0f);
		return Math.abs(clamped) <= PLAYABLE_AXIS_NOISE_EPSILON ? 0.0f : clamped;
	}

	static Velocity worldVelocityForYaw(float localX, float localY, float localZ, float yawDegrees) {
		float yawRadians = (float) Math.toRadians(yawDegrees);
		float cos = (float) Math.cos(yawRadians);
		float sin = (float) Math.sin(yawRadians);
		return new Velocity(
				localX * cos - localZ * sin,
				localY,
				localX * sin + localZ * cos
		);
	}

	static Velocity localVelocityForYaw(float worldX, float worldY, float worldZ, float yawDegrees) {
		float yawRadians = (float) Math.toRadians(yawDegrees);
		float cos = (float) Math.cos(yawRadians);
		float sin = (float) Math.sin(yawRadians);
		return new Velocity(
				worldX * cos + worldZ * sin,
				worldY,
				-worldX * sin + worldZ * cos
		);
	}

	static Velocity reframeVelocityForYaw(float localX, float localY, float localZ, float fromYawDegrees, float toYawDegrees) {
		Velocity world = worldVelocityForYaw(localX, localY, localZ, fromYawDegrees);
		return localVelocityForYaw(world.x(), world.y(), world.z(), toYawDegrees);
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
				modeSwitchCapturedAttitude(mode, previous.pitchRadians() * modeSwitchAttitudeKeep(mode), profile.maxPitchRadians()),
				modeSwitchCapturedAttitude(mode, previous.rollRadians() * modeSwitchAttitudeKeep(mode), profile.maxRollRadians()),
				previous.yawDegreesPerTick() * modeSwitchYawKeep(mode),
				mode,
				MODE_SWITCH_SOFT_CAPTURE_TICKS,
				previous.mode() == FlightMode.ACRO ? previous.acroCollectiveThrustToWeight() : 0.0f,
				previous.mode() == FlightMode.ACRO ? previous.acroPitchRateRadiansPerTick() * modeSwitchYawKeep(mode) : 0.0f,
				previous.mode() == FlightMode.ACRO ? previous.acroRollRateRadiansPerTick() * modeSwitchYawKeep(mode) : 0.0f
		);
	}

	private static float modeSwitchCapturedAttitude(FlightMode mode, float attitudeRadians, float assistedLimitRadians) {
		if (!Float.isFinite(attitudeRadians)) {
			return 0.0f;
		}
		if (mode == FlightMode.ACRO) {
			return attitudeRadians;
		}
		return clamp(attitudeRadians, -assistedLimitRadians, assistedLimitRadians);
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

	private static int nextModeSwitchTicksRemaining(State previous) {
		return Math.max(0, previous.modeSwitchTicksRemaining() - 1);
	}

	private static boolean shouldModeSwitchBrakeHorizontal(State previous, float pitch, float roll) {
		return previous.modeSwitchTicksRemaining() > 0
				&& Math.abs(pitch) <= PLAYABLE_AXIS_NOISE_EPSILON
				&& Math.abs(roll) <= PLAYABLE_AXIS_NOISE_EPSILON;
	}

	private static boolean shouldModeSwitchBrakeYaw(State previous, float yaw) {
		return previous.modeSwitchTicksRemaining() > 0
				&& Math.abs(yaw) <= PLAYABLE_AXIS_NOISE_EPSILON;
	}

	private static float modeSwitchHorizontalBrake(FlightMode mode) {
		return switch (safeMode(mode)) {
			case ANGLE -> MODE_SWITCH_ANGLE_HORIZONTAL_BRAKE;
			case HORIZON -> MODE_SWITCH_HORIZON_HORIZONTAL_BRAKE;
			case ACRO -> MODE_SWITCH_ACRO_HORIZONTAL_BRAKE;
		};
	}

	private static float modeSwitchYawBrake(FlightMode mode) {
		return switch (safeMode(mode)) {
			case ANGLE -> MODE_SWITCH_ANGLE_YAW_BRAKE;
			case HORIZON -> MODE_SWITCH_HORIZON_YAW_BRAKE;
			case ACRO -> MODE_SWITCH_ACRO_YAW_BRAKE;
		};
	}

	private static Attitude attitude(FlightMode mode, Profile profile, float pitch, float roll, State previous, AcroRateResponse acroRate) {
		FlightMode safeMode = safeMode(mode);
		return switch (safeMode) {
			case ANGLE -> angleAttitude(profile, pitch, roll, previous);
			case HORIZON -> horizonAttitude(profile, pitch, roll, previous);
			case ACRO -> new Attitude(
					previous.pitchRadians() + acroRate.pitchRateRadiansPerTick(),
					previous.rollRadians() + acroRate.rollRateRadiansPerTick()
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

	private static AcroRateResponse acroRateResponse(FlightMode mode, State previous, float pitch, float roll, Profile profile) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return AcroRateResponse.ZERO;
		}
		return new AcroRateResponse(
				responsiveAcroRate(previous.acroPitchRateRadiansPerTick(), pitch * profile.pitchRateRadiansPerTick()),
				responsiveAcroRate(previous.acroRollRateRadiansPerTick(), roll * profile.rollRateRadiansPerTick())
		);
	}

	private static float responsiveAcroRate(float previousRateRadiansPerTick, float targetRateRadiansPerTick) {
		if (!Float.isFinite(previousRateRadiansPerTick)) {
			return targetRateRadiansPerTick;
		}
		float smoothing = isRateRising(previousRateRadiansPerTick, targetRateRadiansPerTick)
				? ACRO_RATE_RISE_SMOOTHING
				: ACRO_RATE_FALL_SMOOTHING;
		float responsive = smooth(previousRateRadiansPerTick, targetRateRadiansPerTick, smoothing);
		if (Math.abs(targetRateRadiansPerTick) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK
				&& Math.abs(responsive) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return 0.0f;
		}
		if (Math.abs(responsive - targetRateRadiansPerTick) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return targetRateRadiansPerTick;
		}
		return responsive;
	}

	private static boolean isRateRising(float currentRateRadiansPerTick, float targetRateRadiansPerTick) {
		if (Math.abs(targetRateRadiansPerTick) <= ACRO_RATE_SETTLE_EPSILON_RADIANS_PER_TICK) {
			return false;
		}
		boolean sameDirection = Math.signum(currentRateRadiansPerTick) == 0.0f
				|| Math.signum(currentRateRadiansPerTick) == Math.signum(targetRateRadiansPerTick);
		return sameDirection && Math.abs(targetRateRadiansPerTick) > Math.abs(currentRateRadiansPerTick);
	}

	private static float verticalVelocity(float throttle, float hoverThrottle, Profile profile) {
		if (throttle <= THRUST_DEADZONE) {
			return -ZERO_THROTTLE_TERMINAL_DESCENT_METERS_PER_SECOND;
		}
		float centered = throttle - hoverThrottle;
		float magnitude = Math.abs(centered);
		if (magnitude < profile.hoverBand()) {
			return 0.0f;
		}
		float edgeRamp = smoothStep((magnitude - profile.hoverBand()) / VERTICAL_HOVER_EDGE_SOFTENING);
		if (centered > 0.0f) {
			return centered / Math.max(0.10f, 1.0f - hoverThrottle) * profile.thrustGain() * edgeRamp;
		}
		return centered / Math.max(0.10f, hoverThrottle) * profile.descentGain() * edgeRamp;
	}

	private static float attitudeAdjustedVerticalVelocity(
			FlightMode mode,
			float throttle,
			float hoverThrottle,
			float pitchRadians,
			float rollRadians,
			float verticalVelocity,
			Profile profile
	) {
		if (throttle <= THRUST_DEADZONE) {
			return verticalVelocity;
		}
		float verticalProjection = verticalThrustProjection(pitchRadians, rollRadians);
		float uprightProjection = clamp(verticalProjection, 0.0f, 1.0f);
		float liftProgress = smoothStep(throttle / Math.max(THRUST_DEADZONE, hoverThrottle + profile.hoverBand()));
		float tiltSink = tiltSinkMetersPerSecond(mode) * (1.0f - uprightProjection) * liftProgress;
		if (verticalVelocity > 0.0f) {
			float projectedClimb = verticalVelocity * clamp(verticalProjection, INVERTED_THRUST_VERTICAL_PROJECTION_MIN, 1.0f);
			return projectedClimb - tiltSink;
		}
		if (verticalVelocity < 0.0f) {
			return verticalVelocity - tiltSink * 0.45f;
		}
		return -tiltSink;
	}

	static float verticalThrustProjection(float pitchRadians, float rollRadians) {
		if (!Float.isFinite(pitchRadians) || !Float.isFinite(rollRadians)) {
			return 1.0f;
		}
		return clamp((float) (Math.cos(pitchRadians) * Math.cos(rollRadians)), -1.0f, 1.0f);
	}

	private static float tiltSinkMetersPerSecond(FlightMode mode) {
		return switch (safeMode(mode)) {
			case ANGLE -> ANGLE_TILT_SINK_METERS_PER_SECOND;
			case HORIZON -> HORIZON_TILT_SINK_METERS_PER_SECOND;
			case ACRO -> ACRO_TILT_SINK_METERS_PER_SECOND;
		};
	}

	private static float averageRpm(float throttle, float hoverThrottle) {
		if (throttle <= THRUST_DEADZONE) {
			return IDLE_RPM;
		}
		float safeHover = clamp(hoverThrottle, 0.12f, 0.55f);
		if (throttle <= safeHover) {
			float progress = smoothStep(throttle / Math.max(THRUST_DEADZONE, safeHover));
			return lerp(IDLE_RPM, HOVER_RPM, progress);
		}
		float progress = smoothStep((throttle - safeHover) / Math.max(0.10f, 1.0f - safeHover));
		return lerp(HOVER_RPM, MAX_RPM, progress);
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
		float authority = liftProgress;
		float highThrottleProgress = smoothStep((throttle - liftWindowTop) / Math.max(0.16f, 0.50f - liftWindowTop));
		authority += highThrottleProgress * MAX_HIGH_THROTTLE_HORIZONTAL_BOOST;
		authority *= lowAltitudeHorizontalAuthorityScale;
		if (nearGroundLocked && throttle <= liftWindowTop) {
			authority *= groundHorizontalAuthorityScale(mode);
		}
		return clamp(authority, 0.0f, 1.10f);
	}

	private static float horizontalVelocityCommand(FlightMode mode, float attitudeRadians, float maxAttitudeRadians, Profile profile) {
		float effectiveAttitudeRadians = horizontalVelocityAttitude(mode, attitudeRadians);
		float normalized = clamp(effectiveAttitudeRadians / maxAttitudeRadians, -1.0f, 1.0f);
		float magnitude = Math.abs(normalized);
		float progress = smoothStep(magnitude / Math.max(0.001f, profile.horizontalVelocityLinearStart()));
		float fineScale = clamp(profile.horizontalFineVelocityScale(), 0.0f, 1.0f);
		float gain = lerp(fineScale, 1.0f, progress);
		return Math.copySign(magnitude * gain, normalized);
	}

	private static float horizontalVelocityAttitude(FlightMode mode, float attitudeRadians) {
		if (!Float.isFinite(attitudeRadians)) {
			return 0.0f;
		}
		if (safeMode(mode) != FlightMode.ACRO) {
			return attitudeRadians;
		}
		return (float) Math.asin(clamp((float) Math.sin(attitudeRadians), -1.0f, 1.0f));
	}

	private static Velocity horizontalTargetVelocity(
			FlightMode mode,
			float pitchRadians,
			float rollRadians,
			float throttleAuthority,
			Profile profile
	) {
		if (safeMode(mode) == FlightMode.ACRO) {
			return acroThrustProjectionTargetVelocity(pitchRadians, rollRadians, throttleAuthority, profile);
		}
		return new Velocity(
				-horizontalVelocityCommand(mode, rollRadians, profile.maxRollRadians(), profile)
						* profile.horizontalSpeedMetersPerSecond()
						* throttleAuthority,
				0.0f,
				horizontalVelocityCommand(mode, pitchRadians, profile.maxPitchRadians(), profile)
						* profile.horizontalSpeedMetersPerSecond()
						* throttleAuthority
		);
	}

	private static Velocity acroThrustProjectionTargetVelocity(
			float pitchRadians,
			float rollRadians,
			float throttleAuthority,
			Profile profile
	) {
		Velocity thrustAxis = acroThrustAxis(pitchRadians, rollRadians);
		float horizontalProjection = horizontalMagnitude(thrustAxis.x(), thrustAxis.z());
		if (horizontalProjection <= 1.0e-6f || throttleAuthority <= 0.0f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float projectionMagnitude = acroHorizontalThrustProjectionMagnitude(thrustAxis, profile);
		float targetSpeed = profile.horizontalSpeedMetersPerSecond() * throttleAuthority * projectionMagnitude;
		return new Velocity(
				thrustAxis.x() / horizontalProjection * targetSpeed,
				0.0f,
				thrustAxis.z() / horizontalProjection * targetSpeed
		);
	}

	private static float acroHorizontalThrustProjectionMagnitude(float pitchRadians, float rollRadians, Profile profile) {
		return acroHorizontalThrustProjectionMagnitude(acroThrustAxis(pitchRadians, rollRadians), profile);
	}

	private static float acroHorizontalThrustProjectionMagnitude(Velocity thrustAxis, Profile profile) {
		float verticalProjection = thrustAxis.y();
		float horizontalProjection = (float) Math.sqrt(Math.max(0.0f, 1.0f - verticalProjection * verticalProjection));
		float fullAuthorityProjection = (float) Math.sin(Math.max(profile.maxPitchRadians(), profile.maxRollRadians()));
		return clamp(horizontalProjection / Math.max(0.10f, fullAuthorityProjection), 0.0f, 1.0f);
	}

	private static Velocity acroThrustAxis(float pitchRadians, float rollRadians) {
		return acroBodyFrame(pitchRadians, rollRadians).up();
	}

	private static float completedAcroRotationAttitude(FlightMode mode, float command, float attitudeRadians, float completedRotationCaptureRadians) {
		if (!Float.isFinite(attitudeRadians)
				|| safeMode(mode) != FlightMode.ACRO
				|| Math.abs(attitudeRadians) < ACRO_COMPLETED_ROTATION_MIN_RADIANS) {
			return attitudeRadians;
		}
		float rotationResidual = signedRotationResidualRadians(attitudeRadians);
		float snapRadians = Math.max(
				ACRO_COMPLETED_ROTATION_SNAP_RADIANS,
				Math.max(0.0f, completedRotationCaptureRadians) + ACRO_COMPLETED_ROTATION_SNAP_MARGIN_RADIANS
		);
		float releasedSnapRadians = Math.max(snapRadians, ACRO_COMPLETED_ROTATION_RELEASE_SNAP_RADIANS);
		if (Math.abs(command) <= PLAYABLE_AXIS_NOISE_EPSILON) {
			return Math.abs(rotationResidual) <= releasedSnapRadians ? 0.0f : attitudeRadians;
		}
		boolean releasingPastCompletion = Math.abs(command) <= ACRO_COMPLETED_ROTATION_RELEASE_COMMAND
				&& Math.signum(command) == Math.signum(rotationResidual);
		if (!releasingPastCompletion || Math.abs(rotationResidual) > releasedSnapRadians) {
			return attitudeRadians;
		}
		return 0.0f;
	}

	private static float signedRotationResidualRadians(float attitudeRadians) {
		if (!Float.isFinite(attitudeRadians)) {
			return 0.0f;
		}
		float wrapped = (float) Math.IEEEremainder(attitudeRadians, FULL_ROTATION_RADIANS);
		if (wrapped <= -Math.PI) {
			return wrapped + FULL_ROTATION_RADIANS;
		}
		if (wrapped > Math.PI) {
			return wrapped - FULL_ROTATION_RADIANS;
		}
		return wrapped;
	}

	private static boolean completedAcroAttitudeWasCaptured(FlightMode mode, float unsnappedRadians, float snappedRadians) {
		return safeMode(mode) == FlightMode.ACRO
				&& Float.isFinite(unsnappedRadians)
				&& Float.isFinite(snappedRadians)
				&& Math.abs(unsnappedRadians - snappedRadians) > 1.0e-6f;
	}

	private static float horizontalTargetSpeedLimit(Profile profile, float throttleAuthority) {
		float throttleLimitedSpeed = profile.horizontalSpeedMetersPerSecond() * Math.max(0.0f, throttleAuthority);
		return Math.min(profile.horizontalSpeedLimitMetersPerSecond(), throttleLimitedSpeed);
	}

	private static Velocity velocityStep(
			FlightMode mode,
			float previousVelocityX,
			float previousVelocityY,
			float previousVelocityZ,
			float targetVelocityX,
			float targetVelocityY,
			float targetVelocityZ,
			float throttle,
			float hoverThrottle,
			float collectiveThrustToWeight,
			float pitchRadians,
			float rollRadians,
			Profile profile
	) {
		if (safeMode(mode) == FlightMode.ACRO) {
			return acroPhysicalVelocity(previousVelocityX, previousVelocityY, previousVelocityZ, throttle, hoverThrottle, collectiveThrustToWeight, pitchRadians, rollRadians, profile);
		}
		Velocity horizontalVelocity = horizontalVelocityStep(
				previousVelocityX,
				previousVelocityZ,
				targetVelocityX,
				targetVelocityZ,
				profile
		);
		return new Velocity(
				horizontalVelocity.x(),
				inertialVelocity(
						previousVelocityY,
						targetVelocityY,
						verticalVelocitySmoothing(previousVelocityY, targetVelocityY, profile),
						profile.verticalAccelerationMetersPerSecondSquared(),
						profile.verticalBrakeAccelerationMetersPerSecondSquared()
				),
				horizontalVelocity.z()
		);
	}

	private static Velocity horizontalVelocityStep(
			float previousVelocityX,
			float previousVelocityZ,
			float targetVelocityX,
			float targetVelocityZ,
			Profile profile
	) {
		return new Velocity(
				inertialVelocity(
						previousVelocityX,
						targetVelocityX,
						velocitySmoothing(previousVelocityX, targetVelocityX, profile),
						profile.horizontalAccelerationMetersPerSecondSquared(),
						profile.horizontalBrakeAccelerationMetersPerSecondSquared()
				),
				0.0f,
				inertialVelocity(
						previousVelocityZ,
						targetVelocityZ,
						velocitySmoothing(previousVelocityZ, targetVelocityZ, profile),
						profile.horizontalAccelerationMetersPerSecondSquared(),
						profile.horizontalBrakeAccelerationMetersPerSecondSquared()
				)
		);
	}

	private static Velocity acroPhysicalVelocity(
			float previousVelocityX,
			float previousVelocityY,
			float previousVelocityZ,
			float throttle,
			float hoverThrottle,
			float collectiveThrustToWeight,
			float pitchRadians,
			float rollRadians,
			Profile profile
	) {
		Velocity thrustAxis = acroThrustAxis(pitchRadians, rollRadians);
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(previousVelocityX, previousVelocityY, previousVelocityZ, pitchRadians, rollRadians);
		Velocity dragAcceleration = acroDragAcceleration(previousVelocityX, previousVelocityY, previousVelocityZ, pitchRadians, rollRadians);
		float thrustScale = acroAdvanceRatioThrustScale(previousVelocityX, previousVelocityY, previousVelocityZ, pitchRadians, rollRadians, throttle, hoverThrottle);
		float thrustAcceleration = ACRO_GRAVITY_METERS_PER_SECOND_SQUARED * collectiveThrustToWeight * thrustScale;
		Velocity flappingBodyAcceleration = acroRotorFlappingBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle);
		Velocity inPlaneDragBodyAcceleration = acroRotorInPlaneDragBodyAcceleration(bodyVelocity, thrustAcceleration, throttle, hoverThrottle);
		Velocity rotorDiskAcceleration = yawLocalVelocityForAcroBody(
				flappingBodyAcceleration.x() + inPlaneDragBodyAcceleration.x(),
				flappingBodyAcceleration.y() + inPlaneDragBodyAcceleration.y(),
				flappingBodyAcceleration.z() + inPlaneDragBodyAcceleration.z(),
				pitchRadians,
				rollRadians
		);
		float accelerationX = thrustAxis.x() * thrustAcceleration + dragAcceleration.x() + rotorDiskAcceleration.x();
		float accelerationY = thrustAxis.y() * thrustAcceleration - ACRO_GRAVITY_METERS_PER_SECOND_SQUARED + dragAcceleration.y() + rotorDiskAcceleration.y();
		float accelerationZ = thrustAxis.z() * thrustAcceleration + dragAcceleration.z() + rotorDiskAcceleration.z();
		return limitHorizontalVector(
				previousVelocityX + accelerationX * PLAYABLE_TICK_SECONDS,
				previousVelocityY + accelerationY * PLAYABLE_TICK_SECONDS,
				previousVelocityZ + accelerationZ * PLAYABLE_TICK_SECONDS,
				profile.horizontalSpeedLimitMetersPerSecond()
		);
	}

	private static Velocity completedAcroRotationVelocityTrim(
			float velocityX,
			float velocityY,
			float velocityZ,
			boolean pitchCaptured,
			boolean rollCaptured
	) {
		return new Velocity(
				rollCaptured ? completedAcroRotationAxisVelocityTrim(velocityX) : velocityX,
				velocityY,
				pitchCaptured ? completedAcroRotationAxisVelocityTrim(velocityZ) : velocityZ
		);
	}

	private static float completedAcroRotationAxisVelocityTrim(float velocity) {
		return Math.abs(velocity) <= ACRO_COMPLETED_ROTATION_DRIFT_TRIM_SPEED_METERS_PER_SECOND ? 0.0f : velocity;
	}

	static float acroAdvanceRatioThrustScale(
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float throttle,
			float hoverThrottle
	) {
		float advanceRatio = acroRotorAdvanceRatio(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, throttle, hoverThrottle);
		float progress = smoothStep((advanceRatio - ACRO_ADVANCE_LOSS_START_J) / Math.max(0.05f, ACRO_ADVANCE_LOSS_FULL_J - ACRO_ADVANCE_LOSS_START_J));
		float scale = 1.0f - ACRO_ADVANCE_MAX_THRUST_LOSS * progress;
		return clamp(scale, ACRO_ADVANCE_MIN_THRUST_SCALE, 1.0f);
	}

	static float acroRotorAdvanceRatio(
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float throttle,
			float hoverThrottle
	) {
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		float axialSpeed = Math.abs(bodyVelocity.y());
		float effectiveFlowSpeed = (float) Math.sqrt(diskPlaneSpeed * diskPlaneSpeed + ACRO_ADVANCE_AXIAL_FLOW_WEIGHT * axialSpeed * axialSpeed);
		float rpm = Math.max(ACRO_ADVANCE_REFERENCE_MIN_RPM, averageRpm(throttle, hoverThrottle));
		float revsPerSecond = rpm / 60.0f;
		return effectiveFlowSpeed / Math.max(1.0f, revsPerSecond * ACRO_PROP_DIAMETER_METERS);
	}

	static Velocity acroRotorFlappingBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle
	) {
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (diskPlaneSpeed <= 1.0e-6f || thrustAcceleration <= 1.0e-6f || ACRO_ROTOR_FLAPPING_COEFFICIENT <= 0.0f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float advanceRatioMu = acroRotorDiskAdvanceRatioMu(bodyVelocity, throttle, hoverThrottle);
		float advanceResponse = clamp(advanceRatioMu / ACRO_ROTOR_FLAPPING_FULL_MU, 0.0f, 1.0f);
		float thrustFraction = clamp(
				thrustAcceleration / (ACRO_GRAVITY_METERS_PER_SECOND_SQUARED * ACRO_FULL_THROTTLE_THRUST_TO_WEIGHT),
				0.0f,
				1.0f
		);
		float diskLoadingResponse = clamp(0.72f + 0.28f * (float) Math.sqrt(thrustFraction), 0.0f, 1.0f);
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		float sideslipExposure = smoothStep((sideslip - ACRO_ROTOR_FLAPPING_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_ROTOR_FLAPPING_SIDESLIP_FULL_RADIANS - ACRO_ROTOR_FLAPPING_SIDESLIP_START_RADIANS));
		float flowWeight = ACRO_ROTOR_FLAPPING_STRAIGHT_FLOW_WEIGHT
				+ (1.0f - ACRO_ROTOR_FLAPPING_STRAIGHT_FLOW_WEIGHT) * sideslipExposure;
		float tiltRadians = clamp(
				ACRO_ROTOR_FLAPPING_COEFFICIENT * advanceResponse * diskLoadingResponse * flowWeight,
				0.0f,
				ACRO_ROTOR_FLAPPING_MAX_TILT_RADIANS
		);
		if (tiltRadians <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float tiltX = -bodyVelocity.x() / diskPlaneSpeed * tiltRadians;
		float tiltZ = -bodyVelocity.z() / diskPlaneSpeed * tiltRadians;
		float verticalLoss = thrustAcceleration * (1.0f - (float) Math.sqrt(Math.max(0.0f, 1.0f - tiltRadians * tiltRadians)));
		return new Velocity(
				tiltX * thrustAcceleration,
				-verticalLoss,
				tiltZ * thrustAcceleration
		);
	}

	static float acroRotorDiskAdvanceRatioMu(Velocity bodyVelocity, float throttle, float hoverThrottle) {
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		float rpm = Math.max(ACRO_ADVANCE_REFERENCE_MIN_RPM, averageRpm(throttle, hoverThrottle));
		float tipSpeed = rpm / 60.0f * (float) Math.PI * ACRO_PROP_DIAMETER_METERS;
		return diskPlaneSpeed / Math.max(1.0f, tipSpeed);
	}

	static Velocity acroRotorInPlaneDragBodyAcceleration(
			Velocity bodyVelocity,
			float thrustAcceleration,
			float throttle,
			float hoverThrottle
	) {
		float diskPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (diskPlaneSpeed <= 1.0e-6f || thrustAcceleration <= 1.0e-6f || ACRO_ROTOR_DISK_DRAG_COEFFICIENT <= 0.0f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float rpm = Math.max(ACRO_ADVANCE_REFERENCE_MIN_RPM, averageRpm(throttle, hoverThrottle));
		float spinRatio = clamp(rpm / ACRO_ROTOR_REFERENCE_MAX_RPM, 0.0f, 1.10f);
		if (spinRatio <= 0.08f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}

		float advanceRatioMu = acroRotorDiskAdvanceRatioMu(bodyVelocity, throttle, hoverThrottle);
		float activeDisk = smoothStep((spinRatio - 0.10f) / 0.22f);
		float crossflow = smoothStep((advanceRatioMu - 0.025f) / 0.325f);
		float loadedCrossflow = smoothStep((advanceRatioMu - 0.08f) / 0.47f);
		float sideslip = (float) Math.atan2(Math.abs(bodyVelocity.x()), Math.max(2.0f, Math.abs(bodyVelocity.z())));
		float sideslipExposure = smoothStep((sideslip - ACRO_ROTOR_IN_PLANE_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_ROTOR_IN_PLANE_SIDESLIP_FULL_RADIANS - ACRO_ROTOR_IN_PLANE_SIDESLIP_START_RADIANS));
		float flowWeight = ACRO_ROTOR_IN_PLANE_STRAIGHT_FLOW_WEIGHT
				+ (1.0f - ACRO_ROTOR_IN_PLANE_STRAIGHT_FLOW_WEIGHT) * sideslipExposure;

		float diskDragScale = clamp(ACRO_ROTOR_DISK_DRAG_COEFFICIENT / 0.0028f, 0.0f, 3.5f);
		float thrustCoupledCoefficient = diskDragScale
				* activeDisk
				* crossflow
				* (0.030f + 0.105f * loadedCrossflow);
		float thrustCoupledAcceleration = thrustAcceleration * thrustCoupledCoefficient;

		float totalDiskArea = ACRO_ROTOR_COUNT * (float) Math.PI * ACRO_ROTOR_RADIUS_METERS * ACRO_ROTOR_RADIUS_METERS;
		float dynamicPressure = 0.5f * ACRO_AIR_DENSITY_KILOGRAMS_PER_CUBIC_METER * diskPlaneSpeed * diskPlaneSpeed;
		float profileCoefficient = diskDragScale
				* activeDisk
				* (0.020f + 0.045f * loadedCrossflow)
				* smoothStep((advanceRatioMu - 0.04f) / 0.28f);
		float profileAcceleration = dynamicPressure * totalDiskArea * profileCoefficient / ACRO_REFERENCE_MASS_KILOGRAMS;
		float accelerationMagnitude = clamp(
				(thrustCoupledAcceleration + profileAcceleration) * flowWeight,
				0.0f,
				ACRO_ROTOR_IN_PLANE_MAX_ACCELERATION
		);
		if (accelerationMagnitude <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		return new Velocity(
				-bodyVelocity.x() / diskPlaneSpeed * accelerationMagnitude,
				0.0f,
				-bodyVelocity.z() / diskPlaneSpeed * accelerationMagnitude
		);
	}

	private static float acroResponsiveCollectiveThrustToWeight(FlightMode mode, float previousCollectiveThrustToWeight, float targetCollectiveThrustToWeight) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return targetCollectiveThrustToWeight;
		}
		if (!Float.isFinite(previousCollectiveThrustToWeight)) {
			return targetCollectiveThrustToWeight;
		}
		float smoothing = targetCollectiveThrustToWeight >= previousCollectiveThrustToWeight
				? ACRO_THRUST_RISE_SMOOTHING
				: ACRO_THRUST_FALL_SMOOTHING;
		float responsive = smooth(previousCollectiveThrustToWeight, targetCollectiveThrustToWeight, smoothing);
		if (Math.abs(responsive - targetCollectiveThrustToWeight) <= ACRO_THRUST_SETTLE_EPSILON) {
			return targetCollectiveThrustToWeight;
		}
		return responsive;
	}

	private static float acroCollectiveThrustToWeight(float throttle, float hoverThrottle) {
		if (throttle <= THRUST_DEADZONE) {
			return 0.0f;
		}
		float safeHover = clamp(hoverThrottle, 0.12f, 0.55f);
		if (throttle <= safeHover) {
			return clamp(throttle / safeHover, 0.0f, 1.0f);
		}
		float climbProgress = (throttle - safeHover) / Math.max(0.10f, 1.0f - safeHover);
		return 1.0f + clamp(climbProgress, 0.0f, 1.0f) * (ACRO_FULL_THROTTLE_THRUST_TO_WEIGHT - 1.0f);
	}

	static Velocity acroDragAcceleration(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians) {
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		Velocity bodyDragAcceleration = acroBodyAerodynamicAcceleration(bodyVelocity);
		return yawLocalVelocityForAcroBody(bodyDragAcceleration.x(), bodyDragAcceleration.y(), bodyDragAcceleration.z(), pitchRadians, rollRadians);
	}

	static Velocity acroBodyAerodynamicAcceleration(Velocity bodyVelocity) {
		Velocity baseDragAcceleration = new Velocity(
				-dragAcceleration(bodyVelocity.x(), ACRO_LATERAL_LINEAR_DRAG_PER_SECOND, ACRO_LATERAL_QUADRATIC_DRAG_PER_METER),
				-dragAcceleration(bodyVelocity.y(), ACRO_VERTICAL_LINEAR_DRAG_PER_SECOND, ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER),
				-dragAcceleration(bodyVelocity.z(), ACRO_FORWARD_LINEAR_DRAG_PER_SECOND, ACRO_FORWARD_QUADRATIC_DRAG_PER_METER)
		);
		float separation = acroAirframeSeparationIntensity(bodyVelocity.x(), bodyVelocity.y(), bodyVelocity.z());
		Velocity separatedDragAcceleration = acroSeparatedFlowDragAcceleration(bodyVelocity, separation);
		Velocity pitchLiftAcceleration = acroPitchPlaneLiftAcceleration(bodyVelocity, separation);
		Velocity sideforceAcceleration = acroSideslipSideforceAcceleration(bodyVelocity, separation);
		return new Velocity(
				baseDragAcceleration.x() + separatedDragAcceleration.x() + pitchLiftAcceleration.x() + sideforceAcceleration.x(),
				baseDragAcceleration.y() + separatedDragAcceleration.y() + pitchLiftAcceleration.y() + sideforceAcceleration.y(),
				baseDragAcceleration.z() + separatedDragAcceleration.z() + pitchLiftAcceleration.z() + sideforceAcceleration.z()
		);
	}

	static float acroAirframeSeparationIntensity(float bodyRightVelocity, float bodyUpVelocity, float bodyForwardVelocity) {
		float speedSquared = bodyRightVelocity * bodyRightVelocity + bodyUpVelocity * bodyUpVelocity + bodyForwardVelocity * bodyForwardVelocity;
		if (speedSquared <= 1.0e-6f) {
			return 0.0f;
		}
		float forwardReference = Math.max(2.0f, Math.abs(bodyForwardVelocity));
		float angleOfAttack = (float) Math.atan2(bodyUpVelocity, forwardReference);
		float sideslip = (float) Math.atan2(bodyRightVelocity, forwardReference);
		float pitchSeparation = smoothStep((Math.abs(angleOfAttack) - ACRO_SEPARATION_AOA_START_RADIANS)
				/ Math.max(0.001f, ACRO_SEPARATION_AOA_FULL_RADIANS - ACRO_SEPARATION_AOA_START_RADIANS));
		float yawSeparation = smoothStep((Math.abs(sideslip) - ACRO_SEPARATION_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_SEPARATION_SIDESLIP_FULL_RADIANS - ACRO_SEPARATION_SIDESLIP_START_RADIANS));
		return clamp(1.0f - (1.0f - pitchSeparation) * (1.0f - yawSeparation), 0.0f, 1.0f);
	}

	static Velocity acroPitchPlaneLiftAcceleration(Velocity bodyVelocity, float separation) {
		float pitchPlaneSpeed = horizontalMagnitude(bodyVelocity.y(), bodyVelocity.z());
		if (pitchPlaneSpeed <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float speedSquared = bodyVelocity.x() * bodyVelocity.x() + bodyVelocity.y() * bodyVelocity.y() + bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float angleOfAttack = (float) Math.atan2(bodyVelocity.y(), bodyVelocity.z());
		float pitchStall = smoothStep((Math.abs(angleOfAttack) - ACRO_PITCH_LIFT_AOA_STALL_START_RADIANS)
				/ Math.max(0.001f, ACRO_PITCH_LIFT_AOA_STALL_FULL_RADIANS - ACRO_PITCH_LIFT_AOA_STALL_START_RADIANS));
		float dynamicPitchStall = Math.max(0.32f * pitchStall, clamp(separation, 0.0f, 1.0f) * pitchStall);
		float stallScale = 1.0f - 0.55f * dynamicPitchStall;
		float liftCoefficient = ACRO_PITCH_LIFT_GAIN
				* (float) Math.sqrt(Math.max(0.0f, ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER * ACRO_FORWARD_QUADRATIC_DRAG_PER_METER));
		float liftMagnitude = liftCoefficient * speedSquared * (float) Math.sin(2.0f * angleOfAttack) * stallScale;
		return new Velocity(
				0.0f,
				bodyVelocity.z() / pitchPlaneSpeed * liftMagnitude,
				-bodyVelocity.y() / pitchPlaneSpeed * liftMagnitude
		);
	}

	static Velocity acroSideslipSideforceAcceleration(Velocity bodyVelocity, float separation) {
		float yawPlaneSpeed = horizontalMagnitude(bodyVelocity.x(), bodyVelocity.z());
		if (yawPlaneSpeed <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float sideslip = (float) Math.atan2(bodyVelocity.x(), bodyVelocity.z());
		float yawStall = smoothStep((Math.abs(sideslip) - ACRO_SIDEFORCE_SIDESLIP_STALL_START_RADIANS)
				/ Math.max(0.001f, ACRO_SIDEFORCE_SIDESLIP_STALL_FULL_RADIANS - ACRO_SIDEFORCE_SIDESLIP_STALL_START_RADIANS));
		float dynamicYawStall = Math.max(0.32f * yawStall, clamp(separation, 0.0f, 1.0f) * yawStall);
		float stallScale = 1.0f - 0.50f * dynamicYawStall;
		float sideforceCoefficient = ACRO_SIDEFORCE_GAIN
				* (float) Math.sqrt(Math.max(0.0f, ACRO_LATERAL_QUADRATIC_DRAG_PER_METER * ACRO_FORWARD_QUADRATIC_DRAG_PER_METER));
		float sideforceMagnitude = sideforceCoefficient * yawPlaneSpeed * yawPlaneSpeed * (float) Math.sin(2.0f * sideslip) * stallScale;
		return new Velocity(
				-bodyVelocity.z() / yawPlaneSpeed * sideforceMagnitude,
				0.0f,
				bodyVelocity.x() / yawPlaneSpeed * sideforceMagnitude
		);
	}

	static float acroSideslipWeathercockYawDegreesPerTick(Velocity bodyVelocity) {
		float strength = acroWeathercockStrength(bodyVelocity);
		if (strength <= 1.0e-6f) {
			return 0.0f;
		}
		return clamp(
				-Math.signum(bodyVelocity.x()) * strength * ACRO_WEATHERCOCK_YAW_GAIN_DEGREES_PER_TICK,
				-ACRO_WEATHERCOCK_YAW_MAX_DEGREES_PER_TICK,
				ACRO_WEATHERCOCK_YAW_MAX_DEGREES_PER_TICK
		);
	}

	static float acroSideslipYawDampingSmoothing(Velocity bodyVelocity) {
		float strength = acroWeathercockStrength(bodyVelocity);
		if (strength <= 1.0e-6f) {
			return 0.0f;
		}
		return clamp(strength * ACRO_SIDESLIP_YAW_DAMPING_GAIN, 0.0f, ACRO_SIDESLIP_YAW_DAMPING_MAX);
	}

	private static float acroAerodynamicYawRate(
			FlightMode mode,
			float yawDegreesPerTick,
			float yawCommand,
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians
	) {
		if (safeMode(mode) != FlightMode.ACRO) {
			return yawDegreesPerTick;
		}
		Velocity bodyVelocity = acroBodyVelocityForYawLocal(velocityX, velocityY, velocityZ, pitchRadians, rollRadians);
		float commandSuppression = 1.0f - smoothStep(Math.abs(yawCommand) / ACRO_WEATHERCOCK_YAW_COMMAND_SUPPRESS);
		if (commandSuppression <= 1.0e-6f) {
			return yawDegreesPerTick;
		}
		float damping = acroSideslipYawDampingSmoothing(bodyVelocity) * commandSuppression;
		float dampedYaw = damping <= 1.0e-6f ? yawDegreesPerTick : smooth(yawDegreesPerTick, 0.0f, damping);
		float weathercockYaw = acroSideslipWeathercockYawDegreesPerTick(bodyVelocity) * commandSuppression;
		return settledYawRate(dampedYaw + weathercockYaw, 0.0f);
	}

	private static float acroWeathercockStrength(Velocity bodyVelocity) {
		float lateralSpeed = Math.abs(bodyVelocity.x());
		float forwardSpeed = bodyVelocity.z();
		if (lateralSpeed <= 1.0e-6f || forwardSpeed <= ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND) {
			return 0.0f;
		}
		float speedSquared = bodyVelocity.x() * bodyVelocity.x() + bodyVelocity.y() * bodyVelocity.y() + bodyVelocity.z() * bodyVelocity.z();
		float sideslip = (float) Math.atan2(bodyVelocity.x(), Math.max(2.0f, forwardSpeed));
		float sideslipExposure = smoothStep((Math.abs(sideslip) - ACRO_WEATHERCOCK_SIDESLIP_START_RADIANS)
				/ Math.max(0.001f, ACRO_WEATHERCOCK_SIDESLIP_FULL_RADIANS - ACRO_WEATHERCOCK_SIDESLIP_START_RADIANS));
		float forwardExposure = smoothStep((forwardSpeed - ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_WEATHERCOCK_FORWARD_FULL_METERS_PER_SECOND - ACRO_WEATHERCOCK_FORWARD_START_METERS_PER_SECOND));
		float lateralExposure = smoothStep((lateralSpeed - ACRO_WEATHERCOCK_LATERAL_START_METERS_PER_SECOND)
				/ Math.max(0.001f, ACRO_WEATHERCOCK_LATERAL_FULL_METERS_PER_SECOND - ACRO_WEATHERCOCK_LATERAL_START_METERS_PER_SECOND));
		float weathercockArea = (float) Math.sqrt(Math.max(0.0f, ACRO_LATERAL_QUADRATIC_DRAG_PER_METER * ACRO_FORWARD_QUADRATIC_DRAG_PER_METER));
		float exposure = sideslipExposure * (0.45f + 0.35f * forwardExposure + 0.20f * lateralExposure);
		return Math.max(0.0f, speedSquared * weathercockArea * exposure);
	}

	private static Velocity acroSeparatedFlowDragAcceleration(Velocity bodyVelocity, float separation) {
		float speedSquared = bodyVelocity.x() * bodyVelocity.x() + bodyVelocity.y() * bodyVelocity.y() + bodyVelocity.z() * bodyVelocity.z();
		if (speedSquared <= 1.0e-6f || separation <= 1.0e-6f) {
			return new Velocity(0.0f, 0.0f, 0.0f);
		}
		float speed = (float) Math.sqrt(speedSquared);
		float maxBroadsideDrag = Math.max(ACRO_LATERAL_QUADRATIC_DRAG_PER_METER, ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER);
		float broadsideCoefficient = 0.20f * maxBroadsideDrag
				+ 0.14f * (float) Math.sqrt(Math.max(
						0.0f,
						(ACRO_LATERAL_QUADRATIC_DRAG_PER_METER + ACRO_VERTICAL_QUADRATIC_DRAG_PER_METER)
								* ACRO_FORWARD_QUADRATIC_DRAG_PER_METER
				));
		float separatedDragMagnitude = speedSquared * broadsideCoefficient * clamp(separation, 0.0f, 1.0f);
		return new Velocity(
				-bodyVelocity.x() / speed * separatedDragMagnitude,
				-bodyVelocity.y() / speed * separatedDragMagnitude,
				-bodyVelocity.z() / speed * separatedDragMagnitude
		);
	}

	static Velocity acroBodyVelocityForYawLocal(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians) {
		AcroBodyFrame frame = acroBodyFrame(pitchRadians, rollRadians);
		return new Velocity(
				dot(velocityX, velocityY, velocityZ, frame.right()),
				dot(velocityX, velocityY, velocityZ, frame.up()),
				dot(velocityX, velocityY, velocityZ, frame.forward())
		);
	}

	static Velocity yawLocalVelocityForAcroBody(float bodyRight, float bodyUp, float bodyForward, float pitchRadians, float rollRadians) {
		AcroBodyFrame frame = acroBodyFrame(pitchRadians, rollRadians);
		return new Velocity(
				frame.right().x() * bodyRight + frame.up().x() * bodyUp + frame.forward().x() * bodyForward,
				frame.right().y() * bodyRight + frame.up().y() * bodyUp + frame.forward().y() * bodyForward,
				frame.right().z() * bodyRight + frame.up().z() * bodyUp + frame.forward().z() * bodyForward
		);
	}

	private static AcroBodyFrame acroBodyFrame(float pitchRadians, float rollRadians) {
		float pitch = Float.isFinite(pitchRadians) ? pitchRadians : 0.0f;
		float roll = Float.isFinite(rollRadians) ? rollRadians : 0.0f;
		float sinPitch = (float) Math.sin(pitch);
		float cosPitch = (float) Math.cos(pitch);
		float sinRoll = (float) Math.sin(roll);
		float cosRoll = (float) Math.cos(roll);
		return new AcroBodyFrame(
				new Velocity(cosRoll, cosPitch * sinRoll, sinPitch * sinRoll),
				new Velocity(-sinRoll, cosPitch * cosRoll, sinPitch * cosRoll),
				new Velocity(0.0f, -sinPitch, cosPitch)
		);
	}

	private static float dragAcceleration(float velocity, float linearDragPerSecond, float quadraticDragPerMeter) {
		return velocity * (linearDragPerSecond + quadraticDragPerMeter * Math.abs(velocity));
	}

	private static float bodyQuadraticDragPerMeter(float dragAreaSquareMeters) {
		return 0.5f * ACRO_AIR_DENSITY_KILOGRAMS_PER_CUBIC_METER * dragAreaSquareMeters / ACRO_REFERENCE_MASS_KILOGRAMS;
	}

	private static float dot(float x, float y, float z, Velocity axis) {
		return x * axis.x() + y * axis.y() + z * axis.z();
	}

	private static Velocity limitHorizontalVector(float velocityX, float velocityY, float velocityZ, float limitMetersPerSecond) {
		float limit = Math.max(0.0f, limitMetersPerSecond);
		float horizontalSpeed = horizontalMagnitude(velocityX, velocityZ);
		if (horizontalSpeed <= limit || horizontalSpeed <= 1.0e-6f) {
			return new Velocity(velocityX, velocityY, velocityZ);
		}
		float scale = limit / horizontalSpeed;
		return new Velocity(velocityX * scale, velocityY, velocityZ * scale);
	}

	private static float horizontalMagnitude(float x, float z) {
		return (float) Math.sqrt(x * x + z * z);
	}

	private static float lowAltitudeAttitudeCommandAuthority(FlightMode mode, boolean nearGroundLocked, float lowAltitudeHorizontalAuthorityScale) {
		float minimum = switch (safeMode(mode)) {
			case ANGLE -> 0.68f;
			case HORIZON -> 0.76f;
			case ACRO -> 0.90f;
		};
		return lowAltitudeCommandAuthority(minimum, nearGroundLocked, lowAltitudeHorizontalAuthorityScale);
	}

	private static float lowAltitudeYawCommandAuthority(FlightMode mode, boolean nearGroundLocked, float lowAltitudeHorizontalAuthorityScale) {
		float minimum = switch (safeMode(mode)) {
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

	private static float groundHorizontalAuthorityScale(FlightMode mode) {
		return switch (safeMode(mode)) {
			case ANGLE -> GROUND_ANGLE_HORIZONTAL_AUTHORITY_SCALE;
			case HORIZON -> GROUND_HORIZON_HORIZONTAL_AUTHORITY_SCALE;
			case ACRO -> GROUND_ACRO_HORIZONTAL_AUTHORITY_SCALE;
		};
	}

	private static float smooth(float current, float target, float smoothing) {
		return current + (target - current) * clamp(smoothing, 0.0f, 1.0f);
	}

	private static float inertialVelocity(float current, float target, float smoothing, float acceleration, float brakeAcceleration) {
		float unconstrained = smooth(current, target, smoothing);
		float accelerationLimit = isVelocityBraking(current, target) ? brakeAcceleration : acceleration;
		float maxDelta = Math.max(0.0f, accelerationLimit) * PLAYABLE_TICK_SECONDS;
		return current + clamp(unconstrained - current, -maxDelta, maxDelta);
	}

	private static float velocitySmoothing(float current, float target, Profile profile) {
		return isVelocityBraking(current, target) ? profile.velocityBrakeSmoothing() : profile.velocitySmoothing();
	}

	private static boolean isVelocityBraking(float current, float target) {
		boolean reversing = Math.signum(current) != 0.0f
				&& Math.signum(target) != 0.0f
				&& Math.signum(current) != Math.signum(target);
		return reversing || Math.abs(target) < Math.abs(current);
	}

	private static float verticalVelocitySmoothing(float current, float target, Profile profile) {
		float smoothing = velocitySmoothing(current, target, profile);
		if (Math.abs(target) <= VELOCITY_SETTLE_EPSILON_MPS && Math.abs(current) > VELOCITY_SETTLE_EPSILON_MPS) {
			return Math.max(smoothing, VERTICAL_HOVER_BRAKE_SMOOTHING);
		}
		return smoothing;
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
				&& throttle >= hoverThrottle - profile.airBrakeThrottleBand()
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

	private static FlightMode safeMode(FlightMode mode) {
		return mode == null ? DEFAULT_PLAYABLE_MODE : mode;
	}

	record State(
			float velocityX,
			float velocityY,
			float velocityZ,
			float pitchRadians,
			float rollRadians,
			float yawDegreesPerTick,
			FlightMode mode,
			int modeSwitchTicksRemaining,
			float acroCollectiveThrustToWeight,
			float acroPitchRateRadiansPerTick,
			float acroRollRateRadiansPerTick
	) {
		static final State ZERO = zero(DEFAULT_PLAYABLE_MODE);

		State {
			mode = safeMode(mode);
			modeSwitchTicksRemaining = Math.max(0, modeSwitchTicksRemaining);
			acroCollectiveThrustToWeight = Float.isFinite(acroCollectiveThrustToWeight)
					? clamp(acroCollectiveThrustToWeight, 0.0f, ACRO_FULL_THROTTLE_THRUST_TO_WEIGHT)
					: Float.NaN;
			acroPitchRateRadiansPerTick = Float.isFinite(acroPitchRateRadiansPerTick) ? acroPitchRateRadiansPerTick : Float.NaN;
			acroRollRateRadiansPerTick = Float.isFinite(acroRollRateRadiansPerTick) ? acroRollRateRadiansPerTick : Float.NaN;
		}

		State(float velocityX, float velocityY, float velocityZ) {
			this(velocityX, velocityY, velocityZ, 0.0f, 0.0f, 0.0f, DEFAULT_PLAYABLE_MODE, 0, Float.NaN, Float.NaN, Float.NaN);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, 0.0f, DEFAULT_PLAYABLE_MODE, 0, Float.NaN, Float.NaN, Float.NaN);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians, float yawDegreesPerTick) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, DEFAULT_PLAYABLE_MODE, 0, Float.NaN, Float.NaN, Float.NaN);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians, float yawDegreesPerTick, FlightMode mode) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, mode, 0, Float.NaN, Float.NaN, Float.NaN);
		}

		State(float velocityX, float velocityY, float velocityZ, float pitchRadians, float rollRadians, float yawDegreesPerTick, FlightMode mode, int modeSwitchTicksRemaining) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, mode, modeSwitchTicksRemaining, Float.NaN, Float.NaN, Float.NaN);
		}

		State(
				float velocityX,
				float velocityY,
				float velocityZ,
				float pitchRadians,
				float rollRadians,
				float yawDegreesPerTick,
				FlightMode mode,
				int modeSwitchTicksRemaining,
				float acroCollectiveThrustToWeight
		) {
			this(velocityX, velocityY, velocityZ, pitchRadians, rollRadians, yawDegreesPerTick, mode, modeSwitchTicksRemaining, acroCollectiveThrustToWeight, Float.NaN, Float.NaN);
		}

		static State zero(FlightMode mode) {
			return new State(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, safeMode(mode), 0, 0.0f, 0.0f, 0.0f);
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
			float acroCollectiveThrustToWeight,
			float acroPitchRateRadiansPerTick,
			float acroRollRateRadiansPerTick,
			FlightMode mode,
			int modeSwitchTicksRemaining
	) {
	}

	record Velocity(float x, float y, float z) {
	}

	private record Attitude(float pitchRadians, float rollRadians) {
	}

	private record AcroRateResponse(float pitchRateRadiansPerTick, float rollRateRadiansPerTick) {
		private static final AcroRateResponse ZERO = new AcroRateResponse(0.0f, 0.0f);
	}

	private record AcroBodyFrame(Velocity right, Velocity up, Velocity forward) {
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
			float horizontalAccelerationMetersPerSecondSquared,
			float horizontalBrakeAccelerationMetersPerSecondSquared,
			float verticalAccelerationMetersPerSecondSquared,
			float verticalBrakeAccelerationMetersPerSecondSquared,
			float groundFrictionSmoothing,
			float groundFrictionTargetVelocityThreshold,
			float airBrakeSmoothing,
			float airBrakeCommandThreshold,
			float airBrakeThrottleBand,
			float hoverBand,
			float horizontalFineVelocityScale,
			float horizontalVelocityLinearStart,
			float descentGain,
			float thrustGain
	) {
		private static Profile forMode(FlightMode mode) {
			return switch (safeMode(mode)) {
				case ANGLE -> new Profile(3.20f, 4.40f, radians(24.0f), radians(24.0f), radians(48.0f), radians(48.0f), radians(3.0f), radians(3.2f), 1.75f, 0.58f, 0.78f, 0.24f, radians(2.6f), 0.74f, radians(7.2f), 0.84f, 0.20f, 0.42f, 4.50f, 7.50f, 10.50f, 12.00f, 0.74f, 0.12f, 0.48f, 0.070f, 0.10f, 0.055f, 0.82f, 0.85f, DESCENT_GAIN, 3.60f);
				case HORIZON -> new Profile(8.80f, 12.00f, radians(46.0f), radians(48.0f), radians(80.0f), radians(84.0f), radians(6.3f), radians(6.8f), 3.55f, 0.82f, 0.70f, 0.22f, radians(3.8f), 0.30f, radians(5.2f), 0.93f, 0.18f, 0.28f, 8.50f, 9.50f, 10.80f, 13.00f, 0.56f, 0.16f, 0.06f, 0.060f, 0.09f, HOVER_BAND, 0.92f, 0.78f, 3.00f, THRUST_GAIN);
				case ACRO -> new Profile(25.00f, 32.00f, radians(64.0f), radians(68.0f), radians(115.0f), radians(125.0f), radians(8.8f), radians(9.4f), 5.40f, 0.94f, 0.36f, 0.18f, radians(5.80f), 0.15f, radians(5.20f), 1.0f, 0.28f, 0.18f, 14.00f, 8.00f, 11.50f, 13.50f, 0.34f, 0.18f, 0.0f, 0.0f, 0.0f, 0.030f, 1.0f, 1.0f, 3.40f, 5.00f);
			};
		}

		private static float radians(float degrees) {
			return (float) Math.toRadians(degrees);
		}
	}
}
