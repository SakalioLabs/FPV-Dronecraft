package com.tenicana.dronecraft.acoustics.diffraction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoherentDiffractionCombinerTest {
	@Test
	void equalPathsAddCoherentlyButBroadbandAddsInEnergy() {
		CoherentDiffractionCombiner.Result result =
				CoherentDiffractionCombiner.combine(
						1_000.0,
						343.0,
						List.of(path(10.0), path(10.0))
				);

		assertEquals(2.0, result.coherentPressure().magnitude(), 1.0e-12);
		assertEquals(4.0, result.coherentEnergy(), 1.0e-12);
		assertEquals(2.0, result.incoherentEnergy(), 1.0e-12);
	}

	@Test
	void halfWavelengthDifferenceCancelsTonalPressure() {
		double frequency = 1_000.0;
		double speed = 343.0;
		double halfWavelength = speed / (2.0 * frequency);
		CoherentDiffractionCombiner.Result result =
				CoherentDiffractionCombiner.combine(
						frequency,
						speed,
						List.of(path(10.0), path(10.0 + halfWavelength))
				);

		assertEquals(0.0, result.coherentPressure().magnitude(), 1.0e-12);
		assertEquals(2.0, result.incoherentEnergy(), 1.0e-12);
	}

	@Test
	void commonPathOffsetOnlyChangesReferenceDelay() {
		CoherentDiffractionCombiner.Result first =
				CoherentDiffractionCombiner.combine(
						2_000.0,
						343.0,
						List.of(path(2.0), path(2.2))
				);
		CoherentDiffractionCombiner.Result shifted =
				CoherentDiffractionCombiner.combine(
						2_000.0,
						343.0,
						List.of(path(102.0), path(102.2))
				);

		assertEquals(
				first.coherentPressure().real(),
				shifted.coherentPressure().real(),
				1.0e-12
		);
		assertEquals(
				first.coherentPressure().imaginary(),
				shifted.coherentPressure().imaginary(),
				1.0e-12
		);
		assertEquals(100.0 / 343.0,
				shifted.referenceDelaySeconds() - first.referenceDelaySeconds(),
				1.0e-12);
	}

	@Test
	void sequentialEdgesMultiplyBeforeAlternativePathsAdd() {
		ComplexPressure cascade = CoherentDiffractionCombiner.cascade(List.of(
				new ComplexPressure(0.5, 0.0),
				new ComplexPressure(0.0, -0.5)
		));

		assertEquals(0.0, cascade.real(), 1.0e-12);
		assertEquals(-0.25, cascade.imaginary(), 1.0e-12);
	}

	private static CoherentDiffractionCombiner.PathContribution path(double length) {
		return new CoherentDiffractionCombiner.PathContribution(
				length,
				1.0,
				ComplexPressure.ONE
		);
	}
}
