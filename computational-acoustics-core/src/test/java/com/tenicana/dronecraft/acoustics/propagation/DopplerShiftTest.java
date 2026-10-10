package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.AcousticListenerFrame;
import com.tenicana.dronecraft.acoustics.AcousticSourceFrame;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.RotorAcousticState;
import com.tenicana.dronecraft.acoustics.TonalComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DopplerShiftTest {
	private static final AcousticEmissionFrame EMISSION = new AcousticEmissionFrame(
			List.of(new TonalComponent(
					TonalComponent.Kind.BLADE_PASS, 0, 1, 1_000.0, 0.2, 0.3
			)),
			AcousticBands.SILENT
	);

	@Test
	void approachingSourceRaisesFrequencyAndPreservesPhase() {
		DopplerShift.Result result = DopplerShift.apply(
				EMISSION,
				source(new AcousticVector(10.0, 0.0, 0.0), new AcousticVector(-30.0, 0.0, 0.0)),
				new AcousticListenerFrame(AcousticVector.ZERO, AcousticVector.ZERO),
				343.42
		);

		assertTrue(result.frequencyRatio() > 1.0);
		assertEquals(1_000.0 * result.frequencyRatio(), result.emission().tones().getFirst().frequencyHz(), 1.0e-9);
		assertEquals(0.3, result.emission().tones().getFirst().phaseRadians(), 1.0e-12);
	}

	@Test
	void listenerMovingTowardSourceRaisesFrequency() {
		DopplerShift.Result result = DopplerShift.apply(
				EMISSION,
				source(new AcousticVector(10.0, 0.0, 0.0), AcousticVector.ZERO),
				new AcousticListenerFrame(AcousticVector.ZERO, new AcousticVector(20.0, 0.0, 0.0)),
				343.42
		);

		assertTrue(result.frequencyRatio() > 1.0);
	}

	@Test
	void clampsTeleportVelocityToStableRatio() {
		DopplerShift.Result result = DopplerShift.apply(
				EMISSION,
				source(new AcousticVector(10.0, 0.0, 0.0), new AcousticVector(-10_000.0, 0.0, 0.0)),
				new AcousticListenerFrame(AcousticVector.ZERO, AcousticVector.ZERO),
				343.42
		);

		assertEquals(2.0, result.frequencyRatio(), 1.0e-12);
	}

	private static AcousticSourceFrame source(AcousticVector position, AcousticVector velocity) {
		return new AcousticSourceFrame(
				1L,
				0L,
				position,
				velocity,
				new AcousticVector(0.0, 1.0, 0.0),
				0.1,
				List.of(new RotorAcousticState(10_000, 0.5, 1.0, 0.0635, 3, 7, 1, 0.0))
		);
	}
}
