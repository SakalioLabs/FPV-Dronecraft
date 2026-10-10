package com.tenicana.dronecraft.acoustics.diffraction;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.path.SparseAirGrid;
import com.tenicana.dronecraft.acoustics.path.VoxelBounds;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelDiffractionEdgeExtractorTest {
	@Test
	void extractsKnownInsideCornerAndExactBlockEdgeApex() {
		SparseAirGrid grid = gridWithSingleObstacle(new VoxelDda.Cell(2, 0, 2));
		List<VoxelDda.Cell> path = List.of(
				new VoxelDda.Cell(0, 0, 2),
				new VoxelDda.Cell(1, 0, 2),
				new VoxelDda.Cell(1, 0, 3),
				new VoxelDda.Cell(4, 0, 3)
		);

		List<VoxelDiffractionEdge> edges =
				VoxelDiffractionEdgeExtractor.extract(path, grid, 3);

		assertEquals(1, edges.size());
		VoxelDiffractionEdge edge = edges.getFirst();
		assertEquals(new VoxelDda.Cell(2, 0, 2), edge.obstacleCell());
		assertEquals(new AcousticVector(2.0, 0.5, 3.0), edge.apexPoint());
		assertEquals(
				new VoxelDiffractionEdge.AxisDirection(0, 1, 0),
				edge.edgeAxis()
		);
	}

	@Test
	void rejectsTurnWithoutKnownSolidInsideCorner() {
		SparseAirGrid grid = gridWithSingleObstacle(null);
		List<VoxelDda.Cell> path = List.of(
				new VoxelDda.Cell(0, 0, 0),
				new VoxelDda.Cell(2, 0, 0),
				new VoxelDda.Cell(2, 0, 2)
		);

		assertTrue(VoxelDiffractionEdgeExtractor.extract(path, grid, 3).isEmpty());
	}

	@Test
	void respectsPrincipalEdgeLimitInPathOrder() {
		VoxelBounds bounds = new VoxelBounds(0, 0, 0, 4, 0, 4);
		SparseAirGrid.Builder builder = passable(bounds);
		builder.sample(0, 0, 0, false);
		builder.sample(2, 0, 1, false);
		List<VoxelDda.Cell> path = List.of(
				new VoxelDda.Cell(0, 0, 1),
				new VoxelDda.Cell(1, 0, 1),
				new VoxelDda.Cell(1, 0, 0),
				new VoxelDda.Cell(3, 0, 0),
				new VoxelDda.Cell(3, 0, 2),
				new VoxelDda.Cell(4, 0, 2)
		);

		List<VoxelDiffractionEdge> edges =
				VoxelDiffractionEdgeExtractor.extract(path, builder.build(), 1);

		assertEquals(1, edges.size());
	}

	private static SparseAirGrid gridWithSingleObstacle(VoxelDda.Cell obstacle) {
		VoxelBounds bounds = new VoxelBounds(0, 0, 0, 4, 0, 4);
		SparseAirGrid.Builder builder = passable(bounds);
		if (obstacle != null) {
			builder.sample(obstacle.x(), obstacle.y(), obstacle.z(), false);
		}
		return builder.build();
	}

	private static SparseAirGrid.Builder passable(VoxelBounds bounds) {
		SparseAirGrid.Builder builder = SparseAirGrid.builder(bounds);
		for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
			for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
				for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
					builder.sample(x, y, z, true);
				}
			}
		}
		return builder;
	}
}
