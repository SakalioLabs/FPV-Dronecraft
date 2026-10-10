package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstOrderArrivalClustererTest {
	@Test
	void clustersEveryVisibleReflectionExactlyOnce() {
		DdaFirstOrderBatchSolver.Workspace paths =
				new DdaFirstOrderBatchSolver.Workspace();
		DdaFirstOrderBatchSolver.solve(
				2.85, 2.98, 1.17,
				2.86, 2.99, 1.18,
				new RoomBounds(5.705, 5.965, 2.355),
				32,
				paths
		);
		BoundedFirstOrderGainSolver.Workspace gains =
				new BoundedFirstOrderGainSolver.Workspace();
		AcousticMaterial[] materials = {
				AcousticMaterials.STONE,
				AcousticMaterials.STONE,
				AcousticMaterials.STONE,
				AcousticMaterials.STONE,
				AcousticMaterials.STONE,
				AcousticMaterials.STONE
		};
		double[] zero = new double[6];
		BoundedFirstOrderGainSolver.solve(
				paths, materials, zero, zero, zero, false, gains
		);
		FirstOrderArrivalClusterer.Workspace clusters =
				new FirstOrderArrivalClusterer.Workspace();

		FirstOrderArrivalClusterer.cluster(paths, gains, clusters);

		int clusteredPaths = 0;
		double previous = Double.NEGATIVE_INFINITY;
		for (int cluster = 0; cluster < clusters.clusterCount(); cluster++) {
			clusteredPaths += clusters.pathCount(cluster);
			assertTrue(clusters.arrivalSamples(cluster) >= previous);
			assertTrue(clusters.low(cluster) > 0.0);
			previous = clusters.arrivalSamples(cluster);
		}
		assertEquals(6, clusteredPaths);
		assertTrue(clusters.clusterCount() < 6);
	}
}
