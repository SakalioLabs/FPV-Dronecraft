package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelAStarTest {
	@Test
	void findsGapAroundWall() {
		VoxelBounds bounds = new VoxelBounds(0, 0, 0, 4, 0, 4);
		SparseAirGrid.Builder grid = fullyPassable(bounds);
		for (int z = 0; z < 4; z++) {
			grid.sample(2, 0, z, false);
		}

		VoxelAStar.SearchResult result = VoxelAStar.search(
				new VoxelDda.Cell(0, 0, 2),
				new VoxelDda.Cell(4, 0, 2),
				grid.build(),
				100
		);

		assertTrue(result.reachedGoal());
		assertTrue(result.path().stream().anyMatch(cell -> cell.x() == 2 && cell.z() == 4));
	}

	@Test
	void followsLShapedAirCorridor() {
		VoxelBounds bounds = new VoxelBounds(0, 0, 0, 3, 0, 3);
		SparseAirGrid.Builder grid = SparseAirGrid.builder(bounds);
		for (int x = 0; x <= 3; x++) {
			grid.sample(x, 0, 0, true);
		}
		for (int z = 0; z <= 3; z++) {
			grid.sample(3, 0, z, true);
		}

		VoxelAStar.SearchResult result = VoxelAStar.search(
				new VoxelDda.Cell(0, 0, 0),
				new VoxelDda.Cell(3, 0, 3),
				grid.build(),
				32
		);

		assertTrue(result.reachedGoal());
		assertEquals(
				List.of(
						new VoxelDda.Cell(0, 0, 0),
						new VoxelDda.Cell(3, 0, 0),
						new VoxelDda.Cell(3, 0, 3)
				),
				VoxelAStar.simplify(result.path())
		);
	}

	@Test
	void reportsBudgetExhaustionWithoutReturningPartialPath() {
		VoxelBounds bounds = new VoxelBounds(0, 0, 0, 20, 0, 0);
		VoxelAStar.SearchResult result = VoxelAStar.search(
				new VoxelDda.Cell(0, 0, 0),
				new VoxelDda.Cell(20, 0, 0),
				fullyPassable(bounds).build(),
				4
		);

		assertFalse(result.reachedGoal());
		assertTrue(result.budgetExhausted());
		assertTrue(result.path().isEmpty());
	}

	@Test
	void unknownCellsRemainImpassable() {
		VoxelBounds bounds = new VoxelBounds(0, 0, 0, 2, 0, 0);
		SparseAirGrid grid = SparseAirGrid.builder(bounds)
				.sample(0, 0, 0, true)
				.sample(2, 0, 0, true)
				.build();

		VoxelAStar.SearchResult result = VoxelAStar.search(
				new VoxelDda.Cell(0, 0, 0),
				new VoxelDda.Cell(2, 0, 0),
				grid,
				10
		);

		assertFalse(result.reachedGoal());
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
