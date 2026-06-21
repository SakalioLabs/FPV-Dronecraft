package com.tenicana.dronecraft.sim.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
