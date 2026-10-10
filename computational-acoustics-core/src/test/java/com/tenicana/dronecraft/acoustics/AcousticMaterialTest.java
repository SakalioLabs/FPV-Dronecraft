package com.tenicana.dronecraft.acoustics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcousticMaterialTest {
	@Test
	void convertsTransmissionLossDbToEnergyGain() {
		AcousticMaterial material = new AcousticMaterial(
				"test",
				new AcousticBands(10.0, 20.0, 30.0),
				AcousticBands.SILENT,
				0.0
		);

		AcousticBands gain = material.transmissionEnergyGain(0.5);

		assertEquals(Math.pow(10.0, -0.5), gain.low(), 1.0e-12);
		assertEquals(Math.pow(10.0, -1.0), gain.mid(), 1.0e-12);
		assertEquals(Math.pow(10.0, -1.5), gain.high(), 1.0e-12);
	}

	@Test
	void rejectsAbsorptionOutsideUnitInterval() {
		assertThrows(IllegalArgumentException.class, () -> new AcousticMaterial(
				"invalid",
				AcousticBands.SILENT,
				new AcousticBands(0.0, 1.1, 0.0),
				0.0
		));
	}

	@Test
	void materialTableHasStableDiagnosticIdentity() {
		assertEquals(1, AcousticMaterials.DIAGNOSTIC_SCHEMA_VERSION);
		assertEquals(8, AcousticMaterials.diagnosticTable().size());
		assertEquals(
				"8bed6d00ec4e433107ad44ad8178dc72fb4d6fb7633577c5cf9ea618f5ae84d8",
				AcousticMaterials.diagnosticSha256()
		);
		assertEquals(
				AcousticMaterials.diagnosticSha256(),
				AcousticMaterials.diagnosticSha256()
		);
		assertTrue(AcousticMaterials.diagnosticTable().contains(AcousticMaterial.AIR));
		assertThrows(
				UnsupportedOperationException.class,
				() -> AcousticMaterials.diagnosticTable().clear()
		);
	}
}
