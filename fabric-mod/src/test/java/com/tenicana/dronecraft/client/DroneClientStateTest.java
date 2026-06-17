package com.tenicana.dronecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneClientStateTest {
	@Test
	void hudModeCyclesThroughMinimalFullAndOff() {
		DroneClientState.setHudMode(DroneClientState.HudMode.MINIMAL);

		assertEquals(DroneClientState.HudMode.FULL, DroneClientState.cycleHudMode());
		assertTrue(DroneClientState.isHudEnabled());
		assertEquals(DroneClientState.HudMode.OFF, DroneClientState.cycleHudMode());
		assertFalse(DroneClientState.isHudEnabled());
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
}
