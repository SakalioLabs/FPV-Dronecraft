package com.tenicana.dronecraft.acoustics.voxel;

import com.tenicana.dronecraft.acoustics.AcousticVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Amanatides-Woo grid traversal used as the deterministic CPU reference for
 * all future accelerated propagation backends.
 */
public final class VoxelDda {
	private static final long PACKED_REACHED_END = 1L << 32;

	private VoxelDda() {
	}

	public static Trace trace(AcousticVector start, AcousticVector end, VoxelQuery query, int maxCells) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Objects.requireNonNull(query, "query");
		if (maxCells < 1) {
			throw new IllegalArgumentException("maxCells must be positive");
		}

		List<Cell> visited = new ArrayList<>();
		boolean[] blocked = {false};
		WalkResult result = walk(start, end, (x, y, z, pathLengthMeters) -> {
			Cell cell = new Cell(x, y, z);
			visited.add(cell);
			blocked[0] |= query.isSolid(x, y, z);
			return true;
		}, maxCells);
		return new Trace(visited, blocked[0], result.reachedEnd());
	}

	public static WalkResult walk(
			AcousticVector start,
			AcousticVector end,
			SegmentVisitor visitor,
			int maxCells
	) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		Objects.requireNonNull(visitor, "visitor");
		if (maxCells < 1) {
			throw new IllegalArgumentException("maxCells must be positive");
		}

		AcousticVector delta = end.subtract(start);
		double totalLength = delta.length();
		if (totalLength <= 1.0e-12) {
			Cell cell = cellAt(start);
			boolean keepWalking = visitor.visit(cell.x(), cell.y(), cell.z(), 0.0);
			return new WalkResult(1, keepWalking, !keepWalking);
		}

		int x = floor(start.x());
		int y = floor(start.y());
		int z = floor(start.z());
		int endX = floor(end.x());
		int endY = floor(end.y());
		int endZ = floor(end.z());
		int stepX = sign(delta.x());
		int stepY = sign(delta.y());
		int stepZ = sign(delta.z());
		double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / delta.x());
		double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / delta.y());
		double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / delta.z());
		double tMaxX = initialTMax(start.x(), delta.x(), x, stepX);
		double tMaxY = initialTMax(start.y(), delta.y(), y, stepY);
		double tMaxZ = initialTMax(start.z(), delta.z(), z, stepZ);
		double entryT = 0.0;

		for (int count = 0; count < maxCells; count++) {
			double exitT = Math.min(1.0, Math.min(tMaxX, Math.min(tMaxY, tMaxZ)));
			double segmentLength = Math.max(0.0, exitT - entryT) * totalLength;
			if (!visitor.visit(x, y, z, segmentLength)) {
				return new WalkResult(count + 1, false, true);
			}
			if (x == endX && y == endY && z == endZ) {
				return new WalkResult(count + 1, true, false);
			}

			double crossingT = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
			double epsilon = Math.max(1.0e-12, Math.abs(crossingT) * 1.0e-12);
			if (Math.abs(tMaxX - crossingT) <= epsilon) {
				x += stepX;
				tMaxX += tDeltaX;
			}
			if (Math.abs(tMaxY - crossingT) <= epsilon) {
				y += stepY;
				tMaxY += tDeltaY;
			}
			if (Math.abs(tMaxZ - crossingT) <= epsilon) {
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
			entryT = Math.min(1.0, crossingT);
		}
		return new WalkResult(maxCells, false, false);
	}

	/**
	 * Allocation-free open-volume traversal for batched worker paths. The
	 * encoded result is intentionally primitive so callers can reuse all input
	 * and output storage. Its crossing/tie rules mirror {@link #walk}.
	 */
	public static long traceOpenBoundsPacked(
			double startX,
			double startY,
			double startZ,
			double endX,
			double endY,
			double endZ,
			int minimumX,
			int minimumY,
			int minimumZ,
			int maximumXExclusive,
			int maximumYExclusive,
			int maximumZExclusive,
			int maxCells
	) {
		if (maxCells < 1) {
			throw new IllegalArgumentException("maxCells must be positive");
		}
		double deltaX = endX - startX;
		double deltaY = endY - startY;
		double deltaZ = endZ - startZ;
		int x = floor(startX);
		int y = floor(startY);
		int z = floor(startZ);
		int targetX = floor(endX);
		int targetY = floor(endY);
		int targetZ = floor(endZ);
		int stepX = sign(deltaX);
		int stepY = sign(deltaY);
		int stepZ = sign(deltaZ);
		double tDeltaX = stepX == 0
				? Double.POSITIVE_INFINITY
				: Math.abs(1.0 / deltaX);
		double tDeltaY = stepY == 0
				? Double.POSITIVE_INFINITY
				: Math.abs(1.0 / deltaY);
		double tDeltaZ = stepZ == 0
				? Double.POSITIVE_INFINITY
				: Math.abs(1.0 / deltaZ);
		double tMaxX = initialTMax(startX, deltaX, x, stepX);
		double tMaxY = initialTMax(startY, deltaY, y, stepY);
		double tMaxZ = initialTMax(startZ, deltaZ, z, stepZ);

		for (int count = 0; count < maxCells; count++) {
			if (x < minimumX || x >= maximumXExclusive
					|| y < minimumY || y >= maximumYExclusive
					|| z < minimumZ || z >= maximumZExclusive) {
				return pack(count + 1, false);
			}
			if (x == targetX && y == targetY && z == targetZ) {
				return pack(count + 1, true);
			}
			double crossingT = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
			double epsilon = Math.max(
					1.0e-12,
					Math.abs(crossingT) * 1.0e-12
			);
			if (Math.abs(tMaxX - crossingT) <= epsilon) {
				x += stepX;
				tMaxX += tDeltaX;
			}
			if (Math.abs(tMaxY - crossingT) <= epsilon) {
				y += stepY;
				tMaxY += tDeltaY;
			}
			if (Math.abs(tMaxZ - crossingT) <= epsilon) {
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
		}
		return pack(maxCells, false);
	}

	public static int packedVisitedCellCount(long packed) {
		return (int) packed;
	}

	public static boolean packedReachedEnd(long packed) {
		return (packed & PACKED_REACHED_END) != 0L;
	}

	private static long pack(int visitedCellCount, boolean reachedEnd) {
		return Integer.toUnsignedLong(visitedCellCount)
				| (reachedEnd ? PACKED_REACHED_END : 0L);
	}

	private static Cell cellAt(AcousticVector position) {
		return new Cell(floor(position.x()), floor(position.y()), floor(position.z()));
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}

	private static int sign(double value) {
		return value > 0.0 ? 1 : value < 0.0 ? -1 : 0;
	}

	private static double initialTMax(double start, double delta, int cell, int step) {
		if (step == 0) {
			return Double.POSITIVE_INFINITY;
		}
		double boundary = step > 0 ? cell + 1.0 : cell;
		return (boundary - start) / delta;
	}

	@FunctionalInterface
	public interface VoxelQuery {
		boolean isSolid(int x, int y, int z);
	}

	@FunctionalInterface
	public interface SegmentVisitor {
		boolean visit(int x, int y, int z, double pathLengthMeters);
	}

	public record Cell(int x, int y, int z) {
	}

	public record Trace(List<Cell> visitedCells, boolean blocked, boolean reachedEnd) {
		public Trace {
			visitedCells = List.copyOf(visitedCells);
		}
	}

	public record WalkResult(int visitedCellCount, boolean reachedEnd, boolean stoppedEarly) {
	}
}
