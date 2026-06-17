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

	@Test
	void armReadinessMatchesSharedSafetyRules() {
		assertTrue(DroneHud.isArmReadyForHud(false, 0.02f, 0.0f, 0.0f, 0.0f));
		assertTrue(DroneHud.isArmReadyForHud(false, 0.03f, -0.80f, 0.80f, -0.80f));

		assertFalse(DroneHud.isArmBlockedForHud(true, 0.60f, 0.80f, 0.80f, 0.80f));
		assertFalse(DroneHud.isArmReadyForHud(true, 0.02f, 0.0f, 0.0f, 0.0f));
	}

	@Test
	void armBlockedHintCatchesUnsafeOrInvalidInputs() {
		assertTrue(DroneHud.isArmBlockedForHud(false, 0.42f, 0.0f, 0.0f, 0.0f));
		assertTrue(DroneHud.isArmBlockedForHud(false, 0.02f, 0.38f, 0.0f, 0.0f));
		assertTrue(DroneHud.isArmBlockedForHud(false, Float.NaN, 0.0f, 0.0f, 0.0f));
	}
}
