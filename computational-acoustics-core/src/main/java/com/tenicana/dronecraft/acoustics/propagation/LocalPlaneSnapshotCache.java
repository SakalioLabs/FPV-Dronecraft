package com.tenicana.dronecraft.acoustics.propagation;

import java.util.Objects;

/**
 * Single-group cache keyed by exact coverage, halo and coverage dirty token.
 *
 * <p>The dirty tracker is authoritative for cache hits. The producer's view
 * generation still protects a rebuild from mutation during its own scan.
 */
public final class LocalPlaneSnapshotCache {
	private CellCaptureBounds coverage;
	private int halo;
	private long dirtyToken;
	private CoverageDirtyTracker trackerIdentity;
	private LocalPlaneSnapshotProducer.Result snapshot;

	public Lookup capture(
			LocalPlaneSnapshotProducer.FrozenBlockView view,
			CoverageDirtyTracker dirtyTracker,
			CellCaptureBounds requestedCoverage,
			int requestedHalo
	) {
		Objects.requireNonNull(view, "view");
		Objects.requireNonNull(dirtyTracker, "dirtyTracker");
		Objects.requireNonNull(requestedCoverage, "requestedCoverage");
		long before = dirtyTracker.token(requestedCoverage);
		if (snapshot != null
				&& trackerIdentity == dirtyTracker
				&& coverage.equals(requestedCoverage)
				&& halo == requestedHalo
				&& dirtyToken == before) {
			return new Lookup(snapshot, true, false, before);
		}
		LocalPlaneSnapshotProducer.Result captured =
				LocalPlaneSnapshotProducer.capture(
						view,
						requestedCoverage,
						requestedHalo
				);
		long after = dirtyTracker.token(requestedCoverage);
		if (before != after) {
			invalidate();
			return new Lookup(
					captured.discardGeometry(),
					false,
					true,
					after
			);
		}
		if (captured.complete()) {
			coverage = requestedCoverage;
			halo = requestedHalo;
			dirtyToken = after;
			trackerIdentity = dirtyTracker;
			snapshot = captured;
		} else {
			invalidate();
		}
		return new Lookup(captured, false, false, after);
	}

	public void invalidate() {
		coverage = null;
		trackerIdentity = null;
		snapshot = null;
		dirtyToken = 0L;
	}

	public record Lookup(
			LocalPlaneSnapshotProducer.Result snapshot,
			boolean cacheHit,
			boolean dirtyDuringCapture,
			long dirtyToken
	) {
		public Lookup {
			Objects.requireNonNull(snapshot, "snapshot");
			if (dirtyDuringCapture && snapshot.complete()) {
				throw new IllegalArgumentException(
						"dirty capture must not publish complete geometry"
				);
			}
		}
	}
}
