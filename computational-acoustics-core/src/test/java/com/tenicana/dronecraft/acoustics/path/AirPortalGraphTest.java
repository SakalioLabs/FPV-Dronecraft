package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirPortalGraphTest {
	@Test
	void aggregatesBoundaryCellsIntoOnePortal() {
		SparseAirGrid grid = fullyPassable(new VoxelBounds(0, 0, 0, 3, 0, 1)).build();
		AirPortalGraph graph = AirPortalGraph.build(grid, 2);

		assertEquals(2, graph.regions().size());
		assertEquals(1, graph.portals().size());
		assertEquals(2, graph.portals().getFirst().apertureCellCount());
		assertTrue(graph.searchRegions(
				new VoxelDda.Cell(0, 0, 0),
				new VoxelDda.Cell(3, 0, 0),
				8
		).reachedGoal());
	}

	@Test
	void preservesDisconnectedRegionsWithinOnePartition() {
		VoxelBounds bounds = new VoxelBounds(0, 0, 0, 3, 0, 0);
		SparseAirGrid.Builder builder = fullyPassable(bounds);
		builder.sample(1, 0, 0, false);
		AirPortalGraph graph = AirPortalGraph.build(builder.build(), 4);

		assertEquals(2, graph.regions().size());
		assertTrue(graph.portals().isEmpty());
		assertFalse(graph.searchRegions(
				new VoxelDda.Cell(0, 0, 0),
				new VoxelDda.Cell(3, 0, 0),
				8
		).reachedGoal());
	}

	@Test
	void floorDivPartitionsNegativeCoordinatesCorrectly() {
		SparseAirGrid grid = fullyPassable(new VoxelBounds(-2, 0, 0, 1, 0, 0)).build();
		AirPortalGraph graph = AirPortalGraph.build(grid, 2);

		assertEquals(2, graph.regions().size());
		assertTrue(graph.searchRegions(
				new VoxelDda.Cell(-2, 0, 0),
				new VoxelDda.Cell(1, 0, 0),
				8
		).reachedGoal());
	}

	private static SparseAirGrid.Builder fullyPassable(VoxelBounds bounds) {
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
