package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyboardControlShaperTest {
	@Test
	void shortTapProducesTrainingLevelCommand() {
		float axis = 0.0f;
		for (int i = 0; i < 5; i++) {
			axis = KeyboardControlShaper.approachAxis(axis, 1.0f);
		}

		assertEquals(0.25f, axis, 1.0e-6f);
		assertTrue(KeyboardControlShaper.commandAxis(axis) < 0.06f);
	}

	@Test
	void heldKeyStillReachesFullAuthority() {
		float axis = 0.0f;
		for (int i = 0; i < 20; i++) {
			axis = KeyboardControlShaper.approachAxis(axis, 1.0f);
		}

		assertEquals(1.0f, axis, 1.0e-6f);
		assertEquals(1.0f, KeyboardControlShaper.commandAxis(axis), 1.0e-6f);
	}

	@Test
	void releasedKeyReturnsTowardCenterQuickly() {
		float axis = 0.0f;
		for (int i = 0; i < 5; i++) {
			axis = KeyboardControlShaper.approachAxis(axis, 1.0f);
		}

		axis = KeyboardControlShaper.approachAxis(axis, 0.0f);

		assertTrue(axis < 0.02f);
		assertTrue(KeyboardControlShaper.commandAxis(axis) < 0.003f);
	}

	@Test
	void throttleUsesCoarseStepAwayFromHover() {
		assertEquals(0.014f, KeyboardControlShaper.adjustThrottle(0.0f, 1, 0.20f), 1.0e-6f);
		assertEquals(0.986f, KeyboardControlShaper.adjustThrottle(1.0f, -1, 0.20f), 1.0e-6f);
	}

	@Test
	void throttleUsesFineStepNearHover() {
		assertEquals(0.157f, KeyboardControlShaper.adjustThrottle(0.150f, 1, 0.20f), 1.0e-6f);
		assertEquals(0.243f, KeyboardControlShaper.adjustThrottle(0.250f, -1, 0.20f), 1.0e-6f);
	}

	@Test
	void throttleSnapsToHoverWhenCrossingDetent() {
		assertEquals(0.20f, KeyboardControlShaper.adjustThrottle(0.196f, 1, 0.20f), 1.0e-6f);
		assertEquals(0.20f, KeyboardControlShaper.adjustThrottle(0.204f, -1, 0.20f), 1.0e-6f);
	}

	@Test
	void throttleCanLeaveHoverDetentWhenKeyIsHeld() {
		assertEquals(0.207f, KeyboardControlShaper.adjustThrottle(0.20f, 1, 0.20f), 1.0e-6f);
		assertEquals(0.193f, KeyboardControlShaper.adjustThrottle(0.20f, -1, 0.20f), 1.0e-6f);
	}

	@Test
	void throttleDetentTracksCurrentAirframeHoverThrottle() {
		assertEquals(0.40f, KeyboardControlShaper.adjustThrottle(0.396f, 1, 0.40f), 1.0e-6f);
		assertEquals(0.40f, KeyboardControlShaper.adjustThrottle(0.404f, -1, 0.40f), 1.0e-6f);
	}

	@Test
	void throttleIgnoresConflictingKeysAndClampsBadValues() {
		assertEquals(0.30f, KeyboardControlShaper.adjustThrottle(0.30f, 0, 0.20f), 1.0e-6f);
		assertEquals(0.014f, KeyboardControlShaper.adjustThrottle(Float.NaN, 1, 0.20f), 1.0e-6f);
		assertEquals(1.0f, KeyboardControlShaper.adjustThrottle(1.0f, 1, 0.20f), 1.0e-6f);
		assertEquals(0.0f, KeyboardControlShaper.adjustThrottle(0.0f, -1, 0.20f), 1.0e-6f);
	}
}
