package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneClientControlsTest {
	@Test
	void gamepadInputRequiresDroneControlAuthority() {
		assertFalse(DroneControlAuthority.shouldUseGamepadInput(true, false, false, false));
		assertFalse(DroneControlAuthority.shouldUseGamepadInput(true, false, true, false));
		assertFalse(DroneControlAuthority.shouldUseGamepadInput(false, true, false, false));

		assertTrue(DroneControlAuthority.shouldUseGamepadInput(true, true, false, false));
		assertTrue(DroneControlAuthority.shouldUseGamepadInput(true, false, true, true));
	}

	@Test
	void controlAuthorityComesFromControllerOrLinkedVirtualController() {
		assertFalse(DroneControlAuthority.hasControlAuthority(false, false, false));
		assertFalse(DroneControlAuthority.hasControlAuthority(false, true, false));

		assertTrue(DroneControlAuthority.hasControlAuthority(true, false, false));
		assertTrue(DroneControlAuthority.hasControlAuthority(false, true, true));
	}
}
