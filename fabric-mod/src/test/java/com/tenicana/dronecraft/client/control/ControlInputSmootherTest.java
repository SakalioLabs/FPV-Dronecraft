package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ControlInputSmootherTest {
	@Test
	void axisCommandsRiseGraduallyTowardStickTarget() {
		ControlInputSmoother smoother = new ControlInputSmoother();

		ControlInputSmoother.Axes first = smoother.sample(0.60f, -0.40f, 0.25f, 0.10f, 0.30f);
		ControlInputSmoother.Axes second = smoother.sample(0.60f, -0.40f, 0.25f, 0.10f, 0.30f);

		assertEquals(0.10f, first.pitch(), 1.0e-6f);
		assertEquals(-0.10f, first.roll(), 1.0e-6f);
		assertEquals(0.10f, first.yaw(), 1.0e-6f);
		assertEquals(0.20f, second.pitch(), 1.0e-6f);
		assertEquals(-0.20f, second.roll(), 1.0e-6f);
		assertEquals(0.20f, second.yaw(), 1.0e-6f);
	}

	@Test
	void releasedOrReversedSticksFallBackFaster() {
		ControlInputSmoother smoother = new ControlInputSmoother();
		for (int i = 0; i < 5; i++) {
			smoother.sample(0.60f, 0.60f, 0.60f, 0.10f, 0.30f);
		}

		ControlInputSmoother.Axes released = smoother.sample(0.0f, -0.60f, 0.0f, 0.10f, 0.30f);

		assertTrue(released.pitch() < 0.25f);
		assertTrue(released.roll() < 0.25f);
		assertTrue(released.yaw() < 0.25f);
	}

	@Test
	void resetClearsStoredAxisState() {
		ControlInputSmoother smoother = new ControlInputSmoother();
		smoother.sample(1.0f, 1.0f, 1.0f, 0.20f, 0.30f);

		smoother.reset();
		ControlInputSmoother.Axes axes = smoother.sample(0.0f, 0.0f, 0.0f, 0.20f, 0.30f);

		assertEquals(0.0f, axes.pitch(), 1.0e-6f);
		assertEquals(0.0f, axes.roll(), 1.0e-6f);
		assertEquals(0.0f, axes.yaw(), 1.0e-6f);
	}
}
