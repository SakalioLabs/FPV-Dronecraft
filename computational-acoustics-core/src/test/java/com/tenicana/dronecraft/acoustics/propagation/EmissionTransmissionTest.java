package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticEmissionFrame;
import com.tenicana.dronecraft.acoustics.TonalComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmissionTransmissionTest {
	@Test
	void convertsEnergyGainToPressureAmplitudeByBand() {
		AcousticEmissionFrame emission = new AcousticEmissionFrame(List.of(
				tone(200.0),
				tone(1_000.0),
				tone(4_000.0)
		), new AcousticBands(2.0, 3.0, 4.0));

		AcousticEmissionFrame propagated = EmissionTransmission.apply(
				emission,
				new AcousticBands(0.25, 0.36, 0.49)
		);

		assertEquals(0.5, propagated.tones().get(0).linearAmplitude(), 1.0e-12);
		assertEquals(0.6, propagated.tones().get(1).linearAmplitude(), 1.0e-12);
		assertEquals(0.7, propagated.tones().get(2).linearAmplitude(), 1.0e-12);
		assertEquals(0.5, propagated.broadbandEnergy().low(), 1.0e-12);
		assertEquals(1.08, propagated.broadbandEnergy().mid(), 1.0e-12);
		assertEquals(1.96, propagated.broadbandEnergy().high(), 1.0e-12);
	}

	private static TonalComponent tone(double frequencyHz) {
		return new TonalComponent(
				TonalComponent.Kind.BLADE_PASS,
				0,
				1,
				frequencyHz,
				1.0,
				0.0
		);
	}
}
