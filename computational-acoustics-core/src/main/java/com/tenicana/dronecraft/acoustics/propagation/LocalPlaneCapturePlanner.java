package com.tenicana.dronecraft.acoustics.propagation;

import java.util.Arrays;
import java.util.Objects;

/**
 * Deterministic bounded grouping of listener/source capture cuboids.
 * Planning is snapshot-time work; the reusable workspace avoids GC churn.
 */
public final class LocalPlaneCapturePlanner {
	public static final int MAXIMUM_SOURCES = 16;
	public static final int MAXIMUM_GROUPS = 4;

	private LocalPlaneCapturePlanner() {
	}

	public static void plan(
			int listenerX,
			int listenerY,
			int listenerZ,
			int[] sourceX,
			int[] sourceY,
			int[] sourceZ,
			int sourceCount,
			Config config,
			Workspace output
	) {
		requireSources(sourceX, sourceY, sourceZ, sourceCount);
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(output, "output");
		output.reset(sourceCount);
		for (int source = 0; source < sourceCount; source++) {
			output.order[source] = source;
		}
		for (int index = 1; index < sourceCount; index++) {
			int source = output.order[index];
			long distance = squaredDistance(
					listenerX, listenerY, listenerZ,
					sourceX[source], sourceY[source], sourceZ[source]
			);
			int insert = index;
			while (insert > 0) {
				int previous = output.order[insert - 1];
				long previousDistance = squaredDistance(
						listenerX, listenerY, listenerZ,
						sourceX[previous],
						sourceY[previous],
						sourceZ[previous]
				);
				if (previousDistance < distance
						|| previousDistance == distance
								&& previous < source) {
					break;
				}
				output.order[insert] = previous;
				insert--;
			}
			output.order[insert] = source;
		}

		int horizontalMargin = Math.addExact(
				config.reflectionHorizontalRadius(),
				config.halo()
		);
		int verticalMargin = Math.addExact(
				config.reflectionVerticalRadius(),
				config.halo()
		);
		for (int order = 0; order < sourceCount; order++) {
			int source = output.order[order];
			int requiredMinimumX = Math.subtractExact(
					Math.min(listenerX, sourceX[source]),
					horizontalMargin
			);
			int requiredMaximumX = Math.addExact(
					Math.max(listenerX, sourceX[source]),
					horizontalMargin
			);
			int requiredMinimumY = Math.subtractExact(
					Math.min(listenerY, sourceY[source]),
					verticalMargin
			);
			int requiredMaximumY = Math.addExact(
					Math.max(listenerY, sourceY[source]),
					verticalMargin
			);
			int requiredMinimumZ = Math.subtractExact(
					Math.min(listenerZ, sourceZ[source]),
					horizontalMargin
			);
			int requiredMaximumZ = Math.addExact(
					Math.max(listenerZ, sourceZ[source]),
					horizontalMargin
			);
			if (!admissible(
					requiredMinimumX,
					requiredMinimumY,
					requiredMinimumZ,
					requiredMaximumX,
					requiredMaximumY,
					requiredMaximumZ,
					config
			)) {
				output.fallbackCount++;
				continue;
			}
			long requiredVolume = volume(
					requiredMinimumX,
					requiredMinimumY,
					requiredMinimumZ,
					requiredMaximumX,
					requiredMaximumY,
					requiredMaximumZ
			);
			int selectedGroup = -1;
			long selectedIncrement = Long.MAX_VALUE;
			for (int group = 0; group < output.groupCount; group++) {
				int minimumX = Math.min(
						output.minimumX[group], requiredMinimumX
				);
				int minimumY = Math.min(
						output.minimumY[group], requiredMinimumY
				);
				int minimumZ = Math.min(
						output.minimumZ[group], requiredMinimumZ
				);
				int maximumX = Math.max(
						output.maximumX[group], requiredMaximumX
				);
				int maximumY = Math.max(
						output.maximumY[group], requiredMaximumY
				);
				int maximumZ = Math.max(
						output.maximumZ[group], requiredMaximumZ
				);
				if (!admissible(
						minimumX, minimumY, minimumZ,
						maximumX, maximumY, maximumZ,
						config
				)) {
					continue;
				}
				long increment = volume(
						minimumX, minimumY, minimumZ,
						maximumX, maximumY, maximumZ
				) - output.cellCount[group];
				if (increment <= requiredVolume
						&& increment < selectedIncrement) {
					selectedGroup = group;
					selectedIncrement = increment;
				}
			}
			if (selectedGroup < 0) {
				if (output.groupCount >= config.maximumGroups()) {
					output.fallbackCount++;
					continue;
				}
				selectedGroup = output.groupCount++;
				output.minimumX[selectedGroup] = requiredMinimumX;
				output.minimumY[selectedGroup] = requiredMinimumY;
				output.minimumZ[selectedGroup] = requiredMinimumZ;
				output.maximumX[selectedGroup] = requiredMaximumX;
				output.maximumY[selectedGroup] = requiredMaximumY;
				output.maximumZ[selectedGroup] = requiredMaximumZ;
			} else {
				output.minimumX[selectedGroup] = Math.min(
						output.minimumX[selectedGroup], requiredMinimumX
				);
				output.minimumY[selectedGroup] = Math.min(
						output.minimumY[selectedGroup], requiredMinimumY
				);
				output.minimumZ[selectedGroup] = Math.min(
						output.minimumZ[selectedGroup], requiredMinimumZ
				);
				output.maximumX[selectedGroup] = Math.max(
						output.maximumX[selectedGroup], requiredMaximumX
				);
				output.maximumY[selectedGroup] = Math.max(
						output.maximumY[selectedGroup], requiredMaximumY
				);
				output.maximumZ[selectedGroup] = Math.max(
						output.maximumZ[selectedGroup], requiredMaximumZ
				);
			}
			output.cellCount[selectedGroup] = volume(
					output.minimumX[selectedGroup],
					output.minimumY[selectedGroup],
					output.minimumZ[selectedGroup],
					output.maximumX[selectedGroup],
					output.maximumY[selectedGroup],
					output.maximumZ[selectedGroup]
			);
			output.sourceGroup[source] = selectedGroup;
			output.assignedCount++;
		}
	}

	private static boolean admissible(
			int minimumX,
			int minimumY,
			int minimumZ,
			int maximumX,
			int maximumY,
			int maximumZ,
			Config config
	) {
		long sizeX = (long) maximumX - minimumX + 1L;
		long sizeY = (long) maximumY - minimumY + 1L;
		long sizeZ = (long) maximumZ - minimumZ + 1L;
		return sizeX <= config.maximumHorizontalSpan()
				&& sizeZ <= config.maximumHorizontalSpan()
				&& sizeY <= config.maximumVerticalSpan()
				&& sizeX * sizeY * sizeZ
						<= config.maximumSampledBlockStates();
	}

	private static long volume(
			int minimumX,
			int minimumY,
			int minimumZ,
			int maximumX,
			int maximumY,
			int maximumZ
	) {
		return ((long) maximumX - minimumX + 1L)
				* ((long) maximumY - minimumY + 1L)
				* ((long) maximumZ - minimumZ + 1L);
	}

	private static long squaredDistance(
			int leftX,
			int leftY,
			int leftZ,
			int rightX,
			int rightY,
			int rightZ
	) {
		long x = (long) rightX - leftX;
		long y = (long) rightY - leftY;
		long z = (long) rightZ - leftZ;
		return x * x + y * y + z * z;
	}

	private static void requireSources(
			int[] x, int[] y, int[] z, int sourceCount
	) {
		Objects.requireNonNull(x, "sourceX");
		Objects.requireNonNull(y, "sourceY");
		Objects.requireNonNull(z, "sourceZ");
		if (sourceCount < 0 || sourceCount > MAXIMUM_SOURCES
				|| x.length < sourceCount
				|| y.length < sourceCount
				|| z.length < sourceCount) {
			throw new IllegalArgumentException(
					"source arrays must contain 0..16 entries"
			);
		}
	}

	public record Config(
			int reflectionHorizontalRadius,
			int reflectionVerticalRadius,
			int halo,
			int maximumGroups,
			int maximumSampledBlockStates,
			int maximumHorizontalSpan,
			int maximumVerticalSpan
	) {
		public Config {
			if (reflectionHorizontalRadius < 0
					|| reflectionVerticalRadius < 0
					|| halo < 0
					|| maximumGroups < 1
					|| maximumGroups > MAXIMUM_GROUPS
					|| maximumSampledBlockStates < 1
					|| maximumHorizontalSpan < 1
					|| maximumVerticalSpan < 1) {
				throw new IllegalArgumentException(
						"capture planner limits must be positive and bounded"
				);
			}
		}
	}

	public static final class Workspace {
		private final int[] order = new int[MAXIMUM_SOURCES];
		private final int[] sourceGroup = new int[MAXIMUM_SOURCES];
		private final int[] minimumX = new int[MAXIMUM_GROUPS];
		private final int[] minimumY = new int[MAXIMUM_GROUPS];
		private final int[] minimumZ = new int[MAXIMUM_GROUPS];
		private final int[] maximumX = new int[MAXIMUM_GROUPS];
		private final int[] maximumY = new int[MAXIMUM_GROUPS];
		private final int[] maximumZ = new int[MAXIMUM_GROUPS];
		private final long[] cellCount = new long[MAXIMUM_GROUPS];
		private int groupCount;
		private int assignedCount;
		private int fallbackCount;
		private int sourceCount;

		private void reset(int nextSourceCount) {
			sourceCount = nextSourceCount;
			groupCount = 0;
			assignedCount = 0;
			fallbackCount = 0;
			Arrays.fill(sourceGroup, 0, sourceCount, -1);
		}

		public int groupCount() {
			return groupCount;
		}

		public int assignedCount() {
			return assignedCount;
		}

		public int fallbackCount() {
			return fallbackCount;
		}

		public int sourceGroup(int source) {
			if (source < 0 || source >= sourceCount) {
				throw new IndexOutOfBoundsException(source);
			}
			return sourceGroup[source];
		}

		public CellCaptureBounds bounds(int group) {
			int checked = checkedGroup(group);
			return new CellCaptureBounds(
					minimumX[checked],
					minimumY[checked],
					minimumZ[checked],
					maximumX[checked],
					maximumY[checked],
					maximumZ[checked]
			);
		}

		public long cellCount(int group) {
			return cellCount[checkedGroup(group)];
		}

		public long totalCellCount() {
			long total = 0L;
			for (int group = 0; group < groupCount; group++) {
				total += cellCount[group];
			}
			return total;
		}

		private int checkedGroup(int group) {
			if (group < 0 || group >= groupCount) {
				throw new IndexOutOfBoundsException(group);
			}
			return group;
		}
	}
}
