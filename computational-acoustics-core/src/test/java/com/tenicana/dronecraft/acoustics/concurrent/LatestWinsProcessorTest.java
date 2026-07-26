package com.tenicana.dronecraft.acoustics.concurrent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatestWinsProcessorTest {
	@Test
	void replacesPendingWorkWhileCurrentJobFinishes() throws Exception {
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		List<Integer> processed = new CopyOnWriteArrayList<>();
		try (LatestWinsProcessor<Integer, Integer> processor = new LatestWinsProcessor<>(
				"latest-wins-test",
				input -> {
					processed.add(input);
					if (input == 1) {
						firstStarted.countDown();
						await(releaseFirst);
					}
					return input * 10;
				}
		)) {
			processor.submit(1);
			assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
			processor.submit(2);
			long latestSequence = processor.submit(3);
			releaseFirst.countDown();

			LatestWinsProcessor.Result<Integer> result = waitFor(processor, latestSequence);
			assertNotNull(result);
			assertTrue(result.succeeded());
			assertEquals(30, result.value());
			assertEquals(List.of(1, 3), processed);
		}
	}

	@Test
	void capturesProcessorFailureWithoutKillingWorker() throws Exception {
		try (LatestWinsProcessor<Integer, Integer> processor = new LatestWinsProcessor<>(
				"latest-wins-failure-test",
				input -> {
					if (input < 0) {
						throw new IllegalArgumentException("negative");
					}
					return input;
				}
		)) {
			long failedSequence = processor.submit(-1);
			LatestWinsProcessor.Result<Integer> failure = waitFor(processor, failedSequence);
			assertNotNull(failure);
			assertTrue(!failure.succeeded());

			long successSequence = processor.submit(2);
			LatestWinsProcessor.Result<Integer> success = waitFor(processor, successSequence);
			assertNotNull(success);
			assertTrue(success.succeeded());
			assertEquals(2, success.value());
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(2, TimeUnit.SECONDS)) {
				throw new IllegalStateException("timed out");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private static <O> LatestWinsProcessor.Result<O> waitFor(
			LatestWinsProcessor<?, O> processor,
			long sequence
	) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
		while (System.nanoTime() < deadline) {
			LatestWinsProcessor.Result<O> result = processor.latest();
			if (result != null && result.sequence() >= sequence) {
				return result;
			}
			Thread.sleep(1L);
		}
		return null;
	}
}
