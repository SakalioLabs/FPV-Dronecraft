package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.tools.OfflineFlightRecorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DronePhysicsTest {
	@Test
	void racingQuadDefaultsFavorPlayableSmallStickControl() {
		DroneConfig config = DroneConfig.racingQuad();
		double pitchCenterSensitivity = Math.toDegrees(config.maxPitchRateRadiansPerSecond())
				* (1.0 - config.rateExpo().x())
				* (1.0 - config.rateSuper().x());
		double yawCenterSensitivity = Math.toDegrees(config.maxYawRateRadiansPerSecond())
				* (1.0 - config.rateExpo().y())
				* (1.0 - config.rateSuper().y());
		double rollCenterSensitivity = Math.toDegrees(config.maxRollRateRadiansPerSecond())
				* (1.0 - config.rateExpo().z())
				* (1.0 - config.rateSuper().z());

		assertEquals(480.0, Math.toDegrees(config.maxPitchRateRadiansPerSecond()), 1.0e-12);
		assertEquals(360.0, Math.toDegrees(config.maxYawRateRadiansPerSecond()), 1.0e-12);
		assertEquals(480.0, Math.toDegrees(config.maxRollRateRadiansPerSecond()), 1.0e-12);
		assertTrue(pitchCenterSensitivity <= 165.0);
		assertTrue(yawCenterSensitivity <= 170.0);
		assertTrue(rollCenterSensitivity <= 165.0);
		assertEquals(DroneConfig.DEFAULT_THROTTLE_COMMAND_CURVE_EXPONENT, config.throttleCommandCurveExponent(), 1.0e-12);
		assertTrue(config.hoverThrottle() > 0.18 && config.hoverThrottle() < 0.22, () -> "hoverThrottle=" + config.hoverThrottle());
		assertEquals(config.hoverThrottle(), ControlStickProfile.gamepadThrottle(0.50), 0.005);
		assertTrue(ControlStickProfile.gamepadThrottle(0.45) < config.hoverThrottle());
		assertTrue(ControlStickProfile.gamepadThrottle(0.60) > config.hoverThrottle() + 0.05);
		assertEquals(0.055, config.motorIdleThrustFraction(), 1.0e-12);
		assertEquals(Math.toRadians(32.0), config.selfLevelMaxAngleRadians(), 1.0e-12);
		assertEquals(5.0, config.selfLevelRateGain(), 1.0e-12);
		assertEquals(0.85, config.horizonTransitionEndStick(), 1.0e-12);
		assertEquals(0.035, config.rcCommandSmoothingTimeConstantSeconds(), 1.0e-12);
		assertEquals(0.020, config.rcCommandLatencySeconds(), 1.0e-12);
	}

	@Test
	void hoverThrottleHoldsAltitudeApproximately() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics physics = new DronePhysics(config);
		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		DroneInput input = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 800; i++) {
			physics.step(input, 0.0025);
		}
		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		physics.state().setVelocityMetersPerSecond(Vec3.ZERO);

		for (int i = 0; i < 800; i++) {
			physics.step(input, 0.0025);
		}

		assertEquals(20.0, physics.state().positionMeters().y(), 1.2);
		assertEquals(0.0, physics.state().velocityMetersPerSecond().y(), 1.4);
	}

	@Test
	void sleepAtRestZerosMotionAndMotors() {
		DronePhysics physics = new DronePhysics(DroneConfig.racingQuad());
		physics.state().setPositionMeters(new Vec3(1.0, 4.0, -2.0));
		physics.state().setVelocityMetersPerSecond(new Vec3(2.0, -3.0, 1.0));
		physics.state().setAngularVelocityBodyRadiansPerSecond(new Vec3(0.5, -0.3, 0.2));
		for (int i = 0; i < 120; i++) {
			physics.step(new DroneInput(0.65, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON), 0.005);
		}
		assertTrue(physics.state().averageMotorRpm() > 1000.0);

		DroneInput rawDisarmed = new DroneInput(0.37, 0.12, -0.09, 0.04, false, true, FlightMode.HORIZON);
		DroneInput safeProcessed = new DroneInput(0.0, 0.0, 0.0, 0.0, false, true, FlightMode.HORIZON);

		physics.sleepAtRest(new Vec3(1.0, 0.72, -2.0), rawDisarmed);

		assertEquals(new Vec3(1.0, 0.72, -2.0), physics.state().positionMeters());
		assertEquals(0.0, physics.state().velocityMetersPerSecond().length(), 1.0e-12);
		assertEquals(0.0, physics.state().angularVelocityBodyRadiansPerSecond().length(), 1.0e-12);
		assertEquals(0.0, physics.state().averageMotorRpm(), 1.0e-12);
		assertEquals(0.0, physics.state().averageMotorPower(physics.config()), 1.0e-12);
		assertEquals(rawDisarmed.normalized(), physics.state().rawControlInput());
		assertEquals(safeProcessed.normalized(), physics.state().processedControlInput());
		assertTrue(physics.state().processedControlInput().linkActive());
		assertTrue(!physics.state().processedControlInput().armed());
		assertTrue(!physics.state().controlFailsafeActive());
		assertEquals(0.0, physics.state().controlLinkLossSeconds(), 1.0e-12);
		assertEquals(0.37, physics.state().controlFrameError(), 1.0e-12);
	}

	@Test
	void restoreDirectFlightTelemetryFeedsBlackboxState() {
		DronePhysics physics = new DronePhysics(DroneConfig.racingQuad());
		DroneInput input = new DroneInput(0.42, 0.10, -0.20, 0.05, true, true, FlightMode.HORIZON);
		double[] motorPower = {0.28, 0.30, 0.32, 0.34};
		double[] motorRpm = {5200.0, 5400.0, 5600.0, 5800.0};
		double[] rotorThrust = {0.42, 0.46, 0.50, 0.54};

		physics.restoreDirectFlightTelemetry(input, motorPower, motorRpm, rotorThrust);

		assertEquals(input.normalized(), physics.state().rawControlInput());
		assertEquals(input.normalized(), physics.state().processedControlInput());
		assertEquals(5500.0, physics.state().averageMotorRpm(), 1.0e-9);
		assertEquals(5500.0, physics.state().averageMotorRpmTelemetryRpm(), 1.0e-9);
		assertEquals(1.0, physics.state().averageMotorRpmTelemetryValidity(), 1.0e-12);
		assertEquals(0.50, physics.state().rotorThrustNewtons(2), 1.0e-12);
		assertEquals(0.32, physics.state().escOutputCommand(2), 1.0e-12);
	}

	@Test
	void clearDirectFlightTelemetryZerosMotorsAndKeepsControlContext() {
		DronePhysics physics = new DronePhysics(DroneConfig.racingQuad());
		DroneInput armed = new DroneInput(0.50, 0.12, -0.18, 0.07, true, true, FlightMode.HORIZON);
		double[] motorPower = {0.35, 0.36, 0.37, 0.38};
		double[] motorRpm = {6100.0, 6200.0, 6300.0, 6400.0};
		double[] rotorThrust = {0.60, 0.62, 0.64, 0.66};
		physics.restoreDirectFlightTelemetry(armed, motorPower, motorRpm, rotorThrust);
		assertTrue(physics.state().averageMotorRpm() > 6000.0);
		assertTrue(physics.state().averageMotorRpmTelemetryValidity() > 0.9);

		DroneInput disarmed = new DroneInput(0.25, 0.03, -0.02, 0.01, false, true, FlightMode.ANGLE);
		physics.clearDirectFlightTelemetry(disarmed);

		assertEquals(disarmed.normalized(), physics.state().rawControlInput());
		assertEquals(disarmed.normalized(), physics.state().processedControlInput());
		assertEquals(0.0, physics.state().averageMotorRpm(), 1.0e-12);
		assertEquals(0.0, physics.state().averageMotorRpmTelemetryRpm(), 1.0e-12);
		assertEquals(0.0, physics.state().averageMotorRpmTelemetryValidity(), 1.0e-12);
		assertEquals(0.0, physics.state().averageEscOutputCommand(), 1.0e-12);
		assertEquals(0.0, physics.state().averageMotorCurrentAmps(), 1.0e-12);
		assertEquals(0.0, physics.state().rotorThrustNewtons(2), 1.0e-12);
		assertTrue(!physics.state().controlFailsafeActive());
	}

	@Test
	void constrainAtRestKeepsArmedMotorSpool() {
		DronePhysics physics = new DronePhysics(DroneConfig.racingQuad());
		DroneInput spool = new DroneInput(0.32, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);
		for (int i = 0; i < 160; i++) {
			physics.step(spool, 0.005);
		}
		double rpmBeforeConstraint = physics.state().averageMotorRpm();
		assertTrue(rpmBeforeConstraint > 1000.0);

		physics.constrainAtRest(new Vec3(0.0, 0.175, 0.0));

		assertEquals(new Vec3(0.0, 0.175, 0.0), physics.state().positionMeters());
		assertEquals(0.0, physics.state().velocityMetersPerSecond().length(), 1.0e-12);
		assertEquals(0.0, physics.state().angularVelocityBodyRadiansPerSecond().length(), 1.0e-12);
		assertEquals(rpmBeforeConstraint, physics.state().averageMotorRpm(), 1.0e-12);
		assertTrue(physics.state().processedControlInput().armed());
		assertTrue(physics.state().processedControlInput().linkActive());
	}

	@Test
	void levelAtRestKeepsMotorSpoolAndLevelsAttitude() {
		DronePhysics physics = new DronePhysics(DroneConfig.racingQuad());
		DroneInput spool = new DroneInput(0.34, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);
		for (int i = 0; i < 160; i++) {
			physics.step(spool, 0.005);
		}
		double rpmBeforeConstraint = physics.state().averageMotorRpm();
		physics.state().setOrientation(new Quaternion(0.5, 0.5, 0.5, 0.5).normalized());

		physics.levelAtRest(new Vec3(0.0, 0.175, 0.0));

		assertEquals(new Vec3(0.0, 0.175, 0.0), physics.state().positionMeters());
		assertEquals(Quaternion.IDENTITY, physics.state().orientation());
		assertEquals(Quaternion.IDENTITY, physics.state().estimatedOrientation());
		assertEquals(rpmBeforeConstraint, physics.state().averageMotorRpm(), 1.0e-12);
		assertTrue(physics.state().processedControlInput().armed());
	}

	@Test
	void playableTakeoffThrottleClimbsWithRuntimeActuators() {
		DronePhysics physics = new DronePhysics(DroneConfig.racingQuad());
		physics.state().setPositionMeters(new Vec3(0.0, 3.0, 0.0));
		DroneInput takeoff = new DroneInput(0.55, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);

		for (int i = 0; i < 400; i++) {
			physics.step(takeoff, 0.005);
		}

		assertTrue(physics.state().positionMeters().y() > 8.0, () -> "position=" + physics.state().positionMeters());
		assertTrue(physics.state().velocityMetersPerSecond().y() > 4.0, () -> "velocity=" + physics.state().velocityMetersPerSecond());
		assertTrue(physics.state().averageMotorRpm() > 12_000.0, () -> "rpm=" + physics.state().averageMotorRpm());
		assertTrue(physics.state().processedControlInput().armed());
	}

	@Test
	void zeroStickHorizonTakeoffRemainsLevel() {
		DronePhysics physics = new DronePhysics(DroneConfig.racingQuad());
		physics.state().setPositionMeters(new Vec3(0.0, 3.0, 0.0));
		DroneInput takeoff = new DroneInput(0.55, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);

		for (int i = 0; i < 400; i++) {
			physics.step(takeoff, 0.005, DroneEnvironment.calm());
		}

		Vec3 eulerDegrees = physics.state().orientation().toEulerXYZRadians().multiply(180.0 / Math.PI);
		Vec3 angularRateDegrees = physics.state().angularVelocityBodyRadiansPerSecond().multiply(180.0 / Math.PI);
		assertEquals(0.0, eulerDegrees.x(), 2.0, () -> "euler=" + eulerDegrees);
		assertEquals(0.0, eulerDegrees.z(), 2.0, () -> "euler=" + eulerDegrees);
		assertEquals(0.0, angularRateDegrees.x(), 8.0, () -> "omega=" + angularRateDegrees);
		assertEquals(0.0, angularRateDegrees.y(), 8.0, () -> "omega=" + angularRateDegrees);
		assertEquals(0.0, angularRateDegrees.z(), 8.0, () -> "omega=" + angularRateDegrees);
		assertTrue(physics.state().positionMeters().y() > 8.0, () -> "position=" + physics.state().positionMeters());
	}

	@Test
	void clientLikeHorizonInputsStayRecoverable() {
		DroneConfig config = DroneConfig.racingQuad();
		DronePhysics physics = new DronePhysics(config);
		physics.state().setPositionMeters(new Vec3(0.0, 3.0, 0.0));
		double maxPitchDegrees = 0.0;
		double maxRollDegrees = 0.0;
		double maxPitchRateDegrees = 0.0;
		double maxRollRateDegrees = 0.0;

		for (int i = 0; i < 1100; i++) {
			double seconds = i * 0.005;
			double throttle = seconds < 0.70
					? config.hoverThrottle() + 0.10
					: config.hoverThrottle() + 0.025;
			double rawRoll = seconds >= 1.00 && seconds < 2.00
					? 0.45
					: (seconds >= 2.00 && seconds < 2.45 ? -0.35 : 0.0);
			double rawPitch = seconds >= 2.80 && seconds < 3.55 ? -0.38 : 0.0;
			double rawYaw = seconds >= 3.70 && seconds < 4.25 ? 0.35 : 0.0;
			DroneInput input = new DroneInput(
					throttle,
					ControlStickProfile.gamepadCommand(rawPitch, 0.10),
					ControlStickProfile.gamepadCommand(rawRoll, 0.10),
					ControlStickProfile.gamepadCommand(rawYaw, 0.10),
					true,
					true,
					FlightMode.HORIZON
			);

			physics.step(input, 0.005, DroneEnvironment.calm());
			Vec3 eulerDegrees = physics.state().orientation().toEulerXYZRadians().multiply(180.0 / Math.PI);
			Vec3 angularRateDegrees = physics.state().angularVelocityBodyRadiansPerSecond().multiply(180.0 / Math.PI);
			maxPitchDegrees = Math.max(maxPitchDegrees, Math.abs(eulerDegrees.x()));
			maxRollDegrees = Math.max(maxRollDegrees, Math.abs(eulerDegrees.z()));
			maxPitchRateDegrees = Math.max(maxPitchRateDegrees, Math.abs(angularRateDegrees.x()));
			maxRollRateDegrees = Math.max(maxRollRateDegrees, Math.abs(angularRateDegrees.z()));
		}

		Vec3 finalEulerDegrees = physics.state().orientation().toEulerXYZRadians().multiply(180.0 / Math.PI);
		double observedMaxPitchDegrees = maxPitchDegrees;
		double observedMaxRollDegrees = maxRollDegrees;
		double observedMaxPitchRateDegrees = maxPitchRateDegrees;
		double observedMaxRollRateDegrees = maxRollRateDegrees;
		assertTrue(observedMaxPitchDegrees < 12.0, () -> "maxPitch=" + observedMaxPitchDegrees);
		assertTrue(observedMaxRollDegrees < 12.5, () -> "maxRoll=" + observedMaxRollDegrees);
		assertTrue(observedMaxPitchRateDegrees < 18.0, () -> "maxPitchRate=" + observedMaxPitchRateDegrees);
		assertTrue(observedMaxRollRateDegrees < 35.0, () -> "maxRollRate=" + observedMaxRollRateDegrees);
		assertEquals(0.0, finalEulerDegrees.x(), 8.0, () -> "finalEuler=" + finalEulerDegrees);
		assertEquals(0.0, finalEulerDegrees.z(), 8.0, () -> "finalEuler=" + finalEulerDegrees);
		assertTrue(physics.state().positionMeters().y() > 5.0, () -> "position=" + physics.state().positionMeters());
	}

	@Test
	void rotorBladeCountIsPresetSpecific() {
		assertEquals(3, DroneConfig.racingQuad().rotors().get(0).bladeCount());
		assertEquals(3, DroneConfig.apDrone().rotors().get(0).bladeCount());
		assertEquals(3, DroneConfig.cinewhoop().rotors().get(0).bladeCount());
		assertEquals(2, DroneConfig.heavyLift().rotors().get(0).bladeCount());
		assertEquals(DroneConfig.APDRONE_MOTOR_POLE_PAIRS, DroneConfig.apDrone().rotors().get(0).motorPolePairs(), 1.0e-12);

		RotorSpec defaultRotor = new RotorSpec(
				Vec3.ZERO,
				1,
				12.0,
				1.0e-6,
				0.014,
				0.0635,
				0.08,
				0.16,
				0.0028,
				3.0e-6
		);
		assertEquals(2, defaultRotor.bladeCount());
		assertEquals(RotorSpec.DEFAULT_MOTOR_POLE_PAIRS, defaultRotor.motorPolePairs(), 1.0e-12);
		assertEquals(3, defaultRotor.withBladeCount(3).bladeCount());
		assertEquals(8, defaultRotor.withBladeCount(99).bladeCount());
		assertEquals(6.0, defaultRotor.withMotorPolePairs(6.0).motorPolePairs(), 1.0e-12);
		assertEquals(28.0, defaultRotor.withMotorPolePairs(99.0).motorPolePairs(), 1.0e-12);
		assertEquals(RotorSpec.DEFAULT_MOTOR_POLE_PAIRS, defaultRotor.withMotorPolePairs(Double.NaN).motorPolePairs(), 1.0e-12);
		assertEquals(300.0, DronePhysics.betaflightErpm100FromMechanicalRpm(5_000.0, 6.0), 1.0e-12);
		assertEquals(2_000.0, DronePhysics.betaflightEIntervalMicrosFromMechanicalRpm(5_000.0, 6.0), 1.0e-12);
		assertEquals(
				5.376333333333333e-6,
				RotorSpec.estimatedUniformBladePropInertiaKgMetersSquared(0.0635, 4.0),
				1.0e-15
		);
		assertEquals(0.0, RotorSpec.estimatedUniformBladePropInertiaKgMetersSquared(Double.NaN, 4.0), 1.0e-15);
	}

	@Test
	void apDronePresetMatchesReferenceInertiaAndBetaflightAnchors() {
		DroneConfig config = DroneConfig.apDrone();
		RotorSpec rotor = config.rotors().get(0);
		double motorCenterRadius = Math.hypot(rotor.positionBodyMeters().x(), rotor.positionBodyMeters().z());
		double maxRpm = rotor.maxOmegaRadiansPerSecond() * 60.0 / (Math.PI * 2.0);
		double actualRateCenterSensitivity = Math.toDegrees(config.maxPitchRateRadiansPerSecond())
				* (1.0 - config.rateExpo().x())
				* (1.0 - config.rateSuper().x());

		assertEquals(0.6284, config.massKg(), 1.0e-9);
		assertEquals(0.001346, config.inertiaKgMetersSquared().x(), 1.0e-12);
		assertEquals(0.002480, config.inertiaKgMetersSquared().y(), 1.0e-12);
		assertEquals(0.001410, config.inertiaKgMetersSquared().z(), 1.0e-12);
		assertEquals(0.095, motorCenterRadius, 1.0e-12);
		assertEquals(5.1 * 0.0254 * 0.5, rotor.radiusMeters(), 1.0e-12);
		assertEquals(4.5 / 5.1, rotor.bladePitchToDiameterRatio(), 1.0e-12);
		assertEquals(3, rotor.bladeCount());
		assertEquals(DroneConfig.APDRONE_FOXEER_DONUT_5145_THRUST_COEFFICIENT, rotor.thrustCoefficient(), 1.0e-18);
		assertEquals(
				DroneConfig.APDRONE_FOXEER_DONUT_5145_YAW_TORQUE_PER_THRUST_METERS,
				rotor.yawTorquePerThrustMeter(),
				1.0e-15
		);
		assertEquals(DroneConfig.APDRONE_MOTOR_PDF_WINDING_RESISTANCE_OHMS, rotor.motorWindingResistanceOhms(), 1.0e-12);
		assertEquals(
				RotorSpec.estimatedUniformBladePropInertiaKgMetersSquared(rotor.radiusMeters(), 4.3),
				rotor.rotorInertiaKgMetersSquared(),
				1.0e-15
		);
		assertEquals(EscCommandProtocol.DSHOT600, config.escCommandProtocol());
		assertEquals(2000, config.escCommandResolutionSteps());
		assertEquals(480.0, config.escCommandFrameRateHertz(), 1.0e-12);
		assertEquals(1.5, config.batteryCapacityAmpHours(), 1.0e-12);
		assertEquals(150.0, config.maxBatteryCurrentAmps(), 1.0e-12);
		assertEquals(125.0, config.gyroLowPassCutoffHz(), 1.0e-12);
		assertEquals(
				DroneConfig.APDRONE_BLACKBOX_LOW_MOTION_GYRO_NOISE_RADIANS_PER_SECOND,
				config.gyroNoiseStdDevRadiansPerSecond(),
				1.0e-15
		);
		assertEquals(
				DroneConfig.APDRONE_BLACKBOX_LOW_MOTION_ACCELEROMETER_NOISE_METERS_PER_SECOND_SQUARED,
				config.accelerometerNoiseStdDevMetersPerSecondSquared(),
				1.0e-15
		);
		assertEquals(
				Math.toRadians(RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_DEGREES_PER_SECOND),
				config.maxPitchRateRadiansPerSecond(),
				1.0e-12
		);
		assertEquals(
				Math.toRadians(RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_DEGREES_PER_SECOND),
				config.maxYawRateRadiansPerSecond(),
				1.0e-12
		);
		assertEquals(
				Math.toRadians(RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_RATE_DEGREES_PER_SECOND),
				config.maxRollRateRadiansPerSecond(),
				1.0e-12
		);
		assertEquals(RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_EXPO_FRACTION, config.rateExpo().x(), 1.0e-12);
		assertEquals(RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_EXPO_FRACTION, config.rateExpo().y(), 1.0e-12);
		assertEquals(RateEnvelopeCalibration.APDRONE_SELECTED_ACTUAL_EXPO_FRACTION, config.rateExpo().z(), 1.0e-12);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_PROJECT_EQUIVALENT_SUPER_RATE,
				config.rateSuper().x(),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_PROJECT_EQUIVALENT_SUPER_RATE,
				config.rateSuper().y(),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_SELECTED_PROJECT_EQUIVALENT_SUPER_RATE,
				config.rateSuper().z(),
				1.0e-12
		);
		assertEquals(
				RateEnvelopeCalibration.APDRONE_BETAFLIGHT_ACTUAL_CENTER_SENSITIVITY_DEGREES_PER_SECOND,
				actualRateCenterSensitivity,
				1.0e-9
		);
		assertTrue(maxRpm > 28500.0, () -> "maxRpm=" + maxRpm);
		assertTrue(maxRpm < 30000.0, () -> "maxRpm=" + maxRpm);
		assertTrue(config.hoverDirectThrustFraction() > 0.10, () -> "hoverDirect=" + config.hoverDirectThrustFraction());
		assertTrue(config.hoverDirectThrustFraction() < 0.13, () -> "hoverDirect=" + config.hoverDirectThrustFraction());
		assertEquals(
				DroneConfig.APDRONE_NORMAL_POWER_REFERENCE_THROTTLE_COMMAND,
				config.hoverThrottle(),
				1.0e-12
		);
		assertEquals(
				config.hoverDirectThrustFraction(),
				config.throttleCommandToDirectThrustFraction(config.hoverThrottle()),
				1.0e-12
		);
		assertTrue(config.throttleCommandCurveExponent() > 3.5);
		assertTrue(config.throttleCommandToDirectThrustFraction(0.9867448887828499) > 0.94);
		assertTrue(config.inertiaKgMetersSquared().y() > config.inertiaKgMetersSquared().x());
		assertTrue(config.inertiaKgMetersSquared().y() > config.inertiaKgMetersSquared().z());
		assertTrue(config.bodyDragCoefficients().z() < DroneConfig.racingQuad().bodyDragCoefficients().z());
	}

	@Test
	void apDroneMotorPdfResistanceOverridesCurrentLimitFallback() throws ReflectiveOperationException {
		DroneConfig measured = DroneConfig.apDrone();
		DroneConfig fallback = measured.withMotorWindingResistanceOhms(0.0);
		Method method = DronePhysics.class.getDeclaredMethod("baseMotorWindingResistanceOhms", int.class);
		method.setAccessible(true);

		double measuredResistance = (double) method.invoke(new DronePhysics(measured), 0);
		double fallbackResistance = (double) method.invoke(new DronePhysics(fallback), 0);

		assertEquals(DroneConfig.APDRONE_MOTOR_PDF_WINDING_RESISTANCE_OHMS, measuredResistance, 1.0e-12);
		assertEquals(0.0, fallback.rotors().get(0).motorWindingResistanceOhms(), 1.0e-12);
		assertTrue(fallbackResistance > measuredResistance * 2.0, () -> "fallbackResistance=" + fallbackResistance);
		assertTrue(fallbackResistance < measuredResistance * 3.0, () -> "fallbackResistance=" + fallbackResistance);
	}

	@Test
	void rollCommandProducesBodyRollRate() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput input = new DroneInput(0.45, 0.0, 0.7, 0.0, true);

		for (int i = 0; i < 100; i++) {
			physics.step(input, 0.0025);
		}

		assertTrue(physics.state().angularVelocityBodyRadiansPerSecond().z() > 0.25);
	}

	@Test
	void acroModeDoesNotSelfLevelAtCenteredStick() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		Quaternion tilted = rollOrientation(Math.toRadians(24.0));
		physics.state().setOrientation(tilted);
		physics.state().setEstimatedOrientation(tilted);

		physics.step(new DroneInput(0.45, 0.0, 0.0, 0.0, true, FlightMode.ACRO), 0.005);

		assertEquals(FlightMode.ACRO, physics.state().flightMode());
		assertEquals(0.0, physics.state().selfLevelBlend(), 1.0e-9);
		assertEquals(0.0, physics.state().targetRatesBodyRadiansPerSecond().z(), 1.0e-9);
	}

	@Test
	void angleModeTurnsAttitudeErrorIntoLevelingRate() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		Quaternion tilted = rollOrientation(Math.toRadians(24.0));
		physics.state().setOrientation(tilted);
		physics.state().setEstimatedOrientation(tilted);

		physics.step(new DroneInput(0.45, 0.0, 0.0, 0.0, true, FlightMode.ANGLE), 0.005);

		assertEquals(FlightMode.ANGLE, physics.state().flightMode());
		assertEquals(1.0, physics.state().selfLevelBlend(), 1.0e-9);
		assertTrue(Math.toDegrees(physics.state().levelAttitudeErrorRadians().z()) < -20.0);
		assertTrue(physics.state().targetRatesBodyRadiansPerSecond().z() < -1.5);
	}

	@Test
	void selfLevelTuningChangesAngleLimitAndCorrectionGain() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withSelfLevel(Math.toRadians(30.0), 3.0, 0.20, 0.70);
		DronePhysics physics = new DronePhysics(config);

		physics.step(new DroneInput(0.45, 0.0, 1.0, 0.0, true, FlightMode.ANGLE), 0.005);

		assertEquals(30.0, Math.toDegrees(physics.state().levelTargetAttitudeRadians().z()), 1.0e-9);
		assertEquals(Math.toRadians(30.0) * 3.0, physics.state().targetRatesBodyRadiansPerSecond().z(), 1.0e-9);
	}

	@Test
	void horizonModeBlendsSelfLevelOutAtLargeStickDeflection() {
		DronePhysics centered = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DronePhysics fullStick = new DronePhysics(directControl(DroneConfig.racingQuad()));
		Quaternion tilted = rollOrientation(Math.toRadians(24.0));
		centered.state().setEstimatedOrientation(tilted);
		fullStick.state().setEstimatedOrientation(tilted);

		centered.step(new DroneInput(0.45, 0.0, 0.0, 0.0, true, FlightMode.HORIZON), 0.005);
		fullStick.step(new DroneInput(0.45, 0.0, 1.0, 0.0, true, FlightMode.HORIZON), 0.005);

		assertEquals(FlightMode.HORIZON, centered.state().flightMode());
		assertEquals(1.0, centered.state().selfLevelBlend(), 1.0e-9);
		assertTrue(centered.state().targetRatesBodyRadiansPerSecond().z() < -1.5);
		assertEquals(0.0, fullStick.state().selfLevelBlend(), 1.0e-9);
		assertTrue(fullStick.state().targetRatesBodyRadiansPerSecond().z() > 8.0);
	}

	@Test
	void horizonModeUsesTunedTransitionWindow() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withSelfLevel(DroneConfig.DEFAULT_SELF_LEVEL_MAX_ANGLE_RADIANS, DroneConfig.DEFAULT_SELF_LEVEL_RATE_GAIN, 0.20, 0.60);
		DronePhysics midStick = new DronePhysics(config);
		DronePhysics outsideWindow = new DronePhysics(config);

		midStick.step(new DroneInput(0.45, 0.0, 0.40, 0.0, true, FlightMode.HORIZON), 0.005);
		outsideWindow.step(new DroneInput(0.45, 0.0, 0.60, 0.0, true, FlightMode.HORIZON), 0.005);

		assertEquals(0.5, midStick.state().selfLevelBlend(), 1.0e-9);
		assertEquals(0.0, outsideWindow.state().selfLevelBlend(), 1.0e-9);
	}

	@Test
	void armedIdleKeepsMotorsSpinningAtZeroThrottle() {
		DronePhysics armed = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DronePhysics disarmed = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput armedIdle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 400; i++) {
			armed.state().setVelocityMetersPerSecond(Vec3.ZERO);
			disarmed.state().setVelocityMetersPerSecond(Vec3.ZERO);
			armed.step(armedIdle, 0.0025);
			disarmed.step(DroneInput.idle(), 0.0025);
		}

		assertTrue(armed.state().averageMotorPower(armed.config()) > 0.18);
		assertEquals(0.0, disarmed.state().averageMotorPower(disarmed.config()), 1.0e-9);
	}

	@Test
	void airmodePreservesLowThrottleRollAuthority() {
		DroneConfig noAirmodeConfig = directControl(DroneConfig.racingQuad()).withMotorIdleAndAirmode(0.055, 0.0);
		DroneConfig airmodeConfig = noAirmodeConfig.withMotorIdleAndAirmode(0.055, 1.0);
		DronePhysics noAirmode = new DronePhysics(noAirmodeConfig);
		DronePhysics airmode = new DronePhysics(airmodeConfig);
		DroneInput lowThrottleRoll = new DroneInput(0.0, 0.0, 0.3, 0.0, true);

		for (int i = 0; i < 160; i++) {
			noAirmode.step(lowThrottleRoll, 0.0025);
			airmode.step(lowThrottleRoll, 0.0025);
		}

		assertTrue(airmode.state().angularVelocityBodyRadiansPerSecond().z() > noAirmode.state().angularVelocityBodyRadiansPerSecond().z() + 0.18);
	}

	@Test
	void mixerSaturationReportsClipping() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput saturated = new DroneInput(1.0, 0.0, 1.0, 0.0, true);

		physics.step(saturated, 0.0025);

		assertTrue(physics.state().mixerSaturation() > 0.01);
		assertTrue(physics.state().mixerHighSaturation() > 0.01);
		assertTrue(physics.state().mixerHighSaturation() >= physics.state().mixerLowSaturation());
		assertEquals(0.0, physics.state().mixerHighHeadroom(), 1.0e-9);
		assertTrue(physics.state().mixerLowHeadroom() >= 0.0);
		assertTrue(physics.state().mixerOutputTorqueBodyNewtonMeters().isFinite());
		assertTrue(physics.state().mixerAxisAuthority().isFinite());
		assertTrue(physics.state().minMixerAxisAuthority() > 0.99);
	}

	@Test
	void lowThrottleWithoutAirmodeReportsLostRollAuthority() {
		DroneConfig config = directControl(DroneConfig.racingQuad()).withMotorIdleAndAirmode(0.055, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput lowThrottleRoll = new DroneInput(0.0, 0.0, 1.0, 0.0, true);

		physics.step(lowThrottleRoll, 0.0025);

		assertTrue(physics.state().mixerSaturation() > 0.01);
		assertTrue(physics.state().mixerLowSaturation() > 0.01);
		assertTrue(physics.state().mixerLowSaturation() >= physics.state().mixerHighSaturation());
		assertEquals(0.0, physics.state().mixerLowHeadroom(), 1.0e-9);
		assertTrue(physics.state().mixerHighHeadroom() >= 0.0);
		assertTrue(physics.state().mixerOutputTorqueBodyNewtonMeters().isFinite());
		assertTrue(physics.state().mixerAxisAuthority().isFinite());
		assertEquals(1.0, physics.state().mixerAxisAuthority().x(), 1.0e-9);
		assertEquals(1.0, physics.state().mixerAxisAuthority().y(), 1.0e-9);
		assertTrue(physics.state().mixerAxisAuthority().z() < 0.50);
		assertTrue(physics.state().minMixerAxisAuthority() < 0.50);
	}

	@Test
	void mixerAllocationSolvesCoupledCantedRotorTorqueWithoutCollectiveDrift() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withCenterOfMassOffsetBodyMeters(new Vec3(0.035, 0.0, -0.022))
				.withRotorOutwardCantDegrees(18.0);
		Vec3 requestedTorque = new Vec3(0.110, 0.035, -0.090);

		double[] deltas = DronePhysics.allocateTorqueMixDeltas(
				config.rotors(),
				config.centerOfMassOffsetBodyMeters(),
				requestedTorque
		);
		Vec3 allocatedTorque = torqueFromRotorDeltas(config, deltas);

		assertEquals(0.0, sum(deltas), 1.0e-5);
		assertEquals(requestedTorque.x(), allocatedTorque.x(), 2.0e-5);
		assertEquals(requestedTorque.y(), allocatedTorque.y(), 2.0e-5);
		assertEquals(requestedTorque.z(), allocatedTorque.z(), 2.0e-5);
	}

	@Test
	void quaternionStaysNormalizedDuringAggressiveManeuver() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput input = new DroneInput(0.65, 0.8, -0.8, 0.5, true);

		for (int i = 0; i < 2000; i++) {
			physics.step(input, 0.0025);
		}

		assertEquals(1.0, physics.state().orientation().length(), 1.0e-6);
	}

	@Test
	void damagedRotorLimitsAvailableThrust() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		physics.state().damageRotor(0, 0.85);
		DroneInput input = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 300; i++) {
			physics.step(input, 0.0025);
		}

		double[] motorOmega = physics.state().motorOmegaRadiansPerSecond();
		assertTrue(motorOmega[0] < motorOmega[1] * 0.80);
		assertTrue(physics.state().motorPower(physics.config(), 0) < physics.state().motorPower(physics.config(), 1) * 0.80);
		assertTrue(physics.state().rotorThrustNewtons(0) < physics.state().rotorThrustNewtons(1) * 0.35);
		assertTrue(physics.state().averageRotorHealth() < 0.8);
	}

	@Test
	void massAndRotorThrustTuningChangesHoverThrottle() {
		DroneConfig base = directControl(DroneConfig.racingQuad());
		DroneConfig heavy = base.withMassKg(base.massKg() * 1.6);
		DroneConfig strongerRotors = base.withRotorMaxThrustNewtons(base.rotors().get(0).maxThrustNewtons() * 1.4);

		assertTrue(heavy.hoverThrottle() > base.hoverThrottle() * 1.5);
		assertTrue(strongerRotors.hoverThrottle() < base.hoverThrottle() * 0.75);
	}

	@Test
	void rotorBladePitchDefaultsAndScalesWithRadius() {
		DroneConfig base = DroneConfig.racingQuad();
		RotorSpec rotor = base.rotors().get(0);
		double pitchRatio = rotor.bladePitchMeters() / rotor.radiusMeters();

		assertEquals(RotorSpec.defaultBladePitchMeters(rotor.radiusMeters()), rotor.bladePitchMeters(), 1.0e-12);
		assertEquals(RotorSpec.DEFAULT_BLADE_PITCH_TO_DIAMETER_RATIO, rotor.bladePitchToDiameterRatio(), 1.0e-12);
		assertEquals(21.13, Math.toDegrees(rotor.geometricBladePitchAngleRadians()), 0.02);
		assertEquals(
				rotor.geometricBladePitchAngleRadians(),
				rotor.geometricBladePitchAngleRadians(RotorSpec.BLADE_GEOMETRY_REFERENCE_STATION_FRACTION),
				1.0e-12
		);

		DroneConfig largerRotor = base.withRotorRadiusMeters(rotor.radiusMeters() * 1.35);
		RotorSpec larger = largerRotor.rotors().get(0);
		assertEquals(pitchRatio, larger.bladePitchMeters() / larger.radiusMeters(), 1.0e-12);
		assertEquals(rotor.bladePitchToDiameterRatio(), larger.bladePitchToDiameterRatio(), 1.0e-12);

		DroneConfig tunedPitch = base.withRotorBladePitchMeters(rotor.radiusMeters() * 2.35);
		assertEquals(rotor.radiusMeters() * 2.35, tunedPitch.rotors().get(0).bladePitchMeters(), 1.0e-12);

		DroneConfig hqFiveByFourPointThree = base.withRotorBladePitchToDiameterRatio(0.86);
		RotorSpec hqLikeRotor = hqFiveByFourPointThree.rotors().get(0);
		assertEquals(0.86, hqLikeRotor.bladePitchToDiameterRatio(), 1.0e-12);
		assertEquals(21.36, Math.toDegrees(hqLikeRotor.geometricBladePitchAngleRadians()), 0.02);
	}

	@Test
	void representativeBladeChordTracksBladeCountPitchAndUtilityLiftProps() {
		RotorSpec racingTriBlade = DroneConfig.racingQuad().rotors().get(0);
		RotorSpec racingTwoBlade = racingTriBlade.withBladeCount(2);
		RotorSpec liftProp = DroneConfig.heavyLift().rotors().get(0);

		assertEquals(RotorSpec.DEFAULT_REPRESENTATIVE_CHORD_TO_RADIUS_RATIO, racingTwoBlade.representativeBladeChordToRadiusRatio(), 1.0e-12);
		assertEquals(0.1536, racingTriBlade.representativeBladeChordToRadiusRatio(), 1.0e-5);
		assertTrue(racingTriBlade.representativeBladeChordMeters() > racingTwoBlade.representativeBladeChordMeters() * 1.25);
		assertTrue(liftProp.representativeBladeChordToRadiusRatio() > 0.15);
		assertTrue(liftProp.representativeBladeChordToRadiusRatio() < 0.18);
		assertTrue(liftProp.representativeBladeChordMeters() > racingTriBlade.representativeBladeChordMeters() * 2.0);
	}

	@Test
	void liftPresetsUseLowPitchUtilityPropGeometry() {
		DroneConfig racing = DroneConfig.racingQuad();
		DroneConfig heavyLift = DroneConfig.heavyLift();
		DroneConfig hexLift = DroneConfig.hexLift();
		DroneConfig octoLift = DroneConfig.octoLift();
		DroneConfig coaxialX8 = DroneConfig.coaxialX8();

		assertLiftPropPitch(heavyLift);
		assertLiftPropPitch(hexLift);
		assertLiftPropPitch(octoLift);
		assertLiftPropPitch(coaxialX8);
		assertTrue(heavyLift.rotors().get(0).bladePitchToDiameterRatio()
				< racing.rotors().get(0).bladePitchToDiameterRatio() * 0.60);
		assertEquals(
				octoLift.rotors().get(0).bladePitchToDiameterRatio(),
				coaxialX8.rotors().get(0).bladePitchToDiameterRatio(),
				1.0e-12
		);
	}

	@Test
	void aircraftPresetsRepresentDifferentFrameClasses() {
		DroneConfig racing = directControl(DroneConfig.racingQuad());
		DroneConfig apDrone = DroneConfig.apDrone();
		DroneConfig cinewhoop = DroneConfig.cinewhoop();
		DroneConfig heavyLift = DroneConfig.heavyLift();
		DroneConfig hexLift = DroneConfig.hexLift();
		DroneConfig octoLift = DroneConfig.octoLift();
		DroneConfig coaxialX8 = DroneConfig.coaxialX8();

		assertTrue(apDrone.massKg() < racing.massKg());
		assertTrue(apDrone.hoverDirectThrustFraction() < racing.hoverDirectThrustFraction());
		assertTrue(apDrone.hoverThrottle() > racing.hoverThrottle());
		assertTrue(apDrone.rotors().get(0).positionBodyMeters().length() < racing.rotors().get(0).positionBodyMeters().length());
		assertTrue(apDrone.rotors().get(0).radiusMeters() > racing.rotors().get(0).radiusMeters());
		assertTrue(apDrone.maxRollRateRadiansPerSecond() > cinewhoop.maxRollRateRadiansPerSecond());
		assertTrue(apDrone.maxRollRateRadiansPerSecond() > racing.maxRollRateRadiansPerSecond());
		assertEquals(EscCommandProtocol.DSHOT600, apDrone.escCommandProtocol());
		assertTrue(cinewhoop.maxRollRateRadiansPerSecond() < racing.maxRollRateRadiansPerSecond());
		assertTrue(cinewhoop.bodyDragCoefficients().z() > racing.bodyDragCoefficients().z());
		assertTrue(cinewhoop.hoverThrottle() > racing.hoverThrottle());
		assertTrue(heavyLift.massKg() > racing.massKg() * 3.0);
		assertTrue(heavyLift.rotors().get(0).radiusMeters() > racing.rotors().get(0).radiusMeters() * 1.8);
		assertTrue(heavyLift.rotors().get(0).bladePitchToDiameterRatio()
				< racing.rotors().get(0).bladePitchToDiameterRatio());
		assertTrue(heavyLift.motorTimeConstantSeconds() > racing.motorTimeConstantSeconds() * 2.0);
		assertTrue(heavyLift.nominalBatteryVoltage() > racing.nominalBatteryVoltage());
		assertEquals(6, hexLift.rotors().size());
		assertTrue(hexLift.totalMaxThrustNewtons() > heavyLift.totalMaxThrustNewtons());
		assertTrue(hexLift.hoverThrottle() < heavyLift.hoverThrottle());
		assertTrue(hexLift.maxRollRateRadiansPerSecond() > heavyLift.maxRollRateRadiansPerSecond());
		assertEquals(8, octoLift.rotors().size());
		assertTrue(octoLift.massKg() > hexLift.massKg());
		assertTrue(octoLift.totalMaxThrustNewtons() > hexLift.totalMaxThrustNewtons());
		assertTrue(octoLift.hoverThrottle() > hexLift.hoverThrottle());
		assertTrue(octoLift.maxRollRateRadiansPerSecond() < hexLift.maxRollRateRadiansPerSecond());
		assertEquals(8, coaxialX8.rotors().size());
		assertTrue(coaxialX8.massKg() > octoLift.massKg());
		assertTrue(coaxialX8.hoverThrottle() > octoLift.hoverThrottle());
		assertEquals(
				octoLift.rotors().get(0).bladePitchToDiameterRatio(),
				coaxialX8.rotors().get(0).bladePitchToDiameterRatio(),
				1.0e-12
		);
		assertEquals(coaxialX8.rotors().get(0).positionBodyMeters().x(), coaxialX8.rotors().get(1).positionBodyMeters().x(), 1.0e-9);
		assertEquals(coaxialX8.rotors().get(0).positionBodyMeters().z(), coaxialX8.rotors().get(1).positionBodyMeters().z(), 1.0e-9);
		assertTrue(coaxialX8.rotors().get(0).positionBodyMeters().y() > coaxialX8.rotors().get(1).positionBodyMeters().y());
	}

	@Test
	void racingQuadPresetUsesMeasuredFiveInchRotorSpeedScale() {
		DroneConfig racing = DroneConfig.racingQuad();
		RotorSpec rotor = racing.rotors().get(0);
		double maxRpm = rotor.maxOmegaRadiansPerSecond() * 60.0 / (Math.PI * 2.0);
		double tipMach = rotor.maxOmegaRadiansPerSecond() * rotor.radiusMeters() / 346.1;

		assertTrue(rotor.thrustCoefficient() >= 0.9e-6);
		assertTrue(rotor.thrustCoefficient() <= 1.7e-6);
		assertTrue(maxRpm > 27000.0, () -> "maxRpm=" + maxRpm);
		assertTrue(maxRpm < 31000.0, () -> "maxRpm=" + maxRpm);
		assertTrue(tipMach > 0.50, () -> "tipMach=" + tipMach);
		assertTrue(tipMach < 0.62, () -> "tipMach=" + tipMach);
		assertEquals(
				RotorSpec.estimatedUniformBladePropInertiaKgMetersSquared(rotor.radiusMeters(), 4.0),
				rotor.rotorInertiaKgMetersSquared(),
				1.0e-15
		);
		assertTrue(rotor.rotorInertiaKgMetersSquared() >= 5.0e-6);
		assertTrue(rotor.rotorInertiaKgMetersSquared() <= 6.0e-6);
		assertTrue(rotor.yawTorquePerThrustMeter() >= 0.011);
		assertTrue(rotor.yawTorquePerThrustMeter() <= 0.016);
	}

	@Test
	void hexLiftPresetUsesGenericSixRotorMixer() {
		DroneConfig config = directControl(DroneConfig.hexLift())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(22.2, 22.1, 0.0, 20.0, 160.0);
		DronePhysics hover = new DronePhysics(config);
		DronePhysics yawing = new DronePhysics(config);
		DroneInput hoverInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneInput yawInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 1.0, true);

		for (int i = 0; i < 600; i++) {
			hover.step(hoverInput, 0.0025);
			yawing.step(yawInput, 0.0025);
		}

		assertEquals(6, yawing.state().motorOmegaRadiansPerSecond().length);
		assertTrue(Math.abs(hover.state().angularVelocityBodyRadiansPerSecond().y()) < 0.12);
		assertTrue(Math.abs(yawing.state().angularVelocityBodyRadiansPerSecond().y())
				> Math.abs(hover.state().angularVelocityBodyRadiansPerSecond().y()) + 0.15);

		double positiveSpinThrust = 0.0;
		double negativeSpinThrust = 0.0;
		int positiveSpinCount = 0;
		int negativeSpinCount = 0;
		for (int i = 0; i < config.rotors().size(); i++) {
			if (config.rotors().get(i).spinDirection() > 0) {
				positiveSpinThrust += yawing.state().rotorThrustNewtons(i);
				positiveSpinCount++;
			} else {
				negativeSpinThrust += yawing.state().rotorThrustNewtons(i);
				negativeSpinCount++;
			}
		}

		assertEquals(3, positiveSpinCount);
		assertEquals(3, negativeSpinCount);
		assertTrue(Math.abs(positiveSpinThrust / positiveSpinCount - negativeSpinThrust / negativeSpinCount) > 0.25);
	}

	@Test
	void octoLiftPresetUsesGenericEightRotorMixer() {
		DroneConfig config = directControl(DroneConfig.octoLift())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0);
		DronePhysics hover = new DronePhysics(config);
		DronePhysics yawing = new DronePhysics(config);
		DroneInput hoverInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneInput yawInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 1.0, true);

		for (int i = 0; i < 600; i++) {
			hover.step(hoverInput, 0.0025);
			yawing.step(yawInput, 0.0025);
		}

		assertEquals(8, yawing.state().motorOmegaRadiansPerSecond().length);
		assertTrue(Math.abs(hover.state().angularVelocityBodyRadiansPerSecond().y()) < 0.12);
		assertTrue(Math.abs(yawing.state().angularVelocityBodyRadiansPerSecond().y())
				> Math.abs(hover.state().angularVelocityBodyRadiansPerSecond().y()) + 0.12);

		double positiveSpinThrust = 0.0;
		double negativeSpinThrust = 0.0;
		int positiveSpinCount = 0;
		int negativeSpinCount = 0;
		for (int i = 0; i < config.rotors().size(); i++) {
			if (config.rotors().get(i).spinDirection() > 0) {
				positiveSpinThrust += yawing.state().rotorThrustNewtons(i);
				positiveSpinCount++;
			} else {
				negativeSpinThrust += yawing.state().rotorThrustNewtons(i);
				negativeSpinCount++;
			}
		}

		assertEquals(4, positiveSpinCount);
		assertEquals(4, negativeSpinCount);
		assertTrue(Math.abs(positiveSpinThrust / positiveSpinCount - negativeSpinThrust / negativeSpinCount) > 0.20);
	}

	@Test
	void stackedRotorsReportWakeInterferenceAndLoseLowerRotorThrust() {
		DroneConfig base = directControl(DroneConfig.octoLift())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withRotorImbalanceIntensity(0.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		RotorSpec template = base.rotors().get(0);
		double arm = 0.34;
		double upperY = template.radiusMeters() * 0.70;
		double lowerY = -upperY;
		DroneConfig stacked = base.withRotors(List.of(
				rotorLike(template, new Vec3(arm, upperY, arm), 1),
				rotorLike(template, new Vec3(arm, lowerY, arm), -1),
				rotorLike(template, new Vec3(-arm, upperY, arm), -1),
				rotorLike(template, new Vec3(-arm, lowerY, arm), 1),
				rotorLike(template, new Vec3(-arm, upperY, -arm), 1),
				rotorLike(template, new Vec3(-arm, lowerY, -arm), -1),
				rotorLike(template, new Vec3(arm, upperY, -arm), -1),
				rotorLike(template, new Vec3(arm, lowerY, -arm), 1)
		));
		DronePhysics flat = new DronePhysics(base);
		DronePhysics coaxial = new DronePhysics(stacked);
		DroneInput input = new DroneInput(base.hoverThrottle() + 0.08, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 700; i++) {
			holdInStillAir(flat);
			holdInStillAir(coaxial);
			flat.step(input, 0.005);
			coaxial.step(input, 0.005);
		}

		assertEquals(0.0, flat.state().maxRotorWakeInterferenceIntensity(), 0.02);
		assertEquals(0.0, flat.state().maxRotorWakeSwirlVelocityMetersPerSecond(), 0.05);
		assertEquals(1.0, flat.state().minRotorWakeThrustScale(), 0.01);
		assertTrue(coaxial.state().maxRotorWakeInterferenceIntensity() > 0.35);
		assertTrue(coaxial.state().averageRotorWakeInterferenceIntensity() > 0.15);
		assertTrue(coaxial.state().maxRotorWakeSwirlVelocityMetersPerSecond() > 0.35);
		assertTrue(coaxial.state().averageRotorWakeSwirlVelocityMetersPerSecond() > 0.10);
		assertTrue(coaxial.state().rotorWakeInterferenceIntensity(1)
				> coaxial.state().rotorWakeInterferenceIntensity(0) + 0.30);
		assertTrue(coaxial.state().rotorWakeThrustScale(1)
				< coaxial.state().rotorWakeThrustScale(0) - 0.06);
		assertTrue(coaxial.state().minRotorWakeThrustScale() < 0.93);
		assertTrue(coaxial.state().rotorWakeSwirlVelocityMetersPerSecond(1)
				> coaxial.state().rotorWakeSwirlVelocityMetersPerSecond(0) + 0.25);
		assertTrue(coaxial.state().rotorThrustNewtons(1) < coaxial.state().rotorThrustNewtons(0) * 0.88);
		assertTrue(coaxial.state().rotorAerodynamicLoadFactor(1)
				> coaxial.state().rotorAerodynamicLoadFactor(0) + 0.08);
		assertTrue(coaxial.state().rotorVibration() > flat.state().rotorVibration() + 0.015);
	}

	@Test
	void coaxialX8PresetProducesSameFrameWakeInterference() {
		DroneConfig flatConfig = directControl(DroneConfig.octoLift())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DroneConfig coaxialConfig = directControl(DroneConfig.coaxialX8())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics flat = new DronePhysics(flatConfig);
		DronePhysics coaxial = new DronePhysics(coaxialConfig);
		DroneInput input = new DroneInput(coaxialConfig.hoverThrottle() + 0.08, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 700; i++) {
			holdInStillAir(flat);
			holdInStillAir(coaxial);
			flat.step(input, 0.005);
			coaxial.step(input, 0.005);
		}

		assertEquals(8, coaxial.state().motorCount());
		assertEquals(0.0, flat.state().maxRotorWakeInterferenceIntensity(), 0.02);
		assertEquals(0.0, flat.state().maxRotorWakeSwirlVelocityMetersPerSecond(), 0.05);
		assertEquals(1.0, flat.state().minRotorWakeThrustScale(), 0.01);
		assertTrue(coaxial.state().maxRotorWakeInterferenceIntensity() > 0.30);
		assertTrue(coaxial.state().averageRotorWakeInterferenceIntensity() > 0.12);
		assertTrue(coaxial.state().maxRotorWakeSwirlVelocityMetersPerSecond() > 0.30);
		assertTrue(coaxial.state().averageRotorWakeSwirlVelocityMetersPerSecond() > 0.08);
		assertTrue(coaxial.state().rotorWakeInterferenceIntensity(1)
				> coaxial.state().rotorWakeInterferenceIntensity(0) + 0.25);
		assertTrue(coaxial.state().rotorWakeThrustScale(1)
				< coaxial.state().rotorWakeThrustScale(0) - 0.05);
		assertTrue(coaxial.state().minRotorWakeThrustScale() < 0.94);
		assertTrue(coaxial.state().rotorWakeInterferenceIntensity(3)
				> coaxial.state().rotorWakeInterferenceIntensity(2) + 0.25);
		assertTrue(coaxial.state().rotorWakeSwirlVelocityMetersPerSecond(1)
				> coaxial.state().rotorWakeSwirlVelocityMetersPerSecond(0) + 0.20);
		assertTrue(coaxial.state().rotorThrustNewtons(1) < coaxial.state().rotorThrustNewtons(0) * 0.90);
		assertEquals(0.0, flat.state().maxAbsRotorCoaxialLoadBias(), 1.0e-9);
		assertEquals(0.0, flat.state().maxAbsRotorCoaxialLoadBiasTarget(), 1.0e-9);
		assertEquals(1.0, flat.state().maxRotorCoaxialAllocationCommandRatio(), 1.0e-9);
		assertEquals(0.0, flat.state().maxRotorCoaxialAllocationUncertaintyPercent(), 1.0e-9);
		assertTrue(coaxial.state().maxAbsRotorCoaxialLoadBias() > 0.025);
		assertTrue(coaxial.state().maxAbsRotorCoaxialLoadBiasTarget() >= coaxial.state().maxAbsRotorCoaxialLoadBias());
		assertTrue(coaxial.state().maxRotorCoaxialAllocationLoadFraction() > 0.10);
		assertTrue(coaxial.state().maxRotorCoaxialAllocationCommandRatio() > 1.02);
		assertTrue(coaxial.state().maxRotorCoaxialAllocationMechanicalGainPercent() > 0.10);
		assertTrue(coaxial.state().maxRotorCoaxialAllocationUncertaintyPercent() > 0.10);
		assertTrue(coaxial.state().rotorCoaxialLoadBias(0) > 0.0);
		assertTrue(coaxial.state().rotorCoaxialLoadBias(1) < 0.0);
	}

	@Test
	void coaxialWakeSwirlLimitScalesWithRotorDiskLoading() throws ReflectiveOperationException {
		DroneConfig config = directControl(DroneConfig.coaxialX8())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 500.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput highLoad = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 800; i++) {
			holdInStillAir(physics);
			physics.step(highLoad, 0.005);
		}

		double maxWakeSwirl = physics.state().maxRotorWakeSwirlVelocityMetersPerSecond();
		Method clampSwirl = DronePhysics.class.getDeclaredMethod(
				"clampRotorWakeSwirlVelocity",
				RotorSpec.class,
				Vec3.class
		);
		clampSwirl.setAccessible(true);
		Vec3 limitedSwirl = (Vec3) clampSwirl.invoke(null, config.rotors().get(1), new Vec3(20.0, 0.0, 0.0));

		assertTrue(maxWakeSwirl > 7.50, () -> "maxWakeSwirl=" + maxWakeSwirl);
		assertTrue(maxWakeSwirl < 11.8, () -> "maxWakeSwirl=" + maxWakeSwirl);
		assertTrue(limitedSwirl.length() > 8.05, () -> "limitedSwirl=" + limitedSwirl.length());
		assertTrue(limitedSwirl.length() < 11.8, () -> "limitedSwirl=" + limitedSwirl.length());
		assertEquals(0.0, limitedSwirl.y(), 1.0e-12);
		assertEquals(0.0, limitedSwirl.z(), 1.0e-12);
		assertTrue(physics.state().maxRotorWakeInterferenceIntensity() > 0.70);
	}

	@Test
	void coaxialLoadBiasFollowsBenchmarkSpacingEfficiencyWindows() {
		DroneConfig base = directControl(DroneConfig.coaxialX8())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		double closePeakBias = settledCoaxialLoadBias(withCoaxialX8Spacing(base, 0.32), 0.08);
		double valleyBias = settledCoaxialLoadBias(withCoaxialX8Spacing(base, 0.55), 0.08);
		double x8PeakBias = settledCoaxialLoadBias(withCoaxialX8Spacing(base, 0.72), 0.08);
		double farSpacingBias = settledCoaxialLoadBias(withCoaxialX8Spacing(base, 1.00), 0.08);

		assertTrue(closePeakBias > valleyBias + 0.006,
				() -> "closePeakBias=" + closePeakBias + " valleyBias=" + valleyBias);
		assertTrue(x8PeakBias > valleyBias + 0.010,
				() -> "x8PeakBias=" + x8PeakBias + " valleyBias=" + valleyBias);
		assertTrue(x8PeakBias > farSpacingBias + 0.006,
				() -> "x8PeakBias=" + x8PeakBias + " farSpacingBias=" + farSpacingBias);
	}

	@Test
	void coaxialCommandMapLoadBiasInterpolatesRuntimeLookup() throws ReflectiveOperationException {
		Method commandMapBias = DronePhysics.class.getDeclaredMethod(
				"coaxialCommandMapLoadBias",
				double.class,
				double.class
		);
		Method commandMapRatio = DronePhysics.class.getDeclaredMethod(
				"coaxialCommandMapAllocationRatio",
				double.class,
				double.class
		);
		Method commandMapMechanicalGain = DronePhysics.class.getDeclaredMethod(
				"coaxialCommandMapMechanicalGainPercent",
				double.class,
				double.class
		);
		Method commandMapElectricalGain = DronePhysics.class.getDeclaredMethod(
				"coaxialCommandMapElectricalGainPercent",
				double.class,
				double.class
		);
		Method commandMapUncertainty = DronePhysics.class.getDeclaredMethod(
				"coaxialCommandMapAllocationUncertaintyPercent",
				double.class,
				double.class
		);
		commandMapBias.setAccessible(true);
		commandMapRatio.setAccessible(true);
		commandMapMechanicalGain.setAccessible(true);
		commandMapElectricalGain.setAccessible(true);
		commandMapUncertainty.setAccessible(true);

		double lightLoadBias = (double) commandMapBias.invoke(null, 0.72, 0.35);
		double midLowLoadBias = (double) commandMapBias.invoke(null, 0.72, 0.45);
		double mediumLoadBias = (double) commandMapBias.invoke(null, 0.72, 0.60);
		double midHighLoadBias = (double) commandMapBias.invoke(null, 0.72, 0.75);
		double highLoadBias = (double) commandMapBias.invoke(null, 0.72, 0.85);
		double offSpacingBias = (double) commandMapBias.invoke(null, 0.55, 0.60);
		double mediumLoadRatio = (double) commandMapRatio.invoke(null, 0.72, 0.60);
		double offSpacingRatio = (double) commandMapRatio.invoke(null, 0.55, 0.60);
		double lowerPeakRatio = (double) commandMapRatio.invoke(null, 0.40, 0.60);
		double midLowMechanicalGain = (double) commandMapMechanicalGain.invoke(null, 0.72, 0.45);
		double lowerPeakMechanicalGain = (double) commandMapMechanicalGain.invoke(null, 0.40, 0.60);
		double valleyMechanicalGain = (double) commandMapMechanicalGain.invoke(null, 0.55, 0.60);
		double mediumElectricalGain = (double) commandMapElectricalGain.invoke(null, 0.72, 0.60);
		double lowerPeakElectricalGain = (double) commandMapElectricalGain.invoke(null, 0.40, 0.60);
		double lightUncertainty = (double) commandMapUncertainty.invoke(null, 0.72, 0.35);
		double mediumUncertainty = (double) commandMapUncertainty.invoke(null, 0.72, 0.60);

		assertEquals(0.06692, lightLoadBias, 1.0e-4);
		assertEquals(0.09791, midLowLoadBias, 1.0e-4);
		assertEquals(0.115, mediumLoadBias, 1.0e-3);
		assertEquals(0.10604, midHighLoadBias, 1.0e-4);
		assertEquals(0.08133, highLoadBias, 1.0e-4);
		assertEquals(1.3275111760369225, mediumLoadRatio, 1.0e-9);
		assertEquals(1.125445473618999, offSpacingRatio, 1.0e-9);
		assertEquals(1.1101294655127385, lowerPeakRatio, 1.0e-9);
		assertEquals(4.878985379626062, midLowMechanicalGain, 1.0e-9);
		assertEquals(7.286945739425876, lowerPeakMechanicalGain, 1.0e-9);
		assertEquals(2.585847358303339, valleyMechanicalGain, 1.0e-9);
		assertEquals(2.842254411676719, mediumElectricalGain, 1.0e-9);
		assertEquals(1.8619233726712814, lowerPeakElectricalGain, 1.0e-9);
		assertEquals(1.864380028955876, lightUncertainty, 1.0e-9);
		assertEquals(8.608417644018447, mediumUncertainty, 1.0e-9);
		assertTrue(mediumLoadBias > lightLoadBias + 0.045);
		assertTrue(mediumLoadBias > highLoadBias + 0.030);
		assertTrue(offSpacingBias < mediumLoadBias * 0.60);
		assertTrue(lowerPeakMechanicalGain > midLowMechanicalGain + 2.0);
		assertTrue(lowerPeakMechanicalGain > valleyMechanicalGain + 4.0);
	}

	@Test
	void coaxialCommandMapMechanicalGainScalesRealizedShaftPower() throws ReflectiveOperationException {
		Method powerScaleFormula = DronePhysics.class.getDeclaredMethod(
				"coaxialAllocationMechanicalPowerScale",
				double.class,
				double.class,
				double.class,
				double.class
		);
		Method runtimePowerScale = DronePhysics.class.getDeclaredMethod(
				"coaxialAllocationMechanicalPowerScale",
				int.class
		);
		powerScaleFormula.setAccessible(true);
		runtimePowerScale.setAccessible(true);

		double realizedScale = (double) powerScaleFormula.invoke(null, 4.405006058107876, 0.115, 0.115, 0.60);
		double clippedScale = (double) powerScaleFormula.invoke(null, 4.405006058107876, 0.115, 0.0575, 0.60);
		double inactiveScale = (double) powerScaleFormula.invoke(null, 4.405006058107876, 0.115, 0.0, 0.60);
		double lowLoadScale = (double) powerScaleFormula.invoke(null, 4.405006058107876, 0.115, 0.115, 0.10);

		assertEquals(0.9559499394189213, realizedScale, 1.0e-12);
		assertEquals(0.9779749697094606, clippedScale, 1.0e-12);
		assertEquals(1.0, inactiveScale, 1.0e-12);
		assertTrue(lowLoadScale > 0.997 && lowLoadScale < 1.0,
				() -> "lowLoadScale=" + lowLoadScale);

		DroneConfig config = withCoaxialX8Spacing(directControl(DroneConfig.coaxialX8())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0), 0.72);
		DronePhysics physics = new DronePhysics(config);
		DroneInput input = new DroneInput(config.hoverThrottle() + 0.16, 0.0, 0.0, 0.0, true);
		for (int i = 0; i < 700; i++) {
			holdInStillAir(physics);
			physics.step(input, 0.005);
		}

		double runtimeScale = (double) runtimePowerScale.invoke(physics, 0);
		double lowerRuntimeScale = (double) runtimePowerScale.invoke(physics, 1);
		double omega = physics.state().motorOmegaRadiansPerSecond(0);
		double aeroPower = physics.state().motorAerodynamicTorqueNewtonMeters(0) * omega;
		double mechanicalLossPower = physics.state().motorMechanicalLossTorqueNewtonMeters(0) * omega;
		double positiveInertiaPower = Math.max(
				0.0,
				config.rotors().get(0).rotorInertiaKgMetersSquared()
						* physics.state().motorAngularAccelerationRadiansPerSecondSquared(0)
						* omega
		);
		double expectedShaftPower = aeroPower + mechanicalLossPower + positiveInertiaPower;

		assertTrue(physics.state().maxRotorCoaxialAllocationMechanicalGainPercent() > 3.0);
		assertTrue(physics.state().maxAbsRotorCoaxialLoadBias() > 0.06);
		assertTrue(runtimeScale < 0.98 && runtimeScale > 0.94,
				() -> "runtimeScale=" + runtimeScale);
		assertEquals(runtimeScale, lowerRuntimeScale, 1.0e-9);
		assertTrue(aeroPower / runtimeScale > aeroPower + 1.0,
				() -> "aeroPower=" + aeroPower + " runtimeScale=" + runtimeScale);
		assertEquals(expectedShaftPower, physics.state().motorShaftPowerWatts(0), 1.0e-9);
	}

	@Test
	void coaxialCommandMapElectricalGainReducesRealizedMotorCurrent() throws ReflectiveOperationException {
		Method powerScaleFormula = DronePhysics.class.getDeclaredMethod(
				"coaxialAllocationElectricalPowerScale",
				double.class,
				double.class,
				double.class,
				double.class
		);
		Method efficiencyBonusFormula = DronePhysics.class.getDeclaredMethod(
				"coaxialAllocationElectricalEfficiencyBonus",
				double.class,
				double.class,
				double.class,
				double.class,
				double.class
		);
		Method runtimePowerScale = DronePhysics.class.getDeclaredMethod(
				"coaxialAllocationElectricalPowerScale",
				int.class
		);
		Method motorCurrentEstimate = DronePhysics.class.getDeclaredMethod(
				"estimateMotorCurrent",
				int.class
		);
		powerScaleFormula.setAccessible(true);
		efficiencyBonusFormula.setAccessible(true);
		runtimePowerScale.setAccessible(true);
		motorCurrentEstimate.setAccessible(true);

		double realizedScale = (double) powerScaleFormula.invoke(null, 2.842254411676719, 0.115, 0.115, 0.60);
		double clippedScale = (double) powerScaleFormula.invoke(null, 2.842254411676719, 0.115, 0.0575, 0.60);
		double inactiveScale = (double) powerScaleFormula.invoke(null, 2.842254411676719, 0.115, 0.0, 0.60);
		double lowLoadScale = (double) powerScaleFormula.invoke(null, 2.842254411676719, 0.115, 0.115, 0.10);
		double efficiencyBonus = (double) efficiencyBonusFormula.invoke(null, 0.76, 2.842254411676719, 0.115, 0.115, 0.60);

		assertEquals(0.9715774558832328, realizedScale, 1.0e-12);
		assertEquals(0.9857887279416164, clippedScale, 1.0e-12);
		assertEquals(1.0, inactiveScale, 1.0e-12);
		assertTrue(lowLoadScale > 0.998 && lowLoadScale < 1.0,
				() -> "lowLoadScale=" + lowLoadScale);
		assertEquals(0.0222330534718985, efficiencyBonus, 1.0e-12);

		DroneConfig config = withCoaxialX8Spacing(directControl(DroneConfig.coaxialX8())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0), 0.72);
		DronePhysics physics = new DronePhysics(config);
		DroneInput input = new DroneInput(config.hoverThrottle() + 0.16, 0.0, 0.0, 0.0, true);
		for (int i = 0; i < 700; i++) {
			holdInStillAir(physics);
			physics.step(input, 0.005);
		}

		double runtimeScale = (double) runtimePowerScale.invoke(physics, 0);
		Object withGainEstimate = motorCurrentEstimate.invoke(physics, 0);
		double withGainCurrent = recordDouble(withGainEstimate, "dischargeCurrentAmps");
		double withGainEfficiency = recordDouble(withGainEstimate, "electricalEfficiency");
		physics.state().setRotorCoaxialAllocationElectricalGainPercent(0, 0.0);
		Object withoutGainEstimate = motorCurrentEstimate.invoke(physics, 0);
		double withoutGainCurrent = recordDouble(withoutGainEstimate, "dischargeCurrentAmps");
		double withoutGainEfficiency = recordDouble(withoutGainEstimate, "electricalEfficiency");

		assertTrue(physics.state().maxRotorCoaxialAllocationElectricalGainPercent() > 2.0);
		assertTrue(runtimeScale < 0.985 && runtimeScale > 0.960,
				() -> "runtimeScale=" + runtimeScale);
		assertTrue(withGainEfficiency > withoutGainEfficiency + 0.015,
				() -> "withGainEfficiency=" + withGainEfficiency + " withoutGainEfficiency=" + withoutGainEfficiency);
		assertTrue(withGainCurrent < withoutGainCurrent - 0.10,
				() -> "withGainCurrent=" + withGainCurrent + " withoutGainCurrent=" + withoutGainCurrent);
	}

	@Test
	void coaxialX8RuntimeCommandMapPriorStrengthensCurrentSpacing() {
		DroneConfig base = directControl(DroneConfig.coaxialX8())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		double valleyBias = settledCoaxialLoadBias(withCoaxialX8Spacing(base, 0.55), 0.16);
		double currentSpacingBias = settledCoaxialLoadBias(withCoaxialX8Spacing(base, 0.72), 0.16);
		double farSpacingBias = settledCoaxialLoadBias(withCoaxialX8Spacing(base, 1.00), 0.16);

		assertTrue(currentSpacingBias > 0.080,
				() -> "currentSpacingBias=" + currentSpacingBias);
		assertTrue(currentSpacingBias > valleyBias + 0.030,
				() -> "currentSpacingBias=" + currentSpacingBias + " valleyBias=" + valleyBias);
		assertTrue(currentSpacingBias > farSpacingBias + 0.020,
				() -> "currentSpacingBias=" + currentSpacingBias + " farSpacingBias=" + farSpacingBias);
	}

	@Test
	void asymmetricStackedWakeSwirlAddsHubMoment() {
		PidGains passiveGains = new PidGains(0.0, 0.0, 0.0, 0.0);
		DroneConfig base = withCommonGains(directControl(DroneConfig.octoLift()), passiveGains)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withRotorImbalanceIntensity(0.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		RotorSpec template = base.rotors().get(0);
		double arm = 0.34;
		double upperY = template.radiusMeters() * 0.70;
		double lowerY = -upperY;
		List<RotorSpec> flatRotors = List.of(
				rotorLike(template, new Vec3(arm, 0.0, arm), 1),
				rotorLike(template, new Vec3(arm, 0.0, arm * 0.35), -1),
				rotorLike(template, new Vec3(-arm, 0.0, arm), -1),
				rotorLike(template, new Vec3(-arm, 0.0, -arm), 1),
				rotorLike(template, new Vec3(arm, 0.0, -arm), -1),
				rotorLike(template, new Vec3(0.0, 0.0, arm * 1.45), 1),
				rotorLike(template, new Vec3(-arm * 1.45, 0.0, 0.0), 1),
				rotorLike(template, new Vec3(0.0, 0.0, -arm * 1.45), -1)
		);
		List<RotorSpec> stackedRotors = List.of(
				rotorLike(template, new Vec3(arm, upperY, arm), 1),
				rotorLike(template, new Vec3(arm, lowerY, arm), -1),
				rotorLike(template, new Vec3(-arm, 0.0, arm), -1),
				rotorLike(template, new Vec3(-arm, 0.0, -arm), 1),
				rotorLike(template, new Vec3(arm, 0.0, -arm), -1),
				rotorLike(template, new Vec3(0.0, 0.0, arm * 1.45), 1),
				rotorLike(template, new Vec3(-arm * 1.45, 0.0, 0.0), 1),
				rotorLike(template, new Vec3(0.0, 0.0, -arm * 1.45), -1)
		);
		DronePhysics flat = new DronePhysics(base.withRotors(flatRotors));
		DronePhysics stacked = new DronePhysics(base.withRotors(stackedRotors));
		DroneInput input = new DroneInput(base.hoverThrottle() + 0.10, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 700; i++) {
			holdInStillAir(flat);
			holdInStillAir(stacked);
			flat.step(input, 0.005);
			stacked.step(input, 0.005);
		}

		assertEquals(0.0, flat.state().rotorWakeSwirlTorqueBodyNewtonMeters().length(), 1.0e-9);
		assertTrue(stacked.state().maxRotorWakeSwirlVelocityMetersPerSecond() > 0.30);
		assertTrue(stacked.state().rotorWakeSwirlTorqueBodyNewtonMeters().length() > 0.0025,
				() -> "wakeSwirlTorque=" + stacked.state().rotorWakeSwirlTorqueBodyNewtonMeters());
		assertTrue(stacked.state().rotorTorqueBodyNewtonMeters(1).length()
				> flat.state().rotorTorqueBodyNewtonMeters(1).length() + 0.0020);
		assertEquals(0.0, stacked.state().maxAbsRotorCoaxialLoadBias(), 1.0e-9);
	}

	@Test
	void rotorWakeInterferenceBuildsAndReleasesWithWakeLag() {
		DroneConfig config = directControl(DroneConfig.coaxialX8())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(29.6, 29.5, 0.0, 20.0, 220.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput powered = new DroneInput(config.hoverThrottle() + 0.08, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 8; i++) {
			holdInStillAir(physics);
			physics.step(powered, 0.005);
		}
		double earlyWake = physics.state().maxRotorWakeInterferenceIntensity();
		double earlySwirl = physics.state().maxRotorWakeSwirlVelocityMetersPerSecond();

		for (int i = 0; i < 700; i++) {
			holdInStillAir(physics);
			physics.step(powered, 0.005);
		}
		double settledWake = physics.state().maxRotorWakeInterferenceIntensity();
		double settledSwirl = physics.state().maxRotorWakeSwirlVelocityMetersPerSecond();

		holdInStillAir(physics);
		physics.step(DroneInput.idle(), 0.005);
		double lingeringWake = physics.state().maxRotorWakeInterferenceIntensity();
		double lingeringSwirl = physics.state().maxRotorWakeSwirlVelocityMetersPerSecond();

		for (int i = 0; i < 480; i++) {
			holdInStillAir(physics);
			physics.step(DroneInput.idle(), 0.005);
		}
		double clearedWake = physics.state().maxRotorWakeInterferenceIntensity();
		double clearedSwirl = physics.state().maxRotorWakeSwirlVelocityMetersPerSecond();

		assertTrue(earlyWake < settledWake * 0.60,
				() -> "earlyWake=" + earlyWake + " settledWake=" + settledWake);
		assertTrue(earlySwirl < settledSwirl * 0.60,
				() -> "earlySwirl=" + earlySwirl + " settledSwirl=" + settledSwirl);
		assertTrue(settledWake > 0.30, () -> "settledWake=" + settledWake);
		assertTrue(settledSwirl > 0.30, () -> "settledSwirl=" + settledSwirl);
		assertTrue(lingeringWake > clearedWake + 0.12,
				() -> "lingeringWake=" + lingeringWake + " clearedWake=" + clearedWake);
		assertTrue(lingeringSwirl > clearedSwirl + 0.12,
				() -> "lingeringSwirl=" + lingeringSwirl + " clearedSwirl=" + clearedSwirl);
		assertTrue(clearedWake < 0.04, () -> "clearedWake=" + clearedWake);
		assertTrue(clearedSwirl < 0.04, () -> "clearedSwirl=" + clearedSwirl);
	}

	@Test
	void forwardFlightConvectsFrontRotorWakeOntoRearRotors() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorStallThrustLossCoefficient(0.0)
				.withRotorImbalanceIntensity(0.0);
		DronePhysics hover = new DronePhysics(config);
		DronePhysics forward = new DronePhysics(config);
		DroneInput input = new DroneInput(config.hoverThrottle() + 0.10, 0.0, 0.0, 0.0, true);
		Vec3 forwardVelocity = new Vec3(0.0, 0.0, 22.0);

		for (int i = 0; i < 700; i++) {
			holdInStillAir(hover);
			holdInCruise(forward, forwardVelocity);
			hover.step(input, 0.005);
			forward.step(input, 0.005);
		}

		double frontThrustSum = 0.0;
		double rearThrustSum = 0.0;
		int thrustSamples = 0;
		for (int i = 0; i < 160; i++) {
			holdInStillAir(hover);
			holdInCruise(forward, forwardVelocity);
			hover.step(input, 0.005);
			forward.step(input, 0.005);
			frontThrustSum += 0.5 * (
					forward.state().rotorThrustNewtons(0)
							+ forward.state().rotorThrustNewtons(1)
			);
			rearThrustSum += 0.5 * (
					forward.state().rotorThrustNewtons(2)
							+ forward.state().rotorThrustNewtons(3)
			);
			thrustSamples++;
		}

		double frontWake = 0.5 * (
				forward.state().rotorWakeInterferenceIntensity(0)
						+ forward.state().rotorWakeInterferenceIntensity(1)
		);
		double rearWake = 0.5 * (
				forward.state().rotorWakeInterferenceIntensity(2)
						+ forward.state().rotorWakeInterferenceIntensity(3)
		);
		double frontWakeThrustScale = 0.5 * (
				forward.state().rotorWakeThrustScale(0)
						+ forward.state().rotorWakeThrustScale(1)
		);
		double rearWakeThrustScale = 0.5 * (
				forward.state().rotorWakeThrustScale(2)
						+ forward.state().rotorWakeThrustScale(3)
		);
		double frontThrust = frontThrustSum / thrustSamples;
		double rearThrust = rearThrustSum / thrustSamples;

		assertEquals(0.0, hover.state().maxRotorWakeInterferenceIntensity(), 0.02);
		assertTrue(frontWake < 0.03, () -> "frontWake=" + frontWake + " rearWake=" + rearWake);
		assertTrue(rearWake > 0.055, () -> "frontWake=" + frontWake + " rearWake=" + rearWake);
		assertTrue(rearWake > frontWake + 0.050, () -> "frontWake=" + frontWake + " rearWake=" + rearWake);
		assertTrue(forward.state().maxRotorWakeSwirlVelocityMetersPerSecond() > 0.08);
		assertTrue(rearWakeThrustScale < frontWakeThrustScale - 0.010,
				() -> "frontWakeThrustScale=" + frontWakeThrustScale
						+ " rearWakeThrustScale=" + rearWakeThrustScale
						+ " frontThrust=" + frontThrust
						+ " rearThrust=" + rearThrust);
	}

	@Test
	void inertiaTuningChangesRateResponse() {
		DroneConfig base = directControl(DroneConfig.racingQuad());
		DronePhysics lightInertia = new DronePhysics(base.withInertiaKgMetersSquared(base.inertiaKgMetersSquared().multiply(0.55)));
		DronePhysics heavyInertia = new DronePhysics(base.withInertiaKgMetersSquared(base.inertiaKgMetersSquared().multiply(1.8)));
		DroneInput input = new DroneInput(0.5, 0.0, 0.65, 0.0, true);

		for (int i = 0; i < 80; i++) {
			lightInertia.step(input, 0.0025);
			heavyInertia.step(input, 0.0025);
		}

		assertTrue(lightInertia.state().angularVelocityBodyRadiansPerSecond().z() > heavyInertia.state().angularVelocityBodyRadiansPerSecond().z() * 1.45);
	}

	@Test
	void centerOfMassOffsetChangesRotorLeverArmsAndHoverTrim() {
		DroneConfig centeredConfig = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DroneConfig forwardCgConfig = centeredConfig.withCenterOfMassOffsetBodyMeters(new Vec3(0.0, 0.0, 0.045));
		DronePhysics centered = new DronePhysics(centeredConfig);
		DronePhysics forwardCg = new DronePhysics(forwardCgConfig);
		DroneInput hover = new DroneInput(centeredConfig.hoverThrottle(), 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 220; i++) {
			centered.step(hover, 0.0025);
			forwardCg.step(hover, 0.0025);
		}

		assertEquals(0.045, forwardCg.config().centerOfMassOffsetBodyMeters().z(), 1.0e-9);
		assertTrue(Math.abs(forwardCg.state().angularVelocityBodyRadiansPerSecond().x())
				> Math.abs(centered.state().angularVelocityBodyRadiansPerSecond().x()) + 0.25);
	}

	@Test
	void imuOffsetAddsLeverArmAccelerationToAccelerometer() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		Vec3 imuOffset = new Vec3(0.060, -0.025, 0.040);
		Vec3 omega = new Vec3(3.0, -4.0, 5.0);
		Vec3 angularAcceleration = new Vec3(12.0, -8.0, 6.0);
		DronePhysics centered = new DronePhysics(base);
		DronePhysics offset = new DronePhysics(base);

		centered.state().setLinearAccelerationWorldMetersPerSecondSquared(Vec3.ZERO);
		centered.state().setAngularVelocityBodyRadiansPerSecond(omega);
		centered.state().setAngularAccelerationBodyRadiansPerSecondSquared(angularAcceleration);
		centered.applyConfig(base.withFlightControllerSensors(999.0, 0.0, 1000.0, 0.0, 0.0));

		offset.state().setLinearAccelerationWorldMetersPerSecondSquared(Vec3.ZERO);
		offset.state().setAngularVelocityBodyRadiansPerSecond(omega);
		offset.state().setAngularAccelerationBodyRadiansPerSecondSquared(angularAcceleration);
		offset.applyConfig(base.withImuOffsetBodyMeters(imuOffset));

		Vec3 expectedDelta = angularAcceleration.cross(imuOffset)
				.add(omega.cross(omega.cross(imuOffset)));
		Vec3 measuredDelta = offset.state().accelerometerBodyMetersPerSecondSquared()
				.subtract(centered.state().accelerometerBodyMetersPerSecondSquared());

		assertEquals(expectedDelta.x(), measuredDelta.x(), 1.0e-9);
		assertEquals(expectedDelta.y(), measuredDelta.y(), 1.0e-9);
		assertEquals(expectedDelta.z(), measuredDelta.z(), 1.0e-9);
		assertTrue(measuredDelta.length() > 0.5);
	}

	@Test
	void motorTimeConstantTuningChangesSpoolResponse() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withBattery(16.8, 16.7, 0.0, 20.0, 220.0);
		DronePhysics fastMotors = new DronePhysics(base.withMotorTimeConstantSeconds(0.018));
		DronePhysics slowMotors = new DronePhysics(base.withMotorTimeConstantSeconds(0.18));
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 8; i++) {
			fastMotors.step(punch, 0.0025);
			slowMotors.step(punch, 0.0025);
		}

		assertTrue(fastMotors.state().averageMotorPower(fastMotors.config()) > slowMotors.state().averageMotorPower(slowMotors.config()) + 0.35);
	}

	@Test
	void motorActuatorTrackingTelemetryShowsSpoolLagAndRecovery() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.20)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 220.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		physics.step(punch, 0.005);
		double initialTrackingError = physics.state().averageMotorTrackingError();
		double initialAuthority = physics.state().averageMotorActuatorAuthority();
		double targetRpm = physics.state().averageMotorTargetRpm();

		for (int i = 0; i < 260; i++) {
			physics.step(punch, 0.005);
		}

		double settledTrackingError = physics.state().averageMotorTrackingError();
		double settledAuthority = physics.state().averageMotorActuatorAuthority();
		assertTrue(targetRpm > physics.state().averageMotorRpm(),
				() -> "targetRpm=" + targetRpm + " settledRpm=" + physics.state().averageMotorRpm());
		assertTrue(initialTrackingError > settledTrackingError + 0.35,
				() -> "initialTrackingError=" + initialTrackingError + " settledTrackingError=" + settledTrackingError);
		assertTrue(initialAuthority < 0.20, () -> "initialAuthority=" + initialAuthority);
		assertTrue(settledAuthority > Math.max(0.50, initialAuthority + 0.40),
				() -> "initialAuthority=" + initialAuthority
						+ " settledAuthority=" + settledAuthority
						+ " settledTrackingError=" + settledTrackingError);
	}

	@Test
	void motorMechanicalLossRisesWithRotorSpeedAndLoadsShaftPower() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics lowThrottle = new DronePhysics(config);
		DronePhysics highThrottle = new DronePhysics(config);

		for (int i = 0; i < 80; i++) {
			lowThrottle.step(new DroneInput(0.22, 0.0, 0.0, 0.0, true), 0.005);
			highThrottle.step(new DroneInput(0.86, 0.0, 0.0, 0.0, true), 0.005);
		}

		double lowLoss = lowThrottle.state().averageMotorMechanicalLossTorqueNewtonMeters();
		double highLoss = highThrottle.state().averageMotorMechanicalLossTorqueNewtonMeters();
		double omega = highThrottle.state().motorOmegaRadiansPerSecond(0);
		double loadedPower = highThrottle.state().motorShaftPowerWatts(0);
		double expectedMechanicalLossPower = highThrottle.state().motorMechanicalLossTorqueNewtonMeters(0) * omega;

		assertTrue(highLoss > 0.0008);
		assertTrue(highLoss > lowLoss * 1.8);
		assertTrue(loadedPower > expectedMechanicalLossPower);
	}

	@Test
	void damagedPropAddsMechanicalProfileDrag() throws ReflectiveOperationException {
		Method mechanicalLoss = DronePhysics.class.getDeclaredMethod(
				"motorMechanicalLossTorque",
				RotorSpec.class,
				double.class,
				double.class,
				double.class,
				double.class,
				double.class,
				double.class,
				double.class
		);
		mechanicalLoss.setAccessible(true);
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0).withImbalanceIntensity(0.0);
		double omega = rotor.maxOmegaRadiansPerSecond() * 0.78;

		double cleanLoss = (double) mechanicalLoss.invoke(null, rotor, omega, 1.0, 25.0, 0.0, 0.0, 0.0, 1.0);
		double mildLoss = (double) mechanicalLoss.invoke(null, rotor, omega, 1.0, 25.0, 0.0, 0.0, 0.0, 0.90);
		double bentLoss = (double) mechanicalLoss.invoke(null, rotor, omega, 1.0, 25.0, 0.0, 0.0, 0.0, 0.45);
		double severeLoss = (double) mechanicalLoss.invoke(null, rotor, omega, 1.0, 25.0, 0.0, 0.0, 0.0, 0.15);

		assertTrue(mildLoss > cleanLoss * 1.07,
				() -> "cleanLoss=" + cleanLoss + " mildLoss=" + mildLoss);
		assertTrue(bentLoss > cleanLoss + 0.0010,
				() -> "cleanLoss=" + cleanLoss + " bentLoss=" + bentLoss);
		assertTrue(severeLoss > bentLoss * 1.35,
				() -> "bentLoss=" + bentLoss + " severeLoss=" + severeLoss);
	}

	@Test
	void coldMotorBearingsIncreaseStartupDragAndMechanicalLoss() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 180.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics warmBearings = new DronePhysics(config);
		DronePhysics coldBearings = new DronePhysics(config);
		DroneInput lowStartup = new DroneInput(0.006, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < config.rotors().size(); i++) {
			warmBearings.state().setMotorTemperatureCelsius(i, 35.0);
			coldBearings.state().setMotorTemperatureCelsius(i, -20.0);
		}

		for (int i = 0; i < 90; i++) {
			warmBearings.step(lowStartup, 0.005);
			coldBearings.step(lowStartup, 0.005);
		}

		assertTrue(coldBearings.state().averageMotorMechanicalLossTorqueNewtonMeters()
						> warmBearings.state().averageMotorMechanicalLossTorqueNewtonMeters() * 1.18,
				() -> "warmLoss=" + warmBearings.state().averageMotorMechanicalLossTorqueNewtonMeters()
						+ " coldLoss=" + coldBearings.state().averageMotorMechanicalLossTorqueNewtonMeters());
		assertTrue(coldBearings.state().averageMotorRpm() < warmBearings.state().averageMotorRpm() - 80.0,
				() -> "warmRpm=" + warmBearings.state().averageMotorRpm()
						+ " coldRpm=" + coldBearings.state().averageMotorRpm());
		assertTrue(coldBearings.state().averageMotorTargetRpm() < warmBearings.state().averageMotorTargetRpm() - 80.0,
				() -> "warmTargetRpm=" + warmBearings.state().averageMotorTargetRpm()
						+ " coldTargetRpm=" + coldBearings.state().averageMotorTargetRpm());
	}

	@Test
	void motorCommutationRippleAddsPhaseCurrentAndTorqueTelemetry() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput cruise = new DroneInput(0.62, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 120; i++) {
			physics.step(cruise, 0.005);
		}

		assertTrue(physics.state().averageMotorCommutationRippleIntensity() > 0.005);
		assertTrue(physics.state().averageMotorPhaseCurrentAmps() > 0.05);
		assertTrue(physics.state().averageMotorCurrentRippleAmps() > 0.005);
		assertTrue(physics.state().averageMotorTorqueRippleNewtonMeters() > 1.0e-6);
		assertTrue(physics.state().rotorVibration() > 0.0);

		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig torquePathConfig = config
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorImbalanceIntensity(0.0);
		DronePhysics torquePath = new DronePhysics(torquePathConfig);
		torquePath.step(new DroneInput(0.74, 0.0, 0.0, 0.0, true), 0.005);

		RotorSpec rotor = torquePath.config().rotors().get(0);
		double rippleTorque = torquePath.state().motorTorqueRippleNewtonMeters(0);
		double aerodynamicTorque = torquePath.state().motorAerodynamicTorqueNewtonMeters(0);
		double aerodynamicOnlyReactionTorque = rotor.spinDirection() * aerodynamicTorque;
		double expectedReactionTorque = rotor.spinDirection() * (aerodynamicTorque + rippleTorque);
		double actualReactionTorque = torquePath.state().rotorTorqueBodyNewtonMeters(0).y();

		assertTrue(Math.abs(rippleTorque) > 1.0e-7, () -> "rippleTorque=" + rippleTorque);
		assertEquals(expectedReactionTorque, actualReactionTorque, 1.0e-7);
		assertTrue(
				Math.abs(actualReactionTorque - aerodynamicOnlyReactionTorque) > Math.abs(rippleTorque) * 0.90,
				() -> "actualReactionTorque=" + actualReactionTorque
						+ " aerodynamicOnlyReactionTorque=" + aerodynamicOnlyReactionTorque
						+ " rippleTorque=" + rippleTorque
		);
	}

	@Test
	void bladePassRippleAddsSmallThrustForceTexture() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorImbalanceIntensity(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics obstructed = new DronePhysics(config);
		DroneInput cruise = new DroneInput(0.66, 0.0, 0.0, 0.0, true);
		DroneEnvironment singleRotorObstruction = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {0.38, 0.0, 0.0, 0.0}
		);

		double cleanMinForce = Double.POSITIVE_INFINITY;
		double cleanMaxForce = Double.NEGATIVE_INFINITY;
		double obstructedMinForce = Double.POSITIVE_INFINITY;
		double obstructedMaxForce = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < 180; i++) {
			clean.state().setOrientation(Quaternion.IDENTITY);
			obstructed.state().setOrientation(Quaternion.IDENTITY);
			clean.state().setVelocityMetersPerSecond(Vec3.ZERO);
			obstructed.state().setVelocityMetersPerSecond(Vec3.ZERO);
			clean.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			obstructed.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			clean.step(cruise, 0.005);
			obstructed.step(cruise, 0.005, singleRotorObstruction);

			if (i >= 80) {
				double cleanForce = clean.state().rotorForceBodyNewtons(0).y();
				double obstructedForce = obstructed.state().rotorForceBodyNewtons(0).y();
				cleanMinForce = Math.min(cleanMinForce, cleanForce);
				cleanMaxForce = Math.max(cleanMaxForce, cleanForce);
				obstructedMinForce = Math.min(obstructedMinForce, obstructedForce);
				obstructedMaxForce = Math.max(obstructedMaxForce, obstructedForce);
			}
		}

		double cleanForceRange = cleanMaxForce - cleanMinForce;
		double obstructedForceRange = obstructedMaxForce - obstructedMinForce;
		assertTrue(cleanForceRange > 0.025, () -> "cleanForceRange=" + cleanForceRange);
		assertTrue(
				obstructedForceRange > cleanForceRange * 1.35,
				() -> "cleanForceRange=" + cleanForceRange + " obstructedForceRange=" + obstructedForceRange
		);
		assertTrue(clean.state().rotorVibration() > 0.002, () -> "clean vibration=" + clean.state().rotorVibration());
		assertTrue(obstructed.state().rotorVibration() > clean.state().rotorVibration() + 0.006);
		assertTrue(clean.state().averageRotorBladePassRippleIntensity() > 0.003);
		assertTrue(obstructed.state().rotorBladePassRippleIntensity(0)
				> clean.state().rotorBladePassRippleIntensity(0) + 0.003);
	}

	@Test
	void rotorConingReducesEffectiveHighLoadThrust() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorImbalanceIntensity(0.0)
				.withRotorStallThrustLossCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics lowLoad = new DronePhysics(config);
		DronePhysics highLoad = new DronePhysics(config);
		DroneInput lowThrottle = new DroneInput(0.36, 0.0, 0.0, 0.0, true);
		DroneInput highThrottle = new DroneInput(0.96, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 180; i++) {
			lowLoad.state().setOrientation(Quaternion.IDENTITY);
			highLoad.state().setOrientation(Quaternion.IDENTITY);
			lowLoad.state().setVelocityMetersPerSecond(Vec3.ZERO);
			highLoad.state().setVelocityMetersPerSecond(Vec3.ZERO);
			lowLoad.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			highLoad.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			lowLoad.step(lowThrottle, 0.005);
			highLoad.step(highThrottle, 0.005);
		}

		RotorSpec rotor = config.rotors().get(0);
		double lowIdealThrust = rotor.thrustCoefficient()
				* lowLoad.state().motorOmegaRadiansPerSecond(0)
				* lowLoad.state().motorOmegaRadiansPerSecond(0);
		double highIdealThrust = rotor.thrustCoefficient()
				* highLoad.state().motorOmegaRadiansPerSecond(0)
				* highLoad.state().motorOmegaRadiansPerSecond(0);
		double lowEffectiveThrustRatio = lowLoad.state().rotorThrustNewtons(0) / lowIdealThrust;
		double highEffectiveThrustRatio = highLoad.state().rotorThrustNewtons(0) / highIdealThrust;

		assertTrue(highLoad.state().rotorThrustNewtons(0) > lowLoad.state().rotorThrustNewtons(0) * 2.2,
				() -> "lowThrust=" + lowLoad.state().rotorThrustNewtons(0)
						+ " highThrust=" + highLoad.state().rotorThrustNewtons(0));
		assertTrue(highEffectiveThrustRatio < lowEffectiveThrustRatio - 0.018,
				() -> "lowRatio=" + lowEffectiveThrustRatio + " highRatio=" + highEffectiveThrustRatio);
		assertTrue(highLoad.state().averageRotorAerodynamicLoadFactor()
				> lowLoad.state().averageRotorAerodynamicLoadFactor() + 0.015,
				() -> "lowLoad=" + lowLoad.state().averageRotorAerodynamicLoadFactor()
						+ " highLoad=" + highLoad.state().averageRotorAerodynamicLoadFactor());
		assertTrue(highLoad.state().rotorVibration() > lowLoad.state().rotorVibration() + 0.006,
				() -> "lowVibration=" + lowLoad.state().rotorVibration()
						+ " highVibration=" + highLoad.state().rotorVibration());
		double lowConingAngleDegrees = Math.toDegrees(lowLoad.state().maxRotorConingAngleRadians());
		double highConingAngleDegrees = Math.toDegrees(highLoad.state().maxRotorConingAngleRadians());
		assertTrue(highConingAngleDegrees > lowConingAngleDegrees + 0.50,
				() -> "lowConingAngle=" + lowConingAngleDegrees + " highConingAngle=" + highConingAngleDegrees);
		assertTrue(highConingAngleDegrees > 0.80 && highConingAngleDegrees < 2.60,
				() -> "highConingAngle=" + highConingAngleDegrees);
	}

	@Test
	void rotorConingBuildsAfterHighLoadStep() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorImbalanceIntensity(0.0)
				.withRotorStallThrustLossCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput highThrottle = new DroneInput(0.96, 0.0, 0.0, 0.0, true);
		double earlyRatio = 0.0;
		double earlyConing = 0.0;
		int earlySamples = 0;
		double settledRatio = 0.0;
		double settledConing = 0.0;
		int settledSamples = 0;

		for (int i = 0; i < 260; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.step(highThrottle, 0.0025);
			double ratio = rotorEffectiveThrustRatio(physics, 0);
			if (i >= 1 && i < 8) {
				earlyRatio += ratio;
				earlyConing += physics.state().averageRotorConingIntensity();
				earlySamples++;
			}
			if (i >= 210) {
				settledRatio += ratio;
				settledConing += physics.state().averageRotorConingIntensity();
				settledSamples++;
			}
		}

		earlyRatio /= earlySamples;
		earlyConing /= earlySamples;
		settledRatio /= settledSamples;
		settledConing /= settledSamples;
		double observedEarlyRatio = earlyRatio;
		double observedSettledRatio = settledRatio;
		double observedEarlyConing = earlyConing;
		double observedSettledConing = settledConing;
		assertTrue(observedSettledConing > observedEarlyConing + 0.035,
				() -> "earlyConing=" + observedEarlyConing + " settledConing=" + observedSettledConing);
		assertTrue(observedEarlyRatio > observedSettledRatio + 0.001,
				() -> "earlyRatio=" + observedEarlyRatio + " settledRatio=" + observedSettledRatio);
	}

	@Test
	void rotorConingFlexLingersAfterThrottleChop() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorImbalanceIntensity(0.0)
				.withRotorStallThrustLossCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput highThrottle = new DroneInput(0.96, 0.0, 0.0, 0.0, true);
		DroneInput chop = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 180; i++) {
			holdInStillAir(physics);
			physics.step(highThrottle, 0.0025);
		}
		double loadedConing = physics.state().averageRotorConingIntensity();

		holdInStillAir(physics);
		physics.step(chop, 0.0025);
		double firstChopConing = physics.state().averageRotorConingIntensity();

		for (int i = 0; i < 160; i++) {
			holdInStillAir(physics);
			physics.step(chop, 0.0025);
		}
		double clearedConing = physics.state().averageRotorConingIntensity();

		assertTrue(loadedConing > 0.40, () -> "loadedConing=" + loadedConing);
		assertTrue(firstChopConing > clearedConing + 0.20,
				() -> "firstChopConing=" + firstChopConing + " clearedConing=" + clearedConing);
		assertTrue(clearedConing < 0.04, () -> "clearedConing=" + clearedConing);
	}

	@Test
	void rotorImbalanceRaisesVibrationAndCurrentRipple() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withRotorImbalanceIntensity(0.0)
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics balanced = new DronePhysics(base);
		DronePhysics imbalanced = new DronePhysics(base.withRotorImbalanceIntensity(0.18));
		DroneInput cruise = new DroneInput(0.62, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 140; i++) {
			balanced.step(cruise, 0.005);
			imbalanced.step(cruise, 0.005);
		}

		assertEquals(0.18, imbalanced.config().averageRotorImbalanceIntensity(), 1.0e-9);
		assertTrue(imbalanced.state().rotorVibration() > balanced.state().rotorVibration() + 0.08);
		assertTrue(imbalanced.state().averageMotorCurrentRippleAmps() > balanced.state().averageMotorCurrentRippleAmps() + 0.08);
		assertTrue(imbalanced.state().gyroDynamicNotchAttenuation() > balanced.state().gyroDynamicNotchAttenuation() + 0.10);
		assertTrue(imbalanced.state().averageMotorMechanicalLossTorqueNewtonMeters() > balanced.state().averageMotorMechanicalLossTorqueNewtonMeters());
	}

	@Test
	void rotorImbalanceInjectsRotatingLateralForceIntoAirframe() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withRotorImbalanceIntensity(0.0)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics balanced = new DronePhysics(base);
		DronePhysics imbalanced = new DronePhysics(base.withRotorImbalanceIntensity(0.18));
		DroneInput cruise = new DroneInput(0.62, 0.0, 0.0, 0.0, true);
		double balancedMaxLateralForce = 0.0;
		double imbalancedMaxLateralForce = 0.0;
		double balancedMinYawTorque = Double.POSITIVE_INFINITY;
		double balancedMaxYawTorque = Double.NEGATIVE_INFINITY;
		double imbalancedMinYawTorque = Double.POSITIVE_INFINITY;
		double imbalancedMaxYawTorque = Double.NEGATIVE_INFINITY;

		for (int i = 0; i < 180; i++) {
			holdInStillAir(balanced);
			holdInStillAir(imbalanced);
			balanced.step(cruise, 0.005);
			imbalanced.step(cruise, 0.005);

			if (i >= 80) {
				Vec3 balancedForce = balanced.state().rotorForceBodyNewtons(0);
				Vec3 imbalancedForce = imbalanced.state().rotorForceBodyNewtons(0);
				balancedMaxLateralForce = Math.max(
						balancedMaxLateralForce,
						Math.hypot(balancedForce.x(), balancedForce.z())
				);
				imbalancedMaxLateralForce = Math.max(
						imbalancedMaxLateralForce,
						Math.hypot(imbalancedForce.x(), imbalancedForce.z())
				);
				double balancedYawTorque = balanced.state().rotorTorqueBodyNewtonMeters(0).y();
				double imbalancedYawTorque = imbalanced.state().rotorTorqueBodyNewtonMeters(0).y();
				balancedMinYawTorque = Math.min(balancedMinYawTorque, balancedYawTorque);
				balancedMaxYawTorque = Math.max(balancedMaxYawTorque, balancedYawTorque);
				imbalancedMinYawTorque = Math.min(imbalancedMinYawTorque, imbalancedYawTorque);
				imbalancedMaxYawTorque = Math.max(imbalancedMaxYawTorque, imbalancedYawTorque);
			}
		}

		double balancedYawRange = balancedMaxYawTorque - balancedMinYawTorque;
		double imbalancedYawRange = imbalancedMaxYawTorque - imbalancedMinYawTorque;
		double finalBalancedMaxLateralForce = balancedMaxLateralForce;
		double finalImbalancedMaxLateralForce = imbalancedMaxLateralForce;
		assertTrue(finalBalancedMaxLateralForce < 0.05,
				() -> "balancedMaxLateralForce=" + finalBalancedMaxLateralForce);
		assertTrue(finalImbalancedMaxLateralForce > finalBalancedMaxLateralForce + 0.06,
				() -> "balancedMaxLateralForce=" + finalBalancedMaxLateralForce
						+ " imbalancedMaxLateralForce=" + finalImbalancedMaxLateralForce);
		assertTrue(imbalancedYawRange > balancedYawRange + 0.010,
				() -> "balancedYawRange=" + balancedYawRange + " imbalancedYawRange=" + imbalancedYawRange);
	}

	@Test
	void damagedRotorBehavesLikeBentPropImbalance() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withRotorImbalanceIntensity(0.0)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics clean = new DronePhysics(base);
		DronePhysics bentProp = new DronePhysics(base);
		bentProp.state().damageRotor(0, 0.55);
		DroneInput cruise = new DroneInput(0.72, 0.0, 0.0, 0.0, true);
		double cleanMinForceX = Double.POSITIVE_INFINITY;
		double cleanMaxForceX = Double.NEGATIVE_INFINITY;
		double cleanMinForceZ = Double.POSITIVE_INFINITY;
		double cleanMaxForceZ = Double.NEGATIVE_INFINITY;
		double bentPropMinForceX = Double.POSITIVE_INFINITY;
		double bentPropMaxForceX = Double.NEGATIVE_INFINITY;
		double bentPropMinForceZ = Double.POSITIVE_INFINITY;
		double bentPropMaxForceZ = Double.NEGATIVE_INFINITY;
		double cleanMaxCurrentRipple = 0.0;
		double bentPropMaxCurrentRipple = 0.0;
		double cleanMaxCommutationRipple = 0.0;
		double bentPropMaxCommutationRipple = 0.0;

		for (int i = 0; i < 200; i++) {
			holdInStillAir(clean);
			holdInStillAir(bentProp);
			clean.step(cruise, 0.005);
			bentProp.step(cruise, 0.005);

			if (i >= 90) {
				Vec3 cleanForce = clean.state().rotorForceBodyNewtons(0);
				Vec3 bentPropForce = bentProp.state().rotorForceBodyNewtons(0);
				cleanMinForceX = Math.min(cleanMinForceX, cleanForce.x());
				cleanMaxForceX = Math.max(cleanMaxForceX, cleanForce.x());
				cleanMinForceZ = Math.min(cleanMinForceZ, cleanForce.z());
				cleanMaxForceZ = Math.max(cleanMaxForceZ, cleanForce.z());
				bentPropMinForceX = Math.min(bentPropMinForceX, bentPropForce.x());
				bentPropMaxForceX = Math.max(bentPropMaxForceX, bentPropForce.x());
				bentPropMinForceZ = Math.min(bentPropMinForceZ, bentPropForce.z());
				bentPropMaxForceZ = Math.max(bentPropMaxForceZ, bentPropForce.z());
				cleanMaxCurrentRipple = Math.max(cleanMaxCurrentRipple, clean.state().motorCurrentRippleAmps(0));
				bentPropMaxCurrentRipple = Math.max(bentPropMaxCurrentRipple, bentProp.state().motorCurrentRippleAmps(0));
				cleanMaxCommutationRipple = Math.max(cleanMaxCommutationRipple, clean.state().motorCommutationRippleIntensity(0));
				bentPropMaxCommutationRipple = Math.max(bentPropMaxCommutationRipple, bentProp.state().motorCommutationRippleIntensity(0));
			}
		}

		double cleanLateralComponentRange = cleanMaxForceX - cleanMinForceX + cleanMaxForceZ - cleanMinForceZ;
		double bentPropLateralComponentRange = bentPropMaxForceX - bentPropMinForceX + bentPropMaxForceZ - bentPropMinForceZ;
		double finalCleanMaxCurrentRipple = cleanMaxCurrentRipple;
		double finalBentPropMaxCurrentRipple = bentPropMaxCurrentRipple;
		double finalCleanMaxCommutationRipple = cleanMaxCommutationRipple;
		double finalBentPropMaxCommutationRipple = bentPropMaxCommutationRipple;
		assertTrue(bentPropLateralComponentRange > cleanLateralComponentRange + 0.035,
				() -> "cleanLateralComponentRange=" + cleanLateralComponentRange
						+ " bentPropLateralComponentRange=" + bentPropLateralComponentRange);
		assertTrue(finalBentPropMaxCommutationRipple > finalCleanMaxCommutationRipple + 0.006,
				() -> "cleanCommutation=" + finalCleanMaxCommutationRipple
						+ " bentPropCommutation=" + finalBentPropMaxCommutationRipple);
		assertTrue(finalBentPropMaxCurrentRipple > finalCleanMaxCurrentRipple + 0.020,
				() -> "cleanCurrentRipple=" + finalCleanMaxCurrentRipple
						+ " bentPropCurrentRipple=" + finalBentPropMaxCurrentRipple);
		assertTrue(bentProp.state().rotorVibration() > clean.state().rotorVibration() + 0.035,
				() -> "cleanVibration=" + clean.state().rotorVibration()
						+ " bentPropVibration=" + bentProp.state().rotorVibration());
		assertEquals(0.0, clean.state().maxRotorDamageVibration(), 1.0e-9);
		assertTrue(bentProp.state().rotorDamageVibration(0) > bentProp.state().rotorDamageVibration(1) + 0.02,
				() -> "rotor0DamageVibration=" + bentProp.state().rotorDamageVibration(0)
						+ " rotor1DamageVibration=" + bentProp.state().rotorDamageVibration(1));
		assertEquals(bentProp.state().rotorDamageVibration(0), bentProp.state().maxRotorDamageVibration(), 1.0e-9);
	}

	@Test
	void rotorDamageVibrationKeepsLightFaultsModest() throws ReflectiveOperationException {
		Method damageVibration = DronePhysics.class.getDeclaredMethod(
				"rotorDamageVibration",
				RotorSpec.class,
				double.class,
				double.class
		);
		damageVibration.setAccessible(true);
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0);
		double maxOmega = rotor.maxOmegaRadiansPerSecond();

		double mildFault = (double) damageVibration.invoke(null, rotor, maxOmega, 0.90);
		double moderateFault = (double) damageVibration.invoke(null, rotor, maxOmega, 0.75);
		double heavyFault = (double) damageVibration.invoke(null, rotor, maxOmega, 0.50);
		double severeFault = (double) damageVibration.invoke(null, rotor, maxOmega, 0.25);
		double halfSpeedMildFault = (double) damageVibration.invoke(null, rotor, maxOmega * 0.5, 0.90);

		assertTrue(mildFault > 0.020 && mildFault < 0.050,
				() -> "mildFault=" + mildFault);
		assertTrue(moderateFault > mildFault && moderateFault < 0.10,
				() -> "mildFault=" + mildFault + " moderateFault=" + moderateFault);
		assertTrue(heavyFault > 0.30 && heavyFault < 0.50,
				() -> "heavyFault=" + heavyFault);
		assertTrue(severeFault > heavyFault + 0.25,
				() -> "heavyFault=" + heavyFault + " severeFault=" + severeFault);
		assertTrue(halfSpeedMildFault > mildFault * 0.55,
				() -> "mildFault=" + mildFault + " halfSpeedMildFault=" + halfSpeedMildFault);
		assertTrue(halfSpeedMildFault < mildFault,
				() -> "mildFault=" + mildFault + " halfSpeedMildFault=" + halfSpeedMildFault);
	}

	@Test
	void batteryBusRippleScalesWithMotorCurrentRippleAndPackResistance() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics lowResistance = new DronePhysics(base.withBattery(16.8, 16.7, 0.006, 2.0, 180.0));
		DronePhysics highResistance = new DronePhysics(base.withBattery(16.8, 16.7, 0.060, 2.0, 180.0));
		DroneInput cruise = new DroneInput(0.68, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 160; i++) {
			lowResistance.step(cruise, 0.005);
			highResistance.step(cruise, 0.005);
		}

		assertTrue(lowResistance.state().averageMotorCurrentRippleAmps() > 0.01);
		assertTrue(highResistance.state().batteryBusRippleVoltage() > lowResistance.state().batteryBusRippleVoltage() * 4.0);
		assertTrue(highResistance.state().batteryBusRippleVoltage() > 0.02);
		assertTrue(highResistance.state().batteryVoltage() < highResistance.state().batteryOpenCircuitVoltage());
	}

	@Test
	void imuSupplyNoiseTelemetryTracksSagRippleAndRegenerativeSpike() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics idealRail = new DronePhysics(base.withBattery(16.8, 16.7, 0.0, 20.0, 180.0));
		DronePhysics noisyRail = new DronePhysics(base.withBattery(16.8, 16.7, 0.070, 1.5, 180.0));
		DroneInput punch = new DroneInput(0.92, 0.0, 0.0, 0.0, true);
		DroneInput brake = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		double maxIdealSupplyNoise = 0.0;
		double maxNoisySupplyNoise = 0.0;
		for (int i = 0; i < 180; i++) {
			idealRail.step(punch, 0.005);
			noisyRail.step(punch, 0.005);
			maxIdealSupplyNoise = Math.max(maxIdealSupplyNoise, idealRail.state().imuSupplyNoiseIntensity());
			maxNoisySupplyNoise = Math.max(maxNoisySupplyNoise, noisyRail.state().imuSupplyNoiseIntensity());
		}
		for (int i = 0; i < 12; i++) {
			idealRail.step(brake, 0.005);
			noisyRail.step(brake, 0.005);
			maxIdealSupplyNoise = Math.max(maxIdealSupplyNoise, idealRail.state().imuSupplyNoiseIntensity());
			maxNoisySupplyNoise = Math.max(maxNoisySupplyNoise, noisyRail.state().imuSupplyNoiseIntensity());
		}

		double observedIdealSupplyNoise = maxIdealSupplyNoise;
		double observedNoisySupplyNoise = maxNoisySupplyNoise;
		assertTrue(Double.isFinite(observedNoisySupplyNoise));
		assertTrue(observedNoisySupplyNoise > observedIdealSupplyNoise + 0.18,
				() -> "idealSupplyNoise=" + observedIdealSupplyNoise + " noisySupplyNoise=" + observedNoisySupplyNoise);
		assertTrue(observedNoisySupplyNoise > 0.30);
	}

	@Test
	void batteryBusRippleRaisesEscDesyncAndCommutationTexture() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withBattery(16.8, 16.7, 0.006, 2.0, 180.0);
		DronePhysics cleanRail = new DronePhysics(config);
		DronePhysics noisyRail = new DronePhysics(config);
		DroneInput highDuty = new DroneInput(0.92, 0.0, 0.0, 0.0, true);
		double maxCleanCommutationRipple = 0.0;
		double maxNoisyCommutationRipple = 0.0;

		for (int i = 0; i < 180; i++) {
			cleanRail.state().setBatteryBusRippleVoltage(0.0);
			noisyRail.state().setBatteryBusRippleVoltage(0.36);
			cleanRail.step(highDuty, 0.005);
			noisyRail.step(highDuty, 0.005);
			maxCleanCommutationRipple = Math.max(maxCleanCommutationRipple, cleanRail.state().averageMotorCommutationRippleIntensity());
			maxNoisyCommutationRipple = Math.max(maxNoisyCommutationRipple, noisyRail.state().averageMotorCommutationRippleIntensity());
		}

		double finalMaxCleanCommutationRipple = maxCleanCommutationRipple;
		double finalMaxNoisyCommutationRipple = maxNoisyCommutationRipple;
		assertTrue(noisyRail.state().maxEscDesyncIntensity() > cleanRail.state().maxEscDesyncIntensity() + 0.015,
				() -> "cleanDesync=" + cleanRail.state().maxEscDesyncIntensity()
						+ " noisyDesync=" + noisyRail.state().maxEscDesyncIntensity());
		assertTrue(finalMaxNoisyCommutationRipple > finalMaxCleanCommutationRipple + 0.010,
				() -> "cleanCommutation=" + finalMaxCleanCommutationRipple
						+ " noisyCommutation=" + finalMaxNoisyCommutationRipple);
		assertTrue(noisyRail.state().averageMotorCurrentRippleAmps() > cleanRail.state().averageMotorCurrentRippleAmps() + 0.02);
	}

	@Test
	void batteryVoltageSpikeRaisesActiveBrakingEscStress() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withBattery(16.8, 16.7, 0.010, 2.0, 180.0);
		DronePhysics cleanRail = new DronePhysics(config);
		DronePhysics spikyRail = new DronePhysics(config);
		DroneInput spoolUp = new DroneInput(0.90, 0.0, 0.0, 0.0, true);
		DroneInput brake = new DroneInput(0.0, 0.0, 0.0, 0.0, true);
		double maxCleanDesync = 0.0;
		double maxSpikyDesync = 0.0;
		double maxCleanCommutationRipple = 0.0;
		double maxSpikyCommutationRipple = 0.0;
		double maxCleanCurrentRipple = 0.0;
		double maxSpikyCurrentRipple = 0.0;

		for (int i = 0; i < 130; i++) {
			cleanRail.step(spoolUp, 0.005);
			spikyRail.step(spoolUp, 0.005);
		}

		for (int i = 0; i < 70; i++) {
			cleanRail.state().setBatteryVoltageSpike(0.0);
			spikyRail.state().setBatteryVoltageSpike(1.05);
			cleanRail.step(brake, 0.005);
			spikyRail.step(brake, 0.005);
			maxCleanDesync = Math.max(maxCleanDesync, cleanRail.state().maxEscDesyncIntensity());
			maxSpikyDesync = Math.max(maxSpikyDesync, spikyRail.state().maxEscDesyncIntensity());
			maxCleanCommutationRipple = Math.max(maxCleanCommutationRipple, cleanRail.state().averageMotorCommutationRippleIntensity());
			maxSpikyCommutationRipple = Math.max(maxSpikyCommutationRipple, spikyRail.state().averageMotorCommutationRippleIntensity());
			maxCleanCurrentRipple = Math.max(maxCleanCurrentRipple, cleanRail.state().averageMotorCurrentRippleAmps());
			maxSpikyCurrentRipple = Math.max(maxSpikyCurrentRipple, spikyRail.state().averageMotorCurrentRippleAmps());
		}

		double finalMaxCleanDesync = maxCleanDesync;
		double finalMaxSpikyDesync = maxSpikyDesync;
		double finalMaxCleanCommutationRipple = maxCleanCommutationRipple;
		double finalMaxSpikyCommutationRipple = maxSpikyCommutationRipple;
		double finalMaxCleanCurrentRipple = maxCleanCurrentRipple;
		double finalMaxSpikyCurrentRipple = maxSpikyCurrentRipple;
		assertTrue(finalMaxSpikyDesync > finalMaxCleanDesync + 0.010,
				() -> "cleanDesync=" + finalMaxCleanDesync
						+ " spikyDesync=" + finalMaxSpikyDesync);
		assertTrue(finalMaxSpikyCommutationRipple > finalMaxCleanCommutationRipple + 0.0025,
				() -> "cleanCommutation=" + finalMaxCleanCommutationRipple
						+ " spikyCommutation=" + finalMaxSpikyCommutationRipple);
		assertTrue(finalMaxSpikyCurrentRipple > finalMaxCleanCurrentRipple + 0.002,
				() -> "cleanCurrentRipple=" + finalMaxCleanCurrentRipple
						+ " spikyCurrentRipple=" + finalMaxSpikyCurrentRipple);
	}

	@Test
	void escOutputCurveSoftensMidThrottleResponse() {
		DroneConfig base = directControl(DroneConfig.racingQuad()).withMotorTimeConstantSeconds(0.01);
		DronePhysics linearEsc = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 0.0));
		DronePhysics softEsc = new DronePhysics(base.withEscMotorResponse(1.8, 1000.0, 0.0));
		DroneInput midThrottle = new DroneInput(0.45, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 80; i++) {
			linearEsc.step(midThrottle, 0.005);
			softEsc.step(midThrottle, 0.005);
		}

		assertTrue(softEsc.state().averageEscOutputCommand() < linearEsc.state().averageEscOutputCommand() * 0.75);
		assertTrue(softEsc.state().averageMotorPower(softEsc.config()) < linearEsc.state().averageMotorPower(linearEsc.config()) * 0.82);
	}

	@Test
	void escSlewRateLimitsOutputStepChanges() {
		DroneConfig base = directControl(DroneConfig.racingQuad()).withMotorTimeConstantSeconds(0.01);
		DronePhysics fastEsc = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 0.0));
		DronePhysics slowEsc = new DronePhysics(base.withEscMotorResponse(1.0, 3.0, 0.0));
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 10; i++) {
			fastEsc.step(punch, 0.005);
			slowEsc.step(punch, 0.005);
		}

		assertTrue(slowEsc.state().averageEscOutputCommand() < 0.2);
		assertTrue(fastEsc.state().averageEscOutputCommand() > 0.95);
	}

	@Test
	void escFallSlewRateLimitsThrottleCuts() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.01)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics fastFall = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0));
		DronePhysics slowFall = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 2.0, 0.0, 0.0, 0.0));
		DroneInput highThrottle = new DroneInput(0.75, 0.0, 0.0, 0.0, true);
		DroneInput cutThrottle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 100; i++) {
			fastFall.step(highThrottle, 0.005);
			slowFall.step(highThrottle, 0.005);
		}
		for (int i = 0; i < 12; i++) {
			fastFall.step(cutThrottle, 0.005);
			slowFall.step(cutThrottle, 0.005);
		}

		assertTrue(slowFall.state().averageEscOutputCommand() > fastFall.state().averageEscOutputCommand() + 0.25);
		assertTrue(slowFall.state().averageMotorPower(slowFall.config()) > fastFall.state().averageMotorPower(fastFall.config()) * 1.8);
	}

	@Test
	void lowStartupTorqueMustBreakMotorStaticFriction() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics belowBreakaway = new DronePhysics(config);
		DronePhysics aboveBreakaway = new DronePhysics(config);
		DroneInput tinyThrottle = new DroneInput(0.0005, 0.0, 0.0, 0.0, true);
		DroneInput startingThrottle = new DroneInput(0.006, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 80; i++) {
			belowBreakaway.state().setVelocityMetersPerSecond(Vec3.ZERO);
			aboveBreakaway.state().setVelocityMetersPerSecond(Vec3.ZERO);
			belowBreakaway.step(tinyThrottle, 0.005);
			aboveBreakaway.step(startingThrottle, 0.005);
		}

		assertTrue(belowBreakaway.state().averageEscOutputCommand() > 0.015);
		assertTrue(belowBreakaway.state().averageMotorRpm() < 120.0,
				() -> "below=" + belowBreakaway.state().averageMotorRpm());
		assertTrue(aboveBreakaway.state().averageMotorRpm() > belowBreakaway.state().averageMotorRpm() + 500.0,
				() -> "below=" + belowBreakaway.state().averageMotorRpm() + " above=" + aboveBreakaway.state().averageMotorRpm());
		assertTrue(belowBreakaway.state().averageMotorCurrentAmps() > 0.05);
	}

	@Test
	void escDeadbandSuppressesTinyMotorCommands() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0);
		DronePhysics noDeadband = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0));
		DronePhysics deadband = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.05, 0.0, 0.0));
		DroneInput tinyThrottle = new DroneInput(0.0005, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 8; i++) {
			noDeadband.step(tinyThrottle, 0.005);
			deadband.step(tinyThrottle, 0.005);
		}

		assertTrue(noDeadband.state().averageEscOutputCommand() > 0.015);
		assertEquals(0.0, deadband.state().averageEscOutputCommand(), 1.0e-9);
	}

	@Test
	void escCommandFrameRateHoldsOutputBetweenFrames() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withEscCommandSignal(100.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput lowThrottle = new DroneInput(0.20, 0.0, 0.0, 0.0, true);
		DroneInput highThrottle = new DroneInput(0.80, 0.0, 0.0, 0.0, true);

		physics.step(lowThrottle, 0.005);
		double heldOutput = physics.state().averageEscOutputCommand();
		physics.step(highThrottle, 0.005);

		assertEquals(heldOutput, physics.state().averageEscOutputCommand(), 1.0e-9);
		assertEquals(0.010, physics.state().escCommandFrameIntervalSeconds(), 1.0e-9);
		assertTrue(physics.state().escCommandFrameAgeSeconds() > 0.0);
		assertTrue(physics.state().escCommandError() > 0.20);

		physics.step(highThrottle, 0.005);

		double partiallyUpdatedOutput = physics.state().averageEscOutputCommand();
		assertTrue(partiallyUpdatedOutput > heldOutput);
		assertTrue(physics.state().escOutputCommand(0) > physics.state().escOutputCommand(3) + 0.20);
		assertTrue(physics.state().escCommandFrameAgeSeconds() > 0.0);

		physics.step(highThrottle, 0.005);

		assertTrue(physics.state().averageEscOutputCommand() > heldOutput + 0.20);
		assertTrue(physics.state().averageEscOutputCommand() > partiallyUpdatedOutput + 0.10);
		assertEquals(physics.state().escOutputCommand(0), physics.state().escOutputCommand(3), 1.0e-3);
	}

	@Test
	void escCommandResolutionQuantizesOutput() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withEscCommandSignal(0.0, 11.0);
		DronePhysics physics = new DronePhysics(config);

		physics.step(new DroneInput(0.46, 0.0, 0.0, 0.0, true), 0.005);

		assertEquals(0.70, physics.state().averageEscOutputCommand(), 1.0e-9);
		assertTrue(physics.state().escCommandError() > 0.015);
	}

	@Test
	void escElectricalOutputLagsCommandedOutputOnThrottleSteps() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withEscCommandSignal(EscCommandProtocol.DSHOT600, 400.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.85, 0.0, 0.0, 0.0, true);

		physics.step(punch, 0.00025);

		double commandedOutput = physics.state().averageEscOutputCommand();
		double electricalOutput = physics.state().averageEscElectricalOutputCommand();
		assertTrue(commandedOutput > 0.20, () -> "commanded=" + commandedOutput);
		assertTrue(electricalOutput > 0.04, () -> "electrical=" + electricalOutput);
		assertTrue(electricalOutput < commandedOutput * 0.45,
				() -> "commanded=" + commandedOutput + " electrical=" + electricalOutput);
		assertTrue(physics.state().maxEscElectricalOutputError() > 0.13,
				() -> "error=" + physics.state().maxEscElectricalOutputError());

		for (int i = 0; i < 80; i++) {
			physics.step(punch, 0.00025);
		}

		assertEquals(physics.state().averageEscOutputCommand(), physics.state().averageEscElectricalOutputCommand(), 0.010);
		assertTrue(physics.state().maxEscElectricalOutputError() < 0.012,
				() -> "error=" + physics.state().maxEscElectricalOutputError());
	}

	@Test
	void activeBrakingAcceleratesMotorSpinDownAndAddsLoad() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.090)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics coasting = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0));
		DronePhysics braked = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0));
		DroneInput highThrottle = new DroneInput(0.85, 0.0, 0.0, 0.0, true);
		DroneInput cutThrottle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 220; i++) {
			coasting.step(highThrottle, 0.005);
			braked.step(highThrottle, 0.005);
		}
		double maxBrakedCurrent = 0.0;
		double maxRegenerativeCurrent = 0.0;
		double maxActiveBrakingTorque = 0.0;
		for (int i = 0; i < 10; i++) {
			coasting.step(cutThrottle, 0.005);
			braked.step(cutThrottle, 0.005);
			maxBrakedCurrent = Math.max(maxBrakedCurrent, braked.state().averageMotorCurrentAmps());
			maxRegenerativeCurrent = Math.max(maxRegenerativeCurrent, braked.state().batteryRegenerativeCurrentAmps());
			maxActiveBrakingTorque = Math.max(maxActiveBrakingTorque, braked.state().rotorActiveBrakingTorqueBodyNewtonMeters().length());
		}

		assertTrue(braked.state().averageMotorPower(braked.config()) < coasting.state().averageMotorPower(coasting.config()) * 0.70,
				() -> "brakedPower=" + braked.state().averageMotorPower(braked.config())
						+ " coastingPower=" + coasting.state().averageMotorPower(coasting.config()));
		assertTrue(maxBrakedCurrent > 1.8);
		assertTrue(maxRegenerativeCurrent > 1.0);
		assertTrue(Double.isFinite(maxActiveBrakingTorque));
		assertTrue(maxActiveBrakingTorque > 0.02);
		assertTrue(maxActiveBrakingTorque < 0.50);
	}

	@Test
	void activeBrakingThrottleChopSlewStaysBlackboxPlausible() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.045)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics coasting = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0));
		DronePhysics braked = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0));
		DroneInput highThrottle = new DroneInput(0.92, 0.0, 0.0, 0.0, true);
		DroneInput cutThrottle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);
		double dtSeconds = 0.005;

		for (int i = 0; i < 240; i++) {
			coasting.step(highThrottle, dtSeconds);
			braked.step(highThrottle, dtSeconds);
		}

		double coastingStartRpm = coasting.state().averageMotorRpm();
		double brakedStartRpm = braked.state().averageMotorRpm();
		coasting.step(cutThrottle, dtSeconds);
		braked.step(cutThrottle, dtSeconds);
		double coastingFirstFrameSlewRpmPerSecond = (coastingStartRpm - coasting.state().averageMotorRpm()) / dtSeconds;
		double brakedFirstFrameSlewRpmPerSecond = (brakedStartRpm - braked.state().averageMotorRpm()) / dtSeconds;

		for (int i = 1; i < 10; i++) {
			coasting.step(cutThrottle, dtSeconds);
			braked.step(cutThrottle, dtSeconds);
		}

		double coastingFiftyMillisecondSlewRpmPerSecond = (coastingStartRpm - coasting.state().averageMotorRpm()) / 0.050;
		double brakedFiftyMillisecondSlewRpmPerSecond = (brakedStartRpm - braked.state().averageMotorRpm()) / 0.050;
		double maxRpm = base.rotors().get(0).maxOmegaRadiansPerSecond() * 60.0 / (Math.PI * 2.0);
		double firstOrderSpinupReferenceSlewRpmPerSecond = maxRpm / base.motorTimeConstantSeconds();
		double calibratedRuntimeSlewLimitRpmPerSecond = firstOrderSpinupReferenceSlewRpmPerSecond
				* MotorResponseCalibration.activeBrakingRuntimeSlewScaleOverSpinupProxy();

		assertTrue(brakedFirstFrameSlewRpmPerSecond < calibratedRuntimeSlewLimitRpmPerSecond * 1.08,
				() -> "brakedFirstFrameSlew=" + brakedFirstFrameSlewRpmPerSecond
						+ " calibratedLimit=" + calibratedRuntimeSlewLimitRpmPerSecond);
		assertTrue(brakedFirstFrameSlewRpmPerSecond > coastingFirstFrameSlewRpmPerSecond * 1.08,
				() -> "brakedFirstFrameSlew=" + brakedFirstFrameSlewRpmPerSecond
						+ " coastingFirstFrameSlew=" + coastingFirstFrameSlewRpmPerSecond);
		assertTrue(brakedFiftyMillisecondSlewRpmPerSecond < calibratedRuntimeSlewLimitRpmPerSecond * 1.08,
				() -> "braked50msSlew=" + brakedFiftyMillisecondSlewRpmPerSecond
						+ " calibratedLimit=" + calibratedRuntimeSlewLimitRpmPerSecond);
		assertTrue(brakedFiftyMillisecondSlewRpmPerSecond > coastingFiftyMillisecondSlewRpmPerSecond * 1.30,
				() -> "braked50msSlew=" + brakedFiftyMillisecondSlewRpmPerSecond
						+ " coasting50msSlew=" + coastingFiftyMillisecondSlewRpmPerSecond);
		assertTrue(braked.state().averageMotorRpm() < coasting.state().averageMotorRpm() * 0.40,
				() -> "brakedEndRpm=" + braked.state().averageMotorRpm()
						+ " coastingEndRpm=" + coasting.state().averageMotorRpm());
	}

	@Test
	void activeBrakingTorqueTelemetryShowsAsymmetricThrottleChop() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.090)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput yawPreload = new DroneInput(0.78, 0.0, 0.0, 0.85, true, true, FlightMode.ACRO);
		DroneInput cutThrottle = new DroneInput(0.0, 0.0, 0.0, 0.0, true, true, FlightMode.ACRO);

		for (int i = 0; i < 260; i++) {
			physics.step(yawPreload, 0.005);
		}
		double maxActiveBrakingTorque = 0.0;
		for (int i = 0; i < 14; i++) {
			physics.step(cutThrottle, 0.005);
			maxActiveBrakingTorque = Math.max(maxActiveBrakingTorque, physics.state().rotorActiveBrakingTorqueBodyNewtonMeters().length());
		}

		double observedActiveBrakingTorque = maxActiveBrakingTorque;
		assertTrue(observedActiveBrakingTorque > 0.004,
				() -> "activeBrakingTorque=" + observedActiveBrakingTorque);
	}

	@Test
	void voltageCompensationBoostsEscOutputUnderSag() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.01)
				.withBattery(16.8, 13.2, 0.0, 0.2, 90.0);
		DronePhysics uncompensated = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 0.0));
		DronePhysics compensated = new DronePhysics(base.withEscMotorResponse(1.0, 1000.0, 1.0));
		double capacityAmpSeconds = base.batteryCapacityAmpHours() * 3600.0;
		uncompensated.state().setBatteryAmpSecondsConsumed(capacityAmpSeconds * 0.65);
		compensated.state().setBatteryAmpSecondsConsumed(capacityAmpSeconds * 0.65);
		uncompensated.step(new DroneInput(0.0, 0.0, 0.0, 0.0, true), 0.005);
		compensated.step(new DroneInput(0.0, 0.0, 0.0, 0.0, true), 0.005);
		DroneInput cruise = new DroneInput(0.55, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 80; i++) {
			uncompensated.step(cruise, 0.005);
			compensated.step(cruise, 0.005);
		}

		assertTrue(compensated.state().averageEscOutputCommand() > uncompensated.state().averageEscOutputCommand() * 1.08);
		assertTrue(compensated.state().averageMotorPower(compensated.config()) > uncompensated.state().averageMotorPower(uncompensated.config()) * 1.04);
	}

	@Test
	void batteryOpenCircuitVoltageFollowsLipoDischargeCurve() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withBattery(16.8, 13.2, 0.0, 10.0, 90.0);
		double fullOpenCircuitVoltage = openCircuitVoltageAtStateOfCharge(config, 1.0);
		double highOpenCircuitVoltage = openCircuitVoltageAtStateOfCharge(config, 0.80);
		double midOpenCircuitVoltage = openCircuitVoltageAtStateOfCharge(config, 0.50);
		double reserveOpenCircuitVoltage = openCircuitVoltageAtStateOfCharge(config, 0.20);
		double lowOpenCircuitVoltage = openCircuitVoltageAtStateOfCharge(config, 0.10);
		double voltageRange = config.nominalBatteryVoltage() - config.emptyBatteryVoltage();
		double linearMidVoltage = config.emptyBatteryVoltage() + voltageRange * 0.50;

		assertEquals(config.nominalBatteryVoltage(), fullOpenCircuitVoltage, 0.02);
		assertEquals(15.73, highOpenCircuitVoltage, 0.04);
		assertEquals(14.66, midOpenCircuitVoltage, 0.04);
		assertEquals(14.20, reserveOpenCircuitVoltage, 0.04);
		assertEquals(13.85, lowOpenCircuitVoltage, 0.04);
		assertTrue(midOpenCircuitVoltage < linearMidVoltage - 0.25);
		assertTrue(fullOpenCircuitVoltage > highOpenCircuitVoltage);
		assertTrue(highOpenCircuitVoltage > midOpenCircuitVoltage);
		assertTrue(midOpenCircuitVoltage > reserveOpenCircuitVoltage);
		assertTrue(reserveOpenCircuitVoltage > lowOpenCircuitVoltage);
	}

	@Test
	void lowStateOfChargeAppliesMeasuredR0RiseWithoutHidingReserveLimit() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withBattery(16.8, 13.2, 0.032, 4.0, 180.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics fullPack = new DronePhysics(config);
		DronePhysics lowPack = new DronePhysics(config);
		double capacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0;
		lowPack.state().setBatteryAmpSecondsConsumed(capacityAmpSeconds * 0.88);
		DroneInput punch = new DroneInput(0.82, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 70; i++) {
			fullPack.step(punch, 0.005);
			lowPack.step(punch, 0.005);
		}

		double fullSagPerAmp = fullPack.state().batteryOhmicSagVoltage()
				/ Math.max(1.0, fullPack.state().batteryCurrentAmps());
		double lowSagPerAmp = lowPack.state().batteryOhmicSagVoltage()
				/ Math.max(1.0, lowPack.state().batteryCurrentAmps());
		double fullEffectiveResistance = fullPack.state().batteryEffectiveResistanceOhms();
		double lowEffectiveResistance = lowPack.state().batteryEffectiveResistanceOhms();
		assertTrue(lowPack.state().batteryStateOfCharge() < 0.13);
		assertTrue(fullPack.state().batteryStateOfCharge() > 0.99);
		assertTrue(lowSagPerAmp > fullSagPerAmp * 1.02,
				() -> "fullSagPerAmp=" + fullSagPerAmp + " lowSagPerAmp=" + lowSagPerAmp);
		assertTrue(lowSagPerAmp < fullSagPerAmp * 1.12,
				() -> "fullSagPerAmp=" + fullSagPerAmp + " lowSagPerAmp=" + lowSagPerAmp);
		assertTrue(lowEffectiveResistance > fullEffectiveResistance * 1.02,
				() -> "fullResistance=" + fullEffectiveResistance + " lowResistance=" + lowEffectiveResistance);
		assertTrue(lowEffectiveResistance < fullEffectiveResistance * 1.12,
				() -> "fullResistance=" + fullEffectiveResistance + " lowResistance=" + lowEffectiveResistance);
		assertTrue(lowPack.state().batteryVoltage() < fullPack.state().batteryVoltage() - 1.2,
				() -> "fullVoltage=" + fullPack.state().batteryVoltage()
						+ " lowVoltage=" + lowPack.state().batteryVoltage());
	}

	@Test
	void batterySocResistanceShapeFollowsMendeleyRuntimeLookup() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withBattery(16.8, 13.2, 0.020, 1000.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);

		double freshFull = effectiveBatteryResistanceAt(config, 1.0, 0.0);
		double freshLow = effectiveBatteryResistanceAt(config, 0.10, 0.0);
		double wornFull = effectiveBatteryResistanceAt(config, 1.0, 450.0);
		double wornLow = effectiveBatteryResistanceAt(config, 0.10, 450.0);

		assertEquals(1.030, freshLow / freshFull, 0.004);
		assertEquals(1.039, wornLow / wornFull, 0.008);
		assertTrue(wornFull > freshFull * 1.18,
				() -> "freshFull=" + freshFull + " wornFull=" + wornFull);
		assertTrue(wornLow > freshLow * 1.19,
				() -> "freshLow=" + freshLow + " wornLow=" + wornLow);
	}

	@Test
	void batteryResistanceTelemetrySeparatesSocTemperatureAndAgingScales() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withBattery(16.8, 13.2, 0.020, 1000.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);

		DronePhysics freshFull = batteryPhysicsAt(config, 1.0, 0.0, 25.0);
		DronePhysics freshLow = batteryPhysicsAt(config, 0.10, 0.0, 25.0);
		DronePhysics wornLow = batteryPhysicsAt(config, 0.10, 450.0, 25.0);
		double coldReferenceCelsius = (42.0 - 32.0) * 5.0 / 9.0;
		DronePhysics coldFull = batteryPhysicsAt(config, 1.0, 0.0, coldReferenceCelsius);

		assertEquals(1.0, freshFull.state().batteryStateOfChargeResistanceScale(), 0.002);
		assertEquals(1.030, freshLow.state().batteryStateOfChargeResistanceScale(), 0.004);
		assertEquals(1.039, wornLow.state().batteryStateOfChargeResistanceScale(), 0.008);
		assertEquals(1.0, freshFull.state().batteryTemperatureResistanceScale(), 0.002);
		assertTrue(coldFull.state().batteryTemperatureResistanceScale()
						> freshFull.state().batteryTemperatureResistanceScale() * 1.67,
				() -> "warmTempScale=" + freshFull.state().batteryTemperatureResistanceScale()
						+ " coldTempScale=" + coldFull.state().batteryTemperatureResistanceScale());
		assertEquals(
				reconstructedBatteryResistance(config, freshLow.state()),
				freshLow.state().batteryEffectiveResistanceOhms(),
				1.0e-9
		);
		assertEquals(
				reconstructedBatteryResistance(config, wornLow.state()),
				wornLow.state().batteryEffectiveResistanceOhms(),
				1.0e-9
		);
		assertEquals(
				reconstructedBatteryResistance(config, coldFull.state()),
				coldFull.state().batteryEffectiveResistanceOhms(),
				1.0e-9
		);
	}

	@Test
	void batterySagCurrentHeadroomTracksEffectiveResistance() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withBattery(16.8, 13.2, 0.018, 1.5, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics freshFull = new DronePhysics(config);
		DronePhysics wornLow = new DronePhysics(config);
		double capacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0;
		wornLow.state().setBatteryAmpSecondsConsumed(capacityAmpSeconds * 0.90);
		wornLow.state().setBatteryEquivalentCycles(450.0);

		freshFull.step(DroneInput.idle(), 0.005);
		wornLow.step(DroneInput.idle(), 0.005);

		double expectedFreshCurrent = 0.20 * config.nominalBatteryVoltage()
				/ freshFull.state().batteryEffectiveResistanceOhms();
		double expectedWornCurrent = 0.20 * config.nominalBatteryVoltage()
				/ wornLow.state().batteryEffectiveResistanceOhms();
		assertEquals(expectedFreshCurrent, freshFull.state().batteryTwentyPercentSagCurrentAmps(), 1.0e-6);
		assertEquals(expectedFreshCurrent / config.maxBatteryCurrentAmps(),
				freshFull.state().batteryTwentyPercentSagCurrentMargin(), 1.0e-6);
		assertEquals(expectedWornCurrent, wornLow.state().batteryTwentyPercentSagCurrentAmps(), 1.0e-6);
		assertEquals(expectedWornCurrent / config.maxBatteryCurrentAmps(),
				wornLow.state().batteryTwentyPercentSagCurrentMargin(), 1.0e-6);
		assertTrue(wornLow.state().batteryTwentyPercentSagCurrentAmps()
				< freshFull.state().batteryTwentyPercentSagCurrentAmps() * 0.85,
				() -> "freshSag20A=" + freshFull.state().batteryTwentyPercentSagCurrentAmps()
						+ " wornLowSag20A=" + wornLow.state().batteryTwentyPercentSagCurrentAmps());
		assertTrue(wornLow.state().batteryTwentyPercentSagCurrentMargin()
				< freshFull.state().batteryTwentyPercentSagCurrentMargin() * 0.85);
	}

	@Test
	void batteryColdResistanceFollowsJeffcoRcLipoTemperatureRatio() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withBattery(16.8, 13.2, 0.020, 1000.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		double warmReferenceCelsius = (71.0 - 32.0) * 5.0 / 9.0;
		double coldReferenceCelsius = (42.0 - 32.0) * 5.0 / 9.0;

		double warmResistance = effectiveBatteryResistanceAt(config, 1.0, 0.0, warmReferenceCelsius);
		double coldResistance = effectiveBatteryResistanceAt(config, 1.0, 0.0, coldReferenceCelsius);
		double coldWarmRatio = coldResistance / warmResistance;

		assertTrue(coldWarmRatio > 1.67,
				() -> "coldWarmRatio=" + coldWarmRatio
						+ " warmResistance=" + warmResistance
						+ " coldResistance=" + coldResistance);
		assertTrue(coldWarmRatio < 2.05,
				() -> "coldWarmRatio=" + coldWarmRatio
						+ " warmResistance=" + warmResistance
						+ " coldResistance=" + coldResistance);
	}

	@Test
	void batteryEquivalentCyclesRaiseResistanceOnMendeleyAgingScale() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withBattery(16.8, 13.2, 0.020, 2.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics fresh = new DronePhysics(config);
		DronePhysics aged = new DronePhysics(config);
		aged.state().setBatteryEquivalentCycles(450.0);

		fresh.step(DroneInput.idle(), 0.005);
		aged.step(DroneInput.idle(), 0.005);

		assertEquals(1.0, fresh.state().batteryResistanceAgingScale(), 0.002);
		assertTrue(aged.state().batteryResistanceAgingScale() > 1.18,
				() -> "agingScale=" + aged.state().batteryResistanceAgingScale());
		assertTrue(aged.state().batteryResistanceAgingScale() < 1.22,
				() -> "agingScale=" + aged.state().batteryResistanceAgingScale());
		assertTrue(aged.state().batteryEffectiveResistanceOhms() > fresh.state().batteryEffectiveResistanceOhms() * 1.18,
				() -> "freshResistance=" + fresh.state().batteryEffectiveResistanceOhms()
						+ " agedResistance=" + aged.state().batteryEffectiveResistanceOhms());
		assertTrue(aged.state().batteryEffectiveResistanceOhms() < fresh.state().batteryEffectiveResistanceOhms() * 1.23,
				() -> "freshResistance=" + fresh.state().batteryEffectiveResistanceOhms()
						+ " agedResistance=" + aged.state().batteryEffectiveResistanceOhms());
	}

	@Test
	void batteryEquivalentCyclesReduceEffectiveCapacityOnHighCurrentSohCurve() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withBattery(16.8, 13.2, 0.018, 1.5, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics fresh = new DronePhysics(config);
		DronePhysics highCurrentAged = new DronePhysics(config);
		DronePhysics worn = new DronePhysics(config);
		highCurrentAged.state().setBatteryEquivalentCycles(400.0);
		worn.state().setBatteryEquivalentCycles(450.0);

		fresh.step(DroneInput.idle(), 0.005);
		highCurrentAged.step(DroneInput.idle(), 0.005);
		worn.step(DroneInput.idle(), 0.005);

		assertEquals(1.0, fresh.state().batteryCapacityAgingScale(), 0.001);
		assertEquals(0.808, highCurrentAged.state().batteryCapacityAgingScale(), 0.006);
		assertEquals(0.800, worn.state().batteryCapacityAgingScale(), 0.002);
		assertEquals(1.0, worn.state().batteryStateOfCharge(), 0.001);
	}

	@Test
	void capacityFadeConsumesReserveSoonerForSameAmpSeconds() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withBattery(16.8, 13.2, 0.018, 1.5, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics fresh = new DronePhysics(config);
		DronePhysics worn = new DronePhysics(config);
		double nominalCapacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0;
		fresh.state().setBatteryAmpSecondsConsumed(nominalCapacityAmpSeconds * 0.70);
		worn.state().setBatteryAmpSecondsConsumed(nominalCapacityAmpSeconds * 0.70);
		worn.state().setBatteryEquivalentCycles(450.0);

		fresh.step(DroneInput.idle(), 0.005);
		worn.step(DroneInput.idle(), 0.005);

		assertTrue(worn.state().batteryStateOfCharge() < fresh.state().batteryStateOfCharge() - 0.17,
				() -> "freshSoc=" + fresh.state().batteryStateOfCharge()
						+ " wornSoc=" + worn.state().batteryStateOfCharge()
						+ " wornCapacityScale=" + worn.state().batteryCapacityAgingScale());
		assertTrue(worn.state().batteryOpenCircuitVoltage() < fresh.state().batteryOpenCircuitVoltage() - 0.30,
				() -> "freshOcv=" + fresh.state().batteryOpenCircuitVoltage()
						+ " wornOcv=" + worn.state().batteryOpenCircuitVoltage());
		assertTrue(worn.state().batteryPowerLimit() < fresh.state().batteryPowerLimit(),
				() -> "freshPowerLimit=" + fresh.state().batteryPowerLimit()
						+ " wornPowerLimit=" + worn.state().batteryPowerLimit());
	}

	@Test
	void batteryEquivalentCyclesAccumulateFromCurrentThroughput() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 13.2, 0.018, 0.08, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.92, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 160; i++) {
			physics.step(punch, 0.005);
		}

		assertTrue(physics.state().batteryCurrentAmps() > 8.0);
		assertTrue(physics.state().batteryEquivalentCycles() > 0.04,
				() -> "equivalentCycles=" + physics.state().batteryEquivalentCycles());
	}

	@Test
	void batteryResistanceTuningChangesVoltageSag() {
		DroneConfig base = directControl(DroneConfig.racingQuad());
		DronePhysics lowResistance = new DronePhysics(base.withBattery(16.8, 13.2, 0.005, 1.5, 90.0));
		DronePhysics highResistance = new DronePhysics(base.withBattery(16.8, 13.2, 0.12, 1.5, 90.0));
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 50; i++) {
			lowResistance.step(punch, 0.005);
			highResistance.step(punch, 0.005);
		}

		assertTrue(lowResistance.state().batteryCurrentAmps() > 20.0);
		assertTrue(lowResistance.state().batteryStateOfCharge() < 1.0);
		assertTrue(highResistance.state().batteryEffectiveResistanceOhms() > lowResistance.state().batteryEffectiveResistanceOhms() * 10.0);
		assertTrue(highResistance.state().batteryVoltage() < lowResistance.state().batteryVoltage() - 3.0);
	}

	@Test
	void sustainedHighCurrentBuildsBatteryPolarizationResistance() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 13.2, 0.020, 1.5, 80.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics cruise = new DronePhysics(config);
		DronePhysics punch = new DronePhysics(config);
		DroneInput cruiseInput = new DroneInput(config.hoverThrottle() + 0.04, 0.0, 0.0, 0.0, true);
		DroneInput punchInput = new DroneInput(0.98, 0.0, 0.0, 0.0, true);
		DroneInput cut = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 180; i++) {
			cruise.step(cruiseInput, 0.005);
			punch.step(punchInput, 0.005);
		}
		double loadedScale = punch.state().batteryPolarizationResistanceScale();
		double loadedResistance = punch.state().batteryEffectiveResistanceOhms();
		double loadedVoltage = punch.state().batteryVoltage();
		double loadedCurrent = punch.state().batteryCurrentAmps();
		double cruiseCurrent = cruise.state().batteryCurrentAmps();

		for (int i = 0; i < 260; i++) {
			punch.step(cut, 0.005);
		}

		assertTrue(loadedCurrent > cruiseCurrent + 25.0,
				() -> "cruiseCurrent=" + cruiseCurrent + " loadedCurrent=" + loadedCurrent);
		assertTrue(loadedScale > cruise.state().batteryPolarizationResistanceScale() + 0.08,
				() -> "cruiseScale=" + cruise.state().batteryPolarizationResistanceScale()
						+ " loadedScale=" + loadedScale);
		assertTrue(loadedResistance > cruise.state().batteryEffectiveResistanceOhms() * 1.08,
				() -> "cruiseResistance=" + cruise.state().batteryEffectiveResistanceOhms()
						+ " loadedResistance=" + loadedResistance);
		assertTrue(loadedVoltage < cruise.state().batteryVoltage() - 0.35,
				() -> "cruiseVoltage=" + cruise.state().batteryVoltage()
						+ " loadedVoltage=" + loadedVoltage);
		assertTrue(punch.state().batteryPolarizationResistanceScale() < loadedScale - 0.035,
				() -> "loadedScale=" + loadedScale
						+ " recoveredScale=" + punch.state().batteryPolarizationResistanceScale());
		assertTrue(punch.state().batteryPolarizationResistanceScale() > 1.0);
	}

	@Test
	void sustainedLoadBuildsSlowBatteryPolarizationVoltage() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 13.2, 0.030, 1.5, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.98, 0.0, 0.0, 0.0, true);
		DroneInput cut = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 2400; i++) {
			physics.step(punch, 0.005);
		}
		double loadedSlowPolarization = physics.state().batterySlowPolarizationVoltage();
		double loadedTransientSag = physics.state().batteryTransientSagVoltage();

		for (int i = 0; i < 200; i++) {
			physics.step(cut, 0.005);
		}
		double earlySlowPolarization = physics.state().batterySlowPolarizationVoltage();
		double earlyOpenCircuitVoltage = physics.state().batteryOpenCircuitVoltage();
		double earlyBusVoltage = physics.state().batteryVoltage();

		for (int i = 0; i < 4000; i++) {
			physics.step(cut, 0.005);
		}
		double lateSlowPolarization = physics.state().batterySlowPolarizationVoltage();

		assertTrue(loadedSlowPolarization > 0.050,
				() -> "loadedSlowPolarization=" + loadedSlowPolarization);
		assertTrue(loadedSlowPolarization < loadedTransientSag * 0.35,
				() -> "loadedSlowPolarization=" + loadedSlowPolarization
						+ " loadedTransientSag=" + loadedTransientSag);
		assertTrue(earlySlowPolarization > loadedSlowPolarization * 0.92,
				() -> "loadedSlowPolarization=" + loadedSlowPolarization
						+ " earlySlowPolarization=" + earlySlowPolarization);
		assertTrue(earlyBusVoltage < earlyOpenCircuitVoltage - earlySlowPolarization * 0.80,
				() -> "earlyBusVoltage=" + earlyBusVoltage
						+ " earlyOpenCircuitVoltage=" + earlyOpenCircuitVoltage
						+ " earlySlowPolarization=" + earlySlowPolarization);
		assertTrue(lateSlowPolarization < earlySlowPolarization);
		assertTrue(lateSlowPolarization > earlySlowPolarization * 0.70,
				() -> "earlySlowPolarization=" + earlySlowPolarization
						+ " lateSlowPolarization=" + lateSlowPolarization);
	}

	@Test
	void batteryMaxCurrentAppliesDynamicPowerLimit() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.020)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics highCurrentPack = new DronePhysics(base.withBattery(16.8, 16.7, 0.0, 20.0, 90.0));
		DronePhysics limitedPack = new DronePhysics(base.withBattery(16.8, 16.7, 0.0, 20.0, 24.0));
		DroneInput punch = new DroneInput(0.95, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 140; i++) {
			highCurrentPack.step(punch, 0.005);
			limitedPack.step(punch, 0.005);
		}

		assertTrue(highCurrentPack.state().batteryCurrentLimit() > 0.88);
		assertTrue(limitedPack.state().batteryCurrentLimit() < highCurrentPack.state().batteryCurrentLimit() - 0.03);
		assertTrue(limitedPack.state().batteryPowerLimit() < highCurrentPack.state().batteryPowerLimit() - 0.03);
		assertTrue(limitedPack.state().averageMotorRpm() < highCurrentPack.state().averageMotorRpm() * 0.98);
		assertTrue(limitedPack.state().batteryCurrentAmps() < highCurrentPack.state().batteryCurrentAmps() * 0.80);
	}

	@Test
	void batteryVoltageHasDynamicSagAndRecovery() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 13.2, 0.080, 1.5, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);
		DroneInput cut = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 120; i++) {
			physics.step(punch, 0.005);
		}
		double loadedVoltage = physics.state().batteryVoltage();
		double loadedTransientSag = physics.state().batteryTransientSagVoltage();

		for (int i = 0; i < 12; i++) {
			physics.step(cut, 0.005);
		}
		double earlyRecoveryVoltage = physics.state().batteryVoltage();
		double earlyTransientSag = physics.state().batteryTransientSagVoltage();

		for (int i = 0; i < 420; i++) {
			physics.step(cut, 0.005);
		}

		assertTrue(loadedTransientSag > 1.45);
		assertTrue(earlyRecoveryVoltage > loadedVoltage + 2.0);
		assertTrue(earlyTransientSag > loadedTransientSag * 0.65);
		assertTrue(physics.state().batteryTransientSagVoltage() < earlyTransientSag * 0.40);
		assertTrue(physics.state().batteryVoltage() > earlyRecoveryVoltage + 0.3);
	}

	@Test
	void activeBrakingRegeneratesCurrentAndRaisesBusVoltage() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.080)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0)
				.withBattery(16.8, 16.7, 0.070, 1.5, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.92, 0.0, 0.0, 0.0, true);
		DroneInput cut = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 240; i++) {
			physics.step(punch, 0.005);
		}
		double loadedVoltage = physics.state().batteryVoltage();

		double maxRegenerativeCurrent = 0.0;
		double maxVoltageSpike = 0.0;
		double maxRecoveryVoltage = loadedVoltage;
		double minBatteryToMotorCurrentRatio = Double.POSITIVE_INFINITY;
		for (int i = 0; i < 4; i++) {
			physics.step(cut, 0.005);
			double motorThermalCurrentSum = 0.0;
			for (double motorCurrent : physics.state().motorCurrentAmps()) {
				motorThermalCurrentSum += motorCurrent;
			}
			maxRegenerativeCurrent = Math.max(maxRegenerativeCurrent, physics.state().batteryRegenerativeCurrentAmps());
			maxVoltageSpike = Math.max(maxVoltageSpike, physics.state().batteryVoltageSpike());
			maxRecoveryVoltage = Math.max(maxRecoveryVoltage, physics.state().batteryVoltage());
			minBatteryToMotorCurrentRatio = Math.min(
					minBatteryToMotorCurrentRatio,
					physics.state().batteryCurrentAmps() / Math.max(1.0e-6, motorThermalCurrentSum)
			);
		}
		assertTrue(maxRegenerativeCurrent > 1.0);
		assertTrue(maxVoltageSpike > 0.004);
		assertTrue(minBatteryToMotorCurrentRatio < 0.85);
		assertTrue(maxRecoveryVoltage > loadedVoltage + 0.25);
	}

	@Test
	void hotMotorWindingResistanceReducesTorqueAuthorityAndEfficiency() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 180.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics coolMotor = new DronePhysics(config);
		DronePhysics hotMotor = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.92, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < config.rotors().size(); i++) {
			coolMotor.state().setMotorTemperatureCelsius(i, 25.0);
			hotMotor.state().setMotorTemperatureCelsius(i, 145.0);
		}

		for (int i = 0; i < 36; i++) {
			coolMotor.step(punch, 0.005);
			hotMotor.step(punch, 0.005);
		}

		assertEquals(1.0, hotMotor.state().motorThermalLimit(), 1.0e-9);
		assertEquals(1.0, coolMotor.state().averageMotorWindingResistanceScale(), 1.0e-9);
		assertTrue(hotMotor.state().averageMotorWindingResistanceScale() > 1.45,
				() -> "hotResistanceScale=" + hotMotor.state().averageMotorWindingResistanceScale());
		assertTrue(hotMotor.state().averageMotorRpm() < coolMotor.state().averageMotorRpm() * 0.97,
				() -> "coolRpm=" + coolMotor.state().averageMotorRpm()
						+ " hotRpm=" + hotMotor.state().averageMotorRpm());
		assertTrue(hotMotor.state().averageMotorPhaseCurrentAmps() < coolMotor.state().averageMotorPhaseCurrentAmps() * 0.94,
				() -> "coolPhaseCurrent=" + coolMotor.state().averageMotorPhaseCurrentAmps()
						+ " hotPhaseCurrent=" + hotMotor.state().averageMotorPhaseCurrentAmps());
		assertTrue(hotMotor.state().averageMotorElectricalEfficiency() < coolMotor.state().averageMotorElectricalEfficiency() - 0.025,
				() -> "coolEfficiency=" + coolMotor.state().averageMotorElectricalEfficiency()
						+ " hotEfficiency=" + hotMotor.state().averageMotorElectricalEfficiency());
	}

	@Test
	void motorRpmAccessorsTrackMotorOmega() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hoverInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 80; i++) {
			physics.step(hoverInput, 0.005);
		}

		double expectedRpm = physics.state().motorOmegaRadiansPerSecond(0) * 60.0 / (Math.PI * 2.0);
		double expectedAverageRpm = 0.0;
		for (double rpm : physics.state().motorRpm()) {
			expectedAverageRpm += rpm;
		}
		expectedAverageRpm /= physics.state().motorCount();
		assertEquals(expectedRpm, physics.state().motorRpm(0), 1.0e-9);
		assertEquals(expectedAverageRpm, physics.state().averageMotorRpm(), 1.0e-9);
		assertTrue(physics.state().averageMotorRpm() > 9000.0,
				() -> "hoverRpm=" + physics.state().averageMotorRpm());
		assertTrue(physics.state().averageMotorRpm() < 18000.0,
				() -> "hoverRpm=" + physics.state().averageMotorRpm());
	}

	@Test
	void dshotProtocolTimingMatchesBetaflightFrameReference() {
		assertEquals(53.333333333333336, EscCommandProtocol.DSHOT300.rawFrameMicroseconds(), 1.0e-12);
		assertEquals(26.666666666666668, EscCommandProtocol.DSHOT600.rawFrameMicroseconds(), 1.0e-12);
		assertEquals(2000, EscCommandProtocol.DSHOT600.throttleSteps());
		assertEquals(0.010666666666666668, EscCommandProtocol.DSHOT600.commandWireUtilization(400.0), 1.0e-12);
		assertEquals(93.75, EscCommandProtocol.DSHOT600.commandIntervalRawFrameRatio(400.0), 1.0e-12);
		assertEquals(46.875, EscCommandProtocol.DSHOT300.commandIntervalRawFrameRatio(400.0), 1.0e-12);
	}

	@Test
	void escCommandProtocolTracksDshotResolutionAndGenericFallback() {
		DroneConfig dshot = DroneConfig.racingQuad();
		assertEquals(EscCommandProtocol.DSHOT600, dshot.escCommandProtocol());
		assertEquals(2000, dshot.escCommandResolutionSteps());

		DroneConfig dshot300 = dshot
				.withEscCommandProtocolBitrate(300.0)
				.withMassKg(dshot.massKg() * 1.01);
		assertEquals(EscCommandProtocol.DSHOT300, dshot300.escCommandProtocol());
		assertEquals(2000, dshot300.escCommandResolutionSteps());

		DroneConfig generic = dshot300.withEscCommandSignal(400.0, 2048.0);
		assertEquals(EscCommandProtocol.GENERIC, generic.escCommandProtocol());
		assertEquals(2048, generic.escCommandResolutionSteps());
		assertEquals(0.0, generic.escCommandProtocol().rawFrameMicroseconds(), 1.0e-12);
	}

	@Test
	void gyroDynamicNotchUsesBidirectionalEscRpmTelemetryFrames() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0)
				.withEscCommandSignal(50.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 180.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.025, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput highThrottle = new DroneInput(0.86, 0.0, 0.0, 0.0, true);
		DroneInput chop = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 260; i++) {
			physics.step(highThrottle, 0.005);
		}
		double highTelemetryNotchHertz = physics.state().gyroDynamicNotchFrequencyHertz();
		double highBladePassNotchHertz = physics.state().gyroBladePassNotchFrequencyHertz();
		double highActualMotorHertz = physics.state().averageMotorRpm() / 60.0;

		double maxTelemetryLagHertz = 0.0;
		for (int i = 0; i < 14; i++) {
			physics.step(chop, 0.005);
			double notchHertz = physics.state().gyroDynamicNotchFrequencyHertz();
			double actualMotorHertz = physics.state().averageMotorRpm() / 60.0;
			maxTelemetryLagHertz = Math.max(maxTelemetryLagHertz, notchHertz - actualMotorHertz);
		}

		assertTrue(highTelemetryNotchHertz > 180.0,
				() -> "highTelemetryNotchHertz=" + highTelemetryNotchHertz);
		assertEquals(highTelemetryNotchHertz * 3.0, highBladePassNotchHertz, 3.0);
		double finalMaxTelemetryLagHertz = maxTelemetryLagHertz;
		assertTrue(finalMaxTelemetryLagHertz > 28.0,
				() -> "highTelemetryNotchHertz=" + highTelemetryNotchHertz
						+ " highActualMotorHertz=" + highActualMotorHertz
						+ " maxTelemetryLagHertz=" + finalMaxTelemetryLagHertz
						+ " finalActualMotorHz=" + physics.state().averageMotorRpm() / 60.0
						+ " finalNotchHz=" + physics.state().gyroDynamicNotchFrequencyHertz());
	}

	@Test
	void bidirectionalEscRpmTelemetryDropsOutBelowValidEintervalBand() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0)
				.withEscCommandSignal(400.0, 2048.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 180.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput zeroThrottle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);
		DroneInput highThrottle = new DroneInput(0.72, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 20; i++) {
			physics.step(zeroThrottle, 0.005);
		}

		assertEquals(0.0, physics.state().averageMotorRpmTelemetryValidity(), 1.0e-9);
		assertEquals(0.0, physics.state().motorRpmTelemetryRpm(0), 1.0e-9);
		assertEquals(
				DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS,
				DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(
						physics.state().motorRpmTelemetryRpm(0),
						physics.state().motorRpmTelemetryValidity(0)
				),
				1.0e-9
		);

		for (int i = 0; i < 180; i++) {
			physics.step(highThrottle, 0.005);
		}

		double telemetryRpm = physics.state().motorRpmTelemetryRpm(0);
		double telemetryValidity = physics.state().motorRpmTelemetryValidity(0);
		double telemetryEinterval = DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(
				telemetryRpm,
				telemetryValidity
		);
		assertTrue(physics.state().averageMotorRpmTelemetryValidity() > 0.95);
		assertTrue(telemetryRpm > 6000.0, () -> "telemetryRpm=" + telemetryRpm);
		assertTrue(telemetryEinterval > 0.0 && telemetryEinterval < DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS,
				() -> "telemetryEinterval=" + telemetryEinterval + " validity=" + telemetryValidity);
	}

	@Test
	void bidirectionalEscRpmTelemetryDropsFramesDuringDesyncBurst() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0)
				.withEscCommandSignal(400.0, 2048.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 180.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.025, 1000.0, 0.0, 0.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics obstructed = new DronePhysics(config);
		DroneInput highThrottle = new DroneInput(0.90, 0.0, 0.0, 0.0, true);
		DroneEnvironment obstructedFlow = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {1.0, 1.0, 1.0, 1.0}
		);

		for (int i = 0; i < 360; i++) {
			clean.step(highThrottle, 0.005);
			obstructed.step(highThrottle, 0.005, obstructedFlow);
		}

		double obstructedTelemetryRpm = obstructed.state().motorRpmTelemetryRpm(0);
		double obstructedTelemetryValidity = obstructed.state().motorRpmTelemetryValidity(0);
		assertTrue(clean.state().averageMotorRpmTelemetryValidity() > 0.95,
				() -> "cleanValidity=" + clean.state().averageMotorRpmTelemetryValidity());
		assertTrue(obstructed.state().maxEscDesyncIntensity() > 0.70,
				() -> "desync=" + obstructed.state().maxEscDesyncIntensity());
		assertTrue(obstructedTelemetryRpm > 1000.0,
				() -> "staleTelemetryRpm=" + obstructedTelemetryRpm);
		assertTrue(obstructedTelemetryValidity < 0.5,
				() -> "obstructedTelemetryValidity=" + obstructedTelemetryValidity);
		assertEquals(
				DronePhysics.BETAFLIGHT_EINTERVAL_INVALID_MICROS,
				DronePhysics.betaflightEIntervalMicrosFromTelemetryRpm(
						obstructedTelemetryRpm,
						obstructedTelemetryValidity
				),
				1.0e-9
		);
		assertTrue(obstructed.state().gyroDynamicNotchFrequencyHertz() > 50.0,
				() -> "staleNotchHz=" + obstructed.state().gyroDynamicNotchFrequencyHertz());
	}

	@Test
	void gyroRpmHarmonicNotchWeakensWhenRpmTelemetryDropsOut() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withRotorImbalanceIntensity(0.18)
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0)
				.withEscCommandSignal(400.0, 2048.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 180.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.025, 1000.0, 0.0, 0.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics obstructed = new DronePhysics(config);
		DroneInput highThrottle = new DroneInput(0.86, 0.0, 0.0, 0.0, true);
		DroneEnvironment obstructedFlow = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {1.0, 1.0, 1.0, 1.0}
		);

		for (int i = 0; i < 360; i++) {
			clean.step(highThrottle, 0.005);
			obstructed.step(highThrottle, 0.005, obstructedFlow);
		}

		double cleanHarmonicNotch = clean.state().gyroRpmHarmonicNotchAttenuation();
		double obstructedHarmonicNotch = obstructed.state().gyroRpmHarmonicNotchAttenuation();
		assertTrue(clean.state().averageMotorRpmTelemetryValidity() > 0.95,
				() -> "cleanValidity=" + clean.state().averageMotorRpmTelemetryValidity());
		assertTrue(obstructed.state().averageMotorRpmTelemetryValidity() < 0.50,
				() -> "obstructedValidity=" + obstructed.state().averageMotorRpmTelemetryValidity());
		assertTrue(cleanHarmonicNotch > 0.25,
				() -> "cleanHarmonicNotch=" + cleanHarmonicNotch
						+ " vibration=" + clean.state().rotorVibration());
		assertTrue(obstructedHarmonicNotch < cleanHarmonicNotch * 0.70,
				() -> "cleanHarmonicNotch=" + cleanHarmonicNotch
						+ " obstructedHarmonicNotch=" + obstructedHarmonicNotch
						+ " obstructedValidity=" + obstructed.state().averageMotorRpmTelemetryValidity());
	}

	@Test
	void gyroNotchSpreadTracksPerMotorRpmTelemetry() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 1.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 180.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.025, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput rollInput = new DroneInput(0.58, 0.0, 0.82, 0.0, true);

		for (int i = 0; i < 240; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.step(rollInput, 0.005);
		}

		double motorSpread = physics.state().gyroDynamicNotchSpreadHertz();
		double bladePassSpread = physics.state().gyroBladePassNotchSpreadHertz();
		assertTrue(physics.state().gyroDynamicNotchFrequencyHertz() > 80.0);
		assertTrue(motorSpread > 25.0, () -> "motorSpread=" + motorSpread);
		assertEquals(
				motorSpread * config.rotors().get(0).bladeCount(),
				bladePassSpread,
				Math.max(2.0, motorSpread * 0.04)
		);
		assertTrue(bladePassSpread > 75.0, () -> "bladePassSpread=" + bladePassSpread);
	}

	@Test
	void sampledFrequencyAliasFoldsBladePassAboveNyquist() {
		double hoverBladePass = DronePhysics.bladePassFrequencyHertz(13023.1, 3);

		assertEquals(651.155, hoverBladePass, 1.0e-9);
		assertEquals(372.845, DronePhysics.sampledFrequencyAliasHertz(hoverBladePass, 1024.0), 1.0e-9);
		assertEquals(433.0, DronePhysics.sampledFrequencyAliasHertz(1457.0, 1024.0), 1.0e-9);
		assertEquals(20.0, DronePhysics.sampledFrequencyAliasHertz(2068.0, 1024.0), 1.0e-9);
		assertEquals(0.0, DronePhysics.bladePassAliasHertz(12000.0, 0, 1024.0), 1.0e-9);
		assertEquals(0.0, DronePhysics.sampledFrequencyAliasHertz(Double.NaN, 1024.0), 1.0e-9);
		assertEquals(0.0, DronePhysics.sampledFrequencyAliasHertz(650.0, 0.0), 1.0e-9);
	}

	@Test
	void aerodynamicTelemetryTracksRelativeAirBodyAngles() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics physics = new DronePhysics(config);
		physics.state().setVelocityMetersPerSecond(new Vec3(3.0, 4.0, 12.0));
		DroneEnvironment environment = new DroneEnvironment(new Vec3(1.0, 0.0, 2.0), 1.0, 3.0);

		physics.step(DroneInput.idle(), 0.005, environment);

		Vec3 relativeAir = physics.state().relativeAirVelocityBodyMetersPerSecond();
		assertEquals(2.0, relativeAir.x(), 1.0e-9);
		assertEquals(4.0, relativeAir.y(), 1.0e-9);
		assertEquals(10.0, relativeAir.z(), 1.0e-9);
		assertEquals(Math.sqrt(120.0), physics.state().airspeedMetersPerSecond(), 1.0e-9);
		assertEquals(Math.atan2(4.0, 10.0), physics.state().angleOfAttackRadians(), 1.0e-9);
		assertEquals(Math.atan2(2.0, 10.0), physics.state().sideslipRadians(), 1.0e-9);
	}

	@Test
	void effectiveAirMassWindLagsAbruptWindShift() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment calm = new DroneEnvironment(Vec3.ZERO, 1.0, Double.POSITIVE_INFINITY, 0.0);
		DroneEnvironment gustFront = new DroneEnvironment(new Vec3(10.0, 0.0, 0.0), 1.0, Double.POSITIVE_INFINITY, 0.0);

		physics.step(idle, 0.005, calm);
		physics.step(idle, 0.005, gustFront);

		assertTrue(physics.state().effectiveWindVelocityWorldMetersPerSecond().x() > 0.0);
		assertTrue(physics.state().effectiveWindVelocityWorldMetersPerSecond().x() < 10.0);
		assertTrue(physics.state().windShearAccelerationMetersPerSecondSquared() > 10.0);
		assertEquals(0.0, physics.state().windGustSpeedMetersPerSecond(), 1.0e-9);

		for (int i = 0; i < 260; i++) {
			physics.step(idle, 0.005, gustFront);
		}

		assertTrue(physics.state().effectiveWindVelocityWorldMetersPerSecond().x() > 9.5);
	}

	@Test
	void turbulentAirMassAddsGustAndWindShearTelemetry() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment smoothWind = new DroneEnvironment(new Vec3(4.0, 0.0, 0.0), 1.0, Double.POSITIVE_INFINITY, 0.0);
		DroneEnvironment dirtyWind = new DroneEnvironment(new Vec3(4.0, 0.0, 0.0), 1.0, Double.POSITIVE_INFINITY, 0.95);

		physics.step(idle, 0.005, smoothWind);
		double smoothGust = physics.state().windGustSpeedMetersPerSecond();
		double maxGust = 0.0;
		double maxShear = 0.0;
		for (int i = 0; i < 160; i++) {
			physics.step(idle, 0.005, dirtyWind);
			maxGust = Math.max(maxGust, physics.state().windGustSpeedMetersPerSecond());
			maxShear = Math.max(maxShear, physics.state().windShearAccelerationMetersPerSecondSquared());
		}

		assertEquals(0.0, smoothGust, 1.0e-9);
		assertTrue(maxGust > 0.15);
		assertTrue(maxShear > 0.20);
		assertTrue(physics.state().effectiveWindVelocityWorldMetersPerSecond().subtract(dirtyWind.windVelocityWorldMetersPerSecond()).length() > 0.05);
	}

	@Test
	void a4mcShearDiagnosticsAddTerrainShearGustTelemetry() {
		DronePhysics smooth = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DronePhysics sheared = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment smoothA4mcWind = a4mcTerrainShearWind(0.0, 0.0);
		DroneEnvironment shearedA4mcWind = a4mcTerrainShearWind(1.35, 2.4);

		for (int i = 0; i < 260; i++) {
			smooth.step(idle, 0.005, smoothA4mcWind);
			sheared.step(idle, 0.005, shearedA4mcWind);
		}

		double smoothMaxGust = 0.0;
		double shearedMaxGust = 0.0;
		double shearedMaxUpdraft = 0.0;
		double shearedMaxTerrainShear = 0.0;
		double shearedMaxAcceleration = 0.0;
		for (int i = 0; i < 240; i++) {
			smooth.step(idle, 0.005, smoothA4mcWind);
			sheared.step(idle, 0.005, shearedA4mcWind);
			smoothMaxGust = Math.max(smoothMaxGust, smooth.state().windGustSpeedMetersPerSecond());
			shearedMaxGust = Math.max(shearedMaxGust, sheared.state().windGustSpeedMetersPerSecond());
			shearedMaxUpdraft = Math.max(shearedMaxUpdraft, sheared.state().a4mcUpdraftSpeedMetersPerSecond());
			shearedMaxTerrainShear = Math.max(shearedMaxTerrainShear, sheared.state().a4mcTerrainShearSpeedMetersPerSecond());
			shearedMaxAcceleration = Math.max(
					shearedMaxAcceleration,
					sheared.state().windShearAccelerationMetersPerSecondSquared()
			);
		}

		assertEquals(0.0, smoothMaxGust, 1.0e-9);
		assertTrue(shearedMaxGust > 0.18, "shearedMaxGust=" + shearedMaxGust);
		assertTrue(shearedMaxUpdraft > 0.30, "shearedMaxUpdraft=" + shearedMaxUpdraft);
		assertTrue(
				shearedMaxGust >= Math.max(shearedMaxTerrainShear, shearedMaxUpdraft),
				"shearedMaxGust=" + shearedMaxGust
						+ " shearedMaxTerrainShear=" + shearedMaxTerrainShear
						+ " shearedMaxUpdraft=" + shearedMaxUpdraft
		);
		assertTrue(shearedMaxAcceleration > 0.25, "shearedMaxAcceleration=" + shearedMaxAcceleration);
	}

	@Test
	void a4mcShelterStrengthensTerrainShearGustTelemetry() {
		DronePhysics exposed = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DronePhysics sheltered = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment exposedA4mcWind = a4mcTerrainShearWind(0.75, 0.0, 0.0);
		DroneEnvironment shelteredA4mcWind = a4mcTerrainShearWind(0.75, 0.0, 0.80);

		for (int i = 0; i < 260; i++) {
			exposed.step(idle, 0.005, exposedA4mcWind);
			sheltered.step(idle, 0.005, shelteredA4mcWind);
		}

		double exposedMaxTerrainShear = 0.0;
		double shelteredMaxTerrainShear = 0.0;
		for (int i = 0; i < 240; i++) {
			exposed.step(idle, 0.005, exposedA4mcWind);
			sheltered.step(idle, 0.005, shelteredA4mcWind);
			exposedMaxTerrainShear = Math.max(exposedMaxTerrainShear, exposed.state().a4mcTerrainShearSpeedMetersPerSecond());
			shelteredMaxTerrainShear = Math.max(shelteredMaxTerrainShear, sheltered.state().a4mcTerrainShearSpeedMetersPerSecond());
		}

		assertTrue(exposedMaxTerrainShear > 0.05, "exposedMaxTerrainShear=" + exposedMaxTerrainShear);
		assertTrue(
				shelteredMaxTerrainShear > exposedMaxTerrainShear * 1.25,
				"exposedMaxTerrainShear=" + exposedMaxTerrainShear + " shelteredMaxTerrainShear=" + shelteredMaxTerrainShear
		);
		assertTrue(shelteredMaxTerrainShear < 1.40, "shelteredMaxTerrainShear=" + shelteredMaxTerrainShear);
	}

	@Test
	void a4mcSourceGustSpeedAddsCoherentAirMassGust() {
		DronePhysics smooth = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DronePhysics gusty = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment smoothA4mcWind = a4mcSourceGustWind(0.0);
		DroneEnvironment gustyA4mcWind = a4mcSourceGustWind(4.0);

		for (int i = 0; i < 260; i++) {
			smooth.step(idle, 0.005, smoothA4mcWind);
			gusty.step(idle, 0.005, gustyA4mcWind);
		}

		double smoothMaxGust = 0.0;
		double gustyMaxGust = 0.0;
		double gustyMaxSourceGust = 0.0;
		double gustyMaxTerrainShear = 0.0;
		for (int i = 0; i < 240; i++) {
			smooth.step(idle, 0.005, smoothA4mcWind);
			gusty.step(idle, 0.005, gustyA4mcWind);
			smoothMaxGust = Math.max(smoothMaxGust, smooth.state().windGustSpeedMetersPerSecond());
			gustyMaxGust = Math.max(gustyMaxGust, gusty.state().windGustSpeedMetersPerSecond());
			gustyMaxSourceGust = Math.max(gustyMaxSourceGust, gusty.state().a4mcSourceGustSpeedMetersPerSecond());
			gustyMaxTerrainShear = Math.max(gustyMaxTerrainShear, gusty.state().a4mcTerrainShearSpeedMetersPerSecond());
		}

		assertEquals(0.0, smoothMaxGust, 1.0e-9);
		assertTrue(gustyMaxGust > 0.20, "gustyMaxGust=" + gustyMaxGust);
		assertTrue(gustyMaxGust < 1.40, "gustyMaxGust=" + gustyMaxGust);
		assertEquals(gustyMaxGust, gustyMaxSourceGust, 1.0e-9);
		assertEquals(0.0, gusty.state().a4mcUpdraftSpeedMetersPerSecond(), 1.0e-9);
		assertEquals(0.0, gustyMaxTerrainShear, 1.0e-9);
		assertVecClose(
				gusty.state().windGustVelocityWorldMetersPerSecond(),
				gusty.state().drydenTurbulenceVelocityWorldMetersPerSecond()
						.add(gusty.state().windBurbleVelocityWorldMetersPerSecond())
						.add(gusty.state().a4mcSourceGustVelocityWorldMetersPerSecond())
						.add(gusty.state().a4mcUpdraftVelocityWorldMetersPerSecond())
						.add(gusty.state().a4mcTerrainShearVelocityWorldMetersPerSecond()),
				1.0e-12
		);
	}

	@Test
	void a4mcSourceGustVectorSetsCoherentAirMassDirection() {
		DronePhysics gusty = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment vectorGust = a4mcSourceGustWind(4.0, new Vec3(0.0, 3.0, 0.0));

		for (int i = 0; i < 260; i++) {
			gusty.step(idle, 0.005, vectorGust);
		}

		Vec3 source = gusty.state().a4mcSourceGustVelocityWorldMetersPerSecond();
		assertTrue(source.y() > 0.35, "source=" + source);
		assertEquals(0.0, source.x(), 1.0e-9);
		assertEquals(0.0, source.z(), 1.0e-9);
		assertTrue(source.length() < 0.80, "source=" + source);
	}

	@Test
	void a4mcUpdraftAddsSignedVerticalAirMassAndRotorAxialGust() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics smooth = new DronePhysics(config);
		DronePhysics updraft = new DronePhysics(config);
		DronePhysics downdraft = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment smoothA4mcWind = a4mcUpdraftOnlyWind(0.0);
		DroneEnvironment updraftA4mcWind = a4mcUpdraftOnlyWind(12.0);
		DroneEnvironment downdraftA4mcWind = a4mcUpdraftOnlyWind(-12.0);

		for (int i = 0; i < 520; i++) {
			holdInCruise(smooth, Vec3.ZERO);
			holdInCruise(updraft, Vec3.ZERO);
			holdInCruise(downdraft, Vec3.ZERO);
			smooth.step(hover, 0.005, smoothA4mcWind);
			updraft.step(hover, 0.005, updraftA4mcWind);
			downdraft.step(hover, 0.005, downdraftA4mcWind);
		}

		double smoothAxialGustScale = smooth.state().averageRotorAxialGustThrustScale();
		double updraftAxialGustScale = updraft.state().averageRotorAxialGustThrustScale();
		double downdraftAxialGustScale = downdraft.state().averageRotorAxialGustThrustScale();
		assertEquals(0.0, smooth.state().a4mcUpdraftSpeedMetersPerSecond(), 1.0e-9);
		assertTrue(updraft.state().a4mcUpdraftVelocityWorldMetersPerSecond().y() > 2.0,
				() -> "updraft=" + updraft.state().a4mcUpdraftVelocityWorldMetersPerSecond());
		assertTrue(downdraft.state().a4mcUpdraftVelocityWorldMetersPerSecond().y() < -2.0,
				() -> "downdraft=" + downdraft.state().a4mcUpdraftVelocityWorldMetersPerSecond());
		assertTrue(
				updraft.state().a4mcTerrainShearSpeedMetersPerSecond() < updraft.state().a4mcUpdraftSpeedMetersPerSecond() * 0.25,
				"updraftTerrainShear=" + updraft.state().a4mcTerrainShearSpeedMetersPerSecond()
						+ " updraft=" + updraft.state().a4mcUpdraftSpeedMetersPerSecond()
		);
		assertTrue(
				downdraft.state().a4mcTerrainShearSpeedMetersPerSecond() < downdraft.state().a4mcUpdraftSpeedMetersPerSecond() * 0.25,
				"downdraftTerrainShear=" + downdraft.state().a4mcTerrainShearSpeedMetersPerSecond()
						+ " downdraft=" + downdraft.state().a4mcUpdraftSpeedMetersPerSecond()
		);
		assertTrue(updraft.state().windGustSpeedMetersPerSecond() > smooth.state().windGustSpeedMetersPerSecond() + 2.0);
		assertTrue(downdraft.state().windGustSpeedMetersPerSecond() > smooth.state().windGustSpeedMetersPerSecond() + 2.0);
		assertTrue(
				updraftAxialGustScale > smoothAxialGustScale + 0.01,
				"updraftAxialGustScale=" + updraftAxialGustScale + " smoothAxialGustScale=" + smoothAxialGustScale
		);
		assertTrue(
				downdraftAxialGustScale < smoothAxialGustScale - 0.01,
				"downdraftAxialGustScale=" + downdraftAxialGustScale + " smoothAxialGustScale=" + smoothAxialGustScale
		);
	}

	@Test
	void a4mcSourceQualityGatesCoreGustAndTerrainShear() {
		DroneEnvironment trustedGust = a4mcSourceGustWind(4.0, new Vec3(0.0, 3.0, 0.0), true, 1.0, 0L);
		DroneEnvironment lowConfidenceGust = a4mcSourceGustWind(4.0, new Vec3(0.0, 3.0, 0.0), true, 0.50, 0L);
		DroneEnvironment staleGust = a4mcSourceGustWind(4.0, new Vec3(0.0, 3.0, 0.0), true, 1.0, 160L);
		DroneEnvironment trustedUpdraft = a4mcUpdraftOnlyWind(12.0, true, 1.0, 0L);
		DroneEnvironment lowConfidenceUpdraft = a4mcUpdraftOnlyWind(12.0, true, 0.50, 0L);
		DroneEnvironment staleUpdraft = a4mcUpdraftOnlyWind(12.0, true, 1.0, 160L);
		DroneEnvironment untrustedUpdraft = a4mcUpdraftOnlyWind(12.0, false, 1.0, 0L);
		DroneEnvironment trustedShear = a4mcTerrainShearWind(1.35, 2.4, 0.80, true, 1.0, 0L);
		DroneEnvironment lowConfidenceShear = a4mcTerrainShearWind(1.35, 2.4, 0.80, true, 0.50, 0L);
		DroneEnvironment untrustedShear = a4mcTerrainShearWind(1.35, 2.4, 0.80, false, 1.0, 0L);

		double trustedSourceGust = maxA4mcSourceGustFor(trustedGust);
		double lowConfidenceSourceGust = maxA4mcSourceGustFor(lowConfidenceGust);
		double staleSourceGust = maxA4mcSourceGustFor(staleGust);
		double trustedA4mcUpdraft = maxA4mcUpdraftFor(trustedUpdraft);
		double lowConfidenceA4mcUpdraft = maxA4mcUpdraftFor(lowConfidenceUpdraft);
		double staleA4mcUpdraft = maxA4mcUpdraftFor(staleUpdraft);
		double untrustedA4mcUpdraft = maxA4mcUpdraftFor(untrustedUpdraft);
		double trustedTerrainShear = maxA4mcTerrainShearFor(trustedShear);
		double lowConfidenceTerrainShear = maxA4mcTerrainShearFor(lowConfidenceShear);
		double untrustedTerrainShear = maxA4mcTerrainShearFor(untrustedShear);

		assertTrue(trustedSourceGust > 0.35, "trustedSourceGust=" + trustedSourceGust);
		assertTrue(
				lowConfidenceSourceGust > trustedSourceGust * 0.35
						&& lowConfidenceSourceGust < trustedSourceGust * 0.65,
				"trustedSourceGust=" + trustedSourceGust + " lowConfidenceSourceGust=" + lowConfidenceSourceGust
		);
		assertEquals(0.0, staleSourceGust, 1.0e-12);
		assertTrue(trustedA4mcUpdraft > 2.0, "trustedA4mcUpdraft=" + trustedA4mcUpdraft);
		assertTrue(
				lowConfidenceA4mcUpdraft > trustedA4mcUpdraft * 0.30
						&& lowConfidenceA4mcUpdraft < trustedA4mcUpdraft * 0.90,
				"trustedA4mcUpdraft=" + trustedA4mcUpdraft + " lowConfidenceA4mcUpdraft=" + lowConfidenceA4mcUpdraft
		);
		assertEquals(0.0, staleA4mcUpdraft, 1.0e-12);
		assertEquals(0.0, untrustedA4mcUpdraft, 1.0e-12);
		assertTrue(trustedTerrainShear > 0.18, "trustedTerrainShear=" + trustedTerrainShear);
		assertTrue(
				lowConfidenceTerrainShear > trustedTerrainShear * 0.20
						&& lowConfidenceTerrainShear < trustedTerrainShear * 0.70,
				"trustedTerrainShear=" + trustedTerrainShear + " lowConfidenceTerrainShear=" + lowConfidenceTerrainShear
		);
		assertEquals(0.0, untrustedTerrainShear, 1.0e-12);
	}

	@Test
	void a4mcTerrainShearFilterIgnoresUntrustedSourceDiagnostics() {
		DronePhysics rawUntrusted = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DronePhysics neutralUntrusted = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment trustedShear = a4mcTerrainShearWind(1.35, 2.4, 0.80, true, 1.0, 0L);
		DroneEnvironment rawUntrustedShear = a4mcTerrainShearWind(1.35, 2.4, 0.80, false, 1.0, 0L);
		DroneEnvironment neutralUntrustedShear = a4mcTerrainShearWind(0.0, 0.0, 0.0, false, 1.0, 0L);

		for (int i = 0; i < 320; i++) {
			rawUntrusted.step(idle, 0.005, trustedShear);
			neutralUntrusted.step(idle, 0.005, trustedShear);
		}
		assertVecClose(
				rawUntrusted.state().a4mcTerrainShearVelocityWorldMetersPerSecond(),
				neutralUntrusted.state().a4mcTerrainShearVelocityWorldMetersPerSecond(),
				1.0e-12
		);
		assertTrue(rawUntrusted.state().a4mcTerrainShearSpeedMetersPerSecond() > 0.02);

		for (int i = 0; i < 32; i++) {
			rawUntrusted.step(idle, 0.005, rawUntrustedShear);
			neutralUntrusted.step(idle, 0.005, neutralUntrustedShear);
		}

		assertVecClose(
				rawUntrusted.state().a4mcTerrainShearVelocityWorldMetersPerSecond(),
				neutralUntrusted.state().a4mcTerrainShearVelocityWorldMetersPerSecond(),
				1.0e-12
		);
		assertTrue(rawUntrusted.state().a4mcTerrainShearSpeedMetersPerSecond() > 0.0);
	}

	@Test
	void dirtyAirMassUsesDrydenScaleGustTelemetry() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DronePhysics repeat = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment dirtyWind = new DroneEnvironment(new Vec3(10.0, 0.0, 0.0), 1.0, 6.0, 1.5);
		double sumGustXSquared = 0.0;
		double sumGustYSquared = 0.0;
		double sumGustZSquared = 0.0;
		int samples = 0;

		for (int i = 0; i < 1800; i++) {
			physics.step(idle, 0.005, dirtyWind);
			repeat.step(idle, 0.005, dirtyWind);
			if (i >= 240) {
				Vec3 gust = physics.state().windGustVelocityWorldMetersPerSecond();
				sumGustXSquared += gust.x() * gust.x();
				sumGustYSquared += gust.y() * gust.y();
				sumGustZSquared += gust.z() * gust.z();
				samples++;
			}
		}

		double rmsGustX = Math.sqrt(sumGustXSquared / samples);
		double rmsGustY = Math.sqrt(sumGustYSquared / samples);
		double rmsGustZ = Math.sqrt(sumGustZSquared / samples);
		assertTrue(rmsGustX > 0.40, () -> "rmsGustX=" + rmsGustX);
		assertTrue(rmsGustY > 0.10, () -> "rmsGustY=" + rmsGustY);
		assertTrue(rmsGustZ > 0.40, () -> "rmsGustZ=" + rmsGustZ);
		assertTrue(rmsGustX < 4.50, () -> "rmsGustX=" + rmsGustX);
		assertTrue(rmsGustY < rmsGustX, () -> "rmsGustY=" + rmsGustY + " rmsGustX=" + rmsGustX);
		assertTrue(rmsGustZ < 4.50, () -> "rmsGustZ=" + rmsGustZ);
		assertEquals(0.0, physics.state().windGustVelocityWorldMetersPerSecond()
				.subtract(repeat.state().windGustVelocityWorldMetersPerSecond()).length(), 1.0e-12);
		assertTrue(physics.state().drydenTurbulenceSpeedMetersPerSecond() > 0.05);
		assertTrue(physics.state().windBurbleSpeedMetersPerSecond() > 0.0);
		assertEquals(0.0, physics.state().a4mcSourceGustSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, physics.state().a4mcUpdraftSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, physics.state().a4mcTerrainShearSpeedMetersPerSecond(), 1.0e-12);
		assertVecClose(
				physics.state().windGustVelocityWorldMetersPerSecond(),
				physics.state().drydenTurbulenceVelocityWorldMetersPerSecond()
						.add(physics.state().windBurbleVelocityWorldMetersPerSecond())
						.add(physics.state().a4mcSourceGustVelocityWorldMetersPerSecond())
						.add(physics.state().a4mcUpdraftVelocityWorldMetersPerSecond())
						.add(physics.state().a4mcTerrainShearVelocityWorldMetersPerSecond()),
				1.0e-12
		);
	}

	@Test
	void a4mcAblMixingShapesDrydenTurbulence() {
		DroneEnvironment neutralAbl = a4mcAblWind(0.55, 0.0, 0.0);
		DroneEnvironment unstableMixedAbl = a4mcAblWind(0.55, 0.90, 0.90);
		DroneEnvironment stableMixedAbl = a4mcAblWind(0.55, -0.90, 0.90);

		double neutralDrydenRms = drydenRmsForEnvironment(neutralAbl);
		double unstableDrydenRms = drydenRmsForEnvironment(unstableMixedAbl);
		double stableDrydenRms = drydenRmsForEnvironment(stableMixedAbl);

		assertTrue(unstableDrydenRms > neutralDrydenRms * 1.30,
				() -> "unstable=" + unstableDrydenRms + " neutral=" + neutralDrydenRms);
		assertTrue(stableDrydenRms < neutralDrydenRms * 0.94,
				() -> "stable=" + stableDrydenRms + " neutral=" + neutralDrydenRms);
	}

	@Test
	void a4mcAblMixingShapesDrydenTimeScale() {
		DroneEnvironment neutralAbl = a4mcAblWind(0.55, 0.0, 0.0);
		DroneEnvironment unstableMixedAbl = a4mcAblWind(0.55, 0.90, 0.90);
		DroneEnvironment stableMixedAbl = a4mcAblWind(0.55, -0.90, 0.90);

		double neutralStepRate = drydenNormalizedStepRateForEnvironment(neutralAbl);
		double unstableStepRate = drydenNormalizedStepRateForEnvironment(unstableMixedAbl);
		double stableStepRate = drydenNormalizedStepRateForEnvironment(stableMixedAbl);

		assertTrue(unstableStepRate > neutralStepRate * 1.12,
				() -> "unstable=" + unstableStepRate + " neutral=" + neutralStepRate);
		assertTrue(stableStepRate < neutralStepRate * 0.94,
				() -> "stable=" + stableStepRate + " neutral=" + neutralStepRate);
	}

	@Test
	void a4mcAblMixingFadesWithSourceQuality() {
		DroneEnvironment neutralAbl = a4mcAblWind(0.55, 0.0, 0.0);
		DroneEnvironment trustedAbl = a4mcAblWind(0.55, 0.90, 0.90, true, 1.0, 0L);
		DroneEnvironment lowConfidenceAbl = a4mcAblWind(0.55, 0.90, 0.90, true, 0.50, 0L);
		DroneEnvironment staleAbl = a4mcAblWind(0.55, 0.90, 0.90, true, 1.0, 160L);

		double neutralDrydenRms = drydenRmsForEnvironment(neutralAbl);
		double trustedDrydenRms = drydenRmsForEnvironment(trustedAbl);
		double lowConfidenceDrydenRms = drydenRmsForEnvironment(lowConfidenceAbl);
		double staleDrydenRms = drydenRmsForEnvironment(staleAbl);

		assertTrue(trustedDrydenRms > neutralDrydenRms * 1.30,
				() -> "trusted=" + trustedDrydenRms + " neutral=" + neutralDrydenRms);
		assertTrue(
				lowConfidenceDrydenRms > neutralDrydenRms
						&& lowConfidenceDrydenRms < trustedDrydenRms,
				() -> "lowConfidence=" + lowConfidenceDrydenRms
						+ " neutral=" + neutralDrydenRms
						+ " trusted=" + trustedDrydenRms
		);
		assertEquals(neutralDrydenRms, staleDrydenRms, 1.0e-12);
	}

	@Test
	void restoredAerodynamicTransientStateContinuesWindAndAirframeFilters() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics source = new DronePhysics(config);
		DronePhysics restored = new DronePhysics(config);
		DroneInput idle = DroneInput.idle();
		Vec3 cruiseVelocity = new Vec3(8.0, -1.5, 13.0);
		DroneEnvironment dirtyNearSurfaceWind = new DroneEnvironment(
				new Vec3(10.0, 0.0, 2.0),
				1.0,
				0.12,
				1.20,
				0.75,
				0.45,
				Double.POSITIVE_INFINITY
		);

		for (int i = 0; i < 420; i++) {
			holdInCruise(source, cruiseVelocity);
			source.step(idle, 0.005, dirtyNearSurfaceWind);
		}
		DronePhysics.AerodynamicTransientState snapshot = source.aerodynamicTransientStateSnapshot();
		assertTrue(snapshot.windGustVelocityWorldMetersPerSecond().length() > 0.10,
				() -> "gust=" + snapshot.windGustVelocityWorldMetersPerSecond());
		assertTrue(snapshot.airframeDragForceBody().length() > 0.30,
				() -> "drag=" + snapshot.airframeDragForceBody());

		holdInCruise(restored, cruiseVelocity);
		restored.restoreAerodynamicTransientState(snapshot);
		assertVecClose(snapshot.windGustVelocityWorldMetersPerSecond(), restored.state().windGustVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(snapshot.drydenTurbulenceVelocityWorldMetersPerSecond(), restored.state().drydenTurbulenceVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(snapshot.windBurbleVelocityWorldMetersPerSecond(), restored.state().windBurbleVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(snapshot.a4mcSourceGustVelocityWorldMetersPerSecond(), restored.state().a4mcSourceGustVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(snapshot.a4mcUpdraftVelocityWorldMetersPerSecond(), restored.state().a4mcUpdraftVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(snapshot.a4mcTerrainShearVelocityWorldMetersPerSecond(), restored.state().a4mcTerrainShearVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(snapshot.airframeDragForceBody(), restored.state().airframeBodyDragForceBodyNewtons(), 1.0e-12);
		assertEquals(snapshot.airframeSeparatedFlowIntensity(), restored.state().airframeSeparatedFlowIntensity(), 1.0e-12);

		holdInCruise(source, cruiseVelocity);
		holdInCruise(restored, cruiseVelocity);
		source.step(idle, 0.005, dirtyNearSurfaceWind);
		restored.step(idle, 0.005, dirtyNearSurfaceWind);

		assertVecClose(source.state().effectiveWindVelocityWorldMetersPerSecond(), restored.state().effectiveWindVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(source.state().windGustVelocityWorldMetersPerSecond(), restored.state().windGustVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(source.state().drydenTurbulenceVelocityWorldMetersPerSecond(), restored.state().drydenTurbulenceVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(source.state().windBurbleVelocityWorldMetersPerSecond(), restored.state().windBurbleVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(source.state().a4mcSourceGustVelocityWorldMetersPerSecond(), restored.state().a4mcSourceGustVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(source.state().a4mcUpdraftVelocityWorldMetersPerSecond(), restored.state().a4mcUpdraftVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(source.state().a4mcTerrainShearVelocityWorldMetersPerSecond(), restored.state().a4mcTerrainShearVelocityWorldMetersPerSecond(), 1.0e-12);
		assertVecClose(source.state().airframeBodyDragForceBodyNewtons(), restored.state().airframeBodyDragForceBodyNewtons(), 1.0e-12);
		assertVecClose(source.state().airframeLiftForceBodyNewtons(), restored.state().airframeLiftForceBodyNewtons(), 1.0e-12);
		assertVecClose(source.state().groundEffectDragForceBodyNewtons(), restored.state().groundEffectDragForceBodyNewtons(), 1.0e-12);
		assertVecClose(source.state().groundEffectLevelingTorqueBodyNewtonMeters(), restored.state().groundEffectLevelingTorqueBodyNewtonMeters(), 1.0e-12);
		assertEquals(source.state().airframeSeparatedFlowIntensity(), restored.state().airframeSeparatedFlowIntensity(), 1.0e-12);
		assertEquals(source.aerodynamicTransientStateSnapshot().drydenRandomState(),
				restored.aerodynamicTransientStateSnapshot().drydenRandomState());
	}

	@Test
	void atmosphericTurbulenceStaysDrydenWhileLocalizedDirtyAirAddsBurble() {
		DronePhysics openAir = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DronePhysics localizedDirtyAir = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment openTurbulence = new DroneEnvironment(new Vec3(10.0, 0.0, 0.0), 1.0, 6.0, 1.5);
		DroneEnvironment obstacleWakeTurbulence = new DroneEnvironment(new Vec3(10.0, 0.0, 0.0), 1.0, 6.0, 1.5, 1.0, 1.5);
		double openDrydenSquared = 0.0;
		double openBurbleSquared = 0.0;
		double localizedDrydenSquared = 0.0;
		double localizedBurbleSquared = 0.0;
		int samples = 0;

		for (int i = 0; i < 2200; i++) {
			openAir.step(idle, 0.005, openTurbulence);
			localizedDirtyAir.step(idle, 0.005, obstacleWakeTurbulence);

			Vec3 openDryden = openAir.state().drydenTurbulenceVelocityWorldMetersPerSecond();
			Vec3 openBurble = openAir.state().windBurbleVelocityWorldMetersPerSecond();
			Vec3 localizedDryden = localizedDirtyAir.state().drydenTurbulenceVelocityWorldMetersPerSecond();
			Vec3 localizedBurble = localizedDirtyAir.state().windBurbleVelocityWorldMetersPerSecond();
			if (i >= 240) {
				openDrydenSquared += openDryden.lengthSquared();
				openBurbleSquared += openBurble.lengthSquared();
				localizedDrydenSquared += localizedDryden.lengthSquared();
				localizedBurbleSquared += localizedBurble.lengthSquared();
				samples++;
			}
		}

		double openDrydenRms = Math.sqrt(openDrydenSquared / samples);
		double openBurbleRms = Math.sqrt(openBurbleSquared / samples);
		double localizedDrydenRms = Math.sqrt(localizedDrydenSquared / samples);
		double localizedBurbleRms = Math.sqrt(localizedBurbleSquared / samples);
		assertTrue(openDrydenRms > 0.80, () -> "openDrydenRms=" + openDrydenRms);
		assertTrue(openDrydenRms > openBurbleRms * 3.0,
				() -> "openDrydenRms=" + openDrydenRms + " openBurbleRms=" + openBurbleRms);
		assertEquals(openDrydenRms, localizedDrydenRms, 1.0e-12);
		assertTrue(localizedBurbleRms > openBurbleRms * 1.45,
				() -> "localizedBurbleRms=" + localizedBurbleRms + " openBurbleRms=" + openBurbleRms);
	}

	@Test
	void localizedDirtyAirFeedsBurbleWithoutLargeScaleDrydenTurbulence() {
		DronePhysics localizedDirtyAir = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DronePhysics openAtmosphere = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		DroneEnvironment obstacleWake = new DroneEnvironment(
				new Vec3(10.0, 0.0, 0.0),
				1.0,
				6.0,
				0.0,
				1.0,
				1.5
		);
		DroneEnvironment atmosphericTurbulence = new DroneEnvironment(new Vec3(10.0, 0.0, 0.0), 1.0, 6.0, 1.0);
		double maxLocalizedBurble = 0.0;
		double maxLocalizedDryden = 0.0;
		double maxAtmosphericDryden = 0.0;

		for (int i = 0; i < 1400; i++) {
			localizedDirtyAir.step(idle, 0.005, obstacleWake);
			openAtmosphere.step(idle, 0.005, atmosphericTurbulence);
			if (i >= 160) {
				maxLocalizedBurble = Math.max(maxLocalizedBurble, localizedDirtyAir.state().windBurbleSpeedMetersPerSecond());
				maxLocalizedDryden = Math.max(maxLocalizedDryden, localizedDirtyAir.state().drydenTurbulenceSpeedMetersPerSecond());
				maxAtmosphericDryden = Math.max(maxAtmosphericDryden, openAtmosphere.state().drydenTurbulenceSpeedMetersPerSecond());
			}
		}

		double observedMaxLocalizedBurble = maxLocalizedBurble;
		double observedMaxLocalizedDryden = maxLocalizedDryden;
		double observedMaxAtmosphericDryden = maxAtmosphericDryden;
		assertTrue(observedMaxLocalizedBurble > 0.12, () -> "maxLocalizedBurble=" + observedMaxLocalizedBurble);
		assertTrue(observedMaxLocalizedDryden < 0.010, () -> "maxLocalizedDryden=" + observedMaxLocalizedDryden);
		assertTrue(observedMaxAtmosphericDryden > observedMaxLocalizedDryden + 0.20,
				() -> "maxAtmosphericDryden=" + observedMaxAtmosphericDryden
						+ " maxLocalizedDryden=" + observedMaxLocalizedDryden);
	}

	@Test
	void nearGroundBoundaryLayerReducesHorizontalWindAndAddsShear() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics openAir = new DronePhysics(config);
		DronePhysics nearGround = new DronePhysics(config);
		DroneInput idle = DroneInput.idle();
		Vec3 crosswind = new Vec3(10.0, 0.0, 0.0);
		DroneEnvironment freeStream = new DroneEnvironment(crosswind, 1.0, 3.0, 0.0);
		DroneEnvironment nearSurface = new DroneEnvironment(crosswind, 1.0, 0.08, 0.0);

		double maxNearGroundGust = 0.0;
		for (int i = 0; i < 260; i++) {
			openAir.step(idle, 0.005, freeStream);
			nearGround.step(idle, 0.005, nearSurface);
			maxNearGroundGust = Math.max(maxNearGroundGust, nearGround.state().windGustSpeedMetersPerSecond());
		}

		double openWind = openAir.state().effectiveWindVelocityWorldMetersPerSecond().x();
		double boundaryLayerWind = nearGround.state().effectiveWindVelocityWorldMetersPerSecond().x();
		assertTrue(openWind > 9.5);
		assertTrue(boundaryLayerWind < openWind * 0.35);
		assertTrue(maxNearGroundGust > 0.02);

		nearGround.step(idle, 0.005, freeStream);

		assertTrue(nearGround.state().windShearAccelerationMetersPerSecondSquared() > 8.0);
	}

	@Test
	void nearCeilingBoundaryLayerReducesHorizontalWindAndAddsShear() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics openAir = new DronePhysics(config);
		DronePhysics nearCeiling = new DronePhysics(config);
		DroneInput idle = DroneInput.idle();
		Vec3 crosswind = new Vec3(10.0, 0.0, 0.0);
		DroneEnvironment freeStream = new DroneEnvironment(crosswind, 1.0, 3.0, 0.0);
		DroneEnvironment ceilingBoundary = new DroneEnvironment(
				crosswind,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				0.08
		);

		double maxNearCeilingGust = 0.0;
		for (int i = 0; i < 260; i++) {
			openAir.step(idle, 0.005, freeStream);
			nearCeiling.step(idle, 0.005, ceilingBoundary);
			maxNearCeilingGust = Math.max(maxNearCeilingGust, nearCeiling.state().windGustSpeedMetersPerSecond());
		}

		double openWind = openAir.state().effectiveWindVelocityWorldMetersPerSecond().x();
		double boundaryLayerWind = nearCeiling.state().effectiveWindVelocityWorldMetersPerSecond().x();
		assertTrue(openWind > 9.5);
		assertTrue(boundaryLayerWind < openWind * 0.35);
		assertTrue(maxNearCeilingGust > 0.02);

		nearCeiling.step(idle, 0.005, freeStream);

		assertTrue(nearCeiling.state().windShearAccelerationMetersPerSecondSquared() > 8.0);
	}

	@Test
	void airframeLiftAddsBodyForceFromAngleOfAttackAndSideslip() {
		DroneConfig cleanConfig = directControl(DroneConfig.racingQuad()).withBodyDragCoefficients(Vec3.ZERO);
		DroneConfig liftingConfig = cleanConfig.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32));
		DronePhysics clean = new DronePhysics(cleanConfig);
		DronePhysics lifting = new DronePhysics(liftingConfig);
		Vec3 fastSlip = new Vec3(5.0, 4.0, 20.0);

		for (int i = 0; i < 90; i++) {
			holdInCruise(clean, fastSlip);
			holdInCruise(lifting, fastSlip);
			clean.step(DroneInput.idle(), 0.005);
			lifting.step(DroneInput.idle(), 0.005);
		}

		assertEquals(0.0, clean.state().airframeLiftForceBodyNewtons().length(), 1.0e-9);
		assertTrue(lifting.state().airframeLiftForceBodyNewtons().length() > 1.0);
		assertTrue(lifting.state().airframeLiftForceBodyNewtons().y() > 0.0);
		assertTrue(lifting.state().airframeLiftForceBodyNewtons().x() < 0.0);
		assertTrue(lifting.state().linearAccelerationWorldMetersPerSecondSquared().y() > clean.state().linearAccelerationWorldMetersPerSecondSquared().y());
	}

	@Test
	void poweredRotorWashAddsAirframeLiftAtZeroAirspeed() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics powered = new DronePhysics(config);
		DronePhysics cleanDeck = new DronePhysics(config.withBodyDragCoefficients(Vec3.ZERO));
		DronePhysics unpowered = new DronePhysics(config);
		DroneInput loadedHover = new DroneInput(config.hoverThrottle() + 0.06, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 180; i++) {
			holdInStillAir(powered);
			holdInStillAir(cleanDeck);
			holdInStillAir(unpowered);
			powered.step(loadedHover, 0.005);
			cleanDeck.step(loadedHover, 0.005);
			unpowered.step(DroneInput.idle(), 0.005);
		}

		Vec3 poweredLift = powered.state().airframeLiftForceBodyNewtons();
		double weight = config.massKg() * config.gravityMetersPerSecondSquared();
		assertEquals(0.0, cleanDeck.state().airframeLiftForceBodyNewtons().length(), 1.0e-9);
		assertEquals(0.0, unpowered.state().airframeLiftForceBodyNewtons().length(), 1.0e-9);
		assertTrue(poweredLift.y() > 0.12, () -> "poweredLift=" + poweredLift);
		assertTrue(poweredLift.y() < weight * 0.09, () -> "poweredLift=" + poweredLift + " weight=" + weight);
		assertEquals(0.0, poweredLift.x(), 1.0e-9);
		assertEquals(0.0, poweredLift.z(), 1.0e-9);
	}

	@Test
	void airframeLiftForceBuildsAndRecoversWithAerodynamicLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32));
		DronePhysics physics = new DronePhysics(config);
		Vec3 straightVelocity = new Vec3(0.0, 0.0, 18.0);
		Vec3 slipVelocity = new Vec3(5.0, 4.0, 20.0);

		holdInCruise(physics, slipVelocity);
		physics.step(DroneInput.idle(), 0.005);
		Vec3 firstSlipLift = physics.state().airframeLiftForceBodyNewtons();

		for (int i = 0; i < 90; i++) {
			holdInCruise(physics, slipVelocity);
			physics.step(DroneInput.idle(), 0.005);
		}
		Vec3 settledSlipLift = physics.state().airframeLiftForceBodyNewtons();

		holdInCruise(physics, straightVelocity);
		physics.step(DroneInput.idle(), 0.005);
		Vec3 lingeringStraightLift = physics.state().airframeLiftForceBodyNewtons();

		for (int i = 0; i < 260; i++) {
			holdInCruise(physics, straightVelocity);
			physics.step(DroneInput.idle(), 0.005);
		}
		Vec3 recoveredStraightLift = physics.state().airframeLiftForceBodyNewtons();

		assertTrue(firstSlipLift.length() > 0.4, () -> "firstSlipLift=" + firstSlipLift);
		assertTrue(settledSlipLift.length() > firstSlipLift.length() * 5.0,
				() -> "firstSlipLift=" + firstSlipLift + " settledSlipLift=" + settledSlipLift);
		assertTrue(settledSlipLift.y() > 0.0, () -> "settledSlipLift=" + settledSlipLift);
		assertTrue(settledSlipLift.x() < 0.0, () -> "settledSlipLift=" + settledSlipLift);
		assertTrue(lingeringStraightLift.length() > recoveredStraightLift.length() + 2.0,
				() -> "lingeringStraightLift=" + lingeringStraightLift + " recoveredStraightLift=" + recoveredStraightLift);
		assertTrue(recoveredStraightLift.length() < 0.01,
				() -> "recoveredStraightLift=" + recoveredStraightLift);
	}

	@Test
	void highSideslipAirframeSeparationAddsBroadsideDragRise() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.04));
		DronePhysics shallowSlip = new DronePhysics(config);
		DronePhysics highSlip = new DronePhysics(config);
		Vec3 shallowVelocity = new Vec3(2.0, 0.0, 4.0);
		Vec3 highSlipVelocity = new Vec3(10.0, 0.0, 2.0);
		for (int i = 0; i < 90; i++) {
			holdInCruise(shallowSlip, shallowVelocity);
			holdInCruise(highSlip, highSlipVelocity);
			shallowSlip.step(DroneInput.idle(), 0.005);
			highSlip.step(DroneInput.idle(), 0.005);
		}

		assertTrue(Math.toDegrees(shallowSlip.state().sideslipRadians()) < 32.0);
		assertTrue(Math.toDegrees(highSlip.state().sideslipRadians()) > 75.0);
		assertTrue(highSlip.state().airframeSeparatedFlowIntensity() > 0.85);
		assertTrue(highSlip.state().linearAccelerationWorldMetersPerSecondSquared().x()
				< shallowSlip.state().linearAccelerationWorldMetersPerSecondSquared().x() - 20.0);
		assertTrue(highSlip.state().velocityMetersPerSecond().x() < highSlipVelocity.x() - 0.02);
	}

	@Test
	void airframeBodyDragBuildsAndRecoversWithPressureLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32));
		DronePhysics physics = new DronePhysics(config);
		Vec3 forwardVelocity = new Vec3(0.0, 0.0, 18.0);

		holdInCruise(physics, forwardVelocity);
		physics.step(DroneInput.idle(), 0.005);
		double firstForwardAccelerationZ = physics.state().linearAccelerationWorldMetersPerSecondSquared().z();

		for (int i = 0; i < 90; i++) {
			holdInCruise(physics, forwardVelocity);
			physics.step(DroneInput.idle(), 0.005);
		}
		double settledForwardAccelerationZ = physics.state().linearAccelerationWorldMetersPerSecondSquared().z();

		holdInStillAir(physics);
		physics.step(DroneInput.idle(), 0.005);
		double lingeringStillAccelerationZ = physics.state().linearAccelerationWorldMetersPerSecondSquared().z();

		for (int i = 0; i < 260; i++) {
			holdInStillAir(physics);
			physics.step(DroneInput.idle(), 0.005);
		}
		double recoveredStillAccelerationZ = physics.state().linearAccelerationWorldMetersPerSecondSquared().z();

		assertTrue(firstForwardAccelerationZ < -2.0,
				() -> "firstForwardAccelerationZ=" + firstForwardAccelerationZ);
		assertTrue(settledForwardAccelerationZ < firstForwardAccelerationZ - 20.0,
				() -> "firstForwardAccelerationZ=" + firstForwardAccelerationZ
						+ " settledForwardAccelerationZ=" + settledForwardAccelerationZ);
		assertTrue(lingeringStillAccelerationZ < recoveredStillAccelerationZ - 20.0,
				() -> "lingeringStillAccelerationZ=" + lingeringStillAccelerationZ
						+ " recoveredStillAccelerationZ=" + recoveredStillAccelerationZ);
		assertEquals(0.0, recoveredStillAccelerationZ, 0.05);
	}

	@Test
	void airframeSeparatedFlowBuildsAndRecoversWithLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.04));
		DronePhysics physics = new DronePhysics(config);
		Vec3 cleanForwardVelocity = new Vec3(0.0, 0.0, 12.0);
		Vec3 highSideslipVelocity = new Vec3(10.0, 0.0, 2.0);

		for (int i = 0; i < 140; i++) {
			holdInCruise(physics, cleanForwardVelocity);
			physics.step(DroneInput.idle(), 0.005);
		}
		double cleanSeparatedFlow = physics.state().airframeSeparatedFlowIntensity();

		holdInCruise(physics, highSideslipVelocity);
		physics.step(DroneInput.idle(), 0.005);
		double firstHighSlipSeparatedFlow = physics.state().airframeSeparatedFlowIntensity();

		for (int i = 0; i < 180; i++) {
			holdInCruise(physics, highSideslipVelocity);
			physics.step(DroneInput.idle(), 0.005);
		}
		double settledHighSlipSeparatedFlow = physics.state().airframeSeparatedFlowIntensity();
		double highSideslipDegrees = Math.toDegrees(physics.state().sideslipRadians());

		holdInCruise(physics, cleanForwardVelocity);
		physics.step(DroneInput.idle(), 0.005);
		double firstRecoveredSeparatedFlow = physics.state().airframeSeparatedFlowIntensity();

		for (int i = 0; i < 260; i++) {
			holdInCruise(physics, cleanForwardVelocity);
			physics.step(DroneInput.idle(), 0.005);
		}
		double recoveredSeparatedFlow = physics.state().airframeSeparatedFlowIntensity();

		assertTrue(cleanSeparatedFlow < 0.02);
		assertTrue(highSideslipDegrees > 78.0);
		assertTrue(firstHighSlipSeparatedFlow < settledHighSlipSeparatedFlow * 0.35,
				() -> "firstHighSlipSeparatedFlow=" + firstHighSlipSeparatedFlow
						+ " settledHighSlipSeparatedFlow=" + settledHighSlipSeparatedFlow);
		assertTrue(settledHighSlipSeparatedFlow > 0.88);
		assertTrue(firstRecoveredSeparatedFlow > settledHighSlipSeparatedFlow * 0.80,
				() -> "firstRecoveredSeparatedFlow=" + firstRecoveredSeparatedFlow
						+ " settledHighSlipSeparatedFlow=" + settledHighSlipSeparatedFlow);
		assertTrue(recoveredSeparatedFlow < 0.04,
				() -> "recoveredSeparatedFlow=" + recoveredSeparatedFlow);
	}

	@Test
	void separatedFlowBuffetAddsUnsteadyAirframeTorque() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.04));
		DronePhysics shallowSlip = new DronePhysics(config);
		DronePhysics highSlip = new DronePhysics(config);
		Vec3 shallowVelocity = new Vec3(2.0, 0.0, 18.0);
		Vec3 highSlipVelocity = new Vec3(14.0, 3.0, 4.0);
		double shallowMinYaw = Double.POSITIVE_INFINITY;
		double shallowMaxYaw = Double.NEGATIVE_INFINITY;
		double highMinYaw = Double.POSITIVE_INFINITY;
		double highMaxYaw = Double.NEGATIVE_INFINITY;

		for (int i = 0; i < 320; i++) {
			holdInCruise(shallowSlip, shallowVelocity);
			holdInCruise(highSlip, highSlipVelocity);
			shallowSlip.step(DroneInput.idle(), 0.005);
			highSlip.step(DroneInput.idle(), 0.005);

			if (i >= 80) {
				double shallowYawTorque = shallowSlip.state().airframeAerodynamicTorqueBodyNewtonMeters().y();
				double highYawTorque = highSlip.state().airframeAerodynamicTorqueBodyNewtonMeters().y();
				shallowMinYaw = Math.min(shallowMinYaw, shallowYawTorque);
				shallowMaxYaw = Math.max(shallowMaxYaw, shallowYawTorque);
				highMinYaw = Math.min(highMinYaw, highYawTorque);
				highMaxYaw = Math.max(highMaxYaw, highYawTorque);
			}
		}

		double shallowYawRange = shallowMaxYaw - shallowMinYaw;
		double highYawRange = highMaxYaw - highMinYaw;
		assertTrue(Math.toDegrees(shallowSlip.state().sideslipRadians()) < 10.0);
		assertTrue(Math.toDegrees(highSlip.state().sideslipRadians()) > 70.0);
		assertTrue(shallowYawRange < 0.004, () -> "shallowYawRange=" + shallowYawRange);
		assertTrue(highYawRange > shallowYawRange + 0.040,
				() -> "shallowYawRange=" + shallowYawRange + " highYawRange=" + highYawRange);
	}

	@Test
	void turbulenceEnvironmentAddsDeterministicDisturbanceTorque() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics calm = new DronePhysics(config);
		DronePhysics turbulent = new DronePhysics(config);
		DroneInput idle = DroneInput.idle();
		calm.state().setVelocityMetersPerSecond(new Vec3(12.0, 1.0, 4.0));
		turbulent.state().setVelocityMetersPerSecond(new Vec3(12.0, 1.0, 4.0));

		calm.step(idle, 0.005, new DroneEnvironment(Vec3.ZERO, 1.0, 10.0, 0.0));
		turbulent.step(idle, 0.005, new DroneEnvironment(Vec3.ZERO, 1.0, 10.0, 0.85));

		assertEquals(0.0, calm.state().windTurbulenceTorqueBodyNewtonMeters().length(), 1.0e-12);
		assertTrue(turbulent.state().windTurbulenceTorqueBodyNewtonMeters().length() > 1.0e-4);
		assertTrue(turbulent.state().windTurbulenceTorqueBodyNewtonMeters().subtract(calm.state().windTurbulenceTorqueBodyNewtonMeters()).length() > 1.0e-4);
	}

	@Test
	void dirtyAirSourcesFeedWindTurbulenceTorque() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics clean = new DronePhysics(config);
		DronePhysics obstructed = new DronePhysics(config);
		DronePhysics nearGroundCrosswind = new DronePhysics(config);
		DroneInput idle = DroneInput.idle();
		Vec3 fastCruise = new Vec3(12.0, 1.0, 4.0);
		clean.state().setVelocityMetersPerSecond(fastCruise);
		obstructed.state().setVelocityMetersPerSecond(fastCruise);
		nearGroundCrosswind.state().setVelocityMetersPerSecond(fastCruise);

		clean.step(idle, 0.005, new DroneEnvironment(Vec3.ZERO, 1.0, 3.0, 0.0));
		obstructed.step(idle, 0.005, new DroneEnvironment(Vec3.ZERO, 1.0, 3.0, 0.0, 1.0));
		nearGroundCrosswind.step(idle, 0.005, new DroneEnvironment(new Vec3(10.0, 0.0, 0.0), 1.0, 0.08, 0.0));

		assertEquals(0.0, clean.state().windTurbulenceTorqueBodyNewtonMeters().length(), 1.0e-12);
		assertTrue(obstructed.state().windTurbulenceTorqueBodyNewtonMeters().length() > 1.0e-4);
		assertTrue(nearGroundCrosswind.state().windTurbulenceTorqueBodyNewtonMeters().length() > 1.0e-4);
	}

	@Test
	void environmentClampsObstacleProximityTelemetry() {
		DroneEnvironment low = new DroneEnvironment(Vec3.ZERO, 1.0, 10.0, 0.0, -0.25);
		DroneEnvironment high = new DroneEnvironment(Vec3.ZERO, 1.0, 10.0, 0.0, 1.75);

		assertEquals(0.0, low.obstacleProximity(), 1.0e-12);
		assertEquals(1.0, high.obstacleProximity(), 1.0e-12);
	}

	@Test
	void environmentClampsAmbientTemperatureTelemetry() {
		DroneEnvironment cold = new DroneEnvironment(Vec3.ZERO, 1.0, 10.0, 0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, null, null, null, null, 0.0, 0.0, -120.0);
		DroneEnvironment hot = new DroneEnvironment(Vec3.ZERO, 1.0, 10.0, 0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, null, null, null, null, 0.0, 0.0, 120.0);
		DroneEnvironment invalid = new DroneEnvironment(Vec3.ZERO, 1.0, 10.0, 0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, null, null, null, null, 0.0, 0.0, Double.NaN);

		assertEquals(-40.0, cold.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(65.0, hot.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(25.0, invalid.ambientTemperatureCelsius(), 1.0e-12);
	}

	@Test
	void environmentProvidesPerRotorPrecipitationWetnessFallbackAndClamping() {
		DroneEnvironment environment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				10.0,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				new double[] {-0.25, 0.25, 1.75, Double.NaN},
				0.40,
				25.0
		);

		assertEquals(0.0, environment.rotorPrecipitationWetness(0), 1.0e-12);
		assertEquals(0.25, environment.rotorPrecipitationWetness(1), 1.0e-12);
		assertEquals(1.0, environment.rotorPrecipitationWetness(2), 1.0e-12);
		assertEquals(0.0, environment.rotorPrecipitationWetness(3), 1.0e-12);
		assertEquals(0.40, environment.rotorPrecipitationWetness(4), 1.0e-12);
		assertEquals(1.0, environment.maxRotorPrecipitationWetness(), 1.0e-12);

		double[] wetnesses = environment.rotorPrecipitationWetnesses();
		wetnesses[1] = 0.90;
		assertEquals(0.25, environment.rotorPrecipitationWetness(1), 1.0e-12);
	}

	@Test
	void standardAtmosphereDensityFallsWithAltitudeAndHeat() {
		double seaLevelStandard = DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, 15.0);
		double mountainStandard = DroneEnvironment.standardAtmosphereAirDensityRatio(3000.0, -4.5);
		double hotSeaLevel = DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, 38.0);
		double coldSeaLevel = DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, -10.0);
		double stormLowSeaLevel = DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, 15.0, -2200.0);

		assertEquals(1.0, seaLevelStandard, 0.02);
		assertTrue(mountainStandard < seaLevelStandard * 0.80);
		assertTrue(hotSeaLevel < seaLevelStandard);
		assertTrue(coldSeaLevel > seaLevelStandard);
		assertTrue(stormLowSeaLevel < seaLevelStandard);
		assertTrue(DroneEnvironment.speedOfSoundMetersPerSecond(-10.0) < DroneEnvironment.speedOfSoundMetersPerSecond(35.0));
		assertTrue(DroneEnvironment.barometricPressureHectopascals(3000.0, mountainStandard, -4.5) < 730.0);
		assertTrue(DroneEnvironment.barometricPressureHectopascals(0.0, seaLevelStandard, 15.0) > 1000.0);
		assertEquals(
				DroneEnvironment.barometricPressureHectopascals(0.0, seaLevelStandard, 15.0) - 22.0,
				DroneEnvironment.barometricPressureHectopascals(0.0, seaLevelStandard, 15.0, -2200.0),
				1.0e-9
		);
	}

	@Test
	void wetHotAirReducesEffectiveDensity() {
		DroneEnvironment dryHot = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				42.0
		);
		DroneEnvironment saturatedHot = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				1.0,
				42.0
		);
		DroneEnvironment saturatedCold = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				1.0,
				2.0
		);

		assertEquals(1.0, dryHot.effectiveAirDensityRatio(), 1.0e-12);
		assertTrue(saturatedHot.effectiveAirDensityRatio() < 0.980);
		assertTrue(saturatedCold.effectiveAirDensityRatio() > saturatedHot.effectiveAirDensityRatio());
		assertTrue(DroneEnvironment.moistAirDensityMultiplier(42.0, 1.0)
				< DroneEnvironment.moistAirDensityMultiplier(42.0, 0.25));
	}

	@Test
	void batteryCurrentTracksPerMotorLoadDuringMixerWork() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withRateProfile(Vec3.ZERO, Vec3.ZERO)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics hover = new DronePhysics(config);
		DronePhysics roll = new DronePhysics(config);
		DroneInput hoverInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneInput rollInput = new DroneInput(config.hoverThrottle(), 0.0, 1.0, 0.0, true);

		for (int i = 0; i < 160; i++) {
			hover.step(hoverInput, 0.005);
			roll.step(rollInput, 0.005);
		}

		double motorCurrentSum = 0.0;
		double motorCurrentRippleSum = 0.0;
		double minMotorCurrent = Double.POSITIVE_INFINITY;
		double[] motorCurrents = roll.state().motorCurrentAmps();
		double[] motorCurrentRipples = roll.state().motorCurrentRippleAmps();
		for (int i = 0; i < motorCurrents.length; i++) {
			motorCurrentSum += motorCurrents[i];
			motorCurrentRippleSum += motorCurrentRipples[i];
			minMotorCurrent = Math.min(minMotorCurrent, motorCurrents[i]);
		}

		assertEquals(1.2 + motorCurrentSum - 0.35 * motorCurrentRippleSum, roll.state().batteryCurrentAmps(), 1.0e-6);
		assertTrue(roll.state().maxMotorCurrentAmps() > minMotorCurrent * 1.20);
		assertTrue(roll.state().batteryCurrentAmps() > 10.0);
	}

	@Test
	void rotorAccelerationInertiaAddsTransientCurrent() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.018)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics noInertia = new DronePhysics(base.withRotorInertiaKgMetersSquared(0.0));
		DronePhysics highInertia = new DronePhysics(base.withRotorInertiaKgMetersSquared(2.8e-4));
		DroneInput punch = new DroneInput(0.92, 0.0, 0.0, 0.0, true);

		noInertia.step(punch, 0.005);
		highInertia.step(punch, 0.005);

		assertTrue(noInertia.state().averageMotorAngularAccelerationRadiansPerSecondSquared() > 30_000.0);
		assertTrue(highInertia.state().averageMotorAngularAccelerationRadiansPerSecondSquared()
				< noInertia.state().averageMotorAngularAccelerationRadiansPerSecondSquared() * 0.40);
		assertTrue(highInertia.state().motorCurrentAmps(0) > noInertia.state().motorCurrentAmps(0) + 5.0);
		assertTrue(highInertia.state().batteryCurrentAmps() > noInertia.state().batteryCurrentAmps() + 20.0);
	}

	@Test
	void motorBackEmfLimitsAccelerationNearNoLoadSpeed() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.010)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics spinup = new DronePhysics(config);
		DronePhysics nearKvLimit = new DronePhysics(config);
		double nearNoLoadOmega = config.rotors().get(0).maxOmegaRadiansPerSecond() * 0.94;
		for (int i = 0; i < nearKvLimit.state().motorCount(); i++) {
			nearKvLimit.state().setMotorOmegaRadiansPerSecond(i, nearNoLoadOmega);
		}
		DroneInput fullThrottle = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		spinup.step(fullThrottle, 0.005);
		nearKvLimit.step(fullThrottle, 0.005);

		assertTrue(spinup.state().averageMotorAngularAccelerationRadiansPerSecondSquared() > 10_000.0);
		assertTrue(nearKvLimit.state().averageMotorAngularAccelerationRadiansPerSecondSquared()
				< spinup.state().averageMotorAngularAccelerationRadiansPerSecondSquared() * 0.20);
		assertTrue(nearKvLimit.state().averageMotorVoltageHeadroom() < 0.35);
		assertTrue(nearKvLimit.state().averageMotorCurrentAmps() < spinup.state().averageMotorCurrentAmps());
	}

	@Test
	void motorResponseSlowsUnderLowVoltageAndRotorLoad() {
		DroneConfig strong = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.025)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withRotorInertiaKgMetersSquared(1.6e-5);
		DroneConfig weak = strong
				.withBattery(16.8, 13.2, 0.0, 0.20, 90.0)
				.withRotorInertiaKgMetersSquared(1.8e-4);
		DronePhysics strongPack = new DronePhysics(strong);
		DronePhysics weakLoadedPack = new DronePhysics(weak);
		double weakCapacityAmpSeconds = weak.batteryCapacityAmpHours() * 3600.0;
		weakLoadedPack.state().setBatteryAmpSecondsConsumed(weakCapacityAmpSeconds * 0.70);
		weakLoadedPack.step(new DroneInput(0.0, 0.0, 0.0, 0.0, true), 0.005);
		DroneInput punch = new DroneInput(0.90, 0.0, 0.0, 0.0, true);

		strongPack.step(punch, 0.005);
		weakLoadedPack.step(punch, 0.005);

		assertTrue(weakLoadedPack.state().batteryVoltage() < strongPack.state().batteryVoltage() - 1.5);
		assertTrue(weakLoadedPack.state().averageMotorAngularAccelerationRadiansPerSecondSquared()
				< strongPack.state().averageMotorAngularAccelerationRadiansPerSecondSquared() * 0.45);
		assertTrue(weakLoadedPack.state().averageMotorPower(weakLoadedPack.config())
				< strongPack.state().averageMotorPower(strongPack.config()) * 0.70);
	}

	@Test
	void motorBackEmfHeadroomSlowsResponseNearVoltageLimit() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.030)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.8, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics highHeadroom = new DronePhysics(config);
		DronePhysics lowHeadroom = new DronePhysics(config);
		double maxOmega = config.rotors().get(0).maxOmegaRadiansPerSecond();
		double highHeadroomStart = maxOmega * 0.40;
		double lowHeadroomStart = maxOmega * 0.92;

		for (int i = 0; i < config.rotors().size(); i++) {
			highHeadroom.state().setMotorOmegaRadiansPerSecond(i, highHeadroomStart);
			lowHeadroom.state().setMotorOmegaRadiansPerSecond(i, lowHeadroomStart);
		}

		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);
		highHeadroom.step(punch, 0.005);
		lowHeadroom.step(punch, 0.005);

		double highHeadroomAlpha = (highHeadroom.state().motorOmegaRadiansPerSecond(0) - highHeadroomStart) / (maxOmega - highHeadroomStart);
		double lowHeadroomAlpha = (lowHeadroom.state().motorOmegaRadiansPerSecond(0) - lowHeadroomStart) / (maxOmega - lowHeadroomStart);

		assertTrue(highHeadroom.state().minMotorVoltageHeadroom() > 0.45);
		assertTrue(lowHeadroom.state().minMotorVoltageHeadroom() < 0.35);
		assertTrue(lowHeadroomAlpha < highHeadroomAlpha * 0.82);
	}

	@Test
	void lowVoltageHeadroomReducesMotorElectricalEfficiency() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.45)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 13.2, 0.0, 20.0, 180.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withRotorInertiaKgMetersSquared(0.0);
		DronePhysics healthyRail = new DronePhysics(config);
		DronePhysics weakRail = new DronePhysics(config);
		double startOmega = config.rotors().get(0).maxOmegaRadiansPerSecond() * 0.78;
		for (int i = 0; i < config.rotors().size(); i++) {
			healthyRail.state().setMotorOmegaRadiansPerSecond(i, startOmega);
			weakRail.state().setMotorOmegaRadiansPerSecond(i, startOmega);
		}
		healthyRail.state().setBatteryVoltage(16.8);
		weakRail.state().setBatteryVoltage(12.4);
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		healthyRail.step(punch, 0.005);
		weakRail.step(punch, 0.005);

		assertTrue(weakRail.state().averageMotorVoltageHeadroom()
						< healthyRail.state().averageMotorVoltageHeadroom() - 0.16,
				() -> "healthyHeadroom=" + healthyRail.state().averageMotorVoltageHeadroom()
						+ " weakHeadroom=" + weakRail.state().averageMotorVoltageHeadroom());
		assertTrue(weakRail.state().averageMotorElectricalEfficiency()
						< healthyRail.state().averageMotorElectricalEfficiency() - 0.035,
				() -> "healthyEfficiency=" + healthyRail.state().averageMotorElectricalEfficiency()
						+ " weakEfficiency=" + weakRail.state().averageMotorElectricalEfficiency());
		assertTrue(weakRail.state().averageMotorCurrentAmps() > 20.0);
	}

	@Test
	void motorShaftPowerTracksPropellerLoad() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics hover = new DronePhysics(config);
		DronePhysics punch = new DronePhysics(config);
		DroneInput hoverInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneInput punchInput = new DroneInput(0.90, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 90; i++) {
			hover.step(hoverInput, 0.005);
			punch.step(punchInput, 0.005);
		}

		assertTrue(hover.state().averageMotorAerodynamicTorqueNewtonMeters() > 0.005);
		assertTrue(punch.state().averageMotorAerodynamicTorqueNewtonMeters() > hover.state().averageMotorAerodynamicTorqueNewtonMeters() * 2.0);
		assertTrue(punch.state().averageMotorShaftPowerWatts() > hover.state().averageMotorShaftPowerWatts() * 3.0);
		assertTrue(punch.state().batteryCurrentAmps() > hover.state().batteryCurrentAmps() + 20.0);
	}

	@Test
	void propellerPowerScaleShapesMotorCurrentEstimate() throws ReflectiveOperationException {
		Method motorCurrentEstimate = DronePhysics.class.getDeclaredMethod(
				"estimateMotorCurrent",
				int.class
		);
		motorCurrentEstimate.setAccessible(true);

		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.8, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics staticProp = new DronePhysics(config);
		DronePhysics forwardProp = new DronePhysics(config);
		DronePhysics highLoadProp = new DronePhysics(config);
		preparePropellerPowerCurrentEstimate(staticProp, 0.62, 0.45, 1.0, 1.0);
		preparePropellerPowerCurrentEstimate(forwardProp, 0.62, 0.45, 1.0, 0.688);
		preparePropellerPowerCurrentEstimate(highLoadProp, 0.62, 0.45, 1.0, 1.08);

		double staticCurrent = recordDouble(motorCurrentEstimate.invoke(staticProp, 0), "dischargeCurrentAmps");
		double forwardCurrent = recordDouble(motorCurrentEstimate.invoke(forwardProp, 0), "dischargeCurrentAmps");
		double highLoadCurrent = recordDouble(motorCurrentEstimate.invoke(highLoadProp, 0), "dischargeCurrentAmps");

		assertTrue(forwardCurrent < staticCurrent - 0.80,
				() -> "staticCurrent=" + staticCurrent + " forwardCurrent=" + forwardCurrent);
		assertTrue(highLoadCurrent > staticCurrent + 0.15,
				() -> "staticCurrent=" + staticCurrent + " highLoadCurrent=" + highLoadCurrent);
	}

	@Test
	void mqtbBenchAnchorPullsFiveInchTriBladeCurrentTowardBenchCurve() throws ReflectiveOperationException {
		Method motorCurrentEstimate = DronePhysics.class.getDeclaredMethod(
				"estimateMotorCurrent",
				int.class
		);
		motorCurrentEstimate.setAccessible(true);

		DroneConfig baseConfig = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.8, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics triBlade = new DronePhysics(baseConfig);
		DronePhysics twoBlade = new DronePhysics(baseConfig.withRotorBladeCount(2));
		preparePropellerPowerCurrentEstimate(triBlade, 0.62, 0.45, 1.0, 1.0);
		preparePropellerPowerCurrentEstimate(twoBlade, 0.62, 0.45, 1.0, 1.0);
		triBlade.state().setMotorShaftPowerWatts(0, 180.0);
		twoBlade.state().setMotorShaftPowerWatts(0, 180.0);

		double triBladeCurrent = recordDouble(motorCurrentEstimate.invoke(triBlade, 0), "dischargeCurrentAmps");
		double twoBladeCurrent = recordDouble(motorCurrentEstimate.invoke(twoBlade, 0), "dischargeCurrentAmps");
		double benchCurrent = MotorBenchCurrentModel.mqtbHq5x4x3CurrentAmpsForThrustNewtons(
				triBlade.state().rotorThrustNewtons(0)
		);

		assertTrue(Math.abs(triBladeCurrent - benchCurrent) < Math.abs(twoBladeCurrent - benchCurrent) * 0.65,
				() -> "triBladeCurrent=" + triBladeCurrent
						+ " twoBladeCurrent=" + twoBladeCurrent
						+ " benchCurrent=" + benchCurrent);
		assertTrue(triBladeCurrent < twoBladeCurrent - 1.0,
				() -> "triBladeCurrent=" + triBladeCurrent + " twoBladeCurrent=" + twoBladeCurrent);
		assertTrue(triBladeCurrent > benchCurrent * 0.80,
				() -> "triBladeCurrent=" + triBladeCurrent + " benchCurrent=" + benchCurrent);
	}

	@Test
	void apDronePdfBenchAnchorPullsMotorCurrentTowardPdfCurve() throws ReflectiveOperationException {
		Method motorCurrentEstimate = DronePhysics.class.getDeclaredMethod(
				"estimateMotorCurrent",
				int.class
		);
		motorCurrentEstimate.setAccessible(true);

		DroneConfig baseConfig = directControl(DroneConfig.apDrone())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.8, 0.0, 20.0, 150.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics apDroneAnchored = new DronePhysics(baseConfig);
		DronePhysics noPdfAnchor = new DronePhysics(baseConfig.withRotorBladeCount(2));
		preparePropellerPowerCurrentEstimate(apDroneAnchored, 0.62, 0.45, 1.0, 1.0);
		preparePropellerPowerCurrentEstimate(noPdfAnchor, 0.62, 0.45, 1.0, 1.0);
		apDroneAnchored.state().setMotorShaftPowerWatts(0, 240.0);
		noPdfAnchor.state().setMotorShaftPowerWatts(0, 240.0);

		double anchoredCurrent = recordDouble(motorCurrentEstimate.invoke(apDroneAnchored, 0), "dischargeCurrentAmps");
		double unanchoredCurrent = recordDouble(motorCurrentEstimate.invoke(noPdfAnchor, 0), "dischargeCurrentAmps");
		double benchCurrent = MotorBenchCurrentModel.apDronePdf5045CurrentAmpsForThrustNewtons(
				apDroneAnchored.state().rotorThrustNewtons(0)
		);

		assertTrue(Math.abs(anchoredCurrent - benchCurrent) < Math.abs(unanchoredCurrent - benchCurrent) * 0.55,
				() -> "anchoredCurrent=" + anchoredCurrent
						+ " unanchoredCurrent=" + unanchoredCurrent
						+ " benchCurrent=" + benchCurrent);
		assertTrue(anchoredCurrent < unanchoredCurrent - 5.0,
				() -> "anchoredCurrent=" + anchoredCurrent + " unanchoredCurrent=" + unanchoredCurrent);
		assertTrue(anchoredCurrent > benchCurrent * 1.20,
				() -> "anchoredCurrent=" + anchoredCurrent + " benchCurrent=" + benchCurrent);
	}

	@Test
	void propellerPowerScaleShapesMotorSpinupLoad() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.035)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.8, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withRotorInertiaKgMetersSquared(1.6e-5);
		DronePhysics staticProp = new DronePhysics(config);
		DronePhysics unloadedProp = new DronePhysics(config);
		DronePhysics highLoadProp = new DronePhysics(config);
		preparePropellerPowerSpinupEstimate(staticProp, 0.38, 1.0);
		preparePropellerPowerSpinupEstimate(unloadedProp, 0.38, 0.688);
		preparePropellerPowerSpinupEstimate(highLoadProp, 0.38, 1.08);
		DroneInput input = new DroneInput(0.72, 0.0, 0.0, 0.0, true);

		staticProp.step(input, 0.005);
		unloadedProp.step(input, 0.005);
		highLoadProp.step(input, 0.005);

		double maxOmega = config.rotors().get(0).maxOmegaRadiansPerSecond();
		double staticTarget = staticProp.state().motorTargetOmegaRadiansPerSecond(0);
		double unloadedTarget = unloadedProp.state().motorTargetOmegaRadiansPerSecond(0);
		double highLoadTarget = highLoadProp.state().motorTargetOmegaRadiansPerSecond(0);
		double staticOmega = staticProp.state().motorOmegaRadiansPerSecond(0);
		double unloadedOmega = unloadedProp.state().motorOmegaRadiansPerSecond(0);
		double highLoadOmega = highLoadProp.state().motorOmegaRadiansPerSecond(0);

		assertTrue(unloadedTarget > staticTarget + maxOmega * 0.0035,
				() -> "staticTarget=" + staticTarget + " unloadedTarget=" + unloadedTarget);
		assertTrue(highLoadTarget < staticTarget - maxOmega * 0.0015,
				() -> "staticTarget=" + staticTarget + " highLoadTarget=" + highLoadTarget);
		assertTrue(unloadedOmega > staticOmega + maxOmega * 0.00004,
				() -> "staticOmega=" + staticOmega + " unloadedOmega=" + unloadedOmega);
		assertTrue(highLoadOmega < staticOmega - maxOmega * 0.00001,
				() -> "staticOmega=" + staticOmega + " highLoadOmega=" + highLoadOmega);
	}

	@Test
	void applyingBatteryPresetRecomputesVoltageFromStateOfCharge() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		for (int i = 0; i < 20; i++) {
			physics.step(new DroneInput(0.7, 0.0, 0.0, 0.0, true), 0.005);
		}
		double stateOfCharge = physics.state().batteryStateOfCharge();

		physics.applyConfig(DroneConfig.heavyLift());

		assertEquals(stateOfCharge, physics.state().batteryStateOfCharge(), 1.0e-6);
		assertTrue(physics.state().batteryVoltage() > 20.0);
	}

	@Test
	void lowBatteryLimitsAvailableThrust() {
		DroneConfig config = directControl(DroneConfig.racingQuad()).withBattery(16.8, 13.2, 0.018, 0.2, 90.0);
		DronePhysics fullBattery = new DronePhysics(config);
		DronePhysics lowBattery = new DronePhysics(config);
		double capacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0;
		lowBattery.state().setBatteryAmpSecondsConsumed(capacityAmpSeconds * 0.96);
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 250; i++) {
			fullBattery.step(punch, 0.005);
			lowBattery.step(punch, 0.005);
		}

		assertTrue(lowBattery.state().batteryPowerLimit() < 0.45);
		assertTrue(lowBattery.state().batteryEffectiveResistanceOhms() > fullBattery.state().batteryEffectiveResistanceOhms() * 1.25);
		assertTrue(lowBattery.state().averageMotorPower(lowBattery.config()) < fullBattery.state().averageMotorPower(fullBattery.config()) * 0.75);
	}

	@Test
	void sustainedHighPowerHeatsMotorsAndThermallyLimitsThrust() {
		DroneConfig noHeatConfig = directControl(DroneConfig.racingQuad()).withMotorThermal(0.0, 0.05, 45.0, 60.0);
		DroneConfig hotConfig = directControl(DroneConfig.racingQuad()).withMotorThermal(85.0, 0.015, 45.0, 60.0);
		DronePhysics noHeat = new DronePhysics(noHeatConfig);
		DronePhysics hot = new DronePhysics(hotConfig);
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 700; i++) {
			noHeat.step(punch, 0.005);
			hot.step(punch, 0.005);
		}

		assertTrue(hot.state().maxMotorTemperatureCelsius() > 60.0);
		assertTrue(hot.state().motorThermalLimit() < 0.7);
		assertTrue(hot.state().averageMotorPower(hot.config()) < noHeat.state().averageMotorPower(noHeat.config()) * 0.85);
	}

	@Test
	void sustainedHighCurrentHeatsEscsAndThermallyLimitsPower() {
		DroneConfig noHeatConfig = directControl(DroneConfig.racingQuad()).withMotorThermal(0.0, 0.05, 45.0, 60.0);
		DroneConfig hotConfig = directControl(DroneConfig.racingQuad()).withMotorThermal(85.0, 0.010, 45.0, 60.0);
		DronePhysics noHeat = new DronePhysics(noHeatConfig);
		DronePhysics hot = new DronePhysics(hotConfig);
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 700; i++) {
			noHeat.step(punch, 0.005);
			hot.step(punch, 0.005);
		}

		assertTrue(hot.state().maxEscTemperatureCelsius() > 49.0);
		assertTrue(hot.state().escThermalLimit() < 0.82);
		assertTrue(hot.state().averageMotorPower(hot.config()) < noHeat.state().averageMotorPower(noHeat.config()) * 0.90);
	}

	@Test
	void localAirflowAndObstructionChangeMotorCooling() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorThermal(70.0, 0.20, 200.0, 240.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics obstructed = new DronePhysics(config);
		for (int i = 0; i < clean.state().motorCount(); i++) {
			clean.state().setMotorTemperatureCelsius(i, 85.0);
			obstructed.state().setMotorTemperatureCelsius(i, 85.0);
		}

		DroneInput cruise = new DroneInput(0.42, 0.0, 0.0, 0.0, true);
		Vec3 crossflow = new Vec3(12.0, 0.0, 0.0);
		DroneEnvironment cleanAir = new DroneEnvironment(Vec3.ZERO, 1.0, Double.POSITIVE_INFINITY);
		DroneEnvironment blockedRotor = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {0.75, 0.0, 0.0, 0.0}
		);

		for (int i = 0; i < 260; i++) {
			clean.state().setOrientation(Quaternion.IDENTITY);
			obstructed.state().setOrientation(Quaternion.IDENTITY);
			clean.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			obstructed.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			clean.state().setVelocityMetersPerSecond(crossflow);
			obstructed.state().setVelocityMetersPerSecond(crossflow);
			clean.step(cruise, 0.005, cleanAir);
			obstructed.step(cruise, 0.005, blockedRotor);
		}

		assertTrue(clean.state().averageMotorCoolingFactor() > 1.25);
		assertTrue(obstructed.state().motorCoolingFactor(0) < obstructed.state().motorCoolingFactor(1) * 0.72);
		assertTrue(obstructed.state().motorTemperatureCelsius(0) > clean.state().motorTemperatureCelsius(0) + 0.4);
	}

	@Test
	void localAirflowAndObstructionChangeEscCooling() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorThermal(65.0, 0.16, 200.0, 240.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics obstructed = new DronePhysics(config);
		for (int i = 0; i < clean.state().motorCount(); i++) {
			clean.state().setEscTemperatureCelsius(i, 80.0);
			obstructed.state().setEscTemperatureCelsius(i, 80.0);
		}

		DroneInput cruise = new DroneInput(0.52, 0.0, 0.0, 0.0, true);
		Vec3 crossflow = new Vec3(12.0, 0.0, 0.0);
		DroneEnvironment cleanAir = new DroneEnvironment(Vec3.ZERO, 1.0, Double.POSITIVE_INFINITY);
		DroneEnvironment blockedRotor = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {0.75, 0.0, 0.0, 0.0}
		);

		for (int i = 0; i < 260; i++) {
			clean.state().setOrientation(Quaternion.IDENTITY);
			obstructed.state().setOrientation(Quaternion.IDENTITY);
			clean.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			obstructed.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			clean.state().setVelocityMetersPerSecond(crossflow);
			obstructed.state().setVelocityMetersPerSecond(crossflow);
			clean.step(cruise, 0.005, cleanAir);
			obstructed.step(cruise, 0.005, blockedRotor);
		}

		assertTrue(clean.state().averageEscCoolingFactor() > 1.15);
		assertTrue(obstructed.state().escCoolingFactor(0) < obstructed.state().escCoolingFactor(1) * 0.80);
		assertTrue(obstructed.state().escTemperatureCelsius(0) > clean.state().escTemperatureCelsius(0) + 0.25);
	}

	@Test
	void a4mcLocalShelterReducesMotorAndEscVentilationCooling() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorThermal(68.0, 0.18, 200.0, 240.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics exposed = new DronePhysics(config);
		DronePhysics coarseShelter = new DronePhysics(config);
		DronePhysics localShelter = new DronePhysics(config);
		for (int i = 0; i < exposed.state().motorCount(); i++) {
			exposed.state().setMotorTemperatureCelsius(i, 86.0);
			coarseShelter.state().setMotorTemperatureCelsius(i, 86.0);
			localShelter.state().setMotorTemperatureCelsius(i, 86.0);
			exposed.state().setEscTemperatureCelsius(i, 82.0);
			coarseShelter.state().setEscTemperatureCelsius(i, 82.0);
			localShelter.state().setEscTemperatureCelsius(i, 82.0);
		}

		DroneInput loaded = new DroneInput(0.54, 0.0, 0.0, 0.0, true);
		Vec3 crossflow = new Vec3(11.0, 0.0, 0.0);
		DroneEnvironment exposedA4mc = a4mcShelterCoolingWind(0.0, true);
		DroneEnvironment coarseA4mcShelter = a4mcShelterCoolingWind(0.85, false);
		DroneEnvironment localA4mcShelter = a4mcShelterCoolingWind(0.85, true);

		for (int i = 0; i < 260; i++) {
			holdInCoolingCrossflow(exposed, crossflow);
			holdInCoolingCrossflow(coarseShelter, crossflow);
			holdInCoolingCrossflow(localShelter, crossflow);
			exposed.step(loaded, 0.005, exposedA4mc);
			coarseShelter.step(loaded, 0.005, coarseA4mcShelter);
			localShelter.step(loaded, 0.005, localA4mcShelter);
		}

		assertEquals(exposed.state().averageMotorCoolingFactor(), coarseShelter.state().averageMotorCoolingFactor(), 1.0e-9);
		assertEquals(exposed.state().averageEscCoolingFactor(), coarseShelter.state().averageEscCoolingFactor(), 1.0e-9);
		assertTrue(
				localShelter.state().averageMotorCoolingFactor() < exposed.state().averageMotorCoolingFactor() * 0.88,
				() -> "exposedMotorCooling=" + exposed.state().averageMotorCoolingFactor()
						+ " localShelterMotorCooling=" + localShelter.state().averageMotorCoolingFactor()
		);
		assertTrue(
				localShelter.state().averageEscCoolingFactor() < exposed.state().averageEscCoolingFactor() * 0.88,
				() -> "exposedEscCooling=" + exposed.state().averageEscCoolingFactor()
						+ " localShelterEscCooling=" + localShelter.state().averageEscCoolingFactor()
		);
		assertTrue(localShelter.state().averageMotorTemperatureCelsius() > exposed.state().averageMotorTemperatureCelsius() + 0.20);
		assertTrue(localShelter.state().maxEscTemperatureCelsius() > exposed.state().maxEscTemperatureCelsius() + 0.10);
	}

	@Test
	void recirculatedDirtyAirReducesMotorAndEscCooling() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorThermal(72.0, 0.18, 200.0, 240.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics recirculated = new DronePhysics(config);
		for (int i = 0; i < clean.state().motorCount(); i++) {
			clean.state().setMotorTemperatureCelsius(i, 86.0);
			recirculated.state().setMotorTemperatureCelsius(i, 86.0);
			clean.state().setEscTemperatureCelsius(i, 82.0);
			recirculated.state().setEscTemperatureCelsius(i, 82.0);
		}

		DroneInput loaded = new DroneInput(0.56, 0.0, 0.0, 0.0, true);
		Vec3 crossflow = new Vec3(10.0, 0.0, 0.0);
		DroneEnvironment cleanAir = new DroneEnvironment(Vec3.ZERO, 1.0, Double.POSITIVE_INFINITY, 0.0);
		DroneEnvironment dirtyRecirculation = new DroneEnvironment(
				new Vec3(8.0, 0.0, 0.0),
				1.0,
				0.08,
				0.0,
				0.85,
				0.95,
				0.12
		);

		for (int i = 0; i < 260; i++) {
			clean.state().setOrientation(Quaternion.IDENTITY);
			recirculated.state().setOrientation(Quaternion.IDENTITY);
			clean.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			recirculated.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			clean.state().setVelocityMetersPerSecond(crossflow);
			recirculated.state().setVelocityMetersPerSecond(crossflow);
			clean.step(loaded, 0.005, cleanAir);
			recirculated.step(loaded, 0.005, dirtyRecirculation);
		}

		assertTrue(recirculated.state().averageMotorCoolingFactor() < clean.state().averageMotorCoolingFactor() * 0.82,
				() -> "cleanMotorCooling=" + clean.state().averageMotorCoolingFactor()
						+ " recirculatedMotorCooling=" + recirculated.state().averageMotorCoolingFactor());
		assertTrue(recirculated.state().averageEscCoolingFactor() < clean.state().averageEscCoolingFactor() * 0.88,
				() -> "cleanEscCooling=" + clean.state().averageEscCoolingFactor()
						+ " recirculatedEscCooling=" + recirculated.state().averageEscCoolingFactor());
		assertTrue(recirculated.state().averageMotorTemperatureCelsius() > clean.state().averageMotorTemperatureCelsius() + 0.25);
		assertTrue(recirculated.state().averageEscTemperatureCelsius() > clean.state().averageEscTemperatureCelsius() + 0.12);
	}

	@Test
	void rotorAccelerationReactionTorqueAppearsWhenSpinupIsUnbalanced() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorInertiaKgMetersSquared(2.5e-4)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO);
		DronePhysics physics = new DronePhysics(config);
		physics.state().damageRotor(0, 0.9);
		DroneInput throttlePunch = new DroneInput(0.9, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 8; i++) {
			physics.step(throttlePunch, 0.0025);
		}

		assertTrue(Math.abs(physics.state().angularVelocityBodyRadiansPerSecond().y()) > 0.03);
		assertTrue(Math.abs(physics.state().rotorInertiaTorqueBodyNewtonMeters().y()) > 0.01);
		assertTrue(Math.abs(physics.state().rotorAccelerationReactionTorqueBodyNewtonMeters().y()) > 0.01);
	}

	@Test
	void rotorGyroscopicTorqueCouplesBodyRates() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorInertiaKgMetersSquared(2.5e-4)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO);
		DronePhysics withGyro = new DronePhysics(config);
		DronePhysics withoutGyro = new DronePhysics(config.withRotorInertiaKgMetersSquared(0.0));
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		double rotorOmega = config.rotors().get(0).maxOmegaRadiansPerSecond() * 0.85;
		withGyro.state().setMotorOmegaRadiansPerSecond(0, rotorOmega);
		withGyro.state().setMotorOmegaRadiansPerSecond(2, rotorOmega);
		withoutGyro.state().setMotorOmegaRadiansPerSecond(0, rotorOmega);
		withoutGyro.state().setMotorOmegaRadiansPerSecond(2, rotorOmega);
		withGyro.state().setAngularVelocityBodyRadiansPerSecond(new Vec3(0.0, 0.0, 8.0));
		withoutGyro.state().setAngularVelocityBodyRadiansPerSecond(new Vec3(0.0, 0.0, 8.0));
		withGyro.step(hover, 0.0025);
		withoutGyro.step(hover, 0.0025);

		assertTrue(Math.abs(withGyro.state().angularVelocityBodyRadiansPerSecond().x()) > Math.abs(withoutGyro.state().angularVelocityBodyRadiansPerSecond().x()) + 0.01);
		assertTrue(Math.abs(withGyro.state().rotorInertiaTorqueBodyNewtonMeters().x()) > 0.10);
		assertTrue(Math.abs(withGyro.state().rotorGyroscopicTorqueBodyNewtonMeters().x()) > 0.10);
		assertEquals(0.0, withoutGyro.state().rotorInertiaTorqueBodyNewtonMeters().length(), 1.0e-9);
		assertEquals(0.0, withoutGyro.state().rotorGyroscopicTorqueBodyNewtonMeters().length(), 1.0e-9);
	}

	@Test
	void dTermLowPassFiltersDerivativeSpikes() {
		PidController raw = new PidController(new PidGains(0.0, 0.0, 1.0, 1.0));
		PidController filtered = new PidController(new PidGains(0.0, 0.0, 1.0, 1.0, 0.0, 12.0, 0.0, 1.0, 0.0));

		raw.step(0.0, 0.005);
		filtered.step(0.0, 0.005);
		double rawKick = raw.step(1.0, 0.005);
		double filteredKick = filtered.step(1.0, 0.005);

		assertTrue(filteredKick > 0.0);
		assertTrue(filteredKick < rawKick * 0.5);
	}

	@Test
	void dynamicDTermLowPassCutoffChangesDerivativeFiltering() {
		PidGains gains = new PidGains(0.0, 0.0, 1.0, 1.0, 0.0, 90.0, 0.0, 1.0, 0.0);
		PidController lowCutoff = new PidController(gains);
		PidController highCutoff = new PidController(gains);

		lowCutoff.stepWithDerivativeInput(0.0, 0.0, 0.005, 1.0, 0.0, 1.0, 0.0, 1.0, 18.0);
		highCutoff.stepWithDerivativeInput(0.0, 0.0, 0.005, 1.0, 0.0, 1.0, 0.0, 1.0, 90.0);
		double lowKick = lowCutoff.stepWithDerivativeInput(0.0, 200.0, 0.005, 1.0, 0.0, 1.0, 0.0, 1.0, 18.0);
		double highKick = highCutoff.stepWithDerivativeInput(0.0, 200.0, 0.005, 1.0, 0.0, 1.0, 0.0, 1.0, 90.0);

		assertTrue(lowKick > 0.0);
		assertTrue(highKick > lowKick * 1.70);
	}

	@Test
	void dTermLowPassOpensWithThrottleAndMotorSpeed() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics lowThrottle = new DronePhysics(config);
		DronePhysics highThrottle = new DronePhysics(config);

		lowThrottle.step(new DroneInput(0.10, 0.0, 0.0, 0.0, true), 0.005);
		highThrottle.step(new DroneInput(0.90, 0.0, 0.0, 0.0, true), 0.005);

		assertTrue(lowThrottle.state().pidDTermLowPassCutoffHertz() < config.pitchGains().dTermLowPassCutoffHz() * 0.50);
		assertTrue(highThrottle.state().pidDTermLowPassCutoffHertz() > config.pitchGains().dTermLowPassCutoffHz() * 0.90);
		assertTrue(highThrottle.state().pidDTermLowPassCutoffHertz()
				> lowThrottle.state().pidDTermLowPassCutoffHertz() + 35.0);
	}

	@Test
	void dTermLowPassClosesDuringBladePassRoughAir() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorStallThrustLossCoefficient(0.60)
				.withRotorDiskDragCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics rough = new DronePhysics(config);
		Vec3 highSlip = new Vec3(80.0, 0.0, 0.0);
		DroneInput highThrottle = new DroneInput(0.72, 0.0, 0.0, 0.0, true);
		DroneEnvironment matchingWind = new DroneEnvironment(highSlip, 1.0, Double.POSITIVE_INFINITY);

		for (int i = 0; i < 170; i++) {
			clean.state().setOrientation(Quaternion.IDENTITY);
			rough.state().setOrientation(Quaternion.IDENTITY);
			clean.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			rough.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			clean.state().setVelocityMetersPerSecond(highSlip);
			rough.state().setVelocityMetersPerSecond(highSlip);
			clean.step(highThrottle, 0.005, matchingWind);
			rough.step(highThrottle, 0.005);
		}

		assertTrue(rough.state().averageRotorBladePassRippleIntensity()
				> clean.state().averageRotorBladePassRippleIntensity() + 0.008);
		assertTrue(rough.state().averageRotorStallIntensity() > 0.55);
		assertTrue(rough.state().pidDTermLowPassCutoffHertz()
				< clean.state().pidDTermLowPassCutoffHertz() * 0.78);
	}

	@Test
	void pidIntegratorAttenuationSuppressesAccumulation() {
		PidController normal = new PidController(new PidGains(0.0, 1.0, 0.0, 10.0));
		PidController relaxed = new PidController(new PidGains(0.0, 1.0, 0.0, 10.0));

		for (int i = 0; i < 100; i++) {
			normal.step(1.0, 0.01, 1.0, 0.0, 1.0, 0.0, 1.0);
			relaxed.step(1.0, 0.01, 1.0, 0.0, 1.0, 0.0, 0.0);
		}

		assertTrue(normal.lastIntegralTerm() > 0.9);
		assertEquals(0.0, relaxed.lastIntegralTerm(), 1.0e-12);
	}

	@Test
	void feedForwardImprovesInitialStickResponse() {
		PidGains pidOnly = new PidGains(0.012, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
		PidGains feedForward = new PidGains(0.012, 0.0, 0.0, 1.0, 0.00008, 0.0, 0.0, 1.0, 0.0);
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 0.0);
		DronePhysics withoutFeedForward = new DronePhysics(withCommonGains(base, pidOnly));
		DronePhysics withFeedForward = new DronePhysics(withCommonGains(base, feedForward));
		DroneInput cruise = new DroneInput(0.55, 0.0, 0.0, 0.0, true);
		DroneInput rollStep = new DroneInput(0.55, 0.0, 1.0, 0.0, true);

		withoutFeedForward.step(cruise, 0.005);
		withFeedForward.step(cruise, 0.005);
		for (int i = 0; i < 16; i++) {
			withoutFeedForward.step(rollStep, 0.005);
			withFeedForward.step(rollStep, 0.005);
		}

		assertTrue(withFeedForward.state().angularVelocityBodyRadiansPerSecond().z()
				> withoutFeedForward.state().angularVelocityBodyRadiansPerSecond().z() + 0.06);
	}

	@Test
	void flightControllerUsesGyroDerivativeWithoutSetpointDKick() {
		PidGains dOnly = new PidGains(0.0, 0.0, 0.020, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
		DroneConfig config = withCommonGains(directControl(DroneConfig.racingQuad()), dOnly)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRotorImbalanceIntensity(0.0);
		DronePhysics setpointStep = new DronePhysics(config);
		DronePhysics gyroStep = new DronePhysics(config);
		DroneInput neutral = new DroneInput(0.0, 0.0, 0.0, 0.0, true);
		DroneInput rollStep = new DroneInput(0.0, 0.0, 1.0, 0.0, true);

		setpointStep.step(neutral, 0.005);
		setpointStep.step(rollStep, 0.005);

		gyroStep.step(neutral, 0.005);
		gyroStep.state().setAngularVelocityBodyRadiansPerSecond(new Vec3(0.0, 0.0, 1.0));
		gyroStep.step(neutral, 0.005);

		assertTrue(setpointStep.state().targetRatesBodyRadiansPerSecond().z() > 4.0);
		assertEquals(0.0, setpointStep.state().pidDerivativeTorqueBodyNewtonMeters().z(), 1.0e-9);
		assertTrue(gyroStep.state().pidDerivativeTorqueBodyNewtonMeters().z() < -2.0);
	}

	@Test
	void itermRelaxReducesIntegralBuildUpDuringMixerSaturation() {
		PidGains aggressiveRoll = new PidGains(0.16, 0.65, 0.0, 5.0, 0.0, 0.0, 0.0, 1.0, 0.0);
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withMotorIdleAndAirmode(0.055, 0.0)
				.withRollGains(aggressiveRoll);
		DronePhysics noRelax = new DronePhysics(base.withPidIntegralRelaxStrength(0.0));
		DronePhysics withRelax = new DronePhysics(base.withPidIntegralRelaxStrength(1.0));
		DroneInput saturatedRoll = new DroneInput(0.0, 0.0, 1.0, 0.0, true);

		for (int i = 0; i < 2; i++) {
			noRelax.step(saturatedRoll, 0.005);
			withRelax.step(saturatedRoll, 0.005);
		}

		double noRelaxIntegral = Math.abs(noRelax.state().pidIntegralTorqueBodyNewtonMeters().z());
		double relaxedIntegral = Math.abs(withRelax.state().pidIntegralTorqueBodyNewtonMeters().z());
		Vec3 relaxAxes = withRelax.state().pidIntegralRelaxAxes();
		assertTrue(relaxAxes.x() < 1.0e-6);
		assertTrue(relaxAxes.y() < 1.0e-6);
		assertTrue(relaxAxes.z() > 0.45);
		assertEquals(relaxAxes.z(), withRelax.state().pidIntegralRelax(), 1.0e-9);
		assertTrue(relaxedIntegral < noRelaxIntegral * 0.75);
	}

	@Test
	void pidTelemetryReportsSetpointErrorAndTermSum() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput rollStep = new DroneInput(0.55, 0.0, 0.75, 0.0, true);

		physics.step(rollStep, 0.005);

		Vec3 targetRates = physics.state().targetRatesBodyRadiansPerSecond();
		Vec3 rateError = physics.state().rateErrorBodyRadiansPerSecond();
		Vec3 p = physics.state().pidProportionalTorqueBodyNewtonMeters();
		Vec3 i = physics.state().pidIntegralTorqueBodyNewtonMeters();
		Vec3 d = physics.state().pidDerivativeTorqueBodyNewtonMeters();
		Vec3 ff = physics.state().pidFeedForwardTorqueBodyNewtonMeters();
		Vec3 output = physics.state().pidOutputTorqueBodyNewtonMeters();

		assertTrue(targetRates.z() > 4.0);
		assertTrue(rateError.z() > 4.0);
		assertTrue(Math.abs(p.z()) > 0.01);
		assertEquals(p.x() + i.x() + d.x() + ff.x(), output.x(), 1.0e-12);
		assertEquals(p.y() + i.y() + d.y() + ff.y(), output.y(), 1.0e-12);
		assertEquals(p.z() + i.z() + d.z() + ff.z(), output.z(), 1.0e-12);
	}

	@Test
	void rcCommandSmoothingSoftensStepInputs() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withControlLink(0.050, 0.0, 0.35);
		DronePhysics physics = new DronePhysics(config);
		DroneInput rollStep = new DroneInput(0.8, 0.0, 1.0, 0.0, true);

		physics.step(rollStep, 0.005);

		assertEquals(1.0, physics.state().rawControlInput().roll(), 1.0e-9);
		assertTrue(physics.state().processedControlInput().roll() > 0.05);
		assertTrue(physics.state().processedControlInput().roll() < 0.25);
		assertTrue(physics.state().processedControlInput().throttle() < 0.20);
		assertTrue(!physics.state().controlFailsafeActive());
	}

	@Test
	void rcCommandLatencyDelaysRateSetpoint() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withControlLink(0.0, 0.020, 0.35);
		DronePhysics physics = new DronePhysics(config);
		DroneInput rollStep = new DroneInput(0.55, 0.0, 1.0, 0.0, true);

		physics.step(rollStep, 0.005);

		assertEquals(0.0, physics.state().targetRatesBodyRadiansPerSecond().z(), 1.0e-9);
		assertTrue(!physics.state().processedControlInput().armed());

		for (int i = 0; i < 4; i++) {
			physics.step(rollStep, 0.005);
		}

		assertTrue(physics.state().processedControlInput().armed());
		assertEquals(1.0, physics.state().processedControlInput().roll(), 1.0e-9);
		assertTrue(physics.state().targetRatesBodyRadiansPerSecond().z() > 4.0);
	}

	@Test
	void rcLinkLossHoldsLastCommandThenFailsafeCuts() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withControlLink(0.0, 0.0, 0.030);
		DronePhysics physics = new DronePhysics(config);
		DroneInput linkedCruise = new DroneInput(0.62, 0.0, 0.35, 0.0, true, true);

		physics.step(linkedCruise, 0.005);
		physics.step(DroneInput.idle(), 0.005);

		assertTrue(physics.state().processedControlInput().armed());
		assertEquals(0.62, physics.state().processedControlInput().throttle(), 1.0e-9);
		assertTrue(!physics.state().processedControlInput().linkActive());
		assertTrue(physics.state().controlLinkLossSeconds() > 0.0);
		assertTrue(!physics.state().controlFailsafeActive());

		for (int i = 0; i < 6; i++) {
			physics.step(DroneInput.idle(), 0.005);
		}

		assertTrue(physics.state().controlFailsafeActive());
		assertTrue(!physics.state().processedControlInput().armed());
		assertEquals(0.0, physics.state().processedControlInput().throttle(), 1.0e-9);
	}

	@Test
	void rcReceiverFrameRateHoldsCommandBetweenPackets() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withControlReceiver(50.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput firstFrame = new DroneInput(0.40, 0.0, 0.0, 0.0, true);
		DroneInput nextFrame = new DroneInput(0.90, 0.0, 1.0, 0.0, true);

		physics.step(firstFrame, 0.005);
		physics.step(nextFrame, 0.005);

		assertEquals(0.40, physics.state().processedControlInput().throttle(), 1.0e-9);
		assertEquals(0.0, physics.state().processedControlInput().roll(), 1.0e-9);
		assertEquals(0.020, physics.state().controlFrameIntervalSeconds(), 1.0e-9);
		assertTrue(physics.state().controlFrameAgeSeconds() > 0.0);
		assertEquals(1.0, physics.state().controlFrameError(), 1.0e-9);

		for (int i = 0; i < 3; i++) {
			physics.step(nextFrame, 0.005);
		}

		assertEquals(0.90, physics.state().processedControlInput().throttle(), 1.0e-9);
		assertEquals(1.0, physics.state().processedControlInput().roll(), 1.0e-9);
		assertEquals(0.0, physics.state().controlFrameAgeSeconds(), 1.0e-9);
	}

	@Test
	void rcReceiverQuantizesStickChannels() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withControlReceiver(0.0, 11.0);
		DronePhysics physics = new DronePhysics(config);

		physics.step(new DroneInput(0.33, 0.34, -0.34, 0.12, true), 0.005);

		DroneInput processed = physics.state().processedControlInput();
		assertEquals(0.30, processed.throttle(), 1.0e-9);
		assertEquals(0.40, processed.pitch(), 1.0e-9);
		assertEquals(-0.40, processed.roll(), 1.0e-9);
		assertEquals(0.20, processed.yaw(), 1.0e-9);
		assertEquals(0.0, physics.state().controlFrameIntervalSeconds(), 1.0e-9);
		assertEquals(0.08, physics.state().controlFrameError(), 1.0e-9);
	}

	@Test
	void tpaReducesHighThrottlePidAuthority() {
		PidGains noTpa = new PidGains(0.09, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.3, 0.0);
		PidGains strongTpa = new PidGains(0.09, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.3, 0.8);
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 0.0);
		DronePhysics fullPid = new DronePhysics(withCommonGains(base, noTpa));
		DronePhysics attenuatedPid = new DronePhysics(withCommonGains(base, strongTpa));
		DroneInput highThrottleRoll = new DroneInput(0.75, 0.0, 0.75, 0.0, true);

		for (int i = 0; i < 120; i++) {
			fullPid.step(highThrottleRoll, 0.005);
			attenuatedPid.step(highThrottleRoll, 0.005);
		}

		assertTrue(attenuatedPid.state().pidAttenuation() < 0.6);
		assertTrue(fullPid.state().angularVelocityBodyRadiansPerSecond().z()
				> attenuatedPid.state().angularVelocityBodyRadiansPerSecond().z() * 1.25);
	}

	@Test
	void imuClippingReducesPidAuthority() {
		PidGains noTpa = new PidGains(0.09, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
		DroneConfig config = withCommonGains(directControl(DroneConfig.racingQuad())
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0), noTpa);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics clipped = new DronePhysics(config);
		DroneInput armed = new DroneInput(0.35, 0.0, 0.0, 0.0, true);

		clean.step(armed, 0.005);
		clipped.state().setAngularVelocityBodyRadiansPerSecond(new Vec3(Math.toRadians(2600.0), 0.0, 0.0));
		clipped.step(armed, 0.005);

		assertEquals(1.0, clean.state().pidAttenuation(), 1.0e-9);
		assertTrue(clipped.state().gyroClipIntensity() > 0.25);
		assertTrue(clipped.state().pidAttenuation() < 0.80);
		assertTrue(clipped.state().pidAttenuation() > 0.35);
	}

	@Test
	void antiGravityBoostsIntegratorDuringThrottlePunchAndDecays() {
		PidGains gains = new PidGains(0.02, 0.02, 0.0, 1.0, 0.0, 0.0, 2.2, 1.0, 0.0);
		DronePhysics physics = new DronePhysics(withCommonGains(directControl(DroneConfig.racingQuad()), gains));

		physics.step(new DroneInput(0.2, 0.0, 0.0, 0.0, true), 0.005);
		physics.step(new DroneInput(0.9, 0.0, 0.0, 0.0, true), 0.005);
		double punchBoost = physics.state().antiGravityBoost();

		for (int i = 0; i < 200; i++) {
			physics.step(new DroneInput(0.9, 0.0, 0.0, 0.0, true), 0.005);
		}

		assertTrue(punchBoost > 0.7);
		assertTrue(physics.state().antiGravityBoost() < punchBoost * 0.2);
	}

	@Test
	void runtimeConfigUpdateChangesRateResponse() {
		DronePhysics slow = new DronePhysics(directControl(DroneConfig.racingQuad()).withRollGains(new PidGains(0.01, 0.0, 0.0, 1.0)));
		DronePhysics fast = new DronePhysics(directControl(DroneConfig.racingQuad()));
		fast.applyConfig(fast.config().withRollGains(new PidGains(0.12, 0.0, 0.0, 1.0)));
		DroneInput input = new DroneInput(0.45, 0.0, 1.0, 0.0, true);

		for (int i = 0; i < 120; i++) {
			slow.step(input, 0.0025);
			fast.step(input, 0.0025);
		}

		assertTrue(fast.state().angularVelocityBodyRadiansPerSecond().z() > slow.state().angularVelocityBodyRadiansPerSecond().z() * 1.5);
	}

	@Test
	void rateExpoSoftensMidStickResponse() {
		DroneConfig base = directControl(DroneConfig.racingQuad()).withRollGains(new PidGains(0.08, 0.0, 0.0, 1.0));
		DronePhysics linear = new DronePhysics(base.withRateExpo(Vec3.ZERO));
		DronePhysics expo = new DronePhysics(base.withRateExpo(new Vec3(0.0, 0.0, 0.75)));
		DroneInput input = new DroneInput(0.45, 0.0, 0.5, 0.0, true);

		for (int i = 0; i < 120; i++) {
			linear.step(input, 0.0025);
			expo.step(input, 0.0025);
		}

		assertTrue(linear.state().angularVelocityBodyRadiansPerSecond().z() > expo.state().angularVelocityBodyRadiansPerSecond().z() * 1.4);
	}

	@Test
	void acroRateCurveMatchesActualRatesEquivalentForRacingQuadRoll() {
		DroneConfig preset = DroneConfig.racingQuad();
		DroneConfig config = directControl(preset).withRateProfile(preset.rateExpo(), preset.rateSuper());
		DronePhysics halfStick = new DronePhysics(config);
		DronePhysics threeQuarterStick = new DronePhysics(config);

		halfStick.step(new DroneInput(0.5, 0.0, 0.5, 0.0, true), 0.005);
		threeQuarterStick.step(new DroneInput(0.5, 0.0, 0.75, 0.0, true), 0.005);

		double halfStickRateDegrees = shapedRateDegreesPerSecond(0.5, preset.rateExpo().z(), preset.rateSuper().z(), preset.maxRollRateRadiansPerSecond());
		double threeQuarterStickRateDegrees = shapedRateDegreesPerSecond(0.75, preset.rateExpo().z(), preset.rateSuper().z(), preset.maxRollRateRadiansPerSecond());

		assertEquals(
				halfStickRateDegrees,
				Math.toDegrees(halfStick.state().targetRatesBodyRadiansPerSecond().z()),
				1.0e-9
		);
		assertEquals(
				threeQuarterStickRateDegrees,
				Math.toDegrees(threeQuarterStick.state().targetRatesBodyRadiansPerSecond().z()),
				1.0e-9
		);
		assertTrue(halfStickRateDegrees < 125.0);
		assertTrue(threeQuarterStickRateDegrees < 270.0);
	}

	@Test
	void rateSuperReducesCenterSensitivityWhilePreservingFullStickRate() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRateProfile(Vec3.ZERO, Vec3.ZERO);
		DronePhysics linear = new DronePhysics(base);
		DronePhysics superRate = new DronePhysics(base.withRateSuper(new Vec3(0.0, 0.0, 0.70)));

		linear.step(new DroneInput(0.5, 0.0, 0.25, 0.0, true), 0.005);
		superRate.step(new DroneInput(0.5, 0.0, 0.25, 0.0, true), 0.005);

		assertTrue(superRate.state().targetRatesBodyRadiansPerSecond().z()
				< linear.state().targetRatesBodyRadiansPerSecond().z() * 0.55);

		linear.step(new DroneInput(0.5, 0.0, 1.0, 0.0, true), 0.005);
		superRate.step(new DroneInput(0.5, 0.0, 1.0, 0.0, true), 0.005);

		assertEquals(
				linear.state().targetRatesBodyRadiansPerSecond().z(),
				superRate.state().targetRatesBodyRadiansPerSecond().z(),
				1.0e-9
		);
	}

	@Test
	void gyroLowPassAndLatencyDelayMeasuredBodyRates() {
		DroneConfig base = directControl(DroneConfig.racingQuad()).withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics idealGyro = new DronePhysics(base.withFlightControllerSensors(1000.0, 0.0, 0.0));
		DronePhysics laggedGyro = new DronePhysics(base.withFlightControllerSensors(8.0, 0.0, 0.025));
		Vec3 initialRate = new Vec3(4.0, 1.5, -3.0);
		idealGyro.state().setAngularVelocityBodyRadiansPerSecond(initialRate);
		laggedGyro.state().setAngularVelocityBodyRadiansPerSecond(initialRate);
		DroneInput armedIdle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		idealGyro.step(armedIdle, 0.005);
		laggedGyro.step(armedIdle, 0.005);

		assertEquals(initialRate.x(), idealGyro.state().gyroAngularVelocityBodyRadiansPerSecond().x(), 0.05);
		assertTrue(laggedGyro.state().gyroAngularVelocityBodyRadiansPerSecond().length() < initialRate.length() * 0.2);
	}

	@Test
	void gyroNoiseAddsDeterministicSensorMotion() {
		DroneConfig noNoiseConfig = directControl(DroneConfig.racingQuad()).withFlightControllerSensors(1000.0, 0.0, 0.0);
		DroneConfig noisyConfig = directControl(DroneConfig.racingQuad()).withFlightControllerSensors(1000.0, 0.35, 0.0);
		DronePhysics clean = new DronePhysics(noNoiseConfig);
		DronePhysics noisy = new DronePhysics(noisyConfig);
		DroneInput armedIdle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		clean.step(armedIdle, 0.005);
		noisy.step(armedIdle, 0.005);

		assertEquals(0.0, clean.state().gyroAngularVelocityBodyRadiansPerSecond().length(), 1.0e-9);
		assertTrue(noisy.state().gyroAngularVelocityBodyRadiansPerSecond().length() > 0.03);
	}

	@Test
	void gyroSpecificForceSensitivityAddsHighGRateError() throws ReflectiveOperationException {
		DroneConfig realisticConfig = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.08, 1000.0, 0.0, 0.0);
		DroneConfig idealConfig = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics oneG = new DronePhysics(realisticConfig);
		DronePhysics highG = new DronePhysics(realisticConfig);
		DronePhysics idealHighG = new DronePhysics(idealConfig);
		Vec3 punchAcceleration = new Vec3(0.0, 32.0, 0.0);
		Method updateGyroMeasurement = DronePhysics.class.getDeclaredMethod("updateGyroMeasurement", double.class);
		updateGyroMeasurement.setAccessible(true);

		oneG.state().setLinearAccelerationWorldMetersPerSecondSquared(Vec3.ZERO);
		highG.state().setLinearAccelerationWorldMetersPerSecondSquared(punchAcceleration);
		idealHighG.state().setLinearAccelerationWorldMetersPerSecondSquared(punchAcceleration);
		updateGyroMeasurement.invoke(oneG, 0.005);
		updateGyroMeasurement.invoke(highG, 0.005);
		updateGyroMeasurement.invoke(idealHighG, 0.005);

		Vec3 highGDelta = highG.state().gyroAngularVelocityBodyRadiansPerSecond()
				.subtract(oneG.state().gyroAngularVelocityBodyRadiansPerSecond());
		assertTrue(
				highGDelta.length() > Math.toRadians(0.55),
				() -> "highGDeltaDps=" + Math.toDegrees(highGDelta.length())
		);
		assertEquals(0.0, idealHighG.state().gyroAngularVelocityBodyRadiansPerSecond().length(), 1.0e-9);
	}

	@Test
	void accelerometerReadsSpecificForceNotWorldAcceleration() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics freeFall = new DronePhysics(config);
		DronePhysics hover = new DronePhysics(config);
		DroneInput hoverInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);

		freeFall.step(DroneInput.idle(), 0.005);
		for (int i = 0; i < 600; i++) {
			hover.step(hoverInput, 0.005);
		}

		assertEquals(config.gravityMetersPerSecondSquared(), -freeFall.state().linearAccelerationWorldMetersPerSecondSquared().y(), 0.05);
		assertTrue(freeFall.state().accelerometerBodyMetersPerSecondSquared().length() < 0.05);
		assertEquals(config.gravityMetersPerSecondSquared(), hover.state().accelerometerBodyMetersPerSecondSquared().y(), 0.7);
	}

	@Test
	void accelerometerNoiseAddsDeterministicSensorMotion() {
		DroneConfig cleanConfig = directControl(DroneConfig.racingQuad()).withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DroneConfig noisyConfig = directControl(DroneConfig.racingQuad()).withFlightControllerSensors(1000.0, 0.0, 1000.0, 1.2, 0.0);
		DronePhysics clean = new DronePhysics(cleanConfig);
		DronePhysics noisy = new DronePhysics(noisyConfig);

		clean.step(DroneInput.idle(), 0.005);
		noisy.step(DroneInput.idle(), 0.005);

		assertTrue(clean.state().accelerometerBodyMetersPerSecondSquared().length() < 0.05);
		assertTrue(noisy.state().accelerometerBodyMetersPerSecondSquared().length() > 0.05);
	}

	@Test
	void accelerometerScaleErrorModelsHighGCompressionAndCrossAxis() throws ReflectiveOperationException {
		DroneConfig realisticConfig = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 1.2, 0.0);
		DroneConfig idealConfig = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics realistic = new DronePhysics(realisticConfig);
		DronePhysics ideal = new DronePhysics(idealConfig);
		Method scaleErrorMethod = DronePhysics.class.getDeclaredMethod(
				"accelerometerScaleErrorBodyMetersPerSecondSquared",
				Vec3.class
		);
		scaleErrorMethod.setAccessible(true);
		Vec3 nominalSpecificForce = new Vec3(0.0, realisticConfig.gravityMetersPerSecondSquared(), 0.0);
		Vec3 highSpecificForce = new Vec3(0.0, realisticConfig.gravityMetersPerSecondSquared() + 45.0, 0.0);

		Vec3 nominalError = (Vec3) scaleErrorMethod.invoke(realistic, nominalSpecificForce);
		Vec3 highGError = (Vec3) scaleErrorMethod.invoke(realistic, highSpecificForce);
		Vec3 idealError = (Vec3) scaleErrorMethod.invoke(ideal, highSpecificForce);

		assertEquals(0.0, nominalError.length(), 1.0e-12);
		assertTrue(
				highGError.y() < -4.0,
				() -> "highGErrorY=" + highGError.y()
		);
		assertTrue(
				highGError.z() > 0.06,
				() -> "highGErrorZ=" + highGError.z()
		);
		assertEquals(0.0, idealError.length(), 1.0e-12);
	}

	@Test
	void barometerReportsPressureAltitudeAndLaggedVerticalSpeed() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRotorImbalanceIntensity(0.0);
		DronePhysics physics = new DronePhysics(config);

		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
		physics.step(DroneInput.idle(), 0.005, DroneEnvironment.calm());

		assertEquals(20.0, physics.state().barometerAltitudeMeters(), 0.05);
		assertTrue(physics.state().barometerPressureHectopascals() < 1013.25);

		for (int i = 0; i < 160; i++) {
			physics.state().setPositionMeters(new Vec3(0.0, 20.0 + i * 0.025, 0.0));
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.step(DroneInput.idle(), 0.005, DroneEnvironment.calm());
		}

		assertTrue(physics.state().barometerAltitudeMeters() > 23.0);
		assertTrue(physics.state().barometerVerticalSpeedMetersPerSecond() > 2.0);
		assertEquals(0.0, physics.state().barometerSensorNoiseMeters(), 1.0e-9);
		assertEquals(0.0, physics.state().barometerPressurePortErrorMeters(), 1.0e-9);
		assertEquals(0.0, physics.state().barometerPropwashErrorMeters(), 1.0e-9);
	}

	@Test
	void barometerModelsBatteryBusRippleSupplyNoise() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRotorImbalanceIntensity(0.0)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics cleanRail = new DronePhysics(config);
		DronePhysics noisyRail = new DronePhysics(config);
		DroneEnvironment calm = DroneEnvironment.calm();
		double maxCleanError = 0.0;
		double maxNoisyError = 0.0;
		double maxCleanSensorNoise = 0.0;
		double maxNoisySensorNoise = 0.0;

		for (int i = 0; i < 180; i++) {
			cleanRail.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			cleanRail.state().setVelocityMetersPerSecond(Vec3.ZERO);
			cleanRail.state().setBatteryBusRippleVoltage(0.0);
			cleanRail.step(DroneInput.idle(), 0.005, calm);

			noisyRail.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			noisyRail.state().setVelocityMetersPerSecond(Vec3.ZERO);
			noisyRail.state().setBatteryBusRippleVoltage(0.90);
			noisyRail.step(DroneInput.idle(), 0.005, calm);

			maxCleanError = Math.max(maxCleanError, Math.abs(cleanRail.state().barometerErrorMeters()));
			maxNoisyError = Math.max(maxNoisyError, Math.abs(noisyRail.state().barometerErrorMeters()));
			maxCleanSensorNoise = Math.max(maxCleanSensorNoise, Math.abs(cleanRail.state().barometerSensorNoiseMeters()));
			maxNoisySensorNoise = Math.max(maxNoisySensorNoise, Math.abs(noisyRail.state().barometerSensorNoiseMeters()));
		}

		double finalMaxCleanError = maxCleanError;
		double finalMaxNoisyError = maxNoisyError;
		double finalMaxCleanSensorNoise = maxCleanSensorNoise;
		double finalMaxNoisySensorNoise = maxNoisySensorNoise;
		assertTrue(finalMaxCleanError < 1.0e-6, () -> "maxCleanError=" + finalMaxCleanError);
		assertTrue(finalMaxNoisyError > 0.010, () -> "maxNoisyError=" + finalMaxNoisyError);
		assertTrue(finalMaxCleanSensorNoise < 1.0e-9, () -> "maxCleanSensorNoise=" + finalMaxCleanSensorNoise);
		assertTrue(finalMaxNoisySensorNoise > 0.010, () -> "maxNoisySensorNoise=" + finalMaxNoisySensorNoise);
		assertTrue(Math.abs(noisyRail.state().barometerVerticalSpeedMetersPerSecond()) > 0.02);
	}

	@Test
	void barometerModelsAirspeedStaticPortPressureError() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRotorImbalanceIntensity(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.04));
		DronePhysics still = new DronePhysics(config);
		DronePhysics fastForward = new DronePhysics(config);
		DronePhysics highSideslip = new DronePhysics(config);
		DroneEnvironment calm = DroneEnvironment.calm();

		for (int i = 0; i < 180; i++) {
			still.state().setOrientation(Quaternion.IDENTITY);
			still.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			still.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			still.state().setVelocityMetersPerSecond(Vec3.ZERO);
			still.step(DroneInput.idle(), 0.005, calm);

			fastForward.state().setOrientation(Quaternion.IDENTITY);
			fastForward.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			fastForward.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			fastForward.state().setVelocityMetersPerSecond(new Vec3(0.0, 0.0, 24.0));
			fastForward.step(DroneInput.idle(), 0.005, calm);

			highSideslip.state().setOrientation(Quaternion.IDENTITY);
			highSideslip.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			highSideslip.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			highSideslip.state().setVelocityMetersPerSecond(new Vec3(22.0, 0.0, 5.0));
			highSideslip.step(DroneInput.idle(), 0.005, calm);
		}

		assertEquals(0.0, still.state().barometerPropwashErrorMeters(), 1.0e-9);
		assertEquals(0.0, still.state().barometerPressurePortErrorMeters(), 1.0e-9);
		assertEquals(0.0, fastForward.state().barometerSensorNoiseMeters(), 1.0e-9);
		assertEquals(0.0, fastForward.state().barometerPropwashErrorMeters(), 1.0e-9);
		assertTrue(
				fastForward.state().barometerPressurePortErrorMeters() < -1.10,
				() -> "fastForwardPressureError=" + fastForward.state().barometerPressurePortErrorMeters()
		);
		assertTrue(
				fastForward.state().barometerErrorMeters() < -1.00,
				() -> "fastForwardFilteredError=" + fastForward.state().barometerErrorMeters()
		);
		assertEquals(0.0, highSideslip.state().barometerPropwashErrorMeters(), 1.0e-9);
		assertTrue(
				highSideslip.state().barometerPressurePortErrorMeters() > 0.60,
				() -> "highSideslipPressureError=" + highSideslip.state().barometerPressurePortErrorMeters()
		);
		assertTrue(
				highSideslip.state().barometerPressurePortErrorMeters()
						> fastForward.state().barometerPressurePortErrorMeters() + 1.60,
				() -> "fastForwardPressureError=" + fastForward.state().barometerPressurePortErrorMeters()
						+ " highSideslipPressureError=" + highSideslip.state().barometerPressurePortErrorMeters()
		);
	}

	@Test
	void barometerStaticPortPressureErrorBuildsAndRecoversWithLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRotorImbalanceIntensity(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.04));
		DronePhysics physics = new DronePhysics(config);
		DroneEnvironment calm = DroneEnvironment.calm();
		Vec3 fastForward = new Vec3(0.0, 0.0, 24.0);

		for (int i = 0; i < 120; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.step(DroneInput.idle(), 0.005, calm);
		}

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		physics.state().setVelocityMetersPerSecond(fastForward);
		physics.step(DroneInput.idle(), 0.005, calm);
		double firstFastError = physics.state().barometerPressurePortErrorMeters();

		for (int i = 0; i < 140; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			physics.state().setVelocityMetersPerSecond(fastForward);
			physics.step(DroneInput.idle(), 0.005, calm);
		}
		double settledFastError = physics.state().barometerPressurePortErrorMeters();

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
		physics.step(DroneInput.idle(), 0.005, calm);
		double lingeringStillError = physics.state().barometerPressurePortErrorMeters();

		for (int i = 0; i < 240; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.step(DroneInput.idle(), 0.005, calm);
		}
		double recoveredStillError = physics.state().barometerPressurePortErrorMeters();

		assertTrue(firstFastError < -0.05, () -> "firstFastError=" + firstFastError);
		assertTrue(settledFastError < firstFastError - 0.90,
				() -> "firstFastError=" + firstFastError + " settledFastError=" + settledFastError);
		assertTrue(lingeringStillError < recoveredStillError - 0.40,
				() -> "lingeringStillError=" + lingeringStillError
						+ " recoveredStillError=" + recoveredStillError);
		assertEquals(0.0, recoveredStillError, 0.02);
	}

	@Test
	void barometerModelsRotationalStaticPortPressureError() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRotorImbalanceIntensity(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.04));
		DronePhysics still = new DronePhysics(config);
		DronePhysics fastRoll = new DronePhysics(config);
		DroneEnvironment calm = DroneEnvironment.calm();

		for (int i = 0; i < 180; i++) {
			still.state().setOrientation(Quaternion.IDENTITY);
			still.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			still.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			still.state().setVelocityMetersPerSecond(Vec3.ZERO);
			still.step(DroneInput.idle(), 0.005, calm);

			fastRoll.state().setOrientation(Quaternion.IDENTITY);
			fastRoll.state().setAngularVelocityBodyRadiansPerSecond(new Vec3(0.0, 0.0, Math.toRadians(2100.0)));
			fastRoll.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			fastRoll.state().setVelocityMetersPerSecond(Vec3.ZERO);
			fastRoll.step(DroneInput.idle(), 0.005, calm);
		}

		assertEquals(0.0, still.state().barometerPressurePortErrorMeters(), 1.0e-9);
		assertEquals(0.0, still.state().barometerPropwashErrorMeters(), 1.0e-9);
		double fastRollPressurePortError = fastRoll.state().barometerPressurePortErrorMeters();
		double fastRollPropwashError = Math.abs(fastRoll.state().barometerPropwashErrorMeters());
		assertTrue(
				fastRollPressurePortError > 0.30,
				() -> "fastRollPressureError=" + fastRollPressurePortError
		);
		assertTrue(
				fastRollPropwashError < fastRollPressurePortError * 0.45,
				() -> "fastRollPressureError=" + fastRollPressurePortError
						+ " fastRollPropwashError=" + fastRollPropwashError
		);
		assertTrue(
				fastRoll.state().barometerErrorMeters() > 0.25,
				() -> "fastRollFilteredError=" + fastRoll.state().barometerErrorMeters()
		);
	}

	@Test
	void highAltitudeStandardAtmosphereReducesRotorAuthorityAndPressure() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		double seaTemperature = 15.0;
		double highAltitudeMeters = 3000.0;
		double highTemperature = -4.5;
		DroneEnvironment seaLevel = new DroneEnvironment(
				Vec3.ZERO,
				DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, seaTemperature),
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				seaTemperature
		);
		DroneEnvironment highAltitude = new DroneEnvironment(
				Vec3.ZERO,
				DroneEnvironment.standardAtmosphereAirDensityRatio(highAltitudeMeters, highTemperature),
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				highTemperature
		);
		DronePhysics low = new DronePhysics(config);
		DronePhysics high = new DronePhysics(config);
		DroneInput throttle = new DroneInput(0.62, 0.0, 0.0, 0.0, true);
		high.state().setPositionMeters(new Vec3(0.0, highAltitudeMeters, 0.0));

		for (int i = 0; i < 220; i++) {
			low.step(throttle, 0.005, seaLevel);
			high.step(throttle, 0.005, highAltitude);
			high.state().setPositionMeters(new Vec3(0.0, highAltitudeMeters, 0.0));
		}

		assertTrue(highAltitude.airDensityRatio() < seaLevel.airDensityRatio() * 0.80);
		assertTrue(averageRotorThrust(high.state()) < averageRotorThrust(low.state()) * 0.82);
		assertTrue(high.state().barometerPressureHectopascals() < low.state().barometerPressureHectopascals() * 0.75);
	}

	@Test
	void lowReynoldsSmallPropsLoseDensityNormalizedEfficiency() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorImbalanceIntensity(0.0)
				.withRotorStallThrustLossCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DroneConfig smallPropConfig = base
				.withRotorRadiusMeters(0.020)
				.withRotorBladePitchMeters(0.016);
		DroneConfig referencePropConfig = base
				.withRotorRadiusMeters(0.0635)
				.withRotorBladePitchMeters(0.108);
		DroneEnvironment standardAir = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				25.0
		);
		DronePhysics smallProp = new DronePhysics(smallPropConfig);
		DronePhysics referenceProp = new DronePhysics(referencePropConfig);
		DroneInput cruise = new DroneInput(0.34, 0.0, 0.0, 0.0, true);
		double smallRatioSum = 0.0;
		double referenceRatioSum = 0.0;
		int samples = 0;

		for (int i = 0; i < 220; i++) {
			smallProp.state().setOrientation(Quaternion.IDENTITY);
			referenceProp.state().setOrientation(Quaternion.IDENTITY);
			smallProp.state().setVelocityMetersPerSecond(Vec3.ZERO);
			referenceProp.state().setVelocityMetersPerSecond(Vec3.ZERO);
			smallProp.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			referenceProp.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			smallProp.step(cruise, 0.005, standardAir);
			referenceProp.step(cruise, 0.005, standardAir);
			if (i >= 160) {
				smallRatioSum += densityNormalizedRotorThrust(smallPropConfig, smallProp.state(), standardAir.airDensityRatio());
				referenceRatioSum += densityNormalizedRotorThrust(referencePropConfig, referenceProp.state(), standardAir.airDensityRatio());
				samples++;
			}
		}

		double smallRatio = smallRatioSum / samples;
		double referenceRatio = referenceRatioSum / samples;
		assertTrue(smallProp.state().rotorThrustNewtons(0) > 0.5);
		assertTrue(smallRatio < referenceRatio - 0.12,
				() -> "smallRatio=" + smallRatio + " referenceRatio=" + referenceRatio);
		assertTrue(smallProp.state().averageRotorLowReynoldsLoss() > 0.20,
				() -> "smallLowReLoss=" + smallProp.state().averageRotorLowReynoldsLoss());
		assertTrue(smallProp.state().averageRotorReynoldsNumber() > 0.0,
				() -> "smallRe=" + smallProp.state().averageRotorReynoldsNumber());
		assertTrue(referenceProp.state().averageRotorReynoldsNumber()
						> smallProp.state().averageRotorReynoldsNumber(),
				() -> "smallRe=" + smallProp.state().averageRotorReynoldsNumber()
						+ " referenceRe=" + referenceProp.state().averageRotorReynoldsNumber());
		assertTrue(smallProp.state().averageRotorReynoldsIndex()
						< referenceProp.state().averageRotorReynoldsIndex(),
				() -> "smallReIndex=" + smallProp.state().averageRotorReynoldsIndex()
						+ " referenceReIndex=" + referenceProp.state().averageRotorReynoldsIndex());
		assertTrue(referenceProp.state().averageRotorLowReynoldsLoss()
						< smallProp.state().averageRotorLowReynoldsLoss() * 0.35,
				() -> "smallLowReLoss=" + smallProp.state().averageRotorLowReynoldsLoss()
						+ " referenceLowReLoss=" + referenceProp.state().averageRotorLowReynoldsLoss());
		assertTrue(smallProp.state().rotorVibration() > referenceProp.state().rotorVibration() + 0.006,
				() -> "smallVibration=" + smallProp.state().rotorVibration()
						+ " referenceVibration=" + referenceProp.state().rotorVibration());
	}

	@Test
	void lowReynoldsProxyUsesRepresentativeBladeChord() throws ReflectiveOperationException {
		Method lowReynoldsLoss = DronePhysics.class.getDeclaredMethod(
				"rotorLowReynoldsLoss",
				RotorSpec.class,
				double.class,
				double.class,
				double.class
		);
		lowReynoldsLoss.setAccessible(true);
		RotorSpec twoBladeSmall = DroneConfig.racingQuad().rotors().get(0)
				.withRadiusMeters(0.035)
				.withBladePitchToDiameterRatio(0.40)
				.withBladeCount(2);
		RotorSpec triBladeSmall = twoBladeSmall.withBladeCount(3);
		double omega = twoBladeSmall.maxOmegaRadiansPerSecond() * 0.65;

		double twoBladeLoss = (double) lowReynoldsLoss.invoke(null, twoBladeSmall, omega, 0.78, 30.0);
		double triBladeLoss = (double) lowReynoldsLoss.invoke(null, triBladeSmall, omega, 0.78, 30.0);

		assertTrue(triBladeSmall.representativeBladeChordMeters() > twoBladeSmall.representativeBladeChordMeters() * 1.25);
		assertTrue(twoBladeLoss > 0.35, () -> "twoBladeLoss=" + twoBladeLoss);
		assertTrue(triBladeLoss < twoBladeLoss - 0.25,
				() -> "twoBladeLoss=" + twoBladeLoss + " triBladeLoss=" + triBladeLoss);
	}

	@Test
	void highRotorTipMachReducesPropEfficiencyAndAddsLoad() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withRotorMaxThrustNewtons(80.0)
				.withRotorThrustCoefficient(1.0e-5)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 200.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics lowMach = new DronePhysics(base.withRotorRadiusMeters(0.050));
		DronePhysics highMach = new DronePhysics(base.withRotorRadiusMeters(0.160));
		DroneInput punch = new DroneInput(0.88, 0.0, 0.0, 0.0, true);

		double lowMachThrustSum = 0.0;
		double highMachThrustSum = 0.0;
		double lowMachTorqueSum = 0.0;
		double highMachTorqueSum = 0.0;
		int thrustSamples = 0;
		for (int i = 0; i < 220; i++) {
			lowMach.state().setVelocityMetersPerSecond(Vec3.ZERO);
			highMach.state().setVelocityMetersPerSecond(Vec3.ZERO);
			lowMach.step(punch, 0.005, DroneEnvironment.calm());
			highMach.step(punch, 0.005, DroneEnvironment.calm());
			if (i >= 180) {
				lowMachThrustSum += averageRotorThrust(lowMach.state());
				highMachThrustSum += averageRotorThrust(highMach.state());
				lowMachTorqueSum += lowMach.state().averageMotorAerodynamicTorqueNewtonMeters();
				highMachTorqueSum += highMach.state().averageMotorAerodynamicTorqueNewtonMeters();
				thrustSamples++;
			}
		}
		double lowMachMeanThrust = lowMachThrustSum / thrustSamples;
		double highMachMeanThrust = highMachThrustSum / thrustSamples;
		double lowMachMeanTorque = lowMachTorqueSum / thrustSamples;
		double highMachMeanTorque = highMachTorqueSum / thrustSamples;
		double lowMachTorquePerThrust = lowMachMeanTorque / lowMachMeanThrust;
		double highMachTorquePerThrust = highMachMeanTorque / highMachMeanThrust;

		assertTrue(lowMach.state().maxRotorTipMach() < 0.35);
		assertTrue(highMach.state().maxRotorTipMach() > 0.60);
		assertEquals(1.0, lowMach.state().minRotorCompressibilityThrustScale(), 1.0e-6);
		assertTrue(highMach.state().minRotorCompressibilityThrustScale() < 0.98);
		assertTrue(
				highMachMeanThrust < lowMachMeanThrust * 0.95,
				() -> "lowMachMeanThrust=" + lowMachMeanThrust + " highMachMeanThrust=" + highMachMeanThrust
		);
		assertTrue(highMach.state().averageRotorAerodynamicLoadFactor()
				> lowMach.state().averageRotorAerodynamicLoadFactor() + 0.04);
		assertTrue(highMachTorquePerThrust > lowMachTorquePerThrust * 1.10,
				() -> "lowMachTorquePerThrust=" + lowMachTorquePerThrust
						+ " highMachTorquePerThrust=" + highMachTorquePerThrust);
		assertTrue(highMach.state().rotorVibration() > lowMach.state().rotorVibration() + 0.03);
	}

	@Test
	void rotorBladePitchChangesAxialUnloadAndPropLoad() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		double radius = base.rotors().get(0).radiusMeters();
		DronePhysics lowPitch = new DronePhysics(base.withRotorBladePitchMeters(radius * 0.70));
		DronePhysics highPitch = new DronePhysics(base.withRotorBladePitchMeters(radius * 2.60));
		DroneInput climb = new DroneInput(0.76, 0.0, 0.0, 0.0, true);
		Vec3 axialClimbFlow = new Vec3(0.0, 12.0, 0.0);

		for (int i = 0; i < 180; i++) {
			lowPitch.state().setOrientation(Quaternion.IDENTITY);
			highPitch.state().setOrientation(Quaternion.IDENTITY);
			lowPitch.state().setVelocityMetersPerSecond(axialClimbFlow);
			highPitch.state().setVelocityMetersPerSecond(axialClimbFlow);
			lowPitch.step(climb, 0.005, DroneEnvironment.calm());
			highPitch.step(climb, 0.005, DroneEnvironment.calm());
		}

		assertTrue(Math.toDegrees(lowPitch.state().averageRotorBladeAngleOfAttackRadians()) < -1.0);
		assertTrue(Math.toDegrees(highPitch.state().averageRotorBladeAngleOfAttackRadians())
				> Math.toDegrees(lowPitch.state().averageRotorBladeAngleOfAttackRadians()) + 15.0);
		assertEquals(0.0, lowPitch.state().averageRotorBladeElementStallIntensity(), 1.0e-9);
		assertTrue(averageRotorThrust(highPitch.state()) > averageRotorThrust(lowPitch.state()) * 1.08);
		assertTrue(highPitch.state().averageRotorAerodynamicLoadFactor()
				> lowPitch.state().averageRotorAerodynamicLoadFactor() + 0.018,
				() -> "lowLoad=" + lowPitch.state().averageRotorAerodynamicLoadFactor()
						+ " highLoad=" + highPitch.state().averageRotorAerodynamicLoadFactor());
		assertTrue(highPitch.state().averageMotorCurrentAmps() > lowPitch.state().averageMotorCurrentAmps() + 1.2);
	}

	@Test
	void bladeElementAngleOfAttackReportsStallInSteepDescent() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		double radius = base.rotors().get(0).radiusMeters();
		DronePhysics descending = new DronePhysics(base.withRotorBladePitchMeters(radius * 2.60));
		DroneInput loadedDescent = new DroneInput(0.62, 0.0, 0.0, 0.0, true);
		Vec3 descentFlow = new Vec3(0.0, -10.0, 0.0);

		for (int i = 0; i < 180; i++) {
			descending.state().setOrientation(Quaternion.IDENTITY);
			descending.state().setVelocityMetersPerSecond(descentFlow);
			descending.step(loadedDescent, 0.005, DroneEnvironment.calm());
		}

		assertTrue(Math.toDegrees(descending.state().averageRotorBladeAngleOfAttackRadians()) > 25.0);
		assertTrue(descending.state().averageRotorBladeElementStallIntensity() > 0.80);
		assertTrue(descending.state().averageRotorStallIntensity() > 0.55);
		assertTrue(descending.state().rotorVibration() > 0.18);
	}

	@Test
	void rotorWindmillingTelemetryTracksLowThrottleReverseAxialFlow() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics still = new DronePhysics(config);
		DronePhysics windmilling = new DronePhysics(config);
		DronePhysics poweredDescent = new DronePhysics(config);
		DroneInput idle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);
		DroneInput powered = new DroneInput(0.55, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 180; i++) {
			still.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			still.state().setOrientation(Quaternion.IDENTITY);
			still.state().setVelocityMetersPerSecond(Vec3.ZERO);
			still.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			still.step(idle, 0.005, DroneEnvironment.calm());

			windmilling.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			windmilling.state().setOrientation(Quaternion.IDENTITY);
			windmilling.state().setVelocityMetersPerSecond(new Vec3(0.0, -14.0, 0.0));
			windmilling.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			windmilling.step(idle, 0.005, DroneEnvironment.calm());

			poweredDescent.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			poweredDescent.state().setOrientation(Quaternion.IDENTITY);
			poweredDescent.state().setVelocityMetersPerSecond(new Vec3(0.0, -14.0, 0.0));
			poweredDescent.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			poweredDescent.step(powered, 0.005, DroneEnvironment.calm());
		}

		assertTrue(still.state().maxRotorWindmillingIntensity() < 0.01,
				() -> "stillWindmill=" + still.state().maxRotorWindmillingIntensity());
		assertTrue(windmilling.state().maxRotorWindmillingIntensity() > 0.20,
				() -> "windmilling=" + windmilling.state().maxRotorWindmillingIntensity());
		assertTrue(poweredDescent.state().maxRotorWindmillingIntensity() < 0.05,
				() -> "poweredWindmill=" + poweredDescent.state().maxRotorWindmillingIntensity());
	}

	@Test
	void windmillingGeneratorLoadReportsPerMotorRegenCurrent() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 1.0)
				.withMotorIdleAndAirmode(0.055, 1.0)
				.withMotorTimeConstantSeconds(0.005)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics windmilling = new DronePhysics(config);
		DronePhysics poweredDescent = new DronePhysics(config);
		DroneInput idle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);
		DroneInput powered = new DroneInput(0.55, 0.0, 0.0, 0.0, true);
		Vec3 descent = new Vec3(0.0, -14.0, 0.0);
		double maxWindmillingMotorRegen = 0.0;
		double maxPoweredMotorRegen = 0.0;

		for (int i = 0; i < 180; i++) {
			windmilling.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			windmilling.state().setOrientation(Quaternion.IDENTITY);
			windmilling.state().setVelocityMetersPerSecond(descent);
			windmilling.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			windmilling.step(idle, 0.005, DroneEnvironment.calm());
			maxWindmillingMotorRegen = Math.max(maxWindmillingMotorRegen, windmilling.state().maxMotorRegenerativeCurrentAmps());

			poweredDescent.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
			poweredDescent.state().setOrientation(Quaternion.IDENTITY);
			poweredDescent.state().setVelocityMetersPerSecond(descent);
			poweredDescent.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			poweredDescent.step(powered, 0.005, DroneEnvironment.calm());
			maxPoweredMotorRegen = Math.max(maxPoweredMotorRegen, poweredDescent.state().maxMotorRegenerativeCurrentAmps());
		}
		double observedWindmillingMotorRegen = maxWindmillingMotorRegen;
		double observedPoweredMotorRegen = maxPoweredMotorRegen;

		assertTrue(windmilling.state().maxRotorWindmillingIntensity() > 0.20,
				() -> "windmilling=" + windmilling.state().maxRotorWindmillingIntensity());
		assertTrue(observedWindmillingMotorRegen > 0.005,
				() -> "windmillMotorRegen=" + observedWindmillingMotorRegen);
		assertEquals(
				windmilling.state().batteryRegenerativeCurrentAmps(),
				windmilling.state().averageMotorRegenerativeCurrentAmps() * windmilling.state().motorCount(),
				1.0e-9
		);
		assertTrue(poweredDescent.state().maxRotorWindmillingIntensity() < 0.05,
				() -> "poweredWindmill=" + poweredDescent.state().maxRotorWindmillingIntensity());
		assertTrue(observedPoweredMotorRegen < observedWindmillingMotorRegen * 0.35,
				() -> "windmillMotorRegen=" + observedWindmillingMotorRegen
						+ " poweredMotorRegen=" + observedPoweredMotorRegen);
	}

	@Test
	void bladeElementStallBuildsAndClearsWithSeparationLag() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		double radius = base.rotors().get(0).radiusMeters();
		DronePhysics physics = new DronePhysics(base.withRotorBladePitchMeters(radius * 2.60));
		DroneInput loaded = new DroneInput(0.62, 0.0, 0.0, 0.0, true);
		Vec3 cleanFlow = new Vec3(0.0, 12.0, 0.0);
		Vec3 descentFlow = new Vec3(0.0, -10.0, 0.0);

		for (int i = 0; i < 160; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(cleanFlow);
			physics.step(loaded, 0.005);
		}
		double cleanStall = physics.state().averageRotorBladeElementStallIntensity();

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(descentFlow);
		physics.step(loaded, 0.005);
		double firstDescentStall = physics.state().averageRotorBladeElementStallIntensity();

		for (int i = 0; i < 180; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(descentFlow);
			physics.step(loaded, 0.005);
		}
		double settledDescentStall = physics.state().averageRotorBladeElementStallIntensity();
		double settledVibration = physics.state().rotorVibration();

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(cleanFlow);
		physics.step(loaded, 0.005);
		double lingeringCleanStall = physics.state().averageRotorBladeElementStallIntensity();

		for (int i = 0; i < 320; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(cleanFlow);
			physics.step(loaded, 0.005);
		}
		double clearedCleanStall = physics.state().averageRotorBladeElementStallIntensity();

		assertTrue(cleanStall < 0.02, () -> "cleanStall=" + cleanStall);
		assertTrue(firstDescentStall < settledDescentStall * 0.45,
				() -> "firstDescentStall=" + firstDescentStall
						+ " settledDescentStall=" + settledDescentStall);
		assertTrue(settledDescentStall > 0.80,
				() -> "settledDescentStall=" + settledDescentStall);
		assertTrue(settledVibration > 0.18, () -> "settledVibration=" + settledVibration);
		assertTrue(lingeringCleanStall > clearedCleanStall + 0.20,
				() -> "lingeringCleanStall=" + lingeringCleanStall
						+ " clearedCleanStall=" + clearedCleanStall);
		assertTrue(clearedCleanStall < 0.04, () -> "clearedCleanStall=" + clearedCleanStall);
	}

	@Test
	void barometerModelsRotorWashAndGroundPressureError() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics idle = new DronePhysics(config);
		DronePhysics punch = new DronePhysics(config);
		DroneEnvironment nearGround = new DroneEnvironment(Vec3.ZERO, 1.0, 0.12, 0.0);
		DroneInput punchInput = new DroneInput(0.82, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 180; i++) {
			idle.state().setPositionMeters(new Vec3(0.0, 0.25, 0.0));
			idle.state().setVelocityMetersPerSecond(Vec3.ZERO);
			idle.step(DroneInput.idle(), 0.005, nearGround);

			punch.state().setPositionMeters(new Vec3(0.0, 0.25, 0.0));
			punch.state().setVelocityMetersPerSecond(new Vec3(0.0, -6.0, 0.0));
			punch.step(punchInput, 0.005, nearGround);
		}

		assertTrue(Math.abs(punch.state().barometerPropwashErrorMeters()) > Math.abs(idle.state().barometerPropwashErrorMeters()) + 0.20);
		assertTrue(Math.abs(punch.state().barometerErrorMeters()) > 0.15);
	}

	@Test
	void imuMeasurementsClipAtRealisticSensorFullScale() {
		DroneConfig gyroConfig = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics gyroClip = new DronePhysics(gyroConfig);
		gyroClip.state().setAngularVelocityBodyRadiansPerSecond(new Vec3(Math.toRadians(2600.0), 0.0, -Math.toRadians(2400.0)));

		gyroClip.step(new DroneInput(0.0, 0.0, 0.0, 0.0, true), 0.005);

		assertTrue(gyroClip.state().gyroClipIntensity() > 0.25);
		assertEquals(2000.0, Math.toDegrees(gyroClip.state().gyroAngularVelocityBodyRadiansPerSecond().x()), 1.0);
		assertEquals(-2000.0, Math.toDegrees(gyroClip.state().gyroAngularVelocityBodyRadiansPerSecond().z()), 1.0);

		DroneConfig accelConfig = directControl(DroneConfig.racingQuad())
				.withMassKg(0.12)
				.withRotorMaxThrustNewtons(80.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 200.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics accelClip = new DronePhysics(accelConfig);
		DroneInput punch = new DroneInput(1.0, 0.0, 0.0, 0.0, true);
		double maxAccelerometerClip = 0.0;

		for (int i = 0; i < 16; i++) {
			accelClip.step(punch, 0.005);
			maxAccelerometerClip = Math.max(maxAccelerometerClip, accelClip.state().accelerometerClipIntensity());
		}

		assertTrue(maxAccelerometerClip > 0.05);
		assertTrue(accelClip.state().accelerometerBodyMetersPerSecondSquared().y() <= 16.0 * 9.80665 + 1.0);
	}

	@Test
	void imuBiasDriftsWithHeatAndVibrationAndResetsOnCalibration() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.08, 1000.0, 0.8, 0.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics drifting = new DronePhysics(config);
		DronePhysics ideal = new DronePhysics(config.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0));
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);

		for (int i = 0; i < drifting.state().motorCount(); i++) {
			drifting.state().setMotorTemperatureCelsius(i, 95.0);
			ideal.state().setMotorTemperatureCelsius(i, 95.0);
		}
		drifting.state().damageAllRotors(0.35);
		ideal.state().damageAllRotors(0.35);

		for (int i = 0; i < 520; i++) {
			drifting.step(hover, 0.005);
			ideal.step(hover, 0.005);
		}

		assertTrue(Math.toDegrees(drifting.state().gyroBiasBodyRadiansPerSecond().length()) > 0.35);
		assertTrue(drifting.state().accelerometerBiasBodyMetersPerSecondSquared().length() > 0.08);
		assertEquals(0.0, ideal.state().gyroBiasBodyRadiansPerSecond().length(), 1.0e-12);
		assertEquals(0.0, ideal.state().accelerometerBiasBodyMetersPerSecondSquared().length(), 1.0e-12);

		drifting.resetControlLoops();

		assertEquals(0.0, drifting.state().gyroBiasBodyRadiansPerSecond().length(), 1.0e-12);
		assertEquals(0.0, drifting.state().accelerometerBiasBodyMetersPerSecondSquared().length(), 1.0e-12);
	}

	@Test
	void throttlePunchExcitesRotorArmFlexAndVibration() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.92, 0.0, 0.0, 0.0, true);
		double maxArmFlex = 0.0;
		double maxArmFlexDeflectionMillimeters = 0.0;
		double maxArmFlexTiltDegrees = 0.0;
		double maxRotorVibration = 0.0;

		for (int i = 0; i < 120; i++) {
			physics.step(punch, 0.0025);
			maxArmFlex = Math.max(maxArmFlex, physics.state().maxRotorArmFlexIntensity());
			maxArmFlexDeflectionMillimeters = Math.max(
					maxArmFlexDeflectionMillimeters,
					physics.state().maxRotorArmFlexDeflectionMeters() * 1000.0
			);
			maxArmFlexTiltDegrees = Math.max(
					maxArmFlexTiltDegrees,
					Math.toDegrees(physics.state().maxRotorArmFlexTiltRadians())
			);
			maxRotorVibration = Math.max(maxRotorVibration, physics.state().rotorVibration());
		}

		assertTrue(maxArmFlex > 0.03);
		double observedMaxArmFlexDeflectionMillimeters = maxArmFlexDeflectionMillimeters;
		double observedMaxArmFlexTiltDegrees = maxArmFlexTiltDegrees;
		assertTrue(observedMaxArmFlexDeflectionMillimeters > 0.20,
				() -> "maxArmFlexMm=" + observedMaxArmFlexDeflectionMillimeters);
		assertTrue(observedMaxArmFlexTiltDegrees > 0.05,
				() -> "maxArmFlexTiltDeg=" + observedMaxArmFlexTiltDegrees);
		assertTrue(maxRotorVibration > 0.005);
		Vec3 rotor0Arm = config.rotors().get(0).positionBodyMeters();
		Vec3 rotor0Radial = new Vec3(rotor0Arm.x(), 0.0, rotor0Arm.z()).normalized();
		double inwardFlexForce = -physics.state().rotorForceBodyNewtons(0).dot(rotor0Radial);
		assertTrue(inwardFlexForce > 0.005);

		physics.resetControlLoops();

		assertEquals(0.0, physics.state().averageRotorArmFlexIntensity(), 1.0e-9);
		assertEquals(0.0, physics.state().averageRotorArmFlexDeflectionMeters(), 1.0e-9);
		assertEquals(0.0, physics.state().averageRotorArmFlexTiltRadians(), 1.0e-9);
	}

	@Test
	void rotorArmFlexRingsDownAfterThrottleChop() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.95, 0.0, 0.0, 0.0, true);
		DroneInput chop = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 8; i++) {
			physics.step(punch, 0.0025);
		}
		double flexAtChop = physics.state().maxRotorArmFlexIntensity();
		double postChopPeak = flexAtChop;
		for (int i = 0; i < 26; i++) {
			physics.step(chop, 0.0025);
			postChopPeak = Math.max(postChopPeak, physics.state().maxRotorArmFlexIntensity());
		}

		assertTrue(flexAtChop > 0.01);
		double observedPostChopPeak = postChopPeak;
		assertTrue(observedPostChopPeak > flexAtChop + 0.002,
				() -> "flexAtChop=" + flexAtChop + " postChopPeak=" + observedPostChopPeak);

		for (int i = 0; i < 260; i++) {
			physics.step(chop, 0.005);
		}

		assertTrue(physics.state().maxRotorArmFlexIntensity() < observedPostChopPeak * 0.45);
	}

	@Test
	void damagedRotorsAddImuVibrationEvenWithIdealSensorNoise() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics damaged = new DronePhysics(config);
		damaged.state().damageAllRotors(0.45);
		DroneInput hoverInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		double cleanGyroMagnitudeSquaredSum = 0.0;
		double damagedGyroMagnitudeSquaredSum = 0.0;
		int gyroSamples = 0;

		for (int i = 0; i < 240; i++) {
			clean.step(hoverInput, 0.005);
			damaged.step(hoverInput, 0.005);
			if (i >= 120) {
				cleanGyroMagnitudeSquaredSum += clean.state().gyroAngularVelocityBodyRadiansPerSecond().lengthSquared();
				damagedGyroMagnitudeSquaredSum += damaged.state().gyroAngularVelocityBodyRadiansPerSecond().lengthSquared();
				gyroSamples++;
			}
		}

		assertTrue(clean.state().rotorVibration() < 0.04);
		assertTrue(damaged.state().rotorVibration() > clean.state().rotorVibration() + 0.02);
		assertTrue(damaged.state().gyroDynamicNotchFrequencyHertz() > 40.0);
		assertEquals(
				damaged.state().gyroDynamicNotchFrequencyHertz() * config.rotors().get(0).bladeCount(),
				damaged.state().gyroBladePassNotchFrequencyHertz(),
				1.0e-9
		);
		assertTrue(damaged.state().gyroDynamicNotchAttenuation() > clean.state().gyroDynamicNotchAttenuation());
		assertTrue(damaged.state().gyroBladePassNotchAttenuation() > clean.state().gyroBladePassNotchAttenuation());
		double cleanGyroRms = Math.sqrt(cleanGyroMagnitudeSquaredSum / gyroSamples);
		double damagedGyroRms = Math.sqrt(damagedGyroMagnitudeSquaredSum / gyroSamples);
		assertTrue(damagedGyroRms > cleanGyroRms + 0.01,
				() -> "cleanGyroRms=" + cleanGyroRms + " damagedGyroRms=" + damagedGyroRms);
	}

	@Test
	void attitudeEstimatorFollowsIdealGyroIntegration() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withAttitudeEstimator(0.0, 4.0);
		DronePhysics physics = new DronePhysics(config);
		Vec3 bodyRate = new Vec3(0.0, 0.0, 1.5);
		physics.state().setAngularVelocityBodyRadiansPerSecond(bodyRate);
		DroneInput armedIdle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 120; i++) {
			physics.step(armedIdle, 0.005);
		}

		double trueRoll = physics.state().orientation().toEulerXYZRadians().z();
		double estimatedRoll = physics.state().estimatedOrientation().toEulerXYZRadians().z();
		assertEquals(trueRoll, estimatedRoll, Math.toRadians(2.0));
		assertTrue(Math.abs(physics.state().attitudeEstimateErrorRadians().z()) < Math.toRadians(2.0));
	}

	@Test
	void attitudeEstimatorDropsAccelerometerTrustDuringLargeSpecificForce() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withAttitudeEstimator(4.0, 0.5)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics hover = new DronePhysics(config);
		DronePhysics punch = new DronePhysics(config);

		for (int i = 0; i < 80; i++) {
			hover.step(new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true), 0.005);
			punch.step(new DroneInput(1.0, 0.0, 0.0, 0.0, true), 0.005);
		}

		assertTrue(hover.state().attitudeEstimatorAccelerometerTrust() > 0.4);
		assertEquals(0.0, punch.state().attitudeEstimatorAccelerometerTrust(), 1.0e-9);
	}

	@Test
	void attitudeEstimatorDropsAccelerometerTrustDuringHighVibration() throws ReflectiveOperationException {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withAttitudeEstimator(4.0, 4.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics vibrating = new DronePhysics(config);
		Method correctionMethod = DronePhysics.class.getDeclaredMethod(
				"attitudeAccelerometerCorrectionBody",
				Quaternion.class
		);
		correctionMethod.setAccessible(true);
		Vec3 oneG = new Vec3(0.0, config.gravityMetersPerSecondSquared(), 0.0);

		clean.state().setAccelerometerBodyMetersPerSecondSquared(oneG);
		vibrating.state().setAccelerometerBodyMetersPerSecondSquared(oneG);
		vibrating.state().setRotorVibration(0.32);
		correctionMethod.invoke(clean, Quaternion.IDENTITY);
		correctionMethod.invoke(vibrating, Quaternion.IDENTITY);

		assertTrue(clean.state().attitudeEstimatorAccelerometerTrust() > 0.98);
		assertTrue(vibrating.state().attitudeEstimatorAccelerometerTrust()
						< clean.state().attitudeEstimatorAccelerometerTrust() * 0.35,
				() -> "cleanTrust=" + clean.state().attitudeEstimatorAccelerometerTrust()
						+ " vibratingTrust=" + vibrating.state().attitudeEstimatorAccelerometerTrust());
	}

	@Test
	void attitudeEstimatorAccelerometerCorrectionReducesStaticRollError() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withAttitudeEstimator(3.0, 4.0);
		DronePhysics physics = new DronePhysics(config);
		physics.state().setPositionMeters(new Vec3(0.0, 10.0, 0.0));
		DroneInput hoverInput = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 300; i++) {
			physics.step(hoverInput, 0.005);
		}

		Quaternion biasedEstimate = physics.state().estimatedOrientation()
				.integrateBodyAngularVelocity(new Vec3(0.0, 0.0, Math.toRadians(25.0)), 1.0);
		physics.state().setEstimatedOrientation(biasedEstimate);
		double initialError = Math.abs(physics.state().orientation().toEulerXYZRadians().z() - biasedEstimate.toEulerXYZRadians().z());

		for (int i = 0; i < 300; i++) {
			physics.step(hoverInput, 0.005);
		}

		double correctedError = Math.abs(physics.state().attitudeEstimateErrorRadians().z());
		assertTrue(physics.state().attitudeEstimatorAccelerometerTrust() > 0.2);
		assertTrue(correctedError < initialError * 0.5);
	}

	@Test
	void windCreatesRelativeAirDrag() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneEnvironment tailwind = new DroneEnvironment(new Vec3(8.0, 0.0, 0.0), 1.0, Double.POSITIVE_INFINITY);

		for (int i = 0; i < 200; i++) {
			physics.step(DroneInput.idle(), 0.005, tailwind);
		}

		assertTrue(physics.state().velocityMetersPerSecond().x() > 0.4);
	}

	@Test
	void airframeDragTelemetrySeparatesBodyDragFromDamping() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics physics = new DronePhysics(config);

		for (int i = 0; i < 120; i++) {
			holdInCruise(physics, new Vec3(0.0, 0.0, 10.0));
			physics.step(DroneInput.idle(), 0.005, DroneEnvironment.calm());
		}

		Vec3 bodyDrag = physics.state().airframeBodyDragForceBodyNewtons();
		Vec3 dampingDrag = physics.state().linearDampingDragForceWorldNewtons();
		assertEquals(0.0, bodyDrag.x(), 1.0e-9);
		assertEquals(0.0, bodyDrag.y(), 1.0e-9);
		assertEquals(-config.bodyDragCoefficients().z() * 100.0, bodyDrag.z(), 0.03);
		assertEquals(0.0, dampingDrag.x(), 1.0e-9);
		assertEquals(0.0, dampingDrag.y(), 1.0e-9);
		assertEquals(-config.linearDragCoefficient() * 10.0, dampingDrag.z(), 0.03);
	}

	@Test
	void racingQuadDefaultDragMatchesLowSpeedAirframeReference() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics lateral = new DronePhysics(config);
		DronePhysics forward = new DronePhysics(config);

		for (int i = 0; i < 140; i++) {
			holdInCruise(lateral, new Vec3(10.0, 0.0, 0.0));
			holdInCruise(forward, new Vec3(0.0, 0.0, 10.0));
			lateral.step(DroneInput.idle(), 0.005);
			forward.step(DroneInput.idle(), 0.005);
		}

		double lateralBodyDrag = -lateral.state().airframeBodyDragForceBodyNewtons().x();
		double lateralDampingDrag = -lateral.state().linearDampingDragForceWorldNewtons().x();
		double forwardBodyDrag = -forward.state().airframeBodyDragForceBodyNewtons().z();
		double forwardDampingDrag = -forward.state().linearDampingDragForceWorldNewtons().z();
		double lateralTotalDrag = lateralBodyDrag + lateralDampingDrag;
		double forwardTotalDrag = forwardBodyDrag + forwardDampingDrag;
		double forwardReferenceDrag = AirframeDragCalibration.imav2022ReferenceDragForceNewtons(
				config,
				10.0,
				1.0
		);

		assertTrue(lateralBodyDrag < 0.40, () -> "lateralBodyDrag=" + lateralBodyDrag);
		assertTrue(forwardBodyDrag < 0.65, () -> "forwardBodyDrag=" + forwardBodyDrag);
		assertTrue(lateralTotalDrag > 1.85 && lateralTotalDrag < 2.25,
				() -> "lateralTotalDrag=" + lateralTotalDrag);
		assertTrue(forwardTotalDrag > 2.05 && forwardTotalDrag < 2.55,
				() -> "forwardTotalDrag=" + forwardTotalDrag);
		assertEquals(forwardTotalDrag, forward.state().airframeDragAlongFlowNewtons(), 0.08);
		assertEquals(forwardTotalDrag / 10.0, forward.state().airframeDragEquivalentLinearCoefficient(), 0.01);
		assertEquals(forwardTotalDrag / forwardReferenceDrag, forward.state().airframeDragImavReferenceRatio(), 0.04);
		assertTrue(forward.state().airframeDragEquivalentCdAMetersSquared() > 0.025,
				() -> "equivalentCdA=" + forward.state().airframeDragEquivalentCdAMetersSquared());
		assertTrue(forward.state().airframeDragEquivalentCdAMetersSquared() < 0.050,
				() -> "equivalentCdA=" + forward.state().airframeDragEquivalentCdAMetersSquared());
	}

	@Test
	void racingQuadPassiveCoastdownMatchesImavMassFitReference() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		AirframeDragCalibration.Coastdown lateralLowSpeed = AirframeDragCalibration.coastdown(
				config,
				AirframeDragCalibration.Axis.X,
				12.5,
				2.5
		);
		AirframeDragCalibration.Coastdown forwardLowSpeed = AirframeDragCalibration.coastdown(
				config,
				AirframeDragCalibration.Axis.Z,
				12.5,
				2.5
		);
		AirframeDragCalibration.Coastdown lateralFast = AirframeDragCalibration.coastdown(
				config,
				AirframeDragCalibration.Axis.X,
				20.0,
				5.0
		);
		AirframeDragCalibration.Coastdown forwardFast = AirframeDragCalibration.coastdown(
				config,
				AirframeDragCalibration.Axis.Z,
				20.0,
				5.0
		);

		assertEquals(0.2007, lateralLowSpeed.referenceLinearDragCoefficient(), 1.0e-6);
		assertEquals(2.007, AirframeDragCalibration.imav2022ReferenceDragForceNewtons(config, 10.0, 1.0), 1.0e-6);
		assertEquals(config.linearDragCoefficient(), lateralLowSpeed.linearDampingCoefficient(), 1.0e-12);
		assertEquals(config.bodyDragCoefficients().x(), lateralLowSpeed.bodyQuadraticCoefficient(), 1.0e-12);
		assertEquals(config.bodyDragCoefficients().z(), forwardLowSpeed.bodyQuadraticCoefficient(), 1.0e-12);
		assertTrue(lateralLowSpeed.timeRatioToReference() > 1.00 && lateralLowSpeed.timeRatioToReference() < 1.06,
				() -> "lateralLowSpeed=" + lateralLowSpeed);
		assertTrue(forwardLowSpeed.timeRatioToReference() > 0.94 && forwardLowSpeed.timeRatioToReference() < 1.00,
				() -> "forwardLowSpeed=" + forwardLowSpeed);
		assertTrue(lateralLowSpeed.distanceRatioToReference() > 0.99 && lateralLowSpeed.distanceRatioToReference() < 1.03,
				() -> "lateralLowSpeed=" + lateralLowSpeed);
		assertTrue(forwardLowSpeed.distanceRatioToReference() > 0.92 && forwardLowSpeed.distanceRatioToReference() < 0.97,
				() -> "forwardLowSpeed=" + forwardLowSpeed);
		assertTrue(lateralFast.timeRatioToReference() > 0.94 && lateralFast.timeRatioToReference() < 1.00,
				() -> "lateralFast=" + lateralFast);
		assertTrue(forwardFast.timeRatioToReference() > 0.85 && forwardFast.timeRatioToReference() < 0.92,
				() -> "forwardFast=" + forwardFast);
		assertTrue(lateralFast.distanceRatioToReference() > 0.92 && lateralFast.distanceRatioToReference() < 0.99,
				() -> "lateralFast=" + lateralFast);
		assertTrue(forwardFast.distanceRatioToReference() > 0.82 && forwardFast.distanceRatioToReference() < 0.89,
				() -> "forwardFast=" + forwardFast);
		assertTrue(lateralFast.initialDragForceNewtons() < 5.0);
		assertTrue(forwardFast.initialDragForceNewtons() < 5.5);
	}

	@Test
	void airframeDragCalibrationReportsRealFpvSpeedEnvelopeReachability() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		AirframeDragCalibration.LevelFlightRequirement aiioManualHigh =
				AirframeDragCalibration.worstHorizontalLevelFlightRequirement(config, 14.0, 1.0);
		AirframeDragCalibration.LevelFlightRequirement ratmHighSpeed =
				AirframeDragCalibration.worstHorizontalLevelFlightRequirement(config, 21.0, 1.0);
		AirframeDragCalibration.LevelFlightRequirement uzhFpvVmax =
				AirframeDragCalibration.worstHorizontalLevelFlightRequirement(config, 26.79, 1.0);

		assertEquals(AirframeDragCalibration.Axis.Z, aiioManualHigh.axis());
		assertEquals(54.0, aiioManualHigh.maxTotalThrustNewtons(), 1.0e-9);
		assertTrue(aiioManualHigh.reachable());
		assertTrue(ratmHighSpeed.reachable());
		assertTrue(uzhFpvVmax.reachable());
		assertTrue(aiioManualHigh.baseDragForceNewtons() > 3.3 && aiioManualHigh.baseDragForceNewtons() < 3.5,
				() -> "aiio=" + aiioManualHigh);
		assertTrue(ratmHighSpeed.baseDragForceNewtons() > aiioManualHigh.baseDragForceNewtons());
		assertTrue(uzhFpvVmax.baseDragForceNewtons() > ratmHighSpeed.baseDragForceNewtons());
		assertTrue(aiioManualHigh.requiredMaxThrustFraction() < ratmHighSpeed.requiredMaxThrustFraction());
		assertTrue(ratmHighSpeed.requiredMaxThrustFraction() < uzhFpvVmax.requiredMaxThrustFraction());
		assertTrue(uzhFpvVmax.requiredMaxThrustFraction() < 0.30, () -> "uzh=" + uzhFpvVmax);
		assertTrue(uzhFpvVmax.requiredTiltDegrees() > 34.0 && uzhFpvVmax.requiredTiltDegrees() < 39.0,
				() -> "uzhTilt=" + uzhFpvVmax.requiredTiltDegrees());
		assertTrue(uzhFpvVmax.dragToHorizontalMarginRatio() < 0.20,
				() -> "uzhMarginRatio=" + uzhFpvVmax.dragToHorizontalMarginRatio());
		double dragLimitedForwardSpeed = AirframeDragCalibration.dragLimitedLevelSpeedMetersPerSecond(
				config,
				AirframeDragCalibration.Axis.Z,
				1.0
		);
		double denseAirDragLimitedForwardSpeed = AirframeDragCalibration.dragLimitedLevelSpeedMetersPerSecond(
				config,
				AirframeDragCalibration.Axis.Z,
				2.0
		);
		DroneConfig noDrag = config.withLinearDragCoefficient(0.0).withBodyDragCoefficients(Vec3.ZERO);

		assertTrue(dragLimitedForwardSpeed > 89.0 && dragLimitedForwardSpeed < 92.0,
				() -> "dragLimitedForwardSpeed=" + dragLimitedForwardSpeed);
		assertTrue(denseAirDragLimitedForwardSpeed < dragLimitedForwardSpeed);
		assertEquals(
				Double.POSITIVE_INFINITY,
				AirframeDragCalibration.dragLimitedLevelSpeedMetersPerSecond(
						noDrag,
						AirframeDragCalibration.Axis.Z,
						1.0
				),
				0.0
		);
	}

	@Test
	void airframeDragCalibrationFitsBodyCoefficientFromMeasuredCoastdown() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		AirframeDragCalibration.Coastdown observed = AirframeDragCalibration.coastdown(
				config,
				AirframeDragCalibration.Axis.Z,
				20.0,
				5.0
		);

		AirframeDragCalibration.BodyDragFit fit = AirframeDragCalibration.fitBodyQuadraticCoefficientForCoastdownTime(
				config,
				AirframeDragCalibration.Axis.Z,
				20.0,
				5.0,
				observed.timeSeconds()
		);

		assertTrue(fit.targetReachable());
		assertEquals(AirframeDragCalibration.Axis.Z, fit.axis());
		assertEquals(config.linearDragCoefficient(), fit.linearDampingCoefficient(), 1.0e-12);
		assertEquals(config.bodyDragCoefficients().z(), fit.bodyQuadraticCoefficient(), 1.0e-9);
		assertEquals(observed.timeSeconds(), fit.achievedTimeSeconds(), 1.0e-9);
		assertEquals(observed.distanceMeters(), fit.achievedDistanceMeters(), 1.0e-8);
		assertEquals(0.0, fit.timeResidualSeconds(), 1.0e-9);
	}

	@Test
	void airframeDragCalibrationReportsUnreachableSlowTargetWhenLinearDampingIsTooHigh() {
		DroneConfig overdamped = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.42)
				.withBodyDragCoefficients(Vec3.ZERO);

		AirframeDragCalibration.BodyDragFit fit = AirframeDragCalibration.fitBodyQuadraticCoefficientToImav2022Reference(
				overdamped,
				AirframeDragCalibration.Axis.X,
				20.0,
				5.0
		);
		AirframeDragCalibration.Coastdown zeroQuadratic = AirframeDragCalibration.coastdown(
				overdamped,
				AirframeDragCalibration.Axis.X,
				20.0,
				5.0
		);

		assertTrue(!fit.targetReachable());
		assertEquals(0.0, fit.bodyQuadraticCoefficient(), 1.0e-12);
		assertEquals(zeroQuadratic.timeSeconds(), fit.achievedTimeSeconds(), 1.0e-9);
		assertTrue(fit.achievedTimeSeconds() < fit.targetTimeSeconds());
		assertTrue(fit.timeResidualSeconds() < 0.0);
	}

	@Test
	void rotorWashDragAppearsOnlyWithPoweredSlipstreamAndRelativeMotion() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics powered = new DronePhysics(config);
		DronePhysics unpowered = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.04, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 100; i++) {
			powered.step(hover, 0.005);
		}

		Vec3 slip = new Vec3(11.0, -1.5, 4.0);
		for (int i = 0; i < 8; i++) {
			holdInCruise(powered, slip);
			holdInCruise(unpowered, slip);
			powered.step(hover, 0.005);
			unpowered.step(DroneInput.idle(), 0.005);
		}

		Vec3 washDrag = powered.state().rotorWashDragForceBodyNewtons();
		assertEquals(0.0, unpowered.state().rotorWashDragForceBodyNewtons().length(), 1.0e-9);
		assertTrue(washDrag.x() < -0.45);
		assertTrue(washDrag.y() > 0.012);
		assertTrue(washDrag.z() < -0.15);
		assertTrue(powered.state().linearAccelerationWorldMetersPerSecondSquared().x()
				< unpowered.state().linearAccelerationWorldMetersPerSecondSquared().x() - 0.35);
	}

	@Test
	void rotorWashDragBuildsAndReleasesWithSlipstreamPressureLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.05, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 130; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005);
		}

		Vec3 slip = new Vec3(10.0, 0.0, 4.0);
		holdInCruise(physics, slip);
		physics.step(hover, 0.005);
		double firstSlipDragX = physics.state().rotorWashDragForceBodyNewtons().x();
		for (int i = 0; i < 12; i++) {
			holdInCruise(physics, slip);
			physics.step(hover, 0.005);
		}
		double settledSlipDragX = physics.state().rotorWashDragForceBodyNewtons().x();

		holdInStillAir(physics);
		physics.step(hover, 0.005);
		double lingeringDragX = physics.state().rotorWashDragForceBodyNewtons().x();
		for (int i = 0; i < 70; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005);
		}

		assertTrue(firstSlipDragX < -0.10);
		assertTrue(settledSlipDragX < firstSlipDragX - 0.20,
				() -> "firstSlipDragX=" + firstSlipDragX + " settledSlipDragX=" + settledSlipDragX);
		assertTrue(lingeringDragX < settledSlipDragX * 0.45,
				() -> "settledSlipDragX=" + settledSlipDragX + " lingeringDragX=" + lingeringDragX);
		assertEquals(0.0, physics.state().rotorWashDragForceBodyNewtons().length(), 0.08);
	}

	@Test
	void rotorWashDragProjectsMoreAirframeAreaAtHighSideslip() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics shallowSlip = new DronePhysics(config);
		DronePhysics highSlip = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.05, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 130; i++) {
			holdInStillAir(shallowSlip);
			holdInStillAir(highSlip);
			shallowSlip.step(hover, 0.005);
			highSlip.step(hover, 0.005);
		}

		Vec3 shallow = new Vec3(8.0, 0.0, 14.0);
		Vec3 broadside = new Vec3(8.0, 0.0, 4.0);
		for (int i = 0; i < 8; i++) {
			holdInCruise(shallowSlip, shallow);
			holdInCruise(highSlip, broadside);
			shallowSlip.step(hover, 0.005);
			highSlip.step(hover, 0.005);
		}

		Vec3 shallowWash = shallowSlip.state().rotorWashDragForceBodyNewtons();
		Vec3 highWash = highSlip.state().rotorWashDragForceBodyNewtons();
		assertTrue(Math.toDegrees(shallowSlip.state().sideslipRadians()) < 35.0);
		assertTrue(Math.toDegrees(highSlip.state().sideslipRadians()) > 60.0);
		assertTrue(highWash.x() < shallowWash.x() - 0.08,
				() -> "shallowWash=" + shallowWash + " highWash=" + highWash);
	}

	@Test
	void rotorWashDragAtPressureCenterAddsAirframeMoment() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withCenterOfPressureOffsetBodyMeters(new Vec3(0.0, 0.010, 0.0))
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics unpowered = new DronePhysics(config);
		DronePhysics powered = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.06, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 130; i++) {
			holdInStillAir(powered);
			powered.step(hover, 0.005);
		}

		Vec3 slip = new Vec3(12.0, 0.0, 3.0);
		for (int i = 0; i < 8; i++) {
			holdInCruise(unpowered, slip);
			holdInCruise(powered, slip);
			unpowered.step(DroneInput.idle(), 0.005);
			powered.step(hover, 0.005);
		}

		Vec3 unpoweredTorque = unpowered.state().airframePressureCenterTorqueBodyNewtonMeters();
		Vec3 poweredTorque = powered.state().airframePressureCenterTorqueBodyNewtonMeters();
		Vec3 washDrag = powered.state().rotorWashDragForceBodyNewtons();

		assertTrue(washDrag.x() < -0.45);
		assertTrue(poweredTorque.z() > unpoweredTorque.z() + 0.006,
				() -> "unpoweredTorque=" + unpoweredTorque + " poweredTorque=" + poweredTorque);
		assertTrue(powered.state().airframeAerodynamicTorqueBodyNewtonMeters().z()
				> unpowered.state().airframeAerodynamicTorqueBodyNewtonMeters().z() + 0.006);
	}

	@Test
	void groundEffectAddsNearGroundLift() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics freeAir = new DronePhysics(config);
		DronePhysics nearGround = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment groundEffect = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				config.rotors().get(0).radiusMeters()
		);

		for (int i = 0; i < 600; i++) {
			freeAir.step(hover, 0.005);
			nearGround.step(hover, 0.005, groundEffect);
		}

		assertTrue(nearGround.state().positionMeters().y() > freeAir.state().positionMeters().y() + 0.6);
	}

	@Test
	void racingQuadGroundEffectMatchesJirsCurveFitHeightResponse() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		double rotorRadius = config.rotors().get(0).radiusMeters();
		double halfRadius = DroneEnvironment.groundEffectThrustMultiplier(config, rotorRadius * 0.5);
		double oneRadius = DroneEnvironment.groundEffectThrustMultiplier(config, rotorRadius);
		double fourRadii = DroneEnvironment.groundEffectThrustMultiplier(config, rotorRadius * 4.0);
		double effectHeight = DroneEnvironment.groundEffectThrustMultiplier(config, config.groundEffectHeightMeters());

		assertEquals(1.222, halfRadius, 0.002);
		assertEquals(1.086, oneRadius, 0.002);
		assertEquals(1.0003, fourRadii, 0.0005);
		assertTrue(halfRadius > oneRadius);
		assertTrue(oneRadius > fourRadii);
		assertEquals(1.0, effectHeight, 1.0e-9);
	}

	@Test
	void groundEffectLevelingTorqueUsesZjuMomentHeightShape() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		double veryLow = DroneEnvironment.groundEffectLevelingTorqueIntensity(config, 0.05);
		double nearPeak = DroneEnvironment.groundEffectLevelingTorqueIntensity(config, 0.20);
		double high = DroneEnvironment.groundEffectLevelingTorqueIntensity(config, 0.30);
		double cutoff = DroneEnvironment.groundEffectLevelingTorqueIntensity(config, config.groundEffectHeightMeters());

		assertEquals(0.97, nearPeak, 0.04);
		assertTrue(nearPeak > veryLow * 8.0, () -> "veryLow=" + veryLow + " nearPeak=" + nearPeak);
		assertTrue(nearPeak > high * 2.5, () -> "nearPeak=" + nearPeak + " high=" + high);
		assertEquals(0.0, cutoff, 1.0e-9);
	}

	@Test
	void groundEffectLevelingTorqueRestoresTiltNearSurface() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DroneEnvironment nearPeakGroundEffect = new DroneEnvironment(Vec3.ZERO, 1.0, 0.20);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DronePhysics rolled = new DronePhysics(config);
		DronePhysics pitched = new DronePhysics(config);
		DronePhysics freeAir = new DronePhysics(config);
		Quaternion rollTilt = rollOrientation(Math.toRadians(20.0));
		Quaternion pitchTilt = pitchOrientation(Math.toRadians(20.0));

		for (int i = 0; i < 120; i++) {
			rolled.state().setOrientation(rollTilt);
			rolled.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			pitched.state().setOrientation(pitchTilt);
			pitched.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			freeAir.state().setOrientation(rollTilt);
			freeAir.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			rolled.step(hover, 0.005, nearPeakGroundEffect);
			pitched.step(hover, 0.005, nearPeakGroundEffect);
			freeAir.step(hover, 0.005, DroneEnvironment.calm());
		}

		Vec3 rollTorque = rolled.state().groundEffectLevelingTorqueBodyNewtonMeters();
		Vec3 pitchTorque = pitched.state().groundEffectLevelingTorqueBodyNewtonMeters();
		assertTrue(rollTorque.z() < -0.020, () -> "rollTorque=" + rollTorque);
		assertTrue(Math.abs(rollTorque.x()) < 0.004, () -> "rollTorque=" + rollTorque);
		assertEquals(0.0, rollTorque.y(), 1.0e-12);
		assertTrue(pitchTorque.x() < -0.020, () -> "pitchTorque=" + pitchTorque);
		assertTrue(Math.abs(pitchTorque.z()) < 0.004, () -> "pitchTorque=" + pitchTorque);
		assertEquals(0.0, pitchTorque.y(), 1.0e-12);
		assertEquals(0.0, freeAir.state().groundEffectLevelingTorqueBodyNewtonMeters().length(), 1.0e-9);
	}

	@Test
	void rotorSurfaceEffectBuildsAndReleasesWithPressureLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withGroundEffect(0.35, 0.55);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment freeAir = DroneEnvironment.calm();
		DroneEnvironment nearSurface = new DroneEnvironment(Vec3.ZERO, 1.0, 0.04);

		assertTrue(nearSurface.groundEffectThrustMultiplier(config) > 1.35);

		for (int i = 0; i < 180; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005, freeAir);
		}
		double freeThrust = averageRotorThrust(physics.state());

		holdInStillAir(physics);
		physics.step(hover, 0.005, nearSurface);
		double firstNearSurfaceThrust = averageRotorThrust(physics.state());

		for (int i = 0; i < 120; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005, nearSurface);
		}
		double settledNearSurfaceThrust = averageRotorThrust(physics.state());

		holdInStillAir(physics);
		physics.step(hover, 0.005, freeAir);
		double firstFreeAirThrust = averageRotorThrust(physics.state());

		for (int i = 0; i < 180; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005, freeAir);
		}
		double recoveredFreeThrust = averageRotorThrust(physics.state());
		double settledBoost = settledNearSurfaceThrust - recoveredFreeThrust;
		double firstNearBoost = firstNearSurfaceThrust - freeThrust;
		double firstFreeLag = firstFreeAirThrust - recoveredFreeThrust;

		assertTrue(settledNearSurfaceThrust > recoveredFreeThrust * 1.20);
		assertTrue(firstNearBoost < settledBoost * 0.45,
				() -> "firstNearBoost=" + firstNearBoost + " settledBoost=" + settledBoost);
		assertTrue(firstFreeLag > settledBoost * 0.45,
				() -> "firstFreeLag=" + firstFreeLag + " settledBoost=" + settledBoost);
		assertEquals(freeThrust, recoveredFreeThrust, freeThrust * 0.03);
	}

	@Test
	void groundEffectCushionAddsNearGroundHorizontalDrag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics freeAir = new DronePhysics(config);
		DronePhysics nearGround = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment nearSurface = new DroneEnvironment(Vec3.ZERO, 1.0, 0.08);

		for (int i = 0; i < 80; i++) {
			freeAir.step(hover, 0.005);
			nearGround.step(hover, 0.005, nearSurface);
		}

		Vec3 lateralVelocity = new Vec3(12.0, 0.0, 3.0);
		for (int i = 0; i < 70; i++) {
			freeAir.state().setOrientation(Quaternion.IDENTITY);
			nearGround.state().setOrientation(Quaternion.IDENTITY);
			freeAir.state().setVelocityMetersPerSecond(lateralVelocity);
			nearGround.state().setVelocityMetersPerSecond(lateralVelocity);
			freeAir.step(hover, 0.005);
			nearGround.step(hover, 0.005, nearSurface);
		}

		Vec3 cushionDrag = nearGround.state().groundEffectDragForceBodyNewtons();
		assertEquals(0.0, freeAir.state().groundEffectDragForceBodyNewtons().length(), 1.0e-9);
		assertTrue(cushionDrag.x() < -0.5);
		assertEquals(0.0, cushionDrag.y(), 1.0e-9);
		assertTrue(cushionDrag.z() < -0.1);
		assertTrue(nearGround.state().linearAccelerationWorldMetersPerSecondSquared().x()
				< freeAir.state().linearAccelerationWorldMetersPerSecondSquared().x() - 0.4);
	}

	@Test
	void groundEffectCushionDragBuildsAndReleasesWithPressureLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment freeAir = DroneEnvironment.calm();
		DroneEnvironment nearSurface = new DroneEnvironment(Vec3.ZERO, 1.0, 0.08);
		Vec3 lateralVelocity = new Vec3(12.0, 0.0, 3.0);

		for (int i = 0; i < 100; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005, nearSurface);
		}

		holdInCruise(physics, lateralVelocity);
		physics.step(hover, 0.005, nearSurface);
		Vec3 firstNearSurfaceDrag = physics.state().groundEffectDragForceBodyNewtons();

		for (int i = 0; i < 70; i++) {
			holdInCruise(physics, lateralVelocity);
			physics.step(hover, 0.005, nearSurface);
		}
		Vec3 settledNearSurfaceDrag = physics.state().groundEffectDragForceBodyNewtons();

		holdInCruise(physics, lateralVelocity);
		physics.step(hover, 0.005, freeAir);
		Vec3 lingeringFreeAirDrag = physics.state().groundEffectDragForceBodyNewtons();

		for (int i = 0; i < 220; i++) {
			holdInCruise(physics, lateralVelocity);
			physics.step(hover, 0.005, freeAir);
		}
		Vec3 recoveredFreeAirDrag = physics.state().groundEffectDragForceBodyNewtons();

		assertTrue(firstNearSurfaceDrag.x() < -0.05,
				() -> "firstNearSurfaceDrag=" + firstNearSurfaceDrag);
		assertTrue(settledNearSurfaceDrag.x() < firstNearSurfaceDrag.x() - 0.50,
				() -> "firstNearSurfaceDrag=" + firstNearSurfaceDrag
						+ " settledNearSurfaceDrag=" + settledNearSurfaceDrag);
		assertTrue(lingeringFreeAirDrag.x() < recoveredFreeAirDrag.x() - 0.45,
				() -> "lingeringFreeAirDrag=" + lingeringFreeAirDrag
						+ " recoveredFreeAirDrag=" + recoveredFreeAirDrag);
		assertTrue(recoveredFreeAirDrag.length() < 0.03,
				() -> "recoveredFreeAirDrag=" + recoveredFreeAirDrag);
	}

	@Test
	void ceilingEffectAddsNearCeilingLift() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		DronePhysics freeAir = new DronePhysics(config);
		DronePhysics nearCeiling = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment ceilingEffect = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				0.12
		);

		assertTrue(ceilingEffect.ceilingEffectThrustMultiplier(config) > 1.0);

		for (int i = 0; i < 600; i++) {
			freeAir.step(hover, 0.005);
			nearCeiling.step(hover, 0.005, ceilingEffect);
		}

		assertTrue(nearCeiling.state().positionMeters().y() > freeAir.state().positionMeters().y() + 0.35);
	}

	@Test
	void weightedRotorDiskSurfaceEffectBlendsPartialClearance() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		double nearClearance = 0.08;
		double[] weights = {0.36, 0.11, 0.11, 0.11, 0.11, 0.05, 0.05, 0.05, 0.05};
		double[] fullyNear = {
				nearClearance, nearClearance, nearClearance, nearClearance, nearClearance,
				nearClearance, nearClearance, nearClearance, nearClearance
		};
		double[] halfOpen = {
				nearClearance, nearClearance, nearClearance, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				nearClearance, Double.POSITIVE_INFINITY, nearClearance, Double.POSITIVE_INFINITY
		};
		double[] fullyOpen = {
				Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY
		};

		double nearGround = DroneEnvironment.groundEffectThrustMultiplier(config, nearClearance);
		double averagedNearGround = DroneEnvironment.weightedGroundEffectThrustMultiplier(config, fullyNear, weights);
		double partialGround = DroneEnvironment.weightedGroundEffectThrustMultiplier(config, halfOpen, weights);
		double openGround = DroneEnvironment.weightedGroundEffectThrustMultiplier(config, fullyOpen, weights);
		assertEquals(nearGround, averagedNearGround, 1.0e-9);
		assertEquals(1.0, openGround, 1.0e-9);
		assertTrue(partialGround > 1.0);
		assertTrue(partialGround < nearGround);

		double nearCeiling = DroneEnvironment.ceilingEffectThrustMultiplier(config, nearClearance);
		double averagedNearCeiling = DroneEnvironment.weightedCeilingEffectThrustMultiplier(config, fullyNear, weights);
		double partialCeiling = DroneEnvironment.weightedCeilingEffectThrustMultiplier(config, halfOpen, weights);
		double openCeiling = DroneEnvironment.weightedCeilingEffectThrustMultiplier(config, fullyOpen, weights);
		assertEquals(nearCeiling, averagedNearCeiling, 1.0e-9);
		assertEquals(1.0, openCeiling, 1.0e-9);
		assertTrue(partialCeiling > 1.0);
		assertTrue(partialCeiling < nearCeiling);
	}

	@Test
	void partialSurfacePatchDiameterGatesGroundAndCeilingEffect() {
		DroneConfig config = directControl(DroneConfig.racingQuad());
		double rotorRadius = config.rotors().get(0).radiusMeters();
		double propellerDiameter = rotorRadius * 2.0;
		double clearance = rotorRadius;
		double fullGround = DroneEnvironment.groundEffectThrustMultiplier(config, clearance);
		double fullCeiling = DroneEnvironment.ceilingEffectThrustMultiplier(config, clearance);

		assertEquals(0.0, DroneEnvironment.partialSurfaceEffectGate(config, propellerDiameter * 0.25), 1.0e-12);
		assertEquals(0.0, DroneEnvironment.partialSurfaceEffectGate(config, propellerDiameter * 0.50), 1.0e-12);
		assertEquals(0.5, DroneEnvironment.partialSurfaceEffectGate(config, propellerDiameter * 0.75), 1.0e-12);
		assertEquals(1.0, DroneEnvironment.partialSurfaceEffectGate(config, propellerDiameter), 1.0e-12);
		assertEquals(1.0, DroneEnvironment.partialSurfaceEffectGate(config, 1.0), 1.0e-12);

		assertEquals(1.0,
				DroneEnvironment.partialGroundEffectThrustMultiplier(config, clearance, propellerDiameter * 0.50),
				1.0e-12);
		assertEquals(1.0,
				DroneEnvironment.partialCeilingEffectThrustMultiplier(config, clearance, propellerDiameter * 0.50),
				1.0e-12);
		assertEquals(1.0 + (fullGround - 1.0) * 0.5,
				DroneEnvironment.partialGroundEffectThrustMultiplier(config, clearance, propellerDiameter * 0.75),
				1.0e-12);
		assertEquals(1.0 + (fullCeiling - 1.0) * 0.5,
				DroneEnvironment.partialCeilingEffectThrustMultiplier(config, clearance, propellerDiameter * 0.75),
				1.0e-12);
		assertEquals(fullGround,
				DroneEnvironment.partialGroundEffectThrustMultiplier(config, clearance, propellerDiameter),
				1.0e-12);
		assertEquals(fullCeiling,
				DroneEnvironment.partialCeilingEffectThrustMultiplier(config, clearance, propellerDiameter),
				1.0e-12);
		assertEquals(fullGround,
				DroneEnvironment.partialGroundEffectThrustMultiplier(config, clearance, 1.0),
				1.0e-12);
	}

	@Test
	void perRotorEnvironmentMultipliersCreateAsymmetricThrust() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment asymmetricGroundEffect = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.30, 1.0, 1.0, 1.0}
		);

		assertTrue(asymmetricGroundEffect.rotorThrustAsymmetry(config) > 0.25);

		for (int i = 0; i < 80; i++) {
			physics.step(hover, 0.005, asymmetricGroundEffect);
		}

		assertTrue(physics.state().rotorThrustNewtons(0) > physics.state().rotorThrustNewtons(1) * 1.15);
		assertTrue(physics.state().angularVelocityBodyRadiansPerSecond().length() > 0.02);
	}

	@Test
	void rotorFlowObstructionCanTriggerEscDesyncAndSensorVibration() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains);
		DronePhysics physics = new DronePhysics(config);
		DroneInput highLoad = new DroneInput(0.72, 0.0, 0.0, 0.0, true);
		DroneEnvironment obstructedFlow = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {0.72, 0.0, 0.0, 0.0}
		);

		assertTrue(obstructedFlow.maxRotorFlowObstruction() > 0.70);

		for (int i = 0; i < 160; i++) {
			physics.step(highLoad, 0.005, obstructedFlow);
		}

		assertTrue(physics.state().maxEscDesyncIntensity() > 0.03);
		assertTrue(physics.state().rotorVibration() > 0.012);
		assertTrue(physics.state().gyroDynamicNotchAttenuation() > 0.01);
	}

	@Test
	void waterImmersionReducesRotorAuthorityAndAddsFluidDrag() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics dry = new DronePhysics(config);
		DronePhysics wet = new DronePhysics(config);
		DroneInput highLoad = new DroneInput(0.70, 0.0, 0.0, 0.0, true);
		DroneEnvironment wetEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				0.72
		);

		for (int i = 0; i < 180; i++) {
			dry.step(highLoad, 0.005);
			wet.step(highLoad, 0.005, wetEnvironment);
		}

		assertTrue(wetEnvironment.waterImmersionIntensity() > 0.70);
		assertTrue(wet.state().averageRotorWetThrustScale() < dry.state().averageRotorWetThrustScale() - 0.45);
		assertTrue(wet.state().minRotorWetThrustScale() < 0.45);
		assertEquals(1.0, dry.state().minRotorWetThrustScale(), 0.001);
		assertTrue(wet.state().averageRotorAerodynamicLoadFactor()
				> dry.state().averageRotorAerodynamicLoadFactor() + 0.25);
		assertTrue(averageRotorThrust(wet.state()) < averageRotorThrust(dry.state()) * 0.65);
		assertTrue(wet.state().rotorVibration() > dry.state().rotorVibration() + 0.01);
		assertTrue(wet.state().rotorVibration() > 0.03);
		assertTrue(wet.state().maxEscDesyncIntensity() > dry.state().maxEscDesyncIntensity() + 0.02);

		DronePhysics airDrift = new DronePhysics(config);
		DronePhysics waterDrift = new DronePhysics(config);
		airDrift.state().setVelocityMetersPerSecond(new Vec3(8.0, 0.0, 0.0));
		waterDrift.state().setVelocityMetersPerSecond(new Vec3(8.0, 0.0, 0.0));

		for (int i = 0; i < 80; i++) {
			airDrift.step(DroneInput.idle(), 0.005);
			waterDrift.step(DroneInput.idle(), 0.005, wetEnvironment);
		}

		assertTrue(waterDrift.state().velocityMetersPerSecond().x()
				< airDrift.state().velocityMetersPerSecond().x() * 0.70);
	}

	@Test
	void precipitationWetnessAddsRotorLoadWithoutWaterImmersionDrag() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics dry = new DronePhysics(config);
		DronePhysics rainWet = new DronePhysics(config);
		DroneInput highLoad = new DroneInput(0.86, 0.0, 0.0, 0.0, true);
		DroneEnvironment rainEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.92
		);

		for (int i = 0; i < 500; i++) {
			dry.step(highLoad, 0.005);
			rainWet.step(highLoad, 0.005, rainEnvironment);
		}

		double dryThrust = averageRotorThrust(dry.state());
		double rainThrust = averageRotorThrust(rainWet.state());
		assertEquals(0.0, rainEnvironment.waterImmersionIntensity(), 1.0e-9);
		assertTrue(rainEnvironment.precipitationWetnessIntensity() > 0.90);
		assertEquals(1.0, dry.state().minRotorWetThrustScale(), 0.001);
		// ICAS 2020 heavy-rain CFD reports about 1.7-2.6% CT loss; the full-wet
		// gameplay wetness cap stays close while leaving water immersion severe.
		assertTrue(rainWet.state().averageRotorWetThrustScale() > 0.965);
		assertTrue(rainWet.state().averageRotorWetThrustScale() < 0.980);
		assertTrue(rainWet.state().minRotorWetThrustScale() > 0.960);
		assertTrue(rainWet.state().averageRotorAerodynamicLoadFactor()
				> dry.state().averageRotorAerodynamicLoadFactor() + 0.06);
		assertTrue(rainThrust < dryThrust * 0.99);
		assertTrue(rainThrust > dryThrust * 0.75);
		assertTrue(rainWet.state().rotorVibration() > dry.state().rotorVibration() + 0.01,
				() -> "rainVibration=" + rainWet.state().rotorVibration()
						+ " dryVibration=" + dry.state().rotorVibration());
		assertTrue(
				rainWet.state().maxEscDesyncIntensity() > dry.state().maxEscDesyncIntensity() + 0.001,
				() -> "rain desync=" + rainWet.state().maxEscDesyncIntensity() + " dry desync=" + dry.state().maxEscDesyncIntensity()
		);

		DronePhysics airDrift = new DronePhysics(config);
		DronePhysics rainDrift = new DronePhysics(config);
		airDrift.state().setVelocityMetersPerSecond(new Vec3(8.0, 0.0, 0.0));
		rainDrift.state().setVelocityMetersPerSecond(new Vec3(8.0, 0.0, 0.0));

		for (int i = 0; i < 80; i++) {
			airDrift.step(DroneInput.idle(), 0.005);
			rainDrift.step(DroneInput.idle(), 0.005, rainEnvironment);
		}

		assertTrue(rainDrift.state().velocityMetersPerSecond().x()
				> airDrift.state().velocityMetersPerSecond().x() * 0.98);
	}

	@Test
	void precipitationWetnessBuildsThenDriesWithRotorSpin() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics wet = new DronePhysics(config);
		DroneInput highLoad = new DroneInput(0.86, 0.0, 0.0, 0.0, true);
		DroneEnvironment heavyRain = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.92
		);

		wet.step(highLoad, 0.005, heavyRain);
		double firstFrameWetScale = wet.state().averageRotorWetThrustScale();
		assertTrue(firstFrameWetScale > 0.995,
				() -> "firstFrameWetScale=" + firstFrameWetScale);

		for (int i = 0; i < 499; i++) {
			wet.step(highLoad, 0.005, heavyRain);
		}

		double saturatedWetScale = wet.state().averageRotorWetThrustScale();
		assertTrue(saturatedWetScale > 0.965,
				() -> "saturatedWetScale=" + saturatedWetScale);
		assertTrue(saturatedWetScale < 0.980,
				() -> "saturatedWetScale=" + saturatedWetScale);

		for (int i = 0; i < 80; i++) {
			wet.step(highLoad, 0.005, DroneEnvironment.calm());
		}

		double earlyDryScale = wet.state().averageRotorWetThrustScale();
		assertTrue(earlyDryScale > saturatedWetScale + 0.006,
				() -> "earlyDryScale=" + earlyDryScale + " saturatedWetScale=" + saturatedWetScale);
		assertTrue(earlyDryScale < 0.995,
				() -> "earlyDryScale=" + earlyDryScale);

		for (int i = 0; i < 900; i++) {
			wet.step(highLoad, 0.005, DroneEnvironment.calm());
		}

		assertTrue(wet.state().averageRotorWetThrustScale() > 0.998,
				() -> "recoveredWetScale=" + wet.state().averageRotorWetThrustScale());
	}

	@Test
	void rotorIcingAccumulatesInFreezingWetSpinAndMeltsInWarmDryAir() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics freezingWet = new DronePhysics(config);
		DronePhysics warmWet = new DronePhysics(config);
		DroneInput highLoad = new DroneInput(0.86, 0.0, 0.0, 0.0, true);
		DroneEnvironment freezingRain = rainEnvironmentWithDensity(1.0, 1.0, -8.0);
		DroneEnvironment warmRain = rainEnvironmentWithDensity(1.0, 1.0, 6.0);

		for (int i = 0; i < 2500; i++) {
			freezingWet.step(highLoad, 0.010, freezingRain);
			warmWet.step(highLoad, 0.010, warmRain);
		}

		double frozenSeverity = freezingWet.state().maxRotorIcingSeverity();
		assertTrue(frozenSeverity > 0.10,
				() -> "frozenSeverity=" + frozenSeverity);
		assertTrue(warmWet.state().maxRotorIcingSeverity() < 1.0e-8,
				() -> "warmSeverity=" + warmWet.state().maxRotorIcingSeverity());
		assertTrue(freezingWet.state().minRotorIcingThrustScale() < 0.985,
				() -> "icingThrustScale=" + freezingWet.state().minRotorIcingThrustScale());
		assertTrue(freezingWet.state().maxRotorIcingPowerScale() > 1.05,
				() -> "icingPowerScale=" + freezingWet.state().maxRotorIcingPowerScale());
		assertEquals(1.0, warmWet.state().maxRotorIcingPowerScale(), 1.0e-12);
		double icingVibration = DronePhysics.rotorIcingVibration(
				config.rotors().get(0),
				freezingWet.state().motorOmegaRadiansPerSecond(0),
				freezingWet.state().rotorIcingSeverity(0)
		);
		assertTrue(icingVibration > 0.015,
				() -> "icingVibration=" + icingVibration);
		assertEquals(0.0, DronePhysics.rotorIcingVibration(
				config.rotors().get(0),
				warmWet.state().motorOmegaRadiansPerSecond(0),
				warmWet.state().rotorIcingSeverity(0)
		), 1.0e-12);

		DroneEnvironment warmDry = rainEnvironmentWithDensity(1.0, 0.0, 6.0);
		for (int i = 0; i < 900; i++) {
			freezingWet.step(highLoad, 0.010, warmDry);
		}

		assertTrue(freezingWet.state().maxRotorIcingSeverity() < frozenSeverity * 0.30,
				() -> "recoveredSeverity=" + freezingWet.state().maxRotorIcingSeverity()
						+ " frozenSeverity=" + frozenSeverity);
		assertTrue(freezingWet.state().minRotorIcingThrustScale() > 0.990,
				() -> "recoveredIcingThrustScale=" + freezingWet.state().minRotorIcingThrustScale());
	}

	@Test
	void wetPropFilmDriesSlowerWhenRotorsStop() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics spinningDry = new DronePhysics(config);
		DronePhysics stoppedDry = new DronePhysics(config);
		DroneInput highLoad = new DroneInput(0.86, 0.0, 0.0, 0.0, true);
		DroneEnvironment heavyRain = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.92
		);

		for (int i = 0; i < 500; i++) {
			spinningDry.step(highLoad, 0.005, heavyRain);
			stoppedDry.step(highLoad, 0.005, heavyRain);
		}

		double saturatedWetScale = spinningDry.state().averageRotorWetThrustScale();
		assertEquals(saturatedWetScale, stoppedDry.state().averageRotorWetThrustScale(), 0.001);

		for (int i = 0; i < 160; i++) {
			spinningDry.step(highLoad, 0.005, DroneEnvironment.calm());
			stoppedDry.step(DroneInput.idle(), 0.005, DroneEnvironment.calm());
		}

		double spinDryScale = spinningDry.state().averageRotorWetThrustScale();
		double stoppedScale = stoppedDry.state().averageRotorWetThrustScale();
		assertTrue(spinDryScale > stoppedScale + 0.003,
				() -> "spinDryScale=" + spinDryScale + " stoppedScale=" + stoppedScale);
		assertTrue(stoppedScale < 0.993,
				() -> "stoppedScale=" + stoppedScale);
	}

	@Test
	void moistAirDensityChangesRainRotorAuthority() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		double wetness = 1.0;
		double hotAirCelsius = 45.0;
		double densityCompensation = 1.0 / DroneEnvironment.moistAirDensityMultiplier(hotAirCelsius, wetness);
		DroneEnvironment hotRain = rainEnvironmentWithDensity(1.0, wetness, hotAirCelsius);
		DroneEnvironment densityCompensatedHotRain = rainEnvironmentWithDensity(densityCompensation, wetness, hotAirCelsius);
		DronePhysics uncorrected = new DronePhysics(config);
		DronePhysics compensated = new DronePhysics(config);
		DroneInput highLoad = new DroneInput(0.78, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 220; i++) {
			uncorrected.step(highLoad, 0.005, hotRain);
			compensated.step(highLoad, 0.005, densityCompensatedHotRain);
		}

		double uncorrectedThrust = averageRotorThrust(uncorrected.state());
		double compensatedThrust = averageRotorThrust(compensated.state());
		assertTrue(hotRain.effectiveAirDensityRatio() < hotRain.airDensityRatio());
		assertEquals(1.0, densityCompensatedHotRain.effectiveAirDensityRatio(), 0.002);
		assertTrue(
				compensatedThrust > uncorrectedThrust,
				() -> "compensated thrust=" + compensatedThrust + " uncorrected thrust=" + uncorrectedThrust
		);
	}

	@Test
	void ambientTemperatureChangesBatterySagAndThermalEquilibrium() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig thermalConfig = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.024, 3.0, 55.0)
				.withMotorThermal(55.0, 0.035, 160.0, 220.0);
		DronePhysics cold = new DronePhysics(thermalConfig);
		DronePhysics hot = new DronePhysics(thermalConfig);
		DroneInput highLoad = new DroneInput(0.82, 0.0, 0.0, 0.0, true);
		DroneEnvironment coldEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				-15.0
		);
		DroneEnvironment hotEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				45.0
		);

		for (int i = 0; i < 600; i++) {
			cold.step(highLoad, 0.005, coldEnvironment);
			hot.step(highLoad, 0.005, hotEnvironment);
		}

		assertEquals(-15.0, coldEnvironment.ambientTemperatureCelsius(), 1.0e-12);
		assertEquals(45.0, hotEnvironment.ambientTemperatureCelsius(), 1.0e-12);
		assertTrue(cold.state().batteryOhmicSagVoltage() > hot.state().batteryOhmicSagVoltage() + 0.20);
		assertTrue(cold.state().batteryEffectiveResistanceOhms() > hot.state().batteryEffectiveResistanceOhms());
		assertTrue(hot.state().batteryTemperatureCelsius() > cold.state().batteryTemperatureCelsius() + 45.0);
		assertTrue(hot.state().batteryCoolingFactor() >= 0.20);
		assertTrue(hot.state().averageMotorTemperatureCelsius() > cold.state().averageMotorTemperatureCelsius() + 8.0);
		assertTrue(hot.state().averageEscTemperatureCelsius() > cold.state().averageEscTemperatureCelsius() + 8.0);

		DroneConfig currentLimitedConfig = thermalConfig.withBattery(16.8, 16.7, 0.024, 3.0, 22.0);
		DronePhysics coldCurrentLimit = new DronePhysics(currentLimitedConfig);
		DronePhysics hotCurrentLimit = new DronePhysics(currentLimitedConfig);
		DroneInput currentLimitPunch = new DroneInput(0.98, 0.0, 0.0, 0.0, true);
		for (int i = 0; i < 600; i++) {
			coldCurrentLimit.step(currentLimitPunch, 0.005, coldEnvironment);
			hotCurrentLimit.step(currentLimitPunch, 0.005, hotEnvironment);
		}

		double coldLimit = coldCurrentLimit.state().batteryCurrentLimit();
		double hotLimit = hotCurrentLimit.state().batteryCurrentLimit();
		assertTrue(coldLimit < hotLimit - 0.010,
				() -> "coldCurrentLimit=" + coldLimit
						+ " hotCurrentLimit=" + hotLimit
						+ " coldBatteryTemp=" + coldCurrentLimit.state().batteryTemperatureCelsius()
						+ " hotBatteryTemp=" + hotCurrentLimit.state().batteryTemperatureCelsius());
	}

	@Test
	void hotAmbientHeatSoaksColdBatteryPack() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.018, 2.0, 90.0)
				.withMotorThermal(0.0, 0.50, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneEnvironment coldEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				5.0
		);
		DroneEnvironment hotEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				55.0
		);

		physics.step(DroneInput.idle(), 0.005, coldEnvironment);
		double coldSoakedTemperature = physics.state().batteryTemperatureCelsius();
		for (int i = 0; i < 500; i++) {
			physics.step(DroneInput.idle(), 0.005, hotEnvironment);
		}

		assertEquals(5.0, coldSoakedTemperature, 1.0e-9);
		assertTrue(physics.state().batteryTemperatureCelsius() > coldSoakedTemperature + 6.0,
				() -> "coldSoaked=" + coldSoakedTemperature
						+ " heatSoaked=" + physics.state().batteryTemperatureCelsius());
		assertTrue(physics.state().batteryTemperatureCelsius() < hotEnvironment.ambientTemperatureCelsius());
	}

	@Test
	void restoredBatteryTransientStateSurvivesFirstEnvironmentStep() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 13.2, 0.022, 1.5, 90.0)
				.withMotorThermal(0.0, 0.50, 200.0, 240.0);
		DroneEnvironment coldEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				5.0
		);
		DronePhysics coldStart = new DronePhysics(config);
		DronePhysics restored = new DronePhysics(config);
		restored.restoreBatteryTransientState(0.42, 72.0, 1.35, 0.62);

		coldStart.step(DroneInput.idle(), 0.005, coldEnvironment);
		restored.step(DroneInput.idle(), 0.005, coldEnvironment);

		assertEquals(5.0, coldStart.state().batteryTemperatureCelsius(), 1.0e-9);
		assertTrue(restored.state().batteryTemperatureCelsius() > 71.0,
				() -> "restoredTemp=" + restored.state().batteryTemperatureCelsius());
		assertTrue(restored.state().batterySlowPolarizationVoltage() > 0.419,
				() -> "restoredPolarization=" + restored.state().batterySlowPolarizationVoltage());
		assertTrue(restored.state().batteryThermalLimit() < 0.80,
				() -> "restoredThermalLimit=" + restored.state().batteryThermalLimit());
		assertTrue(restored.state().batteryVoltage() < coldStart.state().batteryVoltage() - 0.25,
				() -> "coldStartVoltage=" + coldStart.state().batteryVoltage()
						+ " restoredVoltage=" + restored.state().batteryVoltage());
	}

	@Test
	void restoredPowertrainThermalStateRecomputesMotorEscLimits() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 13.2, 0.018, 1.5, 90.0)
				.withMotorThermal(0.0, 0.50, 95.0, 125.0);
		DroneEnvironment coldEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				5.0
		);
		DronePhysics coldStart = new DronePhysics(config);
		DronePhysics restored = new DronePhysics(config);
		restored.restorePowertrainThermalState(
				new double[] { 118.0, 112.0, 105.0, 100.0 },
				new double[] { 110.0, 106.0, 99.0, 94.0 },
				new double[] { 0.70, 0.80, 0.90, 1.00 },
				new double[] { 0.60, 0.70, 0.80, 0.90 }
		);

		assertEquals(118.0, restored.state().motorTemperatureCelsius(0), 1.0e-9);
		assertEquals(110.0, restored.state().escTemperatureCelsius(0), 1.0e-9);
		assertEquals(0.70, restored.state().motorCoolingFactor(0), 1.0e-9);
		assertEquals(0.60, restored.state().escCoolingFactor(0), 1.0e-9);
		assertTrue(restored.state().motorThermalLimit() < 0.60,
				() -> "motorThermalLimit=" + restored.state().motorThermalLimit());
		assertTrue(restored.state().escThermalLimit() < 0.70,
				() -> "escThermalLimit=" + restored.state().escThermalLimit());
		assertTrue(restored.state().motorWindingResistanceScale(0) > 1.30,
				() -> "windingScale=" + restored.state().motorWindingResistanceScale(0));

		coldStart.step(DroneInput.idle(), 0.005, coldEnvironment);
		restored.step(DroneInput.idle(), 0.005, coldEnvironment);

		assertTrue(coldStart.state().maxMotorTemperatureCelsius() < 26.0,
				() -> "coldStartMotorTemp=" + coldStart.state().maxMotorTemperatureCelsius());
		assertTrue(restored.state().maxMotorTemperatureCelsius() > 117.0,
				() -> "restoredMotorTemp=" + restored.state().maxMotorTemperatureCelsius());
		assertTrue(restored.state().maxEscTemperatureCelsius() > 109.0,
				() -> "restoredEscTemp=" + restored.state().maxEscTemperatureCelsius());
		assertTrue(restored.state().motorThermalLimit() < 0.62,
				() -> "restoredMotorLimit=" + restored.state().motorThermalLimit());
		assertTrue(restored.state().escThermalLimit() < 0.72,
				() -> "restoredEscLimit=" + restored.state().escThermalLimit());
	}

	@Test
	void restoredRotorDynamicStateSurvivesFirstPoweredStep() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.090)
				.withBattery(16.8, 16.7, 0.0, 10.0, 90.0);
		DronePhysics coldStart = new DronePhysics(config);
		DronePhysics restored = new DronePhysics(config);
		double[] motorOmega = new double[config.rotors().size()];
		double[] escOutput = new double[config.rotors().size()];
		double[] escElectricalOutput = new double[config.rotors().size()];
		double[] telemetryRpm = new double[config.rotors().size()];
		double[] telemetryValidity = new double[config.rotors().size()];
		double[] inducedVelocity = new double[config.rotors().size()];
		double[] inducedLagScale = new double[config.rotors().size()];
		double[] wakeVelocity = new double[config.rotors().size()];
		double[] wakeCarryover = new double[config.rotors().size()];
		double[] surfaceWetness = new double[config.rotors().size()];
		double[] icingSeverity = new double[config.rotors().size()];
		for (int i = 0; i < config.rotors().size(); i++) {
			double maxOmega = config.rotors().get(i).maxOmegaRadiansPerSecond();
			motorOmega[i] = maxOmega * (0.68 - 0.02 * i);
			escOutput[i] = 0.62 - 0.01 * i;
			escElectricalOutput[i] = escOutput[i] - 0.015;
			telemetryRpm[i] = motorOmega[i] * 60.0 / (Math.PI * 2.0);
			telemetryValidity[i] = 1.0;
			inducedVelocity[i] = 5.8 - 0.2 * i;
			inducedLagScale[i] = 0.82 + 0.02 * i;
			wakeVelocity[i] = 6.9 - 0.25 * i;
			wakeCarryover[i] = 0.42 - 0.04 * i;
			surfaceWetness[i] = 0.58 - 0.04 * i;
			icingSeverity[i] = Math.max(0.0, 0.62 - 0.12 * i);
		}
		restored.restoreRotorDynamicState(new DronePhysics.RotorDynamicState(
				motorOmega,
				escOutput,
				escElectricalOutput,
				telemetryRpm,
				telemetryValidity,
				inducedVelocity,
				inducedLagScale,
				wakeVelocity,
				wakeCarryover,
				surfaceWetness,
				icingSeverity,
				0.73,
				0.54,
				0.61,
				0.09,
				0.11
		));

		DronePhysics.RotorDynamicState snapshot = restored.rotorDynamicStateSnapshot();
		assertEquals(motorOmega[0], restored.state().motorOmegaRadiansPerSecond(0), 1.0e-9);
		assertEquals(escOutput[0], restored.state().escOutputCommand(0), 1.0e-9);
		assertEquals(escElectricalOutput[0], restored.state().escElectricalOutputCommand(0), 1.0e-9);
		assertEquals(0.015, restored.state().escElectricalOutputError(0), 1.0e-9);
		assertEquals(telemetryRpm[0], restored.state().motorRpmTelemetryRpm(0), 1.0e-9);
		assertEquals(inducedVelocity[0], restored.state().rotorInducedVelocityMetersPerSecond(0), 1.0e-9);
		assertEquals(inducedLagScale[0], restored.state().rotorInducedLagThrustScale(0), 1.0e-9);
		assertEquals(wakeVelocity[0], snapshot.rotorInducedWakeVelocityMetersPerSecond()[0], 1.0e-9);
		assertEquals(wakeCarryover[0], snapshot.rotorInducedWakeCarryoverIntensity()[0], 1.0e-9);
		assertEquals(surfaceWetness[0], snapshot.rotorSurfaceWetness()[0], 1.0e-9);
		assertEquals(icingSeverity[0], snapshot.rotorIcingSeverity()[0], 1.0e-9);
		assertTrue(restored.state().rotorWetThrustScale(0) < 0.985,
				() -> "restoredWetScale=" + restored.state().rotorWetThrustScale(0));
		assertEquals(icingSeverity[0], restored.state().rotorIcingSeverity(0), 1.0e-9);
		assertTrue(restored.state().rotorIcingThrustScale(0) < 0.89,
				() -> "restoredIcingThrustScale=" + restored.state().rotorIcingThrustScale(0));
		assertTrue(restored.state().rotorIcingPowerScale(0) > 1.30,
				() -> "restoredIcingPowerScale=" + restored.state().rotorIcingPowerScale(0));
		assertEquals(0.73, restored.state().propwashWakeIntensity(), 1.0e-9);
		assertEquals(0.61, restored.state().vortexRingStateIntensity(), 1.0e-9);

		DroneInput throttle = new DroneInput(0.62, 0.0, 0.0, 0.0, true);
		coldStart.step(throttle, 0.005, DroneEnvironment.calm());
		restored.step(throttle, 0.005, DroneEnvironment.calm());

		assertTrue(restored.state().averageMotorRpm() > coldStart.state().averageMotorRpm() + 12000.0,
				() -> "coldRpm=" + coldStart.state().averageMotorRpm()
						+ " restoredRpm=" + restored.state().averageMotorRpm());
		assertTrue(restored.state().maxRotorInducedVelocityMetersPerSecond() > 5.0,
				() -> "restoredInduced=" + restored.state().maxRotorInducedVelocityMetersPerSecond());
		assertTrue(restored.rotorDynamicStateSnapshot().rotorInducedWakeVelocityMetersPerSecond()[0] > 6.2,
				() -> "restoredWake=" + restored.rotorDynamicStateSnapshot().rotorInducedWakeVelocityMetersPerSecond()[0]);
		assertTrue(restored.state().propwashWakeIntensity() > 0.60,
				() -> "propwashWake=" + restored.state().propwashWakeIntensity());
		assertTrue(restored.state().vortexRingStateIntensity() > 0.52,
				() -> "vrs=" + restored.state().vortexRingStateIntensity());
	}

	@Test
	void recirculatedDirtyAirReducesBatteryCooling() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 3.0, 90.0)
				.withMotorThermal(0.0, 0.50, 200.0, 240.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics recirculated = new DronePhysics(config);
		DroneInput loaded = new DroneInput(0.56, 0.0, 0.0, 0.0, true);
		Vec3 crossflow = new Vec3(10.0, 0.0, 0.0);
		DroneEnvironment cleanAir = new DroneEnvironment(Vec3.ZERO, 1.0, Double.POSITIVE_INFINITY, 0.0);
		DroneEnvironment dirtyRecirculation = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				0.08,
				0.0,
				0.85,
				0.95,
				0.12
		);

		clean.step(DroneInput.idle(), 0.005, cleanAir);
		recirculated.step(DroneInput.idle(), 0.005, dirtyRecirculation);
		clean.state().setBatteryTemperatureCelsius(72.0);
		recirculated.state().setBatteryTemperatureCelsius(72.0);

		for (int i = 0; i < 260; i++) {
			clean.state().setOrientation(Quaternion.IDENTITY);
			recirculated.state().setOrientation(Quaternion.IDENTITY);
			clean.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			recirculated.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			clean.state().setVelocityMetersPerSecond(crossflow);
			recirculated.state().setVelocityMetersPerSecond(crossflow);
			clean.step(loaded, 0.005, cleanAir);
			recirculated.step(loaded, 0.005, dirtyRecirculation);
		}

		assertTrue(recirculated.state().batteryCoolingFactor() < clean.state().batteryCoolingFactor() * 0.82,
				() -> "cleanBatteryCooling=" + clean.state().batteryCoolingFactor()
						+ " recirculatedBatteryCooling=" + recirculated.state().batteryCoolingFactor());
		assertTrue(recirculated.state().batteryTemperatureCelsius() > clean.state().batteryTemperatureCelsius() + 1.0,
				() -> "cleanBatteryTemp=" + clean.state().batteryTemperatureCelsius()
						+ " recirculatedBatteryTemp=" + recirculated.state().batteryTemperatureCelsius());
	}

	@Test
	void hotBatteryPackRaisesResistanceAndThermallyLimitsPower() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.006)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.020, 1.0, 120.0)
				.withMotorThermal(120.0, 0.0, 200.0, 240.0);
		DronePhysics coolPack = new DronePhysics(config);
		DronePhysics hotPack = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.92, 0.0, 0.0, 0.0, true);
		DroneEnvironment coolEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				22.0
		);
		DroneEnvironment hotEnvironment = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				65.0
		);

		for (int i = 0; i < 220; i++) {
			coolPack.step(punch, 0.005, coolEnvironment);
			hotPack.step(punch, 0.005, hotEnvironment);
		}

		assertTrue(hotPack.state().batteryTemperatureCelsius() > 70.0);
		assertTrue(hotPack.state().batteryThermalLimit() < 0.90);
		assertTrue(hotPack.state().batteryPowerLimit() <= hotPack.state().batteryThermalLimit() + 1.0e-9);
		assertTrue(hotPack.state().batteryEffectiveResistanceOhms() > coolPack.state().batteryEffectiveResistanceOhms() * 1.05);
		double hotSagPerAmp = hotPack.state().batteryOhmicSagVoltage()
				/ Math.max(1.0, hotPack.state().batteryCurrentAmps());
		double coolSagPerAmp = coolPack.state().batteryOhmicSagVoltage()
				/ Math.max(1.0, coolPack.state().batteryCurrentAmps());
		assertTrue(hotSagPerAmp > coolSagPerAmp * 1.05,
				() -> "hotSagPerAmp=" + hotSagPerAmp
						+ " coolSagPerAmp=" + coolSagPerAmp
						+ " hotSag=" + hotPack.state().batteryOhmicSagVoltage()
						+ " coolSag=" + coolPack.state().batteryOhmicSagVoltage()
						+ " hotCurrent=" + hotPack.state().batteryCurrentAmps()
						+ " coolCurrent=" + coolPack.state().batteryCurrentAmps()
						+ " hotBatteryTemp=" + hotPack.state().batteryTemperatureCelsius()
						+ " coolBatteryTemp=" + coolPack.state().batteryTemperatureCelsius()
						+ " hotThermalLimit=" + hotPack.state().batteryThermalLimit());
	}

	@Test
	void perRotorWaterImmersionCreatesAsymmetricThrustAndTorque() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics wetCorner = new DronePhysics(config);
		DroneInput input = new DroneInput(0.62, 0.0, 0.0, 0.0, true);
		DroneEnvironment oneWetRotor = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				new double[] {0.82, 0.0, 0.0, 0.0},
				0.18
		);

		for (int i = 0; i < 160; i++) {
			wetCorner.step(input, 0.005, oneWetRotor);
		}

		assertEquals(0.82, oneWetRotor.maxRotorWaterImmersion(), 1.0e-9);
		assertTrue(wetCorner.state().rotorThrustNewtons(0)
				< wetCorner.state().rotorThrustNewtons(1) * 0.65);
		assertTrue(wetCorner.state().rotorAerodynamicLoadFactor(0)
				> wetCorner.state().rotorAerodynamicLoadFactor(1) + 0.30);
		assertTrue(wetCorner.state().escDesyncIntensity(0)
				> wetCorner.state().escDesyncIntensity(1) + 0.02);
		assertTrue(wetCorner.state().angularVelocityBodyRadiansPerSecond().length() > 0.08);
	}

	@Test
	void perRotorPrecipitationWetnessCreatesWetPropAsymmetry() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics wetCorner = new DronePhysics(config);
		DroneInput input = new DroneInput(0.62, 0.0, 0.0, 0.0, true);
		DroneEnvironment oneRainWetRotor = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				new double[] {0.92, 0.0, 0.0, 0.0},
				0.23,
				25.0
		);

		for (int i = 0; i < 180; i++) {
			wetCorner.step(input, 0.005, oneRainWetRotor);
		}

		assertEquals(0.92, oneRainWetRotor.maxRotorPrecipitationWetness(), 1.0e-9);
		assertTrue(wetCorner.state().rotorWetThrustScale(0)
				< wetCorner.state().rotorWetThrustScale(1) - 0.020,
				() -> "wet0=" + wetCorner.state().rotorWetThrustScale(0)
						+ " wet1=" + wetCorner.state().rotorWetThrustScale(1));
		assertTrue(wetCorner.state().rotorAerodynamicLoadFactor(0)
				> wetCorner.state().rotorAerodynamicLoadFactor(1) + 0.04,
				() -> "load0=" + wetCorner.state().rotorAerodynamicLoadFactor(0)
						+ " load1=" + wetCorner.state().rotorAerodynamicLoadFactor(1));
	}

	@Test
	void rotorSideFlowObstructionCreatesWallCushionForce() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics nearWall = new DronePhysics(config);
		DroneInput hover = new DroneInput(0.58, 0.0, 0.0, 0.0, true);
		DroneEnvironment cleanAir = DroneEnvironment.calm();
		DroneEnvironment rightWall = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {0.88, 0.88, 0.88, 0.88},
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 0.0, 0.0)
				}
		);

		for (int i = 0; i < 140; i++) {
			clean.step(hover, 0.005, cleanAir);
			nearWall.step(hover, 0.005, rightWall);
		}

		assertEquals(0.0, clean.state().rotorWallEffectForceBodyNewtons().length(), 1.0e-9);
		assertTrue(nearWall.state().rotorWallEffectForceBodyNewtons().x() < -0.07,
				() -> "wallForce=" + nearWall.state().rotorWallEffectForceBodyNewtons());
		assertTrue(nearWall.state().rotorWallEffectForceBodyNewtons().length() > 0.07,
				() -> "wallForce=" + nearWall.state().rotorWallEffectForceBodyNewtons());
	}

	@Test
	void rotorWallCushionForceBuildsAndReleasesWithPressureLag() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.05, 0.0, 0.0, 0.0, true);
		DroneEnvironment cleanAir = DroneEnvironment.calm();
		DroneEnvironment rightWall = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {0.88, 0.88, 0.88, 0.88},
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(1.0, 0.0, 0.0)
				}
		);

		for (int i = 0; i < 120; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005, cleanAir);
		}

		holdInStillAir(physics);
		physics.step(hover, 0.005, rightWall);
		Vec3 firstWallForce = physics.state().rotorWallEffectForceBodyNewtons();

		for (int i = 0; i < 110; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005, rightWall);
		}
		Vec3 settledWallForce = physics.state().rotorWallEffectForceBodyNewtons();

		holdInStillAir(physics);
		physics.step(hover, 0.005, cleanAir);
		Vec3 lingeringCleanForce = physics.state().rotorWallEffectForceBodyNewtons();

		for (int i = 0; i < 220; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005, cleanAir);
		}
		Vec3 recoveredCleanForce = physics.state().rotorWallEffectForceBodyNewtons();

		assertTrue(firstWallForce.x() < -0.02, () -> "firstWallForce=" + firstWallForce);
		assertTrue(settledWallForce.x() < firstWallForce.x() - 0.09,
				() -> "firstWallForce=" + firstWallForce + " settledWallForce=" + settledWallForce);
		assertTrue(lingeringCleanForce.x() < recoveredCleanForce.x() - 0.10,
				() -> "lingeringCleanForce=" + lingeringCleanForce
						+ " recoveredCleanForce=" + recoveredCleanForce);
		assertTrue(recoveredCleanForce.length() < 0.01,
				() -> "recoveredCleanForce=" + recoveredCleanForce);
	}

	@Test
	void transverseRotorFlowAddsLift() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO);
		DronePhysics noRelativeFlow = new DronePhysics(config);
		DronePhysics crossFlow = new DronePhysics(config);
		noRelativeFlow.state().setVelocityMetersPerSecond(new Vec3(12.0, 0.0, 0.0));
		crossFlow.state().setVelocityMetersPerSecond(new Vec3(12.0, 0.0, 0.0));
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment matchingWind = new DroneEnvironment(new Vec3(12.0, 0.0, 0.0), 1.0, Double.POSITIVE_INFINITY);

		for (int i = 0; i < 500; i++) {
			noRelativeFlow.state().setOrientation(Quaternion.IDENTITY);
			crossFlow.state().setOrientation(Quaternion.IDENTITY);
			noRelativeFlow.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			crossFlow.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			noRelativeFlow.state().setVelocityMetersPerSecond(new Vec3(12.0, 0.0, 0.0));
			crossFlow.state().setVelocityMetersPerSecond(new Vec3(12.0, 0.0, 0.0));
			noRelativeFlow.step(hover, 0.005, matchingWind);
			crossFlow.step(hover, 0.005);
		}

		assertEquals(0.0, noRelativeFlow.state().averageRotorTranslationalLiftIntensity(), 1.0e-9);
		assertTrue(crossFlow.state().averageRotorTranslationalLiftIntensity() > 0.30);
		assertTrue(crossFlow.state().averageRotorInducedVelocityMetersPerSecond()
				< noRelativeFlow.state().averageRotorInducedVelocityMetersPerSecond() * 0.95);
	}

	@Test
	void rotorTranslationalLiftBuildsAndClearsWithWakeLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		Vec3 crossflow = new Vec3(12.0, 0.0, 0.0);

		for (int i = 0; i < 120; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.step(hover, 0.005);
		}

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(crossflow);
		physics.step(hover, 0.005);
		double firstCrossflowLift = physics.state().averageRotorTranslationalLiftIntensity();

		for (int i = 0; i < 180; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(crossflow);
			physics.step(hover, 0.005);
		}
		double settledCrossflowLift = physics.state().averageRotorTranslationalLiftIntensity();

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
		physics.step(hover, 0.005);
		double lingeringStillLift = physics.state().averageRotorTranslationalLiftIntensity();

		for (int i = 0; i < 260; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.step(hover, 0.005);
		}
		double clearedStillLift = physics.state().averageRotorTranslationalLiftIntensity();

		assertTrue(firstCrossflowLift < settledCrossflowLift * 0.40,
				() -> "firstCrossflowLift=" + firstCrossflowLift
						+ " settledCrossflowLift=" + settledCrossflowLift);
		assertTrue(settledCrossflowLift > 0.30,
				() -> "settledCrossflowLift=" + settledCrossflowLift);
		assertTrue(lingeringStillLift > clearedStillLift + 0.12,
				() -> "lingeringStillLift=" + lingeringStillLift
						+ " clearedStillLift=" + clearedStillLift);
		assertTrue(clearedStillLift < 0.04, () -> "clearedStillLift=" + clearedStillLift);
	}

	@Test
	void rotorInflowSkewAddsHubMomentInTransverseFlow() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0);
		DronePhysics noRelativeFlow = new DronePhysics(config);
		DronePhysics crossFlow = new DronePhysics(config);
		Vec3 crosswind = new Vec3(14.0, 0.0, 0.0);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment matchingWind = new DroneEnvironment(crosswind, 1.0, Double.POSITIVE_INFINITY);

		for (int i = 0; i < 180; i++) {
			noRelativeFlow.state().setVelocityMetersPerSecond(crosswind);
			crossFlow.state().setVelocityMetersPerSecond(crosswind);
			noRelativeFlow.step(hover, 0.005, matchingWind);
			crossFlow.step(hover, 0.005);
		}

		assertEquals(0.0, noRelativeFlow.state().rotorInflowSkewIntensity(), 1.0e-9);
		assertTrue(crossFlow.state().rotorInflowSkewIntensity() > 0.25);
		assertTrue(crossFlow.state().rotorInflowSkewTorqueBodyNewtonMeters().length() > 0.008,
				() -> "skewTorque=" + crossFlow.state().rotorInflowSkewTorqueBodyNewtonMeters());
		assertTrue(crossFlow.state().angularVelocityBodyRadiansPerSecond().length()
				> noRelativeFlow.state().angularVelocityBodyRadiansPerSecond().length() + 0.04);
	}

	@Test
	void zeroThrottleAxialDescentWindmillsUnpoweredProps() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 0.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics stillAir = new DronePhysics(config);
		DronePhysics axialDescent = new DronePhysics(config);
		DroneInput cutThrottle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);
		Vec3 descentVelocity = new Vec3(0.0, -18.0, 0.0);

		for (int i = 0; i < 120; i++) {
			stillAir.state().setOrientation(Quaternion.IDENTITY);
			axialDescent.state().setOrientation(Quaternion.IDENTITY);
			stillAir.state().setVelocityMetersPerSecond(Vec3.ZERO);
			axialDescent.state().setVelocityMetersPerSecond(descentVelocity);
			stillAir.step(cutThrottle, 0.005);
			axialDescent.step(cutThrottle, 0.005);
		}

		assertTrue(axialDescent.state().averageMotorRpm() > stillAir.state().averageMotorRpm() + 900.0);
		assertTrue(axialDescent.state().rotorForceBodyNewtons(0).y() > stillAir.state().rotorForceBodyNewtons(0).y() + 0.12);
		assertTrue(axialDescent.state().averageRotorAerodynamicLoadFactor()
				> stillAir.state().averageRotorAerodynamicLoadFactor() + 0.03);
		assertTrue(axialDescent.state().rotorVibration() > stillAir.state().rotorVibration() + 0.004);
	}

	@Test
	void rotorAerodynamicLoadTracksCleanAndDirtyPropFlow() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics noRelativeFlow = new DronePhysics(config);
		DronePhysics crossFlow = new DronePhysics(config);
		DroneInput cleanCruise = new DroneInput(0.45, 0.0, 0.0, 0.0, true);
		Vec3 crosswind = new Vec3(55.0, 0.0, 0.0);
		DroneEnvironment matchingWind = new DroneEnvironment(crosswind, 1.0, Double.POSITIVE_INFINITY);

		for (int i = 0; i < 260; i++) {
			noRelativeFlow.state().setOrientation(Quaternion.IDENTITY);
			crossFlow.state().setOrientation(Quaternion.IDENTITY);
			noRelativeFlow.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			crossFlow.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			noRelativeFlow.state().setVelocityMetersPerSecond(crosswind);
			crossFlow.state().setVelocityMetersPerSecond(crosswind);
			noRelativeFlow.step(cleanCruise, 0.005, matchingWind);
			crossFlow.step(cleanCruise, 0.005);
		}

		assertTrue(crossFlow.state().averageRotorTranslationalLiftIntensity() > 0.35);
		assertTrue(crossFlow.state().averageRotorAerodynamicLoadFactor()
				> noRelativeFlow.state().averageRotorAerodynamicLoadFactor() + 0.03);
		assertTrue(crossFlow.state().averageMotorRpm() < noRelativeFlow.state().averageMotorRpm() * 0.70);

		DronePhysics cleanDescentReference = new DronePhysics(config);
		DronePhysics descending = new DronePhysics(config);
		DroneInput loadedDescent = new DroneInput(0.58, 0.0, 0.0, 0.0, true);
		Vec3 descentVelocity = new Vec3(0.0, -8.0, 0.0);
		for (int i = 0; i < 180; i++) {
			cleanDescentReference.state().setOrientation(Quaternion.IDENTITY);
			descending.state().setOrientation(Quaternion.IDENTITY);
			cleanDescentReference.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			descending.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			cleanDescentReference.state().setVelocityMetersPerSecond(Vec3.ZERO);
			descending.state().setVelocityMetersPerSecond(descentVelocity);
			cleanDescentReference.step(loadedDescent, 0.005);
			descending.step(loadedDescent, 0.005);
		}

		assertTrue(descending.state().averageRotorAerodynamicLoadFactor()
				> cleanDescentReference.state().averageRotorAerodynamicLoadFactor() + 0.12);
		assertTrue(descending.state().averageMotorRpm() < cleanDescentReference.state().averageMotorRpm() * 0.99);

		DronePhysics clean = new DronePhysics(config);
		DronePhysics obstructed = new DronePhysics(config);
		DroneInput loaded = new DroneInput(0.55, 0.0, 0.0, 0.0, true);
		DroneEnvironment mildObstruction = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {0.20, 0.0, 0.0, 0.0}
		);

		for (int i = 0; i < 220; i++) {
			clean.state().setVelocityMetersPerSecond(Vec3.ZERO);
			obstructed.state().setVelocityMetersPerSecond(Vec3.ZERO);
			clean.step(loaded, 0.005);
			obstructed.step(loaded, 0.005, mildObstruction);
		}

		assertTrue(obstructed.state().rotorAerodynamicLoadFactor(0) > clean.state().rotorAerodynamicLoadFactor(0) + 0.02,
				() -> "cleanLoad=" + clean.state().rotorAerodynamicLoadFactor(0)
						+ " obstructedLoad=" + obstructed.state().rotorAerodynamicLoadFactor(0));
		assertTrue(obstructed.state().motorCurrentAmps(0) > clean.state().motorCurrentAmps(0) + 0.25,
				() -> "cleanCurrent=" + clean.state().motorCurrentAmps(0)
						+ " obstructedCurrent=" + obstructed.state().motorCurrentAmps(0));
		assertTrue(
				obstructed.state().maxEscDesyncIntensity() < 0.05,
				() -> "obstructed desync=" + obstructed.state().maxEscDesyncIntensity()
		);
	}

	@Test
	void ambientDirtyAirRoughensRotorLoadAndBladePassRipple() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics dirty = new DronePhysics(config);
		DroneInput loaded = new DroneInput(0.55, 0.0, 0.0, 0.0, true);
		DroneEnvironment cleanAir = new DroneEnvironment(Vec3.ZERO, 1.0, 3.0, 0.0);
		DroneEnvironment turbulentAir = new DroneEnvironment(Vec3.ZERO, 1.0, 3.0, 1.0);

		for (int i = 0; i < 180; i++) {
			clean.state().setOrientation(Quaternion.IDENTITY);
			dirty.state().setOrientation(Quaternion.IDENTITY);
			clean.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			dirty.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			clean.state().setVelocityMetersPerSecond(Vec3.ZERO);
			dirty.state().setVelocityMetersPerSecond(Vec3.ZERO);
			clean.step(loaded, 0.005, cleanAir);
			dirty.step(loaded, 0.005, turbulentAir);
		}

		assertTrue(dirty.state().averageRotorAerodynamicLoadFactor()
				> clean.state().averageRotorAerodynamicLoadFactor() + 0.045);
		assertTrue(dirty.state().averageRotorBladePassRippleIntensity()
				> clean.state().averageRotorBladePassRippleIntensity() + 0.004);
		assertTrue(dirty.state().rotorVibration() > clean.state().rotorVibration() + 0.002);
	}

	@Test
	void rotorSurfaceScrapeAddsLoadVibrationAndMotorDrag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics scraping = new DronePhysics(config);
		DroneInput input = new DroneInput(0.62, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 160; i++) {
			clean.step(input, 0.005);
			scraping.step(input, 0.005);
		}

		scraping.state().addRotorSurfaceScrapeIntensity(0, 1.0);
		for (int i = 0; i < 8; i++) {
			clean.step(input, 0.005);
			scraping.step(input, 0.005);
		}

		assertTrue(scraping.state().rotorSurfaceScrapeIntensity(0) > 0.35);
		assertTrue(scraping.state().rotorAerodynamicLoadFactor(0) > clean.state().rotorAerodynamicLoadFactor(0) + 0.25);
		assertTrue(scraping.state().rotorVibration() > clean.state().rotorVibration() + 0.012);
		assertTrue(scraping.state().motorRpm(0) < clean.state().motorRpm(0) * 0.94);
		assertTrue(scraping.state().escDesyncIntensity(0) > clean.state().escDesyncIntensity(0) + 0.02);

		for (int i = 0; i < 90; i++) {
			scraping.step(input, 0.005);
		}

		assertTrue(scraping.state().rotorSurfaceScrapeIntensity(0) < 0.02);
	}

	@Test
	void rotorAerodynamicLoadModulatesYawReactionTorque() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig reactionConfig = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics cleanReaction = new DronePhysics(reactionConfig);
		DronePhysics loadedReaction = new DronePhysics(reactionConfig);
		DroneInput loaded = new DroneInput(0.65, 0.0, 0.0, 0.0, true);
		DroneEnvironment singleRotorObstruction = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {0.20, 0.0, 0.0, 0.0}
		);

		for (int i = 0; i < 260; i++) {
			cleanReaction.state().setOrientation(Quaternion.IDENTITY);
			loadedReaction.state().setOrientation(Quaternion.IDENTITY);
			cleanReaction.state().setVelocityMetersPerSecond(Vec3.ZERO);
			loadedReaction.state().setVelocityMetersPerSecond(Vec3.ZERO);
			cleanReaction.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			loadedReaction.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			cleanReaction.step(loaded, 0.005);
			loadedReaction.step(loaded, 0.005);
		}
		RotorSpec loadedRotor = loadedReaction.config().rotors().get(0);
		double cleanAerodynamicTorque = 0.0;
		double loadedAerodynamicTorque = 0.0;
		double cleanReactionTorqueWithoutRipple = 0.0;
		double loadedReactionTorqueWithoutRipple = 0.0;
		double cleanAerodynamicLoad = 0.0;
		double loadedAerodynamicLoad = 0.0;
		double loadedRotor0Thrust = 0.0;
		double loadedRotor1Thrust = 0.0;
		int samples = 32;
		for (int i = 0; i < samples; i++) {
			cleanReaction.state().setOrientation(Quaternion.IDENTITY);
			loadedReaction.state().setOrientation(Quaternion.IDENTITY);
			cleanReaction.state().setVelocityMetersPerSecond(Vec3.ZERO);
			loadedReaction.state().setVelocityMetersPerSecond(Vec3.ZERO);
			cleanReaction.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			loadedReaction.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			cleanReaction.step(loaded, 0.005);
			loadedReaction.step(loaded, 0.005, singleRotorObstruction);
			cleanAerodynamicTorque += cleanReaction.state().motorAerodynamicTorqueNewtonMeters(0);
			loadedAerodynamicTorque += loadedReaction.state().motorAerodynamicTorqueNewtonMeters(0);
			cleanReactionTorqueWithoutRipple += cleanReaction.state().rotorTorqueBodyNewtonMeters(0).y()
					- loadedRotor.spinDirection() * cleanReaction.state().motorTorqueRippleNewtonMeters(0);
			loadedReactionTorqueWithoutRipple += loadedReaction.state().rotorTorqueBodyNewtonMeters(0).y()
					- loadedRotor.spinDirection() * loadedReaction.state().motorTorqueRippleNewtonMeters(0);
			cleanAerodynamicLoad += cleanReaction.state().rotorAerodynamicLoadFactor(0);
			loadedAerodynamicLoad += loadedReaction.state().rotorAerodynamicLoadFactor(0);
			loadedRotor0Thrust += loadedReaction.state().rotorThrustNewtons(0);
			loadedRotor1Thrust += loadedReaction.state().rotorThrustNewtons(1);
		}
		cleanAerodynamicTorque /= samples;
		loadedAerodynamicTorque /= samples;
		cleanReactionTorqueWithoutRipple /= samples;
		loadedReactionTorqueWithoutRipple /= samples;
		cleanAerodynamicLoad /= samples;
		loadedAerodynamicLoad /= samples;
		loadedRotor0Thrust /= samples;
		loadedRotor1Thrust /= samples;
		double meanCleanAerodynamicTorque = cleanAerodynamicTorque;
		double meanLoadedAerodynamicTorque = loadedAerodynamicTorque;
		double meanCleanReactionTorqueWithoutRipple = cleanReactionTorqueWithoutRipple;
		double meanLoadedReactionTorqueWithoutRipple = loadedReactionTorqueWithoutRipple;

		assertTrue(loadedAerodynamicLoad > cleanAerodynamicLoad + 0.03);
		assertTrue(
				meanLoadedAerodynamicTorque > meanCleanAerodynamicTorque + 0.0005,
				() -> "cleanAerodynamicTorque=" + meanCleanAerodynamicTorque
						+ " loadedAerodynamicTorque=" + meanLoadedAerodynamicTorque
		);
		assertEquals(loadedRotor1Thrust, loadedRotor0Thrust, 0.15);
		assertTrue(
				Math.abs(meanLoadedReactionTorqueWithoutRipple) > Math.abs(meanCleanReactionTorqueWithoutRipple) + 0.001,
				() -> "cleanReactionTorqueWithoutRipple=" + meanCleanReactionTorqueWithoutRipple
						+ " loadedReactionTorqueWithoutRipple=" + meanLoadedReactionTorqueWithoutRipple
		);
		assertTrue(loadedReaction.state().maxEscDesyncIntensity() < 0.05);
	}

	@Test
	void rotorReactionTorqueFollowsFlappedDiskAxisInCrossflow() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig reactionConfig = directControl(DroneConfig.racingQuad())
				.withPitchGains(zeroGains)
				.withYawGains(zeroGains)
				.withRollGains(zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.20)
				.withRotorYawTorquePerThrustMeter(0.08)
				.withRotorInertiaKgMetersSquared(0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DroneConfig noReactionConfig = reactionConfig.withRotorYawTorquePerThrustMeter(0.0);
		DronePhysics reaction = new DronePhysics(reactionConfig);
		DronePhysics noReaction = new DronePhysics(noReactionConfig);
		DroneInput loaded = new DroneInput(0.60, 0.0, 0.0, 0.0, true);
		Vec3 crosswind = new Vec3(22.0, 0.0, 0.0);
		DroneEnvironment singleRotorObstruction = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				new double[] {1.0, 1.0, 1.0, 1.0},
				new double[] {0.45, 0.0, 0.0, 0.0}
		);

		for (int i = 0; i < 260; i++) {
			reaction.state().setOrientation(Quaternion.IDENTITY);
			noReaction.state().setOrientation(Quaternion.IDENTITY);
			reaction.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			noReaction.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			reaction.state().setVelocityMetersPerSecond(crosswind);
			noReaction.state().setVelocityMetersPerSecond(crosswind);
			reaction.step(loaded, 0.005, singleRotorObstruction);
			noReaction.step(loaded, 0.005, singleRotorObstruction);
		}

		double reactionPitch = Math.abs(reaction.state().angularVelocityBodyRadiansPerSecond().x()
				- noReaction.state().angularVelocityBodyRadiansPerSecond().x());
		double reactionYaw = Math.abs(reaction.state().angularVelocityBodyRadiansPerSecond().y()
				- noReaction.state().angularVelocityBodyRadiansPerSecond().y());

		double flappingForce = reaction.state().averageRotorFlappingForceNewtons();
		assertTrue(flappingForce > 0.05, "flappingForce=" + flappingForce);
		assertTrue(reactionYaw > 8.0e-4, "reactionYaw=" + reactionYaw);
		assertTrue(reactionPitch > 1.2e-4, "reactionPitch=" + reactionPitch);
	}

	@Test
	void rotorDiskDragSlowsCrossflowEvenWithoutBodyDrag() {
		DroneConfig noDiskDragConfig = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0);
		DroneConfig diskDragConfig = noDiskDragConfig.withRotorDiskDragCoefficient(0.0028);
		DronePhysics noDiskDrag = new DronePhysics(noDiskDragConfig);
		DronePhysics diskDrag = new DronePhysics(diskDragConfig);
		Vec3 initialVelocity = new Vec3(24.0, 0.0, 0.0);
		noDiskDrag.state().setVelocityMetersPerSecond(initialVelocity);
		diskDrag.state().setVelocityMetersPerSecond(initialVelocity);
		DroneInput hover = new DroneInput(noDiskDragConfig.hoverThrottle(), 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 500; i++) {
			noDiskDrag.step(hover, 0.005);
			diskDrag.step(hover, 0.005);
		}

		assertTrue(diskDrag.state().velocityMetersPerSecond().x() < noDiskDrag.state().velocityMetersPerSecond().x() - 2.0);
	}

	@Test
	void racingQuadFlappingScaleMatchesStarmacLowSpeedReference() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		Vec3 starmacFigureNineWind = new Vec3(3.4, 0.0, 0.0);

		for (int i = 0; i < 300; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(starmacFigureNineWind);
			physics.step(hover, 0.005);
		}

		double flappingTiltDegrees = Math.toDegrees(physics.state().averageRotorFlappingTiltRadians());
		assertEquals(0.039, physics.state().rotorAdvanceRatio(0), 0.004);
		assertTrue(flappingTiltDegrees > 0.85 && flappingTiltDegrees < 1.35,
				() -> "flappingTiltDegrees=" + flappingTiltDegrees);
		assertTrue(physics.state().averageRotorFlappingForceNewtons() > 0.040,
				() -> "flappingForce=" + physics.state().averageRotorFlappingForceNewtons());
	}

	@Test
	void rotorFlappingTiltsThrustAgainstCrossflow() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withCenterOfMassOffsetBodyMeters(new Vec3(0.0, 0.0, 0.045));
		DronePhysics noFlapping = new DronePhysics(base.withRotorFlappingCoefficient(0.0));
		DronePhysics flapping = new DronePhysics(base.withRotorFlappingCoefficient(0.16));
		Vec3 initialVelocity = new Vec3(16.0, 0.0, 0.0);
		noFlapping.state().setVelocityMetersPerSecond(initialVelocity);
		flapping.state().setVelocityMetersPerSecond(initialVelocity);
		DroneInput hover = new DroneInput(base.hoverThrottle(), 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 500; i++) {
			noFlapping.state().setOrientation(Quaternion.IDENTITY);
			flapping.state().setOrientation(Quaternion.IDENTITY);
			noFlapping.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			flapping.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			noFlapping.step(hover, 0.005);
			flapping.step(hover, 0.005);
		}

		assertTrue(flapping.state().averageRotorFlappingForceNewtons() > 0.018,
				() -> "flappingForce=" + flapping.state().averageRotorFlappingForceNewtons());
		assertTrue(Math.toDegrees(flapping.state().averageRotorFlappingTiltRadians()) > 2.7,
				() -> "flappingTiltDeg=" + Math.toDegrees(flapping.state().averageRotorFlappingTiltRadians()));
		assertTrue(flapping.state().rotorFlappingTorqueBodyNewtonMeters().length() > 0.002);
		assertEquals(0.0, noFlapping.state().rotorFlappingTorqueBodyNewtonMeters().length(), 1.0e-9);
		assertTrue(flapping.state().velocityMetersPerSecond().x() < noFlapping.state().velocityMetersPerSecond().x() - 0.20);
	}

	@Test
	void rotorFlappingTiltLagsCrossflowReversal() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRotorFlappingCoefficient(0.16);
		DronePhysics physics = new DronePhysics(base);
		DroneInput hover = new DroneInput(base.hoverThrottle(), 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 24; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(new Vec3(16.0, 0.0, 0.0));
			physics.step(hover, 0.005);
		}
		double forwardForceX = physics.state().rotorForceBodyNewtons(0).x();
		double settledForwardTilt = Math.toDegrees(physics.state().averageRotorFlappingTiltRadians());

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(new Vec3(-16.0, 0.0, 0.0));
		physics.step(hover, 0.005);
		double laggedReverseForceX = physics.state().rotorForceBodyNewtons(0).x();
		double laggedTilt = Math.toDegrees(physics.state().averageRotorFlappingTiltRadians());

		for (int i = 0; i < 60; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(new Vec3(-16.0, 0.0, 0.0));
			physics.step(hover, 0.005);
		}

		assertTrue(forwardForceX < -0.040, () -> "forwardForceX=" + forwardForceX);
		assertTrue(settledForwardTilt > 2.7, () -> "settledForwardTilt=" + settledForwardTilt);
		assertTrue(laggedReverseForceX < -0.025, () -> "laggedReverseForceX=" + laggedReverseForceX);
		assertTrue(laggedTilt < settledForwardTilt - 0.80,
				() -> "laggedTilt=" + laggedTilt + " settledForwardTilt=" + settledForwardTilt);
		assertTrue(physics.state().rotorForceBodyNewtons(0).x() > 0.040,
				() -> "recoveredForceX=" + physics.state().rotorForceBodyNewtons(0).x());
		assertTrue(Math.toDegrees(physics.state().averageRotorFlappingTiltRadians()) > 2.7,
				() -> "recoveredTilt=" + Math.toDegrees(physics.state().averageRotorFlappingTiltRadians()));
	}

	@Test
	void rotorBladeDissymmetryAppearsInFastDiskPlaneFlow() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorStallThrustLossCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics noRelativeFlow = new DronePhysics(base);
		DronePhysics crossFlow = new DronePhysics(base);
		Vec3 diskPlaneFlow = new Vec3(80.0, 0.0, 0.0);
		DroneInput hover = new DroneInput(base.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment matchingWind = new DroneEnvironment(diskPlaneFlow, 1.0, Double.POSITIVE_INFINITY);

		for (int i = 0; i < 180; i++) {
			noRelativeFlow.state().setVelocityMetersPerSecond(diskPlaneFlow);
			crossFlow.state().setVelocityMetersPerSecond(diskPlaneFlow);
			noRelativeFlow.step(hover, 0.005, matchingWind);
			crossFlow.step(hover, 0.005);
		}

		assertEquals(0.0, noRelativeFlow.state().averageRotorBladeDissymmetryIntensity(), 1.0e-9);
		assertEquals(0.0, noRelativeFlow.state().averageRotorReverseFlowInboardFraction(), 1.0e-9);
		assertTrue(crossFlow.state().averageRotorBladeDissymmetryIntensity() > 0.44);
		assertTrue(crossFlow.state().maxRotorBladeDissymmetryIntensity() > 0.45);
		assertTrue(crossFlow.state().averageRotorAdvanceRatio() > 0.70);
		assertTrue(crossFlow.state().averageRotorReverseFlowInboardFraction() > 0.70);
		assertEquals(
				Math.min(1.0, crossFlow.state().averageRotorAdvanceRatio()),
				crossFlow.state().averageRotorReverseFlowInboardFraction(),
				0.015
		);
		assertTrue(crossFlow.state().averageRotorAerodynamicLoadFactor()
				> noRelativeFlow.state().averageRotorAerodynamicLoadFactor() + 0.16);
		assertTrue(crossFlow.state().rotorVibration() > noRelativeFlow.state().rotorVibration() + 0.04);
		assertTrue(crossFlow.state().rotorThrustNewtons(0)
				< noRelativeFlow.state().rotorThrustNewtons(0) * 0.70);
	}

	@Test
	void rotorBladeDissymmetryBuildsAndClearsWithRotorPhaseLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorStallThrustLossCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		Vec3 diskPlaneFlow = new Vec3(60.0, 0.0, 0.0);

		for (int i = 0; i < 160; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.step(hover, 0.005);
		}

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(diskPlaneFlow);
		physics.step(hover, 0.005);
		double firstCrossflowDissymmetry = physics.state().averageRotorBladeDissymmetryIntensity();

		for (int i = 0; i < 180; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(diskPlaneFlow);
			physics.step(hover, 0.005);
		}
		double settledCrossflowDissymmetry = physics.state().averageRotorBladeDissymmetryIntensity();
		double settledVibration = physics.state().rotorVibration();

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
		physics.step(hover, 0.005);
		double lingeringStillDissymmetry = physics.state().averageRotorBladeDissymmetryIntensity();

		for (int i = 0; i < 180; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
			physics.step(hover, 0.005);
		}
		double clearedStillDissymmetry = physics.state().averageRotorBladeDissymmetryIntensity();

		assertTrue(firstCrossflowDissymmetry < settledCrossflowDissymmetry * 0.45,
				() -> "firstCrossflowDissymmetry=" + firstCrossflowDissymmetry
						+ " settledCrossflowDissymmetry=" + settledCrossflowDissymmetry);
		assertTrue(settledCrossflowDissymmetry > 0.50,
				() -> "settledCrossflowDissymmetry=" + settledCrossflowDissymmetry);
		assertTrue(settledVibration > 0.04, () -> "settledVibration=" + settledVibration);
		assertTrue(lingeringStillDissymmetry > clearedStillDissymmetry + 0.10,
				() -> "lingeringStillDissymmetry=" + lingeringStillDissymmetry
						+ " clearedStillDissymmetry=" + clearedStillDissymmetry);
		assertTrue(clearedStillDissymmetry < 0.04,
				() -> "clearedStillDissymmetry=" + clearedStillDissymmetry);
	}

	@Test
	void bodyYawRateCouplesIntoRotorAerodynamicBladeSpeed() {
		PidGains zeroGains = new PidGains(0.0, 0.0, 0.0, 1.0);
		DroneConfig config = withCommonGains(directControl(DroneConfig.racingQuad()), zeroGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withRotorStallThrustLossCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics noYaw = new DronePhysics(config);
		DronePhysics yawing = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.08, 0.0, 0.0, 0.0, true);
		Vec3 yawRate = new Vec3(0.0, Math.toRadians(1600.0), 0.0);
		double noYawPositiveThrust = 0.0;
		double noYawNegativeThrust = 0.0;
		double yawPositiveThrust = 0.0;
		double yawNegativeThrust = 0.0;
		double yawPositiveAdvance = 0.0;
		double yawNegativeAdvance = 0.0;
		int samples = 0;

		for (int i = 0; i < 260; i++) {
			noYaw.state().setOrientation(Quaternion.IDENTITY);
			yawing.state().setOrientation(Quaternion.IDENTITY);
			noYaw.state().setVelocityMetersPerSecond(Vec3.ZERO);
			yawing.state().setVelocityMetersPerSecond(Vec3.ZERO);
			noYaw.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			yawing.state().setAngularVelocityBodyRadiansPerSecond(yawRate);
			noYaw.step(hover, 0.005);
			yawing.step(hover, 0.005);

			if (i >= 120) {
				noYawPositiveThrust += averageRotorThrustForSpin(noYaw, 1);
				noYawNegativeThrust += averageRotorThrustForSpin(noYaw, -1);
				yawPositiveThrust += averageRotorThrustForSpin(yawing, 1);
				yawNegativeThrust += averageRotorThrustForSpin(yawing, -1);
				yawPositiveAdvance += averageRotorAdvanceForSpin(yawing, 1);
				yawNegativeAdvance += averageRotorAdvanceForSpin(yawing, -1);
				samples++;
			}
		}

		noYawPositiveThrust /= samples;
		noYawNegativeThrust /= samples;
		yawPositiveThrust /= samples;
		yawNegativeThrust /= samples;
		yawPositiveAdvance /= samples;
		yawNegativeAdvance /= samples;
		double observedYawPositiveThrust = yawPositiveThrust;
		double observedYawNegativeThrust = yawNegativeThrust;
		double observedYawPositiveAdvance = yawPositiveAdvance;
		double observedYawNegativeAdvance = yawNegativeAdvance;

		assertEquals(noYawPositiveThrust, noYawNegativeThrust, 0.04);
		assertTrue(observedYawPositiveThrust > observedYawNegativeThrust * 1.05,
				() -> "yawPositiveThrust=" + observedYawPositiveThrust
						+ " yawNegativeThrust=" + observedYawNegativeThrust);
		assertTrue(observedYawNegativeAdvance > observedYawPositiveAdvance + 0.001,
				() -> "yawPositiveAdvance=" + observedYawPositiveAdvance
						+ " yawNegativeAdvance=" + observedYawNegativeAdvance);
	}

	@Test
	void rotorForwardAdvanceRolloffMatchesUiucFiveInchCurve() throws ReflectiveOperationException {
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0);
		Method thrustScaleMethod = DronePhysics.class.getDeclaredMethod(
				"rotorForwardAdvanceThrustScale",
				RotorSpec.class,
				double.class
		);
		Method powerScaleMethod = DronePhysics.class.getDeclaredMethod(
				"rotorForwardAdvancePowerScale",
				RotorSpec.class,
				double.class
		);
		Method torquePerThrustScaleMethod = DronePhysics.class.getDeclaredMethod(
				"rotorForwardAdvanceTorquePerThrustScale",
				RotorSpec.class,
				double.class
		);
		thrustScaleMethod.setAccessible(true);
		powerScaleMethod.setAccessible(true);
		torquePerThrustScaleMethod.setAccessible(true);

		double j025Scale = (double) thrustScaleMethod.invoke(null, rotor, 0.25 / Math.PI);
		double j045Scale = (double) thrustScaleMethod.invoke(null, rotor, 0.45 / Math.PI);
		double j065Scale = (double) thrustScaleMethod.invoke(null, rotor, 0.65 / Math.PI);
		double j025PowerScale = (double) powerScaleMethod.invoke(null, rotor, 0.25 / Math.PI);
		double j045PowerScale = (double) powerScaleMethod.invoke(null, rotor, 0.45 / Math.PI);
		double j065PowerScale = (double) powerScaleMethod.invoke(null, rotor, 0.65 / Math.PI);
		double j045TorquePerThrust = (double) torquePerThrustScaleMethod.invoke(null, rotor, 0.45 / Math.PI);
		double j065TorquePerThrust = (double) torquePerThrustScaleMethod.invoke(null, rotor, 0.65 / Math.PI);

		assertEquals(0.905, j025Scale, 0.055);
		assertEquals(0.549, j045Scale, 0.050);
		assertEquals(0.193, j065Scale, 0.090);
		assertEquals(0.968, j025PowerScale, 0.035);
		assertEquals(0.688, j045PowerScale, 0.045);
		assertEquals(0.409, j065PowerScale, 0.060);
		assertTrue(j025Scale > j045Scale + 0.25);
		assertTrue(j045Scale > j065Scale + 0.25);
		assertTrue(j045PowerScale > j045Scale + 0.08);
		assertEquals(1.254, j045TorquePerThrust, 0.14);
		assertTrue(j065TorquePerThrust > j045TorquePerThrust + 0.70);
	}

	@Test
	void rotorForwardAdvancePostPeakWashoutUsesUiucAdvanceRatio() throws ReflectiveOperationException {
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0);
		Method postPeakLossMethod = DronePhysics.class.getDeclaredMethod(
				"rotorForwardAdvancePostPeakThrustLoss",
				RotorSpec.class,
				double.class
		);
		postPeakLossMethod.setAccessible(true);

		double j045Loss = (double) postPeakLossMethod.invoke(null, rotor, 0.45 / Math.PI);
		double j065Loss = (double) postPeakLossMethod.invoke(null, rotor, 0.65 / Math.PI);
		double j105Loss = (double) postPeakLossMethod.invoke(null, rotor, 1.05 / Math.PI);
		double j135Loss = (double) postPeakLossMethod.invoke(null, rotor, 1.35 / Math.PI);

		assertEquals(0.0, j045Loss, 1.0e-9);
		assertEquals(0.0, j065Loss, 1.0e-9);
		assertTrue(j105Loss > 0.02 && j105Loss < 0.06, () -> "j105Loss=" + j105Loss);
		assertEquals(0.20, j135Loss, 1.0e-9);
	}

	@Test
	void axialHoverGustThrustScaleMatchesIcasDirectionality() throws ReflectiveOperationException {
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0);
		Method airflowScaleMethod = DronePhysics.class.getDeclaredMethod(
				"rotorAirflowThrustMultiplier",
				RotorSpec.class,
				Vec3.class,
				double.class,
				double.class
		);
		airflowScaleMethod.setAccessible(true);

		double omega6528Rpm = 6528.0 * Math.PI * 2.0 / 60.0;
		Vec3 axis = rotor.thrustAxisBody();
		double stillScale = (double) airflowScaleMethod.invoke(null, rotor, Vec3.ZERO, omega6528Rpm, 0.0);
		double adverseAxialGustScale = (double) airflowScaleMethod.invoke(
				null,
				rotor,
				axis.multiply(10.0),
				omega6528Rpm,
				0.0
		);
		double assistingAxialGustScale = (double) airflowScaleMethod.invoke(
				null,
				rotor,
				axis.multiply(-10.0),
				omega6528Rpm,
				0.0
		);

		assertEquals(1.0, stillScale, 1.0e-9);
		assertTrue(adverseAxialGustScale > 0.25 && adverseAxialGustScale < 0.48,
				() -> "adverseAxialGustScale=" + adverseAxialGustScale);
		assertTrue(assistingAxialGustScale > 1.35 && assistingAxialGustScale < 1.75,
				() -> "assistingAxialGustScale=" + assistingAxialGustScale);
		assertTrue(assistingAxialGustScale > adverseAxialGustScale + 0.95,
				() -> "adverseAxialGustScale=" + adverseAxialGustScale
						+ " assistingAxialGustScale=" + assistingAxialGustScale);
	}

	@Test
	void axialHoverGustModelFadesOutInCrossflow() throws ReflectiveOperationException {
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0);
		Method axialGustScaleMethod = DronePhysics.class.getDeclaredMethod(
				"rotorAxialGustThrustScale",
				RotorSpec.class,
				Vec3.class,
				double.class,
				double.class,
				double.class
		);
		axialGustScaleMethod.setAccessible(true);

		double omega6528Rpm = 6528.0 * Math.PI * 2.0 / 60.0;
		double tipSpeed = Math.abs(omega6528Rpm) * rotor.radiusMeters();
		Vec3 axis = rotor.thrustAxisBody();
		double pureAxialScale = (double) axialGustScaleMethod.invoke(
				null,
				rotor,
				axis.multiply(-10.0),
				omega6528Rpm,
				tipSpeed,
				0.0
		);
		double mixedCrossflowScale = (double) axialGustScaleMethod.invoke(
				null,
				rotor,
				new Vec3(20.0, -10.0, 0.0),
				omega6528Rpm,
				tipSpeed,
				20.0
		);

		assertTrue(pureAxialScale > 1.65, () -> "pureAxialScale=" + pureAxialScale);
		assertEquals(1.0, mixedCrossflowScale, 1.0e-9);
	}

	@Test
	void forwardFlightPropellerAdvanceRolloffBeatsTranslationalLiftBoost() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorStallThrustLossCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics hover = new DronePhysics(config);
		DronePhysics forward = new DronePhysics(config);
		DroneInput input = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		Vec3 forwardVelocity = new Vec3(0.0, 0.0, 12.5);
		double hoverThrustRatio = 0.0;
		double forwardThrustRatio = 0.0;
		double hoverLoad = 0.0;
		double forwardLoad = 0.0;
		double hoverTorque = 0.0;
		double forwardTorque = 0.0;
		double forwardPropellerThrustScale = 0.0;
		double forwardPropellerPowerScale = 0.0;
		double forwardAdvance = 0.0;
		double forwardTranslationalLift = 0.0;
		int samples = 0;

		for (int i = 0; i < 320; i++) {
			holdInStillAir(hover);
			holdInCruise(forward, forwardVelocity);
			hover.step(input, 0.005);
			forward.step(input, 0.005);

			if (i >= 180) {
				hoverThrustRatio += rotorEffectiveThrustRatio(hover, 0);
				forwardThrustRatio += rotorEffectiveThrustRatio(forward, 0);
				hoverLoad += hover.state().rotorAerodynamicLoadFactor(0);
				forwardLoad += forward.state().rotorAerodynamicLoadFactor(0);
				hoverTorque += hover.state().motorAerodynamicTorqueNewtonMeters(0);
				forwardTorque += forward.state().motorAerodynamicTorqueNewtonMeters(0);
				forwardPropellerThrustScale += forward.state().rotorPropellerThrustScale(0);
				forwardPropellerPowerScale += forward.state().rotorPropellerPowerScale(0);
				forwardAdvance += forward.state().rotorAdvanceRatio(0);
				forwardTranslationalLift += forward.state().rotorTranslationalLiftIntensity(0);
				samples++;
			}
		}

		hoverThrustRatio /= samples;
		forwardThrustRatio /= samples;
		hoverLoad /= samples;
		forwardLoad /= samples;
		hoverTorque /= samples;
		forwardTorque /= samples;
		forwardPropellerThrustScale /= samples;
		forwardPropellerPowerScale /= samples;
		forwardAdvance /= samples;
		forwardTranslationalLift /= samples;
		double observedHoverThrustRatio = hoverThrustRatio;
		double observedForwardThrustRatio = forwardThrustRatio;
		double observedHoverLoad = hoverLoad;
		double observedForwardLoad = forwardLoad;
		double observedHoverTorque = hoverTorque;
		double observedForwardTorque = forwardTorque;
		double observedForwardPropellerThrustScale = forwardPropellerThrustScale;
		double observedForwardPropellerPowerScale = forwardPropellerPowerScale;
		double observedForwardAdvance = forwardAdvance;
		double observedForwardTranslationalLift = forwardTranslationalLift;
		double observedForwardThrustRatioToHover = observedForwardThrustRatio / observedHoverThrustRatio;
		double observedForwardLoadRatioToHover = observedForwardLoad / observedHoverLoad;
		double observedForwardTorqueRatioToHover = observedForwardTorque / observedHoverTorque;
		double observedForwardTorquePerThrustProxy = observedForwardLoadRatioToHover / observedForwardThrustRatioToHover;

		assertTrue(observedForwardAdvance > 0.12 && observedForwardAdvance < 0.18,
				() -> "forwardAdvance=" + observedForwardAdvance);
		assertTrue(Math.PI * observedForwardAdvance > 0.43 && Math.PI * observedForwardAdvance < 0.48,
				() -> "uiucEquivalentJ=" + Math.PI * observedForwardAdvance);
		assertTrue(observedForwardTranslationalLift > 0.55,
				() -> "forwardTranslationalLift=" + observedForwardTranslationalLift);
		// UIUC 5-inch forward-flow fits near J=0.45 give CT/static about 0.55 and Q/T about 1.25.
		assertTrue(observedForwardPropellerThrustScale > 0.50 && observedForwardPropellerThrustScale < 0.63,
				() -> "forwardPropellerThrustScale=" + observedForwardPropellerThrustScale);
		assertTrue(observedForwardThrustRatioToHover > 0.50 && observedForwardThrustRatioToHover < 0.63,
				() -> "forwardThrustRatioToHover=" + observedForwardThrustRatioToHover);
		assertTrue(observedForwardPropellerPowerScale > 0.64 && observedForwardPropellerPowerScale < 0.74,
				() -> "forwardPropellerPowerScale=" + observedForwardPropellerPowerScale);
		assertTrue(observedForwardTorqueRatioToHover > observedForwardThrustRatioToHover + 0.08
						&& observedForwardTorqueRatioToHover < 0.84,
				() -> "forwardTorqueRatioToHover=" + observedForwardTorqueRatioToHover
						+ " forwardThrustRatioToHover=" + observedForwardThrustRatioToHover);
		assertTrue(observedForwardLoadRatioToHover > 0.62 && observedForwardLoadRatioToHover < 0.78,
				() -> "forwardLoadRatioToHover=" + observedForwardLoadRatioToHover);
		assertTrue(observedForwardTorquePerThrustProxy > 1.15 && observedForwardTorquePerThrustProxy < 1.36,
				() -> "forwardTorquePerThrustProxy=" + observedForwardTorquePerThrustProxy);
		assertTrue(observedForwardThrustRatio < observedHoverThrustRatio * 0.70,
				() -> "hoverThrustRatio=" + observedHoverThrustRatio
						+ " forwardThrustRatio=" + observedForwardThrustRatio);
		assertTrue(observedForwardLoad < observedHoverLoad * 0.80,
				() -> "hoverLoad=" + observedHoverLoad + " forwardLoad=" + observedForwardLoad);
	}

	@Test
	void rotorBladeStallReducesThrustAndAddsVibrationAtHighAdvanceRatio() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics noStallLoss = new DronePhysics(base.withRotorStallThrustLossCoefficient(0.0));
		DronePhysics stalled = new DronePhysics(base.withRotorStallThrustLossCoefficient(0.60));
		Vec3 highSlip = new Vec3(60.0, 0.0, 0.0);
		DroneInput hover = new DroneInput(base.hoverThrottle(), 0.0, 0.0, 0.0, true);

		noStallLoss.state().setVelocityMetersPerSecond(highSlip);
		stalled.state().setVelocityMetersPerSecond(highSlip);
		for (int i = 0; i < 140; i++) {
			noStallLoss.step(hover, 0.005);
			stalled.step(hover, 0.005);
			noStallLoss.state().setVelocityMetersPerSecond(highSlip);
			stalled.state().setVelocityMetersPerSecond(highSlip);
		}

		assertTrue(stalled.state().averageRotorStallIntensity() > 0.55);
		assertTrue(stalled.state().averageRotorAdvanceRatio() > 0.70);
		assertTrue(stalled.state().maxRotorAdvanceRatio() > 0.70);
		assertTrue(stalled.state().maxRotorReverseFlowInboardFraction() > 0.70);
		assertTrue(stalled.state().rotorThrustNewtons(0) < noStallLoss.state().rotorThrustNewtons(0) * 0.65);
		assertTrue(noStallLoss.state().rotorVibration() < 0.13);
		assertTrue(stalled.state().rotorVibration() > noStallLoss.state().rotorVibration() + 0.09);
	}

	@Test
	void rotorDynamicStallRecoversSlowlyAfterHighAdvanceFlowClears() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRotorStallThrustLossCoefficient(0.60);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics stalled = new DronePhysics(config);
		DroneInput input = new DroneInput(0.68, 0.0, 0.0, 0.0, true);
		Vec3 highSlip = new Vec3(60.0, 0.0, 0.0);

		for (int i = 0; i < 160; i++) {
			clean.state().setOrientation(Quaternion.IDENTITY);
			stalled.state().setOrientation(Quaternion.IDENTITY);
			clean.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			stalled.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			clean.state().setVelocityMetersPerSecond(Vec3.ZERO);
			stalled.state().setVelocityMetersPerSecond(highSlip);
			clean.step(input, 0.005);
			stalled.step(input, 0.005);
		}

		double cleanStall = clean.state().averageRotorStallIntensity();
		double highStall = stalled.state().averageRotorStallIntensity();
		stalled.state().setOrientation(Quaternion.IDENTITY);
		stalled.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		stalled.state().setVelocityMetersPerSecond(Vec3.ZERO);
		stalled.step(input, 0.005);
		double laggedRecoveryStall = stalled.state().averageRotorStallIntensity();

		for (int i = 0; i < 260; i++) {
			stalled.state().setOrientation(Quaternion.IDENTITY);
			stalled.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			stalled.state().setVelocityMetersPerSecond(Vec3.ZERO);
			stalled.step(input, 0.005);
		}

		assertTrue(cleanStall < 0.02);
		assertTrue(highStall > 0.55);
		assertTrue(laggedRecoveryStall > cleanStall + 0.35);
		assertTrue(laggedRecoveryStall > highStall * 0.85);
		assertTrue(stalled.state().averageRotorStallIntensity() < 0.04);
	}

	@Test
	void airframeAerodynamicMomentAppearsWithAngleOfAttackAndSideslip() {
		DroneConfig noAeroMomentConfig = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO);
		DroneConfig aeroMomentConfig = noAeroMomentConfig.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32));
		DronePhysics noAeroMoment = new DronePhysics(noAeroMomentConfig);
		DronePhysics aeroMoment = new DronePhysics(aeroMomentConfig);
		Vec3 slipVelocity = new Vec3(8.0, -2.5, 14.0);
		noAeroMoment.state().setVelocityMetersPerSecond(slipVelocity);
		aeroMoment.state().setVelocityMetersPerSecond(slipVelocity);

		noAeroMoment.step(DroneInput.idle(), 0.005);
		aeroMoment.step(DroneInput.idle(), 0.005);

		assertEquals(0.0, noAeroMoment.state().airframeAerodynamicTorqueBodyNewtonMeters().length(), 1.0e-9);
		assertTrue(aeroMoment.state().airframeAerodynamicTorqueBodyNewtonMeters().length() > 0.04);
		assertTrue(aeroMoment.state().angularVelocityBodyRadiansPerSecond().length()
				> noAeroMoment.state().angularVelocityBodyRadiansPerSecond().length() + 0.01);
	}

	@Test
	void centerOfPressureOffsetAddsAirframeForceMoment() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32))
				.withAngularDragCoefficient(0.0);
		DronePhysics centered = new DronePhysics(base);
		DronePhysics pressureHigh = new DronePhysics(base.withCenterOfPressureOffsetBodyMeters(new Vec3(0.0, 0.050, 0.0)));
		Vec3 forwardAirspeed = new Vec3(0.0, 0.0, 24.0);

		for (int i = 0; i < 90; i++) {
			holdInCruise(centered, forwardAirspeed);
			holdInCruise(pressureHigh, forwardAirspeed);
			centered.step(DroneInput.idle(), 0.005);
			pressureHigh.step(DroneInput.idle(), 0.005);
		}

		assertEquals(0.0, centered.state().airframePressureCenterTorqueBodyNewtonMeters().length(), 1.0e-9);
		assertTrue(pressureHigh.state().airframePressureCenterTorqueBodyNewtonMeters().x() < -0.20);
		assertEquals(
				pressureHigh.state().airframePressureCenterTorqueBodyNewtonMeters().x(),
				pressureHigh.state().airframeAerodynamicTorqueBodyNewtonMeters().x(),
				1.0e-9
		);
		assertTrue(pressureHigh.state().angularVelocityBodyRadiansPerSecond().x()
				< centered.state().angularVelocityBodyRadiansPerSecond().x() - 0.10);
	}

	@Test
	void sideslipWeathercockDampingOpposesYawRate() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32))
				.withAngularDragCoefficient(0.0)
				.withRotorDiskDragCoefficient(0.0);
		DronePhysics straight = new DronePhysics(config);
		DronePhysics slipped = new DronePhysics(config);
		Vec3 yawRate = new Vec3(0.0, 4.0, 0.0);

		straight.state().setOrientation(Quaternion.IDENTITY);
		slipped.state().setOrientation(Quaternion.IDENTITY);
		straight.state().setAngularVelocityBodyRadiansPerSecond(yawRate);
		slipped.state().setAngularVelocityBodyRadiansPerSecond(yawRate);
		straight.state().setVelocityMetersPerSecond(new Vec3(0.0, 0.0, 18.0));
		slipped.state().setVelocityMetersPerSecond(new Vec3(8.0, 0.0, 16.0));
		straight.step(DroneInput.idle(), 0.005);
		slipped.step(DroneInput.idle(), 0.005);

		double straightYawDamping = straight.state().airframeAngularDragTorqueBodyNewtonMeters().y();
		double slippedYawDamping = slipped.state().airframeAngularDragTorqueBodyNewtonMeters().y();
		assertTrue(straightYawDamping < 0.0, () -> "straightYawDamping=" + straightYawDamping);
		assertTrue(slippedYawDamping < straightYawDamping - 0.015,
				() -> "straightYawDamping=" + straightYawDamping + " slippedYawDamping=" + slippedYawDamping);
	}

	@Test
	void airframePressureCenterMigratesWithAngleOfAttackAndSideslip() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32))
				.withAngularDragCoefficient(0.0);
		DronePhysics straight = new DronePhysics(config);
		DronePhysics slipped = new DronePhysics(config);
		Vec3 straightVelocity = new Vec3(0.0, 0.0, 18.0);
		Vec3 slipVelocity = new Vec3(6.0, 5.0, 14.0);

		for (int i = 0; i < 90; i++) {
			holdInCruise(straight, straightVelocity);
			holdInCruise(slipped, slipVelocity);
			straight.step(DroneInput.idle(), 0.005);
			slipped.step(DroneInput.idle(), 0.005);
		}

		Vec3 migratedTorque = slipped.state().airframePressureCenterTorqueBodyNewtonMeters();
		assertEquals(0.0, straight.state().airframePressureCenterTorqueBodyNewtonMeters().length(), 1.0e-9);
		assertTrue(migratedTorque.x() < -0.035, () -> "migratedTorque=" + migratedTorque);
		assertTrue(Math.abs(migratedTorque.y()) > 0.015, () -> "migratedTorque=" + migratedTorque);
		assertTrue(migratedTorque.z() > 0.010, () -> "migratedTorque=" + migratedTorque);
	}

	@Test
	void dynamicPressureCenterMigrationBuildsAndRecoversWithLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32))
				.withAngularDragCoefficient(0.0);
		DronePhysics physics = new DronePhysics(config);
		Vec3 straightVelocity = new Vec3(0.0, 0.0, 18.0);
		Vec3 slipVelocity = new Vec3(6.0, 5.0, 14.0);

		holdInCruise(physics, slipVelocity);
		physics.step(DroneInput.idle(), 0.005);
		Vec3 firstSlipTorque = physics.state().airframePressureCenterTorqueBodyNewtonMeters();

		for (int i = 0; i < 90; i++) {
			holdInCruise(physics, slipVelocity);
			physics.step(DroneInput.idle(), 0.005);
		}
		Vec3 settledSlipTorque = physics.state().airframePressureCenterTorqueBodyNewtonMeters();

		holdInCruise(physics, straightVelocity);
		physics.step(DroneInput.idle(), 0.005);
		Vec3 lingeringStraightTorque = physics.state().airframePressureCenterTorqueBodyNewtonMeters();

		for (int i = 0; i < 260; i++) {
			holdInCruise(physics, straightVelocity);
			physics.step(DroneInput.idle(), 0.005);
		}
		Vec3 recoveredStraightTorque = physics.state().airframePressureCenterTorqueBodyNewtonMeters();

		assertTrue(settledSlipTorque.length() > firstSlipTorque.length() * 4.0,
				() -> "firstSlipTorque=" + firstSlipTorque + " settledSlipTorque=" + settledSlipTorque);
		assertTrue(lingeringStraightTorque.length() > recoveredStraightTorque.length() + 0.03,
				() -> "lingeringStraightTorque=" + lingeringStraightTorque + " recoveredStraightTorque=" + recoveredStraightTorque);
		assertTrue(recoveredStraightTorque.length() < 0.002,
				() -> "recoveredStraightTorque=" + recoveredStraightTorque);
	}

	@Test
	void rotorOutwardCantTiltsThrustAxesAndReducesVerticalLift() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0);
		DroneConfig cantedConfig = base.withRotorOutwardCantDegrees(12.0);

		assertEquals(12.0, cantedConfig.averageRotorOutwardCantDegrees(), 1.0e-9);
		assertTrue(cantedConfig.hoverThrottle() > base.hoverThrottle() * 1.015);
		for (RotorSpec rotor : cantedConfig.rotors()) {
			Vec3 radial = new Vec3(rotor.positionBodyMeters().x(), 0.0, rotor.positionBodyMeters().z()).normalized();
			assertEquals(1.0, rotor.thrustAxisBody().length(), 1.0e-9);
			assertTrue(rotor.thrustAxisBody().dot(radial) > 0.20);
			assertTrue(rotor.thrustAxisBody().y() < 0.99);
		}

		DronePhysics flat = new DronePhysics(base);
		DronePhysics canted = new DronePhysics(cantedConfig);
		DroneInput climb = new DroneInput(base.hoverThrottle() + 0.06, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 220; i++) {
			flat.step(climb, 0.0025);
			canted.step(climb, 0.0025);
		}

		assertTrue(canted.state().velocityMetersPerSecond().y()
				< flat.state().velocityMetersPerSecond().y() - 0.04);
		Vec3 cantedForce = canted.state().rotorForceBodyNewtons(0);
		Vec3 cantedTorque = canted.state().rotorTorqueBodyNewtonMeters(0);
		Vec3 radial = new Vec3(
				cantedConfig.rotors().get(0).positionBodyMeters().x(),
				0.0,
				cantedConfig.rotors().get(0).positionBodyMeters().z()
		).normalized();
		assertTrue(cantedForce.dot(radial) > 0.15);
		assertTrue(cantedForce.y() > 0.0);
		assertTrue(cantedTorque.length() > 0.001);
	}

	@Test
	void cantedRotorAirflowUsesRotorAxisForAdvanceRatio() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withRotorOutwardCantDegrees(25.0);
		RotorSpec referenceRotor = config.rotors().get(0);
		Vec3 axis = referenceRotor.thrustAxisBody();
		Vec3 reference = new Vec3(1.0, 0.0, 0.0);
		Vec3 transverse = reference.subtract(axis.multiply(reference.dot(axis))).normalized();
		if (transverse.lengthSquared() <= 1.0e-9) {
			transverse = new Vec3(0.0, 0.0, 1.0).subtract(axis.multiply(axis.z())).normalized();
		}

		DronePhysics axialFlow = new DronePhysics(config);
		DronePhysics transverseFlow = new DronePhysics(config);
		DroneInput input = new DroneInput(config.hoverThrottle() + 0.10, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 140; i++) {
			axialFlow.state().setOrientation(Quaternion.IDENTITY);
			axialFlow.state().setVelocityMetersPerSecond(axis.multiply(-16.0));
			transverseFlow.state().setOrientation(Quaternion.IDENTITY);
			transverseFlow.state().setVelocityMetersPerSecond(transverse.multiply(16.0));
			axialFlow.step(input, 0.0025);
			transverseFlow.step(input, 0.0025);
		}

		assertTrue(axialFlow.state().rotorAdvanceRatio(0) < 0.035);
		assertTrue(transverseFlow.state().rotorAdvanceRatio(0)
				> axialFlow.state().rotorAdvanceRatio(0) + 0.08);
		assertEquals(
				Math.PI * axialFlow.state().rotorAdvanceRatio(0),
				axialFlow.state().rotorPropellerAdvanceRatioJ(0),
				1.0e-6
		);
		assertEquals(
				Math.PI * transverseFlow.state().rotorAdvanceRatio(0),
				transverseFlow.state().rotorPropellerAdvanceRatioJ(0),
				1.0e-6
		);
		assertEquals(
				Math.min(1.0, axialFlow.state().rotorAdvanceRatio(0)),
				axialFlow.state().rotorReverseFlowInboardFraction(0),
				0.010
		);
		assertEquals(
				Math.min(1.0, transverseFlow.state().rotorAdvanceRatio(0)),
				transverseFlow.state().rotorReverseFlowInboardFraction(0),
				0.010
		);
		assertTrue(transverseFlow.state().rotorTranslationalLiftIntensity(0)
				> axialFlow.state().rotorTranslationalLiftIntensity(0) + 0.10);
		assertTrue(axialFlow.state().rotorStallIntensity(0) > transverseFlow.state().rotorStallIntensity(0));
	}

	@Test
	void highAdvanceBladeStallAddsLowFrequencyBuffeting() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics highAdvance = new DronePhysics(config);
		DroneInput input = new DroneInput(0.68, 0.0, 0.0, 0.0, true);
		Vec3 crosswind = new Vec3(60.0, 0.0, 0.0);
		DroneEnvironment matchingWind = new DroneEnvironment(crosswind, 1.0, Double.POSITIVE_INFINITY);
		double cleanMinThrust = Double.POSITIVE_INFINITY;
		double cleanMaxThrust = Double.NEGATIVE_INFINITY;
		double stalledMinThrust = Double.POSITIVE_INFINITY;
		double stalledMaxThrust = Double.NEGATIVE_INFINITY;
		double cleanMinForceX = Double.POSITIVE_INFINITY;
		double cleanMaxForceX = Double.NEGATIVE_INFINITY;
		double cleanMinForceZ = Double.POSITIVE_INFINITY;
		double cleanMaxForceZ = Double.NEGATIVE_INFINITY;
		double stalledMinForceX = Double.POSITIVE_INFINITY;
		double stalledMaxForceX = Double.NEGATIVE_INFINITY;
		double stalledMinForceZ = Double.POSITIVE_INFINITY;
		double stalledMaxForceZ = Double.NEGATIVE_INFINITY;

		for (int i = 0; i < 260; i++) {
			clean.state().setOrientation(Quaternion.IDENTITY);
			highAdvance.state().setOrientation(Quaternion.IDENTITY);
			clean.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			highAdvance.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			clean.state().setVelocityMetersPerSecond(crosswind);
			highAdvance.state().setVelocityMetersPerSecond(crosswind);
			clean.step(input, 0.005, matchingWind);
			highAdvance.step(input, 0.005);

			if (i >= 120) {
				double cleanThrust = clean.state().rotorThrustNewtons(0);
				double stalledThrust = highAdvance.state().rotorThrustNewtons(0);
				cleanMinThrust = Math.min(cleanMinThrust, cleanThrust);
				cleanMaxThrust = Math.max(cleanMaxThrust, cleanThrust);
				stalledMinThrust = Math.min(stalledMinThrust, stalledThrust);
				stalledMaxThrust = Math.max(stalledMaxThrust, stalledThrust);
				Vec3 cleanForce = clean.state().rotorForceBodyNewtons(0);
				Vec3 stalledForce = highAdvance.state().rotorForceBodyNewtons(0);
				cleanMinForceX = Math.min(cleanMinForceX, cleanForce.x());
				cleanMaxForceX = Math.max(cleanMaxForceX, cleanForce.x());
				cleanMinForceZ = Math.min(cleanMinForceZ, cleanForce.z());
				cleanMaxForceZ = Math.max(cleanMaxForceZ, cleanForce.z());
				stalledMinForceX = Math.min(stalledMinForceX, stalledForce.x());
				stalledMaxForceX = Math.max(stalledMaxForceX, stalledForce.x());
				stalledMinForceZ = Math.min(stalledMinForceZ, stalledForce.z());
				stalledMaxForceZ = Math.max(stalledMaxForceZ, stalledForce.z());
			}
		}

		double cleanThrustRange = cleanMaxThrust - cleanMinThrust;
		double stalledThrustRange = stalledMaxThrust - stalledMinThrust;
		double cleanLateralRange = cleanMaxForceX - cleanMinForceX + cleanMaxForceZ - cleanMinForceZ;
		double stalledLateralRange = stalledMaxForceX - stalledMinForceX + stalledMaxForceZ - stalledMinForceZ;
		assertTrue(highAdvance.state().rotorAdvanceRatio(0) > 0.45);
		assertTrue(highAdvance.state().rotorStallIntensity(0) > 0.35);
		assertTrue(highAdvance.state().rotorBladeDissymmetryIntensity(0) > clean.state().rotorBladeDissymmetryIntensity(0) + 0.25);
		assertTrue(stalledThrustRange > 0.04,
				() -> "cleanThrustRange=" + cleanThrustRange + " stalledThrustRange=" + stalledThrustRange);
		assertTrue(stalledLateralRange > 0.035,
				() -> "cleanLateralRange=" + cleanLateralRange + " stalledLateralRange=" + stalledLateralRange);
		assertTrue(highAdvance.state().rotorVibration() > clean.state().rotorVibration() + 0.025,
				() -> "cleanVibration=" + clean.state().rotorVibration()
						+ " stalledVibration=" + highAdvance.state().rotorVibration());
	}

	@Test
	void bladeDissymmetryCreatesHubMomentInHighAdvanceFlow() {
		PidGains passiveGains = new PidGains(0.0, 0.0, 0.0, 0.0);
		DroneConfig config = withCommonGains(directControl(DroneConfig.racingQuad()), passiveGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withMotorIdleAndAirmode(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics noRelativeFlow = new DronePhysics(config);
		DronePhysics crossFlow = new DronePhysics(config);
		DronePhysics reacting = new DronePhysics(config);
		DroneInput input = new DroneInput(0.68, 0.0, 0.0, 0.0, true);
		Vec3 crosswind = new Vec3(34.0, 0.0, 0.0);
		DroneEnvironment matchingWind = new DroneEnvironment(crosswind, 1.0, Double.POSITIVE_INFINITY);

		for (int i = 0; i < 220; i++) {
			noRelativeFlow.state().setOrientation(Quaternion.IDENTITY);
			crossFlow.state().setOrientation(Quaternion.IDENTITY);
			noRelativeFlow.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			crossFlow.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			noRelativeFlow.state().setVelocityMetersPerSecond(crosswind);
			crossFlow.state().setVelocityMetersPerSecond(crosswind);
			noRelativeFlow.step(input, 0.005, matchingWind);
			crossFlow.step(input, 0.005);

			reacting.state().setOrientation(Quaternion.IDENTITY);
			reacting.state().setVelocityMetersPerSecond(crosswind);
			reacting.step(input, 0.005);
		}

		Vec3 cleanTorque = noRelativeFlow.state().rotorBladeDissymmetryTorqueBodyNewtonMeters();
		Vec3 crossFlowTorque = crossFlow.state().rotorBladeDissymmetryTorqueBodyNewtonMeters();
		assertEquals(0.0, cleanTorque.length(), 1.0e-9);
		assertTrue(crossFlow.state().averageRotorBladeDissymmetryIntensity() > 0.45);
		assertTrue(crossFlowTorque.z() > 0.004,
				() -> "crossFlowTorque=" + crossFlowTorque);
		assertTrue(Math.abs(crossFlowTorque.z()) > Math.abs(crossFlowTorque.x()) + Math.abs(crossFlowTorque.y()));
		assertTrue(Math.abs(reacting.state().angularVelocityBodyRadiansPerSecond().z()) > 0.20,
				() -> "reactingRates=" + reacting.state().angularVelocityBodyRadiansPerSecond());
	}

	@Test
	void airframeAngularDragStrengthensWithAirspeed() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32));
		DronePhysics stillAir = new DronePhysics(config);
		DronePhysics fastAir = new DronePhysics(config);
		Vec3 initialSpin = new Vec3(0.42, 0.18, -0.34);
		stillAir.state().setAngularVelocityBodyRadiansPerSecond(initialSpin);
		fastAir.state().setAngularVelocityBodyRadiansPerSecond(initialSpin);
		Vec3 forwardAirspeed = new Vec3(0.0, 0.0, 28.0);

		for (int i = 0; i < 20; i++) {
			stillAir.state().setOrientation(Quaternion.IDENTITY);
			fastAir.state().setOrientation(Quaternion.IDENTITY);
			stillAir.state().setVelocityMetersPerSecond(Vec3.ZERO);
			fastAir.state().setVelocityMetersPerSecond(forwardAirspeed);
			stillAir.step(DroneInput.idle(), 0.0025);
			fastAir.step(DroneInput.idle(), 0.0025);
		}

		Vec3 stillDrag = stillAir.state().airframeAngularDragTorqueBodyNewtonMeters();
		Vec3 fastDrag = fastAir.state().airframeAngularDragTorqueBodyNewtonMeters();
		assertTrue(Math.abs(fastDrag.x()) > Math.abs(stillDrag.x()) * 2.2);
		assertTrue(Math.abs(fastDrag.y()) > Math.abs(stillDrag.y()) * 2.2);
		assertTrue(Math.abs(fastDrag.z()) > Math.abs(stillDrag.z()) * 2.2);
		assertTrue(fastAir.state().angularVelocityBodyRadiansPerSecond().length()
				< stillAir.state().angularVelocityBodyRadiansPerSecond().length() - 0.006);
		assertEquals(0.0, fastAir.state().airframeAerodynamicTorqueBodyNewtonMeters().length(), 1.0e-9);
	}

	@Test
	void neuroBemGuardLimitsHighRateAirframeAngularDragToResidualTorqueScale() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32));
		DronePhysics physics = new DronePhysics(config);
		Vec3 highRate = new Vec3(8.0, 3.5, -6.0);
		Vec3 highAirspeed = new Vec3(0.0, 0.0, 28.0);

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setVelocityMetersPerSecond(highAirspeed);
		physics.state().setAngularVelocityBodyRadiansPerSecond(highRate);
		physics.step(DroneInput.idle(), 0.0025);

		Vec3 angularDrag = physics.state().airframeAngularDragTorqueBodyNewtonMeters();
		Vec3 limit = NeuroBemAirframeResidualCalibration.runtimeResidualTorqueP95AxisLimitNewtonMeters(config);
		assertTrue(angularDrag.dot(highRate) < 0.0, () -> "angularDrag=" + angularDrag);
		assertTrue(Math.abs(angularDrag.x()) <= limit.x() + 1.0e-9,
				() -> "angularDrag=" + angularDrag + " limit=" + limit);
		assertTrue(Math.abs(angularDrag.y()) <= limit.y() + 1.0e-9,
				() -> "angularDrag=" + angularDrag + " limit=" + limit);
		assertTrue(Math.abs(angularDrag.z()) <= limit.z() + 1.0e-9,
				() -> "angularDrag=" + angularDrag + " limit=" + limit);
		assertEquals(limit.x(), Math.abs(angularDrag.x()), 1.0e-9);
		assertEquals(limit.z(), Math.abs(angularDrag.z()), 1.0e-9);
	}

	@Test
	void separatedFlowStrengthensAirframeAngularDampingAtHighSideslip() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32))
				.withAngularDragCoefficient(0.0);
		DronePhysics cleanForward = new DronePhysics(config);
		DronePhysics separated = new DronePhysics(config);
		Vec3 bodyRates = new Vec3(5.5, 2.0, -4.5);
		Vec3 forwardVelocity = new Vec3(0.0, 0.0, 18.0);
		Vec3 highSideslipVelocity = new Vec3(17.0, 0.0, 4.0);

		for (int i = 0; i < 180; i++) {
			holdInCruise(cleanForward, forwardVelocity);
			holdInCruise(separated, highSideslipVelocity);
			cleanForward.state().setAngularVelocityBodyRadiansPerSecond(bodyRates);
			separated.state().setAngularVelocityBodyRadiansPerSecond(bodyRates);
			cleanForward.step(DroneInput.idle(), 0.005);
			separated.step(DroneInput.idle(), 0.005);
		}

		Vec3 cleanDrag = cleanForward.state().airframeAngularDragTorqueBodyNewtonMeters();
		Vec3 separatedDrag = separated.state().airframeAngularDragTorqueBodyNewtonMeters();
		assertTrue(cleanForward.state().airframeSeparatedFlowIntensity() < 0.03);
		assertTrue(separated.state().airframeSeparatedFlowIntensity() > 0.80);
		assertTrue(separatedDrag.dot(bodyRates) < cleanDrag.dot(bodyRates) - 0.08,
				() -> "cleanDrag=" + cleanDrag + " separatedDrag=" + separatedDrag);
		assertTrue(Math.abs(separatedDrag.y()) > Math.abs(cleanDrag.y()) + 0.025,
				() -> "cleanDrag=" + cleanDrag + " separatedDrag=" + separatedDrag);
	}

	@Test
	void airframeAngularDragAppearsFromBodyRotationInStillAir() {
		PidGains passiveGains = new PidGains(0.0, 0.0, 0.0, 0.0);
		DroneConfig base = withCommonGains(directControl(DroneConfig.racingQuad()), passiveGains)
				.withLinearDragCoefficient(0.0)
				.withAngularDragCoefficient(0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorDiskDragCoefficient(0.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics noBodyArea = new DronePhysics(base.withBodyDragCoefficients(Vec3.ZERO));
		DronePhysics tumbling = new DronePhysics(base.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32)));
		Vec3 bodyRates = new Vec3(7.0, 3.0, -6.0);
		noBodyArea.state().setAngularVelocityBodyRadiansPerSecond(bodyRates);
		tumbling.state().setAngularVelocityBodyRadiansPerSecond(bodyRates);

		noBodyArea.step(DroneInput.idle(), 0.005);
		tumbling.step(DroneInput.idle(), 0.005);

		Vec3 tumbleDrag = tumbling.state().airframeAngularDragTorqueBodyNewtonMeters();
		assertEquals(0.0, noBodyArea.state().airframeAngularDragTorqueBodyNewtonMeters().length(), 1.0e-9);
		assertTrue(tumbleDrag.dot(bodyRates) < -0.055, () -> "tumbleDrag=" + tumbleDrag);
		assertTrue(tumbling.state().angularVelocityBodyRadiansPerSecond().length()
				< noBodyArea.state().angularVelocityBodyRadiansPerSecond().length() - 0.02);
	}

	@Test
	void rotorWashEnhancesAirframeAngularDampingInPoweredHover() {
		PidGains passiveGains = new PidGains(0.0, 0.0, 0.0, 0.0);
		DroneConfig config = withCommonGains(directControl(DroneConfig.racingQuad()), passiveGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32))
				.withAngularDragCoefficient(0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorDiskDragCoefficient(0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics unpowered = new DronePhysics(config);
		DronePhysics powered = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.05, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 140; i++) {
			holdInStillAir(powered);
			powered.step(hover, 0.005);
		}

		Vec3 bodyRates = new Vec3(6.0, 2.5, -5.0);
		holdInStillAir(unpowered);
		holdInStillAir(powered);
		unpowered.state().setAngularVelocityBodyRadiansPerSecond(bodyRates);
		powered.state().setAngularVelocityBodyRadiansPerSecond(bodyRates);
		unpowered.step(DroneInput.idle(), 0.005);
		powered.step(hover, 0.005);

		Vec3 unpoweredDrag = unpowered.state().airframeAngularDragTorqueBodyNewtonMeters();
		Vec3 poweredDrag = powered.state().airframeAngularDragTorqueBodyNewtonMeters();
		assertTrue(unpoweredDrag.dot(bodyRates) < -0.035, () -> "unpoweredDrag=" + unpoweredDrag);
		assertTrue(poweredDrag.dot(bodyRates) < unpoweredDrag.dot(bodyRates) - 0.055,
				() -> "unpoweredDrag=" + unpoweredDrag + " poweredDrag=" + poweredDrag);
		assertTrue(Math.abs(poweredDrag.x()) > Math.abs(unpoweredDrag.x()) + 0.012,
				() -> "unpoweredDrag=" + unpoweredDrag + " poweredDrag=" + poweredDrag);
		assertTrue(Math.abs(poweredDrag.z()) > Math.abs(unpoweredDrag.z()) + 0.010,
				() -> "unpoweredDrag=" + unpoweredDrag + " poweredDrag=" + poweredDrag);
	}

	@Test
	void rotorWashAirframeAngularDampingBuildsWithPressureLag() {
		PidGains passiveGains = new PidGains(0.0, 0.0, 0.0, 0.0);
		DroneConfig config = withCommonGains(directControl(DroneConfig.racingQuad()), passiveGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(new Vec3(0.36, 0.18, 0.32))
				.withAngularDragCoefficient(0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorDiskDragCoefficient(0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.05, 0.0, 0.0, 0.0, true);
		Vec3 bodyRates = new Vec3(6.0, 0.0, -5.0);

		holdInStillAir(physics);
		physics.state().setAngularVelocityBodyRadiansPerSecond(bodyRates);
		physics.step(DroneInput.idle(), 0.005);
		double baselineDamping = physics.state().airframeAngularDragTorqueBodyNewtonMeters().dot(bodyRates);

		holdInStillAir(physics);
		physics.state().setAngularVelocityBodyRadiansPerSecond(bodyRates);
		physics.step(hover, 0.005);
		double firstPunchDamping = physics.state().airframeAngularDragTorqueBodyNewtonMeters().dot(bodyRates);

		for (int i = 0; i < 36; i++) {
			holdInStillAir(physics);
			physics.state().setAngularVelocityBodyRadiansPerSecond(bodyRates);
			physics.step(hover, 0.005);
		}
		double settledDamping = physics.state().airframeAngularDragTorqueBodyNewtonMeters().dot(bodyRates);

		assertTrue(baselineDamping < -0.030, () -> "baselineDamping=" + baselineDamping);
		assertEquals(baselineDamping, firstPunchDamping, 0.006);
		assertTrue(settledDamping < firstPunchDamping - 0.050,
				() -> "firstPunchDamping=" + firstPunchDamping + " settledDamping=" + settledDamping);
	}

	@Test
	void rotorAngularDragDampsBodyRatesWhenPropsAreSpinning() {
		PidGains passiveGains = new PidGains(0.0, 0.0, 0.0, 0.0);
		DroneConfig base = withCommonGains(directControl(DroneConfig.racingQuad()), passiveGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withAngularDragCoefficient(0.0)
				.withRotorYawTorquePerThrustMeter(0.0)
				.withRotorInertiaKgMetersSquared(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics noRotorAngularDrag = new DronePhysics(base);
		DronePhysics rotorAngularDrag = new DronePhysics(base.withRotorDiskDragCoefficient(0.010));
		Vec3 initialBodyRates = new Vec3(5.5, 2.2, -7.0);
		noRotorAngularDrag.state().setAngularVelocityBodyRadiansPerSecond(initialBodyRates);
		rotorAngularDrag.state().setAngularVelocityBodyRadiansPerSecond(initialBodyRates);
		DroneInput hover = new DroneInput(base.hoverThrottle(), 0.0, 0.0, 0.0, true);
		double maxLoadDelta = 0.0;

		for (int i = 0; i < 260; i++) {
			noRotorAngularDrag.state().setOrientation(Quaternion.IDENTITY);
			rotorAngularDrag.state().setOrientation(Quaternion.IDENTITY);
			noRotorAngularDrag.state().setVelocityMetersPerSecond(Vec3.ZERO);
			rotorAngularDrag.state().setVelocityMetersPerSecond(Vec3.ZERO);
			noRotorAngularDrag.step(hover, 0.005);
			rotorAngularDrag.step(hover, 0.005);
			maxLoadDelta = Math.max(
					maxLoadDelta,
					rotorAngularDrag.state().averageRotorAerodynamicLoadFactor()
							- noRotorAngularDrag.state().averageRotorAerodynamicLoadFactor()
			);
		}

		Vec3 dampingTorque = rotorAngularDrag.state().rotorAngularDragTorqueBodyNewtonMeters();
		assertEquals(0.0, noRotorAngularDrag.state().rotorAngularDragTorqueBodyNewtonMeters().length(), 1.0e-9);
		assertTrue(dampingTorque.dot(rotorAngularDrag.state().angularVelocityBodyRadiansPerSecond()) < -0.01);
		assertTrue(rotorAngularDrag.state().angularVelocityBodyRadiansPerSecond().length()
				< noRotorAngularDrag.state().angularVelocityBodyRadiansPerSecond().length() * 0.82);
		assertTrue(maxLoadDelta > 0.06, "maxLoadDelta=" + maxLoadDelta);
	}

	@Test
	void rotorInPlaneDragOpposesDiskPlaneFlowAndRaisesRotorLoad() {
		PidGains passiveGains = new PidGains(0.0, 0.0, 0.0, 0.0);
		DroneConfig base = withCommonGains(directControl(DroneConfig.racingQuad()), passiveGains)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withRotorInducedInflow(0.0, 0.0)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0);
		DronePhysics noDiskDrag = new DronePhysics(base.withRotorDiskDragCoefficient(0.0));
		DronePhysics hForce = new DronePhysics(base.withRotorDiskDragCoefficient(0.0042));
		DroneInput hover = new DroneInput(base.hoverThrottle() + 0.08, 0.0, 0.0, 0.0, true);
		Vec3 crossflow = new Vec3(24.0, 0.0, 0.0);
		double maxLoadDelta = 0.0;

		for (int i = 0; i < 260; i++) {
			noDiskDrag.state().setOrientation(Quaternion.IDENTITY);
			hForce.state().setOrientation(Quaternion.IDENTITY);
			noDiskDrag.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			hForce.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			noDiskDrag.state().setVelocityMetersPerSecond(crossflow);
			hForce.state().setVelocityMetersPerSecond(crossflow);
			noDiskDrag.step(hover, 0.005, DroneEnvironment.calm());
			hForce.step(hover, 0.005, DroneEnvironment.calm());
			maxLoadDelta = Math.max(
					maxLoadDelta,
					hForce.state().averageRotorAerodynamicLoadFactor()
							- noDiskDrag.state().averageRotorAerodynamicLoadFactor()
			);
		}

		assertEquals(0.0, noDiskDrag.state().maxRotorInPlaneDragForceNewtons(), 1.0e-9);
		assertTrue(hForce.state().averageRotorInPlaneDragForceNewtons() > 0.10,
				() -> "hforce=" + hForce.state().averageRotorInPlaneDragForceNewtons());
		assertTrue(hForce.state().rotorForceBodyNewtons(0).x()
				< noDiskDrag.state().rotorForceBodyNewtons(0).x() - 0.12);
		assertTrue(maxLoadDelta > 0.04, "maxLoadDelta=" + maxLoadDelta);
		assertTrue(hForce.state().averageMotorAerodynamicTorqueNewtonMeters()
						> noDiskDrag.state().averageMotorAerodynamicTorqueNewtonMeters() + 0.0007,
				() -> "hForceTorque=" + hForce.state().averageMotorAerodynamicTorqueNewtonMeters()
						+ " noDiskDragTorque=" + noDiskDrag.state().averageMotorAerodynamicTorqueNewtonMeters());
		assertTrue(hForce.state().averageMotorShaftPowerWatts()
				> noDiskDrag.state().averageMotorShaftPowerWatts() + 1.0);
		assertTrue(hForce.state().batteryCurrentAmps() > noDiskDrag.state().batteryCurrentAmps() + 0.20,
				() -> "hForceCurrent=" + hForce.state().batteryCurrentAmps()
						+ " noDiskDragCurrent=" + noDiskDrag.state().batteryCurrentAmps());
	}

	@Test
	void descendingAxialRotorFlowReducesLift() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO);
		DronePhysics noRelativeDescent = new DronePhysics(config);
		DronePhysics descending = new DronePhysics(config);
		Vec3 descentVelocity = new Vec3(0.0, -6.0, 0.0);
		noRelativeDescent.state().setVelocityMetersPerSecond(descentVelocity);
		descending.state().setVelocityMetersPerSecond(descentVelocity);
		DroneInput hover = new DroneInput(config.hoverThrottle(), 0.0, 0.0, 0.0, true);
		DroneEnvironment fallingAir = new DroneEnvironment(descentVelocity, 1.0, Double.POSITIVE_INFINITY);

		for (int i = 0; i < 500; i++) {
			noRelativeDescent.step(hover, 0.005, fallingAir);
			descending.step(hover, 0.005);
		}

		assertTrue(noRelativeDescent.state().velocityMetersPerSecond().y() > descending.state().velocityMetersPerSecond().y() + 0.35);
	}

	@Test
	void perRotorLocalWindFeedsAxialGustModel() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.08, 0.0, 0.0, 0.0, true);
		Vec3[] rotorWinds = {
				new Vec3(0.0, 9.0, 0.0),
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO
		};
		DroneEnvironment localUpdraft = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				rotorWinds
		);

		for (int i = 0; i < 260; i++) {
			holdInStillAir(physics);
			physics.step(hover, 0.005, localUpdraft);
		}

		assertTrue(physics.state().rotorAxialGustThrustScale(0) > 1.08,
				() -> "rotor0=" + physics.state().rotorAxialGustThrustScale(0));
		assertEquals(1.0, physics.state().rotorAxialGustThrustScale(1), 0.03);
		assertTrue(physics.state().rotorAxialGustThrustScale(0)
						> physics.state().rotorAxialGustThrustScale(1) + 0.08,
				() -> "rotor0=" + physics.state().rotorAxialGustThrustScale(0)
						+ " rotor1=" + physics.state().rotorAxialGustThrustScale(1));
	}

	@Test
	void rotorDiskWindGradientTiltsLoadedRotorDisk() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics clean = new DronePhysics(config);
		DronePhysics gradient = new DronePhysics(config);
		DroneInput hover = new DroneInput(config.hoverThrottle() + 0.08, 0.0, 0.0, 0.0, true);
		Vec3[] diskGradients = {
				new Vec3(12.0, 0.0, 0.0),
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO
		};
		DroneEnvironment cleanAir = DroneEnvironment.calm();
		DroneEnvironment diskGradientAir = new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				null,
				diskGradients
		);

		for (int i = 0; i < 260; i++) {
			holdInStillAir(clean);
			holdInStillAir(gradient);
			clean.step(hover, 0.005, cleanAir);
			gradient.step(hover, 0.005, diskGradientAir);
		}

		assertTrue(gradient.state().rotorFlappingTiltRadians(0)
						> clean.state().rotorFlappingTiltRadians(0) + Math.toRadians(0.8),
				() -> "cleanTiltDeg=" + Math.toDegrees(clean.state().rotorFlappingTiltRadians(0))
						+ " gradientTiltDeg=" + Math.toDegrees(gradient.state().rotorFlappingTiltRadians(0)));
		assertTrue(gradient.state().rotorForceBodyNewtons(0).x()
						> clean.state().rotorForceBodyNewtons(0).x() + 0.04,
				() -> "cleanForce=" + clean.state().rotorForceBodyNewtons(0)
						+ " gradientForce=" + gradient.state().rotorForceBodyNewtons(0));
		assertTrue(gradient.state().rotorAerodynamicLoadFactor(0)
						> clean.state().rotorAerodynamicLoadFactor(0) + 0.015,
				() -> "cleanLoad=" + clean.state().rotorAerodynamicLoadFactor(0)
						+ " gradientLoad=" + gradient.state().rotorAerodynamicLoadFactor(0));
		assertTrue(gradient.state().rotorStallIntensity(0)
						> clean.state().rotorStallIntensity(0) + 0.05,
				() -> "cleanStall=" + clean.state().rotorStallIntensity(0)
						+ " gradientStall=" + gradient.state().rotorStallIntensity(0));
		assertTrue(gradient.state().rotorStallIntensity(0) < 0.18,
				() -> "gradientStall=" + gradient.state().rotorStallIntensity(0));
		assertTrue(gradient.state().rotorFlappingTiltRadians(0)
						> gradient.state().rotorFlappingTiltRadians(1) + Math.toRadians(0.8),
				() -> "rotor0TiltDeg=" + Math.toDegrees(gradient.state().rotorFlappingTiltRadians(0))
						+ " rotor1TiltDeg=" + Math.toDegrees(gradient.state().rotorFlappingTiltRadians(1)));
	}

	@Test
	void inducedInflowLagsThrottlePunchWithoutChangingSteadyThrust() {
		DroneConfig base = directControl(DroneConfig.racingQuad())
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics fastInflow = new DronePhysics(base.withRotorInducedInflow(0.0, 0.0));
		DronePhysics slowInflow = new DronePhysics(base.withRotorInducedInflow(0.22, 0.45));
		DroneInput punch = new DroneInput(0.85, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 5; i++) {
			holdInStillAir(fastInflow);
			holdInStillAir(slowInflow);
			fastInflow.step(punch, 0.005);
			slowInflow.step(punch, 0.005);
		}

		assertTrue(fastInflow.state().averageRotorInducedVelocityMetersPerSecond()
				> slowInflow.state().averageRotorInducedVelocityMetersPerSecond() * 4.0);
		assertTrue(fastInflow.state().rotorThrustNewtons(0) > slowInflow.state().rotorThrustNewtons(0) * 1.20);
		assertTrue(slowInflow.state().minRotorInducedLagThrustScale() < 0.90);
		assertEquals(1.0, fastInflow.state().minRotorInducedLagThrustScale(), 1.0e-9);

		for (int i = 0; i < 500; i++) {
			holdInStillAir(fastInflow);
			holdInStillAir(slowInflow);
			fastInflow.step(punch, 0.005);
			slowInflow.step(punch, 0.005);
		}

		assertEquals(fastInflow.state().rotorThrustNewtons(0), slowInflow.state().rotorThrustNewtons(0), 0.25);
		assertEquals(1.0, slowInflow.state().minRotorInducedLagThrustScale(), 0.02);
	}

	@Test
	void dynamicInducedInflowTimeConstantFollowsWakeTransitScale() throws ReflectiveOperationException {
		DroneConfig config = DroneConfig.racingQuad().withRotorInducedInflow(0.035, 0.16);
		RotorSpec rotor = config.rotors().get(0);
		Method targetInflowMethod = DronePhysics.class.getDeclaredMethod(
				"targetRotorInducedVelocityMetersPerSecond",
				RotorSpec.class,
				double.class,
				double.class
		);
		Method dynamicTauMethod = DronePhysics.class.getDeclaredMethod(
				"rotorDynamicInflowTimeConstantSeconds",
				RotorSpec.class,
				Vec3.class,
				double.class,
				double.class,
				double.class,
				double.class
		);
		targetInflowMethod.setAccessible(true);
		dynamicTauMethod.setAccessible(true);

		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double hoverInflow = (double) targetInflowMethod.invoke(null, rotor, hoverThrust, 1.0);
		double maxInflow = (double) targetInflowMethod.invoke(null, rotor, rotor.maxThrustNewtons(), 1.0);
		double hoverOmega = rotor.maxOmegaRadiansPerSecond() * 0.62;
		double highOmega = rotor.maxOmegaRadiansPerSecond() * 0.92;
		double lowOmega = rotor.maxOmegaRadiansPerSecond() * 0.24;

		double hoverTau = (double) dynamicTauMethod.invoke(null, rotor, Vec3.ZERO, hoverOmega, hoverInflow, 0.0, hoverInflow);
		double highThrustTau = (double) dynamicTauMethod.invoke(null, rotor, Vec3.ZERO, highOmega, maxInflow, 0.0, hoverInflow);
		double lowThrustReleaseTau = (double) dynamicTauMethod.invoke(null, rotor, Vec3.ZERO, lowOmega, hoverInflow * 0.25, hoverInflow, hoverInflow);
		double crossflowTau = (double) dynamicTauMethod.invoke(null, rotor, new Vec3(18.0, 0.0, 0.0), hoverOmega, hoverInflow, 0.0, hoverInflow);
		double descendingTau = (double) dynamicTauMethod.invoke(null, rotor, new Vec3(0.0, -10.0, 0.0), hoverOmega, hoverInflow, hoverInflow, hoverInflow);

		assertTrue(hoverTau > 0.020 && hoverTau < 0.045, () -> "hoverTau=" + hoverTau);
		assertTrue(highThrustTau < hoverTau * 0.70,
				() -> "highThrustTau=" + highThrustTau + " hoverTau=" + hoverTau);
		assertTrue(lowThrustReleaseTau > hoverTau * 2.20,
				() -> "lowThrustReleaseTau=" + lowThrustReleaseTau + " hoverTau=" + hoverTau);
		assertTrue(crossflowTau < hoverTau * 0.95,
				() -> "crossflowTau=" + crossflowTau + " hoverTau=" + hoverTau);
		assertTrue(descendingTau > hoverTau * 1.10,
				() -> "descendingTau=" + descendingTau + " hoverTau=" + hoverTau);
	}

	@Test
	void retainedInducedWakeLoadsRepunchAfterThrottleChop() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 1.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 120.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRotorInducedInflow(0.035, 0.50);
		DronePhysics retainedWake = new DronePhysics(config);
		DronePhysics freshAir = new DronePhysics(config);
		DroneInput highThrottle = new DroneInput(0.86, 0.0, 0.0, 0.0, true);
		DroneInput idle = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 220; i++) {
			holdInStillAir(retainedWake);
			retainedWake.step(highThrottle, 0.005);
		}
		double loadedInducedVelocity = retainedWake.state().averageRotorInducedVelocityMetersPerSecond();

		for (int i = 0; i < 30; i++) {
			holdInStillAir(retainedWake);
			holdInStillAir(freshAir);
			retainedWake.step(idle, 0.005);
			freshAir.step(idle, 0.005);
		}

		holdInStillAir(retainedWake);
		holdInStillAir(freshAir);
		retainedWake.step(highThrottle, 0.005);
		freshAir.step(highThrottle, 0.005);

		double retainedThrust = retainedWake.state().rotorThrustNewtons(0);
		double freshThrust = freshAir.state().rotorThrustNewtons(0);
		double retainedLoad = retainedWake.state().rotorAerodynamicLoadFactor(0);
		double freshLoad = freshAir.state().rotorAerodynamicLoadFactor(0);
		double retainedLagScale = retainedWake.state().minRotorInducedLagThrustScale();
		double freshLagScale = freshAir.state().minRotorInducedLagThrustScale();
		double retainedOmega = retainedWake.state().motorOmegaRadiansPerSecond(0);
		double freshOmega = freshAir.state().motorOmegaRadiansPerSecond(0);

		assertTrue(loadedInducedVelocity > 5.0, () -> "loadedInducedVelocity=" + loadedInducedVelocity);
		assertEquals(freshOmega, retainedOmega, freshOmega * 0.08);
		assertTrue(retainedThrust > freshThrust + 0.08,
				() -> "retainedThrust=" + retainedThrust + " freshThrust=" + freshThrust);
		assertTrue(retainedLagScale > freshLagScale + 0.05,
				() -> "retainedLagScale=" + retainedLagScale + " freshLagScale=" + freshLagScale);
		assertTrue(retainedLoad > freshLoad + 0.02,
				() -> "retainedLoad=" + retainedLoad + " freshLoad=" + freshLoad);
	}

	@Test
	void propwashAppearsDuringHighThrottleDescent() {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		physics.state().setVelocityMetersPerSecond(new Vec3(0.0, -8.0, 0.0));
		DroneInput input = new DroneInput(0.85, 0.0, 0.0, 0.0, true);
		double maxIntensity = 0.0;
		double maxTorque = 0.0;

		for (int i = 0; i < 300; i++) {
			physics.step(input, 0.005);
			maxIntensity = Math.max(maxIntensity, physics.state().propwashIntensity());
			maxTorque = Math.max(maxTorque, physics.state().propwashTorqueBodyNewtonMeters().length());
		}

		assertTrue(maxIntensity > 0.2);
		assertTrue(maxTorque > 0.004);
	}

	@Test
	void propwashWakeBuildsDuringLowThrottleDescentAndHitsOnPunchOut() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO);
		DronePhysics retainedWake = new DronePhysics(config);
		DronePhysics freshAir = new DronePhysics(config);
		Vec3 descent = new Vec3(0.0, -8.0, 0.0);
		DroneInput lowThrottleDescent = new DroneInput(0.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 240; i++) {
			retainedWake.state().setOrientation(Quaternion.IDENTITY);
			retainedWake.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			retainedWake.state().setVelocityMetersPerSecond(descent);
			retainedWake.step(lowThrottleDescent, 0.005);
		}

		assertTrue(retainedWake.state().propwashWakeIntensity() > 0.12);
		assertTrue(retainedWake.state().propwashIntensity() < 0.02);

		DroneInput punchOut = new DroneInput(0.85, 0.0, 0.0, 0.0, true);
		retainedWake.state().setOrientation(Quaternion.IDENTITY);
		freshAir.state().setOrientation(Quaternion.IDENTITY);
		retainedWake.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		freshAir.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		retainedWake.state().setVelocityMetersPerSecond(descent);
		freshAir.state().setVelocityMetersPerSecond(descent);
		retainedWake.step(punchOut, 0.005);
		freshAir.step(punchOut, 0.005);

		assertTrue(retainedWake.state().propwashWakeIntensity() > freshAir.state().propwashWakeIntensity() + 0.08);
		assertTrue(retainedWake.state().propwashIntensity() > freshAir.state().propwashIntensity() + 0.04);
		assertTrue(retainedWake.state().propwashTorqueBodyNewtonMeters().length()
				> freshAir.state().propwashTorqueBodyNewtonMeters().length() + 0.001);
	}

	@Test
	void propwashRequiresRetainedDescendingWake() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO);
		DronePhysics fallingWithAir = new DronePhysics(config);
		DronePhysics flushedByCrossflow = new DronePhysics(config);
		DroneInput input = new DroneInput(0.85, 0.0, 0.0, 0.0, true);
		Vec3 descent = new Vec3(0.0, -8.0, 0.0);
		fallingWithAir.state().setVelocityMetersPerSecond(descent);
		flushedByCrossflow.state().setVelocityMetersPerSecond(new Vec3(10.0, -8.0, 0.0));
		DroneEnvironment fallingAir = new DroneEnvironment(descent, 1.0, Double.POSITIVE_INFINITY);

		for (int i = 0; i < 300; i++) {
			fallingWithAir.state().setOrientation(Quaternion.IDENTITY);
			flushedByCrossflow.state().setOrientation(Quaternion.IDENTITY);
			fallingWithAir.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			flushedByCrossflow.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			fallingWithAir.state().setVelocityMetersPerSecond(descent);
			flushedByCrossflow.state().setVelocityMetersPerSecond(new Vec3(10.0, -8.0, 0.0));
			fallingWithAir.step(input, 0.005, fallingAir);
			flushedByCrossflow.step(input, 0.005);
		}

		assertTrue(fallingWithAir.state().propwashIntensity() < 0.05);
		assertTrue(flushedByCrossflow.state().propwashIntensity() < 0.05);
		assertTrue(fallingWithAir.state().propwashWakeIntensity() < 0.05);
		assertTrue(flushedByCrossflow.state().propwashWakeIntensity() < 0.05);
	}

	@Test
	void propwashWakeUsesRotorAxisForCantedDisks() {
		Vec3 rotorAxis = new Vec3(0.80, 0.60, 0.0).normalized();
		Vec3 axialDescent = rotorAxis.multiply(-8.0);
		Vec3 transverseSameBodyDescent = new Vec3(0.60, -0.80, 0.0).normalized().multiply(6.0);
		assertEquals(axialDescent.y(), transverseSameBodyDescent.y(), 1.0e-9);
		assertEquals(0.0, rotorAxis.dot(transverseSameBodyDescent), 1.0e-9);

		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotors(DroneConfig.racingQuad().rotors().stream()
						.map(rotor -> rotor.withThrustAxisBody(rotorAxis))
						.toList());
		DronePhysics axialWake = new DronePhysics(config);
		DronePhysics transverseWake = new DronePhysics(config);
		DroneInput input = new DroneInput(0.85, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 320; i++) {
			axialWake.state().setOrientation(Quaternion.IDENTITY);
			transverseWake.state().setOrientation(Quaternion.IDENTITY);
			axialWake.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			transverseWake.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			axialWake.state().setVelocityMetersPerSecond(axialDescent);
			transverseWake.state().setVelocityMetersPerSecond(transverseSameBodyDescent);
			axialWake.step(input, 0.005);
			transverseWake.step(input, 0.005);
		}

		assertTrue(axialWake.state().propwashWakeIntensity() > 0.12);
		assertTrue(transverseWake.state().propwashWakeIntensity()
				< axialWake.state().propwashWakeIntensity() - 0.10);
		assertTrue(transverseWake.state().propwashIntensity()
				< axialWake.state().propwashIntensity() - 0.06);
	}

	@Test
	void vortexRingDescentEnvelopePeaksAtSmallPropReferenceBand() throws ReflectiveOperationException {
		Method envelopeMethod = DronePhysics.class.getDeclaredMethod(
				"rotorVortexRingDescentEnvelope",
				double.class
		);
		Method buffetEnvelopeMethod = DronePhysics.class.getDeclaredMethod(
				"rotorVortexRingBuffetEnvelope",
				double.class
		);
		Method steadyVrsMethod = DronePhysics.class.getDeclaredMethod(
				"calculateSteadyRotorVortexRingStateIntensity",
				RotorSpec.class,
				Vec3.class,
				double.class,
				double.class
		);
		envelopeMethod.setAccessible(true);
		buffetEnvelopeMethod.setAccessible(true);
		steadyVrsMethod.setAccessible(true);

		double earlyEntry = (double) envelopeMethod.invoke(null, 0.75);
		double prePeak = (double) envelopeMethod.invoke(null, 0.95);
		double peak = (double) envelopeMethod.invoke(null, 1.25);
		double highDescentRelease = (double) envelopeMethod.invoke(null, 1.85);
		double exited = (double) envelopeMethod.invoke(null, 2.30);
		double noBuffet = (double) buffetEnvelopeMethod.invoke(null, 0.0);
		double digitizedEarlyBuffet = (double) buffetEnvelopeMethod.invoke(null, 0.78);
		double digitizedPeakBuffet = (double) buffetEnvelopeMethod.invoke(null, 1.24);
		double digitizedDeepBuffet = (double) buffetEnvelopeMethod.invoke(null, 1.74);
		double digitizedExitedBuffet = (double) buffetEnvelopeMethod.invoke(null, 2.56);
		RotorSpec rotor = DroneConfig.racingQuad().rotors().get(0);
		double steadyPeak = (double) steadyVrsMethod.invoke(
				null,
				rotor,
				new Vec3(0.0, -12.5, 0.0),
				rotor.maxOmegaRadiansPerSecond(),
				10.0
		);
		double johnsonBandCrossflow = (double) steadyVrsMethod.invoke(
				null,
				rotor,
				new Vec3(8.0, -12.5, 0.0),
				rotor.maxOmegaRadiansPerSecond(),
				10.0
		);
		double crossflowEscape = (double) steadyVrsMethod.invoke(
				null,
				rotor,
				new Vec3(10.5, -12.5, 0.0),
				rotor.maxOmegaRadiansPerSecond(),
				10.0
		);

		assertTrue(earlyEntry > 0.25 && earlyEntry < 0.45, () -> "earlyEntry=" + earlyEntry);
		assertTrue(prePeak > earlyEntry * 1.8 && prePeak < 0.85, () -> "prePeak=" + prePeak);
		assertTrue(peak > 0.98, () -> "peak=" + peak);
		assertTrue(highDescentRelease > 0.25 && highDescentRelease < peak * 0.55,
				() -> "highDescentRelease=" + highDescentRelease);
		assertEquals(0.0, exited, 1.0e-9);
		assertEquals(0.0, noBuffet, 1.0e-9);
		assertTrue(digitizedEarlyBuffet > 0.30 && digitizedEarlyBuffet < 0.55,
				() -> "digitizedEarlyBuffet=" + digitizedEarlyBuffet);
		assertTrue(digitizedPeakBuffet > 0.98, () -> "digitizedPeakBuffet=" + digitizedPeakBuffet);
		assertTrue(digitizedDeepBuffet > 0.18 && digitizedDeepBuffet < digitizedPeakBuffet * 0.45,
				() -> "digitizedDeepBuffet=" + digitizedDeepBuffet);
		assertTrue(digitizedExitedBuffet < digitizedDeepBuffet * 0.30,
				() -> "digitizedExitedBuffet=" + digitizedExitedBuffet);
		assertTrue(steadyPeak > 0.98, () -> "steadyPeak=" + steadyPeak);
		assertTrue(johnsonBandCrossflow > steadyPeak * 0.70 && johnsonBandCrossflow < steadyPeak,
				() -> "johnsonBandCrossflow=" + johnsonBandCrossflow + " steadyPeak=" + steadyPeak);
		assertTrue(crossflowEscape < steadyPeak * 0.05, () -> "crossflowEscape=" + crossflowEscape);
	}

	@Test
	void vortexRingBuffetFrequencyUsesRotorRevolutionTimeScale() throws ReflectiveOperationException {
		Method buffetFrequencyMethod = DronePhysics.class.getDeclaredMethod(
				"rotorVortexRingBuffetFrequencyHertz",
				RotorSpec.class,
				double.class,
				double.class,
				double.class
		);
		buffetFrequencyMethod.setAccessible(true);

		DroneConfig config = DroneConfig.racingQuad();
		RotorSpec rotor = config.rotors().get(0);
		double hoverThrust = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double hoverOmega = Math.sqrt(hoverThrust / rotor.thrustCoefficient());
		double maxOmega = rotor.maxOmegaRadiansPerSecond();
		double hoverRevolutionsPerSecond = hoverOmega / (2.0 * Math.PI);
		double maxRevolutionsPerSecond = maxOmega / (2.0 * Math.PI);
		double hoverPeakFrequency = (double) buffetFrequencyMethod.invoke(null, rotor, hoverOmega, 1.0, 1.24);
		double maxPeakFrequency = (double) buffetFrequencyMethod.invoke(null, rotor, maxOmega, 1.0, 1.24);
		double earlyEntryFrequency = (double) buffetFrequencyMethod.invoke(null, rotor, hoverOmega, 1.0, 0.78);
		double exitedFrequency = (double) buffetFrequencyMethod.invoke(null, rotor, hoverOmega, 1.0, 2.56);
		double idleFrequency = (double) buffetFrequencyMethod.invoke(null, rotor, hoverOmega, 0.0, 1.24);

		assertTrue(hoverPeakFrequency >= hoverRevolutionsPerSecond / 50.0
						&& hoverPeakFrequency <= hoverRevolutionsPerSecond / 20.0,
				() -> "hoverPeakFrequency=" + hoverPeakFrequency);
		assertTrue(maxPeakFrequency >= maxRevolutionsPerSecond / 50.0
						&& maxPeakFrequency <= maxRevolutionsPerSecond / 20.0,
				() -> "maxPeakFrequency=" + maxPeakFrequency);
		assertTrue(hoverPeakFrequency > 4.0 && hoverPeakFrequency < 12.0,
				() -> "hoverPeakFrequency=" + hoverPeakFrequency);
		assertTrue(maxPeakFrequency > 9.0 && maxPeakFrequency < 25.0,
				() -> "maxPeakFrequency=" + maxPeakFrequency);
		assertTrue(earlyEntryFrequency >= hoverRevolutionsPerSecond / 50.0
						&& earlyEntryFrequency < hoverPeakFrequency * 0.82,
				() -> "earlyEntryFrequency=" + earlyEntryFrequency
						+ " hoverPeakFrequency=" + hoverPeakFrequency);
		assertTrue(exitedFrequency >= hoverRevolutionsPerSecond / 50.0
						&& exitedFrequency < hoverPeakFrequency * 0.55,
				() -> "exitedFrequency=" + exitedFrequency
						+ " hoverPeakFrequency=" + hoverPeakFrequency);
		assertEquals(0.0, idleFrequency, 1.0e-12);
	}

	@Test
	void vortexRingStateBuildsAndClearsWithRotorWakeLag() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics physics = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.75, 0.0, 0.0, 0.0, true);
		Vec3 verticalDescent = new Vec3(0.0, -12.0, 0.0);
		Vec3 crossflowEscape = new Vec3(16.0, -12.0, 0.0);

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(verticalDescent);
		physics.step(punch, 0.005);
		double firstVerticalVrs = physics.state().vortexRingStateIntensity();

		for (int i = 0; i < 180; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(verticalDescent);
			physics.step(punch, 0.005);
		}
		double settledVerticalVrs = physics.state().vortexRingStateIntensity();

		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(crossflowEscape);
		physics.step(punch, 0.005);
		double firstCrossflowVrs = physics.state().vortexRingStateIntensity();

		for (int i = 0; i < 220; i++) {
			physics.state().setOrientation(Quaternion.IDENTITY);
			physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			physics.state().setVelocityMetersPerSecond(crossflowEscape);
			physics.step(punch, 0.005);
		}
		double clearedCrossflowVrs = physics.state().vortexRingStateIntensity();

		assertTrue(firstVerticalVrs < settledVerticalVrs * 0.35,
				() -> "firstVerticalVrs=" + firstVerticalVrs + " settledVerticalVrs=" + settledVerticalVrs);
		assertTrue(settledVerticalVrs > 0.25, () -> "settledVerticalVrs=" + settledVerticalVrs);
		assertTrue(firstCrossflowVrs > clearedCrossflowVrs + 0.08,
				() -> "firstCrossflowVrs=" + firstCrossflowVrs + " clearedCrossflowVrs=" + clearedCrossflowVrs);
		assertTrue(clearedCrossflowVrs < 0.06, () -> "clearedCrossflowVrs=" + clearedCrossflowVrs);
	}

	@Test
	void vortexRingStateReducesThrustInRetainedDescendingWake() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 90.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics verticalDescent = new DronePhysics(config);
		DronePhysics crossflowDescent = new DronePhysics(config);
		DroneInput punch = new DroneInput(0.75, 0.0, 0.0, 0.0, true);
		double verticalMinThrust = Double.POSITIVE_INFINITY;
		double verticalMaxThrust = Double.NEGATIVE_INFINITY;
		double crossflowMinThrust = Double.POSITIVE_INFINITY;
		double crossflowMaxThrust = Double.NEGATIVE_INFINITY;
		double verticalMinForceX = Double.POSITIVE_INFINITY;
		double verticalMaxForceX = Double.NEGATIVE_INFINITY;
		double verticalMinForceZ = Double.POSITIVE_INFINITY;
		double verticalMaxForceZ = Double.NEGATIVE_INFINITY;
		double crossflowMinForceX = Double.POSITIVE_INFINITY;
		double crossflowMaxForceX = Double.NEGATIVE_INFINITY;
		double crossflowMinForceZ = Double.POSITIVE_INFINITY;
		double crossflowMaxForceZ = Double.NEGATIVE_INFINITY;

		for (int i = 0; i < 240; i++) {
			verticalDescent.state().setOrientation(Quaternion.IDENTITY);
			verticalDescent.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			verticalDescent.state().setVelocityMetersPerSecond(new Vec3(0.0, -12.0, 0.0));
			crossflowDescent.state().setOrientation(Quaternion.IDENTITY);
			crossflowDescent.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			crossflowDescent.state().setVelocityMetersPerSecond(new Vec3(16.0, -12.0, 0.0));
			verticalDescent.step(punch, 0.005);
			crossflowDescent.step(punch, 0.005);

			if (i >= 120) {
				double verticalThrust = verticalDescent.state().rotorThrustNewtons(0);
				double crossflowThrust = crossflowDescent.state().rotorThrustNewtons(0);
				verticalMinThrust = Math.min(verticalMinThrust, verticalThrust);
				verticalMaxThrust = Math.max(verticalMaxThrust, verticalThrust);
				crossflowMinThrust = Math.min(crossflowMinThrust, crossflowThrust);
				crossflowMaxThrust = Math.max(crossflowMaxThrust, crossflowThrust);
				Vec3 verticalForce = verticalDescent.state().rotorForceBodyNewtons(0);
				Vec3 crossflowForce = crossflowDescent.state().rotorForceBodyNewtons(0);
				verticalMinForceX = Math.min(verticalMinForceX, verticalForce.x());
				verticalMaxForceX = Math.max(verticalMaxForceX, verticalForce.x());
				verticalMinForceZ = Math.min(verticalMinForceZ, verticalForce.z());
				verticalMaxForceZ = Math.max(verticalMaxForceZ, verticalForce.z());
				crossflowMinForceX = Math.min(crossflowMinForceX, crossflowForce.x());
				crossflowMaxForceX = Math.max(crossflowMaxForceX, crossflowForce.x());
				crossflowMinForceZ = Math.min(crossflowMinForceZ, crossflowForce.z());
				crossflowMaxForceZ = Math.max(crossflowMaxForceZ, crossflowForce.z());
			}
		}

		double verticalThrustRange = verticalMaxThrust - verticalMinThrust;
		double crossflowThrustRange = crossflowMaxThrust - crossflowMinThrust;
		double verticalLateralForceRange = verticalMaxForceX - verticalMinForceX + verticalMaxForceZ - verticalMinForceZ;
		double crossflowLateralForceRange = crossflowMaxForceX - crossflowMinForceX + crossflowMaxForceZ - crossflowMinForceZ;
		assertTrue(verticalDescent.state().vortexRingStateIntensity() > 0.25);
		assertTrue(crossflowDescent.state().vortexRingStateIntensity() < 0.05);
		assertTrue(verticalThrustRange > crossflowThrustRange + 0.10,
				() -> "verticalThrustRange=" + verticalThrustRange + " crossflowThrustRange=" + crossflowThrustRange);
		assertTrue(verticalLateralForceRange > crossflowLateralForceRange + 0.08,
				() -> "verticalLateralForceRange=" + verticalLateralForceRange
						+ " crossflowLateralForceRange=" + crossflowLateralForceRange);
		assertTrue(verticalDescent.state().rotorVibration() > crossflowDescent.state().rotorVibration() + 0.015,
				() -> "verticalVibration=" + verticalDescent.state().rotorVibration()
						+ " crossflowVibration=" + crossflowDescent.state().rotorVibration());
	}

	@Test
	void vortexRingStatePeakLossMatchesSmallPropReferenceBand() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics verticalDescent = new DronePhysics(config);
		DronePhysics crossflowEscape = new DronePhysics(config);
		DroneInput fullThrottle = new DroneInput(1.0, 0.0, 0.0, 0.0, true);

		for (int i = 0; i < 320; i++) {
			double inducedVelocity = Math.max(
					9.0,
					verticalDescent.state().averageRotorInducedVelocityMetersPerSecond()
			);
			double descentSpeed = inducedVelocity * 1.20;
			double escapeCrossflowSpeed = inducedVelocity * 1.45;
			verticalDescent.state().setOrientation(Quaternion.IDENTITY);
			verticalDescent.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			verticalDescent.state().setVelocityMetersPerSecond(new Vec3(0.0, -descentSpeed, 0.0));
			crossflowEscape.state().setOrientation(Quaternion.IDENTITY);
			crossflowEscape.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			crossflowEscape.state().setVelocityMetersPerSecond(new Vec3(escapeCrossflowSpeed, -descentSpeed, 0.0));
			verticalDescent.step(fullThrottle, 0.005);
			crossflowEscape.step(fullThrottle, 0.005);
		}

		double meanLossFraction = DronePhysics.rotorVortexRingMeanThrustLoss(
				config.rotors().get(0),
				verticalDescent.state().vortexRingStateIntensity()
		);
		assertTrue(verticalDescent.state().vortexRingStateIntensity() > 0.82,
				() -> "vrs=" + verticalDescent.state().vortexRingStateIntensity());
		assertTrue(crossflowEscape.state().vortexRingStateIntensity() < 0.05,
				() -> "crossflowVrs=" + crossflowEscape.state().vortexRingStateIntensity());
		assertTrue(meanLossFraction > 0.24 && meanLossFraction < 0.40,
				() -> "meanLossFraction=" + meanLossFraction
						+ " verticalVrs=" + verticalDescent.state().vortexRingStateIntensity());
	}

	@Test
	void vortexRingStateBuffetApproachesSmallPropTimeHistoryBand() {
		DroneConfig config = directControl(DroneConfig.racingQuad())
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withLinearDragCoefficient(0.0)
				.withBodyDragCoefficients(Vec3.ZERO)
				.withRotorDiskDragCoefficient(0.0)
				.withRotorFlappingCoefficient(0.0)
				.withRotorTransverseFlowLiftCoefficient(0.0)
				.withMotorTimeConstantSeconds(0.005)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.0)
				.withBattery(16.8, 16.7, 0.0, 20.0, 160.0)
				.withMotorThermal(0.0, 0.0, 200.0, 240.0);
		DronePhysics verticalDescent = new DronePhysics(config);
		DronePhysics crossflowEscape = new DronePhysics(config);
		DroneInput fullThrottle = new DroneInput(1.0, 0.0, 0.0, 0.0, true);
		double verticalMinThrust = Double.POSITIVE_INFINITY;
		double verticalMaxThrust = Double.NEGATIVE_INFINITY;
		double verticalThrustSum = 0.0;
		double crossflowMinThrust = Double.POSITIVE_INFINITY;
		double crossflowMaxThrust = Double.NEGATIVE_INFINITY;
		double crossflowThrustSum = 0.0;
		double verticalMaxTelemetryAmplitude = 0.0;
		double crossflowMaxTelemetryAmplitude = 0.0;
		double verticalMaxTelemetryForce = 0.0;
		double crossflowMaxTelemetryForce = 0.0;
		int samples = 0;

		for (int i = 0; i < 420; i++) {
			double inducedVelocity = Math.max(
					9.0,
					verticalDescent.state().averageRotorInducedVelocityMetersPerSecond()
			);
			double descentSpeed = inducedVelocity * 1.20;
			double escapeCrossflowSpeed = inducedVelocity * 1.45;
			verticalDescent.state().setOrientation(Quaternion.IDENTITY);
			verticalDescent.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			verticalDescent.state().setVelocityMetersPerSecond(new Vec3(0.0, -descentSpeed, 0.0));
			crossflowEscape.state().setOrientation(Quaternion.IDENTITY);
			crossflowEscape.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
			crossflowEscape.state().setVelocityMetersPerSecond(new Vec3(escapeCrossflowSpeed, -descentSpeed, 0.0));
			verticalDescent.step(fullThrottle, 0.005);
			crossflowEscape.step(fullThrottle, 0.005);

			if (i >= 240) {
				double verticalThrust = verticalDescent.state().rotorThrustNewtons(0);
				double crossflowThrust = crossflowEscape.state().rotorThrustNewtons(0);
				verticalMinThrust = Math.min(verticalMinThrust, verticalThrust);
				verticalMaxThrust = Math.max(verticalMaxThrust, verticalThrust);
				verticalThrustSum += verticalThrust;
				crossflowMinThrust = Math.min(crossflowMinThrust, crossflowThrust);
				crossflowMaxThrust = Math.max(crossflowMaxThrust, crossflowThrust);
				crossflowThrustSum += crossflowThrust;
				verticalMaxTelemetryAmplitude = Math.max(
						verticalMaxTelemetryAmplitude,
						verticalDescent.state().maxVortexRingThrustBuffetAmplitude()
				);
				crossflowMaxTelemetryAmplitude = Math.max(
						crossflowMaxTelemetryAmplitude,
						crossflowEscape.state().maxVortexRingThrustBuffetAmplitude()
				);
				verticalMaxTelemetryForce = Math.max(
						verticalMaxTelemetryForce,
						verticalDescent.state().vortexRingBuffetForceBodyNewtons().length()
				);
				crossflowMaxTelemetryForce = Math.max(
						crossflowMaxTelemetryForce,
						crossflowEscape.state().vortexRingBuffetForceBodyNewtons().length()
				);
				samples++;
			}
		}

		double verticalMeanThrust = verticalThrustSum / samples;
		double crossflowMeanThrust = crossflowThrustSum / samples;
		double verticalHalfAmplitude = (verticalMaxThrust - verticalMinThrust) / (2.0 * verticalMeanThrust);
		double crossflowHalfAmplitude = (crossflowMaxThrust - crossflowMinThrust) / (2.0 * crossflowMeanThrust);
		double observedVerticalMinThrust = verticalMinThrust;
		double observedVerticalMaxThrust = verticalMaxThrust;
		double observedVerticalMaxTelemetryAmplitude = verticalMaxTelemetryAmplitude;
		double observedCrossflowMaxTelemetryAmplitude = crossflowMaxTelemetryAmplitude;
		double observedVerticalMaxTelemetryForce = verticalMaxTelemetryForce;
		double observedCrossflowMaxTelemetryForce = crossflowMaxTelemetryForce;
		assertTrue(verticalDescent.state().vortexRingStateIntensity() > 0.82,
				() -> "vrs=" + verticalDescent.state().vortexRingStateIntensity());
		assertTrue(crossflowEscape.state().vortexRingStateIntensity() < 0.05,
				() -> "crossflowVrs=" + crossflowEscape.state().vortexRingStateIntensity());
		// Shetty/Selig small fixed-pitch prop histories report about +/-30% at the strongest VRS point.
		// The default 5-inch FPV preset now tracks the lower half of that text anchor while staying below
		// the larger low-precision measured-limit digitization envelope.
		assertTrue(verticalHalfAmplitude > 0.17 && verticalHalfAmplitude < 0.34,
				() -> "verticalHalfAmplitude=" + verticalHalfAmplitude
						+ " verticalMinThrust=" + observedVerticalMinThrust
						+ " verticalMaxThrust=" + observedVerticalMaxThrust
						+ " verticalMeanThrust=" + verticalMeanThrust);
		assertTrue(crossflowHalfAmplitude < verticalHalfAmplitude * 0.55,
				() -> "verticalHalfAmplitude=" + verticalHalfAmplitude
						+ " crossflowHalfAmplitude=" + crossflowHalfAmplitude);
		assertTrue(observedVerticalMaxTelemetryAmplitude > 0.18 && observedVerticalMaxTelemetryAmplitude < 0.31,
				() -> "verticalMaxTelemetryAmplitude=" + observedVerticalMaxTelemetryAmplitude);
		assertTrue(observedCrossflowMaxTelemetryAmplitude < observedVerticalMaxTelemetryAmplitude * 0.30,
				() -> "verticalMaxTelemetryAmplitude=" + observedVerticalMaxTelemetryAmplitude
						+ " crossflowMaxTelemetryAmplitude=" + observedCrossflowMaxTelemetryAmplitude);
		assertTrue(observedVerticalMaxTelemetryForce > observedCrossflowMaxTelemetryForce + 0.02,
				() -> "verticalMaxTelemetryForce=" + observedVerticalMaxTelemetryForce
						+ " crossflowMaxTelemetryForce=" + observedCrossflowMaxTelemetryForce);
		assertTrue(verticalDescent.state().rotorVibration() > crossflowEscape.state().rotorVibration() + 0.04,
				() -> "verticalVibration=" + verticalDescent.state().rotorVibration()
						+ " crossflowVibration=" + crossflowEscape.state().rotorVibration());
	}

	@Test
	void contactDynamicsSeparatesWallImpactFromTangentialSlip() {
		ContactDynamics.Response response = ContactDynamics.resolve(
				new Vec3(6.0, 1.0, 7.0),
				new Vec3(0.30, 0.0, 0.20),
				new Vec3(0.02, 0.0, 0.20),
				true,
				false
		);

		assertEquals(6.0, response.impactSpeedMetersPerSecond(), 0.05);
		assertTrue(response.contactNormalWorld().x() < -0.90);
		assertTrue(response.velocityMetersPerSecond().x() < -0.20);
		assertTrue(response.velocityMetersPerSecond().z() > 4.0);
		assertTrue(response.slipSpeedMetersPerSecond() > 5.0);
		assertTrue(response.bounceSpeedMetersPerSecond() > 0.20);
	}

	@Test
	void contactDynamicsDampsHardLandingWithSurfaceFriction() {
		ContactDynamics.Response response = ContactDynamics.resolve(
				new Vec3(4.0, -8.0, 0.0),
				new Vec3(0.20, -0.40, 0.0),
				new Vec3(0.20, 0.0, 0.0),
				false,
				true
		);

		assertEquals(8.0, response.impactSpeedMetersPerSecond(), 0.05);
		assertTrue(response.velocityMetersPerSecond().y() > 0.0);
		assertTrue(response.velocityMetersPerSecond().y() < 1.8);
		assertTrue(Math.abs(response.velocityMetersPerSecond().x()) < 2.0);
		assertEquals(4.0, response.slipSpeedMetersPerSecond(), 0.05);
	}

	@Test
	void contactSurfaceFrictionChangesSlideDistance() {
		ContactDynamics.Response ice = ContactDynamics.resolve(
				new Vec3(7.0, -6.0, 0.0),
				new Vec3(0.35, -0.30, 0.0),
				new Vec3(0.35, 0.0, 0.0),
				false,
				true,
				new ContactDynamics.ContactSurface(0.20, 0.80, 0.60)
		);
		ContactDynamics.Response sticky = ContactDynamics.resolve(
				new Vec3(7.0, -6.0, 0.0),
				new Vec3(0.35, -0.30, 0.0),
				new Vec3(0.35, 0.0, 0.0),
				false,
				true,
				new ContactDynamics.ContactSurface(1.90, 0.45, 1.30)
		);

		assertTrue(Math.abs(ice.velocityMetersPerSecond().x()) > Math.abs(sticky.velocityMetersPerSecond().x()) + 3.0);
		assertTrue(sticky.velocityMetersPerSecond().y() < ice.velocityMetersPerSecond().y());
	}

	@Test
	void contactSurfaceRestitutionChangesBounce() {
		ContactDynamics.Response slime = ContactDynamics.resolve(
				new Vec3(0.0, -7.0, 0.0),
				new Vec3(0.0, -0.35, 0.0),
				Vec3.ZERO,
				false,
				true,
				new ContactDynamics.ContactSurface(0.85, 2.20, 0.65)
		);
		ContactDynamics.Response damped = ContactDynamics.resolve(
				new Vec3(0.0, -7.0, 0.0),
				new Vec3(0.0, -0.35, 0.0),
				Vec3.ZERO,
				false,
				true,
				new ContactDynamics.ContactSurface(1.20, 0.35, 1.20)
		);

		assertTrue(slime.velocityMetersPerSecond().y() > damped.velocityMetersPerSecond().y() + 0.80);
		assertTrue(slime.bounceSpeedMetersPerSecond() > damped.bounceSpeedMetersPerSecond() + 0.80);
	}

	@Test
	void contactSurfaceTelemetryRecordsMaterialMultipliers() {
		DroneState state = new DroneState(4);
		ContactDynamics.ContactSurface metal = new ContactDynamics.ContactSurface(0.72, 1.08, 1.70);

		state.setContactTelemetry(5.0, 3.0, 0.6, Vec3.ZERO, metal);

		assertEquals(0.72, state.contactSurfaceFrictionMultiplier(), 1.0e-9);
		assertEquals(1.08, state.contactSurfaceRestitutionMultiplier(), 1.0e-9);
		assertEquals(1.70, state.contactSurfaceScrapeMultiplier(), 1.0e-9);

		state.setContactTelemetry(0.0, 0.0, 0.0);

		assertEquals(1.0, state.contactSurfaceFrictionMultiplier(), 1.0e-9);
		assertEquals(1.0, state.contactSurfaceRestitutionMultiplier(), 1.0e-9);
		assertEquals(1.0, state.contactSurfaceScrapeMultiplier(), 1.0e-9);
	}

	@Test
	void contactAngularImpulseUsesRotorArmAndFrameInertia() {
		DroneConfig config = DroneConfig.racingQuad();
		ContactDynamics.Response response = ContactDynamics.resolve(
				new Vec3(6.0, 0.0, 2.5),
				new Vec3(0.30, 0.0, 0.12),
				new Vec3(0.02, 0.0, 0.12),
				true,
				false
		);

		Vec3 impulse = ContactDynamics.angularVelocityImpulseBody(
				config,
				Quaternion.IDENTITY,
				new Vec3(6.0, 0.0, 2.5),
				response.contactNormalWorld(),
				response.impactSpeedMetersPerSecond(),
				response.slipSpeedMetersPerSecond()
		);

		assertTrue(impulse.length() > 2.0);
		assertTrue(Math.abs(impulse.y()) > 1.0);
	}

	@Test
	void centeredLevelLandingDoesNotInventAngularImpulse() {
		DroneConfig config = DroneConfig.racingQuad();
		ContactDynamics.Response response = ContactDynamics.resolve(
				new Vec3(0.0, -8.0, 0.0),
				new Vec3(0.0, -0.40, 0.0),
				Vec3.ZERO,
				false,
				true
		);

		Vec3 impulse = ContactDynamics.angularVelocityImpulseBody(
				config,
				Quaternion.IDENTITY,
				new Vec3(0.0, -8.0, 0.0),
				response.contactNormalWorld(),
				response.impactSpeedMetersPerSecond(),
				response.slipSpeedMetersPerSecond()
		);

		assertEquals(0.0, impulse.length(), 1.0e-9);
	}

	@Test
	void offlineFlightRecorderWritesCsvWithDynamicEffects(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("racing_quad.csv");
		OfflineFlightRecorder.FlightReport report = OfflineFlightRecorder.record("racing_quad", output);
		List<String> lines = Files.readAllLines(output);
		String[] header = lines.get(0).split(",", -1);
		String[] firstRow = lines.get(1).split(",", -1);
		RotorSpec offlineRotor = OfflineFlightRecorder.preset("racing_quad").rotors().get(0);
		int columnCount = header.length;

		assertTrue(Files.exists(output));
		assertTrue(lines.size() > 200);
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_pitch_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_lift_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_body_drag_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("linear_damping_drag_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_drag_along_flow_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_drag_equivalent_linear_k"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_drag_equivalent_cda_m2"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_drag_imav_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("ground_effect_drag_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("ground_effect_leveling_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_wash_drag_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_wall_effect_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("barometer_altitude_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("barometer_vertical_speed_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("barometer_pressure_hpa"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("barometer_error_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("barometer_sensor_noise_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("barometer_pressure_port_error_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("barometer_propwash_error_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("max_esc_temp_c"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("esc_thermal_limit"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_esc_cooling_factor"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_induced_velocity_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("min_induced_lag_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_dynamic_inflow_tau_s"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_dynamic_inflow_tau_s"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_force_x_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_force_z_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_torque_x_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_torque_z_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_arm_flex"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_arm_flex"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_arm_flex_deflection_mm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_arm_flex_deflection_mm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_arm_flex_tilt_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_arm_flex_tilt_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_translational_lift"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_advance_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_advance_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_prop_advance_ratio_j"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_prop_advance_ratio_j"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_prop_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_prop_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_prop_power_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_prop_power_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_axial_gust_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_axial_gust_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_reverse_flow_fraction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_reverse_flow_fraction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_tip_mach"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_tip_mach"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_compressibility_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_compressibility_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_reynolds_number"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_reynolds_number"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_reynolds_index"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_reynolds_index"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_low_reynolds_loss"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_low_reynolds_loss"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_aerodynamic_load"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_in_plane_drag_force_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_in_plane_drag_force_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_in_plane_drag_force_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_inflow_skew"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_wake_interference"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_wake_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_wake_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_blade_dissymmetry_pitch_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_blade_dissymmetry_yaw_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_blade_dissymmetry_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_skew_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_wake_swirl_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_inertia_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_acceleration_reaction_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_gyroscopic_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_active_braking_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_flapping_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_angular_drag_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_angular_drag_roll_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_transient_sag_v"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_slow_polarization_v"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_effective_resistance_ohm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_resistance_aging_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_capacity_aging_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_soc_resistance_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_temp_resistance_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_equivalent_cycles"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_regen_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_voltage_spike_v"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_20pct_sag_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_20pct_sag_current_margin"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_current_limit"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mqtb_hq5x4x3_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mqtb_hq5x4x3_power_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mqtb_hq5x4x3_current_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mqtb_hq5x4x3_current_residual_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("apdrone_pdf5045_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("apdrone_pdf5045_power_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("apdrone_pdf5045_current_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("apdrone_pdf5045_current_residual_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("imu_supply_noise"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("effective_wind_x_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("effective_wind_y_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("effective_wind_z_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_gust_speed_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_dryden_speed_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_burble_speed_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_a4mc_source_gust_speed_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_a4mc_source_gust_x_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_a4mc_source_gust_y_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_a4mc_source_gust_z_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_a4mc_updraft_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_a4mc_terrain_shear_speed_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_source_turbulence"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_source_quality"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("wind_shear_accel_mps2"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("contact_impact_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("contact_slip_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("contact_bounce_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("contact_surface_friction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("contact_surface_restitution"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("contact_surface_scrape"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("contact_angular_impulse_dps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_cooling_factor"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_rpm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_erpm100"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_einterval_us"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_rpm_telemetry_valid"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_erpm100"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_einterval_us"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_rpm_telemetry_valid"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_target_rpm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_target_erpm100"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_target_einterval_us"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_target_rpm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_target_erpm100"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_target_einterval_us"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_tracking_error"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_tracking_error"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_actuator_authority"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_actuator_authority"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_accel_rad_s2"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_aero_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_commutation_ripple"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_commutation_ripple"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_regen_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_regen_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_phase_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_phase_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_current_ripple_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_current_ripple_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_torque_ripple_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_torque_ripple_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_bus_ripple_v"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_temp_c"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_cooling_factor"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_thermal_limit"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("battery_polarization_resistance_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_mechanical_loss_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_0_mechanical_loss_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_motor_shaft_power_w"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_electrical_efficiency"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_0_electrical_efficiency"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_voltage_headroom"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_0_voltage_headroom"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_winding_resistance_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_winding_resistance_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("angle_of_attack_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_separation"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_stall_intensity"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_surface_scrape"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_pressure_center_pitch_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("turbulence_intensity"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("obstacle_proximity"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("effective_air_density_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("water_immersion"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("precipitation_wetness"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_coaxial_load_bias"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_coaxial_load_bias"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_coaxial_load_bias"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_coaxial_allocation_uncertainty_pct"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_health"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_3_health"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_wet_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_wet_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_wet_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_water_immersion"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_water_immersion"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_precipitation_wetness"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_precipitation_wetness"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_env_thrust_multiplier"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_env_thrust_multiplier"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_flow_obstruction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_flow_obstruction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_disk_wind_gradient_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_disk_wind_gradient_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_a4mc_shelter_obstruction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_a4mc_shelter_obstruction"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_blade_aoa_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_blade_aoa_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_blade_element_stall"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_blade_element_stall"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_blade_dissymmetry"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_blade_dissymmetry"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_blade_pass_ripple"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_blade_pass_ripple"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_flapping_tilt_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_flapping_tilt_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_coning"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_coning"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_coning"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_coning_angle_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_coning_angle_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_coning_angle_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_windmilling"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_windmilling"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_windmilling"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("flight_mode"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("control_frame_age_s"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("control_frame_error"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("esc_command_frame_age_s"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("esc_command_frame_interval_s"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("esc_command_error"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("avg_esc_electrical_output"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("max_esc_electrical_error"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("esc_7_electrical_output"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rc_frame_rate_hz"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rc_resolution_steps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_esc_command_frame_rate_hz"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_esc_command_resolution_steps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_esc_dshot_bitrate_kbit_s"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_esc_dshot_raw_frame_us"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_esc_dshot_wire_utilization"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_esc_command_interval_raw_frame_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rotor_blade_pitch_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rotor_pitch_to_diameter"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rotor_pitch_angle_70r_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rotor_chord_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rotor_chord_to_radius"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rotor_blade_count"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_motor_pole_pairs"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rotor_imbalance"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_cg_x_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_cg_y_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_cg_z_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_imu_x_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_imu_y_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_imu_z_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_cp_x_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_cp_y_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_cp_z_m"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_rotor_outward_cant_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("self_level_blend"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("pid_integral_relax"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("pid_integral_relax_pitch"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("pid_integral_relax_yaw"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("pid_integral_relax_roll"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("pid_dterm_lpf_hz"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_iterm_relax"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_level_angle_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("tune_horizon_end"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_vibration"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_damage_vibration"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_damage_vibration"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_notch_hz"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_blade_pass_notch_hz"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_blade_pass_alias_1024_hz"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_blade_pass_alias_4000_hz"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_blade_pass_notch_attenuation"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_notch_spread_hz"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_rpm_harmonic_notch_attenuation"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_blade_pass_notch_spread_hz"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_bias_pitch_dps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("gyro_clip"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("accel_bias_x_mps2"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("accel_clip"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("propwash_wake_intensity"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("vortex_ring_state"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("vortex_ring_thrust_buffet"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("vortex_ring_max_thrust_buffet"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("vortex_ring_buffet_force_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_output_pitch_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_output_yaw_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_output_roll_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_pitch_authority"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_yaw_authority"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_roll_authority"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_min_axis_authority"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_low_saturation"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_high_saturation"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_low_headroom"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("mixer_high_headroom"));
		assertEquals(columnCount, firstRow.length);
		double loggedInducedLagThrustScale = Double.parseDouble(firstRow[indexOf(header, "min_induced_lag_thrust_scale")]);
		assertTrue(loggedInducedLagThrustScale >= 0.65);
		assertTrue(loggedInducedLagThrustScale <= 1.0);
		double loggedDynamicInflowTau = Double.parseDouble(firstRow[indexOf(header, "rotor_dynamic_inflow_tau_s")]);
		double loggedRotor7DynamicInflowTau = Double.parseDouble(firstRow[indexOf(header, "rotor_7_dynamic_inflow_tau_s")]);
		assertTrue(loggedDynamicInflowTau >= 0.0);
		assertTrue(loggedDynamicInflowTau <= 0.36);
		assertTrue(loggedRotor7DynamicInflowTau >= 0.0);
		assertTrue(loggedRotor7DynamicInflowTau <= 0.36);
		double loggedWakeThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_wake_thrust_scale")]);
		double loggedRotor7WakeThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_7_wake_thrust_scale")]);
		assertTrue(loggedWakeThrustScale >= 0.72);
		assertTrue(loggedWakeThrustScale <= 1.0);
		assertTrue(loggedRotor7WakeThrustScale >= 0.72);
		assertTrue(loggedRotor7WakeThrustScale <= 1.0);
		double loggedWetThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_wet_thrust_scale")]);
		double loggedRotor7WetThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_7_wet_thrust_scale")]);
		assertTrue(loggedWetThrustScale >= 0.08);
		assertTrue(loggedWetThrustScale <= 1.0);
		assertTrue(loggedRotor7WetThrustScale >= 0.08);
		assertTrue(loggedRotor7WetThrustScale <= 1.0);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_blade_dissymmetry_pitch_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_blade_dissymmetry_yaw_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_blade_dissymmetry_roll_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_wake_swirl_pitch_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_wake_swirl_yaw_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_wake_swirl_roll_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_active_braking_pitch_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_active_braking_yaw_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_active_braking_roll_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_acceleration_reaction_pitch_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_acceleration_reaction_yaw_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_acceleration_reaction_roll_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_flapping_pitch_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_flapping_yaw_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_flapping_roll_torque_nm")])));
		double maxBladeDissymmetryTorque = maxVectorLength(
				lines,
				header,
				"rotor_blade_dissymmetry_pitch_torque_nm",
				"rotor_blade_dissymmetry_yaw_torque_nm",
				"rotor_blade_dissymmetry_roll_torque_nm"
		);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "mixer_output_pitch_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "mixer_output_yaw_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "mixer_output_roll_nm")])));
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_pitch_authority")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_pitch_authority")]) <= 1.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_yaw_authority")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_yaw_authority")]) <= 1.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_roll_authority")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_roll_authority")]) <= 1.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_min_axis_authority")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_min_axis_authority")]) <= 1.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_low_saturation")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_low_saturation")]) <= 1.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_high_saturation")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_high_saturation")]) <= 1.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_low_headroom")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_low_headroom")]) <= 1.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_high_headroom")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "mixer_high_headroom")]) <= 1.0);
		double loggedAirDensity = Double.parseDouble(firstRow[indexOf(header, "air_density_ratio")]);
		double loggedEffectiveAirDensity = Double.parseDouble(firstRow[indexOf(header, "effective_air_density_ratio")]);
		assertTrue(Double.isFinite(loggedEffectiveAirDensity));
		assertTrue(loggedEffectiveAirDensity <= loggedAirDensity + 1.0e-6);
		double loggedAirframeSeparation = Double.parseDouble(firstRow[indexOf(header, "airframe_separation")]);
		assertTrue(loggedAirframeSeparation >= 0.0);
		assertTrue(loggedAirframeSeparation <= 1.0);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "airframe_body_drag_n")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "linear_damping_drag_n")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "airframe_drag_along_flow_n")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "airframe_drag_equivalent_linear_k")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "airframe_drag_equivalent_cda_m2")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "airframe_drag_imav_ratio")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "ground_effect_leveling_torque_nm")])));
		assertEquals(
				maxColumn(lines, header, "ground_effect_leveling_torque_nm"),
				report.maxGroundEffectLevelingTorqueNewtonMeters(),
				1.0e-5
		);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "barometer_sensor_noise_m")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "barometer_pressure_port_error_m")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "barometer_propwash_error_m")])));
		assertTrue(maxAbsColumn(lines, header, "barometer_pressure_port_error_m") > 0.25);
		assertTrue(maxAbsColumn(lines, header, "barometer_propwash_error_m") > 0.10);
		assertTrue(maxAbsColumnDifference(lines, header, "barometer_pressure_port_error_m", "barometer_propwash_error_m") > 0.25);
		assertEquals(
				maxAbsColumn(lines, header, "barometer_pressure_port_error_m"),
				report.maxBarometerPressurePortErrorMeters(),
				1.0e-5
		);
		assertEquals(
				maxAbsColumn(lines, header, "barometer_propwash_error_m"),
				report.maxBarometerPropwashErrorMeters(),
				1.0e-5
		);
		assertEquals(1.0, Double.parseDouble(firstRow[indexOf(header, "contact_surface_friction")]), 1.0e-9);
		assertEquals(1.0, Double.parseDouble(firstRow[indexOf(header, "contact_surface_restitution")]), 1.0e-9);
		assertEquals(1.0, Double.parseDouble(firstRow[indexOf(header, "contact_surface_scrape")]), 1.0e-9);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "pid_integral_relax_pitch")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "pid_integral_relax_pitch")]) <= 1.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "pid_integral_relax_yaw")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "pid_integral_relax_yaw")]) <= 1.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "pid_integral_relax_roll")]) >= 0.0);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "pid_integral_relax_roll")]) <= 1.0);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "esc_command_frame_age_s")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "esc_command_frame_interval_s")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "esc_command_error")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "avg_esc_electrical_output")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "max_esc_electrical_error")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "esc_7_electrical_output")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "tune_esc_command_frame_rate_hz")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "tune_esc_command_resolution_steps")])));
		assertEquals(600.0, Double.parseDouble(firstRow[indexOf(header, "tune_esc_dshot_bitrate_kbit_s")]), 1.0e-9);
		assertEquals(26.667, Double.parseDouble(firstRow[indexOf(header, "tune_esc_dshot_raw_frame_us")]), 0.001);
		assertEquals(0.01067, Double.parseDouble(firstRow[indexOf(header, "tune_esc_dshot_wire_utilization")]), 0.00001);
		assertEquals(93.75, Double.parseDouble(firstRow[indexOf(header, "tune_esc_command_interval_raw_frame_ratio")]), 0.01);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "tune_rotor_blade_pitch_m")])));
		assertEquals(
				offlineRotor.bladePitchToDiameterRatio(),
				Double.parseDouble(firstRow[indexOf(header, "tune_rotor_pitch_to_diameter")]),
				0.00001
		);
		assertEquals(
				Math.toDegrees(offlineRotor.geometricBladePitchAngleRadians()),
				Double.parseDouble(firstRow[indexOf(header, "tune_rotor_pitch_angle_70r_deg")]),
				0.02
		);
		assertEquals(
				offlineRotor.representativeBladeChordMeters(),
				Double.parseDouble(firstRow[indexOf(header, "tune_rotor_chord_m")]),
				0.00001
		);
		assertEquals(
				offlineRotor.representativeBladeChordToRadiusRatio(),
				Double.parseDouble(firstRow[indexOf(header, "tune_rotor_chord_to_radius")]),
				0.00001
		);
		assertEquals(3.0, Double.parseDouble(firstRow[indexOf(header, "tune_rotor_blade_count")]), 0.0001);
		assertEquals(RotorSpec.DEFAULT_MOTOR_POLE_PAIRS, Double.parseDouble(firstRow[indexOf(header, "tune_motor_pole_pairs")]), 0.0001);
		assertTrue(Double.parseDouble(firstRow[indexOf(header, "tune_rotor_imbalance")]) >= 0.0);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "tune_cg_x_m")])));
		assertEquals(0.0, Double.parseDouble(firstRow[indexOf(header, "tune_cg_z_m")]), 0.0001);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "tune_imu_x_m")])));
		assertEquals(0.0, Double.parseDouble(firstRow[indexOf(header, "tune_imu_z_m")]), 0.0001);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "tune_cp_x_m")])));
		assertEquals(0.0, Double.parseDouble(firstRow[indexOf(header, "tune_cp_z_m")]), 0.0001);
		assertEquals(0.0, Double.parseDouble(firstRow[indexOf(header, "tune_rotor_outward_cant_deg")]), 0.0001);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "airframe_pressure_center_pitch_torque_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_0_force_y_n")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_force_z_n")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_0_torque_y_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_torque_z_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_arm_flex")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_arm_flex")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_arm_flex_deflection_mm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_arm_flex_deflection_mm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_arm_flex_tilt_deg")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_arm_flex_tilt_deg")])));
		double loggedRotorAdvanceRatio = Double.parseDouble(firstRow[indexOf(header, "rotor_advance_ratio")]);
		double loggedRotorPropAdvanceRatioJ = Double.parseDouble(firstRow[indexOf(header, "rotor_prop_advance_ratio_j")]);
		assertEquals(Math.PI * loggedRotorAdvanceRatio, loggedRotorPropAdvanceRatioJ, 1.0e-4);
		double loggedRotor0AdvanceRatio = Double.parseDouble(firstRow[indexOf(header, "rotor_0_advance_ratio")]);
		double loggedRotor0PropAdvanceRatioJ = Double.parseDouble(firstRow[indexOf(header, "rotor_0_prop_advance_ratio_j")]);
		assertEquals(Math.PI * loggedRotor0AdvanceRatio, loggedRotor0PropAdvanceRatioJ, 1.0e-4);
		double loggedRotorPropellerThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_prop_thrust_scale")]);
		double loggedRotor7PropellerThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_7_prop_thrust_scale")]);
		assertTrue(loggedRotorPropellerThrustScale > 0.0 && loggedRotorPropellerThrustScale <= 1.08);
		assertTrue(loggedRotor7PropellerThrustScale > 0.0 && loggedRotor7PropellerThrustScale <= 1.08);
		double loggedRotorPropellerPowerScale = Double.parseDouble(firstRow[indexOf(header, "rotor_prop_power_scale")]);
		double loggedRotor7PropellerPowerScale = Double.parseDouble(firstRow[indexOf(header, "rotor_7_prop_power_scale")]);
		assertTrue(loggedRotorPropellerPowerScale > 0.0 && loggedRotorPropellerPowerScale <= 1.08);
		assertTrue(loggedRotor7PropellerPowerScale > 0.0 && loggedRotor7PropellerPowerScale <= 1.08);
		double loggedRotorAxialGustThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_axial_gust_thrust_scale")]);
		double loggedRotor7AxialGustThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_7_axial_gust_thrust_scale")]);
		assertTrue(loggedRotorAxialGustThrustScale > 0.0 && loggedRotorAxialGustThrustScale <= 2.5);
		assertTrue(loggedRotor7AxialGustThrustScale > 0.0 && loggedRotor7AxialGustThrustScale <= 2.5);
		double loggedRotorReverseFlow = Double.parseDouble(firstRow[indexOf(header, "rotor_reverse_flow_fraction")]);
		double loggedRotor7ReverseFlow = Double.parseDouble(firstRow[indexOf(header, "rotor_7_reverse_flow_fraction")]);
		assertTrue(loggedRotorReverseFlow >= 0.0);
		assertTrue(loggedRotorReverseFlow <= 1.0);
		assertTrue(loggedRotor7ReverseFlow >= 0.0);
		assertTrue(loggedRotor7ReverseFlow <= 1.0);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_tip_mach")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_tip_mach")])));
		double loggedRotorCompressibilityThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_compressibility_thrust_scale")]);
		double loggedRotor7CompressibilityThrustScale = Double.parseDouble(firstRow[indexOf(header, "rotor_7_compressibility_thrust_scale")]);
		assertTrue(loggedRotorCompressibilityThrustScale >= 0.0);
		assertTrue(loggedRotorCompressibilityThrustScale <= 1.0);
		assertTrue(loggedRotor7CompressibilityThrustScale >= 0.0);
		assertTrue(loggedRotor7CompressibilityThrustScale <= 1.0);
		double loggedRotorReynoldsNumber = Double.parseDouble(firstRow[indexOf(header, "rotor_reynolds_number")]);
		double loggedRotor7ReynoldsNumber = Double.parseDouble(firstRow[indexOf(header, "rotor_7_reynolds_number")]);
		assertTrue(Double.isFinite(loggedRotorReynoldsNumber));
		assertTrue(loggedRotorReynoldsNumber >= 0.0);
		assertTrue(Double.isFinite(loggedRotor7ReynoldsNumber));
		assertTrue(loggedRotor7ReynoldsNumber >= 0.0);
		double loggedRotorReynoldsIndex = Double.parseDouble(firstRow[indexOf(header, "rotor_reynolds_index")]);
		double loggedRotor7ReynoldsIndex = Double.parseDouble(firstRow[indexOf(header, "rotor_7_reynolds_index")]);
		assertTrue(Double.isFinite(loggedRotorReynoldsIndex));
		assertTrue(loggedRotorReynoldsIndex >= 0.0);
		assertTrue(Double.isFinite(loggedRotor7ReynoldsIndex));
		assertTrue(loggedRotor7ReynoldsIndex >= 0.0);
		double loggedRotorLowReynoldsLoss = Double.parseDouble(firstRow[indexOf(header, "rotor_low_reynolds_loss")]);
		double loggedRotor7LowReynoldsLoss = Double.parseDouble(firstRow[indexOf(header, "rotor_7_low_reynolds_loss")]);
		assertTrue(loggedRotorLowReynoldsLoss >= 0.0);
		assertTrue(loggedRotorLowReynoldsLoss <= 1.0);
		assertTrue(loggedRotor7LowReynoldsLoss >= 0.0);
		assertTrue(loggedRotor7LowReynoldsLoss <= 1.0);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_blade_aoa_deg")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_blade_aoa_deg")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_blade_element_stall")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_blade_element_stall")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_blade_dissymmetry")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_blade_dissymmetry")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_blade_pass_ripple")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_blade_pass_ripple")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_flapping_tilt_deg")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_flapping_tilt_deg")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_coning")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_0_coning")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_coning")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_coning_angle_deg")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_0_coning_angle_deg")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_coning_angle_deg")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_windmilling")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_0_windmilling")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_windmilling")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_damage_vibration")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "rotor_7_damage_vibration")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "gyro_blade_pass_notch_hz")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "gyro_blade_pass_alias_1024_hz")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "gyro_blade_pass_alias_4000_hz")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "gyro_blade_pass_notch_attenuation")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "gyro_notch_spread_hz")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "gyro_rpm_harmonic_notch_attenuation")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "gyro_blade_pass_notch_spread_hz")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_commutation_ripple")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_commutation_ripple")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_regen_current_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_regen_current_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_phase_current_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_phase_current_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_current_ripple_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_current_ripple_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_torque_ripple_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_torque_ripple_nm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_winding_resistance_scale")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_winding_resistance_scale")])));
		double firstAvgTelemetryValidity = Double.parseDouble(firstRow[indexOf(header, "avg_motor_rpm_telemetry_valid")]);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "avg_motor_erpm100")])));
		assertTrue(firstAvgTelemetryValidity >= 0.0 && firstAvgTelemetryValidity <= 1.0);
		assertTrue(maxColumn(lines, header, "avg_motor_erpm100") > 1.0);
		assertTrue(maxColumn(lines, header, "motor_0_erpm100") > 1.0);
		assertTrue(maxColumn(lines, header, "avg_motor_rpm_telemetry_valid") > 0.50);
		assertTrue(maxColumn(lines, header, "gyro_notch_hz") > 1.0);
		assertTrue(maxColumn(lines, header, "gyro_blade_pass_notch_hz") > maxColumn(lines, header, "gyro_notch_hz"));
		assertTrue(maxColumn(lines, header, "gyro_blade_pass_notch_attenuation") > 0.0);
		String[] spinningRow = null;
		int avgErpmIndex = indexOf(header, "avg_motor_erpm100");
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			if (Double.parseDouble(row[avgErpmIndex]) > 1.0) {
				spinningRow = row;
				break;
			}
		}
		assertTrue(spinningRow != null);
		double expectedAvgMotorEInterval = 600000.0 / Double.parseDouble(spinningRow[indexOf(header, "avg_motor_erpm100")]);
		assertEquals(
				expectedAvgMotorEInterval,
				Double.parseDouble(spinningRow[indexOf(header, "avg_motor_einterval_us")]),
				Math.max(1.0, expectedAvgMotorEInterval * 0.002)
		);
		assertEquals(
				Double.parseDouble(firstRow[indexOf(header, "motor_7_rpm")]) * 7.0 / 100.0,
				Double.parseDouble(firstRow[indexOf(header, "motor_7_erpm100")]),
				0.1
		);
		assertEquals(0.0, Double.parseDouble(firstRow[indexOf(header, "motor_7_einterval_us")]), 1.0e-9);
		assertEquals(0.0, Double.parseDouble(firstRow[indexOf(header, "motor_7_rpm_telemetry_valid")]), 1.0e-9);
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "avg_motor_target_rpm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "avg_motor_target_erpm100")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "avg_motor_target_einterval_us")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_target_rpm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_target_erpm100")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_target_einterval_us")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "avg_motor_tracking_error")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_tracking_error")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "avg_motor_actuator_authority")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "motor_7_actuator_authority")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_bus_ripple_v")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_effective_resistance_ohm")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_20pct_sag_current_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_20pct_sag_current_margin")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "mqtb_hq5x4x3_current_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "mqtb_hq5x4x3_power_w")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "mqtb_hq5x4x3_current_ratio")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "mqtb_hq5x4x3_current_residual_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "apdrone_pdf5045_current_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "apdrone_pdf5045_power_w")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "apdrone_pdf5045_current_ratio")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "apdrone_pdf5045_current_residual_a")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_slow_polarization_v")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "imu_supply_noise")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_temp_c")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_cooling_factor")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_thermal_limit")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_soc_resistance_scale")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_temp_resistance_scale")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "battery_polarization_resistance_scale")])));
		assertTrue(Double.isFinite(Double.parseDouble(firstRow[indexOf(header, "avg_motor_mechanical_loss_torque_nm")])));
		assertTrue(maxColumn(lines, header, "motor_commutation_ripple") > 0.0);
		assertTrue(maxColumn(lines, header, "motor_regen_current_a") >= 0.0);
		assertTrue(maxColumn(lines, header, "motor_current_ripple_a") > 0.0);
		assertTrue(maxColumn(lines, header, "motor_phase_current_a") > 0.0);
		assertTrue(maxColumn(lines, header, "motor_torque_ripple_nm") > 0.0);
		assertTrue(maxColumn(lines, header, "motor_winding_resistance_scale") >= 1.0);
		assertTrue(maxColumn(lines, header, "avg_motor_target_rpm") > 1000.0);
		assertTrue(maxColumn(lines, header, "avg_motor_tracking_error") > 0.005);
		assertTrue(maxColumn(lines, header, "avg_motor_actuator_authority") <= 1.0);
		assertTrue(maxColumn(lines, header, "rotor_coning") > 0.0);
		assertTrue(maxColumn(lines, header, "rotor_coning_angle_deg") > 0.0);
		assertTrue(maxColumn(lines, header, "rotor_windmilling") > 0.10);
		assertTrue(maxColumn(lines, header, "rotor_dynamic_inflow_tau_s") > 0.02);
		assertTrue(minColumn(lines, header, "rotor_prop_thrust_scale") < 0.90);
		assertTrue(minColumn(lines, header, "rotor_prop_power_scale") < 0.95);
		assertTrue(maxColumn(lines, header, "rotor_reverse_flow_fraction") > 0.05);
		assertTrue(maxColumn(lines, header, "airframe_separation") > 0.50);
		assertTrue(maxColumn(lines, header, "battery_bus_ripple_v") > 0.0);
		assertTrue(maxColumn(lines, header, "battery_effective_resistance_ohm") > 0.0);
		assertTrue(maxColumn(lines, header, "battery_20pct_sag_current_a") > 0.0);
		assertTrue(maxColumn(lines, header, "battery_20pct_sag_current_margin") > 0.0);
		assertTrue(maxColumn(lines, header, "mqtb_hq5x4x3_current_a") > 0.0);
		assertTrue(maxColumn(lines, header, "mqtb_hq5x4x3_power_w") > 0.0);
		assertTrue(maxColumn(lines, header, "mqtb_hq5x4x3_current_ratio") > 0.0);
		assertTrue(maxAbsColumn(lines, header, "mqtb_hq5x4x3_current_residual_a") > 0.0);
		assertTrue(maxColumn(lines, header, "battery_slow_polarization_v") > 0.0);
		assertTrue(maxColumn(lines, header, "battery_soc_resistance_scale") >= 1.0);
		assertTrue(maxColumn(lines, header, "battery_temp_resistance_scale") >= 1.0);
		assertTrue(maxColumn(lines, header, "battery_polarization_resistance_scale") > 1.05);
		assertTrue(maxColumn(lines, header, "battery_temp_c") >= 25.0);
		assertTrue(maxColumn(lines, header, "avg_motor_mechanical_loss_torque_nm") > 0.0);
		assertTrue(maxColumn(lines, header, "obstacle_proximity") > 0.25);
		assertTrue(maxColumn(lines, header, "rotor_wall_effect_n") > 0.04);
		assertTrue(maxColumn(lines, header, "rotor_0_flow_obstruction") >= 0.0);
		assertTrue(maxColumn(lines, header, "rotor_disk_wind_gradient_mps") > 0.20);
		assertTrue(maxColumn(lines, header, "rotor_a4mc_shelter_obstruction") > 0.12);
		assertTrue(maxColumn(lines, header, "rotor_0_a4mc_shelter_obstruction") > 0.12);
		assertTrue(maxColumn(lines, header, "rotor_3_a4mc_shelter_obstruction") > 0.12);
		assertTrue(maxColumn(lines, header, "rotor_1_a4mc_shelter_obstruction") < 0.05);
		assertTrue(maxColumn(lines, header, "rotor_2_a4mc_shelter_obstruction") < 0.05);
		assertTrue(minColumn(lines, header, "rotor_0_env_thrust_multiplier") > 0.0);
		assertEquals(0.0, maxColumn(lines, header, "water_immersion"), 1.0e-9);
		assertTrue(maxColumn(lines, header, "precipitation_wetness") > 0.95);
		assertTrue(minColumn(lines, header, "rotor_wet_thrust_scale") < 0.98);
		assertEquals(1.0, maxColumn(lines, header, "rotor_0_health"), 1.0e-9);
		assertEquals(0.90, minColumn(lines, header, "rotor_0_health"), 1.0e-9);
		assertEquals(1.0, minColumn(lines, header, "rotor_1_health"), 1.0e-9);
		assertEquals(1.0, minColumn(lines, header, "rotor_2_health"), 1.0e-9);
		assertEquals(1.0, minColumn(lines, header, "rotor_3_health"), 1.0e-9);
		assertTrue(maxColumn(lines, header, "rotor_damage_vibration") > 0.001);
		assertTrue(maxColumn(lines, header, "rotor_0_damage_vibration") > 0.001);
		assertEquals(0.0, maxColumn(lines, header, "rotor_1_damage_vibration"), 1.0e-9);
		int phaseIndex = indexOf(header, "phase");
		boolean sawWallSkim = false;
		boolean sawRainBurst = false;
		boolean sawLightPropFault = false;
		double maxWallSkimFlowObstruction = 0.0;
		double maxWallSkimA4mcShelterObstruction = 0.0;
		double maxWallSkimDiskWindGradient = 0.0;
		double minWallSkimThrustMultiplier = 1.0;
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			if ("wall_skim".equals(row[phaseIndex])) {
				sawWallSkim = true;
				for (int rotorIndex = 0; rotorIndex < 4; rotorIndex++) {
					double flowObstruction = Double.parseDouble(row[indexOf(header, "rotor_" + rotorIndex + "_flow_obstruction")]);
					double a4mcShelterObstruction = Double.parseDouble(row[indexOf(header, "rotor_" + rotorIndex + "_a4mc_shelter_obstruction")]);
					double diskWindGradient = Double.parseDouble(row[indexOf(header, "rotor_" + rotorIndex + "_disk_wind_gradient_mps")]);
					double thrustMultiplier = Double.parseDouble(row[indexOf(header, "rotor_" + rotorIndex + "_env_thrust_multiplier")]);
					maxWallSkimFlowObstruction = Math.max(maxWallSkimFlowObstruction, flowObstruction);
					maxWallSkimA4mcShelterObstruction = Math.max(maxWallSkimA4mcShelterObstruction, a4mcShelterObstruction);
					maxWallSkimDiskWindGradient = Math.max(maxWallSkimDiskWindGradient, diskWindGradient);
					minWallSkimThrustMultiplier = Math.min(minWallSkimThrustMultiplier, thrustMultiplier);
					assertTrue(flowObstruction >= a4mcShelterObstruction);
					assertEquals(
							RotorFlowObstructionModel.thrustMultiplier(flowObstruction),
							thrustMultiplier,
							2.0e-5
					);
				}
			} else if ("rain_burst".equals(row[phaseIndex])) {
				sawRainBurst = true;
			} else if ("light_prop_fault".equals(row[phaseIndex])) {
				sawLightPropFault = true;
			}
		}
		assertTrue(sawWallSkim);
		assertTrue(maxWallSkimFlowObstruction > 0.25, "maxWallSkimFlowObstruction=" + maxWallSkimFlowObstruction);
		assertTrue(maxWallSkimA4mcShelterObstruction > 0.12, "maxWallSkimA4mcShelterObstruction=" + maxWallSkimA4mcShelterObstruction);
		assertTrue(maxWallSkimDiskWindGradient > 0.20, "maxWallSkimDiskWindGradient=" + maxWallSkimDiskWindGradient);
		assertTrue(minWallSkimThrustMultiplier < 0.995, "minWallSkimThrustMultiplier=" + minWallSkimThrustMultiplier);
		assertTrue(minWallSkimThrustMultiplier > 0.94, "minWallSkimThrustMultiplier=" + minWallSkimThrustMultiplier);
		assertTrue(sawRainBurst);
		assertTrue(sawLightPropFault);
		assertTrue(report.samples() > 200);
		assertTrue(report.maxSpeedMetersPerSecond() > 6.8, () -> "maxSpeed=" + report.maxSpeedMetersPerSecond());
		assertTrue(report.maxBatteryCurrentAmps() > 45.0);
		assertTrue(report.maxBatteryRegenerativeCurrentAmps() >= 0.0);
		assertTrue(report.maxMotorRegenerativeCurrentAmps() > 0.0);
		assertTrue(report.maxBatteryVoltageSpike() > 1.0e-4);
		assertTrue(report.maxBatteryBusRippleVoltage() > 1.0e-4);
		assertTrue(report.maxRotorDamageVibration() > 0.001);
		assertTrue(report.maxBatteryEffectiveResistanceOhms() > 0.0);
		assertTrue(report.maxAverageMotorTelemetryRpm() > 100.0);
		assertTrue(report.maxMotorTelemetryRpm() >= report.maxAverageMotorTelemetryRpm() * 0.80);
		double maxLoggedMotorErpm100 = 0.0;
		double minLoggedMotorEInterval = Double.POSITIVE_INFINITY;
		double maxLoggedMotorRpmTelemetryValidity = 0.0;
		for (int i = 0; i < 4; i++) {
			maxLoggedMotorErpm100 = Math.max(maxLoggedMotorErpm100, maxColumn(lines, header, "motor_" + i + "_erpm100"));
			minLoggedMotorEInterval = Math.min(minLoggedMotorEInterval, minColumn(lines, header, "motor_" + i + "_einterval_us"));
			maxLoggedMotorRpmTelemetryValidity = Math.max(
					maxLoggedMotorRpmTelemetryValidity,
					maxColumn(lines, header, "motor_" + i + "_rpm_telemetry_valid")
			);
		}
		assertEquals(maxColumn(lines, header, "avg_motor_erpm100"), report.maxAverageMotorTelemetryErpm100(), 0.1);
		assertEquals(maxLoggedMotorErpm100, report.maxMotorTelemetryErpm100(), 0.1);
		assertEquals(minColumn(lines, header, "avg_motor_einterval_us"), report.minAverageMotorTelemetryEIntervalMicros(), 0.1);
		assertEquals(minLoggedMotorEInterval, report.minMotorTelemetryEIntervalMicros(), 0.1);
		assertEquals(
				maxColumn(lines, header, "avg_motor_rpm_telemetry_valid"),
				report.maxAverageMotorRpmTelemetryValidity(),
				1.0e-5
		);
		assertEquals(
				maxLoggedMotorRpmTelemetryValidity,
				report.maxMotorRpmTelemetryValidity(),
				1.0e-5
		);
		assertEquals(maxColumn(lines, header, "gyro_notch_hz"), report.maxGyroNotchFrequencyHertz(), 0.001);
		assertEquals(maxColumn(lines, header, "gyro_notch_attenuation"), report.maxGyroNotchAttenuation(), 1.0e-4);
		assertEquals(maxColumn(lines, header, "gyro_notch_spread_hz"), report.maxGyroNotchSpreadHertz(), 0.001);
		assertEquals(maxColumn(lines, header, "gyro_rpm_harmonic_notch_attenuation"), report.maxGyroRpmHarmonicNotchAttenuation(), 1.0e-4);
		assertEquals(maxColumn(lines, header, "gyro_blade_pass_notch_hz"), report.maxGyroBladePassNotchFrequencyHertz(), 0.001);
		assertEquals(maxColumn(lines, header, "gyro_blade_pass_notch_attenuation"), report.maxGyroBladePassNotchAttenuation(), 1.0e-4);
		assertEquals(maxColumn(lines, header, "gyro_blade_pass_notch_spread_hz"), report.maxGyroBladePassNotchSpreadHertz(), 0.001);
		assertEquals(
				maxColumn(lines, header, "battery_soc_resistance_scale"),
				report.maxBatteryStateOfChargeResistanceScale(),
				1.0e-5
		);
		assertEquals(
				maxColumn(lines, header, "battery_temp_resistance_scale"),
				report.maxBatteryTemperatureResistanceScale(),
				1.0e-5
		);
		assertEquals(
				maxColumn(lines, header, "battery_polarization_resistance_scale"),
				report.maxBatteryPolarizationResistanceScale(),
				1.0e-5
		);
		assertTrue(report.maxBatteryStateOfChargeResistanceScale() >= 1.0);
		assertTrue(report.maxBatteryTemperatureResistanceScale() >= 1.0);
		assertTrue(report.maxBatteryPolarizationResistanceScale() > 1.05);
		assertTrue(report.maxBatteryTemperatureCelsius() >= 25.0);
		assertTrue(report.minBatteryThermalLimit() > 0.0);
		assertTrue(report.maxMotorTrackingError() > 0.005);
		assertTrue(report.minMotorActuatorAuthority() >= 0.0);
		assertTrue(report.minMixerAxisAuthority() >= 0.0);
		assertTrue(report.minMixerAxisAuthority() <= 1.0);
		assertTrue(report.maxWindGustSpeedMetersPerSecond() > 0.05);
		assertTrue(report.maxWindDrydenSpeedMetersPerSecond() >= 0.0);
		assertTrue(report.maxWindBurbleSpeedMetersPerSecond() > 0.0);
		assertTrue(report.maxWindA4mcSourceGustSpeedMetersPerSecond() >= 0.0);
		assertTrue(report.maxAbsWindA4mcUpdraftMetersPerSecond() >= 0.0);
		assertTrue(report.maxWindA4mcTerrainShearSpeedMetersPerSecond() >= 0.0);
		assertEquals(0.86, report.maxWindSourceQualityFactor(), 1.0e-5);
		assertTrue(report.maxWindSourceTurbulenceIntensity() > 0.0);
		assertEquals(
				maxColumn(lines, header, "wind_dryden_speed_mps"),
				report.maxWindDrydenSpeedMetersPerSecond(),
				1.0e-5
		);
		assertEquals(
				maxColumn(lines, header, "wind_burble_speed_mps"),
				report.maxWindBurbleSpeedMetersPerSecond(),
				1.0e-5
		);
		assertEquals(
				maxColumn(lines, header, "wind_a4mc_source_gust_speed_mps"),
				report.maxWindA4mcSourceGustSpeedMetersPerSecond(),
				1.0e-5
		);
		assertEquals(
				maxAbsColumn(lines, header, "wind_a4mc_updraft_mps"),
				report.maxAbsWindA4mcUpdraftMetersPerSecond(),
				1.0e-5
		);
		assertEquals(
				maxColumn(lines, header, "wind_a4mc_terrain_shear_speed_mps"),
				report.maxWindA4mcTerrainShearSpeedMetersPerSecond(),
				1.0e-5
		);
		assertEquals(
				maxColumn(lines, header, "wind_source_quality"),
				report.maxWindSourceQualityFactor(),
				1.0e-5
		);
		assertEquals(
				maxColumn(lines, header, "wind_source_turbulence"),
				report.maxWindSourceTurbulenceIntensity(),
				1.0e-5
		);
		assertTrue(maxColumn(lines, header, "wind_a4mc_terrain_shear_speed_mps") > 0.02);
		assertTrue(maxColumn(lines, header, "wind_source_turbulence") > 0.0);
		assertTrue(maxColumn(lines, header, "wind_source_quality") > 0.80);
		assertTrue(report.maxWindShearAccelerationMetersPerSecondSquared() > 0.10);
		assertTrue(report.maxRotorWallEffectForceNewtons() > 0.04);
		assertTrue(report.maxContactImpactSpeedMetersPerSecond() >= 0.0);
		assertTrue(report.maxContactSlipSpeedMetersPerSecond() >= 0.0);
		assertTrue(report.maxContactBounceSpeedMetersPerSecond() >= 0.0);
		assertTrue(report.maxContactAngularImpulseDegreesPerSecond() >= 0.0);
		assertTrue(report.maxRotorInducedVelocityMetersPerSecond() > 0.0);
		assertTrue(report.minRotorInducedLagThrustScale() >= 0.65);
		assertTrue(report.minRotorInducedLagThrustScale() <= 1.0);
		assertEquals((1.0 - report.minRotorInducedLagThrustScale()) * 100.0, report.maxRotorInducedLagThrustLossPercent(), 1.0e-9);
		assertTrue(report.maxRotorDynamicInflowTimeConstantSeconds() > 0.02);
		assertTrue(report.minRotorWetThrustScale() >= 0.08);
		assertTrue(report.minRotorWetThrustScale() <= 1.0);
		assertTrue(report.maxRotorWetThrustLossPercent() > 2.0);
		assertEquals((1.0 - report.minRotorWetThrustScale()) * 100.0, report.maxRotorWetThrustLossPercent(), 1.0e-9);
		assertTrue(report.maxRotorSurfaceScrapeIntensity() >= 0.0);
		assertTrue(report.minBatteryVoltage() < directControl(DroneConfig.racingQuad()).nominalBatteryVoltage() - 0.5);
		assertTrue(report.maxPropwashIntensity() > 0.02);
		assertTrue(report.maxRotorAdvanceRatio() > 0.05);
		assertEquals(Math.PI * report.maxRotorAdvanceRatio(), report.maxRotorPropellerAdvanceRatioJ(), 0.0001);
		double minLoggedRotorAxialGustThrustScale = minColumn(lines, header, "rotor_axial_gust_thrust_scale");
		double maxLoggedRotorAxialGustThrustScale = maxColumn(lines, header, "rotor_axial_gust_thrust_scale");
		for (int i = 0; i < 8; i++) {
			minLoggedRotorAxialGustThrustScale = Math.min(
					minLoggedRotorAxialGustThrustScale,
					minColumn(lines, header, "rotor_" + i + "_axial_gust_thrust_scale")
			);
			maxLoggedRotorAxialGustThrustScale = Math.max(
					maxLoggedRotorAxialGustThrustScale,
					maxColumn(lines, header, "rotor_" + i + "_axial_gust_thrust_scale")
			);
		}
		assertTrue(minLoggedRotorAxialGustThrustScale > 0.0);
		assertTrue(maxLoggedRotorAxialGustThrustScale <= 2.5);
		assertEquals(minLoggedRotorAxialGustThrustScale, report.minRotorAxialGustThrustScale(), 1.0e-5);
		assertEquals(maxLoggedRotorAxialGustThrustScale, report.maxRotorAxialGustThrustScale(), 1.0e-5);
		assertTrue(report.maxRotorReverseFlowInboardFraction() > 0.05);
		assertTrue(report.maxRotorReverseFlowInboardFraction() <= Math.min(1.0, report.maxRotorAdvanceRatio()) + 0.02);
		double maxLoggedRotorReverseFlow = 0.0;
		for (int i = 0; i < 8; i++) {
			maxLoggedRotorReverseFlow = Math.max(
					maxLoggedRotorReverseFlow,
					maxColumn(lines, header, "rotor_" + i + "_reverse_flow_fraction")
			);
		}
		assertEquals(
				maxLoggedRotorReverseFlow,
				report.maxRotorReverseFlowInboardFraction(),
				1.0e-5
		);
		assertTrue(report.maxRotorTipMach() > 0.05);
		assertTrue(report.maxRotorCompressibilityThrustLossPercent() >= 0.0);
		assertTrue(report.maxRotorCompressibilityThrustLossPercent() <= 26.0);
		assertTrue(report.maxRotorLowReynoldsLoss() >= 0.0);
		assertTrue(report.maxRotorLowReynoldsLoss() <= 1.0);
		assertTrue(report.maxRotorBladePassRippleIntensity() > 0.0);
		assertTrue(report.maxRotorBladePassRippleIntensity() <= 1.0);
		assertTrue(report.maxRotorBladeDissymmetryTorqueNewtonMeters() >= 0.0);
		assertEquals(maxBladeDissymmetryTorque, report.maxRotorBladeDissymmetryTorqueNewtonMeters(), 1.0e-5);
		assertTrue(report.maxRotorArmFlexIntensity() > 0.02);
		assertTrue(report.maxRotorArmFlexDeflectionMeters() > 0.0001);
		assertTrue(report.maxRotorArmFlexTiltRadians() > 0.0);
		assertTrue(report.minMotorElectricalEfficiency() > 0.50);
		assertTrue(report.minMotorElectricalEfficiency() < 0.90);
		assertTrue(report.minMotorVoltageHeadroom() >= 0.0);
		assertTrue(report.minMotorVoltageHeadroom() < 0.45);
		assertTrue(report.maxMotorWindingResistanceScale() >= 1.0);
		assertTrue(report.maxRotorStallIntensity() > 0.10);
		assertTrue(report.maxAirframeSeparatedFlowIntensity() > 0.50);
		assertTrue(report.maxAirframeSeparatedFlowIntensity() <= 1.0);
		assertTrue(report.maxAirframeBodyDragForceNewtons() > 0.05);
		assertTrue(report.maxLinearDampingDragForceNewtons() > 0.50);
		assertTrue(report.maxRotorConingIntensity() > 0.0);
		assertTrue(report.maxRotorConingAngleRadians() > 0.0);
		assertTrue(report.maxRotorWindmillingIntensity() > 0.10);
		assertTrue(report.maxAirframeTorqueNewtonMeters() > 0.025);
		assertTrue(report.maxBarometerErrorMeters() > 0.05);
		assertTrue(report.maxEscTemperatureCelsius() >= 25.0);
		assertTrue(report.minEscThermalLimit() > 0.0);
	}

	@Test
	void offlineFlightRecorderCliSummaryReportsBatteryResistanceScaleSplit(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("short_racing_quad.csv");
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		PrintStream previousOut = System.out;
		try {
			System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
			OfflineFlightRecorder.main(new String[] {"racing_quad", output.toString(), "1.0"});
		} finally {
			System.setOut(previousOut);
		}

		String text = stdout.toString(StandardCharsets.UTF_8);
		assertTrue(Files.exists(output));
		assertTrue(text.contains("max_ir="));
		assertTrue(text.contains("max_irx="));
		assertTrue(text.contains("max_erpm100="));
		assertTrue(text.contains("min_eint="));
		assertTrue(text.contains("notch="));
		assertTrue(text.contains("bpass_notch="));
		assertTrue(text.contains("max_ground_level="));
		assertTrue(text.contains("max_wind_quality="));
		assertTrue(text.contains("Airframe IMAV body-drag fit"));
		assertTrue(text.contains("Airframe base-drag level-flight envelope"));
		assertTrue(text.contains("AI-IO14"));
		assertTrue(text.contains("RATM21"));
		assertTrue(text.contains("UZH26.8"));
		assertTrue(text.contains("RATM high-speed audit"));
		assertTrue(text.contains("RATM-500Hz-Speed-Envelope"));
		assertTrue(text.contains(">=21m/s"));
		assertTrue(text.contains("speed/limit"));
		assertTrue(text.contains("NeuroBEM residual audit"));
		assertTrue(text.contains("NeuroBEM-Drag-Residual-Packet"));
		assertTrue(text.contains("0.772kg"));
		assertTrue(text.contains("guard"));
		assertTrue(text.contains("low-speed-residual-not-wind-tunnel-drag"));
		assertTrue(text.contains("FPV LiPo ESR audit"));
		assertTrue(text.contains("FPV-LiPo-ESR-Calibration-Packet"));
		assertTrue(text.contains("absolute-fpv-ir-vs-shape-priors"));
		assertTrue(text.contains("ir_formula"));
		assertTrue(text.contains("High-advance prop audit"));
		assertTrue(text.contains("APC-High-J-Axial-Propeller-Packet"));
		assertTrue(text.contains("axial-not-edgewise"));
		assertTrue(text.contains("5x11"));
		assertTrue(text.contains("Mejzlik wind-tunnel prop audit"));
		assertTrue(text.contains("Mejzlik-Wind-Tunnel-Prop-Packet"));
		assertTrue(text.contains("CT0 J"));
		assertTrue(text.contains("axial-wind-tunnel-not-edgewise-fpv"));
		assertTrue(text.contains("tilt"));
		assertTrue(text.contains("reachable"));
		assertTrue(text.contains("Prop geometry audit"));
		assertTrue(text.contains("Prop-Pitch-Geometry-Packet"));
		assertTrue(text.contains("HQProp Durable 5x4.5x3 V1S"));
		assertTrue(text.contains("UIUC"));
		assertTrue(text.contains("geometry-not-slip"));
		assertTrue(text.contains("Precipitation/water audit"));
		assertTrue(text.contains("Precipitation-Water-Calibration-Packet"));
		assertTrue(text.contains("ICAS-2020-heavy-rain-CT"));
		assertTrue(text.contains("wet-prop-not-immersion"));
		assertTrue(text.contains("water0.5@5m/s"));
		assertTrue(text.contains("Icing rotor audit"));
		assertTrue(text.contains("Icing-Rotor-MDPI-Packet"));
		assertTrue(text.contains("icing-time-accumulating-not-ordinary-rain"));
		assertTrue(text.contains("MVD120_T-15_h4m"));
		assertTrue(text.contains("Wind/gust audit"));
		assertTrue(text.contains("Wind-Gust-Dryden-Calibration-Packet"));
		assertTrue(text.contains("wind_10m_s_dirty_1p5_alt_6m"));
		assertTrue(text.contains("dryden-burble-ct-separate"));
		assertTrue(text.contains("VRS/propwash audit"));
		assertTrue(text.contains("VRS-Propwash-Calibration-Packet"));
		assertTrue(text.contains("Cambridge peak"));
		assertTrue(text.contains("separate-mean-buffet-lateral-torque"));
		assertTrue(text.contains("Coaxial allocation audit"));
		assertTrue(text.contains("Coaxial-Allocation-Calibration-Packet"));
		assertTrue(text.contains("allocation-not-thrust-loss-fit"));
		assertTrue(text.contains("Surface near-field audit"));
		assertTrue(text.contains("Surface-Nearfield-Calibration-Packet"));
		assertTrue(text.contains("wall0.25R"));
		assertTrue(text.contains("ground-ceiling-wall-separate"));
		assertTrue(text.contains("Rotor dynamics audit"));
		assertTrue(text.contains("Rotor-Dynamics-Inertia-Inflow-Coning-Packet"));
		assertTrue(text.contains("runtime-helpers"));
		assertTrue(text.contains("APDrone inertia audit"));
		assertTrue(text.contains("APDrone-Mendeley-Inertia-PDF"));
		assertTrue(text.contains("yaw_ratio"));
		assertTrue(text.contains("Tyto x3nm static-powertrain audit"));
		assertTrue(text.contains("max_thrust"));
		assertTrue(text.contains("tyto_eq"));
		assertTrue(text.contains("APDrone motor PDF audit"));
		assertTrue(text.contains("YSIDO-2507-1800KV"));
		assertTrue(text.contains("winding_r"));
		assertTrue(text.contains("per_motor_current"));
		assertTrue(text.contains("Foxeer Donut 5145 prop audit"));
		assertTrue(text.contains("Foxeer-Donut-5145"));
		assertTrue(text.contains("Tyto static yaw-torque audit"));
		assertTrue(text.contains("yaw_qt"));
		assertTrue(text.contains("fit_window"));
		assertTrue(text.contains("AI-IO rotor-speed audit"));
		assertTrue(text.contains("low_dyn"));
		assertTrue(text.contains("bpass_nyq"));
		assertTrue(text.contains("APDrone urban motor-RPM audit"));
		assertTrue(text.contains("APDrone-Mendeley-Urban-eRPM"));
		assertTrue(text.contains("erpm100"));
		assertTrue(text.contains("rpm_ratio"));
		assertTrue(text.contains("thr_rpm"));
		assertTrue(text.contains("thr_ratio"));
		assertTrue(text.contains("Motor thermal audit"));
		assertTrue(text.contains("Motor-Thermal-Calibration-Packet"));
		assertTrue(text.contains("cross-class-U8-no-FPV-thermocouple"));
		assertTrue(text.contains("Nanodrone sysid audit"));
		assertTrue(text.contains("Nanodrone-System-Identification-Packet"));
		assertTrue(text.contains("nano-not-fpv-scale"));
		assertTrue(text.contains("Motor response dynamics audit"));
		assertTrue(text.contains("Motor-Response-Dynamics-Packet"));
		assertTrue(text.contains("Betaflight-PR12562-RPM-Slew"));
		assertTrue(text.contains("APDrone-Urban-eRPM-Lag"));
		assertTrue(text.contains("brakeTau"));
		assertTrue(text.contains("obs_ratio"));
		assertTrue(text.contains("frame_delta"));
		assertTrue(text.contains("APDrone control-response audit"));
		assertTrue(text.contains("lag_p50"));
		assertTrue(text.contains("ratios_ctrl"));
		assertTrue(text.contains("reliable"));
		assertTrue(text.contains("APDrone PID tuning audit"));
		assertTrue(text.contains("APDrone-Mendeley-PID-Sweeps"));
		assertTrue(text.contains("PID/PI"));
		assertTrue(text.contains("dmin_match"));
		assertTrue(text.contains("APDrone rate-envelope audit"));
		assertTrue(text.contains("cfg/limit"));
		assertTrue(text.contains("stick25/50/75"));
		assertTrue(text.contains("APDrone IMU noise audit"));
		assertTrue(text.contains("APDrone-Mendeley-Blackbox"));
		assertTrue(text.contains("baro_quiet"));
		assertTrue(text.contains("APDrone barometer noise audit"));
		assertTrue(text.contains("detrended_p50"));
		assertTrue(text.contains("cfg/dps"));
		assertTrue(text.contains("Prop damage vibration audit"));
		assertTrue(text.contains("single_fault"));
		assertTrue(text.contains("padre"));
	}

	@Test
	void propDamageVibrationAuditComparesRuntimeSensorRmsAgainstFaultDatasets() {
		OfflineFlightRecorder.PropDamageVibrationAudit audit =
				OfflineFlightRecorder.propDamageVibrationAudit(DroneConfig.racingQuad());

		assertEquals(0, audit.damagedRotorIndex());
		assertEquals(0.75, audit.rotorDamageAmount(), 1.0e-12);
		assertEquals(1200, audit.sampleCount());
		assertEquals(6.0, audit.sampledSeconds(), 1.0e-12);
		assertEquals(1.566803460010257, audit.referenceSingleBrokenGyroRmsRatio(), 1.0e-15);
		assertEquals(3.65539974853721, audit.referenceSingleBrokenAccelerometerRmsRatio(), 1.0e-15);
		assertEquals(3.000977801285221, audit.padreSingleRotorAccelerometerFeatureRmsRatio(), 1.0e-15);
		assertEquals(3.125830267410634, audit.padreTwoPositionAccelerometerFeatureRmsRatio(), 1.0e-15);
		assertTrue(audit.healthyGyroDynamicRmsRadiansPerSecond() > 0.0);
		assertTrue(audit.damagedGyroDynamicRmsRadiansPerSecond() > audit.healthyGyroDynamicRmsRadiansPerSecond());
		assertTrue(audit.gyroDynamicRmsRatio() > 1.05);
		assertTrue(audit.gyroDynamicRmsRatio() < 20.0);
		assertTrue(audit.healthyAccelerometerDynamicRmsMetersPerSecondSquared() > 0.0);
		assertTrue(audit.damagedAccelerometerDynamicRmsMetersPerSecondSquared()
				> audit.healthyAccelerometerDynamicRmsMetersPerSecondSquared());
		assertTrue(audit.accelerometerDynamicRmsRatio() > 1.05);
		assertTrue(audit.accelerometerDynamicRmsRatio() < 30.0);
		assertTrue(audit.maxDamagedRotorDamageVibration() > 0.10);
		assertTrue(audit.maxDamagedRotorVibration() > audit.maxHealthyRotorVibration() + 0.02);
	}

	@Test
	void offlineFlightRecorderSupportsApDronePreset(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("apdrone.csv");
		OfflineFlightRecorder.FlightReport report = OfflineFlightRecorder.record("apdrone", output, 3.0);
		List<String> lines = Files.readAllLines(output);
		String[] header = lines.get(0).split(",", -1);
		String[] firstRow = lines.get(1).split(",", -1);
		String[] row = lines.get(lines.size() - 1).split(",", -1);
		DroneConfig preset = OfflineFlightRecorder.preset("apdrone");
		RotorSpec rotor = preset.rotors().get(0);
		OfflineFlightRecorder.BatteryAutonomyEstimate[] autonomy =
				OfflineFlightRecorder.apDroneBatteryAutonomyEstimates(preset);
		OfflineFlightRecorder.BatteryVoltageDropAudit voltageDrop =
				OfflineFlightRecorder.apDroneBatteryVoltageDropAudit(preset);
		OfflineFlightRecorder.ReferenceSpeedEnvelopeEstimate[] speedEnvelope =
				OfflineFlightRecorder.apDroneOpenFieldSpeedEnvelopeAudit(preset);

		assertEquals(4, preset.rotors().size());
		assertTrue(Files.exists(output));
		assertTrue(lines.size() > 100);
		assertEquals(header.length, row.length);
		assertEquals("4", row[indexOf(header, "airframe_rotor_count")]);
		assertEquals(480.0, Double.parseDouble(firstRow[indexOf(header, "tune_esc_command_frame_rate_hz")]), 1.0e-9);
		assertEquals(2000.0, Double.parseDouble(firstRow[indexOf(header, "tune_esc_command_resolution_steps")]), 1.0e-9);
		assertEquals(600.0, Double.parseDouble(firstRow[indexOf(header, "tune_esc_dshot_bitrate_kbit_s")]), 1.0e-9);
		assertEquals(26.667, Double.parseDouble(firstRow[indexOf(header, "tune_esc_dshot_raw_frame_us")]), 0.001);
		assertEquals(0.01280, Double.parseDouble(firstRow[indexOf(header, "tune_esc_dshot_wire_utilization")]), 0.00001);
		assertEquals(78.13, Double.parseDouble(firstRow[indexOf(header, "tune_esc_command_interval_raw_frame_ratio")]), 0.01);
		assertEquals(rotor.bladePitchToDiameterRatio(), Double.parseDouble(firstRow[indexOf(header, "tune_rotor_pitch_to_diameter")]), 0.00001);
		assertEquals(3.0, Double.parseDouble(firstRow[indexOf(header, "tune_rotor_blade_count")]), 1.0e-9);
		assertEquals(DroneConfig.APDRONE_MOTOR_POLE_PAIRS, Double.parseDouble(firstRow[indexOf(header, "tune_motor_pole_pairs")]), 1.0e-9);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_0_rpm")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_tip_mach")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_reynolds_number")]) >= 0.0);
		assertTrue(maxColumn(lines, header, "apdrone_pdf5045_current_a") > 0.0);
		assertTrue(maxColumn(lines, header, "apdrone_pdf5045_power_w") > 0.0);
		assertTrue(maxColumn(lines, header, "apdrone_pdf5045_current_ratio") > 0.0);
		assertTrue(maxAbsColumn(lines, header, "apdrone_pdf5045_current_residual_a") > 0.0);
		assertTrue(report.samples() > 100);
		assertTrue(report.maxBatteryCurrentAmps() > 20.0);
		assertEquals(2, autonomy.length);
		assertEquals("max_power", autonomy[0].scenario());
		assertEquals("normal_power", autonomy[1].scenario());
		assertEquals(205.862266, autonomy[0].referenceDurationSeconds(), 1.0e-6);
		assertEquals(511.0542576, autonomy[1].referenceDurationSeconds(), 1.0e-6);
		assertEquals(25.900383408869278, autonomy[0].referenceMeanCurrentAmps(), 1.0e-12);
		assertEquals(9.30422120169919, autonomy[1].referenceMeanCurrentAmps(), 1.0e-12);
		assertTrue(autonomy[0].simulatedDurationSeconds() > 20.0);
		assertTrue(autonomy[1].simulatedDurationSeconds() > autonomy[0].simulatedDurationSeconds());
		assertTrue(autonomy[0].durationRatio() > 0.10);
		assertTrue(autonomy[0].durationRatio() < 0.60);
		assertTrue(autonomy[1].durationRatio() > 0.55);
		assertTrue(autonomy[1].durationRatio() < 0.90);
		assertTrue(autonomy[0].meanCurrentAmps() > autonomy[0].referenceMeanCurrentAmps());
		assertTrue(autonomy[1].meanCurrentAmps() > autonomy[1].referenceMeanCurrentAmps());
		assertTrue(autonomy[0].currentMatchedDirectThrottleCommand() > 0.0);
		assertTrue(autonomy[0].currentMatchedDirectThrottleCommand() < autonomy[0].throttleCommand());
		assertTrue(autonomy[1].currentMatchedDirectThrottleCommand() > 0.0);
		assertTrue(autonomy[1].currentMatchedDirectThrottleCommand() < autonomy[1].throttleCommand() * 0.5);
		assertTrue(autonomy[0].currentMatchedMeanCurrentRatio() > 0.85);
		assertTrue(autonomy[0].currentMatchedMeanCurrentRatio() < 1.15);
		assertTrue(autonomy[1].currentMatchedMeanCurrentRatio() > 0.85);
		assertTrue(autonomy[1].currentMatchedMeanCurrentRatio() < 1.15);
		assertTrue(autonomy[1].referenceThrottleToCurrentMatchedDirectThrottleRatio() > 2.0);
		assertTrue(autonomy[0].currentMatchedPeakCurrentAmps() >= autonomy[0].currentMatchedMeanCurrentAmps());
		assertTrue(autonomy[1].currentMatchedPeakCurrentAmps() >= autonomy[1].currentMatchedMeanCurrentAmps());
		assertTrue(autonomy[0].consumedAmpHours() > 1.0);
		assertTrue(autonomy[0].consumedAmpHours() < 1.7);
		assertTrue(autonomy[1].consumedAmpHours() > 1.0);
		assertTrue(autonomy[1].consumedAmpHours() < 1.7);
		assertEquals(0.016, voltageDrop.configuredResistanceOhms(), 1.0e-12);
		assertEquals(9.30422120169919, voltageDrop.normalMeanCurrentAmps(), 1.0e-12);
		assertEquals(10.99, voltageDrop.normalP95CurrentAmps(), 1.0e-12);
		assertEquals(25.900383408869278, voltageDrop.maxMeanCurrentAmps(), 1.0e-12);
		assertEquals(42.06, voltageDrop.maxP95CurrentAmps(), 1.0e-12);
		assertEquals(16.59616220717009, voltageDrop.deltaCurrentAmps(), 1.0e-12);
		assertEquals(16.576, voltageDrop.normalStartVoltage(), 1.0e-12);
		assertEquals(16.648, voltageDrop.maxStartVoltage(), 1.0e-12);
		assertEquals(14.701756347346997, voltageDrop.normalMeanVoltage(), 1.0e-12);
		assertEquals(14.334578187533713, voltageDrop.maxMeanVoltage(), 1.0e-12);
		assertEquals(0.3671781598132835, voltageDrop.observedMeanVoltageDelta(), 1.0e-12);
		assertEquals(0.14886753922718707, voltageDrop.normalConfiguredSagAtMeanCurrent(), 1.0e-12);
		assertEquals(0.41440613454190844, voltageDrop.maxConfiguredSagAtMeanCurrent(), 1.0e-12);
		assertEquals(0.2655385953147214, voltageDrop.configuredSagDelta(), 1.0e-12);
		assertEquals(0.17584, voltageDrop.normalConfiguredSagAtP95Current(), 1.0e-12);
		assertEquals(0.67296, voltageDrop.maxConfiguredSagAtP95Current(), 1.0e-12);
		assertEquals(0.02212428121813912, voltageDrop.inferredResistanceProxyOhms(), 1.0e-12);
		assertEquals(0.7231873362232449, voltageDrop.configuredOverInferredProxy(), 1.0e-12);
		assertEquals(1.3827675761336953, voltageDrop.observedMeanVoltageDeltaOverConfiguredSagDelta(), 1.0e-12);
		assertEquals(0.024075093997022772, voltageDrop.normalStartDropResistanceProxyOhms(), 1.0e-12);
		assertEquals(0.00586863899273207, voltageDrop.maxStartDropResistanceProxyOhms(), 1.0e-12);
		assertEquals(0.6645872286927987, voltageDrop.normalConfiguredOverStartDropProxy(), 1.0e-12);
		assertEquals(2.726356148302011, voltageDrop.maxConfiguredOverStartDropProxy(), 1.0e-12);
		assertEquals(4, speedEnvelope.length);
		assertEquals("selected_max", speedEnvelope[0].speedPoint());
		assertEquals("open_field_mean_file_max", speedEnvelope[1].speedPoint());
		assertEquals("open_field_flight2_p95", speedEnvelope[2].speedPoint());
		assertEquals("open_field_fastest", speedEnvelope[3].speedPoint());
		assertEquals(5.75, speedEnvelope[0].referenceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(11.072, speedEnvelope[1].referenceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(17.25, speedEnvelope[2].referenceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(18.72, speedEnvelope[3].referenceSpeedMetersPerSecond(), 1.0e-12);
		assertEquals(AirframeDragCalibration.Axis.Z, speedEnvelope[3].axis());
		assertTrue(speedEnvelope[3].reachable());
		assertEquals(3.43789056, speedEnvelope[3].baseDragForceNewtons(), 1.0e-9);
		assertEquals(53.647214352662324, speedEnvelope[3].horizontalThrustMarginNewtons(), 1.0e-12);
		assertTrue(speedEnvelope[3].residualHorizontalMarginNewtons() > 50.0);
		assertTrue(speedEnvelope[3].dragOverHorizontalMargin() > 0.06);
		assertTrue(speedEnvelope[3].dragOverHorizontalMargin() < 0.07);
		assertTrue(speedEnvelope[3].requiredMaxThrustFraction() < 0.14);
		assertTrue(speedEnvelope[3].dragLimitedLevelSpeedMetersPerSecond() > 108.0);
		assertTrue(speedEnvelope[3].dragLimitedLevelSpeedMetersPerSecond() < 110.0);
		assertTrue(speedEnvelope[3].speedOverDragLimitedLevelSpeed() > 0.16);
		assertTrue(speedEnvelope[3].speedOverDragLimitedLevelSpeed() < 0.18);
	}

	@Test
	void offlineFlightRecorderSupportsHexLiftPreset(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("hex_lift.csv");
		OfflineFlightRecorder.FlightReport report = OfflineFlightRecorder.record("hex_lift", output, 3.0);
		List<String> lines = Files.readAllLines(output);
		String[] header = lines.get(0).split(",", -1);
		String[] row = lines.get(lines.size() - 1).split(",", -1);
		int columnCount = header.length;

		assertEquals(6, OfflineFlightRecorder.preset("hex_lift").rotors().size());
		assertTrue(Files.exists(output));
		assertTrue(lines.size() > 100);
		assertEquals(columnCount, row.length);
		assertTrue(OfflineFlightRecorder.csvHeader().contains("airframe_rotor_count"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_5_rpm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_5_erpm100"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_5_einterval_us"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_5_target_erpm100"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_5_target_einterval_us"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_5_regen_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_5_thrust_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_5_windmilling"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_5_wet_thrust_scale"));
		assertEquals("6", row[indexOf(header, "airframe_rotor_count")]);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_5_rpm")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_5_erpm100")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_5_einterval_us")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_5_target_erpm100")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_5_target_einterval_us")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_5_regen_current_a")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_5_thrust_n")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_5_windmilling")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_5_wet_thrust_scale")]) >= 0.08);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_5_wet_thrust_scale")]) <= 1.0);
		assertTrue(report.samples() > 100);
		assertTrue(report.maxBatteryCurrentAmps() > 20.0);
		assertTrue(Double.isFinite(report.maxRotorWakeSwirlTorqueNewtonMeters()));
		assertTrue(Double.isFinite(report.maxRotorActiveBrakingTorqueNewtonMeters()));
		assertTrue(Double.isFinite(report.maxRotorAccelerationReactionTorqueNewtonMeters()));
		assertTrue(Double.isFinite(report.maxRotorGyroscopicTorqueNewtonMeters()));
		assertTrue(Double.isFinite(report.maxRotorFlappingTorqueNewtonMeters()));
		assertTrue(Double.isFinite(report.maxImuSupplyNoiseIntensity()));
		assertTrue(Double.isFinite(report.maxRotorWindmillingIntensity()));
	}

	@Test
	void offlineFlightRecorderSupportsOctoLiftPreset(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("octo_lift.csv");
		OfflineFlightRecorder.FlightReport report = OfflineFlightRecorder.record("octo_lift", output, 3.0);
		List<String> lines = Files.readAllLines(output);
		String[] header = lines.get(0).split(",", -1);
		String[] row = lines.get(lines.size() - 1).split(",", -1);
		int columnCount = header.length;

		assertEquals(8, OfflineFlightRecorder.preset("octo_lift").rotors().size());
		assertTrue(Files.exists(output));
		assertTrue(lines.size() > 100);
		assertEquals(columnCount, row.length);
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_rpm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_erpm100"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_einterval_us"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_rpm_telemetry_valid"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_target_erpm100"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_target_einterval_us"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_electrical_efficiency"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_voltage_headroom"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_mechanical_loss_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_commutation_ripple"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_regen_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_phase_current_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_current_ripple_a"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("motor_7_torque_ripple_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_thrust_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_arm_flex"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_tip_mach"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_compressibility_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_reynolds_number"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_reynolds_index"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_low_reynolds_loss"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_blade_aoa_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_blade_element_stall"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_blade_dissymmetry"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_blade_pass_ripple"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_flapping_tilt_deg"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_coning"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_wake_interference"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_wake_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_wet_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_wake_swirl_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_wake_swirl_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_windmilling"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_dynamic_inflow_tau_s"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_advance_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_prop_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_prop_power_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_axial_gust_thrust_scale"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("ambient_temperature_c"));
		assertEquals("8", row[indexOf(header, "airframe_rotor_count")]);
		assertTrue(Double.parseDouble(row[indexOf(header, "ambient_temperature_c")]) >= -40.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_rpm")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_erpm100")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_einterval_us")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_rpm_telemetry_valid")]) > 0.95);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_target_erpm100")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_target_einterval_us")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_electrical_efficiency")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_voltage_headroom")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_mechanical_loss_torque_nm")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_commutation_ripple")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_regen_current_a")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_phase_current_a")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_current_ripple_a")]) >= 0.0);
		assertTrue(Double.isFinite(Double.parseDouble(row[indexOf(header, "motor_7_torque_ripple_nm")])));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_thrust_n")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_arm_flex")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_tip_mach")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_reynolds_number")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_reynolds_index")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_low_reynolds_loss")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_low_reynolds_loss")]) <= 1.0);
		assertTrue(Double.isFinite(Double.parseDouble(row[indexOf(header, "rotor_7_blade_aoa_deg")])));
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_element_stall")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_dissymmetry")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_blade_pass_ripple")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_flapping_tilt_deg")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_coning")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_dynamic_inflow_tau_s")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_dynamic_inflow_tau_s")]) <= 0.36);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_advance_ratio")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_thrust_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_thrust_scale")]) <= 1.08);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_power_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_prop_power_scale")]) <= 1.08);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_axial_gust_thrust_scale")]) > 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_axial_gust_thrust_scale")]) <= 2.5);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_interference")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_thrust_scale")]) >= 0.72);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_thrust_scale")]) <= 1.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wet_thrust_scale")]) >= 0.08);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wet_thrust_scale")]) <= 1.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_wake_swirl_mps")]) >= 0.0);
		assertTrue(Double.parseDouble(row[indexOf(header, "rotor_7_windmilling")]) >= 0.0);
		assertTrue(report.samples() > 100);
		assertTrue(report.maxBatteryCurrentAmps() > 20.0);
	}

	@Test
	void offlineFlightRecorderSupportsCoaxialX8PresetWithWakeTelemetry(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("coaxial_x8.csv");
		OfflineFlightRecorder.FlightReport report = OfflineFlightRecorder.record("coaxial_x8", output, 3.0);
		List<String> lines = Files.readAllLines(output);
		String[] header = lines.get(0).split(",", -1);
		String[] row = lines.get(lines.size() - 1).split(",", -1);
		int columnCount = header.length;

		assertEquals(8, OfflineFlightRecorder.preset("coaxial_x8").rotors().size());
		assertTrue(Files.exists(output));
		assertTrue(lines.size() > 100);
		assertEquals(columnCount, row.length);
		assertEquals("8", row[indexOf(header, "airframe_rotor_count")]);
		assertTrue(Double.parseDouble(row[indexOf(header, "motor_7_rpm")]) > 0.0);
		assertTrue(maxColumn(lines, header, "rotor_wake_interference") > 0.03);
		assertTrue(maxColumn(lines, header, "rotor_7_wake_interference") > 0.065);
		assertTrue(minColumn(lines, header, "rotor_wake_thrust_scale") < 0.995);
		assertTrue(minColumn(lines, header, "rotor_7_wake_thrust_scale") < 0.99);
		assertTrue(minColumn(lines, header, "rotor_7_wake_thrust_scale") >= 0.72);
		assertTrue(maxColumn(lines, header, "rotor_7_wake_thrust_scale") <= 1.0);
		assertTrue(maxColumn(lines, header, "rotor_wake_swirl_mps") > 0.05);
		assertTrue(maxColumn(lines, header, "rotor_7_wake_swirl_mps") > 0.10);
		assertTrue(maxColumn(lines, header, "rotor_coaxial_load_bias") > 0.015);
		assertTrue(maxColumn(lines, header, "rotor_0_coaxial_load_bias") > 0.015);
		assertTrue(minColumn(lines, header, "rotor_1_coaxial_load_bias") < -0.015);
		assertTrue(maxColumn(lines, header, "rotor_coaxial_load_bias_target")
				+ 1.0e-5 >= maxColumn(lines, header, "rotor_coaxial_load_bias"));
		assertTrue(maxColumn(lines, header, "rotor_coaxial_load_bias_clipping") >= 0.0);
		assertTrue(maxColumn(lines, header, "rotor_coaxial_allocation_load") > 0.10);
		assertTrue(maxColumn(lines, header, "rotor_coaxial_allocation_ratio") > 1.02);
		assertTrue(maxColumn(lines, header, "rotor_coaxial_allocation_mech_gain_pct") > 0.10);
		assertTrue(maxColumn(lines, header, "rotor_coaxial_allocation_elec_gain_pct") > 0.10);
		assertTrue(maxColumn(lines, header, "rotor_coaxial_allocation_uncertainty_pct") > 0.10);
		assertTrue(maxColumn(lines, header, "rotor_windmilling") >= 0.0);
		assertTrue(maxColumn(lines, header, "rotor_7_windmilling") >= 0.0);
		assertTrue(maxColumn(lines, header, "rotor_damage_vibration") >= 0.0);
		assertTrue(maxColumn(lines, header, "rotor_7_damage_vibration") >= 0.0);
		assertTrue(maxColumn(lines, header, "rotor_advance_ratio") >= 0.0);
		assertEquals(
				Math.PI * maxColumn(lines, header, "rotor_advance_ratio"),
				maxColumn(lines, header, "rotor_prop_advance_ratio_j"),
				0.0001
		);
		assertTrue(report.samples() > 100);
		assertTrue(report.maxBatteryCurrentAmps() > 20.0);
		assertTrue(report.maxRotorWakeSwirlVelocityMetersPerSecond() > 0.10);
		assertTrue(report.maxRotorCoaxialLoadBias() > 0.015);
		assertTrue(report.maxRotorCoaxialLoadBiasTarget() + 1.0e-6 >= report.maxRotorCoaxialLoadBias());
		assertTrue(report.maxRotorCoaxialLoadBiasClipping() >= 0.0);
		assertTrue(report.maxRotorCoaxialAllocationLoadFraction() > 0.10);
		assertTrue(report.maxRotorCoaxialAllocationCommandRatio() > 1.02);
		assertTrue(report.maxRotorCoaxialAllocationMechanicalGainPercent() > 0.10);
		assertTrue(report.maxRotorCoaxialAllocationElectricalGainPercent() > 0.10);
		assertTrue(report.maxRotorCoaxialAllocationUncertaintyPercent() > 0.10);
		assertTrue(Double.isFinite(report.maxRotorWindmillingIntensity()));
		assertTrue(Double.isFinite(report.maxRotorWakeSwirlTorqueNewtonMeters()));
		assertTrue(Double.isFinite(report.maxRotorActiveBrakingTorqueNewtonMeters()));
		assertTrue(Double.isFinite(report.maxRotorAccelerationReactionTorqueNewtonMeters()));
		assertTrue(Double.isFinite(report.maxRotorGyroscopicTorqueNewtonMeters()));
		assertTrue(Double.isFinite(report.maxRotorFlappingTorqueNewtonMeters()));
	}

	private static DroneConfig withCommonGains(DroneConfig config, PidGains gains) {
		return config
				.withPitchGains(gains)
				.withYawGains(gains)
				.withRollGains(gains);
	}

	private static double openCircuitVoltageAtStateOfCharge(DroneConfig config, double stateOfCharge) {
		DronePhysics physics = new DronePhysics(config);
		double capacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0;
		physics.state().setBatteryAmpSecondsConsumed(capacityAmpSeconds * (1.0 - stateOfCharge));
		physics.step(DroneInput.idle(), 0.005);
		return physics.state().batteryOpenCircuitVoltage();
	}

	private static double effectiveBatteryResistanceAt(DroneConfig config, double stateOfCharge, double equivalentCycles) {
		DronePhysics physics = new DronePhysics(config);
		physics.state().setBatteryEquivalentCycles(equivalentCycles);
		physics.step(DroneInput.idle(), 0.005);
		double capacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0 * physics.state().batteryCapacityAgingScale();
		physics.state().setBatteryAmpSecondsConsumed(capacityAmpSeconds * (1.0 - stateOfCharge));
		physics.step(DroneInput.idle(), 0.005);
		return physics.state().batteryEffectiveResistanceOhms();
	}

	private static double effectiveBatteryResistanceAt(
			DroneConfig config,
			double stateOfCharge,
			double equivalentCycles,
			double ambientTemperatureCelsius
	) {
		DronePhysics physics = new DronePhysics(config);
		physics.state().setBatteryEquivalentCycles(equivalentCycles);
		physics.step(DroneInput.idle(), 0.005, environmentWithAmbientTemperature(ambientTemperatureCelsius));
		double capacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0 * physics.state().batteryCapacityAgingScale();
		physics.state().setBatteryAmpSecondsConsumed(capacityAmpSeconds * (1.0 - stateOfCharge));
		physics.step(DroneInput.idle(), 0.005, environmentWithAmbientTemperature(ambientTemperatureCelsius));
		return physics.state().batteryEffectiveResistanceOhms();
	}

	private static DronePhysics batteryPhysicsAt(
			DroneConfig config,
			double stateOfCharge,
			double equivalentCycles,
			double ambientTemperatureCelsius
	) {
		DronePhysics physics = new DronePhysics(config);
		physics.state().setBatteryEquivalentCycles(equivalentCycles);
		physics.step(DroneInput.idle(), 0.005, environmentWithAmbientTemperature(ambientTemperatureCelsius));
		double capacityAmpSeconds = config.batteryCapacityAmpHours() * 3600.0 * physics.state().batteryCapacityAgingScale();
		physics.state().setBatteryAmpSecondsConsumed(capacityAmpSeconds * (1.0 - stateOfCharge));
		physics.step(DroneInput.idle(), 0.005, environmentWithAmbientTemperature(ambientTemperatureCelsius));
		return physics;
	}

	private static double reconstructedBatteryResistance(DroneConfig config, DroneState state) {
		return config.batteryInternalResistanceOhms()
				* state.batteryTemperatureResistanceScale()
				* state.batteryStateOfChargeResistanceScale()
				* state.batteryResistanceAgingScale()
				* state.batteryPolarizationResistanceScale();
	}

	private static double recordDouble(Object record, String accessorName) throws ReflectiveOperationException {
		Method accessor = record.getClass().getDeclaredMethod(accessorName);
		accessor.setAccessible(true);
		return (double) accessor.invoke(record);
	}

	private static void preparePropellerPowerCurrentEstimate(
			DronePhysics physics,
			double rpmFraction,
			double thrustFraction,
			double aerodynamicLoadFactor,
			double propellerPowerScale
	) {
		RotorSpec rotor = physics.config().rotors().get(0);
		physics.state().setBatteryVoltage(physics.config().nominalBatteryVoltage());
		physics.state().setEscOutputCommand(0, rpmFraction);
		physics.state().setEscElectricalOutputCommand(0, rpmFraction);
		physics.state().setEscElectricalOutputError(0, 0.0);
		physics.state().setMotorOmegaRadiansPerSecond(0, rotor.maxOmegaRadiansPerSecond() * rpmFraction);
		physics.state().setMotorVoltageHeadroom(0, 0.45);
		physics.state().setMotorShaftPowerWatts(0, 0.0);
		physics.state().setRotorThrustNewtons(0, rotor.maxThrustNewtons() * thrustFraction);
		physics.state().setRotorAerodynamicLoadFactor(0, aerodynamicLoadFactor);
		physics.state().setRotorPropellerPowerScale(0, propellerPowerScale);
	}

	private static void preparePropellerPowerSpinupEstimate(
			DronePhysics physics,
			double rpmFraction,
			double propellerPowerScale
	) {
		holdInStillAir(physics);
		for (int i = 0; i < physics.state().motorCount(); i++) {
			RotorSpec rotor = physics.config().rotors().get(i);
			physics.state().setMotorOmegaRadiansPerSecond(i, rotor.maxOmegaRadiansPerSecond() * rpmFraction);
			physics.state().setRotorAerodynamicLoadFactor(i, 1.0);
			physics.state().setRotorPropellerPowerScale(i, propellerPowerScale);
		}
	}

	private static DroneEnvironment environmentWithAmbientTemperature(double ambientTemperatureCelsius) {
		return new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				0.0,
				ambientTemperatureCelsius
		);
	}

	private static DroneConfig directControl(DroneConfig config) {
		return config
				.withControlLink(0.0, 0.0, config.rcFailsafeTimeoutSeconds())
				.withControlReceiver(0.0, 0.0)
				.withEscCommandSignal(0.0, 0.0)
				.withRateSuper(Vec3.ZERO);
	}

	private static double shapedRateDegreesPerSecond(double input, double expo, double superRate, double maxRateRadiansPerSecond) {
		double clamped = Math.max(-1.0, Math.min(1.0, input));
		double expoClamped = Math.max(0.0, Math.min(1.0, expo));
		double superRateClamped = Math.max(0.0, Math.min(0.95, superRate));
		double centerSensitivityFraction = (1.0 - expoClamped) * (1.0 - superRateClamped);
		double commandAbs = Math.abs(clamped);
		double commandSquared = clamped * clamped;
		double commandFifth = commandSquared * commandSquared * clamped;
		double expoCurve = commandAbs * (commandFifth * expoClamped + clamped * (1.0 - expoClamped));
		double stickMovementFraction = Math.max(0.0, 1.0 - centerSensitivityFraction);
		double shaped = Math.max(-1.0, Math.min(1.0, clamped * centerSensitivityFraction + stickMovementFraction * expoCurve));
		return Math.toDegrees(shaped * maxRateRadiansPerSecond);
	}

	private static void assertVecClose(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.y(), actual.y(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.z(), actual.z(), tolerance, () -> "expected=" + expected + " actual=" + actual);
	}

	private static void holdInStillAir(DronePhysics physics) {
		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		physics.state().setVelocityMetersPerSecond(Vec3.ZERO);
		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
	}

	private static void holdInCoolingCrossflow(DronePhysics physics, Vec3 velocityMetersPerSecond) {
		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
		physics.state().setVelocityMetersPerSecond(velocityMetersPerSecond);
	}

	private static void holdInCruise(DronePhysics physics, Vec3 velocityMetersPerSecond) {
		physics.state().setPositionMeters(new Vec3(0.0, 20.0, 0.0));
		physics.state().setVelocityMetersPerSecond(velocityMetersPerSecond);
		physics.state().setOrientation(Quaternion.IDENTITY);
		physics.state().setAngularVelocityBodyRadiansPerSecond(Vec3.ZERO);
	}

	private static DroneEnvironment rainEnvironmentWithDensity(double airDensityRatio, double precipitationWetness, double ambientTemperatureCelsius) {
		return new DroneEnvironment(
				Vec3.ZERO,
				airDensityRatio,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				precipitationWetness,
				ambientTemperatureCelsius
		);
	}

	private static DroneEnvironment a4mcAblWind(double turbulenceIntensity, double ablStability, double ablMixingStrength) {
		return a4mcAblWind(turbulenceIntensity, ablStability, ablMixingStrength, true, 1.0, -1L);
	}

	private static DroneEnvironment a4mcAblWind(
			double turbulenceIntensity,
			double ablStability,
			double ablMixingStrength,
			boolean trustedForGameplay,
			double confidence,
			long freshnessAgeTicks
	) {
		return new DroneEnvironment(
				new Vec3(9.0, 0.0, 0.0),
				1.0,
				6.0,
				turbulenceIntensity,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				null,
				null,
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				trustedForGameplay,
				confidence,
				turbulenceIntensity,
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				DroneEnvironment.WIND_SOURCE_LEVEL_NONE,
				DroneEnvironment.WIND_SOURCE_AUTHORITY_NONE,
				freshnessAgeTicks,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				ablStability,
				ablMixingStrength
		);
	}

	private static DroneEnvironment a4mcTerrainShearWind(double shearMagnitudePerBlock, double updraftMetersPerSecond) {
		return a4mcTerrainShearWind(shearMagnitudePerBlock, updraftMetersPerSecond, 0.0);
	}

	private static DroneEnvironment a4mcUpdraftOnlyWind(double updraftMetersPerSecond) {
		return a4mcUpdraftOnlyWind(updraftMetersPerSecond, true, 1.0, -1L);
	}

	private static DroneEnvironment a4mcUpdraftOnlyWind(
			double updraftMetersPerSecond,
			boolean trustedForGameplay,
			double confidence,
			long freshnessAgeTicks
	) {
		return new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				6.0,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				null,
				null,
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				trustedForGameplay,
				confidence,
				0.0,
				0.0,
				0.0,
				0.0,
				updraftMetersPerSecond,
				true,
				"l2",
				"server_authoritative",
				freshnessAgeTicks,
				0.0,
				Math.abs(updraftMetersPerSecond),
				0.0,
				false,
				0.0,
				false,
				0.0,
				0.0,
				0.0
		);
	}

	private static DroneEnvironment a4mcShelterCoolingWind(double shelterFactor, boolean localVoxelFlow) {
		return new DroneEnvironment(
				Vec3.ZERO,
				1.0,
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				null,
				null,
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				true,
				1.0,
				0.0,
				0.0,
				0.0,
				shelterFactor,
				0.0,
				localVoxelFlow,
				"l2",
				"server_authoritative",
				0L,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				0.0,
				0.0
		);
	}

	private static DroneEnvironment a4mcSourceGustWind(double sourceGustSpeedMetersPerSecond) {
		return a4mcSourceGustWind(sourceGustSpeedMetersPerSecond, Vec3.ZERO);
	}

	private static DroneEnvironment a4mcSourceGustWind(double sourceGustSpeedMetersPerSecond, Vec3 sourceGustVelocity) {
		return a4mcSourceGustWind(sourceGustSpeedMetersPerSecond, sourceGustVelocity, true, 1.0, -1L);
	}

	private static DroneEnvironment a4mcSourceGustWind(
			double sourceGustSpeedMetersPerSecond,
			Vec3 sourceGustVelocity,
			boolean trustedForGameplay,
			double confidence,
			long freshnessAgeTicks
	) {
		return new DroneEnvironment(
				new Vec3(7.0, 0.0, 0.0),
				1.0,
				6.0,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				null,
				null,
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				trustedForGameplay,
				confidence,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				DroneEnvironment.WIND_SOURCE_LEVEL_NONE,
				DroneEnvironment.WIND_SOURCE_AUTHORITY_NONE,
				freshnessAgeTicks,
				7.0,
				7.0,
				sourceGustSpeedMetersPerSecond,
				false,
				0.0,
				false,
				0.0,
				0.0,
				0.0,
				sourceGustVelocity
		);
	}

	private static DroneEnvironment a4mcTerrainShearWind(
			double shearMagnitudePerBlock,
			double updraftMetersPerSecond,
			double shelterFactor
	) {
		return a4mcTerrainShearWind(shearMagnitudePerBlock, updraftMetersPerSecond, shelterFactor, true, 1.0, -1L);
	}

	private static DroneEnvironment a4mcTerrainShearWind(
			double shearMagnitudePerBlock,
			double updraftMetersPerSecond,
			double shelterFactor,
			boolean trustedForGameplay,
			double confidence,
			long freshnessAgeTicks
	) {
		return new DroneEnvironment(
				new Vec3(7.0, 0.0, 0.0),
				1.0,
				6.0,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				25.0,
				null,
				null,
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				trustedForGameplay,
				confidence,
				0.0,
				0.0,
				shearMagnitudePerBlock,
				shelterFactor,
				updraftMetersPerSecond,
				false,
				DroneEnvironment.WIND_SOURCE_LEVEL_NONE,
				DroneEnvironment.WIND_SOURCE_AUTHORITY_NONE,
				freshnessAgeTicks,
				7.0,
				7.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				0.0,
				0.0
		);
	}

	private static double maxA4mcSourceGustFor(DroneEnvironment environment) {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		double maxSourceGust = 0.0;
		for (int i = 0; i < 260; i++) {
			physics.step(idle, 0.005, environment);
			maxSourceGust = Math.max(maxSourceGust, physics.state().a4mcSourceGustSpeedMetersPerSecond());
		}
		return maxSourceGust;
	}

	private static double maxA4mcUpdraftFor(DroneEnvironment environment) {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		double maxUpdraft = 0.0;
		for (int i = 0; i < 520; i++) {
			physics.step(idle, 0.005, environment);
			maxUpdraft = Math.max(maxUpdraft, physics.state().a4mcUpdraftSpeedMetersPerSecond());
		}
		return maxUpdraft;
	}

	private static double maxA4mcTerrainShearFor(DroneEnvironment environment) {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		double maxTerrainShear = 0.0;
		for (int i = 0; i < 520; i++) {
			physics.step(idle, 0.005, environment);
			maxTerrainShear = Math.max(maxTerrainShear, physics.state().a4mcTerrainShearSpeedMetersPerSecond());
		}
		return maxTerrainShear;
	}

	private static double drydenRmsForEnvironment(DroneEnvironment environment) {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		double sumSquared = 0.0;
		int samples = 0;
		for (int i = 0; i < 1800; i++) {
			physics.step(idle, 0.005, environment);
			if (i >= 240) {
				sumSquared += physics.state().drydenTurbulenceVelocityWorldMetersPerSecond().lengthSquared();
				samples++;
			}
		}
		return Math.sqrt(sumSquared / samples);
	}

	private static double drydenNormalizedStepRateForEnvironment(DroneEnvironment environment) {
		DronePhysics physics = new DronePhysics(directControl(DroneConfig.racingQuad()));
		DroneInput idle = DroneInput.idle();
		double sumSquared = 0.0;
		double sumDeltaSquared = 0.0;
		int samples = 0;
		int deltaSamples = 0;
		Vec3 previous = Vec3.ZERO;
		boolean hasPrevious = false;
		for (int i = 0; i < 1800; i++) {
			physics.step(idle, 0.005, environment);
			if (i >= 240) {
				Vec3 current = physics.state().drydenTurbulenceVelocityWorldMetersPerSecond();
				sumSquared += current.lengthSquared();
				samples++;
				if (hasPrevious) {
					sumDeltaSquared += current.subtract(previous).lengthSquared();
					deltaSamples++;
				}
				previous = current;
				hasPrevious = true;
			}
		}
		double rms = Math.sqrt(sumSquared / samples);
		double deltaRms = Math.sqrt(sumDeltaSquared / deltaSamples);
		return deltaRms / Math.max(1.0e-9, rms);
	}

	private static double averageRotorThrust(DroneState state) {
		double[] thrust = state.rotorThrustNewtons();
		double sum = 0.0;
		for (double value : thrust) {
			sum += value;
		}
		return thrust.length == 0 ? 0.0 : sum / thrust.length;
	}

	private static double averageRotorThrustForSpin(DronePhysics physics, int spinDirection) {
		double sum = 0.0;
		int count = 0;
		for (int i = 0; i < physics.config().rotors().size(); i++) {
			if (Integer.signum(physics.config().rotors().get(i).spinDirection()) == Integer.signum(spinDirection)) {
				sum += physics.state().rotorThrustNewtons(i);
				count++;
			}
		}
		return count == 0 ? 0.0 : sum / count;
	}

	private static double averageRotorAdvanceForSpin(DronePhysics physics, int spinDirection) {
		double sum = 0.0;
		int count = 0;
		for (int i = 0; i < physics.config().rotors().size(); i++) {
			if (Integer.signum(physics.config().rotors().get(i).spinDirection()) == Integer.signum(spinDirection)) {
				sum += physics.state().rotorAdvanceRatio(i);
				count++;
			}
		}
		return count == 0 ? 0.0 : sum / count;
	}

	private static double rotorEffectiveThrustRatio(DronePhysics physics, int rotorIndex) {
		RotorSpec rotor = physics.config().rotors().get(rotorIndex);
		double omega = physics.state().motorOmegaRadiansPerSecond(rotorIndex);
		double idealThrust = rotor.thrustCoefficient() * omega * omega;
		return idealThrust <= 1.0e-9 ? 0.0 : physics.state().rotorThrustNewtons(rotorIndex) / idealThrust;
	}

	private static double densityNormalizedRotorThrust(DroneConfig config, DroneState state, double airDensityRatio) {
		double thrustSum = 0.0;
		double idealSum = 0.0;
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			double omega = state.motorOmegaRadiansPerSecond(i);
			thrustSum += state.rotorThrustNewtons(i);
			idealSum += rotor.thrustCoefficient() * omega * omega * Math.max(1.0e-6, airDensityRatio);
		}
		return idealSum <= 1.0e-9 ? 0.0 : thrustSum / idealSum;
	}

	private static double settledCoaxialLoadBias(DroneConfig config, double throttleOffset) {
		DronePhysics physics = new DronePhysics(config);
		DroneInput input = new DroneInput(config.hoverThrottle() + throttleOffset, 0.0, 0.0, 0.0, true);
		for (int i = 0; i < 700; i++) {
			holdInStillAir(physics);
			physics.step(input, 0.005);
		}
		return physics.state().maxAbsRotorCoaxialLoadBias();
	}

	private static DroneConfig withCoaxialX8Spacing(DroneConfig base, double spacingOverDiameter) {
		RotorSpec template = base.rotors().get(0);
		double arm = Math.hypot(template.positionBodyMeters().x(), template.positionBodyMeters().z());
		double verticalOffset = template.radiusMeters() * spacingOverDiameter;
		return base.withRotors(List.of(
				rotorLike(template, rotorPositionAtDegrees(45.0, arm, verticalOffset), 1),
				rotorLike(template, rotorPositionAtDegrees(45.0, arm, -verticalOffset), -1),
				rotorLike(template, rotorPositionAtDegrees(135.0, arm, verticalOffset), -1),
				rotorLike(template, rotorPositionAtDegrees(135.0, arm, -verticalOffset), 1),
				rotorLike(template, rotorPositionAtDegrees(225.0, arm, verticalOffset), 1),
				rotorLike(template, rotorPositionAtDegrees(225.0, arm, -verticalOffset), -1),
				rotorLike(template, rotorPositionAtDegrees(315.0, arm, verticalOffset), -1),
				rotorLike(template, rotorPositionAtDegrees(315.0, arm, -verticalOffset), 1)
		));
	}

	private static Vec3 rotorPositionAtDegrees(double angleDegrees, double armMeters, double verticalOffsetMeters) {
		double angleRadians = Math.toRadians(angleDegrees);
		return new Vec3(
				Math.sin(angleRadians) * armMeters,
				verticalOffsetMeters,
				Math.cos(angleRadians) * armMeters
		);
	}

	private static Vec3 torqueFromRotorDeltas(DroneConfig config, double[] deltas) {
		Vec3 torque = Vec3.ZERO;
		for (int i = 0; i < config.rotors().size(); i++) {
			RotorSpec rotor = config.rotors().get(i);
			Vec3 arm = rotor.positionBodyMeters().subtract(config.centerOfMassOffsetBodyMeters());
			torque = torque.add(DronePhysics.rotorTorqueCoefficientPerThrust(rotor, arm).multiply(deltas[i]));
		}
		return torque;
	}

	private static double sum(double[] values) {
		double sum = 0.0;
		for (double value : values) {
			sum += value;
		}
		return sum;
	}

	private static void assertLiftPropPitch(DroneConfig config) {
		RotorSpec rotor = config.rotors().get(0);
		assertEquals(DroneConfig.LARGE_LIFT_PROP_PITCH_TO_DIAMETER_RATIO, rotor.bladePitchToDiameterRatio(), 1.0e-12);
		assertEquals(rotor.radiusMeters(), rotor.bladePitchMeters(), 1.0e-12);
		assertEquals(12.81, Math.toDegrees(rotor.geometricBladePitchAngleRadians()), 0.03);
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
				template.motorWindingResistanceOhms(),
				template.motorPolePairs(),
				template.bladeCount()
		);
	}

	private static Quaternion rollOrientation(double rollRadians) {
		double halfAngle = rollRadians * 0.5;
		return new Quaternion(Math.cos(halfAngle), 0.0, 0.0, Math.sin(halfAngle));
	}

	private static Quaternion pitchOrientation(double pitchRadians) {
		double halfAngle = pitchRadians * 0.5;
		return new Quaternion(Math.cos(halfAngle), Math.sin(halfAngle), 0.0, 0.0);
	}

	private static int indexOf(String[] header, String column) {
		for (int i = 0; i < header.length; i++) {
			if (header[i].equals(column)) {
				return i;
			}
		}
		throw new AssertionError("Missing column " + column);
	}

	private static double maxColumn(List<String> lines, String[] header, String column) {
		int index = indexOf(header, column);
		double max = 0.0;
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			max = Math.max(max, Double.parseDouble(row[index]));
		}
		return max;
	}

	private static double minColumn(List<String> lines, String[] header, String column) {
		int index = indexOf(header, column);
		double min = Double.POSITIVE_INFINITY;
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			min = Math.min(min, Double.parseDouble(row[index]));
		}
		return min;
	}

	private static double maxAbsColumn(List<String> lines, String[] header, String column) {
		int index = indexOf(header, column);
		double max = 0.0;
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			max = Math.max(max, Math.abs(Double.parseDouble(row[index])));
		}
		return max;
	}

	private static double maxAbsColumnDifference(List<String> lines, String[] header, String aColumn, String bColumn) {
		int aIndex = indexOf(header, aColumn);
		int bIndex = indexOf(header, bColumn);
		double max = 0.0;
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			double a = Double.parseDouble(row[aIndex]);
			double b = Double.parseDouble(row[bIndex]);
			max = Math.max(max, Math.abs(a - b));
		}
		return max;
	}

	private static double maxVectorLength(List<String> lines, String[] header, String xColumn, String yColumn, String zColumn) {
		int xIndex = indexOf(header, xColumn);
		int yIndex = indexOf(header, yColumn);
		int zIndex = indexOf(header, zColumn);
		double max = 0.0;
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			double x = Double.parseDouble(row[xIndex]);
			double y = Double.parseDouble(row[yIndex]);
			double z = Double.parseDouble(row[zIndex]);
			max = Math.max(max, Math.sqrt(x * x + y * y + z * z));
		}
		return max;
	}
}
