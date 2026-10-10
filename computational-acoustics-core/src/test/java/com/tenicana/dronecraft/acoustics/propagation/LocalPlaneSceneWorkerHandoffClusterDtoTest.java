package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPlaneSceneWorkerHandoffClusterDtoTest {
	@Test
	void workerPublishesCompleteClusterEnergyAndPathCount() {
		AxisAlignedPlanePatch floor = new AxisAlignedPlanePatch(
				1, 1, 0.0,
				-8.0, 8.0,
				-8.0, 8.0,
				AcousticMaterials.STONE
		);
		var snapshot = new LocalPlaneSceneWorkerHandoff.SceneSnapshot(
				true,
				List.of(floor),
				(x, y, z) -> false,
				LocalPlaneReflectionSolver.SegmentBlockQuery.NONE,
				LocalPlaneReflectionSolver.CellCoverageQuery.ALL,
				64
		);
		try (var handoff = new LocalPlaneSceneWorkerHandoff(1)) {
			handoff.start();
			assertTrue(handoff.submit(
					0, 1,
					0, 2, 0,
					2, 2, 0,
					snapshot
			));
			var output = new LocalPlaneSceneWorkerHandoff.Result();
			long deadline = System.nanoTime() + 2_000_000_000L;
			while (!handoff.pollLatest(0, 1, output)
					&& System.nanoTime() < deadline) {
				Thread.onSpinWait();
			}

			assertEquals(1, output.clusterCount());
			assertEquals(1, output.clusterPathCount(0));
			assertTrue(output.clusterArrivalSamples(0) > 0.0);
			assertTrue(output.clusterLow(0) > 0.0);
			assertTrue(output.clusterMid(0) > 0.0);
			assertTrue(output.clusterHigh(0) > 0.0);
		}
	}
}
