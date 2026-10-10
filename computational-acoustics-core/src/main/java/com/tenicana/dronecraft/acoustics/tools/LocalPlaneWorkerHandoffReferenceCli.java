package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.AxisAlignedPlanePatch;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneReflectionSolver;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneWorkerHandoff;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneWorkerHandoff.Result;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneWorkerHandoff.SceneSnapshot;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneWorkerHandoff.Statistics;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Executes D121c latest-generation worker handoff evidence without audio. */
public final class LocalPlaneWorkerHandoffReferenceCli {
	private static final List<AxisAlignedPlanePatch> PATCHES = List.of(
			patch(1, 1, 0.0, -3.0, 3.0, -3.0, 3.0),
			patch(1, -1, 4.0, -3.0, 3.0, -3.0, 3.0),
			patch(0, 1, -3.0, 0.0, 4.0, -3.0, 3.0),
			patch(0, -1, 3.0, 0.0, 4.0, -3.0, 3.0),
			patch(2, 1, -3.0, -3.0, 3.0, 0.0, 4.0),
			patch(2, -1, 3.0, -3.0, 3.0, 0.0, 4.0),
			patch(1, 1, -1.0, -3.0, 3.0, -3.0, 3.0),
			patch(1, 1, -2.0, -3.0, 3.0, -3.0, 3.0)
	);
	private static final LocalPlaneReflectionSolver.CellBlockQuery OPEN =
			(x, y, z) -> false;
	private static final SceneSnapshot COMPLETE = new SceneSnapshot(
			true,
			PATCHES,
			OPEN,
			LocalPlaneReflectionSolver.SegmentBlockQuery.NONE,
			64
	);
	private static final SceneSnapshot INCOMPLETE = new SceneSnapshot(
			false,
			List.of(),
			OPEN,
			LocalPlaneReflectionSolver.SegmentBlockQuery.NONE,
			64
	);
	private static final int[] SOURCE_COUNTS = {1, 4, 8, 16};

	private LocalPlaneWorkerHandoffReferenceCli() {
	}

	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121c-worker-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		FunctionalEvidence functional = functionalEvidence();
		Benchmark[] benchmarks = new Benchmark[SOURCE_COUNTS.length];
		for (int index = 0; index < SOURCE_COUNTS.length; index++) {
			benchmarks[index] = benchmark(SOURCE_COUNTS[index]);
		}
		AllocationEvidence allocation = allocationEvidence();
		boolean budgets = Arrays.stream(benchmarks).allMatch(
				benchmark -> benchmark.p99BatchNanos() <= 25_000_000.0
						&& benchmark.p99SolveNanos() <= 100_000.0
						&& benchmark.p99ApplyNanos() <= 100_000.0
		);
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-minecraft-local-plane-worker-handoff-reference",
				  "source_contract_sha256": "%s",
				  "functional": %s,
				  "source_count_benchmarks": %s,
				  "allocation": {
				    "operations_per_window": %d,
				    "submit_windows_bytes": %s,
				    "submit_median_bytes_per_operation": %.17g,
				    "submit_poll_windows_bytes": %s,
				    "submit_poll_median_bytes_per_operation": %.17g,
				    "checksum": %.17g
				  },
				  "gates": {
				    "actual_worker_thread_executed": true,
				    "latest_pending_generation_wins": %s,
				    "stale_solved_generation_discarded": %s,
				    "incomplete_snapshot_falls_back": %s,
				    "all_source_counts_measured": %s,
				    "timing_budgets_pass": %s,
				    "submit_and_consumer_copy_zero_allocation": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "minecraft_client_started": false,
				  "cuda_executed": false,
				  "minecraft_integration_enabled": false,
				  "worker_handoff_measured": true,
				  "live_early_renderer_enabled": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				functional.json(),
				benchmarksJson(benchmarks),
				allocation.operationsPerWindow(),
				longArray(allocation.submitWindows()),
				allocation.submitBytesPerOperation(),
				longArray(allocation.submitPollWindows()),
				allocation.submitPollBytesPerOperation(),
				allocation.checksum(),
				functional.pendingGeneration() == 2
						&& functional.pendingOverwrites() >= 1,
				functional.staleGeneration() == 2
						&& functional.staleSolvedResults() >= 1,
				functional.incompleteFallback()
						&& functional.incompleteSelectedCount() == 0,
				benchmarks.length == SOURCE_COUNTS.length,
				budgets,
				allocation.submitBytesPerOperation() == 0.0
						&& allocation.submitPollBytesPerOperation() == 0.0
						&& allZero(allocation.submitWindows())
						&& allZero(allocation.submitPollWindows())
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-minecraft-local-plane-worker-handoff-reference\","
						+ "\"max_sources\":16,\"p99_batch_ms\":%.17g,"
						+ "\"allocation_bytes\":%.17g}%n",
				benchmarks[benchmarks.length - 1].p99BatchNanos()
						/ 1_000_000.0,
				allocation.submitPollBytesPerOperation()
		);
	}

	private static FunctionalEvidence functionalEvidence()
			throws InterruptedException {
		long pendingGeneration;
		long pendingOverwrites;
		try (LocalPlaneSceneWorkerHandoff handoff =
						new LocalPlaneSceneWorkerHandoff(1)) {
			handoff.submit(0, 1, 0, 2, 0, 0.5, 2, 0.2, COMPLETE);
			handoff.submit(0, 2, 0, 2, 0, 0.5, 2, 0.2, COMPLETE);
			handoff.start();
			Result result = await(handoff, 0, 2);
			pendingGeneration = result.generation();
			pendingOverwrites = handoff.statistics(
					new Statistics()
			).pendingOverwrites();
		}

		boolean incompleteFallback;
		int incompleteSelected;
		try (LocalPlaneSceneWorkerHandoff handoff =
						new LocalPlaneSceneWorkerHandoff(1)) {
			handoff.start();
			handoff.submit(
					0, 1, 0, 2, 0, 0.5, 2, 0.2, INCOMPLETE
			);
			Result result = await(handoff, 0, 1);
			incompleteFallback = result.conservativeFallback()
					&& !result.complete();
			incompleteSelected = result.selectedCount();
		}

		BlockingQuery blocking = new BlockingQuery();
		SceneSnapshot blockedSnapshot = new SceneSnapshot(
				true,
				PATCHES,
				blocking,
				LocalPlaneReflectionSolver.SegmentBlockQuery.NONE,
				64
		);
		long staleGeneration;
		long staleSolved;
		try (LocalPlaneSceneWorkerHandoff handoff =
						new LocalPlaneSceneWorkerHandoff(1)) {
			handoff.start();
			handoff.submit(
					0, 1, 0, 2, 0, 0.5, 2, 0.2, blockedSnapshot
			);
			if (!blocking.entered.await(2, TimeUnit.SECONDS)) {
				throw new IllegalStateException(
						"worker did not enter controlled query"
				);
			}
			handoff.submit(
					0, 2, 0.001, 2, 0, 0.5, 2, 0.2, COMPLETE
			);
			blocking.release.countDown();
			Result result = await(handoff, 0, 2);
			staleGeneration = result.generation();
			staleSolved = handoff.statistics(
					new Statistics()
			).staleSolvedResults();
		}
		return new FunctionalEvidence(
				pendingGeneration,
				pendingOverwrites,
				staleGeneration,
				staleSolved,
				incompleteFallback,
				incompleteSelected
		);
	}

	private static Benchmark benchmark(int sourceCount)
			throws InterruptedException {
		int warmupFrames = 100;
		int measuredFrames = 500;
		Result[] results = new Result[sourceCount];
		boolean[] received = new boolean[sourceCount];
		for (int source = 0; source < sourceCount; source++) {
			results[source] = new Result();
		}
		long[] batch = new long[measuredFrames];
		long[] queue = new long[measuredFrames * sourceCount];
		long[] solve = new long[measuredFrames * sourceCount];
		long[] publish = new long[measuredFrames * sourceCount];
		long[] endToEnd = new long[measuredFrames * sourceCount];
		long[] apply = new long[measuredFrames * sourceCount];
		double checksum = 0.0;
		try (LocalPlaneSceneWorkerHandoff handoff =
						new LocalPlaneSceneWorkerHandoff(sourceCount)) {
			handoff.start();
			for (int frame = 0;
					frame < warmupFrames + measuredFrames;
					frame++) {
				long generation = frame + 1L;
				Arrays.fill(received, false);
				long batchStarted = System.nanoTime();
				for (int source = 0; source < sourceCount; source++) {
					double offset = source * 0.001 + frame * 1.0e-7;
					if (!handoff.submit(
							source,
							generation,
							offset,
							2.0,
							0.0,
							0.5 + offset,
							2.0,
							0.2,
							COMPLETE
					)) {
						throw new IllegalStateException(
								"benchmark submission rejected"
						);
					}
				}
				int remaining = sourceCount;
				while (remaining > 0) {
					boolean progressed = false;
					for (int source = 0;
							source < sourceCount;
							source++) {
						if (received[source]) {
							continue;
						}
						long applyStarted = System.nanoTime();
						boolean available = handoff.pollLatest(
								source,
								generation,
								results[source]
						);
						long applyCompleted = System.nanoTime();
						if (!available) {
							continue;
						}
						received[source] = true;
						remaining--;
						progressed = true;
						Result result = results[source];
						if (result.generation() != generation
								|| result.source() != source) {
							throw new IllegalStateException(
									"generation/source handoff mismatch"
							);
						}
						checksum += result.selectedCount()
								+ result.clusterCount()
								+ result.pathLengthMeters(0);
						if (frame >= warmupFrames) {
							int index = (
									frame - warmupFrames
							) * sourceCount + source;
							queue[index] = result.solveStartedNanos()
									- result.submittedNanos();
							solve[index] = result.solveCompletedNanos()
									- result.solveStartedNanos();
							publish[index] = result.publishedNanos()
									- result.solveCompletedNanos();
							endToEnd[index] = applyCompleted
									- result.submittedNanos();
							apply[index] =
									applyCompleted - applyStarted;
						}
					}
					if (!progressed) {
						Thread.onSpinWait();
					}
				}
				if (frame >= warmupFrames) {
					batch[frame - warmupFrames] =
							System.nanoTime() - batchStarted;
				}
			}
		}
		long[] sortedBatch = batch.clone();
		long[] sortedQueue = queue.clone();
		long[] sortedSolve = solve.clone();
		long[] sortedPublish = publish.clone();
		long[] sortedEndToEnd = endToEnd.clone();
		long[] sortedApply = apply.clone();
		Arrays.sort(sortedBatch);
		Arrays.sort(sortedQueue);
		Arrays.sort(sortedSolve);
		Arrays.sort(sortedPublish);
		Arrays.sort(sortedEndToEnd);
		Arrays.sort(sortedApply);
		return new Benchmark(
				sourceCount,
				measuredFrames,
				percentile(sortedBatch, 0.50),
				percentile(sortedBatch, 0.95),
				percentile(sortedBatch, 0.99),
				percentile(sortedQueue, 0.99),
				percentile(sortedSolve, 0.99),
				percentile(sortedPublish, 0.99),
				percentile(sortedEndToEnd, 0.99),
				percentile(sortedApply, 0.99),
				checksum,
				batch,
				queue,
				solve,
				publish,
				endToEnd,
				apply
		);
	}

	private static AllocationEvidence allocationEvidence()
			throws InterruptedException {
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		int operations = 2_000;
		long[] submit = new long[5];
		double checksum = 0.0;
		try (LocalPlaneSceneWorkerHandoff handoff =
						new LocalPlaneSceneWorkerHandoff(1)) {
			long generation = 1;
			for (int window = 0; window < submit.length; window++) {
				long before = bean.getThreadAllocatedBytes(threadId);
				for (int operation = 0;
						operation < operations;
						operation++) {
					if (!handoff.submit(
							0,
							generation++,
							0, 2, 0,
							0.5, 2, 0.2,
							COMPLETE
					)) {
						throw new IllegalStateException(
								"allocation submit rejected"
						);
					}
				}
				submit[window] =
						bean.getThreadAllocatedBytes(threadId) - before;
			}
		}

		long[] submitPoll = new long[5];
		try (LocalPlaneSceneWorkerHandoff handoff =
						new LocalPlaneSceneWorkerHandoff(1)) {
			handoff.start();
			Result result = new Result();
			long generation = 1;
			for (int warmup = 0; warmup < 5_000; warmup++) {
				long next = generation++;
				handoff.submit(
						0, next,
						0, 2, 0,
						0.5, 2, 0.2,
						COMPLETE
				);
				while (!handoff.pollLatest(0, next, result)) {
					Thread.onSpinWait();
				}
				checksum += result.selectedCount();
			}
			for (int window = 0; window < submitPoll.length; window++) {
				long before = bean.getThreadAllocatedBytes(threadId);
				for (int operation = 0;
						operation < operations;
						operation++) {
					long next = generation++;
					handoff.submit(
							0, next,
							0, 2, 0,
							0.5, 2, 0.2,
							COMPLETE
					);
					while (!handoff.pollLatest(0, next, result)) {
						Thread.onSpinWait();
					}
					checksum += result.selectedCount()
							+ result.clusterArrivalSamples(0);
				}
				submitPoll[window] =
						bean.getThreadAllocatedBytes(threadId) - before;
			}
		}
		long[] sortedSubmit = submit.clone();
		long[] sortedSubmitPoll = submitPoll.clone();
		Arrays.sort(sortedSubmit);
		Arrays.sort(sortedSubmitPoll);
		return new AllocationEvidence(
				operations,
				submit,
				sortedSubmit[2] / (double) operations,
				submitPoll,
				sortedSubmitPoll[2] / (double) operations,
				checksum
		);
	}

	private static Result await(
			LocalPlaneSceneWorkerHandoff handoff,
			int source,
			long generation
	) {
		Result result = new Result();
		long deadline = System.nanoTime() + 2_000_000_000L;
		while (!handoff.pollLatest(source, generation, result)) {
			if (System.nanoTime() >= deadline) {
				throw new IllegalStateException(
						"timed out awaiting generation " + generation
				);
			}
			Thread.onSpinWait();
		}
		return result;
	}

	private static String benchmarksJson(Benchmark[] benchmarks) {
		StringBuilder json = new StringBuilder("[");
		for (int index = 0; index < benchmarks.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			Benchmark benchmark = benchmarks[index];
			json.append(String.format(
					Locale.ROOT,
					"{\"source_count\":%d,\"measured_frames\":%d,"
							+ "\"p50_batch_ns\":%.17g,"
							+ "\"p95_batch_ns\":%.17g,"
							+ "\"p99_batch_ns\":%.17g,"
							+ "\"p99_queue_ns\":%.17g,"
							+ "\"p99_solve_ns\":%.17g,"
							+ "\"p99_publish_ns\":%.17g,"
							+ "\"p99_end_to_end_ns\":%.17g,"
							+ "\"p99_apply_ns\":%.17g,"
							+ "\"checksum\":%.17g,"
							+ "\"samples\":{\"batch_ns\":%s,"
							+ "\"queue_ns\":%s,\"solve_ns\":%s,"
							+ "\"publish_ns\":%s,\"end_to_end_ns\":%s,"
							+ "\"apply_ns\":%s}}",
					benchmark.sourceCount(),
					benchmark.measuredFrames(),
					benchmark.p50BatchNanos(),
					benchmark.p95BatchNanos(),
					benchmark.p99BatchNanos(),
					benchmark.p99QueueNanos(),
					benchmark.p99SolveNanos(),
					benchmark.p99PublishNanos(),
					benchmark.p99EndToEndNanos(),
					benchmark.p99ApplyNanos(),
					benchmark.checksum(),
					samplesJson(benchmark.batchSamples()),
					samplesJson(benchmark.queueSamples()),
					samplesJson(benchmark.solveSamples()),
					samplesJson(benchmark.publishSamples()),
					samplesJson(benchmark.endToEndSamples()),
					samplesJson(benchmark.applySamples())
			));
		}
		return json.append(']').toString();
	}

	private static AxisAlignedPlanePatch patch(
			int axis,
			int normal,
			double coordinate,
			double minimumFirst,
			double maximumFirst,
			double minimumSecond,
			double maximumSecond
	) {
		return new AxisAlignedPlanePatch(
				axis,
				normal,
				coordinate,
				minimumFirst,
				maximumFirst,
				minimumSecond,
				maximumSecond,
				AcousticMaterials.STONE
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

	private static double percentile(long[] sorted, double quantile) {
		int index = (int) Math.ceil(quantile * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
	}

	private static String longArray(long[] values) {
		return String.format(
				Locale.ROOT,
				"[%d,%d,%d,%d,%d]",
				values[0], values[1], values[2], values[3], values[4]
		);
	}

	private static String samplesJson(long[] values) {
		StringBuilder json = new StringBuilder(
				Math.max(16, values.length * 8)
		).append('[');
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			json.append(values[index]);
		}
		return json.append(']').toString();
	}

	private static boolean allZero(long[] values) {
		for (long value : values) {
			if (value != 0L) {
				return false;
			}
		}
		return true;
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

	private static final class BlockingQuery
			implements LocalPlaneReflectionSolver.CellBlockQuery {
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);

		@Override
		public boolean isBlocked(int x, int y, int z) {
			entered.countDown();
			try {
				if (!release.await(2, TimeUnit.SECONDS)) {
					throw new IllegalStateException(
							"controlled blocker release timed out"
					);
				}
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(
						"controlled blocker interrupted",
						interrupted
				);
			}
			return false;
		}
	}

	private record FunctionalEvidence(
			long pendingGeneration,
			long pendingOverwrites,
			long staleGeneration,
			long staleSolvedResults,
			boolean incompleteFallback,
			int incompleteSelectedCount
	) {
		private String json() {
			return String.format(
					Locale.ROOT,
					"{\"pending_overwrite\":{\"published_generation\":%d,"
							+ "\"pending_overwrites\":%d},"
							+ "\"stale_solve\":{\"published_generation\":%d,"
							+ "\"stale_solved_results\":%d},"
							+ "\"incomplete\":{\"conservative_fallback\":%s,"
							+ "\"selected_count\":%d}}",
					pendingGeneration,
					pendingOverwrites,
					staleGeneration,
					staleSolvedResults,
					incompleteFallback,
					incompleteSelectedCount
			);
		}
	}

	private record Benchmark(
			int sourceCount,
			int measuredFrames,
			double p50BatchNanos,
			double p95BatchNanos,
			double p99BatchNanos,
			double p99QueueNanos,
			double p99SolveNanos,
			double p99PublishNanos,
			double p99EndToEndNanos,
			double p99ApplyNanos,
			double checksum,
			long[] batchSamples,
			long[] queueSamples,
			long[] solveSamples,
			long[] publishSamples,
			long[] endToEndSamples,
			long[] applySamples
	) {
	}

	private record AllocationEvidence(
			int operationsPerWindow,
			long[] submitWindows,
			double submitBytesPerOperation,
			long[] submitPollWindows,
			double submitPollBytesPerOperation,
			double checksum
	) {
	}
}
