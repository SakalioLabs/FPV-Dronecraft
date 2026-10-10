package com.tenicana.dronecraft.acoustics.propagation;

import java.util.Objects;

/**
 * Fixed-capacity diagnostic trace for native world callbacks.
 *
 * <p>The record path performs no allocation after construction. When full,
 * the ring overwrites its oldest entry and increments {@link #overwritten()}.
 * Disabled traces are strict no-ops.
 */
public final class BoundedAcousticWorldEventTrace {
	public enum Kind {
		WORLD_REPLACE,
		BLOCK_APPLY,
		BLOCK_ROLLBACK,
		CHUNK_LOAD,
		CHUNK_REPLACE,
		CHUNK_UNLOAD
	}

	public enum Disposition {
		WORLD_REPLACED,
		ACCEPTED_DIRTY,
		ACCEPTED_OUTSIDE_COVERAGE,
		DUPLICATE_IGNORED,
		CONFLICTING_SEQUENCE_REJECTED,
		OUT_OF_ORDER_REJECTED,
		WORLD_MISMATCH_REJECTED,
		ADAPTER_WORLD_MISMATCH_REJECTED
	}

	private final boolean enabled;
	private final long[] ordinals;
	private final long[] worldEpochs;
	private final long[] sequences;
	private final long[] threadIds;
	private final Kind[] kinds;
	private final Disposition[] dispositions;
	private final int[] minimumXs;
	private final int[] minimumYs;
	private final int[] minimumZs;
	private final int[] maximumXs;
	private final int[] maximumYs;
	private final int[] maximumZs;
	private int head;
	private int size;
	private long recorded;
	private long overwritten;

	public BoundedAcousticWorldEventTrace(int capacity, boolean enabled) {
		if (capacity <= 0) {
			throw new IllegalArgumentException(
					"trace capacity must be positive"
			);
		}
		this.enabled = enabled;
		ordinals = new long[capacity];
		worldEpochs = new long[capacity];
		sequences = new long[capacity];
		threadIds = new long[capacity];
		kinds = new Kind[capacity];
		dispositions = new Disposition[capacity];
		minimumXs = new int[capacity];
		minimumYs = new int[capacity];
		minimumZs = new int[capacity];
		maximumXs = new int[capacity];
		maximumYs = new int[capacity];
		maximumZs = new int[capacity];
	}

	public synchronized void record(
			long worldEpoch,
			long sequence,
			long threadId,
			Kind kind,
			Disposition disposition,
			int minimumX,
			int minimumY,
			int minimumZ,
			int maximumX,
			int maximumY,
			int maximumZ
	) {
		if (!enabled) {
			return;
		}
		if (worldEpoch <= 0L) {
			throw new IllegalArgumentException(
					"trace world epoch must be positive"
			);
		}
		if (sequence < 0L) {
			throw new IllegalArgumentException(
					"trace sequence must not be negative"
			);
		}
		if (threadId <= 0L) {
			throw new IllegalArgumentException(
					"trace thread id must be positive"
			);
		}
		if (minimumX > maximumX
				|| minimumY > maximumY
				|| minimumZ > maximumZ) {
			throw new IllegalArgumentException("invalid trace bounds");
		}
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(disposition, "disposition");
		if (recorded == Long.MAX_VALUE) {
			throw new IllegalStateException(
					"trace ordinal exhausted"
			);
		}
		int index;
		if (size < ordinals.length) {
			index = (head + size) % ordinals.length;
			size++;
		} else {
			index = head;
			head = (head + 1) % ordinals.length;
			if (overwritten == Long.MAX_VALUE) {
				throw new IllegalStateException(
						"trace overwrite counter exhausted"
				);
			}
			overwritten++;
		}
		ordinals[index] = ++recorded;
		worldEpochs[index] = worldEpoch;
		sequences[index] = sequence;
		threadIds[index] = threadId;
		kinds[index] = kind;
		dispositions[index] = disposition;
		minimumXs[index] = minimumX;
		minimumYs[index] = minimumY;
		minimumZs[index] = minimumZ;
		maximumXs[index] = maximumX;
		maximumYs[index] = maximumY;
		maximumZs[index] = maximumZ;
	}

	public synchronized int copyChronological(Entry[] output) {
		Objects.requireNonNull(output, "output");
		if (output.length < size) {
			throw new IllegalArgumentException(
					"trace output is smaller than retained size"
			);
		}
		for (int offset = 0; offset < size; offset++) {
			Entry entry = Objects.requireNonNull(
					output[offset],
					"trace output entry"
			);
			int index = (head + offset) % ordinals.length;
			entry.set(
					ordinals[index],
					worldEpochs[index],
					sequences[index],
					threadIds[index],
					kinds[index],
					dispositions[index],
					minimumXs[index],
					minimumYs[index],
					minimumZs[index],
					maximumXs[index],
					maximumYs[index],
					maximumZs[index]
			);
		}
		return size;
	}

	public boolean enabled() {
		return enabled;
	}

	public int capacity() {
		return ordinals.length;
	}

	public synchronized int size() {
		return size;
	}

	public synchronized long recorded() {
		return recorded;
	}

	public synchronized long overwritten() {
		return overwritten;
	}

	public static final class Entry {
		private long ordinal;
		private long worldEpoch;
		private long sequence;
		private long threadId;
		private Kind kind;
		private Disposition disposition;
		private int minimumX;
		private int minimumY;
		private int minimumZ;
		private int maximumX;
		private int maximumY;
		private int maximumZ;

		public long ordinal() {
			return ordinal;
		}

		public long worldEpoch() {
			return worldEpoch;
		}

		public long sequence() {
			return sequence;
		}

		public long threadId() {
			return threadId;
		}

		public Kind kind() {
			return kind;
		}

		public Disposition disposition() {
			return disposition;
		}

		public int minimumX() {
			return minimumX;
		}

		public int minimumY() {
			return minimumY;
		}

		public int minimumZ() {
			return minimumZ;
		}

		public int maximumX() {
			return maximumX;
		}

		public int maximumY() {
			return maximumY;
		}

		public int maximumZ() {
			return maximumZ;
		}

		private void set(
				long nextOrdinal,
				long nextWorldEpoch,
				long nextSequence,
				long nextThreadId,
				Kind nextKind,
				Disposition nextDisposition,
				int nextMinimumX,
				int nextMinimumY,
				int nextMinimumZ,
				int nextMaximumX,
				int nextMaximumY,
				int nextMaximumZ
		) {
			ordinal = nextOrdinal;
			worldEpoch = nextWorldEpoch;
			sequence = nextSequence;
			threadId = nextThreadId;
			kind = nextKind;
			disposition = nextDisposition;
			minimumX = nextMinimumX;
			minimumY = nextMinimumY;
			minimumZ = nextMinimumZ;
			maximumX = nextMaximumX;
			maximumY = nextMaximumY;
			maximumZ = nextMaximumZ;
		}
	}
}
