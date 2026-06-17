package com.tenicana.dronecraft.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneHudTest {
	@Test
	void idleRpmUsesSemanticDisplayInsteadOfLargeNumericValue() {
		assertTrue(DroneHud.isIdleRpm(true, 0.0f, 13_500.0f));
		assertTrue(DroneHud.isIdleRpm(true, 0.05f, 18_000.0f));
	}

	@Test
	void activeThrottleKeepsNumericRpmVisible() {
		assertFalse(DroneHud.isIdleRpm(true, 0.07f, 13_500.0f));
		assertEquals("13.5k", DroneHud.compactRpmText(true, 13_500.0f));
	}

	@Test
	void disarmedOrStoppedDroneShowsZeroRpm() {
		assertFalse(DroneHud.isIdleRpm(false, 0.0f, 18_000.0f));
		assertFalse(DroneHud.isIdleRpm(true, 0.0f, 0.0f));
		assertEquals("0", DroneHud.compactRpmText(false, 18_000.0f));
		assertEquals("0", DroneHud.compactRpmText(true, 0.0f));
	}

	@Test
	void invalidRpmDoesNotLeakNanIntoHud() {
		assertFalse(DroneHud.isIdleRpm(true, 0.0f, Float.NaN));
		assertEquals("0", DroneHud.compactRpmText(true, Float.NaN));
		assertEquals("0", DroneHud.compactRpmText(true, Float.POSITIVE_INFINITY));
	}
}
