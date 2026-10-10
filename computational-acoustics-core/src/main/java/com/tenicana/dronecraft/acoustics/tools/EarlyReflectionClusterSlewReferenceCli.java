package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.propagation.EarlyReflectionClusterSlew;

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

/** Emits D121h fixed-capacity early-cluster slew evidence. */
public final class EarlyReflectionClusterSlewReferenceCli {
	private static final int ITERATIONS = 100_000;

	private EarlyReflectionClusterSlewReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121h-slew-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		Fixture fixture = fixture();
		Benchmark benchmark = benchmark();
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-early-reflection-cluster-slew-reference",
				  "source_contract_sha256": "%s",
				  "fixture": %s,
				  "benchmark": {
				    "iterations": %d,
				    "elapsed_ns": %s,
				    "p50_ns": %.17g,
				    "p99_ns": %.17g,
				    "allocation_windows_bytes": %s,
				    "checksum": %.17g
				  },
				  "gates": {
				    "fade_in": %s,
				    "nearby_same_slot": %s,
				    "topology_crossfade": %s,
				    "incomplete_fade_and_retire": %s,
				    "zero_allocation": %s,
				    "p99_below_10_us": %s
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
				fixture.json(),
				ITERATIONS,
				longArray(benchmark.elapsed()),
				benchmark.p50(),
				benchmark.p99(),
				longArray(benchmark.allocations()),
				benchmark.checksum(),
				fixture.fadeIn(),
				fixture.nearbySameSlot(),
				fixture.topologyCrossfade(),
				fixture.incompleteFadeAndRetire(),
				Arrays.stream(benchmark.allocations())
						.allMatch(value -> value == 0),
				benchmark.p99() <= 10_000.0
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-early-reflection-cluster-slew-reference\","
						+ "\"p99_us\":%.17g}%n",
				benchmark.p99() / 1_000.0
		);
	}

	private static Fixture fixture() {
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew();
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		MutableInput input = new MutableInput();
		input.setSingle(120, 0.25, 0.36, 0.49, 2, 0, 0);
		slew.update(input, frame);
		int firstSlot = frame.slot(0);
		boolean fadeIn = frame.slotCount() == 1
				&& frame.rampSamples() == 2400
				&& frame.startMid(0) == 0.0
				&& frame.targetMid(0) == 0.6
				&& frame.targetDirectionX(0) == 1.0;

		input.setSingle(150, 0.36, 0.49, 0.64, 0, 3, 0);
		slew.update(input, frame);
		boolean nearby = frame.slotCount() == 1
				&& frame.slot(0) == firstSlot
				&& frame.startDelaySamples(0) == 120.0
				&& frame.targetDelaySamples(0) == 150.0
				&& frame.targetDirectionY(0) == 1.0;

		input.setSingle(400, 0.49, 0.64, 0.81, 0, 0, 4);
		slew.update(input, frame);
		boolean topology = frame.slotCount() == 2
				&& frame.slot(0) != frame.slot(1)
				&& oneFadesInAndOneOut(frame);

		input.complete = false;
		input.count = 0;
		slew.update(input, frame);
		boolean faded = frame.slotCount() >= 1;
		slew.update(input, frame);
		boolean retired = frame.slotCount() == 0;
		boolean incomplete = faded && retired;
		String json = String.format(
				Locale.ROOT,
				"{\"first_slot\":%d,\"ramp_samples\":2400,"
						+ "\"fade_in_target_mid\":0.6,"
						+ "\"nearby_target_delay\":150.0,"
						+ "\"replacement_target_delay\":400.0,"
						+ "\"replacement_slot_count\":2,"
						+ "\"retired_slot_count\":0}",
				firstSlot
		);
		return new Fixture(fadeIn, nearby, topology, incomplete, json);
	}

	private static boolean oneFadesInAndOneOut(
			EarlyReflectionClusterSlew.Frame frame
	) {
		boolean fadeIn = false;
		boolean fadeOut = false;
		for (int index = 0; index < frame.slotCount(); index++) {
			fadeIn |= frame.startMid(index) == 0.0
					&& frame.targetMid(index) > 0.0;
			fadeOut |= frame.startMid(index) > 0.0
					&& frame.targetMid(index) == 0.0;
		}
		return fadeIn && fadeOut;
	}

	private static Benchmark benchmark() {
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew();
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		MutableInput input = new MutableInput();
		input.setSix();
		for (int warmup = 0; warmup < ITERATIONS; warmup++) {
			input.shift(warmup);
			slew.update(input, frame);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		for (int priming = 0; priming < 250_000; priming++) {
			input.shift(priming);
			slew.update(input, frame);
		}
		long[] allocations = new long[5];
		for (int window = 0; window < allocations.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int iteration = 0; iteration < 250_000; iteration++) {
				input.shift(iteration + window);
				slew.update(input, frame);
			}
			allocations[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		long[] elapsed = new long[ITERATIONS];
		double checksum = 0.0;
		for (int iteration = 0; iteration < ITERATIONS; iteration++) {
			input.shift(iteration);
			long started = System.nanoTime();
			slew.update(input, frame);
			elapsed[iteration] = System.nanoTime() - started;
			checksum += frame.targetDelaySamples(iteration % 6)
					+ frame.targetMid(iteration % 6);
		}
		long[] sorted = elapsed.clone();
		Arrays.sort(sorted);
		return new Benchmark(
				elapsed,
				percentile(sorted, 0.50),
				percentile(sorted, 0.99),
				allocations,
				checksum
		);
	}

	private static long percentile(long[] sorted, double quantile) {
		return sorted[(int) Math.ceil(quantile * sorted.length) - 1];
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

	private static final class MutableInput
			implements EarlyReflectionClusterSlew.Input {
		private boolean complete = true;
		private int count;
		private final double[] arrival = new double[6];
		private final double[] low = new double[6];
		private final double[] mid = new double[6];
		private final double[] high = new double[6];
		private final double[] x = new double[6];
		private final double[] y = new double[6];
		private final double[] z = new double[6];

		private void setSingle(
				double nextArrival,
				double nextLow,
				double nextMid,
				double nextHigh,
				double nextX,
				double nextY,
				double nextZ
		) {
			complete = true;
			count = 1;
			arrival[0] = nextArrival;
			low[0] = nextLow;
			mid[0] = nextMid;
			high[0] = nextHigh;
			x[0] = nextX;
			y[0] = nextY;
			z[0] = nextZ;
		}

		private void setSix() {
			complete = true;
			count = 6;
			for (int index = 0; index < count; index++) {
				arrival[index] = 100 + index * 200;
				low[index] = 0.04 + index * 0.01;
				mid[index] = 0.05 + index * 0.01;
				high[index] = 0.06 + index * 0.01;
				x[index] = index % 2;
				y[index] = (index + 1) % 2;
				z[index] = 0.5;
			}
		}

		private void shift(int iteration) {
			double offset = (iteration & 31) * 0.01;
			for (int index = 0; index < count; index++) {
				arrival[index] = 100 + index * 200 + offset;
			}
		}

		@Override
		public boolean complete() {
			return complete;
		}

		@Override
		public int clusterCount() {
			return count;
		}

		@Override
		public double clusterArrivalSamples(int index) {
			return arrival[index];
		}

		@Override
		public double clusterLow(int index) {
			return low[index];
		}

		@Override
		public double clusterMid(int index) {
			return mid[index];
		}

		@Override
		public double clusterHigh(int index) {
			return high[index];
		}

		@Override
		public double clusterDirectionX(int index) {
			return x[index];
		}

		@Override
		public double clusterDirectionY(int index) {
			return y[index];
		}

		@Override
		public double clusterDirectionZ(int index) {
			return z[index];
		}
	}

	private record Fixture(
			boolean fadeIn,
			boolean nearbySameSlot,
			boolean topologyCrossfade,
			boolean incompleteFadeAndRetire,
			String json
	) {
	}

	private record Benchmark(
			long[] elapsed,
			double p50,
			double p99,
			long[] allocations,
			double checksum
	) {
	}
}
