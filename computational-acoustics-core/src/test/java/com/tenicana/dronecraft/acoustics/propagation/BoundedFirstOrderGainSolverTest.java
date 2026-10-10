package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.BoundedFirstOrderGainSolver.Workspace;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedFirstOrderGainSolverTest {
	private static final double[] ZERO = new double[6];

	@Test
	void materialPriorIsFiniteBoundedAndBandOrdered() {
		DdaFirstOrderBatchSolver.Workspace paths = paths();
		Workspace gains = new Workspace();
		AcousticMaterial[] materials = repeat(AcousticMaterials.WOOD);

		BoundedFirstOrderGainSolver.solve(
				paths, materials, ZERO, ZERO, ZERO, false, gains
		);

		assertEquals(1.0, gains.low(0));
		for (int path = 1; path < 7; path++) {
			assertTrue(gains.low(path) >= gains.mid(path));
			assertTrue(gains.mid(path) >= gains.high(path));
			assertTrue(gains.low(path) >= 0.0);
			assertTrue(gains.low(path) <= 1.0);
		}
	}

	@Test
	void unsupportedQueryIgnoresExtremeCalibration() {
		DdaFirstOrderBatchSolver.Workspace paths = paths();
		Workspace baseline = new Workspace();
		Workspace unsupported = new Workspace();
		AcousticMaterial[] materials = repeat(AcousticMaterials.STONE);
		double[] extreme = {
				1_000.0, -1_000.0, 500.0, -500.0, 100.0, -100.0
		};

		BoundedFirstOrderGainSolver.solve(
				paths, materials, ZERO, ZERO, ZERO, false, baseline
		);
		BoundedFirstOrderGainSolver.solve(
				paths,
				materials,
				extreme,
				extreme,
				extreme,
				false,
				unsupported
		);

		for (int path = 0; path < 7; path++) {
			assertEquals(baseline.low(path), unsupported.low(path), 0.0);
			assertEquals(baseline.mid(path), unsupported.mid(path), 0.0);
			assertEquals(baseline.high(path), unsupported.high(path), 0.0);
		}
	}

	@Test
	void supportedCorrectionsAreClamped() {
		DdaFirstOrderBatchSolver.Workspace paths = paths();
		Workspace extreme = new Workspace();
		Workspace clamped = new Workspace();
		AcousticMaterial[] materials = repeat(AcousticMaterials.METAL);
		double[] high = {100, 100, 100, 100, 100, 100};
		double[] maximum = {3, 3, 3, 3, 3, 3};

		BoundedFirstOrderGainSolver.solve(
				paths, materials, high, high, high, true, extreme
		);
		BoundedFirstOrderGainSolver.solve(
				paths, materials, maximum, maximum, maximum, true, clamped
		);

		for (int path = 1; path < 7; path++) {
			assertEquals(clamped.low(path), extreme.low(path), 0.0);
			assertTrue(extreme.low(path) <= 1.0);
		}
	}

	@Test
	void supportBoundsRequireBothEndpoints() {
		SpatialCalibrationSupport support = new SpatialCalibrationSupport(
				new SpatialSupportBounds(1, 1, 1, 2, 2, 2),
				new SpatialSupportBounds(0, 0, 0, 4, 3, 2)
		);

		assertTrue(support.supports(1, 1, 1, 3, 2, 1));
		assertEquals(
				false,
				support.supports(1, 1, 1, 4.1, 2, 1)
		);
		assertEquals(false, support.supports(0, 1, 1, 3, 2, 1));
	}

	private static DdaFirstOrderBatchSolver.Workspace paths() {
		DdaFirstOrderBatchSolver.Workspace paths =
				new DdaFirstOrderBatchSolver.Workspace();
		DdaFirstOrderBatchSolver.solve(
				1.6330509, 0.6820041, 1.16493109,
				3.50149512, 2.61934068, 1.38561103,
				new RoomBounds(5.705, 5.965, 2.355),
				32,
				paths
		);
		return paths;
	}

	private static AcousticMaterial[] repeat(AcousticMaterial material) {
		return new AcousticMaterial[] {
				material, material, material, material, material, material
		};
	}
}
