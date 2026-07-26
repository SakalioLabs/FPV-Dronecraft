package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.propagation.DdaFirstOrderPathSolver.RoomBounds;
import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.Objects;

/**
 * Reusable primitive workspace for direct plus six shoebox first-order paths.
 * A solve call performs no heap allocation after the workspace and room bounds
 * have been created.
 */
public final class DdaFirstOrderBatchSolver {
	public static final int PATH_COUNT = 7;
	public static final int DIRECT_PATH = 0;
	private static final double BOUNDARY_EPSILON_METERS = 1.0e-9;

	private DdaFirstOrderBatchSolver() {
	}

	public static void solve(
			double sourceX,
			double sourceY,
			double sourceZ,
			double listenerX,
			double listenerY,
			double listenerZ,
			RoomBounds bounds,
			int maximumCellsPerLeg,
			Workspace workspace
	) {
		Objects.requireNonNull(bounds, "bounds");
		Objects.requireNonNull(workspace, "workspace");
		validatePosition(
				sourceX,
				sourceY,
				sourceZ,
				bounds,
				"source"
		);
		validatePosition(
				listenerX,
				listenerY,
				listenerZ,
				bounds,
				"listener"
		);
		if (maximumCellsPerLeg < 1) {
			throw new IllegalArgumentException(
					"maximumCellsPerLeg must be positive"
			);
		}
		int maximumX = (int) Math.ceil(bounds.xMeters());
		int maximumY = (int) Math.ceil(bounds.yMeters());
		int maximumZ = (int) Math.ceil(bounds.zMeters());

		long direct = VoxelDda.traceOpenBoundsPacked(
				sourceX,
				sourceY,
				sourceZ,
				listenerX,
				listenerY,
				listenerZ,
				0,
				0,
				0,
				maximumX,
				maximumY,
				maximumZ,
				maximumCellsPerLeg
		);
		workspace.lengthMeters[DIRECT_PATH] = distance(
				sourceX,
				sourceY,
				sourceZ,
				listenerX,
				listenerY,
				listenerZ
		);
		workspace.visitedCells[DIRECT_PATH] =
				VoxelDda.packedVisitedCellCount(direct);
		workspace.topologyVisible[DIRECT_PATH] =
				VoxelDda.packedReachedEnd(direct);
		workspace.reflectionX[DIRECT_PATH] = Double.NaN;
		workspace.reflectionY[DIRECT_PATH] = Double.NaN;
		workspace.reflectionZ[DIRECT_PATH] = Double.NaN;

		for (int facet = 0; facet < 6; facet++) {
			int axis = switch (facet) {
				case 0, 1 -> 2;
				case 2, 4 -> 0;
				case 3, 5 -> 1;
				default -> throw new IllegalStateException();
			};
			boolean maximum = facet == 1 || facet == 4 || facet == 5;
			double plane = maximum
					? switch (axis) {
						case 0 -> bounds.xMeters();
						case 1 -> bounds.yMeters();
						case 2 -> bounds.zMeters();
						default -> throw new IllegalStateException();
					}
					: 0.0;
			double sourceAxis = component(
					axis,
					sourceX,
					sourceY,
					sourceZ
			);
			double listenerAxis = component(
					axis,
					listenerX,
					listenerY,
					listenerZ
			);
			double imageAxis = 2.0 * plane - sourceAxis;
			double interpolation = (plane - imageAxis)
					/ (listenerAxis - imageAxis);
			double imageX = axis == 0 ? imageAxis : sourceX;
			double imageY = axis == 1 ? imageAxis : sourceY;
			double imageZ = axis == 2 ? imageAxis : sourceZ;
			double reflectionX = imageX
					+ interpolation * (listenerX - imageX);
			double reflectionY = imageY
					+ interpolation * (listenerY - imageY);
			double reflectionZ = imageZ
					+ interpolation * (listenerZ - imageZ);
			double interiorX = reflectionX;
			double interiorY = reflectionY;
			double interiorZ = reflectionZ;
			double interiorAxis = plane
					+ (maximum
							? -BOUNDARY_EPSILON_METERS
							: BOUNDARY_EPSILON_METERS);
			if (axis == 0) {
				interiorX = interiorAxis;
			} else if (axis == 1) {
				interiorY = interiorAxis;
			} else {
				interiorZ = interiorAxis;
			}
			long outgoing = VoxelDda.traceOpenBoundsPacked(
					sourceX,
					sourceY,
					sourceZ,
					interiorX,
					interiorY,
					interiorZ,
					0,
					0,
					0,
					maximumX,
					maximumY,
					maximumZ,
					maximumCellsPerLeg
			);
			long incoming = VoxelDda.traceOpenBoundsPacked(
					interiorX,
					interiorY,
					interiorZ,
					listenerX,
					listenerY,
					listenerZ,
					0,
					0,
					0,
					maximumX,
					maximumY,
					maximumZ,
					maximumCellsPerLeg
			);
			int pathIndex = facet + 1;
			workspace.lengthMeters[pathIndex] = distance(
					sourceX,
					sourceY,
					sourceZ,
					reflectionX,
					reflectionY,
					reflectionZ
			) + distance(
					reflectionX,
					reflectionY,
					reflectionZ,
					listenerX,
					listenerY,
					listenerZ
			);
			workspace.visitedCells[pathIndex] =
					VoxelDda.packedVisitedCellCount(outgoing)
							+ VoxelDda.packedVisitedCellCount(incoming);
			workspace.topologyVisible[pathIndex] =
					VoxelDda.packedReachedEnd(outgoing)
							&& VoxelDda.packedReachedEnd(incoming);
			workspace.reflectionX[pathIndex] = reflectionX;
			workspace.reflectionY[pathIndex] = reflectionY;
			workspace.reflectionZ[pathIndex] = reflectionZ;
		}
	}

	private static double component(
			int axis,
			double x,
			double y,
			double z
	) {
		return axis == 0 ? x : axis == 1 ? y : z;
	}

	private static double distance(
			double leftX,
			double leftY,
			double leftZ,
			double rightX,
			double rightY,
			double rightZ
	) {
		double x = rightX - leftX;
		double y = rightY - leftY;
		double z = rightZ - leftZ;
		return Math.sqrt(x * x + y * y + z * z);
	}

	private static void validatePosition(
			double x,
			double y,
			double z,
			RoomBounds bounds,
			String label
	) {
		if (!Double.isFinite(x) || !Double.isFinite(y)
				|| !Double.isFinite(z)
				|| x < 0.0 || x >= bounds.xMeters()
				|| y < 0.0 || y >= bounds.yMeters()
				|| z < 0.0 || z >= bounds.zMeters()) {
			throw new IllegalArgumentException(label + " must be inside room");
		}
	}

	public static final class Workspace {
		private final double[] lengthMeters = new double[PATH_COUNT];
		private final int[] visitedCells = new int[PATH_COUNT];
		private final boolean[] topologyVisible = new boolean[PATH_COUNT];
		private final double[] reflectionX = new double[PATH_COUNT];
		private final double[] reflectionY = new double[PATH_COUNT];
		private final double[] reflectionZ = new double[PATH_COUNT];

		public double lengthMeters(int pathIndex) {
			return lengthMeters[checked(pathIndex)];
		}

		public int visitedCells(int pathIndex) {
			return visitedCells[checked(pathIndex)];
		}

		public boolean topologyVisible(int pathIndex) {
			return topologyVisible[checked(pathIndex)];
		}

		public double reflectionX(int pathIndex) {
			return reflectionX[checked(pathIndex)];
		}

		public double reflectionY(int pathIndex) {
			return reflectionY[checked(pathIndex)];
		}

		public double reflectionZ(int pathIndex) {
			return reflectionZ[checked(pathIndex)];
		}

		private static int checked(int pathIndex) {
			if (pathIndex < 0 || pathIndex >= PATH_COUNT) {
				throw new IndexOutOfBoundsException(pathIndex);
			}
			return pathIndex;
		}
	}
}
