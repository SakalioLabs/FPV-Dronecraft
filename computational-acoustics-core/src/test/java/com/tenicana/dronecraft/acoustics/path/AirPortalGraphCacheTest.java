package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirPortalGraphCacheTest {
	@Test
	void identicalSnapshotReusesEveryPartition() {
		AirPortalGraphCache cache = new AirPortalGraphCache(8);
		AirPortalGraphCache.UpdateResult cold = cache.update(lineGrid(0, 15, -1));
		AirPortalGraphCache.UpdateResult warm = cache.update(lineGrid(0, 15, -1));

		assertEquals(2, cold.rebuiltPartitionCount());
		assertEquals(0, cold.reusedPartitionCount());
		assertEquals(0, warm.rebuiltPartitionCount());
		assertEquals(2, warm.reusedPartitionCount());
		assertTrue(warm.invalidatedPartitions().isEmpty());
		assertEquals(cold.graph().regions().size(), warm.graph().regions().size());
	}

	@Test
	void oneBlockChangeRebuildsOnlyItsPartition() {
		AirPortalGraphCache cache = new AirPortalGraphCache(8);
		cache.update(lineGrid(0, 15, -1));
		AirPortalGraphCache.UpdateResult changed = cache.update(lineGrid(0, 15, 3));

		assertEquals(1, changed.rebuiltPartitionCount());
		assertEquals(1, changed.reusedPartitionCount());
		assertEquals(
				new AirPortalGraph.Partition(0, 0, 0),
				changed.invalidatedPartitions().getFirst()
		);
		assertTrue(changed.graph().searchRegions(
				new VoxelDda.Cell(0, 0, 0),
				new VoxelDda.Cell(15, 0, 0),
				16
		).regionPath().isEmpty());
	}

	@Test
	void movingBoundsDropsExitedPartitionAndReusesOverlap() {
		AirPortalGraphCache cache = new AirPortalGraphCache(8);
		cache.update(lineGrid(0, 15, -1));
		AirPortalGraphCache.UpdateResult moved = cache.update(lineGrid(8, 23, -1));

		assertEquals(1, moved.rebuiltPartitionCount());
		assertEquals(1, moved.reusedPartitionCount());
		assertEquals(1, moved.removedPartitionCount());
		assertEquals(2, moved.invalidatedPartitions().size());
	}

	private static SparseAirGrid lineGrid(int minX, int maxX, int blockedX) {
		VoxelBounds bounds = new VoxelBounds(minX, 0, 0, maxX, 0, 0);
		SparseAirGrid.Builder builder = SparseAirGrid.builder(bounds);
		for (int x = minX; x <= maxX; x++) {
			builder.sample(x, 0, 0, x != blockedX);
		}
		return builder.build();
	}
}
