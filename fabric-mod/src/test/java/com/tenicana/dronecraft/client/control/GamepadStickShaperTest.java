package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GamepadStickShaperTest {
	@Test
	void trainingPathKeepsSmallRightStickInputsQuiet() {
		float gentle = GamepadStickShaper.commandFromCalibratedAxis(0.35f, 0.10f, 0.98f, 0.55f);
		float half = GamepadStickShaper.commandFromCalibratedAxis(0.50f, 0.10f, 0.98f, 0.55f);
		float medium = GamepadStickShaper.commandFromCalibratedAxis(0.70f, 0.10f, 0.98f, 0.55f);

		assertTrue(gentle >= 0.0f);
		assertTrue(gentle < 0.006f, () -> "gentle=" + gentle);
		assertTrue(half > gentle);
		assertTrue(half < 0.030f, () -> "half=" + half);
		assertTrue(medium > 0.09f, () -> "medium=" + medium);
		assertTrue(medium < 0.13f, () -> "medium=" + medium);
	}

	@Test
	void trainingPathPreservesDeliberateFullStickAuthority() {
		float positive = GamepadStickShaper.commandFromCalibratedAxis(1.0f, 0.10f, 0.98f, 0.55f);
		float negative = GamepadStickShaper.commandFromCalibratedAxis(-1.0f, 0.10f, 0.98f, 0.55f);

		assertEquals(0.55f, positive, 1.0e-6f);
		assertEquals(-0.55f, negative, 1.0e-6f);
	}

	@Test
	void conditionedAxisAppliesOnlyPhysicalCenterDeadband() {
		assertEquals(0.0f, GamepadStickShaper.conditionedAxis(0.08f, 0.10f), 1.0e-6f);
		assertEquals(1.0f, GamepadStickShaper.conditionedAxis(1.0f, 0.10f), 1.0e-6f);
		assertEquals(-1.0f, GamepadStickShaper.conditionedAxis(-1.0f, 0.10f), 1.0e-6f);
	}
}
