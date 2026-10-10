package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.diffraction.VoxelDiffractionEdge;
import com.tenicana.dronecraft.acoustics.diffraction.VoxelDiffractionEdgeExtractor;
import com.tenicana.dronecraft.acoustics.path.AirPortalGraph;
import com.tenicana.dronecraft.acoustics.path.HierarchicalAirPathfinder;
import com.tenicana.dronecraft.acoustics.path.SparseAirGrid;
import com.tenicana.dronecraft.acoustics.path.VoxelPathMetrics;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Product baseline combining five direct probes with an optional bounded
 * air-cell detour. The empirical diffraction model is only used when the
 * direct mid-band path is substantially occluded.
 */
public final class HybridPropagationSolver {
	private static final double DIFFRACTION_TRIGGER_MID_GAIN = 0.92;
	private static final int PORTAL_PARTITION_SIZE = 8;
	private static final int MAX_VISITED_REGIONS = 1_024;

	private HybridPropagationSolver() {
	}

	public static Result solve(
			List<MultiPathSolver.Probe> probes,
			AcousticVector listener,
			DirectPathSolver.MaterialQuery materials,
			int maxCellsPerDirectPath,
			Optional<SparseAirGrid> airGrid,
			VoxelDda.Cell sourceCell,
			VoxelDda.Cell listenerCell,
			int maxAStarVisitedNodes,
			EmpiricalDiffraction.Parameters diffractionParameters
	) {
		return solve(
				probes,
				listener,
				materials,
				maxCellsPerDirectPath,
				airGrid,
				Optional.empty(),
				sourceCell,
				listenerCell,
				maxAStarVisitedNodes,
				diffractionParameters
		);
	}

	public static Result solve(
			List<MultiPathSolver.Probe> probes,
			AcousticVector listener,
			DirectPathSolver.MaterialQuery materials,
			int maxCellsPerDirectPath,
			Optional<SparseAirGrid> airGrid,
			Optional<AirPortalGraph> portalGraph,
			VoxelDda.Cell sourceCell,
			VoxelDda.Cell listenerCell,
			int maxAStarVisitedNodes,
			EmpiricalDiffraction.Parameters diffractionParameters
	) {
		Objects.requireNonNull(airGrid, "airGrid");
		Objects.requireNonNull(portalGraph, "portalGraph");
		Objects.requireNonNull(sourceCell, "sourceCell");
		Objects.requireNonNull(listenerCell, "listenerCell");
		MultiPathSolver.Result direct = MultiPathSolver.solve(
				probes,
				listener,
				materials,
				maxCellsPerDirectPath
		);
		if (!direct.complete()
				|| direct.transmissionEnergyGain().mid() >= DIFFRACTION_TRIGGER_MID_GAIN
				|| airGrid.isEmpty()
				|| !airGrid.get().complete()) {
			return Result.directOnly(direct);
		}

		HierarchicalAirPathfinder.Result search = portalGraph.isPresent()
				? HierarchicalAirPathfinder.search(
						sourceCell,
						listenerCell,
						airGrid.get(),
						portalGraph.get(),
						MAX_VISITED_REGIONS,
						maxAStarVisitedNodes
				)
				: HierarchicalAirPathfinder.search(
						sourceCell,
						listenerCell,
						airGrid.get(),
						PORTAL_PARTITION_SIZE,
						MAX_VISITED_REGIONS,
						maxAStarVisitedNodes
				);
		if (!search.reachedGoal()) {
			return new Result(
					direct.transmissionEnergyGain(),
					direct,
					false,
					Optional.empty(),
					search.visitedCellCount(),
					search.cellBudgetExhausted(),
					search.regionCount(),
					search.portalCount(),
					search.usedCellFallback(),
					List.of()
			);
		}

		VoxelPathMetrics metrics = VoxelPathMetrics.measure(
				probes.getFirst().position(),
				listener,
				search.path()
		);
		EmpiricalDiffraction.Result diffraction = EmpiricalDiffraction.evaluate(
				direct.transmissionEnergyGain(),
				metrics,
				diffractionParameters
		);
		List<VoxelDiffractionEdge> diffractionEdges =
				VoxelDiffractionEdgeExtractor.extract(search.path(), airGrid.get(), 3);
		return new Result(
				diffraction.combinedEnergyGain(),
				direct,
				true,
				Optional.of(metrics),
				search.visitedCellCount(),
				false,
				search.regionCount(),
				search.portalCount(),
				search.usedCellFallback(),
				diffractionEdges
		);
	}

	public record Result(
			AcousticBands finalEnergyGain,
			MultiPathSolver.Result direct,
			boolean diffractionApplied,
			Optional<VoxelPathMetrics> diffractionPath,
			int aStarVisitedNodeCount,
			boolean aStarBudgetExhausted,
			int portalRegionCount,
			int portalCount,
			boolean usedCellFallback,
			List<VoxelDiffractionEdge> diffractionEdges
	) {
		public Result {
			Objects.requireNonNull(finalEnergyGain, "finalEnergyGain");
			Objects.requireNonNull(direct, "direct");
			Objects.requireNonNull(diffractionPath, "diffractionPath");
			diffractionEdges = List.copyOf(diffractionEdges);
		}

		private static Result directOnly(MultiPathSolver.Result direct) {
			return new Result(
					direct.transmissionEnergyGain(),
					direct,
					false,
					Optional.empty(),
					0,
					false,
					0,
					0,
					false,
					List.of()
			);
		}
	}
}
