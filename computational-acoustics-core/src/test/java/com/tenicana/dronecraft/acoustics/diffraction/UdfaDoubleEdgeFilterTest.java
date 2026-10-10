package com.tenicana.dronecraft.acoustics.diffraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdfaDoubleEdgeFilterTest {
	private static final UdfaInfiniteWedgeFilter.Parameters PARAMETERS =
			UdfaInfiniteWedgeFilter.Parameters.published2023();
	private static final UdfaDoubleEdgeFilter FILTER =
			new UdfaDoubleEdgeFilter(PARAMETERS);

	@Test
	void equationEightApproachesKnifeEdgeAtZeroWidth() {
		double squareExterior = 3.0 * Math.PI / 2.0;

		assertEquals(
				2.0 * Math.PI,
				UdfaDoubleEdgeFilter.modifiedExteriorWedgeAngle(
						squareExterior,
						squareExterior,
						4.0,
						0.0,
						1.0
				),
				1.0e-12
		);
		assertEquals(
				squareExterior,
				UdfaDoubleEdgeFilter.modifiedExteriorWedgeAngle(
						squareExterior,
						squareExterior,
						4.0,
						0.5,
						0.0
				),
				1.0e-12
		);
	}

	@Test
	void zeroWidthDoubleCornerMatchesSingleKnifeEdge() {
		DoubleEdgeGeometry zeroWidth = new DoubleEdgeGeometry(
				1.5,
				1.5,
				3.5,
				3.5,
				0.0,
				0.0,
				Math.PI + 0.5,
				Math.PI + 0.5,
				3.0 * Math.PI / 2.0,
				3.0 * Math.PI / 2.0,
				0.0,
				Math.PI / 2.0,
				343.0,
				1.0
		);
		InfiniteWedgeGeometry knifeEdge = new InfiniteWedgeGeometry(
				1.5,
				3.5,
				0.0,
				Math.PI + 0.5,
				2.0 * Math.PI,
				Math.PI / 2.0,
				343.0
		);
		UdfaInfiniteWedgeFilter single = new UdfaInfiniteWedgeFilter(PARAMETERS);

		assertEquals(
				single.pressureMagnitude(2_000.0, knifeEdge),
				FILTER.pressureMagnitude(2_000.0, zeroWidth),
				1.0e-12
		);
	}

	@Test
	void doubleShadowResponseIsReciprocal() {
		DoubleEdgeGeometry forward = brasRs5Geometry();
		DoubleEdgeGeometry reverse = new DoubleEdgeGeometry(
				forward.receiverDistanceToSecondEdgeMeters(),
				forward.receiverDistanceToFirstEdgeMeters(),
				forward.sourceDistanceToSecondEdgeMeters(),
				forward.sourceDistanceToFirstEdgeMeters(),
				forward.receiverAzimuthAtSecondEdgeRadians(),
				forward.receiverAzimuthAtFirstEdgeRadians(),
				forward.sourceAzimuthAtSecondEdgeRadians(),
				forward.sourceAzimuthAtFirstEdgeRadians(),
				forward.secondExteriorWedgeAngleRadians(),
				forward.firstExteriorWedgeAngleRadians(),
				forward.edgeSeparationMeters(),
				forward.incidenceAngleRadians(),
				forward.speedOfSoundMetersPerSecond(),
				forward.exteriorAngleBlend()
		);

		for (double frequency : new double[] {250.0, 1_000.0, 4_000.0, 16_000.0}) {
			assertEquals(
					FILTER.pressureMagnitude(frequency, forward),
					FILTER.pressureMagnitude(frequency, reverse),
					1.0e-12
			);
		}
	}

	@Test
	void doubleShadowHighFrequencySlopeApproachesMinusSixDbPerOctave() {
		DoubleEdgeGeometry geometry = brasRs5Geometry();
		double first = FILTER.pressureMagnitude(200_000.0, geometry);
		double octave = FILTER.pressureMagnitude(400_000.0, geometry);
		double changeDb = 20.0 * Math.log10(octave / first);

		assertEquals(-6.0206, changeDb, 0.16);
		assertTrue(first > 0.0);
	}

	@Test
	void brasRs5PaperModelValuesAreStable() {
		DoubleEdgeGeometry geometry = brasRs5Geometry();
		double[] frequencies = {1_000.0, 2_000.0, 4_000.0, 8_000.0, 12_000.0};
		double[] expectedDb = {
				-20.40884666189999,
				-25.782947254637524,
				-31.622075798743424,
				-37.59747875043867,
				-41.11032168354754
		};
		double[] measuredOneMsGateDb = {
				-19.377459013938832,
				-18.38748673728017,
				-23.85873617944409,
				-25.244660440850723,
				-24.981399459915416
		};

		double squaredError = 0.0;
		for (int index = 0; index < frequencies.length; index++) {
			double actualDb = 20.0 * Math.log10(
					FILTER.pressureMagnitude(frequencies[index], geometry)
			);
			assertEquals(expectedDb[index], actualDb, 1.0e-9);
			double error = actualDb - measuredOneMsGateDb[index];
			squaredError += error * error;
		}
		assertEquals(10.28358197394669, Math.sqrt(squaredError / 5.0), 1.0e-9);
	}

	private static DoubleEdgeGeometry brasRs5Geometry() {
		return DoubleEdgeGeometry.squareBarrierInPlane(
				new DoubleEdgeGeometry.PlanarPoint(2.487, 1.235),
				new DoubleEdgeGeometry.PlanarPoint(8.512, 1.235),
				new DoubleEdgeGeometry.PlanarPoint(5.487, 2.066),
				new DoubleEdgeGeometry.PlanarPoint(5.512, 2.066),
				Math.PI / 2.0,
				343.0
		);
	}
}
