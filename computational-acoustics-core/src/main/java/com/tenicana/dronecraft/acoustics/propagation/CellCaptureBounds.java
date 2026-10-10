package com.tenicana.dronecraft.acoustics.propagation;

/**
 * Inclusive captured block-cell cuboid. It is also the coverage predicate used
 * by DDA; cells outside a complete capture are unknown, never implicit air.
 */
public record CellCaptureBounds(
		int minimumX,
		int minimumY,
		int minimumZ,
		int maximumX,
		int maximumY,
		int maximumZ
) implements LocalPlaneReflectionSolver.CellCoverageQuery {
	public CellCaptureBounds {
		if (minimumX > maximumX
				|| minimumY > maximumY
				|| minimumZ > maximumZ) {
			throw new IllegalArgumentException(
					"capture bounds must have positive inclusive dimensions"
			);
		}
	}

	@Override
	public boolean isCovered(int x, int y, int z) {
		return x >= minimumX && x <= maximumX
				&& y >= minimumY && y <= maximumY
				&& z >= minimumZ && z <= maximumZ;
	}

	public int sizeX() {
		return Math.addExact(Math.subtractExact(maximumX, minimumX), 1);
	}

	public int sizeY() {
		return Math.addExact(Math.subtractExact(maximumY, minimumY), 1);
	}

	public int sizeZ() {
		return Math.addExact(Math.subtractExact(maximumZ, minimumZ), 1);
	}

	public long cellCount() {
		return Math.multiplyExact(
				(long) sizeX(),
				Math.multiplyExact((long) sizeY(), sizeZ())
		);
	}
}
