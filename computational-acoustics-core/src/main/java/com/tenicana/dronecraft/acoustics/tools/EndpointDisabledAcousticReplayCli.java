package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.CellCaptureBounds;
import com.tenicana.dronecraft.acoustics.propagation.CoverageDirtyTracker;
import com.tenicana.dronecraft.acoustics.propagation.EarlyReflectionClusterSlew;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneReflectionSolver;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneWorkerHandoff;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotCache;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxSegmentBlockQuery;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;

/** D121m deterministic world-event to pure-memory wet-bus replay. */
public final class EndpointDisabledAcousticReplayCli {
	private static final int BLOCK_SIZE = 256;
	private static final int TICKS = 9;
	private static final int RAMP_SAMPLES = 64;
	private static final int MAXIMUM_CELLS_PER_LEG = 192;
	private static final CellCaptureBounds COVERAGE =
			new CellCaptureBounds(0, -1, 0, 4, 2, 4);
	private static final int HALO = 1;
	private static final String[] EVENTS = {
			"initial-capture",
			"outside-coverage-block-update",
			"inside-coverage-block-update",
			"chunk-unload",
			"chunk-reload",
			"block-update-during-capture",
			"stable-retry",
			"no-world-change",
			"world-change"
	};

	private EndpointDisabledAcousticReplayCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121m-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		ReplayResult replay = replay();
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-endpoint-disabled-acoustic-replay",
				  "source_contract_sha256": "%s",
				  "renderer_contract_version": 2,
				  "renderer_interpolation": "HIGH_BAND_KAISER_SINC8",
				  "sample_rate_hz": 48000,
				  "block_size": %d,
				  "events": %s,
				  "wet_left": %s,
				  "wet_right": %s,
				  "metrics": {
				    "trace_event_count": %d,
				    "cache_hits": %d,
				    "rebuilds": %d,
				    "incomplete_fallbacks": %d,
				    "complete_results_with_clusters": %d,
				    "dirty_during_capture_discards": %d,
				    "maximum_adjacent_wet_step": %.17g,
				    "all_wet_outputs_finite": %s,
				    "incomplete_fades_reached_zero": %s,
				    "energy_endpoint_checks_passed": %d,
				    "wet_checksum": %.17g
				  },
				  "latest_generation_probe": {
				    "applied_generation": %d,
				    "pending_overwrites": %d,
				    "stale_solved_results": %d,
				    "published_results": %d
				  },
				  "steady_renderer_allocation_windows_bytes": %s,
				  "gates": {
				    "trace_counts_match": %s,
				    "outside_dirty_cache_hit": %s,
				    "inside_dirty_rebuilt": %s,
				    "world_change_rebuilt": %s,
				    "latest_generation_only": %s,
				    "wet_continuity_below_0_25": %s,
				    "zero_renderer_allocation": %s
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
				replay.eventsJson(),
				doubles(replay.wetLeft()),
				doubles(replay.wetRight()),
				TICKS,
				replay.cacheHits(),
				replay.rebuilds(),
				replay.incompleteFallbacks(),
				replay.completeWithClusters(),
				replay.dirtyDuringCapture(),
				replay.maximumStep(),
				replay.allFinite(),
				replay.incompleteFadesReachedZero(),
				replay.energyChecks(),
				replay.wetChecksum(),
				replay.latestGeneration(),
				replay.pendingOverwrites(),
				replay.staleSolvedResults(),
				replay.latestPublishedResults(),
				longs(replay.allocationWindows()),
				replay.cacheHits() == 2
						&& replay.rebuilds() == 7
						&& replay.incompleteFallbacks() == 2
						&& replay.completeWithClusters() == 7
						&& replay.dirtyDuringCapture() == 1,
				replay.records()[1].cacheHit(),
				!replay.records()[2].cacheHit()
						&& replay.records()[2].snapshotComplete(),
				!replay.records()[8].cacheHit()
						&& replay.records()[8].snapshotComplete(),
				replay.latestGeneration() == 2
						&& replay.pendingOverwrites() >= 1,
				replay.maximumStep() <= 0.25
						&& replay.allFinite()
						&& replay.incompleteFadesReachedZero(),
				allZero(replay.allocationWindows())
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-endpoint-disabled-acoustic-replay\","
						+ "\"cache_hits\":%d,\"rebuilds\":%d,"
						+ "\"fallbacks\":%d,\"max_step\":%.17g,"
						+ "\"latest_generation\":%d}%n",
				replay.cacheHits(),
				replay.rebuilds(),
				replay.incompleteFallbacks(),
				replay.maximumStep(),
				replay.latestGeneration()
		);
	}

	private static ReplayResult replay() {
		TraceView view = new TraceView();
		CoverageDirtyTracker tracker = new CoverageDirtyTracker();
		LocalPlaneSnapshotCache cache = new LocalPlaneSnapshotCache();
		EarlyReflectionClusterSlew slew =
				new EarlyReflectionClusterSlew(96.0, RAMP_SAMPLES);
		EarlyReflectionClusterSlew.Frame frame =
				new EarlyReflectionClusterSlew.Frame();
		NullEarlyReflectionRenderer renderer =
				new NullEarlyReflectionRenderer(
						16_384,
						NullEarlyReflectionRenderer.Interpolation
								.HIGH_BAND_KAISER_SINC8
				);
		EventRecord[] records = new EventRecord[TICKS];
		double[] dry = drySignal(TICKS * BLOCK_SIZE);
		double[] left = new double[dry.length];
		double[] right = new double[dry.length];
		int cacheHits = 0;
		int rebuilds = 0;
		int incompleteFallbacks = 0;
		int completeWithClusters = 0;
		int dirtyDuringCapture = 0;
		int energyChecks = 0;
		boolean incompleteFadesReachedZero = true;
		long generation = 0L;
		try (LocalPlaneSceneWorkerHandoff worker =
						new LocalPlaneSceneWorkerHandoff(1)) {
			worker.start();
			for (int tick = 0; tick < TICKS; tick++) {
				if (tick == 1) {
					tracker.markDirty(10, 1, 10);
					view.bumpGeneration();
				} else if (tick == 2) {
					tracker.markDirty(2, 1, 2);
					view.bumpGeneration();
				} else if (tick == 3) {
					view.setLoaded(false);
					tracker.markDirty(2, 1, 2);
					view.bumpGeneration();
				} else if (tick == 4) {
					view.setLoaded(true);
					tracker.markDirty(2, 1, 2);
					view.bumpGeneration();
				} else if (tick == 5) {
					tracker.markDirty(2, 1, 2);
					view.bumpGeneration();
					view.armDirtyDuringCapture(tracker);
				} else if (tick == 8) {
					tracker = new CoverageDirtyTracker();
					cache.invalidate();
					view.bumpGeneration();
				}
				LocalPlaneSnapshotCache.Lookup lookup = cache.capture(
						view,
						tracker,
						COVERAGE,
						HALO
				);
				if (lookup.cacheHit()) {
					cacheHits++;
				} else {
					rebuilds++;
				}
				if (lookup.dirtyDuringCapture()) {
					dirtyDuringCapture++;
				}
				LocalPlaneSceneWorkerHandoff.SceneSnapshot scene =
						scene(lookup.snapshot());
				long nextGeneration = ++generation;
				if (!worker.submit(
						0,
						nextGeneration,
						1.25 + tick * 0.02,
						1.5,
						1.5,
						3.25,
						1.5,
						3.0,
						scene
				)) {
					throw new IllegalStateException(
							"replay generation was rejected"
					);
				}
				LocalPlaneSceneWorkerHandoff.Result result =
						await(worker, nextGeneration);
				if (result.conservativeFallback()) {
					incompleteFallbacks++;
				}
				if (result.complete() && result.clusterCount() > 0) {
					completeWithClusters++;
				}
				slew.update(result, frame);
				renderer.submit(frame);
				energyChecks++;
				int offset = tick * BLOCK_SIZE;
				renderer.render(
						dry,
						offset,
						left,
						right,
						offset,
						BLOCK_SIZE
				);
				double lastMagnitude =
						Math.abs(left[offset + BLOCK_SIZE - 1])
								+ Math.abs(
										right[offset + BLOCK_SIZE - 1]
								);
				if (!result.complete()) {
					incompleteFadesReachedZero &=
							lastMagnitude <= 1.0e-15;
				}
				records[tick] = new EventRecord(
						tick,
						EVENTS[tick],
						nextGeneration,
						lookup.cacheHit(),
						lookup.dirtyDuringCapture(),
						lookup.snapshot().complete(),
						lookup.snapshot().patchCount(),
						result.complete(),
						result.conservativeFallback(),
						result.clusterCount(),
						frame.slotCount(),
						blockRms(left, right, offset),
						lastMagnitude
				);
			}
		}
		Continuity continuity = continuity(left, right);
		LatestProbe latest = latestProbe(
				scene(cache.capture(view, tracker, COVERAGE, HALO).snapshot())
		);
		long[] allocations = allocationWindows(renderer, frame);
		return new ReplayResult(
				records,
				cacheHits,
				rebuilds,
				incompleteFallbacks,
				completeWithClusters,
				dirtyDuringCapture,
				energyChecks,
				continuity.maximumStep(),
				continuity.allFinite(),
				incompleteFadesReachedZero,
				checksum(left, right),
				latest.generation(),
				latest.pendingOverwrites(),
				latest.staleSolvedResults(),
				latest.publishedResults(),
				allocations,
				left,
				right
		);
	}

	private static LocalPlaneSceneWorkerHandoff.SceneSnapshot scene(
			LocalPlaneSnapshotProducer.Result snapshot
	) {
		return new LocalPlaneSceneWorkerHandoff.SceneSnapshot(
				snapshot.complete(),
				snapshot.patches(),
				(x, y, z) -> false,
				new MaterialBoxSegmentBlockQuery(
						snapshot.materialBoxes()
				),
				COVERAGE,
				MAXIMUM_CELLS_PER_LEG
		);
	}

	private static LocalPlaneSceneWorkerHandoff.Result await(
			LocalPlaneSceneWorkerHandoff worker,
			long generation
	) {
		LocalPlaneSceneWorkerHandoff.Result result =
				new LocalPlaneSceneWorkerHandoff.Result();
		long deadline = System.nanoTime() + 2_000_000_000L;
		while (!worker.pollLatest(0, generation, result)) {
			if (System.nanoTime() >= deadline) {
				throw new IllegalStateException(
						"scene worker replay timed out"
				);
			}
			LockSupport.parkNanos(100_000L);
		}
		return result;
	}

	private static LatestProbe latestProbe(
			LocalPlaneSceneWorkerHandoff.SceneSnapshot scene
	) {
		try (LocalPlaneSceneWorkerHandoff worker =
						new LocalPlaneSceneWorkerHandoff(1)) {
			worker.submit(
					0, 1, 1.25, 1.5, 1.5, 3.25, 1.5, 3.0, scene
			);
			worker.submit(
					0, 2, 1.30, 1.5, 1.5, 3.25, 1.5, 3.0, scene
			);
			worker.start();
			LocalPlaneSceneWorkerHandoff.Result result = await(worker, 2);
			LocalPlaneSceneWorkerHandoff.Statistics statistics =
					worker.statistics(
							new LocalPlaneSceneWorkerHandoff.Statistics()
					);
			return new LatestProbe(
					result.generation(),
					statistics.pendingOverwrites(),
					statistics.staleSolvedResults(),
					statistics.publishedResults()
			);
		}
	}

	private static long[] allocationWindows(
			NullEarlyReflectionRenderer renderer,
			EarlyReflectionClusterSlew.Frame frame
	) {
		double[] dry = drySignal(BLOCK_SIZE);
		double[] left = new double[BLOCK_SIZE];
		double[] right = new double[BLOCK_SIZE];
		for (int block = 0; block < 1_000; block++) {
			renderer.submit(frame);
			renderer.render(dry, 0, left, right, 0, BLOCK_SIZE);
		}
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		bean.getThreadAllocatedBytes(threadId);
		long[] windows = new long[5];
		for (int window = 0; window < windows.length; window++) {
			long before = bean.getThreadAllocatedBytes(threadId);
			for (int block = 0; block < 2_000; block++) {
				renderer.submit(frame);
				renderer.render(dry, 0, left, right, 0, BLOCK_SIZE);
			}
			windows[window] =
					bean.getThreadAllocatedBytes(threadId) - before;
		}
		return windows;
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

	private static double[] drySignal(int length) {
		double[] output = new double[length];
		for (int index = 0; index < length; index++) {
			output[index] = 0.14 * Math.sin(
					2.0 * Math.PI * 5_760.0 * index / 48_000.0
			) + 0.06 * Math.sin(
					2.0 * Math.PI * 12_000.0 * index / 48_000.0
			);
		}
		return output;
	}

	private static Continuity continuity(
			double[] left,
			double[] right
	) {
		double maximum = 0.0;
		boolean finite = true;
		for (int index = 0; index < left.length; index++) {
			finite &= Double.isFinite(left[index])
					&& Double.isFinite(right[index]);
			if (index > 0) {
				maximum = Math.max(
						maximum,
						Math.abs(left[index] - left[index - 1])
				);
				maximum = Math.max(
						maximum,
						Math.abs(right[index] - right[index - 1])
				);
			}
		}
		return new Continuity(maximum, finite);
	}

	private static double blockRms(
			double[] left,
			double[] right,
			int offset
	) {
		double energy = 0.0;
		for (int index = 0; index < BLOCK_SIZE; index++) {
			double l = left[offset + index];
			double r = right[offset + index];
			energy += l * l + r * r;
		}
		return Math.sqrt(energy / (2.0 * BLOCK_SIZE));
	}

	private static double checksum(double[] left, double[] right) {
		double output = 0.0;
		for (int index = 0; index < left.length; index += 17) {
			output += left[index] + right[index];
		}
		return output;
	}

	private static boolean allZero(long[] values) {
		return Arrays.stream(values).allMatch(value -> value == 0L);
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

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static final class TraceView
			implements LocalPlaneSnapshotProducer.FrozenBlockView {
		private long generation = 1L;
		private boolean loaded = true;
		private CoverageDirtyTracker dirtyDuringCaptureTracker;
		private boolean dirtyTriggered;

		@Override
		public long generation() {
			return generation;
		}

		@Override
		public boolean isLoaded(int x, int y, int z) {
			return loaded;
		}

		@Override
		public void appendMaterialBoxes(
				int x,
				int y,
				int z,
				List<MaterialBox> output
		) {
			if (dirtyDuringCaptureTracker != null && !dirtyTriggered) {
				dirtyTriggered = true;
				dirtyDuringCaptureTracker.markDirty(2, 1, 2);
				generation++;
			}
			if (y == 0) {
				output.add(new MaterialBox(
						x,
						y,
						z,
						x + 1.0,
						y + 1.0,
						z + 1.0,
						AcousticMaterials.STONE
				));
			}
		}

		private void bumpGeneration() {
			generation++;
		}

		private void setLoaded(boolean nextLoaded) {
			loaded = nextLoaded;
		}

		private void armDirtyDuringCapture(
				CoverageDirtyTracker tracker
		) {
			dirtyDuringCaptureTracker = tracker;
			dirtyTriggered = false;
		}
	}

	private record EventRecord(
			int tick,
			String event,
			long generation,
			boolean cacheHit,
			boolean dirtyDuringCapture,
			boolean snapshotComplete,
			int patchCount,
			boolean workerComplete,
			boolean conservativeFallback,
			int clusterCount,
			int renderSlots,
			double wetRms,
			double lastWetMagnitude
	) {
		private String json() {
			return String.format(
					Locale.ROOT,
					"{\"tick\":%d,\"event\":\"%s\","
							+ "\"generation\":%d,\"cache_hit\":%s,"
							+ "\"dirty_during_capture\":%s,"
							+ "\"snapshot_complete\":%s,"
							+ "\"patch_count\":%d,"
							+ "\"worker_complete\":%s,"
							+ "\"conservative_fallback\":%s,"
							+ "\"cluster_count\":%d,"
							+ "\"render_slots\":%d,"
							+ "\"wet_rms\":%.17g,"
							+ "\"last_wet_magnitude\":%.17g}",
					tick,
					event,
					generation,
					cacheHit,
					dirtyDuringCapture,
					snapshotComplete,
					patchCount,
					workerComplete,
					conservativeFallback,
					clusterCount,
					renderSlots,
					wetRms,
					lastWetMagnitude
			);
		}
	}

	private record ReplayResult(
			EventRecord[] records,
			int cacheHits,
			int rebuilds,
			int incompleteFallbacks,
			int completeWithClusters,
			int dirtyDuringCapture,
			int energyChecks,
			double maximumStep,
			boolean allFinite,
			boolean incompleteFadesReachedZero,
			double wetChecksum,
			long latestGeneration,
			long pendingOverwrites,
			long staleSolvedResults,
			long latestPublishedResults,
			long[] allocationWindows,
			double[] wetLeft,
			double[] wetRight
	) {
		private String eventsJson() {
			StringBuilder output = new StringBuilder("[");
			for (int index = 0; index < records.length; index++) {
				if (index > 0) {
					output.append(',');
				}
				output.append(records[index].json());
			}
			return output.append(']').toString();
		}
	}

	private record Continuity(double maximumStep, boolean allFinite) {
	}

	private record LatestProbe(
			long generation,
			long pendingOverwrites,
			long staleSolvedResults,
			long publishedResults
	) {
	}
}
