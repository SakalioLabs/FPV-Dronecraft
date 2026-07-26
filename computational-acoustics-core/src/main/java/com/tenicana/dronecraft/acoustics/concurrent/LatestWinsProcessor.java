package com.tenicana.dronecraft.acoustics.concurrent;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Single-worker processor with a one-item pending queue. A new submission
 * replaces pending stale work but never interrupts a job already executing.
 */
public final class LatestWinsProcessor<I, O> implements AutoCloseable {
	private final Function<I, O> processor;
	private final ExecutorService executor;
	private final AtomicLong sequence = new AtomicLong();
	private final AtomicReference<Job<I>> pending = new AtomicReference<>();
	private final AtomicReference<Result<O>> latest = new AtomicReference<>();
	private final AtomicBoolean drainScheduled = new AtomicBoolean();
	private final AtomicBoolean closed = new AtomicBoolean();

	public LatestWinsProcessor(String threadName, Function<I, O> processor) {
		if (Objects.requireNonNull(threadName, "threadName").isBlank()) {
			throw new IllegalArgumentException("threadName must not be blank");
		}
		this.processor = Objects.requireNonNull(processor, "processor");
		this.executor = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, threadName);
			thread.setDaemon(true);
			return thread;
		});
	}

	public long submit(I input) {
		if (closed.get()) {
			throw new IllegalStateException("processor is closed");
		}
		long nextSequence = sequence.incrementAndGet();
		pending.set(new Job<>(nextSequence, input));
		scheduleDrain();
		return nextSequence;
	}

	public Result<O> latest() {
		return latest.get();
	}

	private void scheduleDrain() {
		if (drainScheduled.compareAndSet(false, true)) {
			executor.execute(this::drain);
		}
	}

	private void drain() {
		try {
			Job<I> job;
			while (!closed.get() && (job = pending.getAndSet(null)) != null) {
				try {
					latest.set(Result.success(job.sequence(), processor.apply(job.input())));
				} catch (Throwable failure) {
					latest.set(Result.failure(job.sequence(), failure));
				}
			}
		} finally {
			drainScheduled.set(false);
			if (!closed.get() && pending.get() != null) {
				scheduleDrain();
			}
		}
	}

	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			pending.set(null);
			executor.shutdownNow();
		}
	}

	private record Job<I>(long sequence, I input) {
	}

	public record Result<O>(long sequence, O value, Throwable failure) {
		static <O> Result<O> success(long sequence, O value) {
			return new Result<>(sequence, value, null);
		}

		static <O> Result<O> failure(long sequence, Throwable failure) {
			return new Result<>(sequence, null, Objects.requireNonNull(failure, "failure"));
		}

		public boolean succeeded() {
			return failure == null;
		}
	}
}
