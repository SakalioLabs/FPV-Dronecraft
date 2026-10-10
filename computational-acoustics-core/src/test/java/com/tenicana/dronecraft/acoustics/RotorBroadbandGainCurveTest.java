package com.tenicana.dronecraft.acoustics;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotorBroadbandGainCurveTest {
	@Test
	void interpolatesEachBandInLogRpmAndDbSpaceAsEnergy() {
		RotorBroadbandGainCurve curve = new RotorBroadbandGainCurve(List.of(
				new RotorBroadbandGainCurve.Anchor(10_000.0, -6.0, 0.0, 12.0),
				new RotorBroadbandGainCurve.Anchor(40_000.0, 6.0, -12.0, 0.0)
		));

		AcousticBands midpoint = curve.evaluate(20_000.0);

		assertEquals(1.0, midpoint.low(), 1.0e-12);
		assertEquals(dbToEnergy(-6.0), midpoint.mid(), 1.0e-12);
		assertEquals(dbToEnergy(6.0), midpoint.high(), 1.0e-12);
		assertFalse(curve.isUnity());
	}

	@Test
	void clampsAllBandsOutsideAnchorRangeIncludingStoppedRotor() {
		RotorBroadbandGainCurve curve = new RotorBroadbandGainCurve(List.of(
				new RotorBroadbandGainCurve.Anchor(10_000.0, -120.0, -3.0, 60.0),
				new RotorBroadbandGainCurve.Anchor(20_000.0, 6.0, 3.0, -2.0)
		));

		assertEquals(new AcousticBands(1.0e-12, dbToEnergy(-3.0), 1.0e6), curve.evaluate(0.0));
		assertEquals(curve.evaluate(10_000.0), curve.evaluate(1_000.0));
		assertEquals(curve.evaluate(20_000.0), curve.evaluate(100_000.0));
	}

	@Test
	void unityHasNoRpmLawAndRecognizesZeroGainAnchors() {
		RotorBroadbandGainCurve measuredUnity = new RotorBroadbandGainCurve(List.of(
				new RotorBroadbandGainCurve.Anchor(4_000.0, 0.0, -0.0, 0.0),
				new RotorBroadbandGainCurve.Anchor(8_000.0, 0.0, 0.0, 0.0)
		));
		assertTrue(measuredUnity.isUnity());
		for (double rpm : new double[]{0.0, 1_000.0, 10_000.0, Double.MAX_VALUE}) {
			assertEquals(new AcousticBands(1.0, 1.0, 1.0), RotorBroadbandGainCurve.unity().evaluate(rpm));
			assertEquals(RotorBroadbandGainCurve.unity().evaluate(rpm), measuredUnity.evaluate(rpm));
		}
	}

	@Test
	void copiesAnchorsAndExposesAnImmutableList() {
		List<RotorBroadbandGainCurve.Anchor> supplied = new ArrayList<>(List.of(
				new RotorBroadbandGainCurve.Anchor(4_000.0, 1.0, 2.0, 3.0)
		));
		RotorBroadbandGainCurve curve = new RotorBroadbandGainCurve(supplied);
		supplied.clear();

		assertEquals(1, curve.anchors().size());
		assertThrows(UnsupportedOperationException.class, () -> curve.anchors().clear());
	}

	@Test
	void rejectsEmptyDuplicateAndDescendingAnchors() {
		assertThrows(IllegalArgumentException.class, () -> new RotorBroadbandGainCurve(List.of()));
		for (double secondRpm : new double[]{10_000.0, 5_000.0}) {
			assertThrows(IllegalArgumentException.class, () -> new RotorBroadbandGainCurve(List.of(
					new RotorBroadbandGainCurve.Anchor(10_000.0, 0.0, 0.0, 0.0),
					new RotorBroadbandGainCurve.Anchor(secondRpm, 0.0, 0.0, 0.0)
			)));
		}
	}

	@Test
	void rejectsInvalidRpmAndEveryOutOfBoundsBand() {
		for (double rpm : new double[]{0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertThrows(IllegalArgumentException.class, () ->
					new RotorBroadbandGainCurve.Anchor(rpm, 0.0, 0.0, 0.0));
		}
		for (double gain : new double[]{-120.01, 60.01, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertThrows(IllegalArgumentException.class, () ->
					new RotorBroadbandGainCurve.Anchor(10_000.0, gain, 0.0, 0.0));
			assertThrows(IllegalArgumentException.class, () ->
					new RotorBroadbandGainCurve.Anchor(10_000.0, 0.0, gain, 0.0));
			assertThrows(IllegalArgumentException.class, () ->
					new RotorBroadbandGainCurve.Anchor(10_000.0, 0.0, 0.0, gain));
		}
		for (double rpm : new double[]{-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertThrows(IllegalArgumentException.class, () -> RotorBroadbandGainCurve.unity().evaluate(rpm));
		}
	}

	private static double dbToEnergy(double gainDb) {
		return Math.pow(10.0, gainDb / 10.0);
	}
}
