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

/** Emits D121i null-backend fractional-delay renderer evidence. */
public final class NullEarlyReflectionRendererReferenceCli {
	private static final int BLOCK_SIZE = 256;
	private static final int TIMING_BLOCKS = 10_000;

	private NullEarlyReflectionRendererReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121i-renderer-contract>"
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
				  "status": "valid-null-early-reflection-renderer-reference",
				  "source_contract_sha256": "%s",
				  "fixture": {
				    "integer_peak_index": %d,
				    "integer_peak_value": %.17g,
				    "fractional_first_index": %d,
				    "fractional_first_value": %.17g,
				    "fractional_second_index": %d,
				    "fractional_second_value": %.17g,
				    "moving_maximum_step": %.17g,
				    "topology_maximum_step": %.17g,
				    "all_outputs_finite": %s,
				    "over_budget_rejected": %s
				  },
				  "benchmark": {
				    "block_size": %d,
				    "timing_blocks": %d,
				    "active_slots": 12,
				    "elapsed_ns_per_frame": %s,
				    "p50_ns_per_frame": %.17g,
				    "p99_ns_per_frame": %.17g,
				    "allocation_windows_bytes": %s,
				    "checksum": %.17g
				  },
				  "gates": {
				    "integer_delay_exact": %s,
				    "fractional_delay_exact": %s,
				    "moving_continuous": %s,
				    "topology_continuous": %s,
				    "finite": %s,
				    "over_budget_rejected": %s,
				    "zero_allocation": %s,
				    "p99_below_500_ns_per_frame": %s
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
				fixture.integerPeakIndex(),
				fixture.integerPeakValue(),
				fixture.fractionalFirstIndex(),
				fixture.fractionalFirstValue(),
				fixture.fractionalSecondIndex(),
				fixture.fractionalSecondValue(),
				fixture.movingMaximumStep(),
				fixture.topologyMaximumStep(),
				fixture.allFinite(),
				fixture.overBudgetRejected(),
				BLOCK_SIZE,
				TIMING_BLOCKS,
				doubleArray(benchmark.elapsedNanosPerFrame()),
				benchmark.p50NanosPerFrame(),
				benchmark.p99NanosPerFrame(),
				longArray(benchmark.allocations()),
				benchmark.checksum(),
				fixture.integerPeakIndex() == 4
						&& close(
								fixture.integerPeakValue(),
								Math.sqrt(0.5)
						),
				fixture.fractionalFirstIndex() == 4
						&& fixture.fractionalSecondIndex() == 5
						&& close(
								fixture.fractionalFirstValue(),
								Math.sqrt(0.5) * 0.5
						)
						&& close(
								fixture.fractionalSecondValue(),
								Math.sqrt(0.5) * 0.5
						),
				fixture.movingMaximumStep() <= 0.25,
				fixture.topologyMaximumStep() <= 0.25,
				fixture.allFinite(),
				fixture.overBudgetRejected(),
				Arrays.stream(benchmark.allocations())
						.allMatch(value -> value == 0L),
				benchmark.p99NanosPerFrame() <= 500.0
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-null-early-reflection-renderer-reference\","
						+ "\"p99_ns_per_frame\":%.17g}%n",
				benchmark.p99NanosPerFrame()
		);
	}

	private static Fixture fixture() {
		double pan = Math.sqrt(0.5);
		double[] integer = impulse(4.0);
		int integerPeak = peakIndex(integer);
		double[] fractional = impulse(4.5);
		int first = firstNonZero(fractional);
		int second = first + 1;

		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(256);
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew(8.0, 2_400);
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		MutableInput input = new MutableInput(1);
		input.set(0, 20, 0.25, 0.0);
		slew.update(input, frame);
		renderer.submit(frame);
		double[] prime = sine(4_096);
		double[] left = new double[4_096];
		double[] right = new double[4_096];
		renderer.render(prime, 0, left, right, 0, prime.length);
		input.arrival[0] = 24;
		slew.update(input, frame);
		renderer.submit(frame);
		double[] moving = sine(2_400);
		left = new double[2_400];
		right = new double[2_400];
		renderer.render(moving, 0, left, right, 0, moving.length);
		double movingStep = maximumStep(left, right);
		boolean finite = allFinite(left) && allFinite(right);
		input.arrival[0] = 100;
		slew.update(input, frame);
		renderer.submit(frame);
		double[] topology = sine(2_400);
		left = new double[2_400];
		right = new double[2_400];
		renderer.render(topology, 0, left, right, 0, topology.length);
		double topologyStep = maximumStep(left, right);
		finite &= allFinite(left) && allFinite(right);

		boolean rejected;
		try {
			MutableInput excessive = new MutableInput(2);
			excessive.set(0, 4, 0.64, 0.0);
			excessive.set(1, 12, 0.64, 0.0);
			EarlyReflectionClusterSlew excessiveSlew =
					new EarlyReflectionClusterSlew(96.0, 32);
			excessiveSlew.update(excessive, frame);
			renderer.submit(frame);
			rejected = false;
		} catch (IllegalArgumentException expected) {
			rejected = true;
		}
		return new Fixture(
				integerPeak,
				integer[integerPeak],
				first,
				fractional[first],
				second,
				fractional[second],
				movingStep,
				topologyStep,
				finite
						&& close(integer[integerPeak], pan)
						&& close(fractional[first], pan * 0.5),
				rejected
		);
	}

	private static double[] impulse(double delay) {
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(64);
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew(96.0, 1);
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		MutableInput input = new MutableInput(1);
		input.set(0, delay, 1.0, 0.0);
		slew.update(input, frame);
		renderer.submit(frame);
		double[] dry = new double[16];
		dry[0] = 1.0;
		double[] left = new double[16];
		double[] right = new double[16];
		renderer.render(dry, 0, left, right, 0, dry.length);
		return left;
	}

	private static Benchmark benchmark() {
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer();
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew(1.0, 2_400);
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		MutableInput input = new MutableInput(6);
		for (int cluster = 0; cluster < 6; cluster++) {
			input.set(
					cluster,
					100 + cluster * 200,
					1.0 / 6.0,
					cluster / 2.5 - 1.0
			);
		}
		slew.update(input, frame);
		renderer.submit(frame);
		double[] dry = sine(BLOCK_SIZE);
		double[] left = new double[BLOCK_SIZE];
		double[] right = new double[BLOCK_SIZE];
		for (int warmup = 0; warmup < 2_000; warmup++) {
			renderer.render(
					dry, 0, left, right, 0, BLOCK_SIZE
			);
		}
		for (int cluster = 0; cluster < 6; cluster++) {
			input.arrival[cluster] += 2_000;
		}
		slew.update(input, frame);
		if (frame.slotCount() != 12) {
			throw new IllegalStateException(
					"benchmark did not create 12-slot crossfade"
			);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		for (int prime = 0; prime < 5_000; prime++) {
			renderer.submit(frame);
			renderer.render(dry, 0, left, right, 0, BLOCK_SIZE);
		}
		long[] allocations = new long[5];
		for (int window = 0; window < allocations.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int block = 0; block < 5_000; block++) {
				renderer.submit(frame);
				renderer.render(
						dry, 0, left, right, 0, BLOCK_SIZE
				);
			}
			allocations[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		double[] elapsedPerFrame = new double[TIMING_BLOCKS];
		double checksum = 0.0;
		for (int block = 0; block < TIMING_BLOCKS; block++) {
			renderer.submit(frame);
			long started = System.nanoTime();
			renderer.render(dry, 0, left, right, 0, BLOCK_SIZE);
			elapsedPerFrame[block] =
					(System.nanoTime() - started) / (double) BLOCK_SIZE;
			checksum += left[block & (BLOCK_SIZE - 1)]
					+ right[(block * 31) & (BLOCK_SIZE - 1)];
		}
		double[] sorted = elapsedPerFrame.clone();
		Arrays.sort(sorted);
		return new Benchmark(
				elapsedPerFrame,
				percentile(sorted, 0.50),
				percentile(sorted, 0.99),
				allocations,
				checksum
		);
	}

	private static double[] sine(int length) {
		double[] output = new double[length];
		for (int index = 0; index < length; index++) {
			output[index] = 0.2 * Math.sin(
					2.0 * Math.PI * 997.0 * index
							/ NullEarlyReflectionRenderer.SAMPLE_RATE_HZ
			);
		}
		return output;
	}

	private static int peakIndex(double[] values) {
		int selected = 0;
		for (int index = 1; index < values.length; index++) {
			if (Math.abs(values[index]) > Math.abs(values[selected])) {
				selected = index;
			}
		}
		return selected;
	}

	private static int firstNonZero(double[] values) {
		for (int index = 0; index < values.length; index++) {
			if (Math.abs(values[index]) > 1.0e-12) {
				return index;
			}
		}
		throw new IllegalStateException("impulse response was silent");
	}

	private static double maximumStep(double[] left, double[] right) {
		double maximum = 0.0;
		for (int index = 1; index < left.length; index++) {
			maximum = Math.max(
					maximum,
					Math.abs(left[index] - left[index - 1])
			);
			maximum = Math.max(
					maximum,
					Math.abs(right[index] - right[index - 1])
			);
		}
		return maximum;
	}

	private static boolean allFinite(double[] values) {
		for (double value : values) {
			if (!Double.isFinite(value)) {
				return false;
			}
		}
		return true;
	}

	private static double percentile(double[] sorted, double quantile) {
		return sorted[(int) Math.ceil(quantile * sorted.length) - 1];
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) <= 1.0e-10;
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

	private static final class MutableInput
			implements EarlyReflectionClusterSlew.Input {
		private final int count;
		private final double[] arrival;
		private final double[] energy;
		private final double[] directionX;

		private MutableInput(int count) {
			this.count = count;
			arrival = new double[count];
			energy = new double[count];
			directionX = new double[count];
		}

		private void set(
				int index,
				double nextArrival,
				double nextEnergy,
				double nextDirectionX
		) {
			arrival[index] = nextArrival;
			energy[index] = nextEnergy;
			directionX[index] = nextDirectionX;
		}

		@Override
		public boolean complete() {
			return true;
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

	private record Fixture(
			int integerPeakIndex,
			double integerPeakValue,
			int fractionalFirstIndex,
			double fractionalFirstValue,
			int fractionalSecondIndex,
			double fractionalSecondValue,
			double movingMaximumStep,
			double topologyMaximumStep,
			boolean allFinite,
			boolean overBudgetRejected
	) {
	}

	private record Benchmark(
			double[] elapsedNanosPerFrame,
			double p50NanosPerFrame,
			double p99NanosPerFrame,
			long[] allocations,
			double checksum
	) {
	}
}
