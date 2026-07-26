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

/** D121l paired CPU/wall budget for 1/4/6 worst-case early renderers. */
public final class PairedHighBandRendererBudgetCli {
	private static final int BLOCK_SIZE = 256;
	private static final int PAIRS = 2_000;
	private static final int WALL_PAIRS = 200;
	private static final int WALL_AGGREGATION_BLOCKS = 20;
	private static final int WALL_SAMPLES =
			WALL_PAIRS / WALL_AGGREGATION_BLOCKS;
	private static final int TRIALS = 3;
	private static final int[] SOURCE_COUNTS = {1, 4, 6};
	private static final int WARMUP_PAIRS = 1_000;
	private static final int ALLOCATION_WINDOWS = 5;
	private static final int ALLOCATION_PAIRS = 100;
	private static final double SAMPLE_PERIOD_NS =
			1_000_000_000.0
					/ NullEarlyReflectionRenderer.SAMPLE_RATE_HZ;

	private PairedHighBandRendererBudgetCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121l-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		ThreadMXBean bean = measurementBean();
		SourceCountResult[] results =
				new SourceCountResult[SOURCE_COUNTS.length];
		for (int index = 0; index < SOURCE_COUNTS.length; index++) {
			results[index] = measureSourceCount(
					SOURCE_COUNTS[index],
					bean
			);
		}
		boolean ratioGate = Arrays.stream(results).allMatch(
				result -> result.maximumMedianCpuRatio() <= 1.25
		);
		SourceCountResult six = results[results.length - 1];
		double sixP95 = six.worst().candidateCpuP95();
		boolean sixBudgetGate = sixP95 <= SAMPLE_PERIOD_NS * 0.20;
		boolean allocationGate = Arrays.stream(results).allMatch(
				SourceCountResult::allZeroAllocation
		);
		StringBuilder cases = new StringBuilder();
		for (int index = 0; index < results.length; index++) {
			if (index > 0) {
				cases.append(",\n");
			}
			cases.append(String.format(
					Locale.ROOT,
					"    \"source_%d\": %s",
					results[index].sourceCount(),
					results[index].json()
			));
		}
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-paired-high-band-renderer-budget",
				  "source_contract_sha256": "%s",
				  "sample_rate_hz": 48000,
				  "sample_period_ns": %.17g,
				  "block_size": %d,
				  "measured_pairs_per_trial": %d,
				  "thread_cpu_counter_aggregation_blocks_per_mode": %d,
				  "wall_diagnostic_block_pairs_per_trial": %d,
				  "wall_diagnostic_aggregation_block_pairs": %d,
				  "trials": %d,
				  "cases": {
				%s
				  },
				  "gates": {
				    "candidate_median_cpu_ratio_below_1_25": %s,
				    "six_source_candidate_cpu_p95_below_20_percent": %s,
				    "all_zero_allocation": %s,
				    "all_checksums_finite": %s,
				    "d121k_failure_preserved": true
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
				SAMPLE_PERIOD_NS,
				BLOCK_SIZE,
				PAIRS,
				PAIRS,
				WALL_PAIRS,
				WALL_AGGREGATION_BLOCKS,
				TRIALS,
				cases,
				ratioGate,
				sixBudgetGate,
				allocationGate,
				Arrays.stream(results).allMatch(
						SourceCountResult::allFiniteChecksums
				)
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-paired-high-band-renderer-budget\","
						+ "\"max_median_cpu_ratio\":%.17g,"
						+ "\"six_source_candidate_cpu_p95\":%.17g,"
						+ "\"six_source_cpu_share\":%.17g}%n",
				Arrays.stream(results)
						.mapToDouble(
								SourceCountResult::maximumMedianCpuRatio
						)
						.max()
						.orElseThrow(),
				sixP95,
				sixP95 / SAMPLE_PERIOD_NS
		);
	}

	private static SourceCountResult measureSourceCount(
			int sourceCount,
			ThreadMXBean bean
	) {
		Trial[] trials = new Trial[TRIALS];
		for (int trial = 0; trial < TRIALS; trial++) {
			trials[trial] = measureTrial(sourceCount, trial, bean);
		}
		int worstIndex = 0;
		for (int index = 1; index < trials.length; index++) {
			if (trials[index].candidateCpuP50()
					> trials[worstIndex].candidateCpuP50()) {
				worstIndex = index;
			}
		}
		return new SourceCountResult(sourceCount, worstIndex, trials);
	}

	private static Trial measureTrial(
			int sourceCount,
			int trialIndex,
			ThreadMXBean bean
	) {
		Bank baseline = new Bank(
				sourceCount,
				NullEarlyReflectionRenderer.Interpolation.LAGRANGE_CUBIC
		);
		Bank candidate = new Bank(
				sourceCount,
				NullEarlyReflectionRenderer.Interpolation
						.HIGH_BAND_KAISER_SINC8
		);
		for (int pair = 0; pair < WARMUP_PAIRS; pair++) {
			if ((pair & 1) == 0) {
				baseline.render();
				candidate.render();
			} else {
				candidate.render();
				baseline.render();
			}
		}
		long threadId = Thread.currentThread().threadId();
		bean.getThreadAllocatedBytes(threadId);
		long[] allocationWindows = new long[ALLOCATION_WINDOWS];
		for (int window = 0; window < ALLOCATION_WINDOWS; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int pair = 0; pair < ALLOCATION_PAIRS; pair++) {
				baseline.render();
				candidate.render();
			}
			allocationWindows[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		double[] baselineCpu = new double[1];
		double[] candidateCpu = new double[1];
		if ((trialIndex & 1) == 0) {
			baselineCpu[0] = measureCpu(baseline, bean);
			candidateCpu[0] = measureCpu(candidate, bean);
		} else {
			candidateCpu[0] = measureCpu(candidate, bean);
			baselineCpu[0] = measureCpu(baseline, bean);
		}
		if (baselineCpu[0] <= 0.0 || candidateCpu[0] <= 0.0) {
			throw new IllegalStateException(
					"thread CPU counter did not resolve full trial"
			);
		}
		double[] baselineWall = new double[WALL_SAMPLES];
		double[] candidateWall = new double[WALL_SAMPLES];
		for (int sample = 0; sample < WALL_SAMPLES; sample++) {
			if ((sample & 1) == 0) {
				measureWall(
						baseline,
						baselineWall,
						sample
				);
				measureWall(
						candidate,
						candidateWall,
						sample
				);
			} else {
				measureWall(
						candidate,
						candidateWall,
						sample
				);
				measureWall(
						baseline,
						baselineWall,
						sample
				);
			}
		}
		return new Trial(
				baselineCpu,
				candidateCpu,
				baselineWall,
				candidateWall,
				allocationWindows,
				baseline.checksum(),
				candidate.checksum()
		);
	}

	private static double measureCpu(
			Bank bank,
			ThreadMXBean bean
	) {
		long started = bean.getCurrentThreadCpuTime();
		for (int block = 0; block < PAIRS; block++) {
			bank.render();
		}
		long elapsed = bean.getCurrentThreadCpuTime() - started;
		return elapsed / (BLOCK_SIZE * (double) PAIRS);
	}

	private static void measureWall(
			Bank bank,
			double[] wall,
			int index
	) {
		long wallStart = System.nanoTime();
		for (
				int block = 0;
				block < WALL_AGGREGATION_BLOCKS;
				block++
		) {
			bank.render();
		}
		long wallElapsed = System.nanoTime() - wallStart;
		double frames =
				BLOCK_SIZE * (double) WALL_AGGREGATION_BLOCKS;
		wall[index] = wallElapsed / frames;
	}

	private static ThreadMXBean measurementBean() {
		java.lang.management.ThreadMXBean base =
				ManagementFactory.getThreadMXBean();
		if (!(base instanceof ThreadMXBean bean)
				|| !bean.isThreadAllocatedMemorySupported()
				|| !bean.isCurrentThreadCpuTimeSupported()) {
			throw new IllegalStateException(
					"thread CPU/allocation counters unavailable"
			);
		}
		if (!bean.isThreadAllocatedMemoryEnabled()) {
			bean.setThreadAllocatedMemoryEnabled(true);
		}
		if (!bean.isThreadCpuTimeEnabled()) {
			bean.setThreadCpuTimeEnabled(true);
		}
		return bean;
	}

	private static double percentile(double[] values, double quantile) {
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		return sorted[(int) Math.ceil(quantile * sorted.length) - 1];
	}

	private static String doubles(double[] values) {
		StringBuilder output = new StringBuilder("[");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				output.append(',');
			}
			output.append(String.format(Locale.ROOT, "%.17g", values[index]));
		}
		return output.append(']').toString();
	}

	private static String longs(long[] values) {
		StringBuilder output = new StringBuilder("[");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				output.append(',');
			}
			output.append(values[index]);
		}
		return output.append(']').toString();
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

	private static final class Bank {
		private final NullEarlyReflectionRenderer[] renderers;
		private final EarlyReflectionClusterSlew.Frame[] frames;
		private final double[][] dry;
		private final double[][] left;
		private final double[][] right;

		private Bank(
				int sourceCount,
				NullEarlyReflectionRenderer.Interpolation interpolation
		) {
			renderers = new NullEarlyReflectionRenderer[sourceCount];
			frames = new EarlyReflectionClusterSlew.Frame[sourceCount];
			dry = new double[sourceCount][BLOCK_SIZE];
			left = new double[sourceCount][BLOCK_SIZE];
			right = new double[sourceCount][BLOCK_SIZE];
			for (int source = 0; source < sourceCount; source++) {
				renderers[source] = new NullEarlyReflectionRenderer(
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
					input.arrival[cluster] =
							100.25 + source * 7.0 + cluster * 200.0;
					input.energy[cluster] = 1.0 / 6.0;
					input.directionX[cluster] = cluster / 2.5 - 1.0;
				}
				slew.update(input, frame);
				renderers[source].submit(frame);
				for (int index = 0; index < BLOCK_SIZE; index++) {
					dry[source][index] =
							0.14 * Math.sin(
									2.0 * Math.PI
											* (5_760.0 + source * 97.0)
											* index / 48_000.0
							)
							+ 0.06 * Math.sin(
									2.0 * Math.PI
											* (12_000.0 - source * 113.0)
											* index / 48_000.0
							);
				}
				renderers[source].render(
						dry[source],
						0,
						left[source],
						right[source],
						0,
						BLOCK_SIZE
				);
				for (int cluster = 0; cluster < 6; cluster++) {
					input.arrival[cluster] += 2_000.5;
				}
				slew.update(input, frame);
				if (frame.slotCount() != 12) {
					throw new IllegalStateException(
							"expected 12 render slots"
					);
				}
				frames[source] = frame;
			}
		}

		private void render() {
			for (int source = 0; source < renderers.length; source++) {
				renderers[source].submit(frames[source]);
				renderers[source].render(
						dry[source],
						0,
						left[source],
						right[source],
						0,
						BLOCK_SIZE
				);
			}
		}

		private double checksum() {
			double result = 0.0;
			for (int source = 0; source < renderers.length; source++) {
				result += left[source][source * 17 & 255]
						+ right[source][source * 31 & 255];
			}
			return result;
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

	private record Trial(
			double[] baselineCpu,
			double[] candidateCpu,
			double[] baselineWall,
			double[] candidateWall,
			long[] allocationWindows,
			double baselineChecksum,
			double candidateChecksum
	) {
		private double baselineCpuP50() {
			return percentile(baselineCpu, 0.50);
		}

		private double candidateCpuP50() {
			return percentile(candidateCpu, 0.50);
		}

		private double candidateCpuP95() {
			return percentile(candidateCpu, 0.95);
		}

		private double medianCpuRatio() {
			return candidateCpuP50() / baselineCpuP50();
		}

		private boolean zeroAllocation() {
			return Arrays.stream(allocationWindows)
					.allMatch(value -> value == 0L);
		}

		private boolean finiteChecksums() {
			return Double.isFinite(baselineChecksum)
					&& Double.isFinite(candidateChecksum);
		}

		private String json() {
			return String.format(
					Locale.ROOT,
					"{\"baseline_cpu_ns_per_frame\":%s,"
							+ "\"candidate_cpu_ns_per_frame\":%s,"
							+ "\"baseline_wall_ns_per_frame\":%s,"
							+ "\"candidate_wall_ns_per_frame\":%s,"
							+ "\"baseline_cpu_p50\":%.17g,"
							+ "\"baseline_cpu_p95\":%.17g,"
							+ "\"baseline_cpu_p99\":%.17g,"
							+ "\"candidate_cpu_p50\":%.17g,"
							+ "\"candidate_cpu_p95\":%.17g,"
							+ "\"candidate_cpu_p99\":%.17g,"
							+ "\"baseline_wall_p99\":%.17g,"
							+ "\"candidate_wall_p99\":%.17g,"
							+ "\"median_cpu_ratio\":%.17g,"
							+ "\"allocation_windows_bytes\":%s,"
							+ "\"baseline_checksum\":%.17g,"
							+ "\"candidate_checksum\":%.17g}",
					doubles(baselineCpu),
					doubles(candidateCpu),
					doubles(baselineWall),
					doubles(candidateWall),
					baselineCpuP50(),
					percentile(baselineCpu, 0.95),
					percentile(baselineCpu, 0.99),
					candidateCpuP50(),
					candidateCpuP95(),
					percentile(candidateCpu, 0.99),
					percentile(baselineWall, 0.99),
					percentile(candidateWall, 0.99),
					medianCpuRatio(),
					longs(allocationWindows),
					baselineChecksum,
					candidateChecksum
			);
		}
	}

	private record SourceCountResult(
			int sourceCount,
			int worstTrialIndex,
			Trial[] trials
	) {
		private Trial worst() {
			return trials[worstTrialIndex];
		}

		private double maximumMedianCpuRatio() {
			return Arrays.stream(trials)
					.mapToDouble(Trial::medianCpuRatio)
					.max()
					.orElseThrow();
		}

		private boolean allZeroAllocation() {
			return Arrays.stream(trials).allMatch(Trial::zeroAllocation);
		}

		private boolean allFiniteChecksums() {
			return Arrays.stream(trials).allMatch(Trial::finiteChecksums);
		}

		private String json() {
			StringBuilder json = new StringBuilder(
					String.format(
							Locale.ROOT,
							"{\"source_count\":%d,"
									+ "\"worst_candidate_median_cpu_"
									+ "trial_index\":%d,"
									+ "\"maximum_median_cpu_ratio\":"
									+ "%.17g,\"trials\":[",
							sourceCount,
							worstTrialIndex,
							maximumMedianCpuRatio()
					)
			);
			for (int index = 0; index < trials.length; index++) {
				if (index > 0) {
					json.append(',');
				}
				json.append(trials[index].json());
			}
			return json.append("]}").toString();
		}
	}
}
