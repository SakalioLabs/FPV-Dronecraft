package com.tenicana.dronecraft.acoustics.propagation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CoverageDirtyTrackerTest {
	private static final CellCaptureBounds COVERAGE =
			new CellCaptureBounds(-1, -1, -1, 1, 1, 1);

	@Test
	void changesOutsideExactCoverageDoNotChangeToken() {
		CoverageDirtyTracker tracker = new CoverageDirtyTracker();
		assertEquals(0, tracker.token(COVERAGE));

		tracker.markDirty(2, 0, 0);
		tracker.markDirty(0, 2, 0);
		tracker.markDirty(0, 0, -2);

		assertEquals(0, tracker.token(COVERAGE));
	}

	@Test
	void insideChangesAndNegativeCoordinatesChangeToken() {
		CoverageDirtyTracker tracker = new CoverageDirtyTracker();
		long first = tracker.markDirty(-1, -1, -1);
		assertEquals(first, tracker.token(COVERAGE));

		long outside = tracker.markDirty(-2, -1, -1);
		assertEquals(first, tracker.token(COVERAGE));

		long second = tracker.markDirty(1, 1, 1);
		assertNotEquals(outside, second);
		assertEquals(second, tracker.token(COVERAGE));
	}

	@Test
	void dirtyBoundsInvalidateEveryOverlappingQuery() {
		CoverageDirtyTracker tracker = new CoverageDirtyTracker();
		CellCaptureBounds unloadedChunkSlice =
				new CellCaptureBounds(0, -1, 0, 15, 1, 15);
		tracker.markDirty(unloadedChunkSlice);

		assertNotEquals(0, tracker.token(COVERAGE));
		assertEquals(
				0,
				tracker.token(
						new CellCaptureBounds(
								-20, -1, -20,
								-16, 1, -16
						)
				)
		);
	}
}
