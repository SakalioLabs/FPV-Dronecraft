package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.HashSet;
import java.util.Set;

/**
 * Immutable, bounded set of known passable air cells. Unknown cells are
 * intentionally non-passable so a worker can never infer unloaded world data.
 */
public final class SparseAirGrid {
	private final VoxelBounds bounds;
	private final Set<Long> knownCells;
	private final Set<Long> passableCells;
	private final boolean complete;

	private SparseAirGrid(
			VoxelBounds bounds,
			Set<Long> knownCells,
			Set<Long> passableCells,
			boolean complete
	) {
		this.bounds = bounds;
		this.knownCells = Set.copyOf(knownCells);
		this.passableCells = Set.copyOf(passableCells);
		this.complete = complete;
	}

	public VoxelBounds bounds() {
		return bounds;
	}

	public boolean isKnown(int x, int y, int z) {
		return bounds.contains(x, y, z) && knownCells.contains(pack(x, y, z));
	}

	public boolean isPassable(int x, int y, int z) {
		return bounds.contains(x, y, z) && passableCells.contains(pack(x, y, z));
	}

	public boolean complete() {
		return complete;
	}

	public int sampledCellCount() {
		return knownCells.size();
	}

	public static Builder builder(VoxelBounds bounds) {
		return new Builder(bounds);
	}

	private static long pack(int x, int y, int z) {
		return ((long) x & 0x3ffffffL) << 38
				| ((long) z & 0x3ffffffL) << 12
				| (long) y & 0xfffL;
	}

	public static final class Builder {
		private final VoxelBounds bounds;
		private final Set<Long> knownCells = new HashSet<>();
		private final Set<Long> passableCells = new HashSet<>();
		private boolean complete = true;

		private Builder(VoxelBounds bounds) {
			this.bounds = bounds;
		}

		public Builder sample(int x, int y, int z, boolean passable) {
			if (!bounds.contains(x, y, z)) {
				throw new IllegalArgumentException("sample lies outside bounds");
			}
			long packed = pack(x, y, z);
			knownCells.add(packed);
			if (passable) {
				passableCells.add(packed);
			} else {
				passableCells.remove(packed);
			}
			return this;
		}

		public Builder markIncomplete() {
			complete = false;
			return this;
		}

		public SparseAirGrid build() {
			return new SparseAirGrid(bounds, knownCells, passableCells, complete);
		}
	}
}
