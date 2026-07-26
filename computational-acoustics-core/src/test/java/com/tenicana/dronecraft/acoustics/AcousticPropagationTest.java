package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcousticPropagationTest {
	@Test
	void temperatureControlsSoundSpeed() {
		assertEquals(343.42, AcousticPropagation.speedOfSoundMetersPerSecond(20.0), 1.0e-9);
	}

	@Test
	void approachingSourceRaisesPitch() {
		double ratio = AcousticPropagation.dopplerRatio(343.42, 0.0, -30.0);
		assertTrue(ratio > 1.0);
	}

	@Test
	void rejectsUnstableSupersonicDenominator() {
		assertThrows(IllegalArgumentException.class,
				() -> AcousticPropagation.dopplerRatio(343.0, 0.0, -340.0));
	}
}
