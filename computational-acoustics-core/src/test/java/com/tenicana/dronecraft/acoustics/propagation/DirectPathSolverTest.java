package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectPathSolverTest {
	@Test
	void clearPathHasUnityTransmission() {
		DirectPathSolver.Result result = DirectPathSolver.solve(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(4.5, 0.5, 0.5),
				(x, y, z) -> DirectPathSolver.MaterialSample.AIR,
				16
		);

		assertEquals(1.0, result.transmissionEnergyGain().low(), 1.0e-12);
		assertEquals(1.0, result.transmissionEnergyGain().mid(), 1.0e-12);
		assertEquals(1.0, result.transmissionEnergyGain().high(), 1.0e-12);
		assertEquals(0, result.materialCellCount());
		assertTrue(result.complete());
	}

	@Test
	void accumulatesOnlyLengthInsideMaterialVoxel() {
		DirectPathSolver.Result result = DirectPathSolver.solve(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(2.5, 0.5, 0.5),
				(x, y, z) -> x == 1
						? DirectPathSolver.MaterialSample.full(AcousticMaterials.WOOD)
						: DirectPathSolver.MaterialSample.AIR,
				16
		);

		assertEquals(4.0, result.transmissionLossDb().low(), 1.0e-12);
		assertEquals(9.0, result.transmissionLossDb().mid(), 1.0e-12);
		assertEquals(15.0, result.transmissionLossDb().high(), 1.0e-12);
		assertEquals(1, result.materialCellCount());
	}

	@Test
	void marksBudgetLimitedTraceIncomplete() {
		DirectPathSolver.Result result = DirectPathSolver.solve(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(30.5, 0.5, 0.5),
				(x, y, z) -> DirectPathSolver.MaterialSample.AIR,
				4
		);

		assertFalse(result.complete());
		assertEquals(4, result.visitedCellCount());
	}

	@Test
	void scalesLossByPartialVoxelFill() {
		DirectPathSolver.Result result = DirectPathSolver.solve(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(2.5, 0.5, 0.5),
				(x, y, z) -> x == 1
						? new DirectPathSolver.MaterialSample(AcousticMaterials.WOOD, 0.5)
						: DirectPathSolver.MaterialSample.AIR,
				16
		);

		assertEquals(2.0, result.transmissionLossDb().low(), 1.0e-12);
		assertEquals(4.5, result.transmissionLossDb().mid(), 1.0e-12);
		assertEquals(7.5, result.transmissionLossDb().high(), 1.0e-12);
	}
}
