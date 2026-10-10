package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Uses a partition-local region/portal graph to constrain cell A*, while
 * retaining unrestricted cell A* as the correctness fallback.
 */
public final class HierarchicalAirPathfinder {
	private HierarchicalAirPathfinder() {
	}

	public static Result search(
			VoxelDda.Cell start,
			VoxelDda.Cell goal,
			SparseAirGrid grid,
			int partitionSize,
			int maxVisitedRegions,
			int maxVisitedCells
	) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(goal, "goal");
		Objects.requireNonNull(grid, "grid");
		AirPortalGraph graph = AirPortalGraph.build(grid, partitionSize);
		return search(start, goal, grid, graph, maxVisitedRegions, maxVisitedCells);
	}

	public static Result search(
			VoxelDda.Cell start,
			VoxelDda.Cell goal,
			SparseAirGrid grid,
			AirPortalGraph graph,
			int maxVisitedRegions,
			int maxVisitedCells
	) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(goal, "goal");
		Objects.requireNonNull(grid, "grid");
		Objects.requireNonNull(graph, "graph");
		AirPortalGraph.RegionSearchResult regionSearch =
				graph.searchRegions(start, goal, maxVisitedRegions);

		VoxelAStar.SearchResult cellSearch = null;
		if (regionSearch.reachedGoal()) {
			Set<Integer> corridor = new HashSet<>(regionSearch.regionPath());
			cellSearch = VoxelAStar.search(
					start,
					goal,
					grid,
					maxVisitedCells,
					cell -> graph.cellBelongsTo(cell, corridor)
			);
		}
		boolean usedFallback = cellSearch == null || !cellSearch.reachedGoal();
		if (usedFallback) {
			cellSearch = VoxelAStar.search(start, goal, grid, maxVisitedCells);
		}
		return new Result(
				cellSearch.path(),
				cellSearch.visitedNodeCount(),
				cellSearch.reachedGoal(),
				cellSearch.budgetExhausted(),
				graph.regions().size(),
				graph.portals().size(),
				regionSearch.visitedRegionCount(),
				usedFallback
		);
	}

	public record Result(
			List<VoxelDda.Cell> path,
			int visitedCellCount,
			boolean reachedGoal,
			boolean cellBudgetExhausted,
			int regionCount,
			int portalCount,
			int visitedRegionCount,
			boolean usedCellFallback
	) {
		public Result {
			path = List.copyOf(path);
		}
	}
}
