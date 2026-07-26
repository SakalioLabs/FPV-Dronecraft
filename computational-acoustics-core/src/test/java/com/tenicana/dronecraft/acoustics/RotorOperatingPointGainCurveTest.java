package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RotorOperatingPointGainCurveTest {
	@Test
	void interpolatesInLogRpmAndDbSpace() {
		RotorOperatingPointGainCurve curve = new RotorOperatingPointGainCurve(List.of(
				new RotorOperatingPointGainCurve.Anchor(
						10_000.0, -6.0, 0.0, 3.0
				),
				new RotorOperatingPointGainCurve.Anchor(
						40_000.0, 6.0, -12.0, -3.0
				)
		));

		RotorOperatingPointGainCurve.Gain midpoint = curve.evaluate(20_000.0);

		assertEquals(1.0, midpoint.rotorTonalAmplitude(), 1.0e-12);
		assertEquals(dbToAmplitude(-6.0), midpoint.motorTonalAmplitude(), 1.0e-12);
		assertEquals(1.0, midpoint.broadbandEnergy(), 1.0e-12);
	}

	@Test
	void clampsOutsideMeasuredRpmRange() {
		RotorOperatingPointGainCurve curve = new RotorOperatingPointGainCurve(List.of(
				new RotorOperatingPointGainCurve.Anchor(
						10_000.0, -6.0, -3.0, -2.0
				),
				new RotorOperatingPointGainCurve.Anchor(
						20_000.0, 6.0, 3.0, 2.0
				)
		));

		assertEquals(
				dbToAmplitude(-6.0),
				curve.evaluate(0.0).rotorTonalAmplitude(),
				1.0e-12
		);
		assertEquals(
				dbToAmplitude(6.0),
				curve.evaluate(100_000.0).rotorTonalAmplitude(),
				1.0e-12
		);
	}

	@Test
	void unityCurveDoesNotInventAnRpmLaw() {
		for (double rpm : new double[]{0.0, 1_000.0, 10_000.0, 100_000.0}) {
			RotorOperatingPointGainCurve.Gain gain =
					RotorOperatingPointGainCurve.unity().evaluate(rpm);
			assertEquals(1.0, gain.rotorTonalAmplitude());
			assertEquals(1.0, gain.motorTonalAmplitude());
			assertEquals(1.0, gain.broadbandEnergy());
		}
	}

	@Test
	void rejectsUnsortedAndOutOfRangeAnchors() {
		assertThrows(IllegalArgumentException.class, () ->
				new RotorOperatingPointGainCurve(List.of(
						new RotorOperatingPointGainCurve.Anchor(
								20_000.0, 0.0, 0.0, 0.0
						),
						new RotorOperatingPointGainCurve.Anchor(
								10_000.0, 0.0, 0.0, 0.0
						)
				))
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> new RotorOperatingPointGainCurve.Anchor(
						10_000.0, 61.0, 0.0, 0.0
				)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> RotorOperatingPointGainCurve.unity().evaluate(Double.NaN)
		);
	}

	private static double dbToAmplitude(double gainDb) {
		return Math.pow(10.0, gainDb / 20.0);
	}
}
