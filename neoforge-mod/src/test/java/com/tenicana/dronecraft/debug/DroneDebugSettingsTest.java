package com.tenicana.dronecraft.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneDebugSettingsTest {
	@Test
	void playableDirectFlightIsDefault() {
		DroneDebugSettings.setFlightModelMode(DroneDebugSettings.FlightModelMode.PLAYABLE);

		assertTrue(DroneDebugSettings.bypassPhysicsEnabled());
		assertTrue(DroneDebugSettings.flightModelMode() == DroneDebugSettings.FlightModelMode.PLAYABLE);
		assertTrue(DroneDebugSettings.statusLine().contains("flight=playable"));
	}

	@Test
	void flightModelModeCanSwitchBetweenPlayableAndSimulation() {
		DroneDebugSettings.setFlightModelMode(DroneDebugSettings.FlightModelMode.SIMULATION);

		assertFalse(DroneDebugSettings.bypassPhysicsEnabled());
		assertTrue(DroneDebugSettings.flightModelMode() == DroneDebugSettings.FlightModelMode.SIMULATION);
		assertTrue(DroneDebugSettings.statusLine().contains("flight=simulation"));

		DroneDebugSettings.setBypassPhysicsEnabled(true);
		assertTrue(DroneDebugSettings.flightModelMode() == DroneDebugSettings.FlightModelMode.PLAYABLE);
		DroneDebugSettings.setFlightModelMode(DroneDebugSettings.FlightModelMode.PLAYABLE);
	}

	@Test
	void ownerlessControlIsOptInDebugBehavior() {
		DroneDebugSettings.setOwnerlessControlEnabled(false);

		assertFalse(DroneDebugSettings.ownerlessControlEnabled());
		assertTrue(DroneDebugSettings.statusLine().contains("ownerless=off"));

		DroneDebugSettings.setOwnerlessControlEnabled(true);
		assertTrue(DroneDebugSettings.ownerlessControlEnabled());
		DroneDebugSettings.setOwnerlessControlEnabled(false);
	}
}
