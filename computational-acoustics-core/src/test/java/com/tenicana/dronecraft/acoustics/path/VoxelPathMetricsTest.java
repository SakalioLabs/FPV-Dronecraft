package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelPathMetricsTest {
	@Test
	void measuresOneTurnAndPositiveDetour() {
		VoxelPathMetrics metrics = VoxelPathMetrics.measure(
				new AcousticVector(0.5, 0.5, 0.5),
				new AcousticVector(3.5, 0.5, 3.5),
				List.of(
						new VoxelDda.Cell(0, 0, 0),
						new VoxelDda.Cell(1, 0, 0),
						new VoxelDda.Cell(2, 0, 0),
						new VoxelDda.Cell(3, 0, 0),
						new VoxelDda.Cell(3, 0, 1),
						new VoxelDda.Cell(3, 0, 2),
						new VoxelDda.Cell(3, 0, 3)
				)
		);

		assertEquals(6.0, metrics.pathLengthMeters(), 1.0e-12);
		assertTrue(metrics.extraPathLengthMeters() > 1.7);
		assertEquals(1, metrics.turnCount());
	}
}
