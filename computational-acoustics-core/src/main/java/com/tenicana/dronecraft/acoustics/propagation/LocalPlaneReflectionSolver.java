package com.tenicana.dronecraft.acoustics.propagation;

import java.util.Objects;

/**
 * Allocation-free continuous image-source geometry plus two-leg voxel
 * visibility for one finite axis-aligned reflection patch.
 */
public final class LocalPlaneReflectionSolver {
	public static final double PATCH_EPSILON_METERS = 1.0e-9;
	public static final double AIR_SIDE_OFFSET_METERS = 1.0e-7;
	private static final long TRACE_REACHED_END = 1L << 32;
	private static final long TRACE_BLOCKED = 1L << 33;
	private static final long TRACE_OUTSIDE_COVERAGE = 1L << 34;

	private LocalPlaneReflectionSolver() {
	}

	public static void solve(
			double sourceX,
			double sourceY,
			double sourceZ,
			double listenerX,
			double listenerY,
			double listenerZ,
			AxisAlignedPlanePatch patch,
			CellBlockQuery blockers,
			int maximumCellsPerLeg,
			Workspace output
	) {
		solve(
				sourceX, sourceY, sourceZ,
				listenerX, listenerY, listenerZ,
				patch,
				blockers,
				SegmentBlockQuery.NONE,
				maximumCellsPerLeg,
				output
		);
	}

	public static void solve(
			double sourceX,
			double sourceY,
			double sourceZ,
			double listenerX,
			double listenerY,
			double listenerZ,
			AxisAlignedPlanePatch patch,
			CellBlockQuery blockers,
			SegmentBlockQuery exactBlockers,
			int maximumCellsPerLeg,
			Workspace output
	) {
		solve(
				sourceX, sourceY, sourceZ,
				listenerX, listenerY, listenerZ,
				patch,
				blockers,
				exactBlockers,
				CellCoverageQuery.ALL,
				maximumCellsPerLeg,
				output
		);
	}

	public static void solve(
			double sourceX,
			double sourceY,
			double sourceZ,
			double listenerX,
			double listenerY,
			double listenerZ,
			AxisAlignedPlanePatch patch,
			CellBlockQuery blockers,
			SegmentBlockQuery exactBlockers,
			CellCoverageQuery coverage,
			int maximumCellsPerLeg,
			Workspace output
	) {
		Objects.requireNonNull(patch, "patch");
		Objects.requireNonNull(blockers, "blockers");
		Objects.requireNonNull(exactBlockers, "exactBlockers");
		Objects.requireNonNull(coverage, "coverage");
		Objects.requireNonNull(output, "output");
		requirePosition(sourceX, sourceY, sourceZ, "source");
		requirePosition(listenerX, listenerY, listenerZ, "listener");
		if (maximumCellsPerLeg < 1) {
			throw new IllegalArgumentException(
					"maximumCellsPerLeg must be positive"
			);
		}
		output.reset();
		int axis = patch.axis();
		double sourceAxis = component(axis, sourceX, sourceY, sourceZ);
		double listenerAxis =
				component(axis, listenerX, listenerY, listenerZ);
		double sourceSide = patch.normalSign()
				* (sourceAxis - patch.coordinateMeters());
		double listenerSide = patch.normalSign()
				* (listenerAxis - patch.coordinateMeters());
		if (sourceSide <= PATCH_EPSILON_METERS
				|| listenerSide <= PATCH_EPSILON_METERS) {
			return;
		}
		double imageAxis = 2.0 * patch.coordinateMeters() - sourceAxis;
		double denominator = listenerAxis - imageAxis;
		if (Math.abs(denominator) <= 1.0e-15) {
			return;
		}
		double interpolation =
				(patch.coordinateMeters() - imageAxis) / denominator;
		if (interpolation <= 0.0 || interpolation >= 1.0) {
			return;
		}
		double imageX = axis == 0 ? imageAxis : sourceX;
		double imageY = axis == 1 ? imageAxis : sourceY;
		double imageZ = axis == 2 ? imageAxis : sourceZ;
		double reflectionX =
				imageX + interpolation * (listenerX - imageX);
		double reflectionY =
				imageY + interpolation * (listenerY - imageY);
		double reflectionZ =
				imageZ + interpolation * (listenerZ - imageZ);
		double first = firstCoordinate(
				axis, reflectionX, reflectionY, reflectionZ
		);
		double second = secondCoordinate(
				axis, reflectionX, reflectionY, reflectionZ
		);
		if (first < patch.minimumFirstMeters() - PATCH_EPSILON_METERS
				|| first > patch.maximumFirstMeters()
						+ PATCH_EPSILON_METERS
				|| second < patch.minimumSecondMeters()
						- PATCH_EPSILON_METERS
				|| second > patch.maximumSecondMeters()
						+ PATCH_EPSILON_METERS) {
			return;
		}
		output.candidateGeometry = true;
		output.reflectionX = reflectionX;
		output.reflectionY = reflectionY;
		output.reflectionZ = reflectionZ;
		double sourceLeg = distance(
				sourceX, sourceY, sourceZ,
				reflectionX, reflectionY, reflectionZ
		);
		double listenerLeg = distance(
				listenerX, listenerY, listenerZ,
				reflectionX, reflectionY, reflectionZ
		);
		output.pathLengthMeters = sourceLeg + listenerLeg;
		output.incidenceCosine =
				Math.abs(sourceAxis - patch.coordinateMeters()) / sourceLeg;
		double offset = patch.normalSign() * AIR_SIDE_OFFSET_METERS;
		double endpointX = reflectionX + (axis == 0 ? offset : 0.0);
		double endpointY = reflectionY + (axis == 1 ? offset : 0.0);
		double endpointZ = reflectionZ + (axis == 2 ? offset : 0.0);
		long outgoing = trace(
				sourceX, sourceY, sourceZ,
				endpointX, endpointY, endpointZ,
				blockers, coverage, maximumCellsPerLeg
		);
		long incoming = trace(
				listenerX, listenerY, listenerZ,
				endpointX, endpointY, endpointZ,
				blockers, coverage, maximumCellsPerLeg
		);
		output.visitedCells = visited(outgoing) + visited(incoming);
		output.complete = concluded(outgoing) && concluded(incoming);
		output.coverageMiss =
				outsideCoverage(outgoing) || outsideCoverage(incoming);
		boolean coarseVisible = output.complete
				&& !blocked(outgoing) && !blocked(incoming);
		output.topologyVisible = coarseVisible
				&& !exactBlockers.isBlocked(
						sourceX, sourceY, sourceZ,
						endpointX, endpointY, endpointZ
				)
				&& !exactBlockers.isBlocked(
						listenerX, listenerY, listenerZ,
						endpointX, endpointY, endpointZ
				);
	}

	private static long trace(
			double startX,
			double startY,
			double startZ,
			double endX,
			double endY,
			double endZ,
			CellBlockQuery blockers,
			CellCoverageQuery coverage,
			int maximumCells
	) {
		double deltaX = endX - startX;
		double deltaY = endY - startY;
		double deltaZ = endZ - startZ;
		int x = floor(startX);
		int y = floor(startY);
		int z = floor(startZ);
		int endCellX = floor(endX);
		int endCellY = floor(endY);
		int endCellZ = floor(endZ);
		int stepX = sign(deltaX);
		int stepY = sign(deltaY);
		int stepZ = sign(deltaZ);
		double tDeltaX = stepX == 0
				? Double.POSITIVE_INFINITY : Math.abs(1.0 / deltaX);
		double tDeltaY = stepY == 0
				? Double.POSITIVE_INFINITY : Math.abs(1.0 / deltaY);
		double tDeltaZ = stepZ == 0
				? Double.POSITIVE_INFINITY : Math.abs(1.0 / deltaZ);
		double tMaxX = initialTMax(startX, deltaX, x, stepX);
		double tMaxY = initialTMax(startY, deltaY, y, stepY);
		double tMaxZ = initialTMax(startZ, deltaZ, z, stepZ);
		for (int count = 0; count < maximumCells; count++) {
			if (!coverage.isCovered(x, y, z)) {
				return pack(count + 1, false, false, true);
			}
			if (blockers.isBlocked(x, y, z)) {
				return pack(count + 1, false, true, false);
			}
			if (x == endCellX && y == endCellY && z == endCellZ) {
				return pack(count + 1, true, false, false);
			}
			double crossing = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
			double epsilon = Math.max(
					1.0e-12, Math.abs(crossing) * 1.0e-12
			);
			if (Math.abs(tMaxX - crossing) <= epsilon) {
				x += stepX;
				tMaxX += tDeltaX;
			}
			if (Math.abs(tMaxY - crossing) <= epsilon) {
				y += stepY;
				tMaxY += tDeltaY;
			}
			if (Math.abs(tMaxZ - crossing) <= epsilon) {
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
		}
		return pack(maximumCells, false, false, false);
	}

	private static long pack(
			int visited,
			boolean reached,
			boolean blocked,
			boolean outsideCoverage
	) {
		return Integer.toUnsignedLong(visited)
				| (reached ? TRACE_REACHED_END : 0L)
				| (blocked ? TRACE_BLOCKED : 0L)
				| (outsideCoverage ? TRACE_OUTSIDE_COVERAGE : 0L);
	}

	private static int visited(long trace) {
		return (int) trace;
	}

	private static boolean concluded(long trace) {
		return (trace & (TRACE_REACHED_END | TRACE_BLOCKED)) != 0L;
	}

	private static boolean blocked(long trace) {
		return (trace & TRACE_BLOCKED) != 0L;
	}

	private static boolean outsideCoverage(long trace) {
		return (trace & TRACE_OUTSIDE_COVERAGE) != 0L;
	}

	private static double component(
			int axis, double x, double y, double z
	) {
		return axis == 0 ? x : axis == 1 ? y : z;
	}

	private static double firstCoordinate(
			int axis, double x, double y, double z
	) {
		return axis == 0 ? y : x;
	}

	private static double secondCoordinate(
			int axis, double x, double y, double z
	) {
		return axis == 2 ? y : z;
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

	private static int floor(double value) {
		return (int) Math.floor(value);
	}

	private static int sign(double value) {
		return value > 0.0 ? 1 : value < 0.0 ? -1 : 0;
	}

	private static double initialTMax(
			double start, double delta, int cell, int step
	) {
		if (step == 0) {
			return Double.POSITIVE_INFINITY;
		}
		double boundary = step > 0 ? cell + 1.0 : cell;
		return (boundary - start) / delta;
	}

	private static void requirePosition(
			double x, double y, double z, String label
	) {
		if (!Double.isFinite(x)
				|| !Double.isFinite(y)
				|| !Double.isFinite(z)) {
			throw new IllegalArgumentException(
					label + " coordinates must be finite"
			);
		}
	}

	@FunctionalInterface
	public interface CellBlockQuery {
		boolean isBlocked(int x, int y, int z);
	}

	@FunctionalInterface
	public interface SegmentBlockQuery {
		SegmentBlockQuery NONE =
				(startX, startY, startZ, endX, endY, endZ) -> false;

		boolean isBlocked(
				double startX,
				double startY,
				double startZ,
				double endX,
				double endY,
				double endZ
		);
	}

	@FunctionalInterface
	public interface CellCoverageQuery {
		CellCoverageQuery ALL = (x, y, z) -> true;

		boolean isCovered(int x, int y, int z);
	}

	public static final class Workspace {
		private boolean candidateGeometry;
		private boolean topologyVisible;
		private boolean complete;
		private boolean coverageMiss;
		private double reflectionX;
		private double reflectionY;
		private double reflectionZ;
		private double pathLengthMeters;
		private double incidenceCosine;
		private int visitedCells;

		private void reset() {
			candidateGeometry = false;
			topologyVisible = false;
			complete = false;
			coverageMiss = false;
			reflectionX = Double.NaN;
			reflectionY = Double.NaN;
			reflectionZ = Double.NaN;
			pathLengthMeters = Double.NaN;
			incidenceCosine = Double.NaN;
			visitedCells = 0;
		}

		public boolean candidateGeometry() {
			return candidateGeometry;
		}

		public boolean topologyVisible() {
			return topologyVisible;
		}

		public boolean complete() {
			return complete;
		}

		public boolean coverageMiss() {
			return coverageMiss;
		}

		public double reflectionX() {
			return reflectionX;
		}

		public double reflectionY() {
			return reflectionY;
		}

		public double reflectionZ() {
			return reflectionZ;
		}

		public double pathLengthMeters() {
			return pathLengthMeters;
		}

		public double incidenceCosine() {
			return incidenceCosine;
		}

		public int visitedCells() {
			return visitedCells;
		}
	}
}
