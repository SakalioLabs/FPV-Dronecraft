package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.LocalPlaneSnapshotProducer.FrozenBlockView;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPlaneSnapshotCacheTest {
	private static final CellCaptureBounds COVERAGE =
			new CellCaptureBounds(-1, -1, -1, 1, 1, 1);

	@Test
	void outsideDirtyReusesAndInsideDirtyRebuilds() {
		CoverageDirtyTracker tracker = new CoverageDirtyTracker();
		LocalPlaneSnapshotCache cache = new LocalPlaneSnapshotCache();
		FrozenBlockView view = new FloorView();

		LocalPlaneSnapshotCache.Lookup first =
				cache.capture(view, tracker, COVERAGE, 1);
		assertTrue(first.snapshot().complete());
		assertFalse(first.cacheHit());

		tracker.markDirty(2, 0, 0);
		LocalPlaneSnapshotCache.Lookup outside =
				cache.capture(view, tracker, COVERAGE, 1);
		assertTrue(outside.cacheHit());
		assertSame(first.snapshot(), outside.snapshot());

		tracker.markDirty(0, 0, 0);
		LocalPlaneSnapshotCache.Lookup inside =
				cache.capture(view, tracker, COVERAGE, 1);
		assertFalse(inside.cacheHit());
		assertTrue(inside.snapshot().complete());
	}

	@Test
	void dirtyDuringCaptureDiscardsAllGeometry() {
		CoverageDirtyTracker tracker = new CoverageDirtyTracker();
		LocalPlaneSnapshotCache cache = new LocalPlaneSnapshotCache();
		FrozenBlockView view = new FloorView() {
			private boolean dirtied;

			@Override
			public void appendMaterialBoxes(
					int x,
					int y,
					int z,
					List<MaterialBox> output
			) {
				super.appendMaterialBoxes(x, y, z, output);
				if (!dirtied) {
					dirtied = true;
					tracker.markDirty(0, 0, 0);
				}
			}
		};

		LocalPlaneSnapshotCache.Lookup lookup =
				cache.capture(view, tracker, COVERAGE, 1);

		assertTrue(lookup.dirtyDuringCapture());
		assertFalse(lookup.snapshot().complete());
		assertTrue(lookup.snapshot().materialBoxes().isEmpty());
		assertTrue(lookup.snapshot().patches().isEmpty());
	}

	@Test
	void incompleteSnapshotsAreNeverCached() {
		CoverageDirtyTracker tracker = new CoverageDirtyTracker();
		LocalPlaneSnapshotCache cache = new LocalPlaneSnapshotCache();
		FrozenBlockView unloaded = new FloorView() {
			@Override
			public boolean isLoaded(int x, int y, int z) {
				return false;
			}
		};

		LocalPlaneSnapshotCache.Lookup first =
				cache.capture(unloaded, tracker, COVERAGE, 1);
		LocalPlaneSnapshotCache.Lookup second =
				cache.capture(unloaded, tracker, COVERAGE, 1);

		assertFalse(first.snapshot().complete());
		assertFalse(second.snapshot().complete());
		assertFalse(first.cacheHit());
		assertFalse(second.cacheHit());
	}

	@Test
	void replacingWorldTrackerInvalidatesSameBoundsAndToken() {
		LocalPlaneSnapshotCache cache = new LocalPlaneSnapshotCache();
		FrozenBlockView view = new FloorView();
		CoverageDirtyTracker firstTracker = new CoverageDirtyTracker();
		CoverageDirtyTracker nextWorldTracker =
				new CoverageDirtyTracker();

		var first = cache.capture(
				view, firstTracker, COVERAGE, 1
		);
		var nextWorld = cache.capture(
				view, nextWorldTracker, COVERAGE, 1
		);

		assertFalse(first.cacheHit());
		assertFalse(nextWorld.cacheHit());
		assertTrue(nextWorld.snapshot().complete());
	}

	private static class FloorView implements FrozenBlockView {
		@Override
		public long generation() {
			return 1;
		}

		@Override
		public boolean isLoaded(int x, int y, int z) {
			return true;
		}

		@Override
		public void appendMaterialBoxes(
				int x,
				int y,
				int z,
				List<MaterialBox> output
		) {
			if (y == 0) {
				output.add(new MaterialBox(
						x, y, z,
						x + 1, y + 1, z + 1,
						AcousticMaterials.STONE
				));
			}
		}
	}
}
