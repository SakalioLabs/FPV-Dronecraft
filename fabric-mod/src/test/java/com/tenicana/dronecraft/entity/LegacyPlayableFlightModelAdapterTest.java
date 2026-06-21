package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.Vec3;
import com.tenicana.dronecraft.sim.flight.FlightModelInitializationContext;
import com.tenicana.dronecraft.sim.flight.FlightStateSnapshot;
import com.tenicana.dronecraft.sim.flight.FlightStepContext;
import com.tenicana.dronecraft.sim.flight.FlightStepResult;
import com.tenicana.dronecraft.sim.flight.StateCorrectionReason;

class LegacyPlayableFlightModelAdapterTest {
	@Test
	void exposesPlayableCapabilitiesAndFiniteStep() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(
				config,
				new FlightStateSnapshot(new Vec3(0.0, 20.0, 0.0), Vec3.ZERO, null, Vec3.ZERO, FlightMode.HORIZON, false),
				DroneEnvironment.calm(),
				0L
		));

		FlightStepResult result = adapter.step(new FlightStepContext(
				new DroneInput(0.52, 0.35, -0.20, 0.10, true, true, FlightMode.HORIZON),
				adapter.snapshot(),
				DroneEnvironment.calm(),
				0.05,
				1L,
				config
		));

		assertEquals("legacy_playable_direct", adapter.id());
		assertTrue(adapter.capabilities().motorTelemetry());
		assertTrue(adapter.capabilities().assistedStateCorrection());
		assertTrue(adapter.capabilities().lossyStateMapping());
		assertTrue(result.nextState().isFinite());
		assertTrue(result.actuatorOutput().averageMotorRpm() > 0.0);
		assertEquals(FlightMode.HORIZON, result.nextState().flightMode());
	}

	@Test
	void reportsUnrepresentedWindWithoutApplyingIt() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config, FlightStateSnapshot.zero(FlightMode.HORIZON), DroneEnvironment.calm(), 0L));

		FlightStepResult calm = adapter.step(new FlightStepContext(
				new DroneInput(0.54, 0.35, 0.0, 0.0, true, true, FlightMode.HORIZON),
				adapter.snapshot(),
				DroneEnvironment.calm(),
				0.05,
				1L,
				config
		));
		Vec3 calmVelocity = calm.nextState().velocityWorldMetersPerSecond();

		adapter.initialize(new FlightModelInitializationContext(config, FlightStateSnapshot.zero(FlightMode.HORIZON), DroneEnvironment.calm(), 0L));
		FlightStepResult windy = adapter.step(new FlightStepContext(
				new DroneInput(0.54, 0.35, 0.0, 0.0, true, true, FlightMode.HORIZON),
				adapter.snapshot(),
				new DroneEnvironment(new Vec3(5.0, 0.0, -2.0), 1.0, Double.POSITIVE_INFINITY),
				0.05,
				1L,
				config
		));

		assertVecClose(calmVelocity, windy.nextState().velocityWorldMetersPerSecond(), 1.0e-12);
		assertTrue(windy.diagnostics().lossyFields().contains("environment.windVelocityWorldMetersPerSecond"));
	}

	@Test
	void idleDisarmedStepReportsExplicitCorrection() {
		DroneConfig config = DroneConfig.racingQuad();
		LegacyPlayableFlightModelAdapter adapter = new LegacyPlayableFlightModelAdapter();
		adapter.initialize(new FlightModelInitializationContext(config, FlightStateSnapshot.zero(FlightMode.ANGLE), DroneEnvironment.calm(), 0L));

		FlightStepResult result = adapter.step(new FlightStepContext(
				new DroneInput(0.0, 0.0, 0.0, 0.0, false, true, FlightMode.ANGLE),
				adapter.snapshot(),
				DroneEnvironment.calm(),
				0.05,
				1L,
				config
		));

		assertFalse(result.nextState().armed());
		assertEquals(StateCorrectionReason.GROUND_STABILIZATION, result.stateCorrections().get(0).reason());
		assertEquals(0.0, result.actuatorOutput().averageMotorPower(), 1.0e-12);
	}

	private static void assertVecClose(Vec3 expected, Vec3 actual, double tolerance) {
		assertEquals(expected.x(), actual.x(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.y(), actual.y(), tolerance, () -> "expected=" + expected + " actual=" + actual);
		assertEquals(expected.z(), actual.z(), tolerance, () -> "expected=" + expected + " actual=" + actual);
	}
}
