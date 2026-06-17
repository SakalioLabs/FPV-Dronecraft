package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GamepadStickShaperTest {
	@Test
	void trainingPathKeepsSmallRightStickInputsQuiet() {
		float micro = GamepadStickShaper.commandFromCalibratedAxis(0.30f, 0.10f, 1.00f, 0.42f);
		float gentle = GamepadStickShaper.commandFromCalibratedAxis(0.35f, 0.10f, 1.00f, 0.42f);
		float half = GamepadStickShaper.commandFromCalibratedAxis(0.50f, 0.10f, 1.00f, 0.42f);
		float medium = GamepadStickShaper.commandFromCalibratedAxis(0.70f, 0.10f, 1.00f, 0.42f);

		assertEquals(0.0f, micro, 0.0015f);
		assertTrue(gentle >= 0.0f);
		assertTrue(gentle < 0.003f, () -> "gentle=" + gentle);
		assertTrue(half > gentle);
		assertTrue(half < 0.012f, () -> "half=" + half);
		assertTrue(medium > 0.070f, () -> "medium=" + medium);
		assertTrue(medium < 0.085f, () -> "medium=" + medium);
	}

	@Test
	void trainingPathPreservesDeliberateFullStickAuthority() {
		float positive = GamepadStickShaper.commandFromCalibratedAxis(1.0f, 0.10f, 1.00f, 0.42f);
		float negative = GamepadStickShaper.commandFromCalibratedAxis(-1.0f, 0.10f, 1.00f, 0.42f);

		assertEquals(0.42f, positive, 1.0e-6f);
		assertEquals(-0.42f, negative, 1.0e-6f);
	}

	@Test
	void conditionedAxisAppliesOnlyPhysicalCenterDeadband() {
		assertEquals(0.0f, GamepadStickShaper.conditionedAxis(0.08f, 0.10f), 1.0e-6f);
		assertEquals(1.0f, GamepadStickShaper.conditionedAxis(1.0f, 0.10f), 1.0e-6f);
		assertEquals(-1.0f, GamepadStickShaper.conditionedAxis(-1.0f, 0.10f), 1.0e-6f);
	}
}
