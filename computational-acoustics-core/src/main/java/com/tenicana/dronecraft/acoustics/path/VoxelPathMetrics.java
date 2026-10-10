package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.List;
import java.util.Objects;

public record VoxelPathMetrics(
		double pathLengthMeters,
		double extraPathLengthMeters,
		int turnCount
) {
	public static VoxelPathMetrics measure(
			AcousticVector source,
			AcousticVector listener,
			List<VoxelDda.Cell> path
	) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(listener, "listener");
		path = List.copyOf(Objects.requireNonNull(path, "path"));
		if (path.isEmpty()) {
			throw new IllegalArgumentException("path must not be empty");
		}
		List<VoxelDda.Cell> simplified = VoxelAStar.simplify(path);
		double length = source.subtract(center(simplified.getFirst())).length();
		for (int index = 1; index < simplified.size(); index++) {
			length += center(simplified.get(index)).subtract(center(simplified.get(index - 1))).length();
		}
		length += listener.subtract(center(simplified.getLast())).length();
		double directLength = source.subtract(listener).length();
		return new VoxelPathMetrics(
				length,
				Math.max(0.0, length - directLength),
				Math.max(0, simplified.size() - 2)
		);
	}

	private static AcousticVector center(VoxelDda.Cell cell) {
		return new AcousticVector(cell.x() + 0.5, cell.y() + 0.5, cell.z() + 0.5);
	}
}
