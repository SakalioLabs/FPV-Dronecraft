package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GamepadStickShaperTest {
	@Test
	void trainingPathMakesDeliberateRightStickInputsVisible() {
		float micro = GamepadStickShaper.commandFromCalibratedAxis(0.10f, 0.08f, 0.60f, 0.86f);
		float gentle = GamepadStickShaper.commandFromCalibratedAxis(0.35f, 0.08f, 0.60f, 0.86f);
		float half = GamepadStickShaper.commandFromCalibratedAxis(0.50f, 0.08f, 0.60f, 0.86f);
		float medium = GamepadStickShaper.commandFromCalibratedAxis(0.70f, 0.08f, 0.60f, 0.86f);

		assertEquals(0.0f, micro, 0.0015f);
		assertTrue(gentle > 0.075f, () -> "gentle=" + gentle);
		assertTrue(gentle < 0.095f, () -> "gentle=" + gentle);
		assertTrue(half > gentle);
		assertTrue(half > 0.16f, () -> "half=" + half);
		assertTrue(half < 0.19f, () -> "half=" + half);
		assertTrue(medium > 0.34f, () -> "medium=" + medium);
		assertTrue(medium < 0.38f, () -> "medium=" + medium);
	}

	@Test
	void trainingPathPreservesDeliberateFullStickAuthority() {
		float positive = GamepadStickShaper.commandFromCalibratedAxis(1.0f, 0.08f, 0.60f, 0.86f);
		float negative = GamepadStickShaper.commandFromCalibratedAxis(-1.0f, 0.08f, 0.60f, 0.86f);

		assertEquals(0.86f, positive, 1.0e-6f);
		assertEquals(-0.86f, negative, 1.0e-6f);
	}

	@Test
	void conditionedAxisAppliesOnlyPhysicalCenterDeadband() {
		assertEquals(0.0f, GamepadStickShaper.conditionedAxis(0.08f, 0.10f), 1.0e-6f);
		assertEquals(1.0f, GamepadStickShaper.conditionedAxis(1.0f, 0.10f), 1.0e-6f);
		assertEquals(-1.0f, GamepadStickShaper.conditionedAxis(-1.0f, 0.10f), 1.0e-6f);
	}
}
