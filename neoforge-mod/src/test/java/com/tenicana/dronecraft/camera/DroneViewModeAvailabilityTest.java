package com.tenicana.dronecraft.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DroneViewModeAvailabilityTest {
	@Test
	void missingCameraClampsEveryModeToLineOfSight() {
		for (DroneViewMode mode : DroneViewMode.values()) {
			assertEquals(DroneViewMode.LINE_OF_SIGHT, mode.available(false), mode.name());
		}
	}

	@Test
	void installedCameraPreservesModeAndCyclesThroughNativeOrder() {
		DroneViewMode mode = DroneViewMode.LINE_OF_SIGHT;
		assertEquals(DroneViewMode.LINE_OF_SIGHT, mode.available(true));

		mode = mode.next();
		assertEquals(DroneViewMode.FPV, mode.available(true));
		mode = mode.next();
		assertEquals(DroneViewMode.THIRD_PERSON, mode.available(true));
		mode = mode.next();
		assertEquals(DroneViewMode.LINE_OF_SIGHT, mode.available(true));
	}

	@Test
	void removingCameraWhileMountedClampsToLosAndReinstallWaitsForNextInput() {
		DroneViewMode mode = DroneViewMode.THIRD_PERSON;

		// Losing the camera while mounted immediately drops the selected view to LOS.
		mode = mode.available(false);
		assertEquals(DroneViewMode.LINE_OF_SIGHT, mode);

		// Reinstalling the camera does not restore a stale drone-camera selection.
		mode = mode.available(true);
		assertEquals(DroneViewMode.LINE_OF_SIGHT, mode);

		// The next explicit view input starts the cycle again.
		assertEquals(DroneViewMode.FPV, mode.next());
	}
}
