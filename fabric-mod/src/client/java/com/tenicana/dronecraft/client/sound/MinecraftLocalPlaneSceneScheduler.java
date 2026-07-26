package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.CellCaptureBounds;
import com.tenicana.dronecraft.acoustics.propagation.CoverageDirtyTracker;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneCapturePlanner;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneReflectionSolver;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSceneWorkerHandoff;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotCache;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxSegmentBlockQuery;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Opt-in research scheduler joining client-thread capture groups to the
 * engine-independent latest-generation scene worker. It does not render or
 * touch audio endpoints.
 */
final class MinecraftLocalPlaneSceneScheduler implements AutoCloseable {
	static final String ENABLE_PROPERTY =
			"fpvdrone.acoustics.localPlaneSchedulerResearch";
	private static final int MAXIMUM_SOURCES = 6;
	private static final int MAXIMUM_CELLS_PER_LEG = 192;
	private static final LocalPlaneCapturePlanner.Config CONFIG =
			new LocalPlaneCapturePlanner.Config(
					2, 1, 1, 4, 4096, 32, 16
			);
	private static final LocalPlaneSceneWorkerHandoff.SceneSnapshot
			INCOMPLETE = new LocalPlaneSceneWorkerHandoff.SceneSnapshot(
					false,
					List.of(),
					(x, y, z) -> false,
					LocalPlaneReflectionSolver.SegmentBlockQuery.NONE,
					LocalPlaneReflectionSolver.CellCoverageQuery.ALL,
					MAXIMUM_CELLS_PER_LEG
			);

	private final boolean enabled;
	private final LocalPlaneCapturePlanner.Workspace plan =
			new LocalPlaneCapturePlanner.Workspace();
	private final LocalPlaneSnapshotCache[] caches =
			new LocalPlaneSnapshotCache[CONFIG.maximumGroups()];
	private final LocalPlaneSceneWorkerHandoff.SceneSnapshot[] groupScenes =
			new LocalPlaneSceneWorkerHandoff.SceneSnapshot[
					CONFIG.maximumGroups()
			];
	private final CellCaptureBounds[] activeCoverage =
			new CellCaptureBounds[CONFIG.maximumGroups()];
	private final LocalPlaneSceneWorkerHandoff.Result[] result =
			new LocalPlaneSceneWorkerHandoff.Result[MAXIMUM_SOURCES];
	private final LocalPlaneSceneWorkerHandoff handoff;
	private ClientLevel activeLevel;
	private long generation;
	private long submitted;
	private long polled;
	private long completeCaptures;
	private long incompleteCaptures;
	private long cacheHits;
	private long rebuilds;

	MinecraftLocalPlaneSceneScheduler() {
		enabled = Boolean.getBoolean(ENABLE_PROPERTY);
		for (int index = 0; index < caches.length; index++) {
			caches[index] = new LocalPlaneSnapshotCache();
		}
		for (int index = 0; index < result.length; index++) {
			result[index] = new LocalPlaneSceneWorkerHandoff.Result();
		}
		handoff = new LocalPlaneSceneWorkerHandoff(MAXIMUM_SOURCES);
		if (enabled) {
			handoff.start();
		}
	}

	boolean enabled() {
		return enabled;
	}

	void tick(
			ClientLevel level,
			AcousticVector listener,
			List<Source> inputSources
	) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(listener, "listener");
		Objects.requireNonNull(inputSources, "inputSources");
		if (!enabled) {
			return;
		}
		if (level != activeLevel) {
			activeLevel = level;
			for (LocalPlaneSnapshotCache cache : caches) {
				cache.invalidate();
			}
		}
		List<Source> sources = new ArrayList<>(inputSources);
		sources.sort(
				Comparator.comparingInt(Source::entityId)
						.thenComparing(Source::entityUuid)
		);
		if (sources.size() > MAXIMUM_SOURCES) {
			sources = new ArrayList<>(
					sources.subList(0, MAXIMUM_SOURCES)
			);
		}
		int sourceCount = sources.size();
		int[] sourceX = new int[sourceCount];
		int[] sourceY = new int[sourceCount];
		int[] sourceZ = new int[sourceCount];
		for (int source = 0; source < sourceCount; source++) {
			AcousticVector position = sources.get(source).position();
			sourceX[source] = floor(position.x());
			sourceY[source] = floor(position.y());
			sourceZ[source] = floor(position.z());
		}
		LocalPlaneCapturePlanner.plan(
				floor(listener.x()),
				floor(listener.y()),
				floor(listener.z()),
				sourceX,
				sourceY,
				sourceZ,
				sourceCount,
				CONFIG,
				plan
		);
		for (int group = 0; group < plan.groupCount(); group++) {
			activeCoverage[group] = plan.bounds(group);
		}
		MinecraftAcousticWorldDirtyTracker.replaceActiveCoverage(
				level,
				activeCoverage,
				plan.groupCount()
		);
		CoverageDirtyTracker dirtyTracker =
				MinecraftAcousticWorldDirtyTracker.tracker(level);
		long worldGeneration = Math.max(0L, level.getGameTime());
		LocalPlaneSnapshotProducer.FrozenBlockView view =
				MinecraftLocalPlaneSnapshot.frozenView(
						level,
						worldGeneration
				);
		for (int group = 0; group < plan.groupCount(); group++) {
			CellCaptureBounds bounds = activeCoverage[group];
			LocalPlaneSnapshotCache.Lookup lookup =
					caches[group].capture(
							view,
							dirtyTracker,
							bounds,
							CONFIG.halo()
					);
			if (lookup.cacheHit()) {
				cacheHits++;
			} else {
				rebuilds++;
			}
			LocalPlaneSnapshotProducer.Result snapshot =
					lookup.snapshot();
			if (snapshot.complete()) {
				completeCaptures++;
			} else {
				incompleteCaptures++;
			}
			groupScenes[group] = sceneSnapshot(snapshot, bounds);
		}
		long nextGeneration = ++generation;
		for (int source = 0; source < sourceCount; source++) {
			poll(source);
			Source current = sources.get(source);
			int group = plan.sourceGroup(source);
			LocalPlaneSceneWorkerHandoff.SceneSnapshot scene =
					group < 0 ? INCOMPLETE : groupScenes[group];
			if (handoff.submit(
					source,
					nextGeneration,
					current.position().x(),
					current.position().y(),
					current.position().z(),
					listener.x(),
					listener.y(),
					listener.z(),
					scene
			)) {
				submitted++;
			}
		}
	}

	private void poll(int source) {
		if (handoff.pollLatest(source, 1L, result[source])) {
			polled++;
		}
	}

	Diagnostics diagnostics() {
		return new Diagnostics(
				enabled,
				generation,
				submitted,
				polled,
				completeCaptures,
				incompleteCaptures,
				cacheHits,
				rebuilds
		);
	}

	private static LocalPlaneSceneWorkerHandoff.SceneSnapshot
	sceneSnapshot(
			LocalPlaneSnapshotProducer.Result snapshot,
			CellCaptureBounds bounds
	) {
		return new LocalPlaneSceneWorkerHandoff.SceneSnapshot(
				snapshot.complete(),
				snapshot.patches(),
				(x, y, z) -> false,
				new MaterialBoxSegmentBlockQuery(
						snapshot.materialBoxes()
				),
				(x, y, z) -> contains(bounds, x, y, z),
				MAXIMUM_CELLS_PER_LEG
		);
	}

	private static boolean contains(
			CellCaptureBounds bounds,
			int x,
			int y,
			int z
	) {
		return x >= bounds.minimumX() && x <= bounds.maximumX()
				&& y >= bounds.minimumY() && y <= bounds.maximumY()
				&& z >= bounds.minimumZ() && z <= bounds.maximumZ();
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}

	@Override
	public void close() {
		handoff.close();
	}

	record Source(int entityId, UUID entityUuid, AcousticVector position) {
		Source {
			Objects.requireNonNull(entityUuid, "entityUuid");
			Objects.requireNonNull(position, "position");
		}
	}

	record Diagnostics(
			boolean enabled,
			long generation,
			long submitted,
			long polled,
			long completeCaptures,
			long incompleteCaptures,
			long cacheHits,
			long rebuilds
	) {
	}
}
