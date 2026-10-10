package com.tenicana.dronecraft.acoustics.diffraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-checks the Java UDFA equations against the independently extracted
 * BRAS RS5 first-arrival reference in docs/scripts/analyze_bras_rs5.py.
 *
 * <p>The measured error is a baseline, not an acceptance threshold: the BRAS
 * partition is 25 mm thick while this comparison deliberately models an ideal
 * infinitely thin screen.</p>
 */
class BrasRs5UdfaReferenceTest {
	private static final double SPEED_OF_SOUND = 343.0;
	private static final double[] FREQUENCIES_HZ = {
			1_000.0, 2_000.0, 4_000.0, 8_000.0, 12_000.0
	};
	private static final double[] MEASURED_ONE_MS_GATE_DB = {
			-19.377459013938832,
			-18.38748673728017,
			-23.85873617944409,
			-25.244660440850723,
			-24.981399459915416
	};
	private static final double[] INDEPENDENT_PYTHON_UDFA_DB = {
			-11.093077327100563,
			-14.070141274882516,
			-17.07131515755849,
			-20.078319599297036,
			-21.838198744105867
	};

	@Test
	void rs5CoordinatesPredictMeasuredFirstArrivalWithinTwoPointOneSamples() {
		double visiblePath = 8.512 - 2.487;
		double sourceEdge = Math.hypot(5.487 - 2.487, 2.066 - 1.235);
		double receiverEdge = Math.hypot(8.512 - 5.487, 2.066 - 1.235);
		double predictedExcessDelayMs = (
				sourceEdge + receiverEdge - visiblePath
		) / SPEED_OF_SOUND * 1_000.0;
		double measuredExcessDelayMs = 31.0 / 44_100.0 * 1_000.0;

		assertEquals(0.656073048408387, predictedExcessDelayMs, 1.0e-12);
		assertEquals(0.7029478458049886, measuredExcessDelayMs, 1.0e-12);
		assertEquals(
				2.067178564190934,
				(measuredExcessDelayMs - predictedExcessDelayMs)
						* 44_100.0 / 1_000.0,
				1.0e-9
		);
	}

	@Test
	void javaEquationsMatchIndependentPythonOracleAndRecordMeasuredError() {
		double sourceHorizontal = 5.487 - 2.487;
		double receiverHorizontal = 8.512 - 5.487;
		double vertical = 2.066 - 1.235;
		double sourceDistance = Math.hypot(sourceHorizontal, vertical);
		double receiverDistance = Math.hypot(receiverHorizontal, vertical);
		double rayAngle = Math.acos(
				(-sourceHorizontal * receiverHorizontal + vertical * vertical)
						/ (sourceDistance * receiverDistance)
		);
		double bendingAngle = Math.PI - rayAngle;
		InfiniteWedgeGeometry geometry = new InfiniteWedgeGeometry(
				sourceDistance,
				receiverDistance,
				0.0,
				Math.PI + bendingAngle,
				2.0 * Math.PI,
				Math.PI / 2.0,
				SPEED_OF_SOUND
		);
		UdfaInfiniteWedgeFilter filter = new UdfaInfiniteWedgeFilter(
				UdfaInfiniteWedgeFilter.Parameters.published2023()
		);

		double squaredError = 0.0;
		for (int index = 0; index < FREQUENCIES_HZ.length; index++) {
			double predictedDb = 20.0 * Math.log10(
					filter.pressureMagnitude(FREQUENCIES_HZ[index], geometry)
			);
			assertEquals(INDEPENDENT_PYTHON_UDFA_DB[index], predictedDb, 1.0e-9);
			double error = predictedDb - MEASURED_ONE_MS_GATE_DB[index];
			squaredError += error * error;
		}

		assertEquals(
				5.829413766996005,
				Math.sqrt(squaredError / FREQUENCIES_HZ.length),
				1.0e-9
		);
	}
}
