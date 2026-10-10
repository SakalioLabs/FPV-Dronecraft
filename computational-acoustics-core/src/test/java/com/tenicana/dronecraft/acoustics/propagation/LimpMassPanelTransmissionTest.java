package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LimpMassPanelTransmissionTest {
	private static final LimpMassPanelTransmission BRAS_MDF =
			LimpMassPanelTransmission.fromDensityAndThickness(
					742.4,
					0.025,
					1.204,
					343.0
			);

	@Test
	void derivesOfficialBrasSurfaceMass() {
		assertEquals(
				18.56,
				BRAS_MDF.surfaceMassKilogramsPerSquareMeter(),
				1.0e-12
		);
	}

	@Test
	void reproducesIndependentBrasMassLawOracle() {
		assertEquals(42.996, BRAS_MDF.transmissionLossDb(1_000.0), 0.001);
		assertEquals(64.580, BRAS_MDF.transmissionLossDb(12_000.0), 0.001);
	}

	@Test
	void pressureAndEnergyContractsAgree() {
		double pressure = BRAS_MDF.pressureMagnitude(2_000.0);
		assertEquals(
				pressure * pressure,
				BRAS_MDF.energyTransmission(2_000.0),
				1.0e-15
		);
		assertEquals(1.0, BRAS_MDF.pressureMagnitude(0.0), 0.0);
	}

	@Test
	void highFrequencyMassLawRisesSixDbPerOctave() {
		double octave = BRAS_MDF.transmissionLossDb(200_000.0)
				- BRAS_MDF.transmissionLossDb(100_000.0);
		assertEquals(6.0206, octave, 0.0001);
	}

	@Test
	void createsExplicitThreeBandBoundaryLoss() {
		AcousticBands bands = BRAS_MDF.transmissionLossBands(
				250.0,
				1_000.0,
				4_000.0
		);
		assertEquals(30.958, bands.low(), 0.001);
		assertEquals(42.996, bands.mid(), 0.001);
		assertEquals(55.037, bands.high(), 0.001);
	}

	@Test
	void rejectsInvalidPanelAndFrequencyInputs() {
		assertThrows(
				IllegalArgumentException.class,
				() -> LimpMassPanelTransmission.fromDensityAndThickness(
						742.4,
						0.0,
						1.204,
						343.0
				)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> BRAS_MDF.pressureMagnitude(-1.0)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> BRAS_MDF.transmissionLossBands(1_000.0, 500.0, 4_000.0)
		);
	}
}
