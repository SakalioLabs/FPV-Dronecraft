package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.propagation.EarlyReflectionClusterSlew;
import com.tenicana.dronecraft.acoustics.propagation.NullEarlyReflectionRenderer;

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

/** Benchmarks executed linear and cubic-Lagrange 12-slot renderers. */
public final class FractionalDelayRendererBenchmarkCli {
	private static final int BLOCK_SIZE = 256;
	private static final int BLOCKS = 5_000;

	private FractionalDelayRendererBenchmarkCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121j-selection-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		Benchmark linear = benchmark(
				NullEarlyReflectionRenderer.Interpolation.LINEAR
		);
		Benchmark lagrange = benchmark(
				NullEarlyReflectionRenderer.Interpolation.LAGRANGE_CUBIC
		);
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-fractional-delay-renderer-benchmark",
				  "source_contract_sha256": "%s",
				  "block_size": %d,
				  "blocks": %d,
				  "active_slots": 12,
				  "linear": %s,
				  "lagrange3": %s,
				  "gates": {
				    "linear_zero_allocation": %s,
				    "lagrange3_zero_allocation": %s,
				    "linear_p99_below_500_ns": %s,
				    "lagrange3_p99_below_500_ns": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "minecraft_client_started": false,
				  "client_level_read": false,
				  "cuda_executed": false,
				  "minecraft_integration_enabled": false,
				  "live_early_renderer_enabled": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				BLOCK_SIZE,
				BLOCKS,
				linear.json(),
				lagrange.json(),
				allZero(linear.allocations()),
				allZero(lagrange.allocations()),
				linear.p99() <= 500.0,
				lagrange.p99() <= 500.0
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-fractional-delay-renderer-benchmark\","
						+ "\"linear_p99\":%.17g,"
						+ "\"lagrange3_p99\":%.17g}%n",
				linear.p99(),
				lagrange.p99()
		);
	}

	private static Benchmark benchmark(
			NullEarlyReflectionRenderer.Interpolation interpolation
	) {
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(
						NullEarlyReflectionRenderer
								.DEFAULT_MAXIMUM_DELAY_SAMPLES,
						interpolation
				);
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew(1.0, 2_400);
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		Input input = new Input();
		for (int cluster = 0; cluster < 6; cluster++) {
			input.arrival[cluster] = 100.25 + cluster * 200;
			input.energy[cluster] = 1.0 / 6.0;
			input.directionX[cluster] = cluster / 2.5 - 1.0;
		}
		slew.update(input, frame);
		renderer.submit(frame);
		double[] dry = new double[BLOCK_SIZE];
		double[] left = new double[BLOCK_SIZE];
		double[] right = new double[BLOCK_SIZE];
		for (int index = 0; index < BLOCK_SIZE; index++) {
			dry[index] = 0.2 * Math.sin(
					2.0 * Math.PI * 5_760.0 * index / 48_000.0
			);
		}
		for (int warmup = 0; warmup < 2_000; warmup++) {
			renderer.render(dry, 0, left, right, 0, BLOCK_SIZE);
		}
		for (int cluster = 0; cluster < 6; cluster++) {
			input.arrival[cluster] += 2_000.5;
		}
		slew.update(input, frame);
		if (frame.slotCount() != 12) {
			throw new IllegalStateException("expected 12 render slots");
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		for (int prime = 0; prime < 2_000; prime++) {
			renderer.submit(frame);
			renderer.render(dry, 0, left, right, 0, BLOCK_SIZE);
		}
		long[] allocations = new long[5];
		for (int window = 0; window < allocations.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int block = 0; block < 2_000; block++) {
				renderer.submit(frame);
				renderer.render(dry, 0, left, right, 0, BLOCK_SIZE);
			}
			allocations[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		double[] elapsed = new double[BLOCKS];
		double checksum = 0.0;
		for (int block = 0; block < BLOCKS; block++) {
			renderer.submit(frame);
			long started = System.nanoTime();
			renderer.render(dry, 0, left, right, 0, BLOCK_SIZE);
			elapsed[block] =
					(System.nanoTime() - started) / (double) BLOCK_SIZE;
			checksum += left[block & 255] + right[(block * 31) & 255];
		}
		double[] sorted = elapsed.clone();
		Arrays.sort(sorted);
		return new Benchmark(
				elapsed,
				percentile(sorted, 0.50),
				percentile(sorted, 0.99),
				allocations,
				checksum
		);
	}

	private static double percentile(double[] sorted, double quantile) {
		return sorted[(int) Math.ceil(quantile * sorted.length) - 1];
	}

	private static boolean allZero(long[] values) {
		return Arrays.stream(values).allMatch(value -> value == 0L);
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

	private static String doubleArray(double[] values) {
		StringBuilder json = new StringBuilder("[");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append(String.format(Locale.ROOT, "%.17g", values[index]));
		}
		return json.append(']').toString();
	}

	private static String longArray(long[] values) {
		StringBuilder json = new StringBuilder("[");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append(values[index]);
		}
		return json.append(']').toString();
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static final class Input
			implements EarlyReflectionClusterSlew.Input {
		private final double[] arrival = new double[6];
		private final double[] energy = new double[6];
		private final double[] directionX = new double[6];

		@Override
		public boolean complete() {
			return true;
		}

		@Override
		public int clusterCount() {
			return 6;
		}

		@Override
		public double clusterArrivalSamples(int index) {
			return arrival[index];
		}

		@Override
		public double clusterLow(int index) {
			return energy[index];
		}

		@Override
		public double clusterMid(int index) {
			return energy[index];
		}

		@Override
		public double clusterHigh(int index) {
			return energy[index];
		}

		@Override
		public double clusterDirectionX(int index) {
			return directionX[index];
		}

		@Override
		public double clusterDirectionY(int index) {
			return 0.0;
		}

		@Override
		public double clusterDirectionZ(int index) {
			return 1.0;
		}
	}

	private record Benchmark(
			double[] elapsed,
			double p50,
			double p99,
			long[] allocations,
			double checksum
	) {
		private String json() {
			return String.format(
					Locale.ROOT,
					"{\"elapsed_ns_per_frame\":%s,"
							+ "\"p50_ns_per_frame\":%.17g,"
							+ "\"p99_ns_per_frame\":%.17g,"
							+ "\"allocation_windows_bytes\":%s,"
							+ "\"checksum\":%.17g}",
					doubleArray(elapsed),
					p50,
					p99,
					longArray(allocations),
					checksum
			);
		}
	}
}
