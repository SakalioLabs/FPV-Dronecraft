package com.tenicana.dronecraft.sim.flight;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

class SimulationFlightModelAdapterTest {
	@Test
	void stepMatchesWrappedDronePhysics() {
		DroneConfig config = deterministic(DroneConfig.racingQuad());
		FlightStateSnapshot initial = new FlightStateSnapshot(
				new Vec3(0.0, 20.0, 0.0),
				new Vec3(1.0, 0.0, -0.5),
				Quaternion.IDENTITY,
				Vec3.ZERO,
				FlightMode.HORIZON,
				true
		);
		DroneInput input = new DroneInput(0.55, 0.25, -0.10, 0.08, true, true, FlightMode.HORIZON);
		DroneEnvironment environment = new DroneEnvironment(new Vec3(2.0, 0.0, -1.0), 1.0, Double.POSITIVE_INFINITY);
		DronePhysics physics = new DronePhysics(config);
		physics.state().setPositionMeters(initial.positionWorldMeters());
		physics.state().setVelocityMetersPerSecond(initial.velocityWorldMetersPerSecond());
		physics.state().setOrientation(initial.attitude());
		physics.state().setEstimatedOrientation(initial.attitude());
		physics.state().setAngularVelocityBodyRadiansPerSecond(initial.angularVelocityBodyRadiansPerSecond());

		SimulationFlightModelAdapter adapter = new SimulationFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config, initial, environment, 0L));

		physics.step(input, 0.005, environment);
		FlightStepResult result = adapter.step(new FlightStepContext(input, initial, environment, 0.005, 1L, config));

		DroneState expected = physics.state();
		FlightStateSnapshot actual = result.nextState();
		assertVecClose(expected.positionMeters(), actual.positionWorldMeters(), 1.0e-12);
		assertVecClose(expected.velocityMetersPerSecond(), actual.velocityWorldMetersPerSecond(), 1.0e-12);
		assertQuaternionClose(expected.orientation(), actual.attitude(), 1.0e-12);
		assertVecClose(expected.angularVelocityBodyRadiansPerSecond(), actual.angularVelocityBodyRadiansPerSecond(), 1.0e-12);
		assertEquals(expected.averageMotorRpm(), result.actuatorOutput().averageMotorRpm(), 1.0e-12);
		assertEquals(expected.averageMotorPower(config), result.actuatorOutput().averageMotorPower(), 1.0e-12);
		assertTrue(result.diagnostics().finite());
	}

	@Test
	void configChangeIsReportedAsCorrection() {
		DroneConfig config = deterministic(DroneConfig.racingQuad());
		DroneConfig warmerBattery = config.withBattery(16.8, 16.7, 0.020, 20.0, 90.0);
		SimulationFlightModelAdapter adapter = new SimulationFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config, FlightStateSnapshot.zero(FlightMode.HORIZON), DroneEnvironment.calm(), 0L));

		FlightStepResult result = adapter.step(new FlightStepContext(
				new DroneInput(0.2, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON),
				adapter.snapshot(),
				DroneEnvironment.calm(),
				0.005,
				1L,
				warmerBattery
		));

		assertEquals(StateCorrectionReason.MODEL_INITIALIZATION, result.stateCorrections().get(0).reason());
		assertEquals(warmerBattery, adapter.physics().config());
	}

	@Test
	void resolvedContactStatePreservesEstimatorButResetSynchronizesIt() {
		DroneConfig config = deterministic(DroneConfig.racingQuad());
		SimulationFlightModelAdapter adapter = new SimulationFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config, FlightStateSnapshot.zero(FlightMode.HORIZON), DroneEnvironment.calm(), 0L));
		Quaternion estimator = new Quaternion(0.98, 0.01, 0.14, 0.02).normalized();
		Quaternion resolvedAttitude = new Quaternion(0.97, 0.03, 0.23, 0.01).normalized();
		adapter.physics().state().setEstimatedOrientation(estimator);
		FlightStateSnapshot resolved = new FlightStateSnapshot(
				new Vec3(1.0, 2.0, 3.0),
				new Vec3(0.5, -0.25, 0.75),
				resolvedAttitude,
				new Vec3(0.1, 0.2, 0.3),
				FlightMode.HORIZON,
				true
		);

		adapter.applyResolvedState(
				resolved,
				new StateCorrection(StateCorrectionReason.COLLISION_CONTACT_SOLVE, "TEST_CONTACT", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)
		);

		assertQuaternionClose(resolvedAttitude, adapter.physics().state().orientation(), 1.0e-12);
		assertQuaternionClose(estimator, adapter.physics().state().estimatedOrientation(), 1.0e-12);

		adapter.applyResolvedState(
				resolved,
				new StateCorrection(StateCorrectionReason.RESET_TELEPORT, "TEST_RESET", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)
		);

		assertQuaternionClose(resolvedAttitude, adapter.physics().state().estimatedOrientation(), 1.0e-12);
	}

	@Test
	void stateOnlyStepMatchesRichStepAndRefreshesDiagnosticsOnDemand() {
		DroneConfig config = deterministic(DroneConfig.racingQuad());
		FlightStateSnapshot initial = new FlightStateSnapshot(
				new Vec3(0.5, 12.0, -1.0),
				new Vec3(2.0, -0.25, 1.5),
				new Quaternion(0.98, 0.04, -0.16, 0.08).normalized(),
				new Vec3(0.2, -0.1, 0.3),
				FlightMode.ACRO,
				true
		);
		DroneEnvironment environment = new DroneEnvironment(new Vec3(3.0, 0.5, -2.0), 0.96, Double.POSITIVE_INFINITY);
		SimulationFlightModelAdapter rich = new SimulationFlightModelAdapter();
		SimulationFlightModelAdapter stateOnly = new SimulationFlightModelAdapter();
		rich.initialize(new FlightModelInitializationContext(config, initial, environment, 0L));
		stateOnly.initialize(new FlightModelInitializationContext(config, initial, environment, 0L));
		FlightModelDiagnostics diagnosticsBefore = stateOnly.diagnostics();

		for (int tick = 0; tick < 20; tick++) {
			DroneInput input = new DroneInput(
					0.42 + tick * 0.012,
					Math.sin(tick * 0.21) * 0.65,
					Math.cos(tick * 0.17) * 0.55,
					Math.sin(tick * 0.09) * 0.35,
					true,
					true,
					FlightMode.ACRO
			);
			FlightStepResult richResult = rich.step(new FlightStepContext(
					input,
					rich.snapshot(),
					environment,
					0.005,
					tick,
					config
			));
			stateOnly.stepStateOnly(input, environment, 0.005, tick, config, Map.of());

			FlightStateSnapshot actual = stateOnly.snapshot();
			assertVecClose(richResult.nextState().positionWorldMeters(), actual.positionWorldMeters(), 1.0e-12);
			assertVecClose(richResult.nextState().velocityWorldMetersPerSecond(), actual.velocityWorldMetersPerSecond(), 1.0e-12);
			assertQuaternionClose(richResult.nextState().attitude(), actual.attitude(), 1.0e-12);
			assertVecClose(richResult.nextState().angularVelocityBodyRadiansPerSecond(), actual.angularVelocityBodyRadiansPerSecond(), 1.0e-12);
			assertQuaternionClose(rich.physics().state().estimatedOrientation(), stateOnly.physics().state().estimatedOrientation(), 1.0e-12);
			assertEquals(rich.physics().state().batteryVoltage(), stateOnly.physics().state().batteryVoltage(), 1.0e-12);
			assertArrayEquals(rich.physics().state().motorRpm(), stateOnly.physics().state().motorRpm(), 1.0e-12);
			assertArrayEquals(rich.physics().state().rotorThrustNewtons(), stateOnly.physics().state().rotorThrustNewtons(), 1.0e-12);
		}

		FlightModelDiagnostics refreshed = stateOnly.diagnostics();
		assertNotSame(diagnosticsBefore, refreshed);
		assertTrue(refreshed.finite());
		assertEquals(
				stateOnly.physics().state().averageMotorRpm(),
				Double.parseDouble(refreshed.values().get("average_motor_rpm")),
				1.0e-12
		);
		assertSame(refreshed, stateOnly.diagnostics());
		assertThrows(IllegalArgumentException.class, () -> stateOnly.stepStateOnly(
				DroneInput.idle(),
				environment,
				Double.NaN,
				21L,
				config,
				Map.of()
		));
	}

	@Test
	void stateOnlyStepSynchronizesExternallyAppliedConfigWithoutDelayedCorrection() {
		DroneConfig config = deterministic(DroneConfig.racingQuad());
		DroneConfig warmerBattery = config.withBattery(16.8, 16.7, 0.020, 20.0, 90.0);
		SimulationFlightModelAdapter adapter = new SimulationFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config, FlightStateSnapshot.zero(FlightMode.HORIZON), DroneEnvironment.calm(), 0L));
		adapter.physics().applyConfig(warmerBattery);

		DroneInput input = new DroneInput(0.3, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON);
		adapter.stepStateOnly(input, DroneEnvironment.calm(), 0.005, 1L, warmerBattery, Map.of());
		for (int tick = 2; tick <= 10; tick++) {
			adapter.stepStateOnly(input, DroneEnvironment.calm(), 0.005, tick, warmerBattery, Map.of());
		}

		assertEquals(warmerBattery, adapter.physics().config());
		assertEquals(StateCorrectionReason.MODEL_INITIALIZATION, adapter.diagnostics().stateCorrections().get(0).reason());
		FlightStepResult next = adapter.step(new FlightStepContext(
				input,
				adapter.snapshot(),
				DroneEnvironment.calm(),
				0.005,
				11L,
				warmerBattery
		));
		assertTrue(next.stateCorrections().isEmpty());
	}

	private static DroneConfig deterministic(DroneConfig config) {
		return config
				.withControlLink(0.0, 0.0, config.rcFailsafeTimeoutSeconds())
				.withControlReceiver(0.0, 0.0)
				.withEscCommandSignal(0.0, 0.0)
				.withFlightControllerSensors(1000.0, 0.0, 1000.0, 0.0, 0.0)
				.withRateSuper(Vec3.ZERO);
	}

	private static void assertVecClose(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.y(), actual.y(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.z(), actual.z(), tolerance, () -> "expected=" + expected + " actual=" + actual);
	}

	private static void assertQuaternionClose(Quaternion expected, Quaternion actual, double tolerance) {
		assertEquals(expected.w(), actual.w(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.x(), actual.x(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.y(), actual.y(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.z(), actual.z(), tolerance, () -> "expected=" + expected + " actual=" + actual);
	}
}
