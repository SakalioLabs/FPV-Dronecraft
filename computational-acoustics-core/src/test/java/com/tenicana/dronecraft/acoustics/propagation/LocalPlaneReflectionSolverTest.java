package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPlaneReflectionSolverTest {
	private static final AxisAlignedPlanePatch SLAB_TOP =
			new AxisAlignedPlanePatch(
					1, 1, 0.5,
					0.0, 1.0,
					0.0, 1.0,
					AcousticMaterials.STONE
			);

	@Test
	void continuousSlabTopProducesSubVoxelReflection() {
		LocalPlaneReflectionSolver.Workspace result =
				new LocalPlaneReflectionSolver.Workspace();

		LocalPlaneReflectionSolver.solve(
				0.2, 1.5, 0.4,
				0.8, 1.5, 0.6,
				SLAB_TOP,
				(x, y, z) -> false,
				32,
				result
		);

		assertTrue(result.candidateGeometry());
		assertTrue(result.topologyVisible());
		assertTrue(result.complete());
		assertEquals(0.5, result.reflectionX(), 1.0e-12);
		assertEquals(0.5, result.reflectionY(), 1.0e-12);
		assertEquals(0.5, result.reflectionZ(), 1.0e-12);
		assertEquals(Math.sqrt(4.4), result.pathLengthMeters(), 1.0e-12);
	}

	@Test
	void exactSlabBoxDoesNotSelfOccludeAirSideReflection() {
		LocalPlaneReflectionSolver.Workspace result =
				new LocalPlaneReflectionSolver.Workspace();
		MaterialBoxSegmentBlockQuery exactBlockers =
				new MaterialBoxSegmentBlockQuery(List.of(
						new MaterialBox(
								0.0, 0.0, 0.0,
								1.0, 0.5, 1.0,
								AcousticMaterials.STONE
						)
				));

		LocalPlaneReflectionSolver.solve(
				0.2, 1.5, 0.4,
				0.8, 1.5, 0.6,
				SLAB_TOP,
				(x, y, z) -> false,
				exactBlockers,
				32,
				result
		);

		assertTrue(result.candidateGeometry());
		assertTrue(result.topologyVisible());
		assertTrue(result.complete());
	}

	@Test
	void exactMaterialBoxOnOneLegOccludesReflection() {
		LocalPlaneReflectionSolver.Workspace result =
				new LocalPlaneReflectionSolver.Workspace();
		MaterialBoxSegmentBlockQuery exactBlockers =
				new MaterialBoxSegmentBlockQuery(List.of(
						new MaterialBox(
								0.0, 0.0, 0.0,
								1.0, 0.5, 1.0,
								AcousticMaterials.STONE
						),
						new MaterialBox(
								0.28, 0.85, 0.40,
								0.42, 1.20, 0.50,
								AcousticMaterials.WOOD
						)
				));

		LocalPlaneReflectionSolver.solve(
				0.2, 1.5, 0.4,
				0.8, 1.5, 0.6,
				SLAB_TOP,
				(x, y, z) -> false,
				exactBlockers,
				32,
				result
		);

		assertTrue(result.candidateGeometry());
		assertFalse(result.topologyVisible());
		assertTrue(result.complete());
	}

	@Test
	void blockerOnEitherLegRejectsVisibility() {
		LocalPlaneReflectionSolver.Workspace result =
				new LocalPlaneReflectionSolver.Workspace();

		LocalPlaneReflectionSolver.solve(
				-1.5, 0.5, 0.5,
				-1.5, 1.5, 0.5,
				new AxisAlignedPlanePatch(
						0, -1, 0.0,
						0.0, 2.0,
						0.0, 1.0,
						AcousticMaterials.WOOD
				),
				(x, y, z) -> x == -1 && y == 0,
				32,
				result
		);

		assertTrue(result.candidateGeometry());
		assertFalse(result.topologyVisible());
		assertTrue(result.complete());
	}

	@Test
	void finitePatchAndAirHalfSpaceAreEnforced() {
		LocalPlaneReflectionSolver.Workspace outside =
				new LocalPlaneReflectionSolver.Workspace();
		LocalPlaneReflectionSolver.Workspace wrongSide =
				new LocalPlaneReflectionSolver.Workspace();

		LocalPlaneReflectionSolver.solve(
				2.0, 1.5, 0.5,
				3.0, 1.5, 0.5,
				SLAB_TOP,
				(x, y, z) -> false,
				32,
				outside
		);
		LocalPlaneReflectionSolver.solve(
				0.2, 0.25, 0.4,
				0.8, 1.5, 0.6,
				SLAB_TOP,
				(x, y, z) -> false,
				32,
				wrongSide
		);

		assertFalse(outside.candidateGeometry());
		assertFalse(wrongSide.candidateGeometry());
	}

	@Test
	void cellBudgetExhaustionIsIncompleteNotOccluded() {
		LocalPlaneReflectionSolver.Workspace result =
				new LocalPlaneReflectionSolver.Workspace();

		LocalPlaneReflectionSolver.solve(
				0.2, 20.0, 0.4,
				0.8, 20.0, 0.6,
				SLAB_TOP,
				(x, y, z) -> false,
				2,
				result
		);

		assertTrue(result.candidateGeometry());
		assertFalse(result.topologyVisible());
		assertFalse(result.complete());
	}

	@Test
	void leavingCapturedCellCoverageIsIncompleteNotAir() {
		LocalPlaneReflectionSolver.Workspace result =
				new LocalPlaneReflectionSolver.Workspace();

		LocalPlaneReflectionSolver.solve(
				0.2, 1.5, 0.4,
				0.8, 1.5, 0.6,
				SLAB_TOP,
				(x, y, z) -> false,
				LocalPlaneReflectionSolver.SegmentBlockQuery.NONE,
				(x, y, z) -> y >= 1,
				32,
				result
		);

		assertTrue(result.candidateGeometry());
		assertFalse(result.topologyVisible());
		assertFalse(result.complete());
		assertTrue(result.coverageMiss());
	}
}
