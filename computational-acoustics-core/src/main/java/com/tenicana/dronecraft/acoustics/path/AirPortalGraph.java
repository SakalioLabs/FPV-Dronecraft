package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Hierarchical graph of connected air regions inside fixed-size voxel
 * partitions. Adjacent regions are connected by aggregated boundary portals.
 */
public final class AirPortalGraph {
	private static final int[][] POSITIVE_NEIGHBOURS = {
			{1, 0, 0}, {0, 1, 0}, {0, 0, 1}
	};

	private final int partitionSize;
	private final Map<VoxelDda.Cell, Integer> regionByCell;
	private final List<Region> regions;
	private final List<Portal> portals;
	private final Map<Integer, List<Portal>> adjacency;

	private AirPortalGraph(
			int partitionSize,
			Map<VoxelDda.Cell, Integer> regionByCell,
			List<Region> regions,
			List<Portal> portals,
			Map<Integer, List<Portal>> adjacency
	) {
		this.partitionSize = partitionSize;
		this.regionByCell = Map.copyOf(regionByCell);
		this.regions = List.copyOf(regions);
		this.portals = List.copyOf(portals);
		this.adjacency = Map.copyOf(adjacency);
	}

	public static AirPortalGraph build(SparseAirGrid grid, int partitionSize) {
		Objects.requireNonNull(grid, "grid");
		if (partitionSize < 1) {
			throw new IllegalArgumentException("partitionSize must be positive");
		}
		List<AirPartitionTopology> topologies =
				AirPartitionTopology.partitionsFor(grid.bounds(), partitionSize).stream()
						.map(partition -> AirPartitionTopology.build(
								grid, partitionSize, partition
						))
						.toList();
		return assemble(grid, partitionSize, topologies);
	}

	static AirPortalGraph assemble(
			SparseAirGrid grid,
			int partitionSize,
			List<AirPartitionTopology> topologies
	) {
		Map<VoxelDda.Cell, Integer> regionByCell = new HashMap<>();
		List<Region> regions = new ArrayList<>();
		List<AirPartitionTopology> ordered = topologies.stream()
				.sorted(Comparator
						.comparingInt((AirPartitionTopology topology) ->
								topology.partition().x())
						.thenComparingInt(topology -> topology.partition().y())
						.thenComparingInt(topology -> topology.partition().z()))
				.toList();
		for (AirPartitionTopology topology : ordered) {
			for (List<VoxelDda.Cell> component : topology.components()) {
				int id = regions.size();
				for (VoxelDda.Cell cell : component) {
					regionByCell.put(cell, id);
				}
				regions.add(new Region(id, topology.partition(), component));
			}
		}

		Map<Long, PortalAccumulator> accumulators = new HashMap<>();
		VoxelBounds bounds = grid.bounds();
		for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
			for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
				for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
					VoxelDda.Cell firstCell = new VoxelDda.Cell(x, y, z);
					Integer firstRegion = regionByCell.get(firstCell);
					if (firstRegion == null) {
						continue;
					}
					for (int[] offset : POSITIVE_NEIGHBOURS) {
						VoxelDda.Cell secondCell = new VoxelDda.Cell(
								x + offset[0], y + offset[1], z + offset[2]
						);
						Integer secondRegion = regionByCell.get(secondCell);
						if (secondRegion == null || firstRegion.equals(secondRegion)) {
							continue;
						}
						int low = Math.min(firstRegion, secondRegion);
						int high = Math.max(firstRegion, secondRegion);
						long key = ((long) low << 32) | (high & 0xffffffffL);
						accumulators.computeIfAbsent(
								key, ignored -> new PortalAccumulator(low, high)
						).add(firstCell, secondCell);
					}
				}
			}
		}

		List<Portal> portals = accumulators.values().stream()
				.map(PortalAccumulator::build)
				.sorted(Comparator.comparingInt(Portal::firstRegionId)
						.thenComparingInt(Portal::secondRegionId))
				.toList();
		Map<Integer, List<Portal>> mutableAdjacency = new HashMap<>();
		for (Portal portal : portals) {
			mutableAdjacency.computeIfAbsent(
					portal.firstRegionId(), ignored -> new ArrayList<>()
			).add(portal);
			mutableAdjacency.computeIfAbsent(
					portal.secondRegionId(), ignored -> new ArrayList<>()
			).add(portal);
		}
		Map<Integer, List<Portal>> adjacency = new HashMap<>();
		for (Map.Entry<Integer, List<Portal>> entry : mutableAdjacency.entrySet()) {
			entry.getValue().sort(Comparator
					.comparingInt((Portal portal) -> portal.other(entry.getKey()))
					.thenComparingInt(Portal::firstRegionId)
					.thenComparingInt(Portal::secondRegionId));
			adjacency.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return new AirPortalGraph(partitionSize, regionByCell, regions, portals, adjacency);
	}

	public RegionSearchResult searchRegions(
			VoxelDda.Cell start,
			VoxelDda.Cell goal,
			int maxVisitedRegions
	) {
		if (maxVisitedRegions < 1) {
			throw new IllegalArgumentException("maxVisitedRegions must be positive");
		}
		OptionalInt startRegion = regionId(start);
		OptionalInt goalRegion = regionId(goal);
		if (startRegion.isEmpty() || goalRegion.isEmpty()) {
			return new RegionSearchResult(List.of(), 0, false, false);
		}

		int target = goalRegion.getAsInt();
		long sequence = 0L;
		PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator
				.comparingInt(SearchNode::estimatedTotalCost)
				.thenComparingInt(SearchNode::heuristic)
				.thenComparingLong(SearchNode::sequence));
		Map<Integer, Integer> bestCost = new HashMap<>();
		Map<Integer, Integer> previous = new HashMap<>();
		int initial = regionHeuristic(startRegion.getAsInt(), target);
		open.add(new SearchNode(startRegion.getAsInt(), 0, initial, sequence++));
		bestCost.put(startRegion.getAsInt(), 0);
		int visited = 0;

		while (!open.isEmpty() && visited < maxVisitedRegions) {
			SearchNode current = open.poll();
			if (current.cost() != bestCost.getOrDefault(current.regionId(), Integer.MAX_VALUE)) {
				continue;
			}
			visited++;
			if (current.regionId() == target) {
				return new RegionSearchResult(
						reconstruct(previous, target), visited, true, false
				);
			}
			for (Portal portal : adjacency.getOrDefault(current.regionId(), List.of())) {
				int next = portal.other(current.regionId());
				int nextCost = current.cost() + 1;
				if (nextCost >= bestCost.getOrDefault(next, Integer.MAX_VALUE)) {
					continue;
				}
				bestCost.put(next, nextCost);
				previous.put(next, current.regionId());
				int heuristic = regionHeuristic(next, target);
				open.add(new SearchNode(next, nextCost, heuristic, sequence++));
			}
		}
		return new RegionSearchResult(List.of(), visited, false, !open.isEmpty());
	}

	public OptionalInt regionId(VoxelDda.Cell cell) {
		Integer region = regionByCell.get(cell);
		return region == null ? OptionalInt.empty() : OptionalInt.of(region);
	}

	public boolean cellBelongsTo(VoxelDda.Cell cell, Set<Integer> allowedRegions) {
		Integer region = regionByCell.get(cell);
		return region != null && allowedRegions.contains(region);
	}

	public int partitionSize() {
		return partitionSize;
	}

	public List<Region> regions() {
		return regions;
	}

	public List<Portal> portals() {
		return portals;
	}

	private int regionHeuristic(int firstRegion, int secondRegion) {
		Partition first = regions.get(firstRegion).partition();
		Partition second = regions.get(secondRegion).partition();
		return Math.abs(first.x() - second.x())
				+ Math.abs(first.y() - second.y())
				+ Math.abs(first.z() - second.z());
	}

	static Partition partitionOf(VoxelDda.Cell cell, int partitionSize) {
		return new Partition(
				Math.floorDiv(cell.x(), partitionSize),
				Math.floorDiv(cell.y(), partitionSize),
				Math.floorDiv(cell.z(), partitionSize)
		);
	}

	private static List<Integer> reconstruct(Map<Integer, Integer> previous, int goal) {
		List<Integer> path = new ArrayList<>();
		Integer region = goal;
		path.add(region);
		while ((region = previous.get(region)) != null) {
			path.add(region);
		}
		Collections.reverse(path);
		return List.copyOf(path);
	}

	public record Partition(int x, int y, int z) {
	}

	public record Region(int id, Partition partition, List<VoxelDda.Cell> cells) {
		public Region {
			cells = List.copyOf(cells);
		}
	}

	public record Portal(
			int firstRegionId,
			int secondRegionId,
			int apertureCellCount,
			double centerX,
			double centerY,
			double centerZ
	) {
		public int other(int regionId) {
			if (regionId == firstRegionId) {
				return secondRegionId;
			}
			if (regionId == secondRegionId) {
				return firstRegionId;
			}
			throw new IllegalArgumentException("region is not incident to portal");
		}
	}

	public record RegionSearchResult(
			List<Integer> regionPath,
			int visitedRegionCount,
			boolean reachedGoal,
			boolean budgetExhausted
	) {
		public RegionSearchResult {
			regionPath = List.copyOf(regionPath);
		}
	}

	private record SearchNode(int regionId, int cost, int heuristic, long sequence) {
		private int estimatedTotalCost() {
			return cost + heuristic;
		}
	}

	private static final class PortalAccumulator {
		private final int firstRegion;
		private final int secondRegion;
		private int count;
		private double centerX;
		private double centerY;
		private double centerZ;

		private PortalAccumulator(int firstRegion, int secondRegion) {
			this.firstRegion = firstRegion;
			this.secondRegion = secondRegion;
		}

		private void add(VoxelDda.Cell first, VoxelDda.Cell second) {
			count++;
			centerX += (first.x() + second.x() + 1.0) * 0.5;
			centerY += (first.y() + second.y() + 1.0) * 0.5;
			centerZ += (first.z() + second.z() + 1.0) * 0.5;
		}

		private Portal build() {
			return new Portal(
					firstRegion,
					secondRegion,
					count,
					centerX / count,
					centerY / count,
					centerZ / count
			);
		}
	}
}
