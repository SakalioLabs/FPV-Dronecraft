package com.tenicana.dronecraft.acoustics.voxel;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DdaParityOracleTest {
	@Test
	void recordsSegmentsAndFirstMaterialCell() {
		DdaParityOracle.RayTrace trace = DdaParityOracle.trace(
				new AcousticVector(0.25, 0.5, 0.5),
				new AcousticVector(3.75, 0.5, 0.5),
				(x, y, z) -> x == 2 ? 7 : DdaParityOracle.AIR_MATERIAL_ID,
				16
		);

		assertEquals(4, trace.visitedCellCount());
		assertEquals(
				List.of(0.75, 1.0, 1.0, 0.75),
				trace.segments().stream()
						.map(DdaParityOracle.Segment::lengthMeters)
						.toList()
		);
		assertEquals(
				new VoxelDda.Cell(2, 0, 0),
				trace.firstMaterialCell().orElseThrow()
		);
		assertEquals(7, trace.segments().get(2).materialId());
		assertTrue(trace.reachedEnd());
		assertFalse(trace.stoppedEarly());
		assertFalse(trace.truncated());
	}

	@Test
	void preservesExactCornerTiePolicy() {
		DdaParityOracle.RayTrace trace = DdaParityOracle.trace(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(2.5, 2.5, 2.5),
				(x, y, z) -> 0,
				16
		);

		assertEquals(
				List.of(
						new VoxelDda.Cell(0, 0, 0),
						new VoxelDda.Cell(1, 1, 1),
						new VoxelDda.Cell(2, 2, 2)
				),
				trace.segments().stream()
						.map(DdaParityOracle.Segment::cell)
						.toList()
		);
		assertEquals(
				new AcousticVector(2.0, 2.0, 2.0).length(),
				trace.segments().stream()
						.mapToDouble(DdaParityOracle.Segment::lengthMeters)
						.sum(),
				1.0e-12
		);
	}

	@Test
	void distinguishesBudgetTruncationFromCompletion() {
		DdaParityOracle.RayTrace trace = DdaParityOracle.trace(
				new AcousticVector(0.1, 0.1, 0.1),
				new AcousticVector(5.1, 0.1, 0.1),
				(x, y, z) -> 0,
				2
		);

		assertEquals(2, trace.visitedCellCount());
		assertFalse(trace.reachedEnd());
		assertFalse(trace.stoppedEarly());
		assertTrue(trace.truncated());
	}

	@Test
	void rejectsNegativeMaterialIds() {
		assertThrows(
				IllegalArgumentException.class,
				() -> DdaParityOracle.trace(
						new AcousticVector(0.1, 0.1, 0.1),
						new AcousticVector(1.1, 0.1, 0.1),
						(x, y, z) -> -1,
						4
				)
		);
	}
}
