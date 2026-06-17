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
}
