package com.tenicana.dronecraft.debug;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneDebugSettingsTest {
	@Test
	void playableDirectFlightIsDefault() {
		assertTrue(DroneDebugSettings.bypassPhysicsEnabled());
		assertTrue(DroneDebugSettings.statusLine().contains("physics=direct"));
	}
}
