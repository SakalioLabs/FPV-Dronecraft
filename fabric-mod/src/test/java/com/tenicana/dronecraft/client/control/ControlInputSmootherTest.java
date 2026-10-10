package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.tenicana.dronecraft.sim.FlightMode;

class ControlInputSmootherTest {
	@Test
	void acroGamepadThrottleTracksPunchAndCutWithoutAnExtraRamp() {
		ControlInputSmoother smoother = new ControlInputSmoother();
		assertEquals(1.0f, smoother.sampleGamepadThrottle(1.0f, FlightMode.ACRO), 1.0e-6f);
		assertEquals(0.37f, smoother.sampleGamepadThrottle(0.37f, FlightMode.ACRO), 1.0e-6f);
		assertEquals(0.0f, smoother.sampleGamepadThrottle(0.0f, FlightMode.ACRO), 1.0e-6f);
	}

	@Test
	void assistedModeKeepsItsRampAndUsesTheLastAcroThrottleOnSwitch() {
		ControlInputSmoother smoother = new ControlInputSmoother();
		assertEquals(0.08f, smoother.sampleGamepadThrottle(1.0f, FlightMode.ANGLE), 1.0e-6f);
		assertEquals(0.16f, smoother.sampleGamepadThrottle(1.0f, FlightMode.HORIZON), 1.0e-6f);
		assertEquals(0.80f, smoother.sampleGamepadThrottle(0.80f, FlightMode.ACRO), 1.0e-6f);
		assertEquals(0.66f, smoother.sampleGamepadThrottle(0.0f, FlightMode.ANGLE), 1.0e-6f);
	}

	@Test
	void acroGamepadThrottleRejectsInvalidSamplesAndClampsEndpoints() {
		ControlInputSmoother smoother = new ControlInputSmoother();
		assertEquals(1.0f, smoother.sampleGamepadThrottle(1.2f, FlightMode.ACRO), 1.0e-6f);
		assertEquals(0.0f, smoother.sampleGamepadThrottle(Float.NaN, FlightMode.ACRO), 1.0e-6f);
		assertEquals(0.0f, smoother.sampleGamepadThrottle(-0.2f, FlightMode.ACRO), 1.0e-6f);
	}

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
	void releasedSmallAxisResidualSnapsToZero() {
		ControlInputSmoother smoother = new ControlInputSmoother();
		for (int i = 0; i < 12; i++) {
			smoother.sample(1.0f, -1.0f, 1.0f, 0.032f, 0.32f);
		}

		ControlInputSmoother.Axes released = smoother.sample(0.0f, 0.0f, 0.0f, 0.032f, 0.32f);

		assertEquals(0.0f, released.pitch(), 1.0e-6f);
		assertEquals(0.0f, released.roll(), 1.0e-6f);
		assertEquals(0.0f, released.yaw(), 1.0e-6f);
	}

	@Test
	void releasedLargeAxisResidualStillUsesBrakeStep() {
		ControlInputSmoother smoother = new ControlInputSmoother();
		for (int i = 0; i < 20; i++) {
			smoother.sample(1.0f, 1.0f, 1.0f, 0.032f, 0.32f);
		}

		ControlInputSmoother.Axes released = smoother.sample(0.0f, 0.0f, 0.0f, 0.032f, 0.32f);

		assertTrue(released.pitch() > 0.25f);
		assertTrue(released.roll() > 0.25f);
		assertTrue(released.yaw() > 0.25f);
	}

	@Test
	void throttleRisesSmoothlyButReachesHoverQuickly() {
		ControlInputSmoother smoother = new ControlInputSmoother();

		float first = smoother.sampleThrottle(0.20f, 0.08f, 0.14f);
		float second = smoother.sampleThrottle(0.20f, 0.08f, 0.14f);
		float third = smoother.sampleThrottle(0.20f, 0.08f, 0.14f);

		assertEquals(0.08f, first, 1.0e-6f);
		assertEquals(0.16f, second, 1.0e-6f);
		assertEquals(0.20f, third, 1.0e-6f);
	}

	@Test
	void throttleFallsBackFasterThanItRises() {
		ControlInputSmoother smoother = new ControlInputSmoother();
		for (int i = 0; i < 6; i++) {
			smoother.sampleThrottle(0.60f, 0.08f, 0.14f);
		}

		float released = smoother.sampleThrottle(0.20f, 0.08f, 0.14f);

		assertEquals(0.34f, released, 1.0e-6f);
	}

	@Test
	void resetClearsStoredAxisState() {
		ControlInputSmoother smoother = new ControlInputSmoother();
		smoother.sampleThrottle(0.50f, 0.08f, 0.14f);
		smoother.sample(1.0f, 1.0f, 1.0f, 0.20f, 0.30f);

		smoother.reset();
		assertEquals(0.0f, smoother.sampleThrottle(0.0f, 0.08f, 0.14f), 1.0e-6f);
		ControlInputSmoother.Axes axes = smoother.sample(0.0f, 0.0f, 0.0f, 0.20f, 0.30f);

		assertEquals(0.0f, axes.pitch(), 1.0e-6f);
		assertEquals(0.0f, axes.roll(), 1.0e-6f);
		assertEquals(0.0f, axes.yaw(), 1.0e-6f);
	}
}
