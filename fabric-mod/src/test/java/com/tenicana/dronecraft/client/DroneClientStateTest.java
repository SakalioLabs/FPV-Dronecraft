package com.tenicana.dronecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.FlightMode;

class DroneClientStateTest {
	@Test
	void defaultsToAngleModeForFirstTimeControl() {
		DroneClientState.updateControls(
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				false,
				false,
				false,
				false,
				null,
				DroneClientState.InputSource.KEYBOARD,
				true,
				false
		);

		assertEquals(FlightMode.ANGLE, DroneClientState.flightMode());
	}

	@Test
	void hudModeCyclesFromMinimalToOffBeforeFullTelemetry() {
		DroneClientState.setHudMode(DroneClientState.HudMode.MINIMAL);

		assertEquals(DroneClientState.HudMode.OFF, DroneClientState.cycleHudMode());
		assertFalse(DroneClientState.isHudEnabled());
		assertEquals(DroneClientState.HudMode.FULL, DroneClientState.cycleHudMode());
		assertTrue(DroneClientState.isHudEnabled());
		assertEquals(DroneClientState.HudMode.MINIMAL, DroneClientState.cycleHudMode());
		assertTrue(DroneClientState.isHudEnabled());
	}

	@Test
	void legacyHudToggleMapsToMinimalOrOff() {
		DroneClientState.setHudMode(DroneClientState.HudMode.FULL);

		DroneClientState.setHudEnabled(false);
		assertEquals(DroneClientState.HudMode.OFF, DroneClientState.hudMode());
		assertFalse(DroneClientState.isHudEnabled());

		DroneClientState.setHudEnabled(true);
		assertEquals(DroneClientState.HudMode.MINIMAL, DroneClientState.hudMode());
		assertTrue(DroneClientState.isHudEnabled());
	}

	@Test
	void resetTransientFlightStateClearsFpvAndControlStateButKeepsHudMode() {
		DroneClientState.setHudMode(DroneClientState.HudMode.FULL);
		DroneClientState.setFpvViewEnabled(true);
		DroneClientState.updateControls(
				0.7f,
				-0.3f,
				0.4f,
				-0.5f,
				true,
				true,
				true,
				true,
				FlightMode.ACRO,
				DroneClientState.InputSource.GAMEPAD,
				false,
				true
		);

		DroneClientState.resetTransientFlightState();

		assertFalse(DroneClientState.isFpvViewEnabled());
		assertFalse(DroneClientState.hasController());
		assertFalse(DroneClientState.hasPhysicalController());
		assertFalse(DroneClientState.isVirtualControllerEnabled());
		assertEquals(0.0f, DroneClientState.throttle(), 1.0e-6f);
		assertEquals(0.0f, DroneClientState.pitch(), 1.0e-6f);
		assertEquals(0.0f, DroneClientState.roll(), 1.0e-6f);
		assertEquals(0.0f, DroneClientState.yaw(), 1.0e-6f);
		assertFalse(DroneClientState.armed());
		assertEquals(DroneClientState.DEFAULT_FLIGHT_MODE, DroneClientState.flightMode());
		assertEquals(DroneClientState.InputSource.KEYBOARD, DroneClientState.inputSource());
		assertFalse(DroneClientState.throttleCalibrationActive());
		assertEquals(DroneClientState.HudMode.FULL, DroneClientState.hudMode());
	}
}
