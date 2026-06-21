package com.tenicana.dronecraft.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.RotorSpec;
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.flight.ActuatorOutput;
import com.tenicana.dronecraft.sim.flight.FlightModel;
import com.tenicana.dronecraft.sim.flight.FlightModelCapabilities;
import com.tenicana.dronecraft.sim.flight.FlightModelDiagnostics;
import com.tenicana.dronecraft.sim.flight.FlightModelInitializationContext;
import com.tenicana.dronecraft.sim.flight.FlightStateSnapshot;
import com.tenicana.dronecraft.sim.flight.FlightStepContext;
import com.tenicana.dronecraft.sim.flight.FlightStepResult;
import com.tenicana.dronecraft.sim.flight.ForceTorqueDiagnostics;
import com.tenicana.dronecraft.sim.flight.StateCorrection;
import com.tenicana.dronecraft.sim.flight.StateCorrectionReason;

final class LegacyPlayableFlightModelAdapter implements FlightModel {
	private static final String ID = "legacy_playable_direct";
	private static final float DEBUG_AXIS_RISE_SMOOTH = PlayableDebugAxisFilter.DEFAULT_RISE_SMOOTHING;
	private static final float DEBUG_AXIS_FALL_SMOOTH = PlayableDebugAxisFilter.DEFAULT_FALL_SMOOTHING;
	private static final float DEBUG_THRUST_DEADZONE = 0.005f;
	private static final float DEBUG_MOVEMENT_EPSILON = 0.015f;
	private static final float LOW_ALTITUDE_LOCKED_AUTHORITY = 0.62f;
	private static final double APPROXIMATE_GROUND_LOCK_CLEARANCE_METERS = 0.30;
	private static final double PLAYABLE_TAKEOFF_GUARD_RELEASE_CLEARANCE_METERS = 1.35;
	private static final FlightMode DEFAULT_ENTITY_FLIGHT_MODE = FlightMode.DEFAULT_FIRST_FLIGHT;

	private DroneConfig config = DroneConfig.racingQuad();
	private Vec3 positionWorldMeters = Vec3.ZERO;
	private Vec3 lastWorldVelocityMetersPerSecond = Vec3.ZERO;
	private Vec3 lastBodyVelocityMetersPerSecond = Vec3.ZERO;
	private Vec3 lastBodyRateRadiansPerSecond = Vec3.ZERO;
	private Quaternion lastAttitude = Quaternion.IDENTITY;
	private boolean lastArmed;
	private float yawDegrees;
	private float debugVelocityYawDegrees;
	private float debugCommandThrottle;
	private float debugCommandPitch;
	private float debugCommandRoll;
	private float debugCommandYaw;
	private float debugVelocityX;
	private float debugVelocityY;
	private float debugVelocityZ;
	private float debugVisualPitchRadians;
	private float debugVisualRollRadians;
	private float debugTargetYawRate;
	private FlightMode debugFlightMode = DEFAULT_ENTITY_FLIGHT_MODE;
	private int debugModeSwitchTicksRemaining;
	private float debugMotorPower;
	private float debugAverageMotorRpm;
	private float debugAcroCollectiveThrustToWeight;
	private float debugAcroPitchRateRadiansPerTick;
	private float debugAcroRollRateRadiansPerTick;
	private int debugAcroRollRecoveryTicksRemaining;
	private float debugAcroAeroCrossflowLag;
	private float debugAcroSidewashMemory;
	private FlightModelDiagnostics diagnostics = FlightModelDiagnostics.empty();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public FlightModelCapabilities capabilities() {
		return FlightModelCapabilities.playableDirect();
	}

	@Override
	public void initialize(FlightModelInitializationContext context) {
		config = context.config();
		reset(context.initialState());
		diagnostics = diagnosticsFor(context.environment(), List.of(), List.of("initial_state.euler_attitude_projection"));
	}

	@Override
	public void reset(FlightStateSnapshot state) {
		FlightStateSnapshot safeState = state == null ? FlightStateSnapshot.zero() : state;
		positionWorldMeters = safeState.positionWorldMeters();
		lastWorldVelocityMetersPerSecond = safeState.velocityWorldMetersPerSecond();
		Vec3 euler = safeState.attitude().toEulerXYZRadians();
		debugVisualPitchRadians = (float) euler.x();
		yawDegrees = (float) Math.toDegrees(euler.y());
		debugVisualRollRadians = (float) euler.z();
		PlayableFlightModel.Velocity localVelocity = PlayableFlightModel.localVelocityForYaw(
				(float) lastWorldVelocityMetersPerSecond.x(),
				(float) lastWorldVelocityMetersPerSecond.y(),
				(float) lastWorldVelocityMetersPerSecond.z(),
				yawDegrees
		);
		debugVelocityX = localVelocity.x();
		debugVelocityY = localVelocity.y();
		debugVelocityZ = localVelocity.z();
		debugVelocityYawDegrees = yawDegrees;
		lastAttitude = attitudeQuaternion(yawDegrees, debugVisualPitchRadians, debugVisualRollRadians);
		lastBodyVelocityMetersPerSecond = lastAttitude.conjugate().rotate(lastWorldVelocityMetersPerSecond);
		lastBodyRateRadiansPerSecond = safeState.angularVelocityBodyRadiansPerSecond();
		lastArmed = safeState.armed();
		debugCommandThrottle = 0.0f;
		debugCommandPitch = 0.0f;
		debugCommandRoll = 0.0f;
		debugCommandYaw = 0.0f;
		debugTargetYawRate = 0.0f;
		debugMotorPower = 0.0f;
		debugAverageMotorRpm = 0.0f;
		debugAcroCollectiveThrustToWeight = 0.0f;
		debugAcroPitchRateRadiansPerTick = 0.0f;
		debugAcroRollRateRadiansPerTick = 0.0f;
		debugAcroRollRecoveryTicksRemaining = 0;
		debugAcroAeroCrossflowLag = 0.0f;
		debugAcroSidewashMemory = 0.0f;
		debugModeSwitchTicksRemaining = 0;
		debugFlightMode = safeState.flightMode();
		diagnostics = FlightModelDiagnostics.empty();
	}

	@Override
	public FlightStepResult step(FlightStepContext context) {
		config = context.config();
		DroneInput input = context.input();
		DroneEnvironment environment = context.environment();
		List<StateCorrection> corrections = new ArrayList<>();
		List<String> lossyFields = lossyEnvironmentFields(environment);
		rebaseDebugVelocityToCurrentYaw();

		float throttle = (float) MathUtil.clamp(input.throttle(), 0.0, 1.0);
		float pitch = (float) MathUtil.clamp(input.pitch(), -1.0, 1.0);
		float roll = (float) MathUtil.clamp(input.roll(), -1.0, 1.0);
		float yaw = (float) MathUtil.clamp(input.yaw(), -1.0, 1.0);

		float playablePitch = PlayableFlightModel.playableAxisCommand(pitch);
		float playableRoll = PlayableFlightModel.playableAxisCommand(roll);
		float playableYaw = PlayableFlightModel.playableAxisCommand(yaw);

		float smoothedThrottle = PlayableDebugAxisFilter.throttle(debugCommandThrottle, throttle);
		float smoothedPitch = PlayableDebugAxisFilter.filter(debugCommandPitch, playablePitch, DEBUG_AXIS_RISE_SMOOTH, DEBUG_AXIS_FALL_SMOOTH, true);
		float smoothedRoll = PlayableDebugAxisFilter.filter(debugCommandRoll, playableRoll, DEBUG_AXIS_RISE_SMOOTH, DEBUG_AXIS_FALL_SMOOTH, true);
		float smoothedYaw = PlayableDebugAxisFilter.filter(debugCommandYaw, playableYaw, DEBUG_AXIS_RISE_SMOOTH, DEBUG_AXIS_FALL_SMOOTH, true);

		debugCommandThrottle = smoothedThrottle;
		debugCommandPitch = smoothedPitch;
		debugCommandRoll = smoothedRoll;
		debugCommandYaw = smoothedYaw;

		boolean shouldFly = input.armed()
				|| smoothedThrottle > DEBUG_THRUST_DEADZONE
				|| Math.abs(smoothedPitch) > DEBUG_MOVEMENT_EPSILON
				|| Math.abs(smoothedRoll) > DEBUG_MOVEMENT_EPSILON;
		float previousPitch = debugVisualPitchRadians;
		float previousRoll = debugVisualRollRadians;
		float previousYaw = yawDegrees;
		int previousRecoveryTicks = debugAcroRollRecoveryTicksRemaining;
		boolean nearGroundLocked = nearGroundLocked(environment);
		float lowAltitudeHorizontalAuthorityScale = lowAltitudeHorizontalAuthorityScale(environment, nearGroundLocked);
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				input.flightMode(),
				smoothedThrottle,
				smoothedPitch,
				smoothedRoll,
				smoothedYaw,
				(float) MathUtil.clamp(config.hoverThrottle(), 0.12, 0.55),
				nearGroundLocked,
				lowAltitudeHorizontalAuthorityScale,
				new PlayableFlightModel.State(
						debugVelocityX,
						debugVelocityY,
						debugVelocityZ,
						debugVisualPitchRadians,
						debugVisualRollRadians,
						debugTargetYawRate,
						debugFlightMode,
						debugModeSwitchTicksRemaining,
						debugAcroCollectiveThrustToWeight,
						debugAcroPitchRateRadiansPerTick,
						debugAcroRollRateRadiansPerTick,
						debugAcroRollRecoveryTicksRemaining,
						debugAcroAeroCrossflowLag,
						debugAcroSidewashMemory
				)
		);

		float targetYaw = settledTargetYaw(step.yawDegreesPerTick());
		if (!shouldFly) {
			clearDebugFlightState();
			lastArmed = input.armed();
			StateCorrection correction = new StateCorrection(StateCorrectionReason.GROUND_STABILIZATION, "IDLE_CLEAR", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
			corrections.add(correction);
			diagnostics = diagnosticsFor(environment, corrections, lossyFields);
			return result(corrections);
		}

		debugVelocityX = settledVelocity(step.velocityX(), 0.015f);
		debugVelocityY = settledVelocity(step.velocityY(), 0.010f);
		debugVelocityZ = settledVelocity(step.velocityZ(), 0.015f);
		debugVisualPitchRadians = step.pitchRadians();
		debugVisualRollRadians = step.rollRadians();
		debugFlightMode = step.mode();
		debugModeSwitchTicksRemaining = step.modeSwitchTicksRemaining();
		debugMotorPower = step.motorPower();
		debugAverageMotorRpm = step.averageRpm();
		debugTargetYawRate = targetYaw;
		debugAcroCollectiveThrustToWeight = step.acroCollectiveThrustToWeight();
		debugAcroPitchRateRadiansPerTick = step.acroPitchRateRadiansPerTick();
		debugAcroRollRateRadiansPerTick = step.acroRollRateRadiansPerTick();
		debugAcroRollRecoveryTicksRemaining = step.acroRollRecoveryTicksRemaining();
		debugAcroAeroCrossflowLag = step.acroAeroCrossflowLag();
		debugAcroSidewashMemory = step.acroSidewashMemory();
		if (previousRecoveryTicks == 0 && debugAcroRollRecoveryTicksRemaining > 0) {
			corrections.add(new StateCorrection(StateCorrectionReason.COMPLETED_ROLL_VELOCITY_TRIM, "ACRO_ROLL_RECOVERY_STARTED", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO));
		}

		float movementYawDegrees = PlayableMovementYaw.midpointForTick(yawDegrees, targetYaw);
		PlayableFlightModel.Velocity worldVelocity = PlayableFlightModel.worldVelocityForYaw(
				debugVelocityX,
				debugVelocityY,
				debugVelocityZ,
				movementYawDegrees
		);
		lastWorldVelocityMetersPerSecond = new Vec3(worldVelocity.x(), worldVelocity.y(), worldVelocity.z());
		positionWorldMeters = positionWorldMeters.add(lastWorldVelocityMetersPerSecond.multiply(context.dtSeconds()));
		PlayableFlightModel.Velocity localVelocity = PlayableFlightModel.localVelocityForYaw(
				(float) lastWorldVelocityMetersPerSecond.x(),
				(float) lastWorldVelocityMetersPerSecond.y(),
				(float) lastWorldVelocityMetersPerSecond.z(),
				movementYawDegrees
		);
		debugVelocityX = localVelocity.x();
		debugVelocityY = localVelocity.y();
		debugVelocityZ = localVelocity.z();
		debugVelocityYawDegrees = movementYawDegrees;
		if (Math.abs(targetYaw) > PlayableMovementYaw.APPLY_EPSILON_DEGREES) {
			yawDegrees += targetYaw;
		}
		lastAttitude = attitudeQuaternion(yawDegrees, debugVisualPitchRadians, debugVisualRollRadians);
		lastBodyVelocityMetersPerSecond = lastAttitude.conjugate().rotate(lastWorldVelocityMetersPerSecond);
		lastBodyRateRadiansPerSecond = new Vec3(
				(debugVisualPitchRadians - previousPitch) / context.dtSeconds(),
				Math.toRadians(yawDegrees - previousYaw) / context.dtSeconds(),
				(debugVisualRollRadians - previousRoll) / context.dtSeconds()
		);
		lastArmed = input.armed();
		diagnostics = diagnosticsFor(environment, corrections, lossyFields);
		return result(corrections);
	}

	@Override
	public FlightStateSnapshot snapshot() {
		return new FlightStateSnapshot(
				positionWorldMeters,
				lastWorldVelocityMetersPerSecond,
				lastAttitude,
				lastBodyRateRadiansPerSecond,
				debugFlightMode,
				lastArmed
		);
	}

	@Override
	public FlightModelDiagnostics diagnostics() {
		return diagnostics;
	}

	private FlightStepResult result(List<StateCorrection> corrections) {
		return new FlightStepResult(
				snapshot(),
				actuatorOutput(),
				ForceTorqueDiagnostics.zero(),
				corrections,
				diagnostics
		);
	}

	private ActuatorOutput actuatorOutput() {
		List<RotorSpec> rotors = config.rotors();
		double[] motorPower = new double[rotors.size()];
		double[] motorRpm = new double[rotors.size()];
		double[] rotorThrust = new double[rotors.size()];
		for (int i = 0; i < rotors.size(); i++) {
			double mix = 1.0 + rotorMixerPreview(i);
			motorPower[i] = MathUtil.clamp(debugMotorPower * mix, 0.0, 1.0);
			motorRpm[i] = Math.max(0.0, debugAverageMotorRpm * mix);
			rotorThrust[i] = motorPower[i] * rotors.get(i).maxThrustNewtons() * 0.45;
		}
		return new ActuatorOutput(motorPower, motorRpm, rotorThrust);
	}

	private double rotorMixerPreview(int rotorIndex) {
		double rollSign = switch (rotorIndex & 3) {
			case 0, 3 -> 1.0;
			default -> -1.0;
		};
		double pitchSign = switch (rotorIndex & 3) {
			case 0, 1 -> 1.0;
			default -> -1.0;
		};
		double yawSign = config.rotors().get(rotorIndex).spinDirection();
		return 0.04 * debugCommandRoll * rollSign
				+ 0.04 * debugCommandPitch * pitchSign
				+ 0.025 * debugCommandYaw * yawSign;
	}

	private FlightModelDiagnostics diagnosticsFor(DroneEnvironment environment, List<StateCorrection> corrections, List<String> lossyFields) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("yaw_degrees", Float.toString(yawDegrees));
		values.put("velocity_yaw_degrees", Float.toString(debugVelocityYawDegrees));
		values.put("mode_switch_ticks_remaining", Integer.toString(debugModeSwitchTicksRemaining));
		values.put("acro_roll_recovery_ticks_remaining", Integer.toString(debugAcroRollRecoveryTicksRemaining));
		values.put("ground_clearance_m", Double.toString(environment.groundClearanceMeters()));
		values.put("ground_lock_threshold_m", Double.toString(APPROXIMATE_GROUND_LOCK_CLEARANCE_METERS));
		return new FlightModelDiagnostics(snapshot().isFinite(), values, corrections, lossyFields);
	}

	private List<String> lossyEnvironmentFields(DroneEnvironment environment) {
		List<String> lossy = new ArrayList<>();
		if (environment.windVelocityWorldMetersPerSecond().length() > 1.0e-9) {
			lossy.add("environment.windVelocityWorldMetersPerSecond");
		}
		if (Math.abs(environment.airDensityRatio() - 1.0) > 1.0e-9) {
			lossy.add("environment.airDensityRatio");
		}
		if (environment.turbulenceIntensity() > 1.0e-9) {
			lossy.add("environment.turbulenceIntensity");
		}
		if (environment.obstacleProximity() > 1.0e-9) {
			lossy.add("environment.obstacleProximity");
		}
		if (Double.isFinite(environment.ceilingClearanceMeters())) {
			lossy.add("environment.ceilingClearanceMeters");
		}
		if (environment.rotorThrustMultipliers() != null) {
			lossy.add("environment.rotorThrustMultipliers");
		}
		if (environment.rotorFlowObstructions() != null) {
			lossy.add("environment.rotorFlowObstructions");
		}
		return lossy;
	}

	private boolean nearGroundLocked(DroneEnvironment environment) {
		return Double.isFinite(environment.groundClearanceMeters())
				&& environment.groundClearanceMeters() <= APPROXIMATE_GROUND_LOCK_CLEARANCE_METERS;
	}

	private float lowAltitudeHorizontalAuthorityScale(DroneEnvironment environment, boolean nearGroundLocked) {
		if (nearGroundLocked) {
			return LOW_ALTITUDE_LOCKED_AUTHORITY;
		}
		double clearance = environment.groundClearanceMeters();
		if (!Double.isFinite(clearance)) {
			return 1.0f;
		}
		double releaseClearance = Math.max(APPROXIMATE_GROUND_LOCK_CLEARANCE_METERS + 0.05, PLAYABLE_TAKEOFF_GUARD_RELEASE_CLEARANCE_METERS);
		double progress = MathUtil.clamp((clearance - APPROXIMATE_GROUND_LOCK_CLEARANCE_METERS) / (releaseClearance - APPROXIMATE_GROUND_LOCK_CLEARANCE_METERS), 0.0, 1.0);
		double eased = progress * progress * (3.0 - 2.0 * progress);
		return (float) MathUtil.lerp(LOW_ALTITUDE_LOCKED_AUTHORITY, 1.0, eased);
	}

	private void rebaseDebugVelocityToCurrentYaw() {
		if (Math.abs(yawDegrees - debugVelocityYawDegrees) <= 1.0e-4f) {
			return;
		}
		PlayableFlightModel.Velocity localVelocity = PlayableFlightModel.reframeVelocityForYaw(
				debugVelocityX,
				debugVelocityY,
				debugVelocityZ,
				debugVelocityYawDegrees,
				yawDegrees
		);
		debugVelocityX = localVelocity.x();
		debugVelocityY = localVelocity.y();
		debugVelocityZ = localVelocity.z();
		debugVelocityYawDegrees = yawDegrees;
	}

	private void clearDebugFlightState() {
		debugVelocityX = 0.0f;
		debugVelocityY = 0.0f;
		debugVelocityZ = 0.0f;
		debugVelocityYawDegrees = yawDegrees;
		debugVisualPitchRadians = 0.0f;
		debugVisualRollRadians = 0.0f;
		debugMotorPower = 0.0f;
		debugAverageMotorRpm = 0.0f;
		debugTargetYawRate = 0.0f;
		debugAcroCollectiveThrustToWeight = 0.0f;
		debugAcroPitchRateRadiansPerTick = 0.0f;
		debugAcroRollRateRadiansPerTick = 0.0f;
		debugAcroRollRecoveryTicksRemaining = 0;
		debugAcroAeroCrossflowLag = 0.0f;
		debugAcroSidewashMemory = 0.0f;
		debugModeSwitchTicksRemaining = 0;
		debugCommandThrottle = 0.0f;
		debugCommandPitch = 0.0f;
		debugCommandRoll = 0.0f;
		debugCommandYaw = 0.0f;
		debugFlightMode = DEFAULT_ENTITY_FLIGHT_MODE;
		lastWorldVelocityMetersPerSecond = Vec3.ZERO;
		lastBodyVelocityMetersPerSecond = Vec3.ZERO;
		lastBodyRateRadiansPerSecond = Vec3.ZERO;
		lastAttitude = attitudeQuaternion(yawDegrees, 0.0f, 0.0f);
	}

	private static float settledVelocity(float value, float epsilon) {
		return Math.abs(value) < epsilon ? 0.0f : value;
	}

	private static float settledTargetYaw(float value) {
		return Math.abs(value) < 0.015f ? 0.0f : value;
	}

	private static Quaternion attitudeQuaternion(double yawDegrees, double pitchRadians, double rollRadians) {
		Quaternion yaw = axisAngle(0.0, 1.0, 0.0, Math.toRadians(yawDegrees));
		Quaternion pitch = axisAngle(1.0, 0.0, 0.0, pitchRadians);
		Quaternion roll = axisAngle(0.0, 0.0, 1.0, rollRadians);
		return yaw.multiply(pitch).multiply(roll).normalized();
	}

	private static Quaternion axisAngle(double x, double y, double z, double radians) {
		double half = radians * 0.5;
		double sin = Math.sin(half);
		return new Quaternion(Math.cos(half), x * sin, y * sin, z * sin).normalized();
	}
}
