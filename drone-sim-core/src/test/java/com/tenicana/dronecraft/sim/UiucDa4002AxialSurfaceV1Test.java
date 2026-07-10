package com.tenicana.dronecraft.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1.PropellerEnvelope;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.NominalTrackEnvelope;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.Propeller;

class UiucDa4002AxialSurfaceV1Test {
	private static final String EXPECTED_SOURCE_DATA_SHA256 =
			"abf67ed5ba706cba92f97fc18834e846ee7241a03a1179d09c381969421951ad";
	private static final String EXPECTED_ALGORITHM_SHA256 =
			"2d0457e49d0032d33d896890a92362fd1b324a3467e2f902106a14fc97ece8e5";

	@Test
	void freezesSourceRowsAlgorithmAndNonRectangularEnvelope() {
		assertEquals("uiuc-da4002-axial-surface-v1",
				UiucDa4002AxialSurfaceV1.VERSION_ID);
		assertEquals(EXPECTED_SOURCE_DATA_SHA256,
				UiucDa4002AxialSurfaceV1.sourceDataSha256());
		assertEquals(EXPECTED_ALGORITHM_SHA256,
				UiucDa4002AxialSurfaceV1.interpolationAlgorithmSha256());
		assertEquals(32, UiucDa4002AxialSurfaceV1.staticSourceRowCount());
		assertEquals(112, UiucDa4002AxialSurfaceV1.advanceSourceRowCount());
		assertEquals(12, UiucDa4002AxialSurfaceV1.referenceSlices().size());

		PropellerEnvelope five = UiucDa4002AxialSurfaceV1.envelope(
				Propeller.DA4002_5X3_75);
		assertEquals(1_410.0, five.staticRpmEnvelope().minimumRpm(), 0.0);
		assertEquals(7_440.0, five.staticRpmEnvelope().maximumRpm(), 0.0);
		assertEquals(4_000.0, five.minimumForwardRpm(), 0.0);
		assertEquals(6_000.0, five.maximumForwardRpm(), 0.0);
		assertTrackEnvelope(five.nominalTrackEnvelopes(), 0,
				4_000.0, 0.857870, 1);
		assertTrackEnvelope(five.nominalTrackEnvelopes(), 1,
				5_000.0, 0.851340, 1);
		assertTrackEnvelope(five.nominalTrackEnvelopes(), 2,
				6_000.0, 0.895451, 1);

		PropellerEnvelope nine = UiucDa4002AxialSurfaceV1.envelope(
				Propeller.DA4002_9X6_75);
		assertEquals(1_546.667, nine.staticRpmEnvelope().minimumRpm(), 0.0);
		assertEquals(5_943.333, nine.staticRpmEnvelope().maximumRpm(), 0.0);
		assertEquals(2_000.0, nine.minimumForwardRpm(), 0.0);
		assertEquals(5_000.0, nine.maximumForwardRpm(), 0.0);
		assertTrackEnvelope(nine.nominalTrackEnvelopes(), 0,
				2_000.0, 0.894262, 1);
		assertTrackEnvelope(nine.nominalTrackEnvelopes(), 1,
				3_000.0, 0.887498, 1);
		assertTrackEnvelope(nine.nominalTrackEnvelopes(), 2,
				4_000.0, 0.865364, 2);
		assertTrackEnvelope(nine.nominalTrackEnvelopes(), 3,
				5_000.0, 0.914534, 2);

		for (PropellerEnvelope envelope : List.of(five, nine)) {
			var re = envelope.referenceReynoldsEnvelope();
			assertTrue(re.diagnosticOnly());
			assertTrue(re.minimumStaticRotationalReynolds75() > 0.0);
			assertTrue(re.maximumStaticRotationalReynolds75()
					> re.minimumStaticRotationalReynolds75());
			assertTrue(re.maximumForwardRotationalReynolds75()
					> re.minimumForwardRotationalReynolds75());
			assertTrue(re.maximumForwardResultantReynolds75()
					> re.maximumForwardRotationalReynolds75());
		}

		assertFalse(UiucDa4002AxialSurfaceV1.evaluate(
				Propeller.DA4002_5X3_75, 0.0, 2_000.0).blocked());
		assertTrue(UiucDa4002AxialSurfaceV1.evaluate(
				Propeller.DA4002_5X3_75, 0.1, 2_000.0).blocked());
		assertFalse(UiucDa4002AxialSurfaceV1.evaluate(
				Propeller.DA4002_9X6_75, 0.86, 4_500.0).blocked());
		var blocked = UiucDa4002AxialSurfaceV1.evaluate(
				Propeller.DA4002_9X6_75, 0.87, 4_500.0);
		assertTrue(blocked.blocked());
		assertFalse(blocked.clamped());
		assertTrue(blocked.outOfEnvelope());
	}

	@Test
	void stableFacadeRemainsBitIdenticalToTheMeasuredProductionModel() {
		List<Query> queries = List.of(
				new Query(Propeller.DA4002_5X3_75, 0.0, 4_500.0),
				new Query(Propeller.DA4002_5X3_75, 0.4, 4_500.0),
				new Query(Propeller.DA4002_9X6_75, 0.6, 3_500.0),
				new Query(Propeller.DA4002_9X6_75, 0.9, 5_000.0),
				new Query(Propeller.DA4002_9X6_75, 0.9, 4_000.0)
		);
		for (Query query : queries) {
			var expected = UiucDa4002MeasuredRotorModel.sample(
					query.propeller(), query.advanceRatioJ(), query.rpm(),
					UiucDa4002AxialSurfaceV1
							.REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER,
					UiucDa4002AxialSurfaceV1
							.REFERENCE_DYNAMIC_VISCOSITY_PASCAL_SECONDS);
			var actual = UiucDa4002AxialSurfaceV1.sample(
					query.propeller(), query.advanceRatioJ(), query.rpm(),
					UiucDa4002AxialSurfaceV1
							.REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER,
					UiucDa4002AxialSurfaceV1
							.REFERENCE_DYNAMIC_VISCOSITY_PASCAL_SECONDS);
			assertEquals(expected, actual);
		}
	}

	private static void assertTrackEnvelope(
			List<NominalTrackEnvelope> tracks,
			int index,
			double rpm,
			double maximumAdvanceRatioJ,
			int sourceCurveCount
	) {
		NominalTrackEnvelope track = tracks.get(index);
		assertEquals(rpm, track.nominalRpm(), 0.0);
		assertEquals(0.0, track.minimumSupportedAdvanceRatioJ(), 0.0);
		assertTrue(track.firstMeasuredAdvanceRatioJ() > 0.0);
		assertEquals(maximumAdvanceRatioJ,
				track.maximumSupportedAdvanceRatioJ(), 0.0);
		assertEquals(sourceCurveCount, track.sourceCurveIds().size());
	}

	private record Query(Propeller propeller, double advanceRatioJ, double rpm) {
	}
}
