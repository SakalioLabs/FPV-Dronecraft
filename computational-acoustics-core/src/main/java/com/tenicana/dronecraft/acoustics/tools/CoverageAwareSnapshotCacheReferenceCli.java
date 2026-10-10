package com.tenicana.dronecraft.acoustics.tools;

import com.sun.management.ThreadMXBean;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.CellCaptureBounds;
import com.tenicana.dronecraft.acoustics.propagation.CoverageDirtyTracker;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneCapturePlanner;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotCache;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer.FrozenBlockView;
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

/** Emits D121f exact-coverage cache and moving-trajectory evidence. */
public final class CoverageAwareSnapshotCacheReferenceCli {
	private static final int[] SOURCE_COUNTS = {1, 4, 8, 16};
	private static final int FRAMES = 256;
	private static final CellCaptureBounds FIXTURE_BOUNDS =
			new CellCaptureBounds(-1, -1, -1, 1, 1, 1);
	private static final LocalPlaneCapturePlanner.Config CONFIG =
			new LocalPlaneCapturePlanner.Config(
					2, 1, 1, 4, 4096, 32, 16
			);

	private CoverageAwareSnapshotCacheReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121f-cache-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		Trajectory[] trajectories =
				new Trajectory[SOURCE_COUNTS.length];
		boolean latencyGate = true;
		for (int index = 0; index < SOURCE_COUNTS.length; index++) {
			trajectories[index] = trajectory(SOURCE_COUNTS[index]);
			latencyGate &= trajectories[index].p99Nanos()
					<= 10_000_000.0;
		}
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-coverage-aware-snapshot-cache-reference",
				  "source_contract_sha256": "%s",
				  "fixtures": %s,
				  "trajectories": %s,
				  "gates": {
				    "all_source_counts_measured": true,
				    "all_trajectories_have_hits": %s,
				    "all_trajectories_have_rebuilds": %s,
				    "complete_frame_p99_below_10_ms": %s
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "minecraft_client_started": false,
				  "client_level_read": false,
				  "cuda_executed": false,
				  "minecraft_integration_enabled": false,
				  "trajectory_cache_measured": true,
				  "client_level_snapshot_producer_measured": false,
				  "live_early_renderer_enabled": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				fixturesJson(),
				trajectoriesJson(trajectories),
				Arrays.stream(trajectories)
						.allMatch(value -> value.hits() > 0),
				Arrays.stream(trajectories)
						.allMatch(value -> value.rebuilds() > 0),
				latencyGate
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				"{\"status\":\"valid-coverage-aware-snapshot-cache-reference\","
						+ "\"p99_16_ms\":%.17g}%n",
				trajectories[3].p99Nanos() / 1_000_000.0
		);
	}

	private static String fixturesJson() {
		CoverageDirtyTracker tracker = new CoverageDirtyTracker();
		LocalPlaneSnapshotCache cache = new LocalPlaneSnapshotCache();
		FloorView view = new FloorView();
		var first = cache.capture(view, tracker, FIXTURE_BOUNDS, 1);
		tracker.markDirty(2, 0, 0);
		var outside = cache.capture(view, tracker, FIXTURE_BOUNDS, 1);
		tracker.markDirty(0, 0, 0);
		var inside = cache.capture(view, tracker, FIXTURE_BOUNDS, 1);
		var moved = cache.capture(
				view,
				tracker,
				new CellCaptureBounds(0, -1, -1, 2, 1, 1),
				1
		);

		CoverageDirtyTracker concurrentTracker =
				new CoverageDirtyTracker();
		LocalPlaneSnapshotCache concurrentCache =
				new LocalPlaneSnapshotCache();
		FloorView concurrentView = new FloorView() {
			private boolean dirtied;

			@Override
			public void appendMaterialBoxes(
					int x, int y, int z, List<MaterialBox> output
			) {
				super.appendMaterialBoxes(x, y, z, output);
				if (!dirtied) {
					dirtied = true;
					concurrentTracker.markDirty(0, 0, 0);
				}
			}
		};
		var concurrent = concurrentCache.capture(
				concurrentView, concurrentTracker, FIXTURE_BOUNDS, 1
		);

		CoverageDirtyTracker unloadTracker =
				new CoverageDirtyTracker();
		long beforeUnload = unloadTracker.token(FIXTURE_BOUNDS);
		unloadTracker.markDirty(
				new CellCaptureBounds(0, -1, 0, 15, 1, 15)
		);
		long afterUnload = unloadTracker.token(FIXTURE_BOUNDS);
		FloorView unloadedView = new FloorView();
		unloadedView.loaded = false;
		LocalPlaneSnapshotCache unloadedCache =
				new LocalPlaneSnapshotCache();
		var unloadedFirst = unloadedCache.capture(
				unloadedView, unloadTracker, FIXTURE_BOUNDS, 1
		);
		var unloadedSecond = unloadedCache.capture(
				unloadedView, unloadTracker, FIXTURE_BOUNDS, 1
		);
		return String.format(
				Locale.ROOT,
				"{\"first_complete\":%s,\"outside_cache_hit\":%s,"
						+ "\"outside_same_instance\":%s,"
						+ "\"inside_rebuilt\":%s,"
						+ "\"moved_coverage_rebuilt\":%s,"
						+ "\"dirty_during_capture\":%s,"
						+ "\"dirty_capture_complete\":%s,"
						+ "\"dirty_capture_published_boxes\":%d,"
						+ "\"bulk_unload_token_before\":%d,"
						+ "\"bulk_unload_token_after\":%d,"
						+ "\"unloaded_first_hit\":%s,"
						+ "\"unloaded_second_hit\":%s,"
						+ "\"unloaded_complete\":%s}",
				first.snapshot().complete(),
				outside.cacheHit(),
				first.snapshot() == outside.snapshot(),
				!inside.cacheHit() && inside.snapshot().complete(),
				!moved.cacheHit() && moved.snapshot().complete(),
				concurrent.dirtyDuringCapture(),
				concurrent.snapshot().complete(),
				concurrent.snapshot().materialBoxes().size(),
				beforeUnload,
				afterUnload,
				unloadedFirst.cacheHit(),
				unloadedSecond.cacheHit(),
				unloadedSecond.snapshot().complete()
		);
	}

	private static Trajectory trajectory(int sourceCount) {
		CoverageDirtyTracker tracker = new CoverageDirtyTracker();
		LocalPlaneSnapshotCache[] caches =
				new LocalPlaneSnapshotCache[CONFIG.maximumGroups()];
		Arrays.setAll(caches, ignored -> new LocalPlaneSnapshotCache());
		LocalPlaneCapturePlanner.Workspace plan =
				new LocalPlaneCapturePlanner.Workspace();
		FloorView view = new FloorView();
		CellCaptureBounds[] previous =
				new CellCaptureBounds[CONFIG.maximumGroups()];
		long[] elapsed = new long[FRAMES];
		ThreadMXBean bean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		long allocatedBefore = bean.getThreadAllocatedBytes(threadId);
		int hits = 0;
		int rebuilds = 0;
		int fallbacks = 0;
		int outsideDirty = 0;
		int insideDirty = 0;
		int boundsChanges = 0;
		int loadStateEvents = 0;
		for (int frame = 0; frame < FRAMES; frame++) {
			int[] sourceX = new int[sourceCount];
			int[] sourceY = new int[sourceCount];
			int[] sourceZ = new int[sourceCount];
			Arrays.fill(sourceY, 2);
			for (int source = 0; source < sourceCount; source++) {
				sourceX[source] = source % 5 - 2 + frame / 64;
				sourceZ[source] = source / 5 - 1;
			}
			LocalPlaneCapturePlanner.plan(
					0, 2, 0,
					sourceX, sourceY, sourceZ, sourceCount,
					CONFIG, plan
			);
			if (frame % 7 == 3) {
				tracker.markDirty(1000 + frame, 0, 1000);
				outsideDirty++;
			}
			if (frame % 11 == 5 && plan.groupCount() > 0) {
				CellCaptureBounds bounds = plan.bounds(0);
				tracker.markDirty(
						bounds.minimumX(),
						bounds.minimumY(),
						bounds.minimumZ()
				);
				insideDirty++;
			}
			if (plan.groupCount() > 0 && frame % 53 == 17) {
				view.loaded = false;
				tracker.markDirty(plan.bounds(0));
				loadStateEvents++;
			} else if (plan.groupCount() > 0 && frame % 53 == 18) {
				view.loaded = true;
				tracker.markDirty(plan.bounds(0));
				loadStateEvents++;
			}
			long started = System.nanoTime();
			for (int group = 0; group < plan.groupCount(); group++) {
				CellCaptureBounds bounds = plan.bounds(group);
				if (previous[group] != null
						&& !previous[group].equals(bounds)) {
					boundsChanges++;
				}
				previous[group] = bounds;
				var lookup = caches[group].capture(
						view, tracker, bounds, 1
				);
				if (lookup.cacheHit()) {
					hits++;
				} else {
					rebuilds++;
				}
				if (!lookup.snapshot().complete()) {
					fallbacks++;
				}
			}
			elapsed[frame] = System.nanoTime() - started;
		}
		long allocated =
				bean.getThreadAllocatedBytes(threadId) - allocatedBefore;
		long[] sorted = elapsed.clone();
		Arrays.sort(sorted);
		return new Trajectory(
				sourceCount, FRAMES, hits, rebuilds, fallbacks,
				outsideDirty, insideDirty, boundsChanges,
				loadStateEvents,
				elapsed,
				percentile(sorted, 0.50),
				percentile(sorted, 0.99),
				allocated / (double) FRAMES
		);
	}

	private static String trajectoriesJson(Trajectory[] trajectories) {
		StringBuilder json = new StringBuilder("[");
		for (int index = 0; index < trajectories.length; index++) {
			if (index > 0) {
				json.append(',');
			}
			Trajectory value = trajectories[index];
			json.append(String.format(
					Locale.ROOT,
					"{\"source_count\":%d,\"frames\":%d,\"cache_hits\":%d,"
							+ "\"rebuilds\":%d,\"fallbacks\":%d,"
							+ "\"outside_dirty_events\":%d,"
							+ "\"inside_dirty_events\":%d,"
							+ "\"bounds_changes\":%d,"
							+ "\"load_state_events\":%d,"
							+ "\"elapsed_ns\":%s,"
							+ "\"p50_ns\":%.17g,\"p99_ns\":%.17g,"
							+ "\"allocated_bytes_per_frame\":%.17g}",
					value.sourceCount(), value.frames(), value.hits(),
					value.rebuilds(), value.fallbacks(),
					value.outsideDirtyEvents(),
					value.insideDirtyEvents(), value.boundsChanges(),
					value.loadStateEvents(),
					longArray(value.elapsedNanos()),
					value.p50Nanos(), value.p99Nanos(),
					value.allocatedBytesPerFrame()
			));
		}
		return json.append(']').toString();
	}

	private static long percentile(long[] sorted, double quantile) {
		return sorted[(int) Math.ceil(quantile * sorted.length) - 1];
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

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static class FloorView implements FrozenBlockView {
		private boolean loaded = true;

		@Override
		public long generation() {
			return 1;
		}

		@Override
		public boolean isLoaded(int x, int y, int z) {
			return loaded;
		}

		@Override
		public void appendMaterialBoxes(
				int x, int y, int z, List<MaterialBox> output
		) {
			if (y == 0) {
				output.add(new MaterialBox(
						x, y, z, x + 1, y + 1, z + 1,
						AcousticMaterials.STONE
				));
			}
		}
	}

	private record Trajectory(
			int sourceCount,
			int frames,
			int hits,
			int rebuilds,
			int fallbacks,
			int outsideDirtyEvents,
			int insideDirtyEvents,
			int boundsChanges,
			int loadStateEvents,
			long[] elapsedNanos,
			double p50Nanos,
			double p99Nanos,
			double allocatedBytesPerFrame
	) {
	}
}
