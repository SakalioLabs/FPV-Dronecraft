package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.path.SparseAirGrid;
import com.tenicana.dronecraft.acoustics.path.VoxelBounds;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridPropagationSolverTest {
	private static final AcousticMaterial OPAQUE = new AcousticMaterial(
			"opaque",
			new AcousticBands(80.0, 100.0, 120.0),
			AcousticBands.SILENT,
			0.0
	);

	@Test
	void addsLowFrequencyDetourAroundSingleWall() {
		AcousticVector source = new AcousticVector(0.5, 0.5, 1.5);
		AcousticVector listener = new AcousticVector(4.5, 0.5, 1.5);
		SparseAirGrid grid = wallWithGapGrid();
		HybridPropagationSolver.Result result = HybridPropagationSolver.solve(
				List.of(new MultiPathSolver.Probe(source, 1.0)),
				listener,
				(x, y, z) -> x == 2 && z < 3
						? DirectPathSolver.MaterialSample.full(OPAQUE)
						: DirectPathSolver.MaterialSample.AIR,
				32,
				Optional.of(grid),
				new VoxelDda.Cell(0, 0, 1),
				new VoxelDda.Cell(4, 0, 1),
				128,
				EmpiricalDiffraction.Parameters.researchDefaults()
		);

		assertTrue(result.diffractionApplied());
		assertTrue(result.finalEnergyGain().low() > result.direct().transmissionEnergyGain().low());
		assertTrue(result.finalEnergyGain().low() > result.finalEnergyGain().high());
		assertFalse(result.diffractionEdges().isEmpty());
	}

	@Test
	void clearDirectPathSkipsAStar() {
		AcousticVector source = new AcousticVector(0.5, 0.5, 0.5);
		AcousticVector listener = new AcousticVector(4.5, 0.5, 0.5);
		HybridPropagationSolver.Result result = HybridPropagationSolver.solve(
				List.of(new MultiPathSolver.Probe(source, 1.0)),
				listener,
				(x, y, z) -> DirectPathSolver.MaterialSample.AIR,
				16,
				Optional.of(wallWithGapGrid()),
				new VoxelDda.Cell(0, 0, 0),
				new VoxelDda.Cell(4, 0, 0),
				128,
				EmpiricalDiffraction.Parameters.researchDefaults()
		);

		assertFalse(result.diffractionApplied());
		assertEquals(0, result.aStarVisitedNodeCount());
		assertEquals(1.0, result.finalEnergyGain().mid(), 1.0e-12);
	}

	private static SparseAirGrid wallWithGapGrid() {
		VoxelBounds bounds = new VoxelBounds(0, 0, 0, 4, 0, 3);
		SparseAirGrid.Builder builder = SparseAirGrid.builder(bounds);
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 3; z++) {
				builder.sample(x, 0, z, !(x == 2 && z < 3));
			}
		}
		return builder.build();
	}
}
