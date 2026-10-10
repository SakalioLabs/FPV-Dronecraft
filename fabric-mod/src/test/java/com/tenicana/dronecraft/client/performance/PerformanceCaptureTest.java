package com.tenicana.dronecraft.client.performance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PerformanceCaptureTest {
	@Test
	void ringWrapRetainsChronologicalRowsAndLifetimeCounters() {
		PerformanceCapture capture = new PerformanceCapture(1, 3, 3);
		for (int i = 1; i <= 5; i++) {
			frame(capture, i, i * 100L, 10);
			capture.serverTick(i * 100L, i * 100L + 1, false);
		}
		capture.seal(1_000);

		PerformanceCapture.Snapshot snapshot = capture.snapshot();
		assertEquals(5, snapshot.totalFrames());
		assertEquals(5, snapshot.totalEvents());
		assertEquals(3, snapshot.frames()[PerformanceCapture.TOKEN].length);
		assertArrayEquals(new long[] { 3, 4, 5 }, snapshot.frames()[PerformanceCapture.TOKEN]);
		assertEquals(3, snapshot.events()[0].length);
		assertArrayEquals(new long[] { 300, 400, 500 }, snapshot.events()[1]);
	}

	@Test
	void frameIntervalAndWorkComponentsRemainSeparate() {
		PerformanceCapture capture = new PerformanceCapture(1, 4, 4);
		capture.beginFrame(1, 100, 0, 7, 0, 0);
		capture.finishFrame(1, 40, true);
		capture.beginFrame(2, 200, 0, 7, 0, 0);
		capture.render(2, 13, true);
		capture.render(2, 99, false);
		capture.swap(2, 7);
		capture.limit(2, 3);
		capture.clientTick(2, 5);
		capture.clientTick(2, 8);
		capture.finishFrame(2, 60, true);
		capture.seal(300);

		long[][] frames = capture.snapshot().frames();
		assertEquals(-1, frames[PerformanceCapture.INTERVAL][0]);
		assertEquals(100, frames[PerformanceCapture.INTERVAL][1]);
		assertEquals(60, frames[PerformanceCapture.TOTAL][1]);
		assertEquals(13, frames[PerformanceCapture.RENDER][1]);
		assertEquals(7, frames[PerformanceCapture.SWAP][1]);
		assertEquals(3, frames[PerformanceCapture.LIMIT][1]);
		assertEquals(2, frames[PerformanceCapture.CLIENT_TICKS][1]);
		assertEquals(13, frames[PerformanceCapture.CLIENT_TICK_SUM][1]);
		assertEquals(8, frames[PerformanceCapture.CLIENT_TICK_MAX][1]);
	}

	@Test
	void incompleteFramesAreDiscardedAndCounted() {
		PerformanceCapture capture = new PerformanceCapture(1, 3, 3);
		capture.beginFrame(1, 100, 0, 1, 0, 0);
		capture.finishFrame(1, 10, false);
		capture.beginFrame(2, 200, 0, 1, 0, 0);
		capture.seal(300);

		PerformanceCapture.Snapshot snapshot = capture.snapshot();
		assertEquals(0, snapshot.frames()[PerformanceCapture.TOKEN].length);
		assertEquals(0, snapshot.totalFrames());
		assertEquals(2, snapshot.incompleteFrames());
	}

	@Test
	void gpuResultsMatchOnlyRetainedFrameTokens() {
		PerformanceCapture capture = new PerformanceCapture(1, 2, 4);
		frame(capture, 11, 100, 10);
		frame(capture, 12, 200, 10);
		frame(capture, 13, 300, 10);
		capture.gpu(11, 5); // Row has already been overwritten.
		capture.gpu(12, 8); // Delayed result still belongs to a retained row.
		capture.seal(400);

		PerformanceCapture.Snapshot snapshot = capture.snapshot();
		assertEquals(1, snapshot.gpuSkipped());
		assertArrayEquals(new long[] { 12, 13 }, snapshot.frames()[PerformanceCapture.TOKEN]);
		assertArrayEquals(new long[] { 8, -1 }, snapshot.frames()[PerformanceCapture.GPU]);
	}

	@Test
	void sealingRejectsLateWritesAndExportIsDetached() {
		PerformanceCapture capture = new PerformanceCapture(1, 3, 3);
		frame(capture, 1, 100, 10);
		capture.mark(110, "before seal");
		capture.seal(200);
		PerformanceCapture.Snapshot snapshot = capture.snapshot();
		capture.beginFrame(2, 210, 0, 1, 0, 0);
		capture.serverTick(220, 230, false);
		capture.mark(240, "after seal");
		capture.gpu(1, 99);

		assertEquals(1, snapshot.totalFrames());
		assertEquals(1, snapshot.totalEvents());
		assertArrayEquals(new long[] { -1 }, snapshot.frames()[PerformanceCapture.GPU]);
		assertEquals("before seal", snapshot.markers().getFirst());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.markers().add("mutation"));
		snapshot.frames()[PerformanceCapture.TOTAL][0] = 999;
		assertEquals(10, capture.snapshot().frames()[PerformanceCapture.TOTAL][0]);
	}

	@Test
	void saveThatBeginsAndFinishesInsideFrameStillMarksOverlap() {
		PerformanceCapture capture = new PerformanceCapture(1, 3, 4);
		capture.beginFrame(1, 100, 0, 1, 0, 0);
		capture.saveStarted(110, false, false, true);
		capture.saveFinished(110, 115, 1);
		capture.finishFrame(1, 20, true);
		capture.seal(200);

		assertEquals(1, capture.snapshot().frames()[PerformanceCapture.SAVE_OVERLAP][0]);
	}

	@Test
	void pausedAndActiveServerTicksHaveDistinctEventKinds() {
		PerformanceCapture capture = new PerformanceCapture(1, 3, 4);
		capture.serverTick(100, 120, false);
		capture.serverTick(200, 230, true);
		capture.seal(300);

		long[][] events = capture.snapshot().events();
		assertArrayEquals(new long[] { PerformanceCapture.SERVER_TICK, PerformanceCapture.PAUSED_SERVER_TICK }, events[0]);
		assertArrayEquals(new long[] { 20, 30 }, events[2]);
	}

	@Test
	void poseAgeBelongsToCurrentEntityAndResetsOnEntityChange() {
		PerformanceCapture capture = new PerformanceCapture(1, 4, 4);
		capture.beginFrame(1, 100, 0, 7, 0, 0);
		capture.pose(7, false, 90, 0);
		capture.finishFrame(1, 10, true);
		capture.beginFrame(2, 200, 0, 8, 0, 0);
		capture.finishFrame(2, 10, true);
		capture.beginFrame(3, 300, 0, 8, 0, 0);
		capture.pose(8, true, 280, 0);
		capture.finishFrame(3, 10, true);
		capture.seal(400);

		assertArrayEquals(new long[] { 20, -1, 30 }, capture.snapshot().frames()[PerformanceCapture.POSE_AGE]);
	}

	@Test
	void statisticsExcludeMissingAndFirstIntervalSamples() {
		PerformanceCapture.Statistics stats = PerformanceCapture.statistics(
				new double[] { 0, -1, 10, 20, 30, Double.NaN }, 15, true);
		assertEquals(3, stats.samples());
		assertEquals(20, stats.p50Ms());
		assertEquals(30, stats.p95Ms());
		assertEquals(30, stats.p99Ms());
		assertEquals(30, stats.maxMs());
		assertEquals(2, stats.overBudget());

		PerformanceCapture capture = new PerformanceCapture(1, 3, 3);
		frame(capture, 1, 100, 10);
		frame(capture, 2, 200, 10);
		capture.seal(300);
		PerformanceCapture.Live live = capture.live(3, 0.00005);
		assertEquals(1, live.intervals().samples());
		assertEquals(0.0001, live.intervals().meanMs(), 1e-12);
	}

	private static void frame(PerformanceCapture capture, long token, long start, long elapsed) {
		capture.beginFrame(token, start, 0, 1, 0, 0);
		capture.finishFrame(token, elapsed, true);
	}
}
