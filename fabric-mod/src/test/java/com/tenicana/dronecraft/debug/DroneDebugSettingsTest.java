package com.tenicana.dronecraft.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneDebugSettingsTest {
	@Test
	void playableDirectFlightIsDefault() {
		assertTrue(DroneDebugSettings.bypassPhysicsEnabled());
		assertTrue(DroneDebugSettings.statusLine().contains("physics=direct"));
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
