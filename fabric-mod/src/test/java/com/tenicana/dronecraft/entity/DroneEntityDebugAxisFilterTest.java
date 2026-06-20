package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DroneEntityDebugAxisFilterTest {
	private static final float RISE = PlayableDebugAxisFilter.DEFAULT_RISE_SMOOTHING;
	private static final float FALL = PlayableDebugAxisFilter.DEFAULT_FALL_SMOOTHING;

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
	void defaultPlayableFilterSoftensShortStickTapsAndRecentersQuickly() {
		float filtered = 0.0f;
		for (int i = 0; i < 5; i++) {
			filtered = PlayableDebugAxisFilter.filter(filtered, 0.20f, RISE, FALL, true);
		}

		assertTrue(filtered > 0.10f);
		assertTrue(filtered < 0.11f);

		filtered = PlayableDebugAxisFilter.filter(filtered, 0.0f, RISE, FALL, true);
		filtered = PlayableDebugAxisFilter.filter(filtered, 0.0f, RISE, FALL, true);

		assertEquals(0.0f, filtered, 1.0e-6f);
	}

	@Test
	void deliberateFullStickStillBuildsAuthority() {
		float filtered = 0.0f;
		for (int i = 0; i < 10; i++) {
			filtered = PlayableDebugAxisFilter.filter(filtered, 1.0f, RISE, FALL, true);
		}

		assertTrue(filtered > 0.75f);
	}

	@Test
	void throttleFallsBackIntoHoverBandFasterThanItRisesIntoPunch() {
		float falling = 0.60f;
		for (int i = 0; i < 4; i++) {
			falling = PlayableDebugAxisFilter.throttle(falling, 0.20f);
		}

		float rising = 0.20f;
		for (int i = 0; i < 4; i++) {
			rising = PlayableDebugAxisFilter.throttle(rising, 0.60f);
		}

		assertTrue(falling < 0.255f, "falling=" + falling);
		assertTrue(rising > falling + 0.12f, "rising=" + rising + " falling=" + falling);
		assertTrue(rising < 0.48f, "rising=" + rising);
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
		float filtered = PlayableDebugAxisFilter.throttle(0.10f, -1.0f);

		assertEquals(0.0f, filtered, 1.0e-6f);
	}

	@Test
	void playableMovementYawUsesMidpointHeadingForVisibleYawRate() {
		assertEquals(12.5f, PlayableMovementYaw.midpointForTick(10.0f, 5.0f), 1.0e-6f);
		assertEquals(7.5f, PlayableMovementYaw.midpointForTick(10.0f, -5.0f), 1.0e-6f);
	}

	@Test
	void playableMovementYawKeepsCurrentHeadingForNoiseAndInvalidSamples() {
		assertEquals(10.0f, PlayableMovementYaw.midpointForTick(10.0f, 0.02f), 1.0e-6f);
		assertEquals(10.0f, PlayableMovementYaw.midpointForTick(10.0f, Float.NaN), 1.0e-6f);
		assertEquals(2.0f, PlayableMovementYaw.midpointForTick(Float.NaN, 4.0f), 1.0e-6f);
	}

	@Test
	void midpointYawCurvesForwardVelocityDuringTheSameTick() {
		float midpointYaw = PlayableMovementYaw.midpointForTick(0.0f, 10.0f);
		PlayableFlightModel.Velocity midpointVelocity = PlayableFlightModel.worldVelocityForYaw(0.0f, 0.0f, 20.0f, midpointYaw);
		PlayableFlightModel.Velocity oldHeadingVelocity = PlayableFlightModel.worldVelocityForYaw(0.0f, 0.0f, 20.0f, 0.0f);

		assertTrue(midpointVelocity.x() < -1.7f, "midpointVelocity.x=" + midpointVelocity.x());
		assertTrue(midpointVelocity.z() < oldHeadingVelocity.z(), "midpointVelocity.z=" + midpointVelocity.z());
		assertTrue(midpointVelocity.z() > 19.8f, "midpointVelocity.z=" + midpointVelocity.z());
	}
}
