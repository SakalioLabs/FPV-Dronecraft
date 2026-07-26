package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchicalAirPathfinderTest {
	@Test
	void hierarchicalPathMatchesCellOracleAroundWall() {
		VoxelBounds bounds = new VoxelBounds(0, 0, 0, 12, 0, 6);
		SparseAirGrid.Builder builder = fullyPassable(bounds);
		for (int z = 0; z < 6; z++) {
			builder.sample(6, 0, z, false);
		}
		SparseAirGrid grid = builder.build();
		VoxelDda.Cell start = new VoxelDda.Cell(0, 0, 2);
		VoxelDda.Cell goal = new VoxelDda.Cell(12, 0, 2);

		VoxelAStar.SearchResult oracle = VoxelAStar.search(start, goal, grid, 512);
		HierarchicalAirPathfinder.Result hierarchical =
				HierarchicalAirPathfinder.search(start, goal, grid, 4, 64, 512);

		assertTrue(hierarchical.reachedGoal());
		assertFalse(hierarchical.usedCellFallback());
		assertEquals(oracle.path().size(), hierarchical.path().size());
		assertTrue(hierarchical.regionCount() > 1);
		assertTrue(hierarchical.portalCount() > 0);
	}

	@Test
	void unrestrictedOracleRemainsFallbackWhenRegionBudgetIsTooSmall() {
		SparseAirGrid grid = fullyPassable(new VoxelBounds(0, 0, 0, 12, 0, 0)).build();
		HierarchicalAirPathfinder.Result result = HierarchicalAirPathfinder.search(
				new VoxelDda.Cell(0, 0, 0),
				new VoxelDda.Cell(12, 0, 0),
				grid,
				4,
				1,
				64
		);

		assertTrue(result.reachedGoal());
		assertTrue(result.usedCellFallback());
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
