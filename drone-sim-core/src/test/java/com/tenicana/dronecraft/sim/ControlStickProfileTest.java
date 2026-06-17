package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ControlStickProfileTest {
	@Test
	void gamepadProfileSoftensCenterWithoutLosingFullStick() {
		assertEquals(0.0, ControlStickProfile.gamepadCommand(0.10, 0.10), 1.0e-12);
		assertTrue(ControlStickProfile.gamepadCommand(0.35, 0.10) < 0.02);
		assertTrue(ControlStickProfile.gamepadCommand(0.50, 0.10) > 0.04);
		assertTrue(ControlStickProfile.gamepadCommand(0.50, 0.10) < 0.07);
		assertTrue(ControlStickProfile.gamepadCommand(0.70, 0.10) > 0.22);
		assertTrue(ControlStickProfile.gamepadCommand(0.70, 0.10) < 0.27);
		assertEquals(1.0, ControlStickProfile.gamepadCommand(1.0, 0.10), 1.0e-12);
		assertEquals(-1.0, ControlStickProfile.gamepadCommand(-1.0, 0.10), 1.0e-12);
	}

	@Test
	void configurableGamepadRateKeepsExpoShapeButLimitsAuthority() {
		double defaultHalfStick = ControlStickProfile.gamepadCommand(0.70, 0.10);
		double softenedHalfStick = ControlStickProfile.gamepadCommand(0.70, 0.10, 0.97, 0.50);

		assertEquals(defaultHalfStick * 0.50, softenedHalfStick, 1.0e-12);
		assertEquals(0.50, ControlStickProfile.gamepadCommand(1.0, 0.10, 0.97, 0.50), 1.0e-12);
		assertEquals(-0.50, ControlStickProfile.gamepadCommand(-1.0, 0.10, 0.97, 0.50), 1.0e-12);
	}

	@Test
	void keyboardProfileRoundsBinaryKeysIntoUsableCommands() {
		assertEquals(0.0, ControlStickProfile.keyboardCommand(0.0), 1.0e-12);
		assertTrue(ControlStickProfile.keyboardCommand(0.25) < 0.06);
		assertTrue(ControlStickProfile.keyboardCommand(0.50) < 0.18);
		assertEquals(1.0, ControlStickProfile.keyboardCommand(1.0), 1.0e-12);
	}

	@Test
	void gamepadThrottleMovesHoverIntoMiddleStickTravel() {
		assertEquals(0.0, ControlStickProfile.gamepadThrottle(0.0), 1.0e-12);
		assertTrue(ControlStickProfile.gamepadThrottle(0.45) > 0.16);
		assertTrue(ControlStickProfile.gamepadThrottle(0.45) < 0.18);
		assertEquals(0.20, ControlStickProfile.gamepadThrottle(0.50), 1.0e-12);
		assertTrue(ControlStickProfile.gamepadThrottle(0.55) > 0.225);
		assertTrue(ControlStickProfile.gamepadThrottle(0.55) < 0.235);
		assertTrue(ControlStickProfile.gamepadThrottle(0.60) > 0.27);
		assertTrue(ControlStickProfile.gamepadThrottle(0.60) < 0.29);
		assertEquals(1.0, ControlStickProfile.gamepadThrottle(1.0), 1.0e-12);
	}
}
