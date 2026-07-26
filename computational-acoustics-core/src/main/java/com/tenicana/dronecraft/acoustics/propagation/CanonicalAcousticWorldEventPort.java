package com.tenicana.dronecraft.acoustics.propagation;

import java.util.Objects;

/**
 * Allocation-bounded, engine-independent world-event port for acoustic
 * snapshot invalidation.
 *
 * <p>Events are strictly sequenced within a monotonically increasing world
 * epoch. Only the last accepted event signature is retained: an immediate
 * exact duplicate is ignored, a conflicting duplicate is rejected, and every
 * older sequence is rejected. A prediction rollback is intentionally another
 * dirty event; this port never attempts to reverse dirty history.
 */
public final class CanonicalAcousticWorldEventPort {
	public static final int MAXIMUM_ACTIVE_COVERAGES = 4;

	public enum EventType {
		BLOCK_APPLY,
		BLOCK_ROLLBACK,
		CHUNK_LOAD,
		CHUNK_REPLACE,
		CHUNK_UNLOAD
	}

	public enum Outcome {
		ACCEPTED_DIRTY,
		ACCEPTED_OUTSIDE_COVERAGE,
		DUPLICATE_IGNORED,
		CONFLICTING_SEQUENCE_REJECTED,
		OUT_OF_ORDER_REJECTED,
		WORLD_MISMATCH_REJECTED
	}

	private final CellCaptureBounds[] active =
			new CellCaptureBounds[MAXIMUM_ACTIVE_COVERAGES];
	private CoverageDirtyTracker tracker = new CoverageDirtyTracker();
	private long worldEpoch;
	private long lastSequence;
	private EventType lastType;
	private int lastMinimumX;
	private int lastMinimumY;
	private int lastMinimumZ;
	private int lastMaximumX;
	private int lastMaximumY;
	private int lastMaximumZ;
	private int activeCount;
	private long worldReplacements;
	private long acceptedEvents;
	private long dirtyEvents;
	private long duplicateEvents;
	private long conflictingSequenceEvents;
	private long outOfOrderEvents;
	private long worldMismatchEvents;
	private long markedCells;

	public synchronized boolean replaceWorld(long nextWorldEpoch) {
		if (nextWorldEpoch <= 0L) {
			throw new IllegalArgumentException("world epoch must be positive");
		}
		if (nextWorldEpoch <= worldEpoch) {
			return false;
		}
		worldEpoch = nextWorldEpoch;
		lastSequence = 0L;
		lastType = null;
		tracker = new CoverageDirtyTracker();
		clearCoverage();
		worldReplacements++;
		return true;
	}

	public synchronized void replaceActiveCoverage(
			CellCaptureBounds[] coverage,
			int count
	) {
		Objects.requireNonNull(coverage, "coverage");
		if (worldEpoch == 0L) {
			throw new IllegalStateException("world epoch is not initialized");
		}
		if (count < 0
				|| count > MAXIMUM_ACTIVE_COVERAGES
				|| count > coverage.length) {
			throw new IllegalArgumentException(
					"active coverage count out of range"
			);
		}
		for (int index = 0; index < count; index++) {
			active[index] = Objects.requireNonNull(
					coverage[index],
					"active coverage"
			);
		}
		for (int index = count; index < activeCount; index++) {
			active[index] = null;
		}
		activeCount = count;
	}

	public synchronized Outcome acceptBlock(
			long eventWorldEpoch,
			long sequence,
			EventType type,
			int x,
			int y,
			int z
	) {
		if (type != EventType.BLOCK_APPLY
				&& type != EventType.BLOCK_ROLLBACK) {
			throw new IllegalArgumentException(
					"block event type required"
			);
		}
		Outcome ordering = inspectOrdering(
				eventWorldEpoch,
				sequence,
				type,
				x,
				y,
				z,
				x,
				y,
				z
		);
		if (ordering != null) {
			return ordering;
		}
		remember(sequence, type, x, y, z, x, y, z);
		acceptedEvents++;
		for (int index = 0; index < activeCount; index++) {
			if (contains(active[index], x, y, z)) {
				tracker.markDirty(x, y, z);
				dirtyEvents++;
				markedCells++;
				return Outcome.ACCEPTED_DIRTY;
			}
		}
		return Outcome.ACCEPTED_OUTSIDE_COVERAGE;
	}

	public synchronized Outcome acceptChunk(
			long eventWorldEpoch,
			long sequence,
			EventType type,
			int minimumX,
			int minimumZ,
			int maximumX,
			int maximumZ
	) {
		if (type != EventType.CHUNK_LOAD
				&& type != EventType.CHUNK_REPLACE
				&& type != EventType.CHUNK_UNLOAD) {
			throw new IllegalArgumentException(
					"chunk event type required"
			);
		}
		if (minimumX > maximumX || minimumZ > maximumZ) {
			throw new IllegalArgumentException("invalid chunk footprint");
		}
		Outcome ordering = inspectOrdering(
				eventWorldEpoch,
				sequence,
				type,
				minimumX,
				0,
				minimumZ,
				maximumX,
				0,
				maximumZ
		);
		if (ordering != null) {
			return ordering;
		}
		remember(
				sequence,
				type,
				minimumX,
				0,
				minimumZ,
				maximumX,
				0,
				maximumZ
		);
		acceptedEvents++;
		boolean dirty = false;
		long cells = 0L;
		for (int index = 0; index < activeCount; index++) {
			CellCaptureBounds bounds = active[index];
			int intersectionMinimumX =
					Math.max(bounds.minimumX(), minimumX);
			int intersectionMinimumZ =
					Math.max(bounds.minimumZ(), minimumZ);
			int intersectionMaximumX =
					Math.min(bounds.maximumX(), maximumX);
			int intersectionMaximumZ =
					Math.min(bounds.maximumZ(), maximumZ);
			if (intersectionMinimumX > intersectionMaximumX
					|| intersectionMinimumZ > intersectionMaximumZ) {
				continue;
			}
			CellCaptureBounds intersection = new CellCaptureBounds(
					intersectionMinimumX,
					bounds.minimumY(),
					intersectionMinimumZ,
					intersectionMaximumX,
					bounds.maximumY(),
					intersectionMaximumZ
			);
			tracker.markDirty(intersection);
			cells = Math.addExact(cells, volume(intersection));
			dirty = true;
		}
		if (dirty) {
			dirtyEvents++;
			markedCells = Math.addExact(markedCells, cells);
			return Outcome.ACCEPTED_DIRTY;
		}
		return Outcome.ACCEPTED_OUTSIDE_COVERAGE;
	}

	public synchronized CoverageDirtyTracker tracker() {
		return tracker;
	}

	public synchronized long worldEpoch() {
		return worldEpoch;
	}

	public synchronized Diagnostics diagnostics() {
		return new Diagnostics(
				worldEpoch,
				lastSequence,
				activeCount,
				worldReplacements,
				acceptedEvents,
				dirtyEvents,
				duplicateEvents,
				conflictingSequenceEvents,
				outOfOrderEvents,
				worldMismatchEvents,
				markedCells
		);
	}

	private Outcome inspectOrdering(
			long eventWorldEpoch,
			long sequence,
			EventType type,
			int minimumX,
			int minimumY,
			int minimumZ,
			int maximumX,
			int maximumY,
			int maximumZ
	) {
		Objects.requireNonNull(type, "type");
		if (sequence <= 0L) {
			throw new IllegalArgumentException(
					"event sequence must be positive"
			);
		}
		if (eventWorldEpoch != worldEpoch || worldEpoch == 0L) {
			worldMismatchEvents++;
			return Outcome.WORLD_MISMATCH_REJECTED;
		}
		if (sequence < lastSequence) {
			outOfOrderEvents++;
			return Outcome.OUT_OF_ORDER_REJECTED;
		}
		if (sequence == lastSequence) {
			if (lastType == type
					&& lastMinimumX == minimumX
					&& lastMinimumY == minimumY
					&& lastMinimumZ == minimumZ
					&& lastMaximumX == maximumX
					&& lastMaximumY == maximumY
					&& lastMaximumZ == maximumZ) {
				duplicateEvents++;
				return Outcome.DUPLICATE_IGNORED;
			}
			conflictingSequenceEvents++;
			return Outcome.CONFLICTING_SEQUENCE_REJECTED;
		}
		return null;
	}

	private void remember(
			long sequence,
			EventType type,
			int minimumX,
			int minimumY,
			int minimumZ,
			int maximumX,
			int maximumY,
			int maximumZ
	) {
		lastSequence = sequence;
		lastType = type;
		lastMinimumX = minimumX;
		lastMinimumY = minimumY;
		lastMinimumZ = minimumZ;
		lastMaximumX = maximumX;
		lastMaximumY = maximumY;
		lastMaximumZ = maximumZ;
	}

	private void clearCoverage() {
		for (int index = 0; index < activeCount; index++) {
			active[index] = null;
		}
		activeCount = 0;
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

	private static long volume(CellCaptureBounds bounds) {
		long width = (long) bounds.maximumX() - bounds.minimumX() + 1L;
		long height = (long) bounds.maximumY() - bounds.minimumY() + 1L;
		long depth = (long) bounds.maximumZ() - bounds.minimumZ() + 1L;
		return Math.multiplyExact(Math.multiplyExact(width, height), depth);
	}

	public record Diagnostics(
			long worldEpoch,
			long lastSequence,
			int activeCoverageCount,
			long worldReplacements,
			long acceptedEvents,
			long dirtyEvents,
			long duplicateEvents,
			long conflictingSequenceEvents,
			long outOfOrderEvents,
			long worldMismatchEvents,
			long markedCells
	) {
	}
}
