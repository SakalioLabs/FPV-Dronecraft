package com.tenicana.dronecraft.sim;

import java.util.List;

public record DroneConfig(
		double massKg,
		Vec3 inertiaKgMetersSquared,
		Vec3 centerOfMassOffsetBodyMeters,
		Vec3 imuOffsetBodyMeters,
		Vec3 centerOfPressureOffsetBodyMeters,
		List<RotorSpec> rotors,
		double gravityMetersPerSecondSquared,
		double linearDragCoefficient,
		Vec3 bodyDragCoefficients,
		double groundEffectHeightMeters,
		double groundEffectMaxThrustBoost,
		double propwashStartDescentMetersPerSecond,
		double propwashFullDescentMetersPerSecond,
		double propwashMaxTorqueNewtonMeters,
		double angularDragCoefficient,
		double motorTimeConstantSeconds,
		double escOutputCurveExponent,
		double throttleCommandCurveExponent,
		double escOutputSlewRatePerSecond,
		double escOutputFallSlewRatePerSecond,
		double escDeadband,
		double motorActiveBrakingStrength,
		double voltageCompensationStrength,
		double motorThermalRiseCelsiusPerSecond,
		double motorCoolingRatePerSecond,
		double motorThermalLimitCelsius,
		double motorThermalCutoffCelsius,
		double motorIdleThrustFraction,
		double airmodeStrength,
		double gyroLowPassCutoffHz,
		double gyroNoiseStdDevRadiansPerSecond,
		double accelerometerLowPassCutoffHz,
		double accelerometerNoiseStdDevMetersPerSecondSquared,
		double controlLatencySeconds,
		double nominalBatteryVoltage,
		double emptyBatteryVoltage,
		double batteryInternalResistanceOhms,
		double batteryCapacityAmpHours,
		double maxBatteryCurrentAmps,
		double maxPitchRateRadiansPerSecond,
		double maxYawRateRadiansPerSecond,
		double maxRollRateRadiansPerSecond,
		Vec3 rateExpo,
		Vec3 rateSuper,
		PidGains pitchGains,
		PidGains yawGains,
		PidGains rollGains,
		double rcCommandSmoothingTimeConstantSeconds,
		double rcCommandLatencySeconds,
		double rcFailsafeTimeoutSeconds,
		double attitudeEstimatorAccelerometerCorrectionGain,
		double attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
		double selfLevelMaxAngleRadians,
		double selfLevelRateGain,
		double horizonTransitionStartStick,
		double horizonTransitionEndStick,
		double pidIntegralRelaxStrength,
		double rcFrameRateHertz,
		int rcChannelResolutionSteps,
		double escCommandFrameRateHertz,
		int escCommandResolutionSteps,
		EscCommandProtocol escCommandProtocol
) {
	public static final double DEFAULT_SELF_LEVEL_MAX_ANGLE_RADIANS = Math.toRadians(55.0);
	public static final double DEFAULT_SELF_LEVEL_RATE_GAIN = 6.0;
	public static final double DEFAULT_HORIZON_TRANSITION_START_STICK = 0.35;
	public static final double DEFAULT_HORIZON_TRANSITION_END_STICK = 0.95;
	public static final double DEFAULT_PID_INTEGRAL_RELAX_STRENGTH = 0.70;
	public static final double DEFAULT_RC_FRAME_RATE_HERTZ = 150.0;
	public static final int DEFAULT_RC_CHANNEL_RESOLUTION_STEPS = 2048;
	public static final double DEFAULT_ESC_COMMAND_FRAME_RATE_HERTZ = 400.0;
	public static final EscCommandProtocol DEFAULT_ESC_COMMAND_PROTOCOL = EscCommandProtocol.DSHOT600;
	public static final int DEFAULT_ESC_COMMAND_RESOLUTION_STEPS = DEFAULT_ESC_COMMAND_PROTOCOL.throttleSteps();
	public static final double DEFAULT_THROTTLE_COMMAND_CURVE_EXPONENT = 1.0;
	public static final double APDRONE_NORMAL_POWER_REFERENCE_THROTTLE_COMMAND = 0.5439609800526073;
	public static final double APDRONE_MOTOR_PDF_WINDING_RESISTANCE_OHMS = 0.0586;
	public static final double LARGE_LIFT_PROP_PITCH_TO_DIAMETER_RATIO = 0.50;

	public DroneConfig {
		rotors = List.copyOf(rotors);
		if (massKg <= 0.0) {
			throw new IllegalArgumentException("massKg must be positive");
		}
		if (inertiaKgMetersSquared.x() <= 0.0 || inertiaKgMetersSquared.y() <= 0.0 || inertiaKgMetersSquared.z() <= 0.0) {
			throw new IllegalArgumentException("inertiaKgMetersSquared components must be positive");
		}
		if (centerOfMassOffsetBodyMeters == null || !centerOfMassOffsetBodyMeters.isFinite()) {
			centerOfMassOffsetBodyMeters = Vec3.ZERO;
		} else {
			centerOfMassOffsetBodyMeters = centerOfMassOffsetBodyMeters.clamp(-1.0, 1.0);
		}
		if (imuOffsetBodyMeters == null || !imuOffsetBodyMeters.isFinite()) {
			imuOffsetBodyMeters = Vec3.ZERO;
		} else {
			imuOffsetBodyMeters = imuOffsetBodyMeters.clamp(-1.0, 1.0);
		}
		if (centerOfPressureOffsetBodyMeters == null || !centerOfPressureOffsetBodyMeters.isFinite()) {
			centerOfPressureOffsetBodyMeters = Vec3.ZERO;
		} else {
			centerOfPressureOffsetBodyMeters = centerOfPressureOffsetBodyMeters.clamp(-1.0, 1.0);
		}
		if (rotors.isEmpty()) {
			throw new IllegalArgumentException("at least one rotor is required");
		}
		linearDragCoefficient = Math.max(0.0, linearDragCoefficient);
		bodyDragCoefficients = new Vec3(
				Math.max(0.0, bodyDragCoefficients.x()),
				Math.max(0.0, bodyDragCoefficients.y()),
				Math.max(0.0, bodyDragCoefficients.z())
		);
		groundEffectHeightMeters = Math.max(0.0, groundEffectHeightMeters);
		groundEffectMaxThrustBoost = MathUtil.clamp(groundEffectMaxThrustBoost, 0.0, 0.6);
		propwashStartDescentMetersPerSecond = Math.max(0.0, propwashStartDescentMetersPerSecond);
		propwashFullDescentMetersPerSecond = Math.max(propwashStartDescentMetersPerSecond + 0.1, propwashFullDescentMetersPerSecond);
		propwashMaxTorqueNewtonMeters = MathUtil.clamp(propwashMaxTorqueNewtonMeters, 0.0, 0.2);
		angularDragCoefficient = Math.max(0.0, angularDragCoefficient);
		motorTimeConstantSeconds = MathUtil.clamp(motorTimeConstantSeconds, 0.005, 0.5);
		escOutputCurveExponent = MathUtil.clamp(escOutputCurveExponent, 0.45, 2.5);
		throttleCommandCurveExponent = MathUtil.clamp(throttleCommandCurveExponent, 0.25, 8.0);
		escOutputSlewRatePerSecond = MathUtil.clamp(escOutputSlewRatePerSecond, 0.1, 1000.0);
		escOutputFallSlewRatePerSecond = MathUtil.clamp(escOutputFallSlewRatePerSecond, 0.1, 1000.0);
		escDeadband = MathUtil.clamp(escDeadband, 0.0, 0.25);
		motorActiveBrakingStrength = MathUtil.clamp(motorActiveBrakingStrength, 0.0, 1.0);
		voltageCompensationStrength = MathUtil.clamp(voltageCompensationStrength, 0.0, 1.0);
		motorThermalRiseCelsiusPerSecond = MathUtil.clamp(motorThermalRiseCelsiusPerSecond, 0.0, 250.0);
		motorCoolingRatePerSecond = MathUtil.clamp(motorCoolingRatePerSecond, 0.0, 5.0);
		motorThermalLimitCelsius = MathUtil.clamp(motorThermalLimitCelsius, 30.0, 220.0);
		motorThermalCutoffCelsius = MathUtil.clamp(motorThermalCutoffCelsius, motorThermalLimitCelsius + 1.0, 260.0);
		motorIdleThrustFraction = MathUtil.clamp(motorIdleThrustFraction, 0.0, 0.18);
		airmodeStrength = MathUtil.clamp(airmodeStrength, 0.0, 1.0);
		gyroLowPassCutoffHz = MathUtil.clamp(gyroLowPassCutoffHz, 0.0, 1000.0);
		gyroNoiseStdDevRadiansPerSecond = MathUtil.clamp(gyroNoiseStdDevRadiansPerSecond, 0.0, 5.0);
		accelerometerLowPassCutoffHz = MathUtil.clamp(accelerometerLowPassCutoffHz, 0.0, 1000.0);
		accelerometerNoiseStdDevMetersPerSecondSquared = MathUtil.clamp(accelerometerNoiseStdDevMetersPerSecondSquared, 0.0, 20.0);
		controlLatencySeconds = MathUtil.clamp(controlLatencySeconds, 0.0, 0.08);
		rcCommandSmoothingTimeConstantSeconds = MathUtil.clamp(rcCommandSmoothingTimeConstantSeconds, 0.0, 0.25);
		rcCommandLatencySeconds = MathUtil.clamp(rcCommandLatencySeconds, 0.0, 0.20);
		rcFailsafeTimeoutSeconds = MathUtil.clamp(rcFailsafeTimeoutSeconds, 0.0, 2.0);
		attitudeEstimatorAccelerometerCorrectionGain = MathUtil.clamp(attitudeEstimatorAccelerometerCorrectionGain, 0.0, 10.0);
		attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared = MathUtil.clamp(attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared, 0.1, 12.0);
		nominalBatteryVoltage = Math.max(1.0, nominalBatteryVoltage);
		emptyBatteryVoltage = MathUtil.clamp(emptyBatteryVoltage, 0.5, nominalBatteryVoltage - 0.1);
		batteryInternalResistanceOhms = Math.max(0.0, batteryInternalResistanceOhms);
		batteryCapacityAmpHours = Math.max(0.05, batteryCapacityAmpHours);
		maxBatteryCurrentAmps = Math.max(1.0, maxBatteryCurrentAmps);
		rateExpo = rateExpo.clamp(0.0, 1.0);
		rateSuper = rateSuper.clamp(0.0, 0.95);
		selfLevelMaxAngleRadians = MathUtil.clamp(selfLevelMaxAngleRadians, Math.toRadians(5.0), Math.toRadians(85.0));
		selfLevelRateGain = MathUtil.clamp(selfLevelRateGain, 0.5, 15.0);
		horizonTransitionStartStick = MathUtil.clamp(horizonTransitionStartStick, 0.0, 0.95);
		horizonTransitionEndStick = MathUtil.clamp(horizonTransitionEndStick, horizonTransitionStartStick + 0.05, 1.0);
		pidIntegralRelaxStrength = MathUtil.clamp(pidIntegralRelaxStrength, 0.0, 1.0);
		rcFrameRateHertz = MathUtil.clamp(rcFrameRateHertz, 0.0, 1000.0);
		rcChannelResolutionSteps = Math.max(0, Math.min(65535, rcChannelResolutionSteps));
		escCommandFrameRateHertz = MathUtil.clamp(escCommandFrameRateHertz, 0.0, 8000.0);
		escCommandResolutionSteps = Math.max(0, Math.min(65535, escCommandResolutionSteps));
		if (escCommandProtocol == null) {
			escCommandProtocol = DEFAULT_ESC_COMMAND_PROTOCOL;
		}
		escCommandProtocol = escCommandProtocol.normalizedForResolution(escCommandResolutionSteps);
	}

	public static DroneConfig racingQuad() {
		double arm = 0.18 / Math.sqrt(2.0);
		double maxRotorThrust = 13.5;
		double thrustCoefficient = 1.45e-6;
		double yawTorquePerThrust = 0.014;
		double rotorRadius = 0.0635;
		double transverseFlowLift = 0.08;
		double axialFlowLoss = 0.16;
		double diskDrag = 0.0028;
		double rotorInertia = RotorSpec.estimatedUniformBladePropInertiaKgMetersSquared(rotorRadius, 4.0);
		double inflowTimeConstant = 0.035;
		double inflowLag = 0.16;
		double flapping = 0.055;
		double stallLoss = 0.34;
		return new DroneConfig(
				1.1,
				new Vec3(0.012, 0.021, 0.014),
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO,
				List.of(
						new RotorSpec(new Vec3(arm, 0.0, arm), 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(-arm, 0.0, arm), -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(-arm, 0.0, -arm), 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(arm, 0.0, -arm), -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss)
				),
				9.80665,
				0.18,
				new Vec3(0.0025, 0.0020, 0.0045),
				0.6,
				0.18,
				2.2,
				7.5,
				0.035,
				0.018,
				0.045,
				1.00,
				DEFAULT_THROTTLE_COMMAND_CURVE_EXPONENT,
				160.0,
				360.0,
				0.018,
				0.55,
				0.85,
				12.0,
				0.035,
				95.0,
				125.0,
				0.055,
				1.0,
				120.0,
				0.025,
				80.0,
				0.22,
				0.015,
				16.8,
				13.2,
				0.018,
				1.5,
				90.0,
				Math.toRadians(720.0),
				Math.toRadians(520.0),
				Math.toRadians(720.0),
				new Vec3(0.35, 0.15, 0.35),
				new Vec3(0.45, 0.20, 0.45),
				new PidGains(0.045, 0.016, 0.0008, 1.8, 0.000018, 90.0, 1.70, 0.65, 0.22),
				new PidGains(0.038, 0.012, 0.0005, 1.4, 0.000010, 70.0, 1.35, 0.65, 0.18),
				new PidGains(0.045, 0.016, 0.0008, 1.8, 0.000018, 90.0, 1.70, 0.65, 0.22),
				0.018,
				0.018,
				0.35,
				1.8,
				4.0,
				DEFAULT_SELF_LEVEL_MAX_ANGLE_RADIANS,
				DEFAULT_SELF_LEVEL_RATE_GAIN,
				DEFAULT_HORIZON_TRANSITION_START_STICK,
				DEFAULT_HORIZON_TRANSITION_END_STICK,
				DEFAULT_PID_INTEGRAL_RELAX_STRENGTH,
				DEFAULT_RC_FRAME_RATE_HERTZ,
				DEFAULT_RC_CHANNEL_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_FRAME_RATE_HERTZ,
				DEFAULT_ESC_COMMAND_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_PROTOCOL).withRotorBladeCount(3);
	}

	public static DroneConfig apDrone() {
		double motorCenterRadius = 0.095;
		double arm = motorCenterRadius / Math.sqrt(2.0);
		double maxRotorThrust = 13.5;
		double thrustCoefficient = 1.45e-6;
		double yawTorquePerThrust = 0.0145;
		double rotorRadius = 5.1 * 0.0254 * 0.5;
		double rotorPitchToDiameter = 4.5 / 5.1;
		double transverseFlowLift = 0.085;
		double axialFlowLoss = 0.16;
		double diskDrag = 0.0030;
		double rotorInertia = RotorSpec.estimatedUniformBladePropInertiaKgMetersSquared(rotorRadius, 4.3);
		double inflowTimeConstant = 0.034;
		double inflowLag = 0.16;
		double flapping = 0.060;
		double stallLoss = 0.34;
		double hoverDirectThrustFraction = 0.6284 * 9.80665 / (4.0 * maxRotorThrust);
		double throttleCommandCurveExponent = Math.log(hoverDirectThrustFraction)
				/ Math.log(APDRONE_NORMAL_POWER_REFERENCE_THROTTLE_COMMAND);
		return new DroneConfig(
				0.6284,
				new Vec3(0.001346, 0.002480, 0.001410),
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO,
				List.of(
						new RotorSpec(new Vec3(arm, 0.0, arm), 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(-arm, 0.0, arm), -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(-arm, 0.0, -arm), 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(arm, 0.0, -arm), -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss)
				),
				9.80665,
				0.12,
				new Vec3(0.0018, 0.0014, 0.0034),
				0.55,
				0.18,
				2.0,
				7.3,
				0.030,
				0.012,
				0.015,
				1.00,
				throttleCommandCurveExponent,
				190.0,
				430.0,
				0.012,
				0.62,
				0.88,
				13.0,
				0.040,
				100.0,
				130.0,
				0.045,
				1.0,
				125.0,
				0.025,
				80.0,
				0.20,
				0.010,
				16.8,
				13.2,
				0.016,
				1.5,
				150.0,
				Math.toRadians(1998.0),
				Math.toRadians(1998.0),
				Math.toRadians(1998.0),
				new Vec3(0.65, 0.65, 0.65),
				new Vec3(0.90, 0.90, 0.90),
				new PidGains(0.034, 0.0109, 0.00063, 1.00, 0.000012, 125.0, 1.60, 0.65, 0.20),
				new PidGains(0.035, 0.0070, 0.00063, 0.90, 0.000009, 125.0, 1.30, 0.65, 0.18),
				new PidGains(0.016, 0.0060, 0.00042, 0.70, 0.000010, 125.0, 1.20, 0.65, 0.18),
				0.012,
				0.010,
				0.35,
				2.0,
				3.5,
				DEFAULT_SELF_LEVEL_MAX_ANGLE_RADIANS,
				DEFAULT_SELF_LEVEL_RATE_GAIN,
				DEFAULT_HORIZON_TRANSITION_START_STICK,
				DEFAULT_HORIZON_TRANSITION_END_STICK,
				DEFAULT_PID_INTEGRAL_RELAX_STRENGTH,
				DEFAULT_RC_FRAME_RATE_HERTZ,
				DEFAULT_RC_CHANNEL_RESOLUTION_STEPS,
				480.0,
				DEFAULT_ESC_COMMAND_RESOLUTION_STEPS,
				EscCommandProtocol.DSHOT600)
				.withRotorBladeCount(3)
				.withRotorBladePitchToDiameterRatio(rotorPitchToDiameter)
				.withMotorWindingResistanceOhms(APDRONE_MOTOR_PDF_WINDING_RESISTANCE_OHMS);
	}

	public static DroneConfig cinewhoop() {
		double arm = 0.13 / Math.sqrt(2.0);
		double maxRotorThrust = 8.0;
		double thrustCoefficient = 1.15e-5;
		double yawTorquePerThrust = 0.013;
		double rotorRadius = 0.038;
		double transverseFlowLift = 0.05;
		double axialFlowLoss = 0.22;
		double diskDrag = 0.0065;
		double rotorInertia = 9.0e-6;
		double inflowTimeConstant = 0.055;
		double inflowLag = 0.20;
		double flapping = 0.075;
		double stallLoss = 0.42;
		return new DroneConfig(
				0.95,
				new Vec3(0.010, 0.018, 0.012),
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO,
				List.of(
						new RotorSpec(new Vec3(arm, 0.0, arm), 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(-arm, 0.0, arm), -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(-arm, 0.0, -arm), 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(arm, 0.0, -arm), -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss)
				),
				9.80665,
				0.26,
				new Vec3(0.0090, 0.0060, 0.0140),
				0.75,
				0.24,
				1.6,
				5.0,
				0.045,
				0.035,
				0.070,
				1.08,
				DEFAULT_THROTTLE_COMMAND_CURVE_EXPONENT,
				80.0,
				150.0,
				0.022,
				0.38,
				0.65,
				13.5,
				0.030,
				90.0,
				120.0,
				0.070,
				0.75,
				90.0,
				0.018,
				65.0,
				0.20,
				0.018,
				14.8,
				12.0,
				0.035,
				1.3,
				65.0,
				Math.toRadians(430.0),
				Math.toRadians(320.0),
				Math.toRadians(430.0),
				new Vec3(0.45, 0.25, 0.45),
				new Vec3(0.25, 0.12, 0.25),
				new PidGains(0.038, 0.018, 0.0011, 1.2, 0.000014, 70.0, 1.30, 0.68, 0.16),
				new PidGains(0.030, 0.012, 0.0007, 0.9, 0.000008, 55.0, 1.05, 0.68, 0.12),
				new PidGains(0.038, 0.018, 0.0011, 1.2, 0.000014, 70.0, 1.30, 0.68, 0.16),
				0.025,
				0.025,
				0.40,
				2.2,
				3.5,
				Math.toRadians(45.0),
				5.4,
				0.30,
				0.90,
				0.65,
				100.0,
				DEFAULT_RC_CHANNEL_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_FRAME_RATE_HERTZ,
				DEFAULT_ESC_COMMAND_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_PROTOCOL).withRotorBladeCount(3);
	}

	public static DroneConfig heavyLift() {
		double arm = 0.36 / Math.sqrt(2.0);
		double maxRotorThrust = 38.0;
		double thrustCoefficient = 4.5e-5;
		double yawTorquePerThrust = 0.030;
		double rotorRadius = 0.127;
		double transverseFlowLift = 0.06;
		double axialFlowLoss = 0.20;
		double diskDrag = 0.009;
		double rotorInertia = 8.5e-5;
		double inflowTimeConstant = 0.095;
		double inflowLag = 0.18;
		double flapping = 0.045;
		double stallLoss = 0.28;
		return new DroneConfig(
				4.5,
				new Vec3(0.120, 0.220, 0.140),
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO,
				List.of(
						new RotorSpec(new Vec3(arm, 0.0, arm), 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(-arm, 0.0, arm), -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(-arm, 0.0, -arm), 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						new RotorSpec(new Vec3(arm, 0.0, -arm), -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss)
				),
				9.80665,
				0.34,
				new Vec3(0.0160, 0.0120, 0.0240),
				1.2,
				0.12,
				1.5,
				5.5,
				0.080,
				0.060,
				0.120,
				1.00,
				DEFAULT_THROTTLE_COMMAND_CURVE_EXPONENT,
				45.0,
				70.0,
				0.015,
				0.25,
				0.50,
				8.0,
				0.026,
				105.0,
				145.0,
				0.060,
				0.60,
				70.0,
				0.012,
				45.0,
				0.16,
				0.025,
				22.2,
				18.0,
				0.028,
				5.0,
				140.0,
				Math.toRadians(260.0),
				Math.toRadians(180.0),
				Math.toRadians(260.0),
				new Vec3(0.30, 0.20, 0.30),
				new Vec3(0.15, 0.08, 0.15),
				new PidGains(0.075, 0.025, 0.0020, 2.6, 0.000050, 45.0, 0.90, 0.72, 0.12),
				new PidGains(0.052, 0.016, 0.0013, 1.8, 0.000028, 38.0, 0.75, 0.72, 0.10),
				new PidGains(0.075, 0.025, 0.0020, 2.6, 0.000050, 45.0, 0.90, 0.72, 0.12),
				0.040,
				0.035,
				0.50,
				1.5,
				3.0,
				Math.toRadians(35.0),
				4.2,
				0.25,
				0.85,
				0.55,
				100.0,
				DEFAULT_RC_CHANNEL_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_FRAME_RATE_HERTZ,
				DEFAULT_ESC_COMMAND_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_PROTOCOL)
				.withRotorBladePitchToDiameterRatio(LARGE_LIFT_PROP_PITCH_TO_DIAMETER_RATIO);
	}

	public static DroneConfig hexLift() {
		double arm = 0.42;
		double maxRotorThrust = 26.0;
		double thrustCoefficient = 3.6e-5;
		double yawTorquePerThrust = 0.024;
		double rotorRadius = 0.105;
		double transverseFlowLift = 0.055;
		double axialFlowLoss = 0.19;
		double diskDrag = 0.0080;
		double rotorInertia = 6.2e-5;
		double inflowTimeConstant = 0.085;
		double inflowLag = 0.17;
		double flapping = 0.050;
		double stallLoss = 0.30;
		return new DroneConfig(
				4.2,
				new Vec3(0.135, 0.240, 0.150),
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO,
				List.of(
						rotorAtDegrees(30.0, arm, 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(90.0, arm, -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(150.0, arm, 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(210.0, arm, -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(270.0, arm, 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(330.0, arm, -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss)
				),
				9.80665,
				0.32,
				new Vec3(0.0180, 0.0130, 0.0250),
				1.05,
				0.11,
				1.4,
				5.2,
				0.070,
				0.055,
				0.095,
				1.00,
				DEFAULT_THROTTLE_COMMAND_CURVE_EXPONENT,
				55.0,
				90.0,
				0.015,
				0.30,
				0.55,
				9.0,
				0.024,
				105.0,
				145.0,
				0.055,
				0.75,
				75.0,
				0.014,
				42.0,
				0.14,
				0.022,
				22.2,
				18.0,
				0.026,
				6.0,
				160.0,
				Math.toRadians(300.0),
				Math.toRadians(210.0),
				Math.toRadians(300.0),
				new Vec3(0.28, 0.18, 0.28),
				new Vec3(0.18, 0.08, 0.18),
				new PidGains(0.068, 0.024, 0.0017, 2.4, 0.000040, 45.0, 0.85, 0.72, 0.12),
				new PidGains(0.048, 0.015, 0.0011, 1.7, 0.000024, 38.0, 0.72, 0.72, 0.10),
				new PidGains(0.068, 0.024, 0.0017, 2.4, 0.000040, 45.0, 0.85, 0.72, 0.12),
				0.040,
				0.035,
				0.55,
				1.5,
				3.0,
				Math.toRadians(38.0),
				4.0,
				0.25,
				0.85,
				0.55,
				100.0,
				DEFAULT_RC_CHANNEL_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_FRAME_RATE_HERTZ,
				DEFAULT_ESC_COMMAND_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_PROTOCOL)
				.withRotorBladePitchToDiameterRatio(LARGE_LIFT_PROP_PITCH_TO_DIAMETER_RATIO);
	}

	public static DroneConfig octoLift() {
		double arm = 0.52;
		double maxRotorThrust = 24.0;
		double thrustCoefficient = 3.2e-5;
		double yawTorquePerThrust = 0.023;
		double rotorRadius = 0.115;
		double transverseFlowLift = 0.052;
		double axialFlowLoss = 0.18;
		double diskDrag = 0.0085;
		double rotorInertia = 7.0e-5;
		double inflowTimeConstant = 0.090;
		double inflowLag = 0.18;
		double flapping = 0.045;
		double stallLoss = 0.30;
		return new DroneConfig(
				6.8,
				new Vec3(0.220, 0.390, 0.235),
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO,
				List.of(
						rotorAtDegrees(22.5, arm, 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(67.5, arm, -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(112.5, arm, 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(157.5, arm, -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(202.5, arm, 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(247.5, arm, -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(292.5, arm, 1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss),
						rotorAtDegrees(337.5, arm, -1, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss)
				),
				9.80665,
				0.46,
				new Vec3(0.0250, 0.0180, 0.0340),
				1.15,
				0.10,
				1.2,
				4.8,
				0.080,
				0.070,
				0.105,
				1.00,
				DEFAULT_THROTTLE_COMMAND_CURVE_EXPONENT,
				50.0,
				85.0,
				0.015,
				0.28,
				0.50,
				8.0,
				0.026,
				105.0,
				145.0,
				0.055,
				0.70,
				70.0,
				0.014,
				38.0,
				0.14,
				0.024,
				29.6,
				24.0,
				0.030,
				8.0,
				220.0,
				Math.toRadians(240.0),
				Math.toRadians(170.0),
				Math.toRadians(240.0),
				new Vec3(0.26, 0.17, 0.26),
				new Vec3(0.14, 0.07, 0.14),
				new PidGains(0.060, 0.022, 0.0016, 2.3, 0.000034, 42.0, 0.78, 0.72, 0.10),
				new PidGains(0.044, 0.014, 0.0011, 1.6, 0.000022, 36.0, 0.68, 0.72, 0.09),
				new PidGains(0.060, 0.022, 0.0016, 2.3, 0.000034, 42.0, 0.78, 0.72, 0.10),
				0.045,
				0.040,
				0.55,
				1.4,
				3.0,
				Math.toRadians(35.0),
				3.8,
				0.25,
				0.85,
				0.55,
				100.0,
				DEFAULT_RC_CHANNEL_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_FRAME_RATE_HERTZ,
				DEFAULT_ESC_COMMAND_RESOLUTION_STEPS,
				DEFAULT_ESC_COMMAND_PROTOCOL)
				.withRotorBladePitchToDiameterRatio(LARGE_LIFT_PROP_PITCH_TO_DIAMETER_RATIO);
	}

	public static DroneConfig coaxialX8() {
		DroneConfig base = octoLift()
				.withMassKg(7.2)
				.withInertiaKgMetersSquared(new Vec3(0.245, 0.430, 0.255))
				.withBodyDragCoefficients(new Vec3(0.0300, 0.0220, 0.0400))
				.withAngularDragCoefficient(1.25)
				.withRates(Math.toRadians(220.0), Math.toRadians(155.0), Math.toRadians(220.0));
		RotorSpec rotor = base.rotors().get(0);
		double arm = 0.42;
		double verticalOffset = rotor.radiusMeters() * 0.72;
		return base.withRotors(List.of(
				rotorLike(rotor, rotorPositionAtDegrees(45.0, arm, verticalOffset), 1),
				rotorLike(rotor, rotorPositionAtDegrees(45.0, arm, -verticalOffset), -1),
				rotorLike(rotor, rotorPositionAtDegrees(135.0, arm, verticalOffset), -1),
				rotorLike(rotor, rotorPositionAtDegrees(135.0, arm, -verticalOffset), 1),
				rotorLike(rotor, rotorPositionAtDegrees(225.0, arm, verticalOffset), 1),
				rotorLike(rotor, rotorPositionAtDegrees(225.0, arm, -verticalOffset), -1),
				rotorLike(rotor, rotorPositionAtDegrees(315.0, arm, verticalOffset), -1),
				rotorLike(rotor, rotorPositionAtDegrees(315.0, arm, -verticalOffset), 1)
		));
	}

	private static RotorSpec rotorAtDegrees(
			double angleDegrees,
			double armMeters,
			int spinDirection,
			double maxRotorThrust,
			double thrustCoefficient,
			double yawTorquePerThrust,
			double rotorRadius,
			double transverseFlowLift,
			double axialFlowLoss,
			double diskDrag,
			double rotorInertia,
			double inflowTimeConstant,
			double inflowLag,
			double flapping,
			double stallLoss
	) {
		Vec3 position = rotorPositionAtDegrees(angleDegrees, armMeters, 0.0);
		return new RotorSpec(position, spinDirection, maxRotorThrust, thrustCoefficient, yawTorquePerThrust, rotorRadius, transverseFlowLift, axialFlowLoss, diskDrag, rotorInertia, inflowTimeConstant, inflowLag, flapping, stallLoss);
	}

	private static Vec3 rotorPositionAtDegrees(double angleDegrees, double armMeters, double verticalOffsetMeters) {
		double angleRadians = Math.toRadians(angleDegrees);
		return new Vec3(
				Math.sin(angleRadians) * armMeters,
				verticalOffsetMeters,
				Math.cos(angleRadians) * armMeters
		);
	}

	private static RotorSpec rotorLike(RotorSpec template, Vec3 positionBodyMeters, int spinDirection) {
		return new RotorSpec(
				positionBodyMeters,
				template.thrustAxisBody(),
				spinDirection,
				template.maxThrustNewtons(),
				template.thrustCoefficient(),
				template.yawTorquePerThrustMeter(),
				template.radiusMeters(),
				template.bladePitchMeters(),
				template.transverseFlowLiftCoefficient(),
				template.axialFlowThrustLossCoefficient(),
				template.diskDragCoefficient(),
				template.rotorInertiaKgMetersSquared(),
				template.inducedInflowTimeConstantSeconds(),
				template.inducedInflowLagCoefficient(),
				template.flappingCoefficient(),
				template.stallThrustLossCoefficient(),
				template.imbalanceIntensity(),
				template.bladeCount()
		);
	}

	public double totalMaxThrustNewtons() {
		return rotors.stream().mapToDouble(RotorSpec::maxThrustNewtons).sum();
	}

	public double totalMaxVerticalThrustNewtons() {
		return rotors.stream()
				.mapToDouble(rotor -> rotor.maxThrustNewtons() * Math.max(0.0, rotor.thrustAxisBody().y()))
				.sum();
	}

	public double hoverDirectThrustFraction() {
		return massKg * gravityMetersPerSecondSquared / Math.max(1.0e-6, totalMaxVerticalThrustNewtons());
	}

	public double hoverThrottle() {
		return directThrustFractionToThrottleCommand(hoverDirectThrustFraction());
	}

	public double throttleCommandToDirectThrustFraction(double throttleCommand) {
		double clamped = MathUtil.clamp(throttleCommand, 0.0, 1.0);
		return MathUtil.clamp(Math.pow(clamped, throttleCommandCurveExponent), 0.0, 1.0);
	}

	public double directThrustFractionToThrottleCommand(double directThrustFraction) {
		double clamped = MathUtil.clamp(directThrustFraction, 0.0, 1.0);
		double exponent = Math.max(0.25, throttleCommandCurveExponent);
		return MathUtil.clamp(Math.pow(clamped, 1.0 / exponent), 0.0, 1.0);
	}

	public double averageRotorOutwardCantDegrees() {
		double sum = 0.0;
		int count = 0;
		for (RotorSpec rotor : rotors) {
			Vec3 radial = new Vec3(rotor.positionBodyMeters().x(), 0.0, rotor.positionBodyMeters().z());
			if (radial.lengthSquared() <= 1.0e-12) {
				continue;
			}
			double outward = rotor.thrustAxisBody().dot(radial.normalized());
			double vertical = Math.max(1.0e-6, rotor.thrustAxisBody().y());
			sum += Math.toDegrees(Math.atan2(outward, vertical));
			count++;
		}
		return count == 0 ? 0.0 : sum / count;
	}

	public double averageRotorImbalanceIntensity() {
		return rotors.stream()
				.mapToDouble(RotorSpec::imbalanceIntensity)
				.average()
				.orElse(0.0);
	}

	public DroneConfig withMassKg(double massKg) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withInertiaKgMetersSquared(Vec3 inertiaKgMetersSquared) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withCenterOfMassOffsetBodyMeters(Vec3 centerOfMassOffsetBodyMeters) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withImuOffsetBodyMeters(Vec3 imuOffsetBodyMeters) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withCenterOfPressureOffsetBodyMeters(Vec3 centerOfPressureOffsetBodyMeters) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withAngularDragCoefficient(double angularDragCoefficient) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withMotorTimeConstantSeconds(double motorTimeConstantSeconds) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withThrottleCommandCurveExponent(double throttleCommandCurveExponent) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withEscMotorResponse(
			double escOutputCurveExponent,
			double escOutputSlewRatePerSecond,
			double voltageCompensationStrength
	) {
		return withEscMotorResponse(
				escOutputCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				voltageCompensationStrength,
				motorActiveBrakingStrength
		);
	}

	public DroneConfig withEscMotorResponse(
			double escOutputCurveExponent,
			double escOutputSlewRatePerSecond,
			double escOutputFallSlewRatePerSecond,
			double escDeadband,
			double voltageCompensationStrength,
			double motorActiveBrakingStrength
	) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withMotorThermal(
			double thermalRiseCelsiusPerSecond,
			double coolingRatePerSecond,
			double thermalLimitCelsius,
			double thermalCutoffCelsius
	) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				thermalRiseCelsiusPerSecond,
				coolingRatePerSecond,
				thermalLimitCelsius,
				thermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withFlightControllerSensors(
			double gyroLowPassCutoffHz,
			double gyroNoiseStdDevRadiansPerSecond,
			double controlLatencySeconds
	) {
		return withFlightControllerSensors(
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds
		);
	}

	public DroneConfig withFlightControllerSensors(
			double gyroLowPassCutoffHz,
			double gyroNoiseStdDevRadiansPerSecond,
			double accelerometerLowPassCutoffHz,
			double accelerometerNoiseStdDevMetersPerSecondSquared,
			double controlLatencySeconds
	) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withControlLink(
			double rcCommandSmoothingTimeConstantSeconds,
			double rcCommandLatencySeconds,
			double rcFailsafeTimeoutSeconds
	) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withControlReceiver(double rcFrameRateHertz, double rcChannelResolutionSteps) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				(int) Math.round(rcChannelResolutionSteps),
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withEscCommandSignal(double escCommandFrameRateHertz, double escCommandResolutionSteps) {
		int roundedResolutionSteps = (int) Math.round(escCommandResolutionSteps);
		return withEscCommandSignalAndProtocol(
				escCommandFrameRateHertz,
				roundedResolutionSteps,
				escCommandProtocol.normalizedForResolution(roundedResolutionSteps)
		);
	}

	public DroneConfig withEscCommandProtocolBitrate(double bitrateKilobitsPerSecond) {
		return withEscCommandProtocol(EscCommandProtocol.fromBitrateKilobitsPerSecond(bitrateKilobitsPerSecond));
	}

	public DroneConfig withEscCommandProtocol(EscCommandProtocol protocol) {
		EscCommandProtocol selectedProtocol = protocol == null ? EscCommandProtocol.GENERIC : protocol;
		int resolutionSteps = selectedProtocol.digital() ? selectedProtocol.throttleSteps() : escCommandResolutionSteps;
		return withEscCommandSignalAndProtocol(escCommandFrameRateHertz, resolutionSteps, selectedProtocol);
	}

	public DroneConfig withEscCommandSignal(EscCommandProtocol protocol, double escCommandFrameRateHertz) {
		EscCommandProtocol selectedProtocol = protocol == null ? EscCommandProtocol.GENERIC : protocol;
		int resolutionSteps = selectedProtocol.digital() ? selectedProtocol.throttleSteps() : escCommandResolutionSteps;
		return withEscCommandSignalAndProtocol(escCommandFrameRateHertz, resolutionSteps, selectedProtocol);
	}

	private DroneConfig withEscCommandSignalAndProtocol(
			double escCommandFrameRateHertz,
			int escCommandResolutionSteps,
			EscCommandProtocol escCommandProtocol
	) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withAttitudeEstimator(
			double attitudeEstimatorAccelerometerCorrectionGain,
			double attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared
	) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withBattery(double nominalVoltage, double emptyVoltage, double internalResistanceOhms, double capacityAmpHours, double maxCurrentAmps) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalVoltage,
				emptyVoltage,
				internalResistanceOhms,
				capacityAmpHours,
				maxCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withRotorMaxThrustNewtons(double maxThrustNewtons) {
		return withRotors(rotors.stream()
				.map(rotor -> rotor.withMaxThrustNewtons(maxThrustNewtons))
				.toList());
	}

	public DroneConfig withRotorThrustCoefficient(double thrustCoefficient) {
		return withRotors(rotors.stream()
				.map(rotor -> rotor.withThrustCoefficient(thrustCoefficient))
				.toList());
	}

	public DroneConfig withRotorRadiusMeters(double radiusMeters) {
		return withRotors(rotors.stream()
				.map(rotor -> rotor.withRadiusMeters(radiusMeters))
				.toList());
	}

	public DroneConfig withRotorBladePitchMeters(double bladePitchMeters) {
		return withRotors(rotors.stream()
				.map(rotor -> rotor.withBladePitchMeters(bladePitchMeters))
				.toList());
	}

	public DroneConfig withRotorBladePitchToDiameterRatio(double pitchToDiameterRatio) {
		return withRotors(rotors.stream()
				.map(rotor -> rotor.withBladePitchToDiameterRatio(pitchToDiameterRatio))
				.toList());
	}

	public DroneConfig withRotorBladeCount(int bladeCount) {
		return withRotors(rotors.stream()
				.map(rotor -> rotor.withBladeCount(bladeCount))
				.toList());
	}

	public DroneConfig withMotorWindingResistanceOhms(double motorWindingResistanceOhms) {
		return withRotors(rotors.stream()
				.map(rotor -> rotor.withMotorWindingResistanceOhms(motorWindingResistanceOhms))
				.toList());
	}

	public DroneConfig withRotorTransverseFlowLiftCoefficient(double transverseFlowLiftCoefficient) {
		return withRotors(rotors.stream()
				.map(rotor -> rotor.withTransverseFlowLiftCoefficient(transverseFlowLiftCoefficient))
				.toList());
	}

	public DroneConfig withRotorAxialFlowThrustLossCoefficient(double axialFlowThrustLossCoefficient) {
		return withRotors(rotors.stream()
				.map(rotor -> rotor.withAxialFlowThrustLossCoefficient(axialFlowThrustLossCoefficient))
				.toList());
	}

	public DroneConfig withLinearDragCoefficient(double linearDragCoefficient) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withBodyDragCoefficients(Vec3 bodyDragCoefficients) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withRotorDiskDragCoefficient(double diskDragCoefficient) {
		List<RotorSpec> updatedRotors = rotors.stream()
				.map(rotor -> rotor.withDiskDragCoefficient(diskDragCoefficient))
				.toList();
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				updatedRotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withRotorInertiaKgMetersSquared(double rotorInertiaKgMetersSquared) {
		List<RotorSpec> updatedRotors = rotors.stream()
				.map(rotor -> rotor.withRotorInertiaKgMetersSquared(rotorInertiaKgMetersSquared))
				.toList();
		return withRotors(updatedRotors);
	}

	public DroneConfig withRotorInducedInflow(double inducedInflowTimeConstantSeconds, double inducedInflowLagCoefficient) {
		List<RotorSpec> updatedRotors = rotors.stream()
				.map(rotor -> rotor.withInducedInflow(inducedInflowTimeConstantSeconds, inducedInflowLagCoefficient))
				.toList();
		return withRotors(updatedRotors);
	}

	public DroneConfig withRotorFlappingCoefficient(double flappingCoefficient) {
		List<RotorSpec> updatedRotors = rotors.stream()
				.map(rotor -> rotor.withFlappingCoefficient(flappingCoefficient))
				.toList();
		return withRotors(updatedRotors);
	}

	public DroneConfig withRotorStallThrustLossCoefficient(double stallThrustLossCoefficient) {
		List<RotorSpec> updatedRotors = rotors.stream()
				.map(rotor -> rotor.withStallThrustLossCoefficient(stallThrustLossCoefficient))
				.toList();
		return withRotors(updatedRotors);
	}

	public DroneConfig withRotorImbalanceIntensity(double imbalanceIntensity) {
		List<RotorSpec> updatedRotors = rotors.stream()
				.map(rotor -> rotor.withImbalanceIntensity(imbalanceIntensity))
				.toList();
		return withRotors(updatedRotors);
	}

	public DroneConfig withRotorYawTorquePerThrustMeter(double yawTorquePerThrustMeter) {
		List<RotorSpec> updatedRotors = rotors.stream()
				.map(rotor -> rotor.withYawTorquePerThrustMeter(yawTorquePerThrustMeter))
				.toList();
		return withRotors(updatedRotors);
	}

	public DroneConfig withRotorOutwardCantDegrees(double outwardCantDegrees) {
		double cantRadians = Math.toRadians(MathUtil.clamp(outwardCantDegrees, -35.0, 35.0));
		double horizontal = Math.sin(cantRadians);
		double vertical = Math.cos(cantRadians);
		List<RotorSpec> updatedRotors = rotors.stream()
				.map(rotor -> rotor.withThrustAxisBody(outwardCantAxis(rotor.positionBodyMeters(), horizontal, vertical)))
				.toList();
		return withRotors(updatedRotors);
	}

	private static Vec3 outwardCantAxis(Vec3 positionBodyMeters, double horizontal, double vertical) {
		Vec3 radial = new Vec3(positionBodyMeters.x(), 0.0, positionBodyMeters.z());
		if (radial.lengthSquared() <= 1.0e-12) {
			return new Vec3(0.0, vertical, 0.0).normalized();
		}
		Vec3 radialUnit = radial.normalized();
		return new Vec3(
				radialUnit.x() * horizontal,
				vertical,
				radialUnit.z() * horizontal
		).normalized();
	}

	public DroneConfig withGroundEffect(double heightMeters, double maxThrustBoost) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				heightMeters,
				maxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withPropwash(double startDescentMetersPerSecond, double fullDescentMetersPerSecond, double maxTorqueNewtonMeters) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				startDescentMetersPerSecond,
				fullDescentMetersPerSecond,
				maxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withMotorIdleAndAirmode(double motorIdleThrustFraction, double airmodeStrength) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withRates(double pitchRadiansPerSecond, double yawRadiansPerSecond, double rollRadiansPerSecond) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				pitchRadiansPerSecond,
				yawRadiansPerSecond,
				rollRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withRateExpo(Vec3 rateExpo) {
		return withRateProfile(rateExpo, rateSuper);
	}

	public DroneConfig withRateSuper(Vec3 rateSuper) {
		return withRateProfile(rateExpo, rateSuper);
	}

	public DroneConfig withRateProfile(Vec3 rateExpo, Vec3 rateSuper) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withSelfLevel(
			double maxAngleRadians,
			double rateGain,
			double horizonTransitionStartStick,
			double horizonTransitionEndStick
	) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				maxAngleRadians,
				rateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withPidIntegralRelaxStrength(double pidIntegralRelaxStrength) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol
		);
	}

	public DroneConfig withPitchGains(PidGains pitchGains) {
		return withGains(pitchGains, yawGains, rollGains);
	}

	public DroneConfig withYawGains(PidGains yawGains) {
		return withGains(pitchGains, yawGains, rollGains);
	}

	public DroneConfig withRollGains(PidGains rollGains) {
		return withGains(pitchGains, yawGains, rollGains);
	}

	private DroneConfig withGains(PidGains pitchGains, PidGains yawGains, PidGains rollGains) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}

	public DroneConfig withRotors(List<RotorSpec> rotors) {
		return new DroneConfig(
				massKg,
				inertiaKgMetersSquared,
				centerOfMassOffsetBodyMeters,
				imuOffsetBodyMeters,
				centerOfPressureOffsetBodyMeters,
				rotors,
				gravityMetersPerSecondSquared,
				linearDragCoefficient,
				bodyDragCoefficients,
				groundEffectHeightMeters,
				groundEffectMaxThrustBoost,
				propwashStartDescentMetersPerSecond,
				propwashFullDescentMetersPerSecond,
				propwashMaxTorqueNewtonMeters,
				angularDragCoefficient,
				motorTimeConstantSeconds,
				escOutputCurveExponent,
				throttleCommandCurveExponent,
				escOutputSlewRatePerSecond,
				escOutputFallSlewRatePerSecond,
				escDeadband,
				motorActiveBrakingStrength,
				voltageCompensationStrength,
				motorThermalRiseCelsiusPerSecond,
				motorCoolingRatePerSecond,
				motorThermalLimitCelsius,
				motorThermalCutoffCelsius,
				motorIdleThrustFraction,
				airmodeStrength,
				gyroLowPassCutoffHz,
				gyroNoiseStdDevRadiansPerSecond,
				accelerometerLowPassCutoffHz,
				accelerometerNoiseStdDevMetersPerSecondSquared,
				controlLatencySeconds,
				nominalBatteryVoltage,
				emptyBatteryVoltage,
				batteryInternalResistanceOhms,
				batteryCapacityAmpHours,
				maxBatteryCurrentAmps,
				maxPitchRateRadiansPerSecond,
				maxYawRateRadiansPerSecond,
				maxRollRateRadiansPerSecond,
				rateExpo,
				rateSuper,
				pitchGains,
				yawGains,
				rollGains,
				rcCommandSmoothingTimeConstantSeconds,
				rcCommandLatencySeconds,
				rcFailsafeTimeoutSeconds,
				attitudeEstimatorAccelerometerCorrectionGain,
				attitudeEstimatorAccelerometerTrustMarginMetersPerSecondSquared,
				selfLevelMaxAngleRadians,
				selfLevelRateGain,
				horizonTransitionStartStick,
				horizonTransitionEndStick,
				pidIntegralRelaxStrength,
				rcFrameRateHertz,
				rcChannelResolutionSteps,
				escCommandFrameRateHertz,
				escCommandResolutionSteps,
				escCommandProtocol);
	}
}
