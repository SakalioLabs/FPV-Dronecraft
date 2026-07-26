package com.tenicana.dronecraft.acoustics.voxel;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelDdaTest {
	@Test
	void traversesEveryCellAlongAxisAndFindsBlocker() {
		VoxelDda.Trace trace = VoxelDda.trace(
				new AcousticVector(0.25, 1.25, 2.25),
				new AcousticVector(3.75, 1.25, 2.25),
				(x, y, z) -> x == 2 && y == 1 && z == 2,
				16
		);

		assertEquals(4, trace.visitedCells().size());
		assertEquals(new VoxelDda.Cell(0, 1, 2), trace.visitedCells().getFirst());
		assertEquals(new VoxelDda.Cell(3, 1, 2), trace.visitedCells().getLast());
		assertTrue(trace.blocked());
		assertTrue(trace.reachedEnd());
	}

	@Test
	void handlesNegativeCoordinatesAndReportsBudgetExhaustion() {
		VoxelDda.Trace trace = VoxelDda.trace(
				new AcousticVector(-0.1, -0.1, -0.1),
				new AcousticVector(-10.1, -0.1, -0.1),
				(x, y, z) -> false,
				3
		);

		assertEquals(new VoxelDda.Cell(-1, -1, -1), trace.visitedCells().getFirst());
		assertFalse(trace.blocked());
		assertFalse(trace.reachedEnd());
	}

	@Test
	void reportsPhysicalLengthInsideEachVoxel() {
		double[] length = {0.0};
		VoxelDda.WalkResult result = VoxelDda.walk(
				new AcousticVector(0.25, 0.5, 0.5),
				new AcousticVector(3.75, 0.5, 0.5),
				(x, y, z, segmentLengthMeters) -> {
					length[0] += segmentLengthMeters;
					return true;
				},
				16
		);

		assertEquals(3.5, length[0], 1.0e-12);
		assertEquals(4, result.visitedCellCount());
		assertTrue(result.reachedEnd());
	}

	@Test
	void advancesAllAxesAtExactCornerCrossings() {
		VoxelDda.Trace trace = VoxelDda.trace(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(2.5, 2.5, 2.5),
				(x, y, z) -> false,
				16
		);

		assertEquals(
				java.util.List.of(
						new VoxelDda.Cell(0, 0, 0),
						new VoxelDda.Cell(1, 1, 1),
						new VoxelDda.Cell(2, 2, 2)
				),
				trace.visitedCells()
		);
	}

	@Test
	void allocationFreeOpenBoundsMatchesObjectWalk() {
		AcousticVector start = new AcousticVector(0.25, 0.5, 0.5);
		AcousticVector end = new AcousticVector(3.75, 2.5, 1.5);
		VoxelDda.WalkResult object = VoxelDda.walk(
				start,
				end,
				(x, y, z, length) -> x >= 0 && x < 6
						&& y >= 0 && y < 6
						&& z >= 0 && z < 2,
				32
		);
		long packed = VoxelDda.traceOpenBoundsPacked(
				start.x(),
				start.y(),
				start.z(),
				end.x(),
				end.y(),
				end.z(),
				0,
				0,
				0,
				6,
				6,
				2,
				32
		);

		assertEquals(
				object.visitedCellCount(),
				VoxelDda.packedVisitedCellCount(packed)
		);
		assertEquals(
				object.reachedEnd(),
				VoxelDda.packedReachedEnd(packed)
		);
	}
}
