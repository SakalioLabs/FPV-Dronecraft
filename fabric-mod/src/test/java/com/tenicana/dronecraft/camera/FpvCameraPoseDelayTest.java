package com.tenicana.dronecraft.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FpvCameraPoseDelayTest {
	@Test
	void returnsInterpolatedDelayedPose() {
		FpvCameraPoseDelay delay = new FpvCameraPoseDelay();
		delay.sample(pose(0.0, 0.0f, 0.00), 0.025);
		delay.sample(pose(2.0, 20.0f, 0.02), 0.025);
		delay.sample(pose(4.0, 40.0f, 0.04), 0.025);

		FpvCameraPoseDelay.Pose delayed = delay.sample(pose(5.0, 50.0f, 0.05), 0.025);

		assertEquals(2.5, delayed.xMeters(), 1.0e-6);
		assertEquals(25.0f, delayed.yawDegrees(), 1.0e-5f);
		assertEquals(0.025, delayed.timeSeconds(), 1.0e-9);
	}

	@Test
	void interpolatesYawAcrossWrapBoundary() {
		FpvCameraPoseDelay delay = new FpvCameraPoseDelay();
		delay.sample(pose(0.0, 179.0f, 0.00), 0.010);

		FpvCameraPoseDelay.Pose delayed = delay.sample(pose(1.0, -179.0f, 0.02), 0.010);

		assertTrue(Math.abs(Math.abs(delayed.yawDegrees()) - 180.0f) < 1.0e-5f);
	}

	@Test
	void zeroLatencyReturnsCurrentPose() {
		FpvCameraPoseDelay delay = new FpvCameraPoseDelay();
		delay.sample(pose(0.0, 0.0f, 0.00), 0.050);

		FpvCameraPoseDelay.Pose current = delay.sample(pose(3.0, 30.0f, 0.02), 0.0);

		assertEquals(3.0, current.xMeters(), 1.0e-6);
		assertEquals(30.0f, current.yawDegrees(), 1.0e-6f);
		assertEquals(0.02, current.timeSeconds(), 1.0e-9);
	}

	private static FpvCameraPoseDelay.Pose pose(double xMeters, float yawDegrees, double timeSeconds) {
		return new FpvCameraPoseDelay.Pose(xMeters, 0.0, 0.0, yawDegrees, 0.0f, 0.0f, timeSeconds);
	}
}
