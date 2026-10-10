package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicRotorPhaseTest {
	@Test
	void isStableDistinctAndWrapped() {
		double first = DeterministicRotorPhase.phaseRadians(42L, 0);

		assertEquals(first, DeterministicRotorPhase.phaseRadians(42L, 0));
		assertNotEquals(first, DeterministicRotorPhase.phaseRadians(42L, 1));
		assertNotEquals(first, DeterministicRotorPhase.phaseRadians(43L, 0));
		for (int rotor = 0; rotor < 256; rotor++) {
			double phase = DeterministicRotorPhase.phaseRadians(-91L, rotor);
			assertTrue(phase >= 0.0);
			assertTrue(phase < 2.0 * Math.PI);
		}
	}

	@Test
	void avoidsExactCommonMultirotorBladePassCancellation() {
		for (int rotorCount : new int[]{4, 6, 8}) {
			for (int bladeCount : new int[]{2, 3}) {
				for (int harmonic = 1; harmonic <= 12; harmonic++) {
					double real = 0.0;
					double imaginary = 0.0;
					for (int rotor = 0; rotor < rotorCount; rotor++) {
						double phase = DeterministicRotorPhase.phaseRadians(42L, rotor)
								* bladeCount * harmonic;
						real += Math.cos(phase);
						imaginary += Math.sin(phase);
					}
					assertTrue(
							Math.hypot(real, imaginary) > 1.0e-3,
							"exact cancellation for rotors=" + rotorCount
									+ ", blades=" + bladeCount
									+ ", harmonic=" + harmonic
					);
				}
			}
		}
	}

	@Test
	void rejectsNegativeRotorIndex() {
		assertThrows(
				IllegalArgumentException.class,
				() -> DeterministicRotorPhase.phaseRadians(1L, -1)
		);
	}
}
