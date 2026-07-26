package com.tenicana.dronecraft.acoustics.wave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaveGrid2dTest {
	@Test
	void constructsAtRequestedCourantSafety() {
		WaveGrid2d grid = WaveGrid2d.withCourantSafety(
				100,
				80,
				0.01,
				0.95,
				343.0,
				1.204
		);

		assertEquals(0.95 / Math.sqrt(2.0), grid.courantNumber(), 1.0e-15);
		assertEquals(1.0 / grid.timeStepSeconds(), grid.sampleRateHertz(), 0.0);
	}

	@Test
	void rejectsTwoDimensionalCflViolation() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new WaveGrid2d(
						100,
						80,
						0.01,
						0.01 / 343.0,
						343.0,
						1.204
				)
		);
	}
}
