package com.tenicana.dronecraft.client.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneArmSafetyTest {
	@Test
	void momentaryArmRequiresLowThrottleAndCenteredSticks() {
		assertTrue(DroneArmSafety.canArmFromMomentaryControl(0.02f, 0.0f, 0.0f, 0.0f));

		assertFalse(DroneArmSafety.canArmFromMomentaryControl(0.18f, 0.0f, 0.0f, 0.0f));
		assertFalse(DroneArmSafety.canArmFromMomentaryControl(0.02f, 0.30f, 0.0f, 0.0f));
		assertFalse(DroneArmSafety.canArmFromMomentaryControl(0.02f, 0.0f, -0.30f, 0.0f));
		assertFalse(DroneArmSafety.canArmFromMomentaryControl(0.02f, 0.0f, 0.0f, 0.30f));
	}

	@Test
	void stickGestureUsesModeTwoBottomCorners() {
		assertTrue(DroneArmSafety.isStickArmGesture(0.03f, -0.80f, 0.80f, -0.80f));

		assertFalse(DroneArmSafety.isStickArmGesture(0.14f, -0.80f, 0.80f, -0.80f));
		assertFalse(DroneArmSafety.isStickArmGesture(0.03f, -0.60f, 0.80f, -0.80f));
		assertFalse(DroneArmSafety.isStickArmGesture(0.03f, -0.80f, -0.80f, -0.80f));
		assertFalse(DroneArmSafety.isStickArmGesture(0.03f, -0.80f, 0.80f, 0.80f));
	}

	@Test
	void nonFiniteInputsCannotArm() {
		assertFalse(DroneArmSafety.canArmFromMomentaryControl(Float.NaN, 0.0f, 0.0f, 0.0f));
		assertFalse(DroneArmSafety.isStickArmGesture(0.0f, Float.POSITIVE_INFINITY, 1.0f, -1.0f));
	}
}
