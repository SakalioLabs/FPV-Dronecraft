package com.tenicana.dronecraft.acoustics.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Worker-thread cache that rebuilds connected components only for partitions
 * whose known/passable voxel fingerprint changed.
 */
public final class AirPortalGraphCache {
	private final int partitionSize;
	private final Map<AirPortalGraph.Partition, AirPartitionTopology> topologies =
			new HashMap<>();

	public AirPortalGraphCache(int partitionSize) {
		if (partitionSize < 1) {
			throw new IllegalArgumentException("partitionSize must be positive");
		}
		this.partitionSize = partitionSize;
	}

	public UpdateResult update(SparseAirGrid grid) {
		Objects.requireNonNull(grid, "grid");
		List<AirPortalGraph.Partition> required =
				AirPartitionTopology.partitionsFor(grid.bounds(), partitionSize);
		Set<AirPortalGraph.Partition> requiredSet = new HashSet<>(required);
		List<AirPortalGraph.Partition> invalidated = new ArrayList<>();
		int reused = 0;

		for (AirPortalGraph.Partition partition : required) {
			long fingerprint = AirPartitionTopology.fingerprint(
					grid, partitionSize, partition
			);
			AirPartitionTopology cached = topologies.get(partition);
			if (cached != null && cached.fingerprint() == fingerprint) {
				reused++;
				continue;
			}
			topologies.put(
					partition,
					AirPartitionTopology.build(grid, partitionSize, partition)
			);
			invalidated.add(partition);
		}

		List<AirPortalGraph.Partition> removed = topologies.keySet().stream()
				.filter(partition -> !requiredSet.contains(partition))
				.toList();
		for (AirPortalGraph.Partition partition : removed) {
			topologies.remove(partition);
			invalidated.add(partition);
		}

		List<AirPartitionTopology> activeTopologies = required.stream()
				.map(topologies::get)
				.toList();
		return new UpdateResult(
				AirPortalGraph.assemble(grid, partitionSize, activeTopologies),
				invalidated,
				required.size() - reused,
				reused,
				removed.size()
		);
	}

	public int partitionSize() {
		return partitionSize;
	}

	public record UpdateResult(
			AirPortalGraph graph,
			List<AirPortalGraph.Partition> invalidatedPartitions,
			int rebuiltPartitionCount,
			int reusedPartitionCount,
			int removedPartitionCount
	) {
		public UpdateResult {
			Objects.requireNonNull(graph, "graph");
			invalidatedPartitions = List.copyOf(invalidatedPartitions);
		}
	}
}
