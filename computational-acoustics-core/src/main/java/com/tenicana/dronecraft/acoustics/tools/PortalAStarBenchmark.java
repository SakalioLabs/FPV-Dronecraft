package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.path.SparseAirGrid;
import com.tenicana.dronecraft.acoustics.path.AirPortalGraphCache;
import com.tenicana.dronecraft.acoustics.path.HierarchicalAirPathfinder;
import com.tenicana.dronecraft.acoustics.path.VoxelAStar;
import com.tenicana.dronecraft.acoustics.path.VoxelBounds;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.Arrays;
import java.util.Locale;

/**
 * Deterministic microbenchmark for the bounded local-air A* prototype.
 *
 * <p>This is an engineering gate rather than a JMH benchmark: it reports
 * per-search percentiles for the exact workload used by the runtime design.</p>
 */
public final class PortalAStarBenchmark {
	private static final int WARMUP_SEARCHES = 200;

	private PortalAStarBenchmark() {
	}

	public static void main(String[] arguments) {
		int measuredSearches = arguments.length == 0 ? 1_000 : Integer.parseInt(arguments[0]);
		if (measuredSearches < 1) {
			throw new IllegalArgumentException("measuredSearches must be positive");
		}

		VoxelDda.Cell start = new VoxelDda.Cell(0, 0, 0);
		VoxelDda.Cell goal = new VoxelDda.Cell(64, 0, 0);
		SparseAirGrid grid = wallWithOffsetOpening();
		for (int index = 0; index < WARMUP_SEARCHES; index++) {
			requireReached(VoxelAStar.search(start, goal, grid, 4_096));
			requireReached(HierarchicalAirPathfinder.search(
					start, goal, grid, 8, 1_024, 4_096
			));
		}

		Metrics cell = measureCell(start, goal, grid, measuredSearches);
		Metrics hierarchical = measureHierarchical(start, goal, grid, measuredSearches);
		Metrics cached = measureCached(
				start, goal, grid, grid, measuredSearches
		);
		Metrics onePartitionChange = measureCached(
				start, goal, grid, wallWithOffsetOpening(true), measuredSearches
		);
		System.out.printf(
				Locale.ROOT,
				"mode=cell searches=%d sampledCells=%d averageVisited=%.1f avg=%.1f us p50=%.1f us p95=%.1f us max=%.1f us%n",
				measuredSearches,
				grid.sampledCellCount(),
				cell.averageVisited(),
				cell.averageMicros(),
				cell.p50Micros(),
				cell.p95Micros(),
				cell.maxMicros()
		);
		System.out.printf(
				Locale.ROOT,
				"mode=hierarchical searches=%d sampledCells=%d averageVisited=%.1f avg=%.1f us p50=%.1f us p95=%.1f us max=%.1f us%n",
				measuredSearches,
				grid.sampledCellCount(),
				hierarchical.averageVisited(),
				hierarchical.averageMicros(),
				hierarchical.p50Micros(),
				hierarchical.p95Micros(),
				hierarchical.maxMicros()
		);
		printMetrics("cached-warm", measuredSearches, grid, cached);
		printMetrics(
				"cached-one-partition-change",
				measuredSearches,
				grid,
				onePartitionChange
		);
	}

	private static void printMetrics(
			String mode,
			int searches,
			SparseAirGrid grid,
			Metrics metrics
	) {
		System.out.printf(
				Locale.ROOT,
				"mode=%s searches=%d sampledCells=%d averageVisited=%.1f avg=%.1f us p50=%.1f us p95=%.1f us max=%.1f us%n",
				mode,
				searches,
				grid.sampledCellCount(),
				metrics.averageVisited(),
				metrics.averageMicros(),
				metrics.p50Micros(),
				metrics.p95Micros(),
				metrics.maxMicros()
		);
	}

	private static Metrics measureCell(
			VoxelDda.Cell start,
			VoxelDda.Cell goal,
			SparseAirGrid grid,
			int searches
	) {
		long[] elapsedNanos = new long[searches];
		int visitedTotal = 0;
		for (int index = 0; index < searches; index++) {
			long started = System.nanoTime();
			VoxelAStar.SearchResult result = VoxelAStar.search(start, goal, grid, 4_096);
			elapsedNanos[index] = System.nanoTime() - started;
			requireReached(result);
			visitedTotal += result.visitedNodeCount();
		}
		return metrics(elapsedNanos, visitedTotal);
	}

	private static Metrics measureHierarchical(
			VoxelDda.Cell start,
			VoxelDda.Cell goal,
			SparseAirGrid grid,
			int searches
	) {
		long[] elapsedNanos = new long[searches];
		int visitedTotal = 0;
		for (int index = 0; index < searches; index++) {
			long started = System.nanoTime();
			HierarchicalAirPathfinder.Result result = HierarchicalAirPathfinder.search(
					start, goal, grid, 8, 1_024, 4_096
			);
			elapsedNanos[index] = System.nanoTime() - started;
			requireReached(result);
			visitedTotal += result.visitedCellCount();
		}
		return metrics(elapsedNanos, visitedTotal);
	}

	private static Metrics measureCached(
			VoxelDda.Cell start,
			VoxelDda.Cell goal,
			SparseAirGrid firstGrid,
			SparseAirGrid secondGrid,
			int searches
	) {
		AirPortalGraphCache cache = new AirPortalGraphCache(8);
		cache.update(firstGrid);
		long[] elapsedNanos = new long[searches];
		int visitedTotal = 0;
		for (int index = 0; index < searches; index++) {
			SparseAirGrid grid = index % 2 == 0 ? firstGrid : secondGrid;
			long started = System.nanoTime();
			AirPortalGraphCache.UpdateResult update = cache.update(grid);
			HierarchicalAirPathfinder.Result result = HierarchicalAirPathfinder.search(
					start, goal, grid, update.graph(), 1_024, 4_096
			);
			elapsedNanos[index] = System.nanoTime() - started;
			requireReached(result);
			visitedTotal += result.visitedCellCount();
		}
		return metrics(elapsedNanos, visitedTotal);
	}

	private static Metrics metrics(long[] elapsedNanos, int visitedTotal) {
		Arrays.sort(elapsedNanos);
		return new Metrics(
				(double) visitedTotal / elapsedNanos.length,
				Arrays.stream(elapsedNanos).average().orElseThrow() / 1_000.0,
				percentile(elapsedNanos, 0.50) / 1_000.0,
				percentile(elapsedNanos, 0.95) / 1_000.0,
				elapsedNanos[elapsedNanos.length - 1] / 1_000.0
		);
	}

	private static SparseAirGrid wallWithOffsetOpening() {
		return wallWithOffsetOpening(false);
	}

	private static SparseAirGrid wallWithOffsetOpening(boolean extraBlock) {
		VoxelBounds bounds = new VoxelBounds(0, -3, -3, 64, 3, 3);
		SparseAirGrid.Builder builder = SparseAirGrid.builder(bounds);
		for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
			for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
				for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
					boolean wall = x == 32 && z != 3;
					wall |= extraBlock && x == 4 && y == 3 && z == 3;
					builder.sample(x, y, z, !wall);
				}
			}
		}
		return builder.build();
	}

	private static long percentile(long[] sorted, double quantile) {
		int index = (int) Math.ceil(quantile * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
	}

	private static void requireReached(VoxelAStar.SearchResult result) {
		if (!result.reachedGoal()) {
			throw new IllegalStateException("benchmark path did not reach goal");
		}
	}

	private static void requireReached(HierarchicalAirPathfinder.Result result) {
		if (!result.reachedGoal()) {
			throw new IllegalStateException("hierarchical benchmark path did not reach goal");
		}
	}

	private record Metrics(
			double averageVisited,
			double averageMicros,
			double p50Micros,
			double p95Micros,
			double maxMicros
	) {
	}
}
