package com.tenicana.dronecraft.acoustics.propagation;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;

import java.util.List;
import java.util.Objects;

/**
 * Bounded six-candidate local-plane scene solve. Snapshot construction may
 * allocate; repeated solves reuse only primitive workspaces.
 */
public final class LocalPlaneSceneSolver {
	public static final int MAXIMUM_CANDIDATES = 6;
	public static final double SAMPLE_RATE_HZ = 48_000.0;
	public static final double SPEED_OF_SOUND_METERS_PER_SECOND = 343.0;
	public static final double CLUSTER_SEPARATION_SAMPLES = 16.0;

	private LocalPlaneSceneSolver() {
	}

	public static void solve(
			double sourceX,
			double sourceY,
			double sourceZ,
			double listenerX,
			double listenerY,
			double listenerZ,
			List<AxisAlignedPlanePatch> patches,
			LocalPlaneReflectionSolver.CellBlockQuery blockers,
			int maximumCellsPerLeg,
			Workspace output
	) {
		solve(
				sourceX, sourceY, sourceZ,
				listenerX, listenerY, listenerZ,
				patches,
				blockers,
				LocalPlaneReflectionSolver.SegmentBlockQuery.NONE,
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
			List<AxisAlignedPlanePatch> patches,
			LocalPlaneReflectionSolver.CellBlockQuery blockers,
			LocalPlaneReflectionSolver.SegmentBlockQuery exactBlockers,
			int maximumCellsPerLeg,
			Workspace output
	) {
		solve(
				sourceX, sourceY, sourceZ,
				listenerX, listenerY, listenerZ,
				patches,
				blockers,
				exactBlockers,
				LocalPlaneReflectionSolver.CellCoverageQuery.ALL,
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
			List<AxisAlignedPlanePatch> patches,
			LocalPlaneReflectionSolver.CellBlockQuery blockers,
			LocalPlaneReflectionSolver.SegmentBlockQuery exactBlockers,
			LocalPlaneReflectionSolver.CellCoverageQuery coverage,
			int maximumCellsPerLeg,
			Workspace output
	) {
		Objects.requireNonNull(patches, "patches");
		Objects.requireNonNull(blockers, "blockers");
		Objects.requireNonNull(exactBlockers, "exactBlockers");
		Objects.requireNonNull(coverage, "coverage");
		Objects.requireNonNull(output, "output");
		output.reset();
		double direct = distance(
				sourceX, sourceY, sourceZ,
				listenerX, listenerY, listenerZ
		);
		for (int patchIndex = 0; patchIndex < patches.size(); patchIndex++) {
			AxisAlignedPlanePatch patch = patches.get(patchIndex);
			LocalPlaneReflectionSolver.solve(
					sourceX, sourceY, sourceZ,
					listenerX, listenerY, listenerZ,
					patch,
					blockers,
					exactBlockers,
					coverage,
					maximumCellsPerLeg,
					output.path
			);
			if (!output.path.candidateGeometry()) {
				continue;
			}
			output.geometryCandidates++;
			if (!output.path.complete()) {
				output.incompleteCandidates++;
				continue;
			}
			if (!output.path.topologyVisible()) {
				output.occludedCandidates++;
				continue;
			}
			AcousticMaterial material = patch.material();
			AcousticBands absorption = material.surfaceAbsorption();
			double low = BoundedFirstOrderGainSolver.energyGain(
					direct,
					output.path.pathLengthMeters(),
					absorption.low(),
					material.scattering(),
					0.0,
					false
			);
			double mid = BoundedFirstOrderGainSolver.energyGain(
					direct,
					output.path.pathLengthMeters(),
					absorption.mid(),
					material.scattering(),
					0.0,
					false
			);
			double high = BoundedFirstOrderGainSolver.energyGain(
					direct,
					output.path.pathLengthMeters(),
					absorption.high(),
					material.scattering(),
					0.0,
					false
			);
			insert(
					patchIndex,
					output.path,
					low,
					mid,
					high,
					output
			);
		}
		cluster(listenerX, listenerY, listenerZ, output);
	}

	private static void insert(
			int patchIndex,
			LocalPlaneReflectionSolver.Workspace path,
			double low,
			double mid,
			double high,
			Workspace output
	) {
		double score = low + mid + high;
		int insert = output.selectedCount;
		if (insert == MAXIMUM_CANDIDATES) {
			insert--;
			if (!before(
					score,
					path.pathLengthMeters(),
					patchIndex,
					output.score[insert],
					output.pathLength[insert],
					output.patchIndex[insert]
			)) {
				return;
			}
		} else {
			output.selectedCount++;
		}
		while (insert > 0 && before(
				score,
				path.pathLengthMeters(),
				patchIndex,
				output.score[insert - 1],
				output.pathLength[insert - 1],
				output.patchIndex[insert - 1]
		)) {
			copy(insert - 1, insert, output);
			insert--;
		}
		output.patchIndex[insert] = patchIndex;
		output.reflectionX[insert] = path.reflectionX();
		output.reflectionY[insert] = path.reflectionY();
		output.reflectionZ[insert] = path.reflectionZ();
		output.pathLength[insert] = path.pathLengthMeters();
		output.incidenceCosine[insert] = path.incidenceCosine();
		output.low[insert] = low;
		output.mid[insert] = mid;
		output.high[insert] = high;
		output.score[insert] = score;
	}

	private static boolean before(
			double leftScore,
			double leftLength,
			int leftIndex,
			double rightScore,
			double rightLength,
			int rightIndex
	) {
		int scoreOrder = Double.compare(leftScore, rightScore);
		if (scoreOrder != 0) {
			return scoreOrder > 0;
		}
		int lengthOrder = Double.compare(leftLength, rightLength);
		return lengthOrder != 0
				? lengthOrder < 0
				: leftIndex < rightIndex;
	}

	private static void copy(int source, int target, Workspace output) {
		output.patchIndex[target] = output.patchIndex[source];
		output.reflectionX[target] = output.reflectionX[source];
		output.reflectionY[target] = output.reflectionY[source];
		output.reflectionZ[target] = output.reflectionZ[source];
		output.pathLength[target] = output.pathLength[source];
		output.incidenceCosine[target] = output.incidenceCosine[source];
		output.low[target] = output.low[source];
		output.mid[target] = output.mid[source];
		output.high[target] = output.high[source];
		output.score[target] = output.score[source];
	}

	private static void cluster(
			double listenerX,
			double listenerY,
			double listenerZ,
			Workspace output
	) {
		for (int selected = 0; selected < output.selectedCount; selected++) {
			int insert = selected;
			while (insert > 0
					&& output.pathLength[
							output.arrivalOrder[insert - 1]
					] > output.pathLength[selected]) {
				output.arrivalOrder[insert] =
						output.arrivalOrder[insert - 1];
				insert--;
			}
			output.arrivalOrder[insert] = selected;
		}
		for (int order = 0; order < output.selectedCount; order++) {
			int selected = output.arrivalOrder[order];
			double arrival = output.pathLength[selected]
					/ SPEED_OF_SOUND_METERS_PER_SECOND
					* SAMPLE_RATE_HZ;
			boolean join = output.clusterCount > 0
					&& arrival - output.lastArrival[
							output.clusterCount - 1
					] <= CLUSTER_SEPARATION_SAMPLES;
			int cluster = join
					? output.clusterCount - 1
					: output.clusterCount++;
			if (!join) {
				output.clusterLow[cluster] = 0.0;
				output.clusterMid[cluster] = 0.0;
				output.clusterHigh[cluster] = 0.0;
				output.clusterWeightedArrival[cluster] = 0.0;
				output.clusterWeight[cluster] = 0.0;
				output.clusterDirectionX[cluster] = 0.0;
				output.clusterDirectionY[cluster] = 0.0;
				output.clusterDirectionZ[cluster] = 0.0;
				output.clusterFallbackDirectionX[cluster] = 0.0;
				output.clusterFallbackDirectionY[cluster] = 0.0;
				output.clusterFallbackDirectionZ[cluster] = 0.0;
				output.clusterPathCount[cluster] = 0;
			}
			double weight = output.score[selected];
			double directionX =
					output.reflectionX[selected] - listenerX;
			double directionY =
					output.reflectionY[selected] - listenerY;
			double directionZ =
					output.reflectionZ[selected] - listenerZ;
			double directionLength = Math.sqrt(
					directionX * directionX
							+ directionY * directionY
							+ directionZ * directionZ
			);
			output.clusterLow[cluster] += output.low[selected];
			output.clusterMid[cluster] += output.mid[selected];
			output.clusterHigh[cluster] += output.high[selected];
			output.clusterWeightedArrival[cluster] += arrival * weight;
			output.clusterWeight[cluster] += weight;
			if (directionLength > 0.0) {
				double unitX = directionX / directionLength;
				double unitY = directionY / directionLength;
				double unitZ = directionZ / directionLength;
				output.clusterDirectionX[cluster] += unitX * weight;
				output.clusterDirectionY[cluster] += unitY * weight;
				output.clusterDirectionZ[cluster] += unitZ * weight;
				if (!join) {
					output.clusterFallbackDirectionX[cluster] = unitX;
					output.clusterFallbackDirectionY[cluster] = unitY;
					output.clusterFallbackDirectionZ[cluster] = unitZ;
				}
			}
			output.clusterPathCount[cluster]++;
			output.lastArrival[cluster] = arrival;
		}
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

	public static final class Workspace {
		private final LocalPlaneReflectionSolver.Workspace path =
				new LocalPlaneReflectionSolver.Workspace();
		private final int[] patchIndex = new int[MAXIMUM_CANDIDATES];
		private final double[] reflectionX =
				new double[MAXIMUM_CANDIDATES];
		private final double[] reflectionY =
				new double[MAXIMUM_CANDIDATES];
		private final double[] reflectionZ =
				new double[MAXIMUM_CANDIDATES];
		private final double[] pathLength =
				new double[MAXIMUM_CANDIDATES];
		private final double[] incidenceCosine =
				new double[MAXIMUM_CANDIDATES];
		private final double[] low = new double[MAXIMUM_CANDIDATES];
		private final double[] mid = new double[MAXIMUM_CANDIDATES];
		private final double[] high = new double[MAXIMUM_CANDIDATES];
		private final double[] score = new double[MAXIMUM_CANDIDATES];
		private final int[] arrivalOrder =
				new int[MAXIMUM_CANDIDATES];
		private final double[] clusterLow =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterMid =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterHigh =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterWeightedArrival =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterWeight =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterDirectionX =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterDirectionY =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterDirectionZ =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterFallbackDirectionX =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterFallbackDirectionY =
				new double[MAXIMUM_CANDIDATES];
		private final double[] clusterFallbackDirectionZ =
				new double[MAXIMUM_CANDIDATES];
		private final double[] lastArrival =
				new double[MAXIMUM_CANDIDATES];
		private final int[] clusterPathCount =
				new int[MAXIMUM_CANDIDATES];
		private int geometryCandidates;
		private int occludedCandidates;
		private int incompleteCandidates;
		private int selectedCount;
		private int clusterCount;

		private void reset() {
			geometryCandidates = 0;
			occludedCandidates = 0;
			incompleteCandidates = 0;
			selectedCount = 0;
			clusterCount = 0;
		}

		public int geometryCandidates() {
			return geometryCandidates;
		}

		public int occludedCandidates() {
			return occludedCandidates;
		}

		public int incompleteCandidates() {
			return incompleteCandidates;
		}

		public int selectedCount() {
			return selectedCount;
		}

		public int patchIndex(int selected) {
			return patchIndex[checkedSelected(selected)];
		}

		public double reflectionX(int selected) {
			return reflectionX[checkedSelected(selected)];
		}

		public double reflectionY(int selected) {
			return reflectionY[checkedSelected(selected)];
		}

		public double reflectionZ(int selected) {
			return reflectionZ[checkedSelected(selected)];
		}

		public double pathLengthMeters(int selected) {
			return pathLength[checkedSelected(selected)];
		}

		public double incidenceCosine(int selected) {
			return incidenceCosine[checkedSelected(selected)];
		}

		public double low(int selected) {
			return low[checkedSelected(selected)];
		}

		public double mid(int selected) {
			return mid[checkedSelected(selected)];
		}

		public double high(int selected) {
			return high[checkedSelected(selected)];
		}

		public double selectedLowSum() {
			double sum = 0.0;
			for (int selected = 0; selected < selectedCount; selected++) {
				sum += low[selected];
			}
			return sum;
		}

		public double selectedMidSum() {
			double sum = 0.0;
			for (int selected = 0; selected < selectedCount; selected++) {
				sum += mid[selected];
			}
			return sum;
		}

		public double selectedHighSum() {
			double sum = 0.0;
			for (int selected = 0; selected < selectedCount; selected++) {
				sum += high[selected];
			}
			return sum;
		}

		public int clusterCount() {
			return clusterCount;
		}

		public double clusterArrivalSamples(int cluster) {
			int checked = checkedCluster(cluster);
			return clusterWeightedArrival[checked]
					/ clusterWeight[checked];
		}

		public double clusterLow(int cluster) {
			return clusterLow[checkedCluster(cluster)];
		}

		public double clusterMid(int cluster) {
			return clusterMid[checkedCluster(cluster)];
		}

		public double clusterHigh(int cluster) {
			return clusterHigh[checkedCluster(cluster)];
		}

		public int clusterPathCount(int cluster) {
			return clusterPathCount[checkedCluster(cluster)];
		}

		public double clusterDirectionX(int cluster) {
			int checked = checkedCluster(cluster);
			double length = clusterDirectionLength(checked);
			return length > 1.0e-15
					? clusterDirectionX[checked] / length
					: clusterFallbackDirectionX[checked];
		}

		public double clusterDirectionY(int cluster) {
			int checked = checkedCluster(cluster);
			double length = clusterDirectionLength(checked);
			return length > 1.0e-15
					? clusterDirectionY[checked] / length
					: clusterFallbackDirectionY[checked];
		}

		public double clusterDirectionZ(int cluster) {
			int checked = checkedCluster(cluster);
			double length = clusterDirectionLength(checked);
			return length > 1.0e-15
					? clusterDirectionZ[checked] / length
					: clusterFallbackDirectionZ[checked];
		}

		private double clusterDirectionLength(int cluster) {
			double x = clusterDirectionX[cluster];
			double y = clusterDirectionY[cluster];
			double z = clusterDirectionZ[cluster];
			return Math.sqrt(x * x + y * y + z * z);
		}

		private int checkedSelected(int selected) {
			if (selected < 0 || selected >= selectedCount) {
				throw new IndexOutOfBoundsException(selected);
			}
			return selected;
		}

		private int checkedCluster(int cluster) {
			if (cluster < 0 || cluster >= clusterCount) {
				throw new IndexOutOfBoundsException(cluster);
			}
			return cluster;
		}
	}
}
