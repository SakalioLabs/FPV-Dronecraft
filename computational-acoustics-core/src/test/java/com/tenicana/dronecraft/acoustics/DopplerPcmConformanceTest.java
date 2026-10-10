package com.tenicana.dronecraft.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DopplerPcmConformanceTest {
	private static final AcousticEmissionFrame EMISSION =
			new AcousticEmissionFrame(
					List.of(new TonalComponent(
							TonalComponent.Kind.SHAFT,
							0,
							1,
							1_000.0,
							0.5,
							0.0
					)),
					AcousticBands.SILENT
			);
	private static final RotorAcousticState ROTOR =
			new RotorAcousticState(
					30_000.0,
					0.5,
					1.0,
					0.0635,
					2,
					7,
					1,
					0.0
			);

	@Test
	void quantizedPcmTracksApproachingAndRecedingSource() {
		DopplerPcmConformance.Result approaching = measure(-40.0, 10.0);
		DopplerPcmConformance.Result receding = measure(40.0, -10.0);

		assertTrue(approaching.dopplerFrequencyRatio() > 1.0);
		assertTrue(receding.dopplerFrequencyRatio() < 1.0);
		assertTrue(
				approaching.measuredFrequencyHz()
						> receding.measuredFrequencyHz()
		);
		assertTrue(Math.abs(approaching.frequencyErrorPpm()) < 25.0);
		assertTrue(Math.abs(receding.frequencyErrorPpm()) < 25.0);
		assertEquals(0, approaching.clippedSamples());
		assertTrue(approaching.pcm16Quantized());
		assertTrue(approaching.broadbandExcluded());
	}

	@Test
	void stationaryPcmRetainsBaseFrequency() {
		DopplerPcmConformance.Result stationary = measure(0.0, 0.0);

		assertEquals(1.0, stationary.dopplerFrequencyRatio(), 1.0e-12);
		assertEquals(1_000.0, stationary.expectedFrequencyHz(), 1.0e-9);
		assertEquals(1_000.0, stationary.measuredFrequencyHz(), 0.01);
	}

	@Test
	void frequencyEstimatorRejectsSilentPcm() {
		assertThrows(
				IllegalArgumentException.class,
				() -> DopplerPcmConformance.estimateFrequency(
						new short[1_000],
						100,
						48_000
				)
		);
	}

	private static DopplerPcmConformance.Result measure(
			double sourceVelocity,
			double listenerVelocity
	) {
		AcousticSourceFrame source = new AcousticSourceFrame(
				1L,
				0L,
				new AcousticVector(10.0, 0.0, 0.0),
				new AcousticVector(sourceVelocity, 0.0, 0.0),
				new AcousticVector(0.0, 1.0, 0.0),
				0.0635,
				List.of(ROTOR)
		);
		AcousticListenerFrame listener = new AcousticListenerFrame(
				AcousticVector.ZERO,
				new AcousticVector(listenerVelocity, 0.0, 0.0)
		);
		return DopplerPcmConformance.measure(
				EMISSION,
				source,
				listener,
				343.42,
				PhaseContinuousSynthesizer.Layer.MOTOR
		);
	}
}
