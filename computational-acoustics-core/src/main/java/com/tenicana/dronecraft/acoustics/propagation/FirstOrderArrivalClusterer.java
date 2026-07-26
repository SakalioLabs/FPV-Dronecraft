package com.tenicana.dronecraft.acoustics.propagation;

import java.util.Objects;

/**
 * Fixed-size collision clustering for the six first-order reflected arrivals.
 * The caller reuses the workspace, so clustering performs no heap allocation.
 */
public final class FirstOrderArrivalClusterer {
	public static final double SAMPLE_RATE_HZ = 48_000.0;
	public static final double SPEED_OF_SOUND_METERS_PER_SECOND = 343.0;
	public static final double MAXIMUM_ADJACENT_SEPARATION_SAMPLES = 16.0;

	private FirstOrderArrivalClusterer() {
	}

	public static void cluster(
			DdaFirstOrderBatchSolver.Workspace paths,
			BoundedFirstOrderGainSolver.Workspace gains,
			Workspace output
	) {
		Objects.requireNonNull(paths, "paths");
		Objects.requireNonNull(gains, "gains");
		Objects.requireNonNull(output, "output");
		output.clusterCount = 0;
		int visibleCount = 0;
		for (int path = 1; path < DdaFirstOrderBatchSolver.PATH_COUNT; path++) {
			if (paths.topologyVisible(path)
					&& gains.totalEnergy(path) > 0.0) {
				int insert = visibleCount;
				while (insert > 0
						&& paths.lengthMeters(output.sortedPaths[insert - 1])
						> paths.lengthMeters(path)) {
					output.sortedPaths[insert] =
							output.sortedPaths[insert - 1];
					insert--;
				}
				output.sortedPaths[insert] = path;
				visibleCount++;
			}
		}
		for (int sorted = 0; sorted < visibleCount; sorted++) {
			int path = output.sortedPaths[sorted];
			double arrivalSamples = paths.lengthMeters(path)
					/ SPEED_OF_SOUND_METERS_PER_SECOND * SAMPLE_RATE_HZ;
			boolean join = output.clusterCount > 0
					&& arrivalSamples - output.lastArrivalSamples[
							output.clusterCount - 1
					] <= MAXIMUM_ADJACENT_SEPARATION_SAMPLES;
			int cluster = join
					? output.clusterCount - 1
					: output.clusterCount++;
			if (!join) {
				output.low[cluster] = 0.0;
				output.mid[cluster] = 0.0;
				output.high[cluster] = 0.0;
				output.weightedArrivalSamples[cluster] = 0.0;
				output.totalWeight[cluster] = 0.0;
				output.pathCount[cluster] = 0;
			}
			double weight = gains.totalEnergy(path);
			output.low[cluster] += gains.low(path);
			output.mid[cluster] += gains.mid(path);
			output.high[cluster] += gains.high(path);
			output.weightedArrivalSamples[cluster] += arrivalSamples * weight;
			output.totalWeight[cluster] += weight;
			output.pathCount[cluster]++;
			output.lastArrivalSamples[cluster] = arrivalSamples;
		}
	}

	public static final class Workspace {
		private final int[] sortedPaths = new int[6];
		private final double[] low = new double[6];
		private final double[] mid = new double[6];
		private final double[] high = new double[6];
		private final double[] weightedArrivalSamples = new double[6];
		private final double[] totalWeight = new double[6];
		private final double[] lastArrivalSamples = new double[6];
		private final int[] pathCount = new int[6];
		private int clusterCount;

		public int clusterCount() {
			return clusterCount;
		}

		public double low(int clusterIndex) {
			return low[checked(clusterIndex)];
		}

		public double mid(int clusterIndex) {
			return mid[checked(clusterIndex)];
		}

		public double high(int clusterIndex) {
			return high[checked(clusterIndex)];
		}

		public double arrivalSamples(int clusterIndex) {
			int checked = checked(clusterIndex);
			return weightedArrivalSamples[checked] / totalWeight[checked];
		}

		public int pathCount(int clusterIndex) {
			return pathCount[checked(clusterIndex)];
		}

		private int checked(int clusterIndex) {
			if (clusterIndex < 0 || clusterIndex >= clusterCount) {
				throw new IndexOutOfBoundsException(clusterIndex);
			}
			return clusterIndex;
		}
	}
}
