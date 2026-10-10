package com.tenicana.dronecraft.acoustics.diffraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdfaSingleTermWedgeFilterTest {
	private static final UdfaInfiniteWedgeFilter.Parameters PARAMETERS =
			UdfaInfiniteWedgeFilter.Parameters.published2023();
	private static final UdfaSingleTermWedgeFilter FILTER =
			new UdfaSingleTermWedgeFilter(PARAMETERS);
	private static final InfiniteWedgeGeometry GEOMETRY =
			new InfiniteWedgeGeometry(
					1.2,
					3.4,
					0.4,
					3.8,
					3.0 * Math.PI / 2.0,
					Math.PI / 2.0,
					343.0
			);

	@Test
	void equationFiveCombinesComponentCutoffsAndGains() {
		UdfaInfiniteWedgeFilter twoTerm = new UdfaInfiniteWedgeFilter(PARAMETERS);
		double wedgeIndex = Math.PI / GEOMETRY.exteriorWedgeAngleRadians();
		double thetaMinus = GEOMETRY.sourceAzimuthRadians()
				- GEOMETRY.receiverAzimuthRadians();
		double thetaPlus = GEOMETRY.sourceAzimuthRadians()
				+ GEOMETRY.receiverAzimuthRadians();
		double expectedRoot = (
				UdfaInfiniteWedgeFilter.angularGain(thetaMinus, wedgeIndex)
						* Math.sqrt(twoTerm.cutoffFrequencyHz(thetaMinus, GEOMETRY))
						+ UdfaInfiniteWedgeFilter.angularGain(thetaPlus, wedgeIndex)
						* Math.sqrt(twoTerm.cutoffFrequencyHz(thetaPlus, GEOMETRY))
		) / 2.0;

		assertEquals(
				expectedRoot * expectedRoot,
				FILTER.cutoffFrequencyHz(GEOMETRY),
				1.0e-12
		);
	}

	@Test
	void singleTermHasUnityDcAndFractionalLowPassAsymptote() {
		assertEquals(1.0, FILTER.pressureMagnitude(0.0, GEOMETRY), 1.0e-12);
		double first = FILTER.pressureMagnitude(200_000.0, GEOMETRY);
		double octave = FILTER.pressureMagnitude(400_000.0, GEOMETRY);
		double changeDb = 20.0 * Math.log10(octave / first);

		assertEquals(-3.0103, changeDb, 0.08);
		assertTrue(first > 0.0);
	}
}
