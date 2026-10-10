package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.BoundedFirstOrderGainSolver;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderBatchSolver;
import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;
import com.tenicana.dronecraft.acoustics.propagation.FirstOrderArrivalClusterer;
import com.tenicana.dronecraft.acoustics.propagation.SpatialCalibrationSupport;
import com.tenicana.dronecraft.acoustics.propagation.SpatialSupportBounds;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/** Emits D120 cross-language reference vectors and a local hot-path gate. */
public final class BoundedFirstOrderGainReferenceCli {
	private static final RoomBounds ROOM =
			new RoomBounds(5.705, 5.965, 2.355);
	private static final SpatialCalibrationSupport SUPPORT =
			new SpatialCalibrationSupport(
					new SpatialSupportBounds(
							1.6, 0.6, 1.1, 1.7, 0.8, 1.2
					),
					new SpatialSupportBounds(
							0.5, 0.5, 0.5, 5.0, 5.0, 2.0
					)
			);
	private static final double[] SOURCE =
			{1.6330509, 0.6820041, 1.16493109};
	private static final double[] LISTENER =
			{3.50149512, 2.61934068, 1.38561103};
	private static final AcousticMaterial[] MATERIALS = {
			AcousticMaterials.STONE,
			AcousticMaterials.SOFT,
			AcousticMaterials.WOOD,
			AcousticMaterials.GLASS,
			AcousticMaterials.METAL,
			AcousticMaterials.FOLIAGE
	};
	private static final double[] LOW_CORRECTION =
			{100.0, -100.0, 2.0, -2.0, 0.5, -0.5};
	private static final double[] MID_CORRECTION =
			{4.0, -8.0, 1.0, -1.0, 0.25, -0.25};
	private static final double[] HIGH_CORRECTION =
			{3.0, -6.0, 0.0, 0.0, -3.0, 3.0};

	private BoundedFirstOrderGainReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D120-contract-json>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		if (!Files.isRegularFile(contract)) {
			throw new IllegalArgumentException(
					"D120 contract does not exist: " + contract
			);
		}
		DdaFirstOrderBatchSolver.Workspace paths =
				new DdaFirstOrderBatchSolver.Workspace();
		BoundedFirstOrderGainSolver.Workspace supported =
				new BoundedFirstOrderGainSolver.Workspace();
		BoundedFirstOrderGainSolver.Workspace unsupported =
				new BoundedFirstOrderGainSolver.Workspace();
		FirstOrderArrivalClusterer.Workspace clusters =
				new FirstOrderArrivalClusterer.Workspace();
		solvePaths(paths);
		BoundedFirstOrderGainSolver.solve(
				paths,
				MATERIALS,
				LOW_CORRECTION,
				MID_CORRECTION,
				HIGH_CORRECTION,
				true,
				supported
		);
		BoundedFirstOrderGainSolver.solve(
				paths,
				MATERIALS,
				LOW_CORRECTION,
				MID_CORRECTION,
				HIGH_CORRECTION,
				false,
				unsupported
		);
		FirstOrderArrivalClusterer.cluster(paths, unsupported, clusters);
		Benchmark benchmark = benchmark(paths);
		String report = report(
				sha256(Files.readAllBytes(contract)),
				paths,
				supported,
				unsupported,
				clusters,
				benchmark
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-bounded-first-order-gain-reference\","
						+ "\"clusters\":%d,\"allocation_bytes_per_solve\":%.17g,"
						+ "\"p99_ns_per_solve\":%.17g}%n",
				clusters.clusterCount(),
				benchmark.allocatedBytesPerSolve(),
				benchmark.p99NanosPerSolve()
		);
	}

	private static Benchmark benchmark(
			DdaFirstOrderBatchSolver.Workspace paths
	) {
		BoundedFirstOrderGainSolver.Workspace gains =
				new BoundedFirstOrderGainSolver.Workspace();
		FirstOrderArrivalClusterer.Workspace clusters =
				new FirstOrderArrivalClusterer.Workspace();
		for (int iteration = 0; iteration < 200_000; iteration++) {
			runGainAndCluster(paths, gains, clusters, iteration % 2 == 0);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		int solves = 100_000;
		long[] allocationWindows = new long[5];
		double checksum = 0.0;
		for (int window = 0; window < allocationWindows.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int iteration = 0; iteration < solves; iteration++) {
				checksum += runGainAndCluster(
						paths,
						gains,
						clusters,
						iteration % 2 == 0
				);
			}
			allocationWindows[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		long[] sortedAllocation = allocationWindows.clone();
		Arrays.sort(sortedAllocation);
		long[] elapsed = new long[400];
		for (int batch = 0; batch < elapsed.length; batch++) {
			long started = System.nanoTime();
			for (int solve = 0; solve < 64; solve++) {
				checksum += runGainAndCluster(
						paths,
						gains,
						clusters,
						solve % 2 == 0
				);
			}
			elapsed[batch] = System.nanoTime() - started;
		}
		Arrays.sort(elapsed);
		if (!Double.isFinite(checksum) || checksum <= 0.0) {
			throw new IllegalStateException("benchmark checksum changed");
		}
		return new Benchmark(
				solves,
				allocationWindows,
				sortedAllocation[sortedAllocation.length / 2]
						/ (double) solves,
				percentile(elapsed, 0.50) / 64.0,
				percentile(elapsed, 0.95) / 64.0,
				percentile(elapsed, 0.99) / 64.0,
				checksum
		);
	}

	private static double runGainAndCluster(
			DdaFirstOrderBatchSolver.Workspace paths,
			BoundedFirstOrderGainSolver.Workspace gains,
			FirstOrderArrivalClusterer.Workspace clusters,
			boolean supported
	) {
		BoundedFirstOrderGainSolver.solve(
				paths,
				MATERIALS,
				LOW_CORRECTION,
				MID_CORRECTION,
				HIGH_CORRECTION,
				supported,
				gains
		);
		FirstOrderArrivalClusterer.cluster(paths, gains, clusters);
		double checksum = clusters.clusterCount();
		for (int path = 0; path < 7; path++) {
			checksum += gains.totalEnergy(path);
		}
		return checksum;
	}

	private static void solvePaths(
			DdaFirstOrderBatchSolver.Workspace paths
	) {
		DdaFirstOrderBatchSolver.solve(
				SOURCE[0], SOURCE[1], SOURCE[2],
				LISTENER[0], LISTENER[1], LISTENER[2],
				ROOM,
				32,
				paths
		);
	}

	private static String report(
			String contractSha256,
			DdaFirstOrderBatchSolver.Workspace paths,
			BoundedFirstOrderGainSolver.Workspace supported,
			BoundedFirstOrderGainSolver.Workspace unsupported,
			FirstOrderArrivalClusterer.Workspace clusters,
			Benchmark benchmark
	) {
		boolean supportDecision = SUPPORT.supports(
				SOURCE[0], SOURCE[1], SOURCE[2],
				LISTENER[0], LISTENER[1], LISTENER[2]
		);
		return String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-bounded-first-order-gain-reference",
				  "source_contract_sha256": "%s",
				  "room_m": [5.705,5.965,2.355],
				  "source_m": %s,
				  "listener_m": %s,
				  "support_decision": %s,
				  "facet_material_ids": ["stone","soft","wood","glass","metal","foliage"],
				  "correction_db": {"low":%s,"mid":%s,"high":%s},
				  "path_length_m": %s,
				  "topology_visible": %s,
				  "supported_gain": {"low":%s,"mid":%s,"high":%s},
				  "unsupported_gain": {"low":%s,"mid":%s,"high":%s},
				  "unsupported_clusters": %s,
				  "benchmark": {
				    "solves_per_allocation_window": %d,
				    "allocation_windows_bytes": %s,
				    "median_allocated_bytes_per_solve": %.17g,
				    "p50_ns_per_solve": %.17g,
				    "p95_ns_per_solve": %.17g,
				    "p99_ns_per_solve": %.17g,
				    "checksum": %.17g
				  },
				  "gates": {
				    "support_decision_true": %s,
				    "zero_allocation_hot_path": %s,
				    "p99_below_20_microseconds": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "cuda_executed": false,
				  "minecraft_integration_enabled": false,
				  "release_calibrated": false,
				  "claim_boundary": "D120 deterministic Java CPU reference and local hot-JVM measurement for bounded gain plus six-arrival clustering. It excludes Minecraft scheduling, rendering, audio capture and CUDA."
				}
				""",
				contractSha256,
				doubles(SOURCE, SOURCE.length),
				doubles(LISTENER, LISTENER.length),
				supportDecision,
				doubles(LOW_CORRECTION, 6),
				doubles(MID_CORRECTION, 6),
				doubles(HIGH_CORRECTION, 6),
				pathLengths(paths),
				booleans(paths),
				gains(supported, 0),
				gains(supported, 1),
				gains(supported, 2),
				gains(unsupported, 0),
				gains(unsupported, 1),
				gains(unsupported, 2),
				clusters(clusters),
				benchmark.solvesPerAllocationWindow(),
				longs(benchmark.allocationWindows()),
				benchmark.allocatedBytesPerSolve(),
				benchmark.p50NanosPerSolve(),
				benchmark.p95NanosPerSolve(),
				benchmark.p99NanosPerSolve(),
				benchmark.checksum(),
				supportDecision,
				benchmark.allocatedBytesPerSolve() == 0.0,
				benchmark.p99NanosPerSolve() <= 20_000.0
		);
	}

	private static String pathLengths(
			DdaFirstOrderBatchSolver.Workspace paths
	) {
		double[] values = new double[7];
		for (int path = 0; path < values.length; path++) {
			values[path] = paths.lengthMeters(path);
		}
		return doubles(values, values.length);
	}

	private static String booleans(
			DdaFirstOrderBatchSolver.Workspace paths
	) {
		StringBuilder result = new StringBuilder("[");
		for (int path = 0; path < 7; path++) {
			if (path > 0) {
				result.append(',');
			}
			result.append(paths.topologyVisible(path));
		}
		return result.append(']').toString();
	}

	private static String gains(
			BoundedFirstOrderGainSolver.Workspace gains,
			int band
	) {
		double[] values = new double[7];
		for (int path = 0; path < values.length; path++) {
			values[path] = switch (band) {
				case 0 -> gains.low(path);
				case 1 -> gains.mid(path);
				case 2 -> gains.high(path);
				default -> throw new IllegalStateException();
			};
		}
		return doubles(values, values.length);
	}

	private static String clusters(
			FirstOrderArrivalClusterer.Workspace clusters
	) {
		StringBuilder result = new StringBuilder("[");
		for (int cluster = 0; cluster < clusters.clusterCount(); cluster++) {
			if (cluster > 0) {
				result.append(',');
			}
			result.append(String.format(
					Locale.ROOT,
					"{\"arrival_samples\":%.17g,\"path_count\":%d,"
							+ "\"low\":%.17g,\"mid\":%.17g,\"high\":%.17g}",
					clusters.arrivalSamples(cluster),
					clusters.pathCount(cluster),
					clusters.low(cluster),
					clusters.mid(cluster),
					clusters.high(cluster)
			));
		}
		return result.append(']').toString();
	}

	private static String doubles(double[] values, int length) {
		StringBuilder result = new StringBuilder("[");
		for (int index = 0; index < length; index++) {
			if (index > 0) {
				result.append(',');
			}
			result.append(String.format(Locale.ROOT, "%.17g", values[index]));
		}
		return result.append(']').toString();
	}

	private static String longs(long[] values) {
		StringBuilder result = new StringBuilder("[");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				result.append(',');
			}
			result.append(values[index]);
		}
		return result.append(']').toString();
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

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private record Benchmark(
			int solvesPerAllocationWindow,
			long[] allocationWindows,
			double allocatedBytesPerSolve,
			double p50NanosPerSolve,
			double p95NanosPerSolve,
			double p99NanosPerSolve,
			double checksum
	) {
	}
}
