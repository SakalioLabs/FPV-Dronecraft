package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenAlNativeDopplerGuardTest {
	private static final float[] SOURCE_POSITION = {10.0f, 0.0f, 0.0f};
	private static final float[] LISTENER_POSITION = {0.0f, 0.0f, 0.0f};

	@Test
	void equalSourceAndListenerVelocityNeutralizesNativeDoppler() {
		float[] sharedVelocity = {12.0f, -3.0f, 4.0f};
		double ratio = OpenAlNativeDopplerGuard.nativeDopplerRatio(
				343.3,
				1.0,
				SOURCE_POSITION,
				sharedVelocity,
				LISTENER_POSITION,
				sharedVelocity
		);
		assertEquals(1.0, ratio, 1.0e-12);
	}

	@Test
	void nativeAndInternalDopplerWouldMultiply() {
		double nativeRatio =
				OpenAlNativeDopplerGuard.nativeDopplerRatio(
						343.3,
						1.0,
						SOURCE_POSITION,
						new float[] {-20.0f, 0.0f, 0.0f},
						LISTENER_POSITION,
						new float[] {0.0f, 0.0f, 0.0f}
				);
		assertTrue(Math.abs(nativeRatio - 1.0) > 0.01);
		double internalRatio = 1.06;
		assertTrue(
				Math.abs(nativeRatio * internalRatio - internalRatio)
						> 0.01
		);
	}

	@Test
	void zeroGlobalFactorDisablesNativeDoppler() {
		double ratio = OpenAlNativeDopplerGuard.nativeDopplerRatio(
				343.3,
				0.0,
				SOURCE_POSITION,
				new float[] {100.0f, 0.0f, 0.0f},
				LISTENER_POSITION,
				new float[] {-100.0f, 0.0f, 0.0f}
		);
		assertEquals(1.0, ratio);
	}

	@Test
	void rejectsInvalidGlobalOrVectorState() {
		assertThrows(
				IllegalArgumentException.class,
				() -> OpenAlNativeDopplerGuard.nativeDopplerRatio(
						0.0,
						1.0,
						SOURCE_POSITION,
						new float[3],
						LISTENER_POSITION,
						new float[3]
				)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> OpenAlNativeDopplerGuard.nativeDopplerRatio(
						343.3,
						1.0,
						SOURCE_POSITION,
						new float[] {Float.NaN, 0.0f, 0.0f},
						LISTENER_POSITION,
						new float[3]
				)
		);
	}
}
