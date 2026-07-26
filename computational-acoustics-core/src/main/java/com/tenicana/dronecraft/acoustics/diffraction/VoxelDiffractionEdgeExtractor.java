package com.tenicana.dronecraft.acoustics.diffraction;

import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.path.SparseAirGrid;
import com.tenicana.dronecraft.acoustics.path.VoxelAStar;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts convex block-edge candidates from turns in a cardinal voxel path.
 *
 * <p>A candidate is accepted only when the diagonal cell on the inside of the
 * turn is known and non-passable. This deliberately rejects unsupported turns
 * rather than inventing wedge geometry from unknown world data.</p>
 */
public final class VoxelDiffractionEdgeExtractor {
	private VoxelDiffractionEdgeExtractor() {
	}

	public static List<VoxelDiffractionEdge> extract(
			List<VoxelDda.Cell> path,
			SparseAirGrid grid,
			int maxEdges
	) {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(grid, "grid");
		if (maxEdges < 1) {
			throw new IllegalArgumentException("maxEdges must be positive");
		}
		List<VoxelDda.Cell> simplified = VoxelAStar.simplify(path);
		List<VoxelDiffractionEdge> edges = new ArrayList<>();
		for (int index = 1; index < simplified.size() - 1
				&& edges.size() < maxEdges; index++) {
			VoxelDda.Cell previous = simplified.get(index - 1);
			VoxelDda.Cell turn = simplified.get(index);
			VoxelDda.Cell next = simplified.get(index + 1);
			VoxelDiffractionEdge.AxisDirection incoming = direction(previous, turn);
			VoxelDiffractionEdge.AxisDirection outgoing = direction(turn, next);
			int crossX = incoming.y() * outgoing.z() - incoming.z() * outgoing.y();
			int crossY = incoming.z() * outgoing.x() - incoming.x() * outgoing.z();
			int crossZ = incoming.x() * outgoing.y() - incoming.y() * outgoing.x();
			if (crossX == 0 && crossY == 0 && crossZ == 0) {
				continue;
			}

			VoxelDda.Cell obstacle = new VoxelDda.Cell(
					turn.x() + outgoing.x() - incoming.x(),
					turn.y() + outgoing.y() - incoming.y(),
					turn.z() + outgoing.z() - incoming.z()
			);
			if (!grid.isKnown(obstacle.x(), obstacle.y(), obstacle.z())
					|| grid.isPassable(obstacle.x(), obstacle.y(), obstacle.z())) {
				continue;
			}
			AcousticVector apex = new AcousticVector(
					turn.x() + 0.5 + 0.5 * (outgoing.x() - incoming.x()),
					turn.y() + 0.5 + 0.5 * (outgoing.y() - incoming.y()),
					turn.z() + 0.5 + 0.5 * (outgoing.z() - incoming.z())
			);
			edges.add(new VoxelDiffractionEdge(
					index,
					turn,
					obstacle,
					apex,
					new VoxelDiffractionEdge.AxisDirection(crossX, crossY, crossZ),
					incoming,
					outgoing
			));
		}
		return List.copyOf(edges);
	}

	private static VoxelDiffractionEdge.AxisDirection direction(
			VoxelDda.Cell first,
			VoxelDda.Cell second
	) {
		int dx = Integer.signum(second.x() - first.x());
		int dy = Integer.signum(second.y() - first.y());
		int dz = Integer.signum(second.z() - first.z());
		return new VoxelDiffractionEdge.AxisDirection(dx, dy, dz);
	}
}
