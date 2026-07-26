package com.tenicana.dronecraft.acoustics.wave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RickerWaveletTest {
	@Test
	void peaksAtOneAtCentreTime() {
		double[] wavelet = RickerWavelet.generate(101, 0.001, 10.0, 0.05);

		assertEquals(1.0, wavelet[50], 1.0e-15);
		assertEquals(wavelet[49], wavelet[51], 1.0e-15);
		assertTrue(wavelet[40] < wavelet[50]);
	}

	@Test
	void rejectsInvalidInputs() {
		assertThrows(
				IllegalArgumentException.class,
				() -> RickerWavelet.generate(0, 0.001, 10.0, 0.05)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> RickerWavelet.generate(100, 0.001, 0.0, 0.05)
		);
	}
}
