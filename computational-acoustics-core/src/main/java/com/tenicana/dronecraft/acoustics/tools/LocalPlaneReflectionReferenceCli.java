package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.AxisAlignedPlanePatch;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneReflectionSolver;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneCacheKey;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Emits D121 continuous local-plane and two-leg visibility evidence. */
public final class LocalPlaneReflectionReferenceCli {
	private static final LocalPlaneReflectionSolver.CellBlockQuery OPEN =
			(x, y, z) -> false;
	private static final LocalPlaneReflectionSolver.CellBlockQuery BLOCKED =
			(x, y, z) -> x == -1 && y == 0 && z == 0;
	private static final AxisAlignedPlanePatch SLAB_TOP =
			new AxisAlignedPlanePatch(
					1, 1, 0.5,
					0.0, 1.0,
					0.0, 1.0,
					AcousticMaterials.STONE
			);
	private static final AxisAlignedPlanePatch WALL =
			new AxisAlignedPlanePatch(
					0, -1, 0.0,
					0.0, 2.0,
					0.0, 1.0,
					AcousticMaterials.WOOD
			);
	private static final Scenario[] SCENARIOS = {
			new Scenario(
					"subvoxel-slab-visible",
					new double[] {0.2, 1.5, 0.4},
					new double[] {0.8, 1.5, 0.6},
					SLAB_TOP, OPEN, 32, "[]"
			),
			new Scenario(
					"wall-outgoing-blocked",
					new double[] {-1.5, 0.5, 0.5},
					new double[] {-1.5, 1.5, 0.5},
					WALL, BLOCKED, 32, "[[-1,0,0]]"
			),
			new Scenario(
					"finite-patch-miss",
					new double[] {2.0, 1.5, 0.5},
					new double[] {3.0, 1.5, 0.5},
					SLAB_TOP, OPEN, 32, "[]"
			),
			new Scenario(
					"cell-budget-incomplete",
					new double[] {0.2, 20.0, 0.4},
					new double[] {0.8, 20.0, 0.6},
					SLAB_TOP, OPEN, 2, "[]"
			)
	};

	private LocalPlaneReflectionReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121-local-plane-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		LocalPlaneReflectionSolver.Workspace workspace =
				new LocalPlaneReflectionSolver.Workspace();
		StringBuilder scenarios = new StringBuilder("[");
		for (int index = 0; index < SCENARIOS.length; index++) {
			if (index > 0) {
				scenarios.append(',');
			}
			Scenario scenario = SCENARIOS[index];
			solve(scenario, workspace);
			scenarios.append(scenarioJson(scenario, workspace));
		}
		scenarios.append(']');
		Benchmark benchmark = benchmark(workspace);
		String unionFixtures = unionFixturesJson();
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-local-plane-reflection-reference",
				  "source_contract_sha256": "%s",
				  "scenarios": %s,
				  "union_fixtures": %s,
				  "cache_invalidation": %s,
				  "benchmark": {
				    "solves_per_window": %d,
				    "allocation_windows_bytes": %s,
				    "median_allocated_bytes_per_solve": %.17g,
				    "p99_ns_per_solve": %.17g,
				    "checksum": %.17g
				  },
				  "gates": {
				    "four_scenarios": true,
				    "zero_allocation_hot_path": %s,
				    "p99_below_10_microseconds": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "cuda_executed": false,
				  "minecraft_integration_enabled": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				scenarios,
				unionFixtures,
				cacheInvalidationJson(),
				benchmark.solvesPerWindow(),
				longArray(benchmark.allocationWindows()),
				benchmark.allocatedBytesPerSolve(),
				benchmark.p99NanosPerSolve(),
				benchmark.checksum(),
				benchmark.allocatedBytesPerSolve() == 0.0,
				benchmark.p99NanosPerSolve() <= 10_000.0
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-local-plane-reflection-reference\","
						+ "\"allocation_bytes\":%.17g,\"p99_ns\":%.17g}%n",
				benchmark.allocatedBytesPerSolve(),
				benchmark.p99NanosPerSolve()
		);
	}

	private static String cacheInvalidationJson() {
		LocalPlaneSceneCacheKey cache = new LocalPlaneSceneCacheKey();
		boolean initial = cache.matches(7, 1, 2, 3, 4, 5, 6);
		cache.update(7, 1, 2, 3, 4, 5, 6);
		boolean exact = cache.matches(7, 1, 2, 3, 4, 5, 6);
		boolean sourceMoved =
				cache.matches(7, 1.000001, 2, 3, 4, 5, 6);
		boolean listenerMoved =
				cache.matches(7, 1, 2, 3, 4, 5.000001, 6);
		boolean generationChanged =
				cache.matches(8, 1, 2, 3, 4, 5, 6);
		cache.invalidate();
		boolean invalidated = cache.matches(7, 1, 2, 3, 4, 5, 6);
		return String.format(
				Locale.ROOT,
				"{\"initial_match\":%s,\"exact_match\":%s,"
						+ "\"source_moved_match\":%s,"
						+ "\"listener_moved_match\":%s,"
						+ "\"generation_changed_match\":%s,"
						+ "\"explicitly_invalidated_match\":%s}",
				initial,
				exact,
				sourceMoved,
				listenerMoved,
				generationChanged,
				invalidated
		);
	}

	private static String unionFixturesJson() {
		List<MaterialBox> fullCube = List.of(
				box(0, 0, 0, 1, 1, 1)
		);
		List<MaterialBox> adjacent = List.of(
				box(0, 0, 0, 0.5, 1, 1),
				box(0.5, 0, 0, 1, 1, 1)
		);
		List<MaterialBox> stair = List.of(
				box(0, 0, 0, 1, 0.5, 1),
				box(0, 0.5, 0, 0.5, 1, 1)
		);
		return "["
				+ unionFixtureJson("full-cube", fullCube) + ","
				+ unionFixtureJson("adjacent-boxes", adjacent) + ","
				+ unionFixtureJson("stair", stair)
				+ "]";
	}

	private static String unionFixtureJson(
			String name,
			List<MaterialBox> boxes
	) {
		List<AxisAlignedPlanePatch> patches =
				MaterialBoxUnionSurfaceExtractor.extract(boxes);
		StringBuilder boxJson = new StringBuilder("[");
		for (int index = 0; index < boxes.size(); index++) {
			if (index > 0) {
				boxJson.append(',');
			}
			MaterialBox box = boxes.get(index);
			boxJson.append(String.format(
					Locale.ROOT,
					"[%.17g,%.17g,%.17g,%.17g,%.17g,%.17g]",
					box.minimumX(), box.minimumY(), box.minimumZ(),
					box.maximumX(), box.maximumY(), box.maximumZ()
			));
		}
		boxJson.append(']');
		StringBuilder patchJson = new StringBuilder("[");
		for (int index = 0; index < patches.size(); index++) {
			if (index > 0) {
				patchJson.append(',');
			}
			AxisAlignedPlanePatch patch = patches.get(index);
			patchJson.append(String.format(
					Locale.ROOT,
					"[%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g]",
					patch.axis(),
					patch.normalSign(),
					patch.coordinateMeters(),
					patch.minimumFirstMeters(),
					patch.maximumFirstMeters(),
					patch.minimumSecondMeters(),
					patch.maximumSecondMeters()
			));
		}
		patchJson.append(']');
		return String.format(
				Locale.ROOT,
				"{\"name\":\"%s\",\"boxes\":%s,\"patch_count\":%d,"
						+ "\"patches\":%s}",
				name,
				boxJson,
				patches.size(),
				patchJson
		);
	}

	private static MaterialBox box(
			double minimumX,
			double minimumY,
			double minimumZ,
			double maximumX,
			double maximumY,
			double maximumZ
	) {
		return new MaterialBox(
				minimumX,
				minimumY,
				minimumZ,
				maximumX,
				maximumY,
				maximumZ,
				AcousticMaterials.STONE
		);
	}

	private static Benchmark benchmark(
			LocalPlaneReflectionSolver.Workspace workspace
	) {
		Scenario scenario = SCENARIOS[0];
		for (int iteration = 0; iteration < 300_000; iteration++) {
			solve(scenario, workspace);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		int solves = 100_000;
		long[] allocation = new long[5];
		double checksum = 0.0;
		for (int window = 0; window < allocation.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int iteration = 0; iteration < solves; iteration++) {
				solve(scenario, workspace);
				checksum += workspace.pathLengthMeters()
						+ workspace.incidenceCosine()
						+ workspace.visitedCells();
			}
			allocation[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		long[] elapsed = new long[400];
		double latencyChecksum = 0.0;
		for (int batch = 0; batch < elapsed.length; batch++) {
			long started = System.nanoTime();
			for (int iteration = 0; iteration < 128; iteration++) {
				solve(scenario, workspace);
				latencyChecksum += workspace.reflectionX()
						+ workspace.pathLengthMeters();
			}
			elapsed[batch] = System.nanoTime() - started;
		}
		checksum += latencyChecksum;
		Arrays.sort(elapsed);
		long[] sortedAllocation = allocation.clone();
		Arrays.sort(sortedAllocation);
		if (!Double.isFinite(checksum) || checksum <= 0.0) {
			throw new IllegalStateException("benchmark checksum changed");
		}
		return new Benchmark(
				solves,
				allocation,
				sortedAllocation[2] / (double) solves,
				percentile(elapsed, 0.99) / 128.0,
				checksum
		);
	}

	private static void solve(
			Scenario scenario,
			LocalPlaneReflectionSolver.Workspace workspace
	) {
		LocalPlaneReflectionSolver.solve(
				scenario.source()[0],
				scenario.source()[1],
				scenario.source()[2],
				scenario.listener()[0],
				scenario.listener()[1],
				scenario.listener()[2],
				scenario.patch(),
				scenario.blockers(),
				scenario.maximumCells(),
				workspace
		);
	}

	private static String scenarioJson(
			Scenario scenario,
			LocalPlaneReflectionSolver.Workspace result
	) {
		AxisAlignedPlanePatch patch = scenario.patch();
		return String.format(
				Locale.ROOT,
				"{\"name\":\"%s\",\"source_m\":%s,\"listener_m\":%s,"
						+ "\"patch\":{\"axis\":%d,\"normal_sign\":%d,"
						+ "\"coordinate_m\":%.17g,\"minimum_first_m\":%.17g,"
						+ "\"maximum_first_m\":%.17g,"
						+ "\"minimum_second_m\":%.17g,"
						+ "\"maximum_second_m\":%.17g},"
						+ "\"blocked_cells\":%s,\"maximum_cells\":%d,"
						+ "\"result\":{\"candidate_geometry\":%s,"
						+ "\"topology_visible\":%s,\"complete\":%s,"
						+ "\"reflection_m\":[%s,%s,%s],"
						+ "\"path_length_m\":%s,\"incidence_cosine\":%s,"
						+ "\"visited_cells\":%d}}",
				scenario.name(),
				vector(scenario.source()),
				vector(scenario.listener()),
				patch.axis(),
				patch.normalSign(),
				patch.coordinateMeters(),
				patch.minimumFirstMeters(),
				patch.maximumFirstMeters(),
				patch.minimumSecondMeters(),
				patch.maximumSecondMeters(),
				scenario.blockedCellsJson(),
				scenario.maximumCells(),
				result.candidateGeometry(),
				result.topologyVisible(),
				result.complete(),
				number(result.reflectionX()),
				number(result.reflectionY()),
				number(result.reflectionZ()),
				number(result.pathLengthMeters()),
				number(result.incidenceCosine()),
				result.visitedCells()
		);
	}

	private static String vector(double[] values) {
		return String.format(
				Locale.ROOT,
				"[%.17g,%.17g,%.17g]",
				values[0], values[1], values[2]
		);
	}

	private static String number(double value) {
		return Double.isFinite(value)
				? String.format(Locale.ROOT, "%.17g", value)
				: "null";
	}

	private static String longArray(long[] values) {
		return String.format(
				Locale.ROOT,
				"[%d,%d,%d,%d,%d]",
				values[0], values[1], values[2], values[3], values[4]
		);
	}

	private static ThreadMXBean allocationBean() {
		java.lang.management.ThreadMXBean base =
				ManagementFactory.getThreadMXBean();
		if (!(base instanceof ThreadMXBean bean)
				|| !bean.isThreadAllocatedMemorySupported()) {
			throw new IllegalStateException("allocation counter unavailable");
		}
		if (!bean.isThreadAllocatedMemoryEnabled()) {
			bean.setThreadAllocatedMemoryEnabled(true);
		}
		return bean;
	}

	private static long percentile(long[] sorted, double quantile) {
		int index = (int) Math.ceil(quantile * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private record Scenario(
			String name,
			double[] source,
			double[] listener,
			AxisAlignedPlanePatch patch,
			LocalPlaneReflectionSolver.CellBlockQuery blockers,
			int maximumCells,
			String blockedCellsJson
	) {
	}

	private record Benchmark(
			int solvesPerWindow,
			long[] allocationWindows,
			double allocatedBytesPerSolve,
			double p99NanosPerSolve,
			double checksum
	) {
	}
}
