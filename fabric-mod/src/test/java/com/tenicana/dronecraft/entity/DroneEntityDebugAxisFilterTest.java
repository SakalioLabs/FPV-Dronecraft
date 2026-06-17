package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneEntityDebugAxisFilterTest {
	private static final float RISE = 0.18f;
	private static final float FALL = 0.56f;

	@Test
	void releasedAxisSnapsToZeroAfterShortTail() {
		float filtered = 0.18f;
		for (int i = 0; i < 4; i++) {
			filtered = PlayableDebugAxisFilter.filter(filtered, 0.0f, RISE, FALL, true);
		}

		assertEquals(0.0f, filtered, 1.0e-6f);
	}

	@Test
	void reversingSmallAxisAcrossCenterDoesNotLeaveATinyResidualCommand() {
		float filtered = PlayableDebugAxisFilter.filter(0.020f, -0.020f, RISE, FALL, true);

		assertEquals(0.0f, filtered, 1.0e-6f);
	}

	@Test
	void nonFiniteAxisSamplesNormalizeBeforeFiltering() {
		float invalidCurrent = PlayableDebugAxisFilter.filter(Float.NaN, 0.50f, RISE, FALL, true);
		float invalidTarget = PlayableDebugAxisFilter.filter(0.20f, Float.NaN, RISE, FALL, true);

		assertTrue(Float.isFinite(invalidCurrent));
		assertTrue(Float.isFinite(invalidTarget));
	}

	@Test
	void throttleFilterDoesNotGoNegative() {
		float filtered = PlayableDebugAxisFilter.filter(0.10f, -1.0f, RISE, FALL, false);

		assertEquals(0.0f, filtered, 1.0e-6f);
	}
}
