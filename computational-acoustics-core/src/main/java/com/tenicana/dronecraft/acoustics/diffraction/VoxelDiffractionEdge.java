package com.tenicana.dronecraft.acoustics.diffraction;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

/**
 * Auditable voxel-edge candidate extracted from a cardinal air path.
 */
public record VoxelDiffractionEdge(
		int pathTurnIndex,
		VoxelDda.Cell airTurnCell,
		VoxelDda.Cell obstacleCell,
		AcousticVector apexPoint,
		AxisDirection edgeAxis,
		AxisDirection incomingDirection,
		AxisDirection outgoingDirection
) {
	public record AxisDirection(int x, int y, int z) {
		public AxisDirection {
			if (Math.abs(x) + Math.abs(y) + Math.abs(z) != 1) {
				throw new IllegalArgumentException("direction must be a signed unit axis");
			}
		}
	}
}
