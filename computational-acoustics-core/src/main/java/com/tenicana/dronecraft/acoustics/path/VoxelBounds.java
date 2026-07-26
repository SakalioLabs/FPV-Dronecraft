package com.tenicana.dronecraft.acoustics.path;

public record VoxelBounds(
		int minX,
		int minY,
		int minZ,
		int maxX,
		int maxY,
		int maxZ
) {
	public VoxelBounds {
		if (minX > maxX || minY > maxY || minZ > maxZ) {
			throw new IllegalArgumentException("minimum bounds must not exceed maximum bounds");
		}
	}

	public boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX
				&& y >= minY && y <= maxY
				&& z >= minZ && z <= maxZ;
	}

	public long volume() {
		return (long) (maxX - minX + 1)
				* (maxY - minY + 1)
				* (maxZ - minZ + 1);
	}
}
