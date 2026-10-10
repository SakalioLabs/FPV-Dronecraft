package com.tenicana.dronecraft.acoustics.diffraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdfaInfiniteWedgeFilterTest {
	private static final UdfaInfiniteWedgeFilter FILTER =
			new UdfaInfiniteWedgeFilter(
					UdfaInfiniteWedgeFilter.Parameters.published2023()
			);
	private static final InfiniteWedgeGeometry KNIFE_EDGE =
			new InfiniteWedgeGeometry(
					1.5,
					3.5,
					0.0,
					Math.PI + 0.5,
					2.0 * Math.PI,
					Math.PI / 2.0,
					343.0
			);

	@Test
	void publishedKnifeEdgeCutoffMatchesEquationFour() {
		double theta = KNIFE_EDGE.receiverAzimuthRadians();
		double expected = 2.0 * 343.0
				/ (Math.PI * Math.PI
				* KNIFE_EDGE.characteristicDistanceMeters())
				* Math.pow(
						0.5 / Math.sin(0.25),
						2.0
				);

		assertEquals(expected, FILTER.cutoffFrequencyHz(theta, KNIFE_EDGE), 1.0e-10);
	}

	@Test
	void knifeEdgeIsUnityAtDcAndMonotonicAtAudioFrequencies() {
		assertEquals(1.0, FILTER.pressureMagnitude(0.0, KNIFE_EDGE), 1.0e-12);
		double low = FILTER.pressureMagnitude(250.0, KNIFE_EDGE);
		double mid = FILTER.pressureMagnitude(1_000.0, KNIFE_EDGE);
		double high = FILTER.pressureMagnitude(4_000.0, KNIFE_EDGE);

		assertTrue(low > mid);
		assertTrue(mid > high);
		assertTrue(high > 0.0);
	}

	@Test
	void highFrequencyAsymptoteApproachesMinusThreeDbPerOctave() {
		double first = FILTER.pressureMagnitude(200_000.0, KNIFE_EDGE);
		double octave = FILTER.pressureMagnitude(400_000.0, KNIFE_EDGE);
		double changeDb = 20.0 * Math.log10(octave / first);

		assertEquals(-3.0103, changeDb, 0.08);
	}

	@Test
	void sourceReceiverDistanceSwapPreservesMagnitude() {
		InfiniteWedgeGeometry swapped = new InfiniteWedgeGeometry(
				KNIFE_EDGE.receiverDistanceMeters(),
				KNIFE_EDGE.sourceDistanceMeters(),
				KNIFE_EDGE.sourceAzimuthRadians(),
				KNIFE_EDGE.receiverAzimuthRadians(),
				KNIFE_EDGE.exteriorWedgeAngleRadians(),
				KNIFE_EDGE.incidenceAngleRadians(),
				KNIFE_EDGE.speedOfSoundMetersPerSecond()
		);

		assertEquals(
				FILTER.pressureMagnitude(2_000.0, KNIFE_EDGE),
				FILTER.pressureMagnitude(2_000.0, swapped),
				1.0e-12
		);
	}
}
