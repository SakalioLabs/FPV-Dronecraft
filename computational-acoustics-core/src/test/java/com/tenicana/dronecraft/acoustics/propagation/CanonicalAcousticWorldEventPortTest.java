package com.tenicana.dronecraft.acoustics.propagation;

import org.junit.jupiter.api.Test;

import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.BLOCK_APPLY;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.BLOCK_ROLLBACK;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.CHUNK_REPLACE;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.Outcome.ACCEPTED_DIRTY;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.Outcome.ACCEPTED_OUTSIDE_COVERAGE;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.Outcome.CONFLICTING_SEQUENCE_REJECTED;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.Outcome.DUPLICATE_IGNORED;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.Outcome.OUT_OF_ORDER_REJECTED;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.Outcome.WORLD_MISMATCH_REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalAcousticWorldEventPortTest {
	private static final CellCaptureBounds COVERAGE =
			new CellCaptureBounds(0, 0, 0, 31, 15, 31);

	@Test
	void ordersDeduplicatesAndTreatsRollbackAsDirty() {
		CanonicalAcousticWorldEventPort port =
				new CanonicalAcousticWorldEventPort();
		assertTrue(port.replaceWorld(1L));
		port.replaceActiveCoverage(
				new CellCaptureBounds[] {COVERAGE},
				1
		);
		CoverageDirtyTracker tracker = port.tracker();

		assertEquals(
				ACCEPTED_DIRTY,
				port.acceptBlock(1L, 1L, BLOCK_APPLY, 4, 2, 4)
		);
		long afterApply = tracker.token(COVERAGE);
		assertEquals(
				DUPLICATE_IGNORED,
				port.acceptBlock(1L, 1L, BLOCK_APPLY, 4, 2, 4)
		);
		assertEquals(afterApply, tracker.token(COVERAGE));
		assertEquals(
				CONFLICTING_SEQUENCE_REJECTED,
				port.acceptBlock(1L, 1L, BLOCK_APPLY, 5, 2, 4)
		);
		assertEquals(afterApply, tracker.token(COVERAGE));
		assertEquals(
				ACCEPTED_OUTSIDE_COVERAGE,
				port.acceptBlock(1L, 2L, BLOCK_APPLY, 40, 2, 40)
		);
		assertEquals(
				ACCEPTED_DIRTY,
				port.acceptBlock(1L, 3L, BLOCK_ROLLBACK, 4, 2, 4)
		);
		assertTrue(tracker.token(COVERAGE) > afterApply);
		assertEquals(
				OUT_OF_ORDER_REJECTED,
				port.acceptBlock(1L, 2L, BLOCK_APPLY, 40, 2, 40)
		);
	}

	@Test
	void chunkReplacementMarksOnlyIntersectionAndWorldSwapIsolatesState() {
		CanonicalAcousticWorldEventPort port =
				new CanonicalAcousticWorldEventPort();
		port.replaceWorld(1L);
		port.replaceActiveCoverage(
				new CellCaptureBounds[] {COVERAGE},
				1
		);
		assertEquals(
				ACCEPTED_DIRTY,
				port.acceptChunk(
						1L, 1L, CHUNK_REPLACE, 16, 16, 31, 31
				)
		);
		assertEquals(4_096L, port.diagnostics().markedCells());
		CoverageDirtyTracker first = port.tracker();

		assertTrue(port.replaceWorld(2L));
		assertNotSame(first, port.tracker());
		assertEquals(0L, port.tracker().currentRevision());
		assertEquals(
				WORLD_MISMATCH_REJECTED,
				port.acceptBlock(1L, 2L, BLOCK_APPLY, 1, 1, 1)
		);
		port.replaceActiveCoverage(
				new CellCaptureBounds[] {COVERAGE},
				1
		);
		assertEquals(
				ACCEPTED_DIRTY,
				port.acceptBlock(2L, 1L, BLOCK_APPLY, 1, 1, 1)
		);
		assertEquals(1L, port.tracker().currentRevision());
	}

	@Test
	void rejectsInvalidProtocolInputs() {
		CanonicalAcousticWorldEventPort port =
				new CanonicalAcousticWorldEventPort();
		assertThrows(
				IllegalStateException.class,
				() -> port.replaceActiveCoverage(
						new CellCaptureBounds[] {COVERAGE}, 1
				)
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> port.replaceWorld(0L)
		);
		port.replaceWorld(1L);
		assertThrows(
				IllegalArgumentException.class,
				() -> port.acceptBlock(
						1L, 0L, BLOCK_APPLY, 0, 0, 0
				)
		);
	}
}
