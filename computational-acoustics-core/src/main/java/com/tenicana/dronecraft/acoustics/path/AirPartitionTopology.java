package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable connected-air topology for one fixed voxel partition.
 */
final class AirPartitionTopology {
	private static final int[][] NEIGHBOURS = {
			{1, 0, 0}, {-1, 0, 0},
			{0, 0, 1}, {0, 0, -1},
			{0, 1, 0}, {0, -1, 0}
	};
	private static final long FNV_OFFSET = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;

	private final AirPortalGraph.Partition partition;
	private final long fingerprint;
	private final List<List<VoxelDda.Cell>> components;

	private AirPartitionTopology(
			AirPortalGraph.Partition partition,
			long fingerprint,
			List<List<VoxelDda.Cell>> components
	) {
		this.partition = partition;
		this.fingerprint = fingerprint;
		this.components = components.stream().map(List::copyOf).toList();
	}

	static AirPartitionTopology build(
			SparseAirGrid grid,
			int partitionSize,
			AirPortalGraph.Partition partition
	) {
		Objects.requireNonNull(grid, "grid");
		Objects.requireNonNull(partition, "partition");
		VoxelBounds clipped = clippedBounds(grid.bounds(), partitionSize, partition);
		Map<VoxelDda.Cell, Integer> componentByCell = new HashMap<>();
		List<List<VoxelDda.Cell>> components = new ArrayList<>();
		for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
			for (int y = clipped.minY(); y <= clipped.maxY(); y++) {
				for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
					VoxelDda.Cell seed = new VoxelDda.Cell(x, y, z);
					if (!grid.isPassable(x, y, z) || componentByCell.containsKey(seed)) {
						continue;
					}
					int componentId = components.size();
					components.add(flood(
							seed, componentId, clipped, grid, componentByCell
					));
				}
			}
		}
		return new AirPartitionTopology(
				partition,
				fingerprint(grid, partitionSize, partition),
				components
		);
	}

	static long fingerprint(
			SparseAirGrid grid,
			int partitionSize,
			AirPortalGraph.Partition partition
	) {
		VoxelBounds clipped = clippedBounds(grid.bounds(), partitionSize, partition);
		long hash = FNV_OFFSET;
		hash = mix(hash, clipped.minX());
		hash = mix(hash, clipped.minY());
		hash = mix(hash, clipped.minZ());
		hash = mix(hash, clipped.maxX());
		hash = mix(hash, clipped.maxY());
		hash = mix(hash, clipped.maxZ());
		for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
			for (int y = clipped.minY(); y <= clipped.maxY(); y++) {
				for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
					int state = !grid.isKnown(x, y, z)
							? 0
							: grid.isPassable(x, y, z) ? 2 : 1;
					hash = mix(hash, state);
				}
			}
		}
		return hash;
	}

	static List<AirPortalGraph.Partition> partitionsFor(
			VoxelBounds bounds,
			int partitionSize
	) {
		List<AirPortalGraph.Partition> partitions = new ArrayList<>();
		for (int x = Math.floorDiv(bounds.minX(), partitionSize);
				x <= Math.floorDiv(bounds.maxX(), partitionSize); x++) {
			for (int y = Math.floorDiv(bounds.minY(), partitionSize);
					y <= Math.floorDiv(bounds.maxY(), partitionSize); y++) {
				for (int z = Math.floorDiv(bounds.minZ(), partitionSize);
						z <= Math.floorDiv(bounds.maxZ(), partitionSize); z++) {
					partitions.add(new AirPortalGraph.Partition(x, y, z));
				}
			}
		}
		return List.copyOf(partitions);
	}

	AirPortalGraph.Partition partition() {
		return partition;
	}

	long fingerprint() {
		return fingerprint;
	}

	List<List<VoxelDda.Cell>> components() {
		return components;
	}

	private static List<VoxelDda.Cell> flood(
			VoxelDda.Cell seed,
			int componentId,
			VoxelBounds clipped,
			SparseAirGrid grid,
			Map<VoxelDda.Cell, Integer> componentByCell
	) {
		ArrayDeque<VoxelDda.Cell> queue = new ArrayDeque<>();
		List<VoxelDda.Cell> cells = new ArrayList<>();
		queue.add(seed);
		componentByCell.put(seed, componentId);
		while (!queue.isEmpty()) {
			VoxelDda.Cell cell = queue.removeFirst();
			cells.add(cell);
			for (int[] offset : NEIGHBOURS) {
				VoxelDda.Cell next = new VoxelDda.Cell(
						cell.x() + offset[0], cell.y() + offset[1], cell.z() + offset[2]
				);
				if (!clipped.contains(next.x(), next.y(), next.z())
						|| !grid.isPassable(next.x(), next.y(), next.z())
						|| componentByCell.containsKey(next)) {
					continue;
				}
				componentByCell.put(next, componentId);
				queue.addLast(next);
			}
		}
		return List.copyOf(cells);
	}

	private static VoxelBounds clippedBounds(
			VoxelBounds gridBounds,
			int partitionSize,
			AirPortalGraph.Partition partition
	) {
		int minX = partition.x() * partitionSize;
		int minY = partition.y() * partitionSize;
		int minZ = partition.z() * partitionSize;
		return new VoxelBounds(
				Math.max(gridBounds.minX(), minX),
				Math.max(gridBounds.minY(), minY),
				Math.max(gridBounds.minZ(), minZ),
				Math.min(gridBounds.maxX(), minX + partitionSize - 1),
				Math.min(gridBounds.maxY(), minY + partitionSize - 1),
				Math.min(gridBounds.maxZ(), minZ + partitionSize - 1)
		);
	}

	private static long mix(long hash, int value) {
		hash ^= value & 0xffL;
		hash *= FNV_PRIME;
		hash ^= value >>> 8 & 0xffL;
		hash *= FNV_PRIME;
		hash ^= value >>> 16 & 0xffL;
		hash *= FNV_PRIME;
		hash ^= value >>> 24 & 0xffL;
		return hash * FNV_PRIME;
	}
}
