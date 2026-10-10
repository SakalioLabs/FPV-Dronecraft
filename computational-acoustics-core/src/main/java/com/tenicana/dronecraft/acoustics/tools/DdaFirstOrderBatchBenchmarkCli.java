package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderBatchSolver;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderBatchSolver.Workspace;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.Facet;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.Path;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/**
 * D117 allocation and parity gate for the reusable seven-path CPU DDA batch.
 */
public final class DdaFirstOrderBatchBenchmarkCli {
	private static final RoomBounds BOUNDS =
			new RoomBounds(5.705, 5.965, 2.355);
	private static final int MAXIMUM_CELLS = 32;
	private static final double[][] PAIRS = {
			{
					1.6330509, 0.6820041, 1.16493109,
					2.21190695, 1.71556362, 1.30742631
			},
			{
					1.6330509, 0.6820041, 1.16493109,
					3.50149512, 2.61934068, 1.38561103
			},
			{
					1.6330509, 0.6820041, 1.16493109,
					3.72758061, 4.02235716, 0.95058622
			},
			{
					3.651, 1.004, 1.38,
					2.44071254, 1.6029892, 1.30742631
			},
			{
					3.651, 1.004, 1.38,
					2.87343067, 3.56761246, 1.49048013
			},
			{
					3.651, 1.004, 1.38,
					0.80316092, 3.83141445, 1.04391528
			}
	};

	private DdaFirstOrderBatchBenchmarkCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D117-report-json>"
			);
		}
		java.nio.file.Path output = java.nio.file.Path.of(args[0]);
		java.nio.file.Path source = java.nio.file.Path.of(args[1]);
		if (!Files.isRegularFile(source)) {
			throw new IllegalArgumentException(
					"D117 report does not exist: " + source
			);
		}
		Parity parity = parity();
		Benchmark benchmark = benchmark();
		String report = report(
				parity,
				benchmark,
				sha256(Files.readAllBytes(source))
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-dda-first-order-batch-benchmark\","
						+ "\"allocation_bytes_per_scenario\":%.17g,"
						+ "\"p99_ns_per_scenario\":%.17g,"
						+ "\"parity_paths\":%d}%n",
				benchmark.allocatedBytesPerScenario(),
				benchmark.p99NanosPerScenario(),
				parity.paths()
		);
	}

	private static Parity parity() {
		Workspace workspace = new Workspace();
		double maximumLengthError = 0.0;
		double maximumReflectionPointError = 0.0;
		int cellMismatches = 0;
		int visibilityMismatches = 0;
		int paths = 0;
		for (double[] pair : PAIRS) {
			DdaFirstOrderBatchSolver.solve(
					pair[0],
					pair[1],
					pair[2],
					pair[3],
					pair[4],
					pair[5],
					BOUNDS,
					MAXIMUM_CELLS,
					workspace
			);
			AcousticVector source =
					new AcousticVector(pair[0], pair[1], pair[2]);
			AcousticVector listener =
					new AcousticVector(pair[3], pair[4], pair[5]);
			Path direct = DdaFirstOrderPathSolver.direct(
					source,
					listener,
					BOUNDS,
					MAXIMUM_CELLS
			);
			maximumLengthError = Math.max(
					maximumLengthError,
					Math.abs(
							direct.lengthMeters()
									- workspace.lengthMeters(0)
					)
			);
			cellMismatches += direct.visitedCells()
					== workspace.visitedCells(0) ? 0 : 1;
			visibilityMismatches += direct.topologyVisible()
					== workspace.topologyVisible(0) ? 0 : 1;
			paths++;
			for (Facet facet : Facet.values()) {
				Path path = DdaFirstOrderPathSolver.reflected(
						source,
						listener,
						BOUNDS,
						facet,
						MAXIMUM_CELLS
				);
				int index = facet.ordinal() + 1;
				maximumLengthError = Math.max(
						maximumLengthError,
						Math.abs(
								path.lengthMeters()
										- workspace.lengthMeters(index)
						)
				);
				maximumReflectionPointError = Math.max(
						maximumReflectionPointError,
						Math.max(
								Math.abs(
										path.reflectionPoint().x()
												- workspace.reflectionX(index)
								),
								Math.max(
										Math.abs(
												path.reflectionPoint().y()
														- workspace.reflectionY(index)
										),
										Math.abs(
												path.reflectionPoint().z()
														- workspace.reflectionZ(index)
										)
								)
						)
				);
				cellMismatches += path.visitedCells()
						== workspace.visitedCells(index) ? 0 : 1;
				visibilityMismatches += path.topologyVisible()
						== workspace.topologyVisible(index) ? 0 : 1;
				paths++;
			}
		}
		return new Parity(
				paths,
				maximumLengthError,
				maximumReflectionPointError,
				cellMismatches,
				visibilityMismatches
		);
	}

	private static Benchmark benchmark() {
		Workspace workspace = new Workspace();
		for (int iteration = 0; iteration < 1_000_000; iteration++) {
			runScenario(PAIRS[iteration % PAIRS.length], workspace);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		int allocationScenarios = 250_000;
		long[] allocationWindows = new long[5];
		double checksum = 0.0;
		for (int window = 0; window < allocationWindows.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int iteration = 0; iteration < allocationScenarios; iteration++) {
				checksum += runScenario(
						PAIRS[iteration % PAIRS.length],
						workspace
				);
			}
			allocationWindows[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		long[] sortedAllocation = allocationWindows.clone();
		Arrays.sort(sortedAllocation);

		long[] elapsed = new long[500];
		for (int batch = 0; batch < elapsed.length; batch++) {
			long started = System.nanoTime();
			for (int scenario = 0; scenario < 66; scenario++) {
				checksum += runScenario(
						PAIRS[scenario % PAIRS.length],
						workspace
				);
			}
			elapsed[batch] = System.nanoTime() - started;
		}
		Arrays.sort(elapsed);
		if (!Double.isFinite(checksum) || checksum <= 0.0) {
			throw new IllegalStateException("benchmark checksum changed");
		}
		return new Benchmark(
				allocationScenarios,
				allocationWindows,
				sortedAllocation[0],
				sortedAllocation[sortedAllocation.length / 2],
				sortedAllocation[sortedAllocation.length - 1],
				sortedAllocation[0] / (double) allocationScenarios,
				elapsed.length,
				percentile(elapsed, 0.50) / 66.0,
				percentile(elapsed, 0.95) / 66.0,
				percentile(elapsed, 0.99) / 66.0,
				checksum
		);
	}

	private static double runScenario(
			double[] pair,
			Workspace workspace
	) {
		DdaFirstOrderBatchSolver.solve(
				pair[0],
				pair[1],
				pair[2],
				pair[3],
				pair[4],
				pair[5],
				BOUNDS,
				MAXIMUM_CELLS,
				workspace
		);
		double checksum = 0.0;
		for (int path = 0; path < DdaFirstOrderBatchSolver.PATH_COUNT; path++) {
			checksum += workspace.lengthMeters(path)
					+ workspace.visitedCells(path) * 1.0e-6
					+ (workspace.topologyVisible(path) ? 1.0e-9 : 0.0);
		}
		return checksum;
	}

	private static ThreadMXBean allocationBean() {
		java.lang.management.ThreadMXBean base =
				ManagementFactory.getThreadMXBean();
		if (!(base instanceof ThreadMXBean bean)
				|| !bean.isThreadAllocatedMemorySupported()) {
			throw new IllegalStateException(
					"thread allocation counter unavailable"
			);
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

	private static String report(
			Parity parity,
			Benchmark benchmark,
			String sourceSha256
	) {
		return String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-dda-first-order-batch-benchmark",
				  "source_d117_report_sha256": "%s",
				  "parity": {
				    "pairs": 6,
				    "paths": %d,
				    "maximum_length_error_m": %.17g,
				    "maximum_reflection_point_error_m": %.17g,
				    "visited_cell_mismatches": %d,
				    "visibility_mismatches": %d
				  },
				  "benchmark": {
				    "paths_per_scenario": 7,
				    "allocation_scenarios_per_window": %d,
				    "allocation_windows_bytes": %s,
				    "minimum_allocated_bytes": %d,
				    "median_allocated_bytes": %d,
				    "maximum_allocated_bytes": %d,
				    "minimum_allocated_bytes_per_scenario": %.17g,
				    "latency_batches": %d,
				    "scenarios_per_latency_batch": 66,
				    "p50_ns_per_scenario": %.17g,
				    "p95_ns_per_scenario": %.17g,
				    "p99_ns_per_scenario": %.17g,
				    "checksum": %.17g
				  },
				  "gates": {
				    "object_reference_parity": %s,
				    "zero_allocation_hot_path": %s,
				    "p99_below_50_microseconds": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "cuda_executed": false,
				  "release_calibrated": false,
				  "claim_boundary": "Local hot-JVM benchmark of a reusable primitive workspace for seven Java CPU DDA paths. It excludes Minecraft snapshot construction, worker handoff, GC pauses outside the measured interval, audio rendering and CUDA."
				}
				""",
				sourceSha256,
				parity.paths(),
				parity.maximumLengthErrorMeters(),
				parity.maximumReflectionPointErrorMeters(),
				parity.cellMismatches(),
				parity.visibilityMismatches(),
				benchmark.allocationScenarios(),
				longArray(benchmark.allocationWindows()),
				benchmark.minimumAllocatedBytes(),
				benchmark.medianAllocatedBytes(),
				benchmark.maximumAllocatedBytes(),
				benchmark.allocatedBytesPerScenario(),
				benchmark.latencyBatches(),
				benchmark.p50NanosPerScenario(),
				benchmark.p95NanosPerScenario(),
				benchmark.p99NanosPerScenario(),
				benchmark.checksum(),
				parity.cellMismatches() == 0
						&& parity.visibilityMismatches() == 0
						&& parity.maximumLengthErrorMeters() <= 1.0e-12
						&& parity.maximumReflectionPointErrorMeters() <= 1.0e-12,
				benchmark.medianAllocatedBytes() == 0L,
				benchmark.p99NanosPerScenario() <= 50_000.0
		);
	}

	private static String longArray(long[] values) {
		StringBuilder result = new StringBuilder("[");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				result.append(',');
			}
			result.append(values[index]);
		}
		return result.append(']').toString();
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

	private record Parity(
			int paths,
			double maximumLengthErrorMeters,
			double maximumReflectionPointErrorMeters,
			int cellMismatches,
			int visibilityMismatches
	) {
	}

	private record Benchmark(
			int allocationScenarios,
			long[] allocationWindows,
			long minimumAllocatedBytes,
			long medianAllocatedBytes,
			long maximumAllocatedBytes,
			double allocatedBytesPerScenario,
			int latencyBatches,
			double p50NanosPerScenario,
			double p95NanosPerScenario,
			double p99NanosPerScenario,
			double checksum
	) {
	}
}
