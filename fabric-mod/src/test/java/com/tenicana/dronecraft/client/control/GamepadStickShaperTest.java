package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GamepadStickShaperTest {
	@Test
	void trainingPathKeepsSmallRightStickInputsQuiet() {
		float gentle = GamepadStickShaper.commandFromCalibratedAxis(0.35f, 0.10f, 0.98f, 0.48f);
		float half = GamepadStickShaper.commandFromCalibratedAxis(0.50f, 0.10f, 0.98f, 0.48f);
		float medium = GamepadStickShaper.commandFromCalibratedAxis(0.70f, 0.10f, 0.98f, 0.48f);

		assertTrue(gentle >= 0.0f);
		assertTrue(gentle < 0.006f, () -> "gentle=" + gentle);
		assertTrue(half > gentle);
		assertTrue(half < 0.030f, () -> "half=" + half);
		assertTrue(medium > 0.08f, () -> "medium=" + medium);
		assertTrue(medium < 0.11f, () -> "medium=" + medium);
	}

	@Test
	void trainingPathPreservesDeliberateFullStickAuthority() {
		float positive = GamepadStickShaper.commandFromCalibratedAxis(1.0f, 0.10f, 0.98f, 0.48f);
		float negative = GamepadStickShaper.commandFromCalibratedAxis(-1.0f, 0.10f, 0.98f, 0.48f);

		assertEquals(0.48f, positive, 1.0e-6f);
		assertEquals(-0.48f, negative, 1.0e-6f);
	}

	@Test
	void conditionedAxisAppliesOnlyPhysicalCenterDeadband() {
		assertEquals(0.0f, GamepadStickShaper.conditionedAxis(0.08f, 0.10f), 1.0e-6f);
		assertEquals(1.0f, GamepadStickShaper.conditionedAxis(1.0f, 0.10f), 1.0e-6f);
		assertEquals(-1.0f, GamepadStickShaper.conditionedAxis(-1.0f, 0.10f), 1.0e-6f);
	}
}
