package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class DroneInputTest {
	@Test
	void idleAndFailsafeUseFirstFlightAngleMode() {
		DroneInput idle = DroneInput.idle();
		DroneInput failsafe = DroneInput.failsafe();

		assertFalse(idle.armed());
		assertFalse(idle.linkActive());
		assertEquals(FlightMode.DEFAULT_FIRST_FLIGHT, idle.flightMode());
		assertEquals(FlightMode.DEFAULT_FIRST_FLIGHT, failsafe.flightMode());
	}

	@Test
	void nullFlightModeNormalizesToFirstFlightAngleMode() {
		DroneInput input = new DroneInput(1.2, -2.0, 2.0, 0.25, true, true, null).normalized();

		assertEquals(1.0, input.throttle(), 1.0e-12);
		assertEquals(-1.0, input.pitch(), 1.0e-12);
		assertEquals(1.0, input.roll(), 1.0e-12);
		assertEquals(0.25, input.yaw(), 1.0e-12);
		assertEquals(FlightMode.DEFAULT_FIRST_FLIGHT, input.flightMode());
	}

	@Test
	void legacySimulationConstructorsRemainAcroUnlessModeIsExplicit() {
		assertEquals(FlightMode.ACRO, new DroneInput(0.0, 0.0, 0.0, 0.0, true).flightMode());
		assertEquals(FlightMode.ACRO, new DroneInput(0.0, 0.0, 0.0, 0.0, true, true).flightMode());
		assertEquals(FlightMode.HORIZON, new DroneInput(0.0, 0.0, 0.0, 0.0, true, FlightMode.HORIZON).flightMode());
	}

	@Test
	void nonFiniteAxesNormalizeToFailsafe() {
		DroneInput failsafe = DroneInput.failsafe();

		assertEquals(failsafe, new DroneInput(Double.NaN, 0.0, 0.0, 0.0, true, true).normalized());
		assertEquals(failsafe, new DroneInput(0.5, Double.POSITIVE_INFINITY, 0.0, 0.0, true, true).normalized());
		assertEquals(failsafe, new DroneInput(0.5, 0.0, Double.NEGATIVE_INFINITY, 0.0, true, true).normalized());
		assertEquals(failsafe, new DroneInput(0.5, 0.0, 0.0, Double.NaN, true, true).normalized());
	}
}
