package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;

import java.util.Objects;

/**
 * Immutable worker-side bounds and material query used for environment probes.
 * Bounds are cell coordinates with an exclusive maximum.
 */
public record ReflectionVolume(
		int minimumX,
		int minimumY,
		int minimumZ,
		int maximumXExclusive,
		int maximumYExclusive,
		int maximumZExclusive,
		long generation,
		boolean complete,
		DirectPathSolver.MaterialQuery materials
) {
	public ReflectionVolume {
		if (maximumXExclusive <= minimumX
				|| maximumYExclusive <= minimumY
				|| maximumZExclusive <= minimumZ) {
			throw new IllegalArgumentException(
					"reflection volume must have positive extent"
			);
		}
		Objects.requireNonNull(materials, "materials");
	}

	public boolean containsCell(int x, int y, int z) {
		return x >= minimumX && x < maximumXExclusive
				&& y >= minimumY && y < maximumYExclusive
				&& z >= minimumZ && z < maximumZExclusive;
	}

	public boolean containsPosition(double x, double y, double z) {
		return x >= minimumX && x < maximumXExclusive
				&& y >= minimumY && y < maximumYExclusive
				&& z >= minimumZ && z < maximumZExclusive;
	}
}
