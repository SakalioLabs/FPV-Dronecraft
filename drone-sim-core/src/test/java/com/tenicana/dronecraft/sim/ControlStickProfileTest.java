package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ControlStickProfileTest {
	@Test
	void gamepadProfileSoftensCenterWithoutLosingFullStick() {
		assertEquals(0.0, ControlStickProfile.gamepadCommand(0.10, 0.10), 1.0e-12);
		assertTrue(ControlStickProfile.gamepadCommand(0.35, 0.10) < 0.04);
		assertTrue(ControlStickProfile.gamepadCommand(0.50, 0.10) > 0.09);
		assertTrue(ControlStickProfile.gamepadCommand(0.50, 0.10) < 0.11);
		assertTrue(ControlStickProfile.gamepadCommand(0.70, 0.10) > 0.28);
		assertTrue(ControlStickProfile.gamepadCommand(0.70, 0.10) < 0.33);
		assertEquals(1.0, ControlStickProfile.gamepadCommand(1.0, 0.10), 1.0e-12);
		assertEquals(-1.0, ControlStickProfile.gamepadCommand(-1.0, 0.10), 1.0e-12);
	}

	@Test
	void keyboardProfileRoundsBinaryKeysIntoUsableCommands() {
		assertEquals(0.0, ControlStickProfile.keyboardCommand(0.0), 1.0e-12);
		assertTrue(ControlStickProfile.keyboardCommand(0.25) < 0.14);
		assertTrue(ControlStickProfile.keyboardCommand(0.50) < 0.25);
		assertEquals(1.0, ControlStickProfile.keyboardCommand(1.0), 1.0e-12);
	}

	@Test
	void gamepadThrottleMovesHoverIntoMiddleStickTravel() {
		assertEquals(0.0, ControlStickProfile.gamepadThrottle(0.0), 1.0e-12);
		assertEquals(0.2025, ControlStickProfile.gamepadThrottle(0.45), 1.0e-12);
		assertEquals(0.25, ControlStickProfile.gamepadThrottle(0.50), 1.0e-12);
		assertEquals(1.0, ControlStickProfile.gamepadThrottle(1.0), 1.0e-12);
	}
}
