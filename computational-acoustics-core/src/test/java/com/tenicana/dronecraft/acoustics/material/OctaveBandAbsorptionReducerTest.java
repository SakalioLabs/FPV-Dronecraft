package com.tenicana.dronecraft.acoustics.material;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OctaveBandAbsorptionReducerTest {
	@Test
	void reducesWithExplicitEnergyWeights() {
		AcousticBands reduced = OctaveBandAbsorptionReducer.reduce(
				new double[] {0.1, 0.2, 0.3, 0.4, 0.6, 0.8},
				new double[] {1.0, 2.0, 1.0, 3.0, 1.0, 5.0}
		);
		assertEquals(0.2, reduced.low(), 1.0e-12);
		assertEquals(0.45, reduced.mid(), 1.0e-12);
		assertEquals(0.8, reduced.high(), 1.0e-12);
	}

	@Test
	void assignsFourKilohertzToHighBand() {
		AcousticBands reduced = OctaveBandAbsorptionReducer.reduce(
				new double[] {0.1, 0.1, 0.1, 0.2, 0.2, 0.9},
				new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 1.0}
		);
		assertEquals(0.1, reduced.low(), 1.0e-12);
		assertEquals(0.2, reduced.mid(), 1.0e-12);
		assertEquals(0.9, reduced.high(), 1.0e-12);
	}

	@Test
	void refusesImplicitClampingAndEmptyRuntimeBands() {
		assertThrows(
				IllegalArgumentException.class,
				() -> OctaveBandAbsorptionReducer.reduce(
						new double[] {0.1, 0.1, 0.1, 0.1, 0.1, 1.1},
						new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 1.0}
				)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> OctaveBandAbsorptionReducer.reduce(
						new double[] {0.1, 0.1, 0.1, 0.1, 0.1, 0.1},
						new double[] {1.0, 1.0, 1.0, 0.0, 0.0, 1.0}
				)
		);
	}
}
