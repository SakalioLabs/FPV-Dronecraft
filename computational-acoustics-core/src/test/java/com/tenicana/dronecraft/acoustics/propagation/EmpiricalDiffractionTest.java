package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.path.VoxelPathMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmpiricalDiffractionTest {
	@Test
	void preservesMoreLowFrequencyEnergyThanHighFrequencyEnergy() {
		EmpiricalDiffraction.Result result = EmpiricalDiffraction.evaluate(
				AcousticBands.SILENT,
				new VoxelPathMetrics(8.0, 2.0, 1),
				EmpiricalDiffraction.Parameters.researchDefaults()
		);

		assertTrue(result.diffractedEnergyGain().low() > result.diffractedEnergyGain().mid());
		assertTrue(result.diffractedEnergyGain().mid() > result.diffractedEnergyGain().high());
	}

	@Test
	void extraLengthAndTurnsReduceEveryBandMonotonically() {
		EmpiricalDiffraction.Parameters parameters = EmpiricalDiffraction.Parameters.researchDefaults();
		EmpiricalDiffraction.Result shortPath = EmpiricalDiffraction.evaluate(
				AcousticBands.SILENT,
				new VoxelPathMetrics(5.0, 1.0, 1),
				parameters
		);
		EmpiricalDiffraction.Result longPath = EmpiricalDiffraction.evaluate(
				AcousticBands.SILENT,
				new VoxelPathMetrics(12.0, 5.0, 3),
				parameters
		);

		assertTrue(shortPath.diffractedEnergyGain().low() > longPath.diffractedEnergyGain().low());
		assertTrue(shortPath.diffractedEnergyGain().mid() > longPath.diffractedEnergyGain().mid());
		assertTrue(shortPath.diffractedEnergyGain().high() > longPath.diffractedEnergyGain().high());
	}

	@Test
	void combinedEnergyNeverDropsDirectPathOrExceedsUnity() {
		AcousticBands direct = new AcousticBands(0.2, 0.1, 0.05);
		EmpiricalDiffraction.Result result = EmpiricalDiffraction.evaluate(
				direct,
				new VoxelPathMetrics(6.0, 2.0, 2),
				EmpiricalDiffraction.Parameters.researchDefaults()
		);

		assertTrue(result.combinedEnergyGain().low() >= direct.low());
		assertTrue(result.combinedEnergyGain().mid() >= direct.mid());
		assertTrue(result.combinedEnergyGain().high() >= direct.high());
		assertTrue(result.combinedEnergyGain().low() <= 1.0);
		assertTrue(result.combinedEnergyGain().mid() <= 1.0);
		assertTrue(result.combinedEnergyGain().high() <= 1.0);
	}
}
